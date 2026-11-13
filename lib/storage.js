// Supabase Storage replacement for the old MEDIA_ROOT local-disk storage in
// server.js. Vercel serverless functions have no persistent, shared local
// disk, so course media (video/PDF/audio/thumbnail files) now lives in a
// private Supabase Storage bucket instead of app/storage/media.
//
// Upload flow (see api/index.js PUT/.../assets and POST/.../confirm, and
// public/media.js for the matching frontend change):
//   1. PUT /api/instructor/courses/:id/assets issues a short-lived SIGNED
//      UPLOAD URL via createUploadTicket() — the browser then uploads the
//      file bytes DIRECTLY to Supabase Storage, never through this
//      function. This avoids Vercel's ~4.5MB function request-body limit,
//      which course videos would routinely exceed.
//   2. POST /api/instructor/courses/:id/assets/:assetId/confirm checks the
//      object actually landed in the bucket (objectExists()) and only then
//      writes the course_assets database row.
//
// Download flow: GET /api/media/:id runs the existing canReadCourseMedia()
// authorization check, then redirects (HTTP 302) to a signed READ URL from
// createReadUrl(). Supabase Storage's signed URLs support HTTP Range
// requests, so video scrubbing keeps working without this app reimplementing
// byte-range serving — the browser's <video> tag follows the redirect and
// issues its own Range requests straight to Supabase.
//
// Requires SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY env vars. The service
// role key bypasses Row Level Security and must stay server-side only —
// never sent to the browser, never prefixed NEXT_PUBLIC_/VITE_/etc.
const { createClient } = require('@supabase/supabase-js');

const BUCKET = process.env.SUPABASE_MEDIA_BUCKET || 'course-media';

let _client = null;
function client() {
  if (_client) return _client;
  const url = process.env.SUPABASE_URL;
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!url || !key) {
    throw Object.assign(
      new Error('Media storage is not configured. Set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY.'),
      { code: 'STORAGE_NOT_CONFIGURED' }
    );
  }
  _client = createClient(url, key, { auth: { persistSession: false } });
  return _client;
}

/**
 * Issue a signed upload URL for a NEW object at `storedName` in the bucket.
 * Valid for 2 hours per Supabase's documented default — plenty for one
 * browser upload, short enough that a leaked URL doesn't stay useful long.
 * @returns {Promise<{uploadUrl:string, token:string, path:string}>}
 */
async function createUploadTicket(storedName) {
  const { data, error } = await client().storage.from(BUCKET).createSignedUploadUrl(storedName);
  if (error) throw Object.assign(new Error('Could not prepare the upload: ' + error.message), { status: 502 });
  return { uploadUrl: data.signedUrl, token: data.token, path: data.path };
}

/**
 * Confirm an object was actually written to the bucket after the browser's
 * direct upload — used before trusting the client's claimed upload and
 * writing the course_assets row.
 */
async function objectExists(storedName) {
  const slash = storedName.lastIndexOf('/');
  const dir = slash === -1 ? '' : storedName.slice(0, slash);
  const name = slash === -1 ? storedName : storedName.slice(slash + 1);
  const { data, error } = await client().storage.from(BUCKET).list(dir, { search: name, limit: 1 });
  if (error) return false;
  return !!(data || []).find((f) => f.name === name);
}

/**
 * A short-lived signed URL for reading/streaming a private object.
 * 300s matches the original streamMedia()'s `Cache-Control: private,
 * max-age=300` — short enough that a leaked/shared link stops working soon,
 * long enough for one page view (including scrubbing a video).
 */
async function createReadUrl(storedName, expiresInSeconds = 300) {
  const { data, error } = await client().storage.from(BUCKET).createSignedUrl(storedName, expiresInSeconds);
  if (error) throw Object.assign(new Error('Could not access this media file: ' + error.message), { status: 404 });
  return data.signedUrl;
}

/** Best-effort delete — used when a course asset row is removed. Never throws. */
async function removeObject(storedName) {
  try {
    await client().storage.from(BUCKET).remove([storedName]);
  } catch {
    /* best-effort */
  }
}

module.exports = { BUCKET, createUploadTicket, objectExists, createReadUrl, removeObject };
