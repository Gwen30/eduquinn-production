// Single Vercel Function serving every /api/* route (and /health, /ready),
// per vercel.json's rewrites. This is a line-by-line port of the original
// server.js's `api(req,res,url)` if-chain from Node's raw (req,res) model to
// Vercel's current (Sept 2026) Web-standard `fetch(request): Response`
// convention for framework-less projects — see the migration plan for why a
// single shared function (not 58 separate files) is both Vercel's own
// recommendation and the lower-risk port.
//
// What changed vs. server.js, and why:
//   - Node (req,res) handlers → Web Request/Response: body reading is
//     `await request.text()` instead of stream events; responses are
//     built and returned, not written-then-ended.
//   - In-memory rate limiting (`rateBuckets` Map) → Postgres-backed
//     (lib/rateLimit.js) — a Map has no meaning across independent
//     serverless invocations.
//   - Local-disk media (`MEDIA_ROOT`, readBinary/streamMedia) → Supabase
//     Storage signed URLs (lib/storage.js). Upload is now a 3-step flow
//     (ticket → direct browser upload → confirm); download is an HTTP
//     redirect to a signed, Range-capable URL. See public/media.js for the
//     matching frontend change.
//   - Static file serving, gzip, and the SPA/html fallback are REMOVED —
//     Vercel serves public/ natively per vercel.json's outputDirectory.
//   - The boot-time `db.migrate()` call is REMOVED — run `npm run migrate`
//     manually/in CI instead of on every cold start (see db.js).
// Everything else (route paths, validation, role checks, SQL, business
// logic) is intentionally UNCHANGED from server.js.
const crypto = require('crypto');
const https = require('https');
const bcrypt = require('bcryptjs');
const db = require('../db');
const storage = require('../lib/storage');
const { hitRate } = require('../lib/rateLimit');

const SUPPORT_EMAIL = process.env.SUPPORT_EMAIL || 'support@eduquinn.co.zw';
const SUPPORT_FROM_EMAIL = process.env.SUPPORT_FROM_EMAIL || SUPPORT_EMAIL;
const BREVO_API_KEY = process.env.BREVO_API_KEY || '';
const SESSION_DAYS = Math.max(1, Math.min(90, Number(process.env.SESSION_DAYS || 14)));
const NODE_ENV = process.env.NODE_ENV || 'development';
const APP_BASE_URL = String(process.env.APP_BASE_URL || '').replace(/\/$/, '');
const ALLOWED_ORIGINS = new Set(String(process.env.ALLOWED_ORIGINS || APP_BASE_URL).split(',').map((x) => x.trim()).filter(Boolean));
const API_RATE_LIMIT = Math.max(30, Number(process.env.API_RATE_LIMIT || 300));
const LOGIN_RATE_LIMIT = Math.max(3, Number(process.env.LOGIN_RATE_LIMIT || 10));
const RATE_WINDOW_MS = Math.max(60000, Number(process.env.RATE_WINDOW_MS || 300000));
const INSTRUCTOR_SHARE_PERCENT = Math.max(0, Math.min(100, Number(process.env.INSTRUCTOR_SHARE_PERCENT || 75)));
const PAYMENT_SANDBOX = /^(1|true|yes)$/i.test(process.env.PAYMENT_SANDBOX || '');

const securityHeaders = {
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'SAMEORIGIN',
  'Referrer-Policy': 'strict-origin-when-cross-origin',
  'Permissions-Policy': 'camera=(self), microphone=(self), geolocation=()',
  'Cross-Origin-Opener-Policy': 'same-origin',
  'Cross-Origin-Resource-Policy': 'same-origin',
  'Content-Security-Policy':
    "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'sha256-t/eTlbLWDWAIU9+yWPOS1zIpJdkMVj7iLcX0GPuqM7s=' 'sha256-6W3Pwz7K6uVZ0uWt1Y+aDG3lz9IEpQUar17l3gPXuFE=' 'sha256-ioK0Mc1gISEXTnzGL6U7NudlIaWQVaUhst2/AKlJoPw=' 'sha256-ClYVc3pR1yHqcwlTxk91rgG1LFLp7g1tVycyuPbEttI=' 'sha256-l2j6/x1YjrcrfMl4ZC8lDEa2NZBgZWQfwdvi3RcM0c8=' 'sha256-U+JvqpGjJ1B2M+1wvoRXoZQ4frPmC4RqTGTb7nSGLpk=' 'sha256-vrQMcf+56qfy3wfISz92LpbminwzHpWVStabmbVDE6M='; connect-src 'self'; frame-ancestors 'self'; base-uri 'self'; form-action 'self'",
};
function securityFor() {
  const h = { ...securityHeaders };
  if (NODE_ENV === 'production') h['Strict-Transport-Security'] = 'max-age=31536000; includeSubDomains';
  return h;
}
function uuid() { return crypto.randomUUID(); }
function sha(v) { return crypto.createHash('sha256').update(v).digest('hex'); }
function requestId(request) { return String(request.headers.get('x-request-id') || crypto.randomUUID()).slice(0, 100); }
// Vercel always sets x-forwarded-for to the visitor's real IP on every
// request (see Vercel's request-headers docs) — there is no equivalent of
// Node's req.socket.remoteAddress in the Web Request model, so this is the
// only source of the client IP here.
function clientIp(request) {
  const x = String(request.headers.get('x-forwarded-for') || '').split(',')[0].trim();
  return x || 'unknown';
}
function sameOriginAllowed(request, method) {
  if (!['POST', 'PUT', 'PATCH', 'DELETE'].includes(method || '')) return true;
  const origin = String(request.headers.get('origin') || '');
  if (!origin) return true;
  if (ALLOWED_ORIGINS.size) return ALLOWED_ORIGINS.has(origin);
  try { const host = String(request.headers.get('host') || ''); return new URL(origin).host === host; } catch { return false; }
}
async function requireApiGuard(request, url) {
  if (!sameOriginAllowed(request, request.method)) return json(403, { message: 'Request origin is not allowed.' });
  const group = url.pathname === '/api/auth/login' ? 'login' : 'api';
  const limit = group === 'login' ? LOGIN_RATE_LIMIT : API_RATE_LIMIT;
  const r = await hitRate(`${group}:${clientIp(request)}`, limit, RATE_WINDOW_MS);
  if (!r.ok) return json(429, { message: 'Too many requests. Please try again shortly.', retryAfter: r.retry }, { 'Retry-After': String(r.retry) });
  return null;
}
function cookieMap(request) {
  return Object.fromEntries(
    String(request.headers.get('cookie') || '').split(';').map((x) => x.trim()).filter(Boolean).map((x) => {
      const i = x.indexOf('=');
      return [decodeURIComponent(i < 0 ? x : x.slice(0, i)), decodeURIComponent(i < 0 ? '' : x.slice(i + 1))];
    })
  );
}
function setSessionCookieHeader(token, maxAge) {
  const secure = NODE_ENV === 'production' ? '; Secure' : '';
  return `eq_session=${encodeURIComponent(token)}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${maxAge}${secure}`;
}
function clearSessionCookieHeader() {
  const secure = NODE_ENV === 'production' ? '; Secure' : '';
  return `eq_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0${secure}`;
}
function json(status, data, extra = {}) {
  return new Response(JSON.stringify(data), { status, headers: { ...securityFor(), 'Content-Type': 'application/json; charset=utf-8', ...extra } });
}
async function readJson(request, limit = 100000) {
  const text = await request.text();
  if (text.length > limit) throw Object.assign(new Error('Payload too large'), { status: 413 });
  try { return JSON.parse(text || '{}'); } catch { throw Object.assign(new Error('Invalid JSON'), { status: 400 }); }
}
async function readRawBody(request, limit = 100000) {
  const text = await request.text();
  if (text.length > limit) throw Object.assign(new Error('Payload too large'), { status: 413 });
  return text;
}
function publicUser(u) { return u ? { id: u.id, name: u.name, email: u.email, role: u.role, status: u.status, expertise: u.expertise || '', emailVerified: !!u.email_verified } : null; }
async function currentUser(request) {
  const token = cookieMap(request).eq_session;
  if (!token) return null;
  const r = await db.query(`SELECT u.*,s.id session_id FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=$1 AND s.expires_at>now() AND u.status='active'`, [sha(token)]);
  if (!r.rows[0]) return null;
  db.query('UPDATE sessions SET last_seen_at=now() WHERE id=$1', [r.rows[0].session_id]).catch(() => {});
  return r.rows[0];
}
// Returns a user object on success, or a Response to return immediately on
// failure. Callers: `const u = await requireRole(request, [...]); if (u instanceof Response) return u;`
async function requireRole(request, roles) {
  let user;
  try { user = await currentUser(request); } catch (e) { return dbFail(e); }
  if (!user) return json(401, { message: 'Please sign in to continue.' });
  if (!roles.includes(user.role)) return json(403, { message: 'You do not have permission to perform this action.' });
  return user;
}
function dbFail(e) {
  return json(e.code === 'DB_NOT_CONFIGURED' ? 503 : 500, { message: e.code === 'DB_NOT_CONFIGURED' ? 'The database is not configured. Set DATABASE_URL.' : 'A database operation failed.', code: e.code || 'DB_ERROR' });
}
function validEmail(v) { return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(v || '')); }
function weakPassword(p) { p = String(p || ''); return p.length < 10 || !/[a-zA-Z]/.test(p) || !/[0-9]/.test(p); }
function slugify(v) { return String(v || 'course').toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 180) || 'course'; }
async function audit(user, action, entityType = null, entityId = null, details = {}) {
  try { await db.query('INSERT INTO audit_log(id,actor_user_id,action,entity_type,entity_id,details) VALUES($1,$2,$3,$4,$5,$6::jsonb)', [uuid(), user?.id || null, action, entityType, entityId, JSON.stringify(details)]); } catch {}
}
async function securityEvent(request, eventType, userId = null, details = {}) {
  try { await db.query('INSERT INTO security_events(id,user_id,event_type,ip_address,user_agent,details) VALUES($1,$2,$3,$4,$5,$6::jsonb)', [uuid(), userId, eventType, clientIp(request), String(request.headers.get('user-agent') || '').slice(0, 500), JSON.stringify(details)]); } catch {}
}
async function sendBrevoEmail({ userId = null, email, name = '', subject, text, template = 'general' }) {
  if (!BREVO_API_KEY || !validEmail(email)) return { status: 'not_configured' };
  const payload = JSON.stringify({ sender: { name: 'EduQuinn', email: SUPPORT_FROM_EMAIL }, to: [{ email, name: name || undefined }], subject, textContent: text });
  let status = 0, body = '';
  await new Promise((resolve) => {
    const r = https.request({ hostname: 'api.brevo.com', path: '/v3/smtp/email', method: 'POST', headers: { 'api-key': BREVO_API_KEY, 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(payload) } }, (br) => { status = br.statusCode || 0; br.on('data', (c) => (body += c)); br.on('end', resolve); });
    r.on('error', (e) => { body = e.message; resolve(); });
    r.write(payload); r.end();
  });
  const ok = status >= 200 && status < 300;
  try { await db.query('INSERT INTO email_delivery_log(id,user_id,email,template,subject,status,provider_reference,error_message) VALUES($1,$2,$3,$4,$5,$6,$7,$8)', [uuid(), userId, email, template, subject, ok ? 'sent' : 'failed', ok ? body.slice(0, 200) : null, ok ? null : body.slice(0, 1000)]); } catch {}
  return { status: ok ? 'sent' : 'failed', code: status };
}
async function notificationPrefs(userId) {
  const r = await db.query('SELECT * FROM notification_preferences WHERE user_id=$1', [userId]);
  return r.rows[0] || { in_app_messages: true, email_messages: true, in_app_announcements: true, email_announcements: true, in_app_live_classes: true, email_live_classes: true };
}
async function notifyUser(userId, { type = 'general', title, body = '', link = '', emailSubject = '', emailText = '', channel = 'messages' }) {
  const u = (await db.query('SELECT id,name,email FROM users WHERE id=$1', [userId])).rows[0]; if (!u) return;
  const prefs = await notificationPrefs(userId), inKey = 'in_app_' + channel, emailKey = 'email_' + channel;
  if (prefs[inKey] !== false) await db.query('INSERT INTO notifications(id,user_id,type,title,body,link) VALUES($1,$2,$3,$4,$5,$6)', [uuid(), userId, type, title, body, link || null]);
  if (emailSubject && prefs[emailKey] !== false) await sendBrevoEmail({ userId, email: u.email, name: u.name, subject: emailSubject, text: emailText || body, template: type });
}
async function ownsCourse(user, courseId) {
  if (user.role === 'admin') return true;
  const r = await db.query('SELECT 1 FROM courses WHERE id=$1 AND instructor_id=$2', [courseId, user.id]);
  return !!r.rowCount;
}
async function canReadCourseMedia(user, courseId) {
  if (!user) return false;
  if (user.role === 'admin') return true;
  if (user.role === 'instructor') return ownsCourse(user, courseId);
  if (user.role === 'student') { const r = await db.query('SELECT 1 FROM enrolments WHERE student_id=$1 AND course_id=$2', [user.id, courseId]); return !!r.rowCount; }
  return false;
}
function safeExt(name, mime = '') {
  const path = require('path');
  const ext = path.extname(String(name || '')).toLowerCase();
  if (/^\.[a-z0-9]{1,8}$/.test(ext)) return ext;
  const m = { 'video/mp4': '.mp4', 'video/webm': '.webm', 'application/pdf': '.pdf', 'image/jpeg': '.jpg', 'image/png': '.png', 'audio/mpeg': '.mp3' };
  return m[mime] || '.bin';
}

function cents(v) { return Math.max(0, Math.round(Number(v || 0))); }
async function couponFor(code, userId, subtotal) {
  code = String(code || '').trim().toUpperCase(); if (!code) return { code: null, discount: 0, coupon: null };
  const r = await db.query(`SELECT c.*,(SELECT count(*) FROM coupon_redemptions cr WHERE cr.coupon_id=c.id) used FROM coupons c WHERE upper(c.code)=upper($1) AND c.active=true AND (c.starts_at IS NULL OR c.starts_at<=now()) AND (c.ends_at IS NULL OR c.ends_at>=now())`, [code]);
  const c = r.rows[0]; if (!c) return { error: 'Coupon is invalid or expired.' };
  if (c.max_redemptions !== null && Number(c.used) >= Number(c.max_redemptions)) return { error: 'Coupon redemption limit has been reached.' };
  let discount = c.discount_type === 'percent' ? Math.floor((subtotal * Math.min(100, Number(c.discount_value))) / 100) : Math.min(subtotal, Number(c.discount_value));
  return { code: c.code, discount: Math.max(0, discount), coupon: c };
}
async function markOrderPaid(orderId, reference = '') {
  return db.withTransaction(async (client) => {
    const o = await client.query('SELECT * FROM orders WHERE id=$1', [orderId]); if (!o.rowCount) throw Object.assign(new Error('Order not found.'), { status: 404 });
    if (o.rows[0].status === 'paid') return o.rows[0];
    await client.query("UPDATE orders SET status='paid',paid_at=now(),updated_at=now(),payment_reference=COALESCE(NULLIF($2,''),payment_reference) WHERE id=$1", [orderId, reference]);
    await client.query("UPDATE payment_transactions SET status='paid',provider_reference=COALESCE(NULLIF($2,''),provider_reference),updated_at=now() WHERE order_id=$1 AND status<>'paid'", [orderId, reference]);
    const items = await client.query(`SELECT oi.id order_item_id,oi.course_id,oi.unit_price_cents,c.instructor_id FROM order_items oi JOIN courses c ON c.id=oi.course_id WHERE oi.order_id=$1`, [orderId]);
    const share = INSTRUCTOR_SHARE_PERCENT;
    for (const i of items.rows) {
      await client.query(`INSERT INTO enrolments(id,student_id,course_id) VALUES($1,$2,$3) ON CONFLICT(student_id,course_id) DO NOTHING`, [uuid(), o.rows[0].student_id, i.course_id]);
      if (i.instructor_id) { const instructor = Math.floor((Number(i.unit_price_cents) * share) / 100), platform = Number(i.unit_price_cents) - instructor; await client.query(`INSERT INTO instructor_earnings(id,instructor_id,order_id,order_item_id,course_id,gross_cents,instructor_cents,platform_cents,status) VALUES($1,$2,$3,$4,$5,$6,$7,$8,'available') ON CONFLICT(order_item_id) DO NOTHING`, [uuid(), i.instructor_id, orderId, i.order_item_id, i.course_id, i.unit_price_cents, instructor, platform]); }
    }
    return (await client.query('SELECT * FROM orders WHERE id=$1', [orderId])).rows[0];
  });
}

// ---- Paynow (EcoCash / OneMoney / Visa & Mastercard via Paynow's hosted checkout) ----
let _paynowLib = null;
function loadPaynowLib() {
  if (_paynowLib !== null) return _paynowLib || null;
  try { _paynowLib = require('paynow'); } catch { console.error('The "paynow" package is not installed. Run `npm install` to enable online payments.'); _paynowLib = false; }
  return _paynowLib || null;
}
function getPaynowClient() {
  if (!process.env.PAYNOW_INTEGRATION_ID || !process.env.PAYNOW_INTEGRATION_KEY) return null;
  const lib = loadPaynowLib(); if (!lib) return null;
  return new lib.Paynow(process.env.PAYNOW_INTEGRATION_ID, process.env.PAYNOW_INTEGRATION_KEY);
}
async function startPaynowPayment(order, lineItems) {
  const client = getPaynowClient();
  if (!client) return { started: false, reason: 'Online payments are not configured yet.' };
  try {
    client.resultUrl = process.env.PAYNOW_RESULT_URL || `${APP_BASE_URL}/api/payments/paynow/webhook`;
    client.returnUrl = (process.env.PAYNOW_RETURN_URL || `${APP_BASE_URL}/order-success.html`) + `?db=1&orderId=${encodeURIComponent(order.id)}`;
    const payment = client.createPayment(order.id);
    for (const it of lineItems) payment.add(String(it.title || 'Course').slice(0, 100), Number(it.unit_price_cents || it.price_cents || 0) / 100);
    const response = await client.send(payment);
    if (response && response.success) {
      try { await db.query(`UPDATE payment_transactions SET payload=jsonb_set(payload,'{pollUrl}',to_jsonb($2::text),true),updated_at=now() WHERE order_id=$1 AND status<>'paid'`, [order.id, response.pollUrl || '']); } catch {}
      return { started: true, redirectUrl: response.redirectUrl };
    }
    return { started: false, reason: (response && response.error) || 'Paynow did not accept the request.' };
  } catch (e) { return { started: false, reason: e.message }; }
}
async function paynowWebhook(request) {
  // Server-to-server status push from Paynow. Always acknowledge with 200 so Paynow does not
  // retry-storm us; only act on an update whose hash we have verified (parseStatusUpdate throws
  // on a bad hash), and only once we can match it to a real pending order.
  // await request.text() gives the exact raw bytes Paynow signed — Vercel's fetch-handler model
  // never auto-parses the body for you, so this is simpler/safer here than on frameworks that do.
  try {
    const raw = await readRawBody(request, 20000);
    const client = getPaynowClient();
    if (!client) return new Response('ignored', { status: 200, headers: securityHeaders });
    let update;
    try { update = client.parseStatusUpdate(raw); } catch (e) { await securityEvent(request, 'paynow.webhook_invalid', null, { error: e.message }); return new Response('ignored', { status: 200, headers: securityHeaders }); }
    const orderId = String(update.reference || ''), status = String(update.status || '').toLowerCase();
    if (/^[0-9a-f-]{8}-[0-9a-f-]{4}-[0-9a-f-]{4}-[0-9a-f-]{4}-[0-9a-f-]{12}$/i.test(orderId) && ['paid', 'awaiting delivery', 'delivered'].includes(status)) {
      try { await markOrderPaid(orderId, update.paynowreference || ''); } catch (e) { console.error('paynow webhook markOrderPaid failed', orderId, e.message); }
    }
    return new Response('OK', { status: 200, headers: securityHeaders });
  } catch (e) {
    console.error('paynow webhook error', e);
    return new Response('OK', { status: 200, headers: securityHeaders });
  }
}

async function receiveSupport(request) {
  try {
    const data = await readJson(request, 25000), clean = (v) => String(v || '').trim();
    const name = clean(data.name), email = clean(data.email), role = clean(data.role), topic = clean(data.topic), subject = clean(data.subject), message = clean(data.message);
    if (!name || !validEmail(email) || !subject || !message) return json(400, { message: 'Please complete all required support fields.' });
    let user = null; try { user = await currentUser(request); } catch {}
    const ticketId = uuid();
    try { await db.query('INSERT INTO support_tickets(id,user_id,name,email,role,topic,subject,message) VALUES($1,$2,$3,$4,$5,$6,$7,$8)', [ticketId, user?.id || null, name, email, role, topic, subject, message]); } catch (e) { if (e.code !== 'DB_NOT_CONFIGURED') throw e; }
    if (!BREVO_API_KEY) return json(202, { message: 'Your support request has been recorded.', ticketId, emailDelivery: 'not_configured', fallback: true, email: SUPPORT_EMAIL });
    const text = `Ticket: ${ticketId}\nName: ${name}\nEmail: ${email}\nRole: ${role}\nTopic: ${topic}\n\n${message}`;
    const payload = JSON.stringify({ sender: { name: 'EduQuinn Support Form', email: SUPPORT_FROM_EMAIL }, to: [{ email: SUPPORT_EMAIL, name: 'EduQuinn Support' }], replyTo: { email, name }, subject: `[EduQuinn Support] ${topic} — ${subject}`, textContent: text });
    const status = await new Promise((resolve) => {
      const r = https.request({ hostname: 'api.brevo.com', path: '/v3/smtp/email', method: 'POST', headers: { 'api-key': BREVO_API_KEY, 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(payload) } }, (br) => { br.resume(); br.on('end', () => resolve(br.statusCode)); });
      r.on('error', () => resolve(0)); r.write(payload); r.end();
    });
    return status >= 200 && status < 300 ? json(200, { message: 'Your support request has been sent to EduQuinn Support.', ticketId }) : json(202, { message: 'Your support request was recorded, but email delivery is temporarily unavailable.', ticketId, fallback: true, email: SUPPORT_EMAIL });
  } catch (e) { return e.status ? json(e.status, { message: e.message }) : dbFail(e); }
}

async function handle(request) {
  const url = new URL(request.url);
  const method = request.method;

  if (url.pathname === '/health') return json(200, { status: 'ok', phase: '17' });
  if (url.pathname === '/ready') { const database = await db.ping(); return json(database.ok ? 200 : 503, { status: database.ok ? 'ready' : 'not_ready', phase: '17', database }); }

  const guardFailure = await requireApiGuard(request, url);
  if (guardFailure) return guardFailure;

  if (url.pathname === '/api/support-config' && method === 'GET') return json(200, { email: SUPPORT_EMAIL });
  if (url.pathname === '/api/notifications' && method === 'GET') {
    const u = await requireRole(request, ['student', 'instructor', 'admin']); if (u instanceof Response) return u;
    try { const limit = Math.max(1, Math.min(100, Number(url.searchParams.get('limit') || 30))); const r = await db.query('SELECT id,type,title,body,link,read_at,created_at FROM notifications WHERE user_id=$1 ORDER BY created_at DESC LIMIT $2', [u.id, limit]); const unread = await db.query('SELECT count(*)::int n FROM notifications WHERE user_id=$1 AND read_at IS NULL', [u.id]); return json(200, { notifications: r.rows, unread: Number(unread.rows[0].n) }); } catch (e) { return dbFail(e); }
  }
  const notificationRead = url.pathname.match(/^\/api\/notifications\/([0-9a-f-]{36})\/read$/i);
  if (notificationRead && method === 'POST') { const u = await requireRole(request, ['student', 'instructor', 'admin']); if (u instanceof Response) return u; try { await db.query('UPDATE notifications SET read_at=COALESCE(read_at,now()) WHERE id=$1 AND user_id=$2', [notificationRead[1], u.id]); return json(200, { ok: true }); } catch (e) { return dbFail(e); } }
  if (url.pathname === '/api/notifications/read-all' && method === 'POST') { const u = await requireRole(request, ['student', 'instructor', 'admin']); if (u instanceof Response) return u; try { await db.query('UPDATE notifications SET read_at=COALESCE(read_at,now()) WHERE user_id=$1', [u.id]); return json(200, { ok: true }); } catch (e) { return dbFail(e); } }
  if (url.pathname === '/api/notification-preferences' && method === 'GET') { const u = await requireRole(request, ['student', 'instructor', 'admin']); if (u instanceof Response) return u; try { return json(200, { preferences: await notificationPrefs(u.id), emailConfigured: !!BREVO_API_KEY }); } catch (e) { return dbFail(e); } }
  if (url.pathname === '/api/notification-preferences' && method === 'PUT') { const u = await requireRole(request, ['student', 'instructor', 'admin']); if (u instanceof Response) return u; try { const x = await readJson(request), v = (k) => x[k] !== false; await db.query(`INSERT INTO notification_preferences(user_id,in_app_messages,email_messages,in_app_announcements,email_announcements,in_app_live_classes,email_live_classes,updated_at) VALUES($1,$2,$3,$4,$5,$6,$7,now()) ON CONFLICT(user_id) DO UPDATE SET in_app_messages=EXCLUDED.in_app_messages,email_messages=EXCLUDED.email_messages,in_app_announcements=EXCLUDED.in_app_announcements,email_announcements=EXCLUDED.email_announcements,in_app_live_classes=EXCLUDED.in_app_live_classes,email_live_classes=EXCLUDED.email_live_classes,updated_at=now()`, [u.id, v('in_app_messages'), v('email_messages'), v('in_app_announcements'), v('email_announcements'), v('in_app_live_classes'), v('email_live_classes')]); return json(200, { preferences: await notificationPrefs(u.id) }); } catch (e) { return dbFail(e); } }
  if (url.pathname === '/api/conversations' && method === 'POST') {
    const u = await requireRole(request, ['student']); if (u instanceof Response) return u;
    try { const x = await readJson(request), courseId = String(x.courseId || ''); if (!/^[0-9a-f-]{36}$/i.test(courseId)) return json(400, { message: 'Select a valid course.' }); const c = await db.query(`SELECT c.id,c.title,c.instructor_id FROM courses c JOIN enrolments e ON e.course_id=c.id WHERE c.id=$1 AND e.student_id=$2`, [courseId, u.id]); if (!c.rowCount) return json(403, { message: 'You can message instructors for courses you are enrolled in.' }); let r = await db.query('SELECT id FROM conversations WHERE student_id=$1 AND instructor_id=$2 AND course_id=$3', [u.id, c.rows[0].instructor_id, courseId]); if (!r.rowCount) { const id = uuid(); await db.query('INSERT INTO conversations(id,student_id,instructor_id,course_id) VALUES($1,$2,$3,$4)', [id, u.id, c.rows[0].instructor_id, courseId]); r = { rows: [{ id }] }; } return json(201, { conversationId: r.rows[0].id }); } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/announcements' && method === 'GET') {
    const u = await requireRole(request, ['student', 'instructor', 'admin']); if (u instanceof Response) return u;
    try { let r; if (u.role === 'student') r = await db.query(`SELECT a.id,a.title,a.body,a.created_at,c.title course_title,au.name author_name FROM course_announcements a JOIN courses c ON c.id=a.course_id JOIN users au ON au.id=a.author_id JOIN enrolments e ON e.course_id=a.course_id WHERE e.student_id=$1 ORDER BY a.created_at DESC LIMIT 100`, [u.id]); else if (u.role === 'instructor') r = await db.query(`SELECT a.id,a.title,a.body,a.created_at,c.title course_title FROM course_announcements a JOIN courses c ON c.id=a.course_id WHERE a.author_id=$1 ORDER BY a.created_at DESC LIMIT 100`, [u.id]); else r = await db.query(`SELECT a.id,a.title,a.body,a.created_at,c.title course_title,au.name author_name FROM course_announcements a JOIN courses c ON c.id=a.course_id JOIN users au ON au.id=a.author_id ORDER BY a.created_at DESC LIMIT 100`); return json(200, { announcements: r.rows }); } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/announcements' && method === 'POST') {
    const u = await requireRole(request, ['instructor', 'admin']); if (u instanceof Response) return u;
    try { const x = await readJson(request), courseId = String(x.courseId || ''), title = String(x.title || '').trim(), body = String(x.body || '').trim(), emailStudents = x.emailStudents !== false; if (!/^[0-9a-f-]{36}$/i.test(courseId) || title.length < 3 || body.length < 3) return json(400, { message: 'Choose a course and enter an announcement title and message.' }); if (u.role === 'instructor' && !(await ownsCourse(u, courseId))) return json(403, { message: 'You can only announce to students in your own courses.' }); const id = uuid(); await db.query('INSERT INTO course_announcements(id,course_id,author_id,title,body,email_students) VALUES($1,$2,$3,$4,$5,$6)', [id, courseId, u.id, title, body, emailStudents]); const course = (await db.query('SELECT title FROM courses WHERE id=$1', [courseId])).rows[0]; const learners = await db.query('SELECT u.id FROM enrolments e JOIN users u ON u.id=e.student_id WHERE e.course_id=$1', [courseId]); for (const row of learners.rows) await notifyUser(row.id, { type: 'course_announcement', title, body: `${course?.title || 'Course'}: ${body}`, link: '/my-learning.html', emailSubject: emailStudents ? `EduQuinn course announcement: ${title}` : '', emailText: `${course?.title || 'Your course'}\n\n${title}\n\n${body}`, channel: 'announcements' }); await audit(u, 'announcement.created', 'course', courseId, { announcementId: id, title, recipients: learners.rowCount }); return json(201, { id, recipients: learners.rowCount }); } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/auth/session' && method === 'GET') {
    try { return json(200, { user: publicUser(await currentUser(request)) }); } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/auth/register' && method === 'POST') {
    try {
      const x = await readJson(request), name = String(x.name || '').trim(), email = String(x.email || '').trim().toLowerCase(), role = x.role, expertise = String(x.expertise || '').trim(), password = String(x.password || '');
      const launch = (await db.query('SELECT * FROM platform_launch_state WHERE id=1')).rows[0];
      if (launch && !launch.registrations_open) return json(403, { message: 'New registrations are temporarily closed.' });
      if (role === 'instructor' && launch && !launch.instructor_applications_open) return json(403, { message: 'Instructor applications are temporarily closed.' });
      if (name.length < 2 || !validEmail(email) || !['student', 'instructor'].includes(role)) return json(400, { message: 'Please provide valid registration details.' });
      if (weakPassword(password)) return json(400, { message: 'Password must be at least 10 characters and include both a letter and a number.' });
      if (role === 'instructor' && !expertise) return json(400, { message: 'Please select your primary expertise.' });
      const exists = await db.query('SELECT 1 FROM users WHERE lower(email)=lower($1)', [email]); if (exists.rowCount) return json(409, { message: 'An account with this email address already exists.' });
      const id = uuid(), hash = await bcrypt.hash(password, 12); await db.query('INSERT INTO users(id,name,email,password_hash,role,status,expertise) VALUES($1,$2,$3,$4,$5,$6,$7)', [id, name, email, hash, role, 'active', role === 'instructor' ? expertise : null]);
      const token = crypto.randomBytes(32).toString('hex'), sid = uuid(), seconds = SESSION_DAYS * 86400; await db.query(`INSERT INTO sessions(id,user_id,token_hash,expires_at) VALUES($1,$2,$3,now()+($4||' seconds')::interval)`, [sid, id, sha(token), seconds]); await audit({ id }, 'account.registered', 'user', id, { role });
      return json(201, { user: { id, name, email, role, status: 'active', expertise: role === 'instructor' ? expertise : '' } }, { 'Set-Cookie': setSessionCookieHeader(token, seconds) });
    } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/auth/login' && method === 'POST') {
    try {
      const x = await readJson(request), email = String(x.email || '').trim().toLowerCase(), password = String(x.password || ''); const r = await db.query('SELECT * FROM users WHERE lower(email)=lower($1)', [email]); const u = r.rows[0]; if (!u || !(await bcrypt.compare(password, u.password_hash))) { await securityEvent(request, 'auth.login_failed', u?.id || null, { email }); return json(401, { message: 'The email address or password is incorrect.' }); } if (u.status !== 'active') { await securityEvent(request, 'auth.login_blocked', u.id, { status: u.status }); return json(403, { message: 'This account is not active.' }); }
      if (x.portal === 'public' && u.role === 'admin') return json(403, { message: 'Administrator sign-in is not available from the public login page.' }); if (x.portal === 'admin' && u.role !== 'admin') return json(403, { message: 'This account is not authorised for administration.' }); if (x.portal === 'public' && x.expectedRole && u.role !== x.expectedRole) return json(403, { message: x.expectedRole === 'student' ? 'This is not a student account. Choose Teacher login if you are an instructor.' : 'This is not a teacher account. Choose Student login if you are a learner.' });
      const token = crypto.randomBytes(32).toString('hex'), sid = uuid(), seconds = SESSION_DAYS * 86400; await db.query(`INSERT INTO sessions(id,user_id,token_hash,expires_at) VALUES($1,$2,$3,now()+($4||' seconds')::interval)`, [sid, u.id, sha(token), seconds]); await audit(u, 'auth.login', 'user', u.id, { portal: x.portal || 'public' }); await securityEvent(request, 'auth.login_succeeded', u.id, { portal: x.portal || 'public' });
      return json(200, { user: publicUser(u) }, { 'Set-Cookie': setSessionCookieHeader(token, seconds) });
    } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/auth/logout' && method === 'POST') {
    try { const token = cookieMap(request).eq_session; if (token) await db.query('DELETE FROM sessions WHERE token_hash=$1', [sha(token)]); return json(200, { ok: true }, { 'Set-Cookie': clearSessionCookieHeader() }); } catch (e) { return json(200, { ok: true }, { 'Set-Cookie': clearSessionCookieHeader() }); }
  }
  if (url.pathname === '/api/courses' && method === 'GET') {
    try { const level = url.searchParams.get('level'), params = [], where = ["c.status='published'"]; if (level) { params.push(level); where.push(`c.education_level=$${params.length}`); } const r = await db.query(`SELECT c.id,c.title,c.slug,c.subtitle,c.description,c.education_level,c.category,c.price_cents,c.currency,c.quality_score,u.name instructor_name,(SELECT count(*)::int FROM enrolments e WHERE e.course_id=c.id) students,(SELECT round(avg(r.rating)::numeric,1) FROM reviews r WHERE r.course_id=c.id AND r.status='published') rating,(SELECT count(*)::int FROM reviews r WHERE r.course_id=c.id AND r.status='published') review_count FROM courses c LEFT JOIN users u ON u.id=c.instructor_id WHERE ${where.join(' AND ')} ORDER BY c.created_at DESC LIMIT 100`, params); return json(200, { courses: r.rows }); } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/public/stats' && method === 'GET') {
    try { const r = await db.query(`SELECT (SELECT count(*)::int FROM users WHERE role='student' AND status='active') students,(SELECT count(*)::int FROM users WHERE role='instructor' AND status='active') instructors,(SELECT count(*)::int FROM courses WHERE status='published') courses`); return json(200, r.rows[0]); } catch (e) { return dbFail(e); }
  }
  const publicCourse = url.pathname.match(/^\/api\/courses\/([0-9a-f-]{36})$/i);
  if (publicCourse && method === 'GET') {
    try { const r = await db.query(`SELECT c.*,u.name instructor_name,(SELECT count(*)::int FROM enrolments e WHERE e.course_id=c.id) students,(SELECT round(avg(rv.rating)::numeric,1) FROM reviews rv WHERE rv.course_id=c.id AND rv.status='published') rating,(SELECT count(*)::int FROM lessons l WHERE l.course_id=c.id) lesson_count FROM courses c LEFT JOIN users u ON u.id=c.instructor_id WHERE c.id=$1 AND c.status='published'`, [publicCourse[1]]); if (!r.rowCount) return json(404, { message: 'Course not found.' }); const rv = await db.query(`SELECT r.rating,r.review,r.created_at,u.name student_name FROM reviews r JOIN users u ON u.id=r.student_id WHERE r.course_id=$1 AND r.status='published' ORDER BY r.created_at DESC LIMIT 50`, [publicCourse[1]]); return json(200, { course: r.rows[0], reviews: rv.rows }); } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/wishlist' && method === 'GET') {
    const u = await requireRole(request, ['student']); if (u instanceof Response) return u;
    try { const r = await db.query(`SELECT c.id,c.title,c.price_cents,c.currency,c.education_level,c.category,i.name instructor_name FROM wishlist w JOIN courses c ON c.id=w.course_id LEFT JOIN users i ON i.id=c.instructor_id WHERE w.user_id=$1 AND c.status='published' ORDER BY w.created_at DESC`, [u.id]); return json(200, { courses: r.rows }); } catch (e) { return dbFail(e); }
  }
  const wish = url.pathname.match(/^\/api\/wishlist\/([0-9a-f-]{36})$/i);
  if (wish && method === 'POST') {
    const u = await requireRole(request, ['student']); if (u instanceof Response) return u;
    try { const c = await db.query("SELECT 1 FROM courses WHERE id=$1 AND status='published'", [wish[1]]); if (!c.rowCount) return json(404, { message: 'Course not found.' }); await db.query('INSERT INTO wishlist(user_id,course_id) VALUES($1,$2) ON CONFLICT DO NOTHING', [u.id, wish[1]]); return json(200, { ok: true }); } catch (e) { return dbFail(e); }
  }
  if (wish && method === 'DELETE') {
    const u = await requireRole(request, ['student']); if (u instanceof Response) return u;
    try { await db.query('DELETE FROM wishlist WHERE user_id=$1 AND course_id=$2', [u.id, wish[1]]); return json(200, { ok: true }); } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/instructor/courses' && method === 'GET') {
    const u = await requireRole(request, ['instructor', 'admin']); if (u instanceof Response) return u;
    try { const r = await db.query(`SELECT * FROM courses WHERE instructor_id=$1 ORDER BY updated_at DESC`, [u.id]); return json(200, { courses: r.rows }); } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/instructor/courses' && method === 'POST') {
    const u = await requireRole(request, ['instructor', 'admin']); if (u instanceof Response) return u;
    try { const x = await readJson(request), title = String(x.title || '').trim(); if (title.length < 4) return json(400, { message: 'Course title is required.' }); const id = uuid(), slug = slugify(title) + '-' + id.slice(0, 8); await db.query(`INSERT INTO courses(id,instructor_id,title,slug,subtitle,description,education_level,category,price_cents,currency,status) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,'USD','draft')`, [id, u.id, title, slug, String(x.subtitle || ''), String(x.description || ''), String(x.educationLevel || 'professional'), String(x.category || ''), Math.max(0, Math.round(Number(x.price || 0) * 100))]); await audit(u, 'course.created', 'course', id, { title }); return json(201, { id, slug }); } catch (e) { return dbFail(e); }
  }
  const instructorCourseMatch = url.pathname.match(/^\/api\/instructor\/courses\/([0-9a-f-]{36})$/i);
  if (instructorCourseMatch && method === 'PATCH') {
    const u = await requireRole(request, ['instructor', 'admin']); if (u instanceof Response) return u;
    try {
      const courseId = instructorCourseMatch[1];
      if (!(await ownsCourse(u, courseId))) return json(403, { message: 'You do not manage this course.' });
      const current = await db.query('SELECT * FROM courses WHERE id=$1', [courseId]);
      if (!current.rowCount) return json(404, { message: 'Course not found.' });
      const x = await readJson(request);
      const title = String(x.title ?? current.rows[0].title).trim();
      const category = String(x.category ?? current.rows[0].category ?? '').trim();
      const educationLevel = String(x.educationLevel ?? current.rows[0].education_level).trim();
      const subtitle = String(x.subtitle ?? current.rows[0].subtitle ?? '').trim().slice(0, 200);
      const description = String(x.description ?? current.rows[0].description ?? '').trim().slice(0, 8000);
      let priceCents = Number(current.rows[0].price_cents || 0);
      if (x.price !== undefined) priceCents = Math.max(0, Math.round(Number(x.price || 0) * 100));
      if (title.length < 4) return json(400, { message: 'Course title must contain at least 4 characters.' });
      await db.query(`UPDATE courses SET title=$2,category=$3,education_level=$4,price_cents=$5,subtitle=$6,description=$7,updated_at=now() WHERE id=$1`, [courseId, title, category, educationLevel, priceCents, subtitle, description]);
      await audit(u, 'course.updated', 'course', courseId, { title, category, educationLevel, priceCents });
      return json(200, { ok: true, id: courseId });
    } catch (e) { return dbFail(e); }
  }
  if (instructorCourseMatch && method === 'DELETE') {
    const u = await requireRole(request, ['instructor', 'admin']); if (u instanceof Response) return u;
    try {
      const courseId = instructorCourseMatch[1];
      if (!(await ownsCourse(u, courseId))) return json(403, { message: 'You do not manage this course.' });
      const course = await db.query('SELECT * FROM courses WHERE id=$1', [courseId]);
      if (!course.rowCount) return json(404, { message: 'Course not found.' });
      const history = await db.query(`SELECT (SELECT count(*) FROM enrolments WHERE course_id=$1)::int AS enrolments,(SELECT count(*) FROM order_items WHERE course_id=$1)::int AS orders`, [courseId]);
      const hasHistory = Number(history.rows[0].enrolments) > 0 || Number(history.rows[0].orders) > 0;
      if (hasHistory) {
        await db.query(`UPDATE courses SET status='archived',updated_at=now() WHERE id=$1`, [courseId]);
        await audit(u, 'course.archived', 'course', courseId, {});
        return json(200, { ok: true, archived: true, message: 'This course has student or transaction history, so it was archived instead of permanently deleted.' });
      }
      await db.query('DELETE FROM courses WHERE id=$1', [courseId]);
      await audit(u, 'course.deleted', 'course', courseId, {});
      return json(200, { ok: true, deleted: true, message: 'Course permanently deleted.' });
    } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/enrolments' && method === 'GET') {
    const u = await requireRole(request, ['student']); if (u instanceof Response) return u;
    try { const r = await db.query(`SELECT e.*,c.title,c.slug,c.education_level,c.category FROM enrolments e JOIN courses c ON c.id=e.course_id WHERE e.student_id=$1 ORDER BY e.enrolled_at DESC`, [u.id]); return json(200, { enrolments: r.rows }); } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/conversations' && method === 'GET') {
    const u = await requireRole(request, ['student', 'instructor']); if (u instanceof Response) return u;
    try { const col = u.role === 'student' ? 'student_id' : 'instructor_id'; const r = await db.query(`SELECT c.id,c.course_id,c.updated_at,co.title course_title,s.id student_id,s.name student_name,s.email student_email,i.id instructor_id,i.name instructor_name,i.email instructor_email,(SELECT body FROM messages m WHERE m.conversation_id=c.id ORDER BY created_at DESC LIMIT 1) last_message FROM conversations c JOIN users s ON s.id=c.student_id JOIN users i ON i.id=c.instructor_id LEFT JOIN courses co ON co.id=c.course_id WHERE c.${col}=$1 ORDER BY c.updated_at DESC`, [u.id]); return json(200, { conversations: r.rows }); } catch (e) { return dbFail(e); }
  }
  const msgMatch = url.pathname.match(/^\/api\/conversations\/([0-9a-f-]{36})\/messages$/i);
  if (msgMatch && method === 'GET') {
    const u = await requireRole(request, ['student', 'instructor']); if (u instanceof Response) return u;
    try { const c = await db.query('SELECT * FROM conversations WHERE id=$1 AND (student_id=$2 OR instructor_id=$2)', [msgMatch[1], u.id]); if (!c.rowCount) return json(404, { message: 'Conversation not found.' }); const r = await db.query(`SELECT m.id,m.body,m.created_at,m.read_at,m.sender_id,u.name sender_name,u.role sender_role FROM messages m JOIN users u ON u.id=m.sender_id WHERE m.conversation_id=$1 ORDER BY m.created_at`, [msgMatch[1]]); await db.query('UPDATE messages SET read_at=COALESCE(read_at,now()) WHERE conversation_id=$1 AND sender_id<>$2', [msgMatch[1], u.id]); return json(200, { messages: r.rows }); } catch (e) { return dbFail(e); }
  }
  if (msgMatch && method === 'POST') {
    const u = await requireRole(request, ['student', 'instructor']); if (u instanceof Response) return u;
    try {
      const x = await readJson(request), body = String(x.body || '').trim(); if (!body || body.length > 5000) return json(400, { message: 'Message must contain between 1 and 5000 characters.' });
      const c = await db.query('SELECT * FROM conversations WHERE id=$1 AND (student_id=$2 OR instructor_id=$2)', [msgMatch[1], u.id]); if (!c.rowCount) return json(404, { message: 'Conversation not found.' });
      const id = uuid(); await db.query('INSERT INTO messages(id,conversation_id,sender_id,body) VALUES($1,$2,$3,$4)', [id, msgMatch[1], u.id, body]); await db.query('UPDATE conversations SET updated_at=now() WHERE id=$1', [msgMatch[1]]);
      const cv = c.rows[0], recipient = u.role === 'student' ? cv.instructor_id : cv.student_id;
      await notifyUser(recipient, { type: 'message', title: `New message from ${u.name}`, body: body.slice(0, 240), link: u.role === 'student' ? '/instructor-messages.html' : '/messages.html', emailSubject: `New EduQuinn message from ${u.name}`, emailText: `${u.name} sent you a message on EduQuinn:\n\n${body}\n\nSign in to reply.`, channel: 'messages' });
      return json(201, { id });
    } catch (e) { return dbFail(e); }
  }

  // ---- Course media: Supabase Storage signed-URL flow (replaces local-disk
  // MEDIA_ROOT streaming). Upload is now 3 steps — see public/media.js:
  //   1. PUT this route -> signed upload ticket
  //   2. browser PUTs file bytes directly to Supabase Storage
  //   3. POST .../assets/:assetId/confirm -> registers the course_assets row
  const assetsMatch = url.pathname.match(/^\/api\/instructor\/courses\/([0-9a-f-]{36})\/assets$/i);
  if (assetsMatch && method === 'GET') { const u = await requireRole(request, ['instructor', 'admin']); if (u instanceof Response) return u; try { if (!(await ownsCourse(u, assetsMatch[1]))) return json(403, { message: 'You do not manage this course.' }); const r = await db.query('SELECT id,kind,original_name,mime_type,size_bytes,created_at FROM course_assets WHERE course_id=$1 ORDER BY created_at DESC', [assetsMatch[1]]); return json(200, { assets: r.rows }); } catch (e) { return dbFail(e); } }
  if (assetsMatch && method === 'PUT') {
    const u = await requireRole(request, ['instructor', 'admin']); if (u instanceof Response) return u;
    try {
      const courseId = assetsMatch[1]; if (!(await ownsCourse(u, courseId))) return json(403, { message: 'You do not manage this course.' });
      const name = decodeURIComponent(url.searchParams.get('name') || 'upload.bin').slice(0, 255), kind = String(url.searchParams.get('kind') || 'other').toLowerCase(), allowed = ['video', 'document', 'audio', 'thumbnail', 'other'];
      if (!allowed.includes(kind)) return json(400, { message: 'Unsupported media type.' });
      const mime = String(request.headers.get('content-type') || 'application/octet-stream').split(';')[0].slice(0, 160);
      const assetId = uuid(), stored = assetId + safeExt(name, mime);
      const ticket = await storage.createUploadTicket(stored);
      return json(201, { assetId, uploadUrl: ticket.uploadUrl, token: ticket.token, path: ticket.path, kind, originalName: name, mimeType: mime });
    } catch (e) { return e.status ? json(e.status, { message: e.message }) : dbFail(e); }
  }
  const confirmAssetMatch = url.pathname.match(/^\/api\/instructor\/courses\/([0-9a-f-]{36})\/assets\/([0-9a-f-]{36})\/confirm$/i);
  if (confirmAssetMatch && method === 'POST') {
    const u = await requireRole(request, ['instructor', 'admin']); if (u instanceof Response) return u;
    try {
      const [, courseId, assetId] = confirmAssetMatch; if (!(await ownsCourse(u, courseId))) return json(403, { message: 'You do not manage this course.' });
      const x = await readJson(request), name = String(x.name || 'upload.bin').slice(0, 255), kind = String(x.kind || 'other').toLowerCase(), allowed = ['video', 'document', 'audio', 'thumbnail', 'other'];
      if (!allowed.includes(kind)) return json(400, { message: 'Unsupported media type.' });
      const mime = String(x.mimeType || 'application/octet-stream').slice(0, 160), storedName = String(x.storedName || ''), size = Math.max(0, Math.round(Number(x.sizeBytes || 0)));
      if (!storedName) return json(400, { message: 'Missing uploaded file reference.' });
      if (!(await storage.objectExists(storedName))) return json(409, { message: 'The upload did not complete — please try again.' });
      await db.query('INSERT INTO course_assets(id,course_id,uploaded_by,kind,original_name,stored_name,mime_type,size_bytes) VALUES($1,$2,$3,$4,$5,$6,$7,$8)', [assetId, courseId, u.id, kind, name, storedName, mime, size]);
      await audit(u, 'media.uploaded', 'course', courseId, { assetId, name, kind, size });
      return json(201, { asset: { id: assetId, courseId, kind, originalName: name, mimeType: mime, sizeBytes: size, url: `/api/media/${assetId}` } });
    } catch (e) { return e.status ? json(e.status, { message: e.message }) : dbFail(e); }
  }
  const mediaMatch = url.pathname.match(/^\/api\/media\/([0-9a-f-]{36})$/i);
  if (mediaMatch && method === 'GET') {
    try {
      const r = await db.query('SELECT * FROM course_assets WHERE id=$1', [mediaMatch[1]]), a = r.rows[0]; if (!a) return json(404, { message: 'Media not found.' });
      let user = null; try { user = await currentUser(request); } catch {}
      let preview = false; if (a.kind === 'thumbnail') preview = true; else { const pr = await db.query('SELECT 1 FROM lessons WHERE asset_id=$1 AND is_preview=true LIMIT 1', [a.id]); preview = !!pr.rowCount; }
      if (!preview && !(await canReadCourseMedia(user, a.course_id))) return json(user ? 403 : 401, { message: 'Sign in with course access to view this media.' });
      // The browser (or <video>/<img> tag) follows this redirect transparently, including
      // re-issuing Range requests at the signed URL — Supabase Storage serves those directly,
      // so video scrubbing keeps working without this function proxying any bytes.
      const signedUrl = await storage.createReadUrl(a.stored_name, 300);
      return new Response(null, { status: 302, headers: { ...securityFor(), Location: signedUrl, 'Cache-Control': 'private, max-age=0' } });
    } catch (e) { return e.status ? json(e.status, { message: e.message }) : dbFail(e); }
  }

  const lessonsMatch = url.pathname.match(/^\/api\/instructor\/courses\/([0-9a-f-]{36})\/lessons$/i);
  if (lessonsMatch && method === 'GET') { const u = await requireRole(request, ['instructor', 'admin']); if (u instanceof Response) return u; try { if (!(await ownsCourse(u, lessonsMatch[1]))) return json(403, { message: 'You do not manage this course.' }); const r = await db.query('SELECT l.*,a.original_name asset_name,a.kind asset_kind FROM lessons l LEFT JOIN course_assets a ON a.id=l.asset_id WHERE l.course_id=$1 ORDER BY l.position,l.created_at', [lessonsMatch[1]]); return json(200, { lessons: r.rows }); } catch (e) { return dbFail(e); } }
  if (lessonsMatch && method === 'POST') {
    const u = await requireRole(request, ['instructor', 'admin']); if (u instanceof Response) return u;
    try {
      const courseId = lessonsMatch[1];
      if (!(await ownsCourse(u, courseId))) return json(403, { message: 'You do not manage this course.' });
      const x = await readJson(request, 250000);
      const title = String(x.title || '').trim();
      const lessonType = ['video', 'article', 'quiz', 'assignment', 'resource'].includes(x.lessonType) ? x.lessonType : 'video';
      if (title.length < 2) return json(400, { message: 'Lesson title is required.' });
      const lessonId = uuid();
      let body = String(x.body || '');
      if (lessonType === 'assignment') {
        const assignment = x.assignment || {};
        body = JSON.stringify({ instructions: String(assignment.instructions || '').trim(), dueDate: assignment.dueDate || null, maxMarks: Math.max(1, Number(assignment.maxMarks || 100)) });
        if (!assignment.instructions) return json(400, { message: 'Assignment instructions are required.' });
      }
      await db.withTransaction(async (client) => {
        await client.query(
          `INSERT INTO lessons(id,course_id,title,lesson_type,position,asset_id,body,duration_seconds,is_preview,metadata) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10::jsonb)`,
          [lessonId, courseId, title, lessonType, Number(x.position || 0), x.assetId || null, body, Math.max(0, Number(x.durationSeconds || 0)), !!x.isPreview, JSON.stringify(lessonType === 'assignment' ? x.assignment || {} : {})]
        );
        if (lessonType === 'quiz') {
          const quiz = x.quiz || {};
          const questions = Array.isArray(quiz.questions) ? quiz.questions : [];
          if (!questions.length) throw Object.assign(new Error('Add at least one quiz question.'), { status: 400 });
          const assessmentId = uuid();
          const passMark = Math.max(0, Math.min(100, Number(quiz.passMark || 70)));
          const maxAttempts = Math.max(1, Math.min(100, Number(quiz.maxAttempts || 3)));
          const timeLimit = Math.max(0, Math.min(1440, Number(quiz.timeLimitMinutes || 0)));
          await client.query(
            `INSERT INTO assessments(id,course_id,lesson_id,title,pass_mark,instructions,max_attempts,time_limit_minutes,shuffle_questions,show_answers) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)`,
            [assessmentId, courseId, lessonId, title, passMark, String(quiz.instructions || ''), maxAttempts, timeLimit, !!quiz.shuffleQuestions, quiz.showAnswers !== false]
          );
          for (let i = 0; i < questions.length; i++) {
            const q = questions[i] || {};
            const prompt = String(q.prompt || '').trim();
            if (!prompt) throw Object.assign(new Error('Every quiz question requires question text.'), { status: 400 });
            const type = ['single', 'multiple', 'true_false', 'short_answer'].includes(q.type) ? q.type : 'single';
            const rawOptions = Array.isArray(q.options) ? q.options : [];
            const options = rawOptions.map((option, index) => ({ text: String(option.text || '').trim(), position: Number(option.position || index + 1) })).filter((option) => option.text);
            const correctAnswers = rawOptions.map((option, index) => ({ index, text: String(option.text || '').trim(), correct: !!option.correct })).filter((option) => option.correct && option.text).map((option) => ({ index: option.index, text: option.text }));
            if (type !== 'short_answer' && options.length < 2) throw Object.assign(new Error('Each quiz question needs at least two answers.'), { status: 400 });
            if (!correctAnswers.length) throw Object.assign(new Error(type === 'short_answer' ? 'Each short-answer question requires an accepted answer.' : 'Each quiz question requires a correct answer.'), { status: 400 });
            if (['single', 'true_false'].includes(type) && correctAnswers.length !== 1) throw Object.assign(new Error('Single-answer questions must have exactly one correct answer.'), { status: 400 });
            await client.query(
              `INSERT INTO assessment_questions(id,assessment_id,prompt,options,correct_index,position,question_type,correct_answers,marks,explanation) VALUES($1,$2,$3,$4::jsonb,$5,$6,$7,$8::jsonb,$9,$10)`,
              [uuid(), assessmentId, prompt, JSON.stringify(options), correctAnswers.length === 1 ? correctAnswers[0].index : null, Number(q.position || i + 1), type, JSON.stringify(correctAnswers), Math.max(1, Number(q.marks || 1)), String(q.explanation || '')]
            );
          }
        }
      });
      await audit(u, 'lesson.created', 'course', courseId, { lessonId, lessonType, title });
      return json(201, { id: lessonId, lessonType });
    } catch (e) { return e.status ? json(e.status, { message: e.message }) : dbFail(e); }
  }
  const learningMatch = url.pathname.match(/^\/api\/learning\/courses\/([0-9a-f-]{36})$/i);
  if (learningMatch && method === 'GET') { const u = await requireRole(request, ['student']); if (u instanceof Response) return u; try { const enr = await db.query('SELECT 1 FROM enrolments WHERE student_id=$1 AND course_id=$2', [u.id, learningMatch[1]]); if (!enr.rowCount) return json(403, { message: 'You are not enrolled in this course.' }); const r = await db.query(`SELECT l.id,l.title,l.lesson_type,l.position,l.duration_seconds,l.is_preview,l.asset_id,l.body,COALESCE(p.completed,false) completed,COALESCE(p.position_seconds,0) position_seconds FROM lessons l LEFT JOIN lesson_progress p ON p.lesson_id=l.id AND p.student_id=$1 WHERE l.course_id=$2 ORDER BY l.position,l.created_at`, [u.id, learningMatch[1]]); return json(200, { lessons: r.rows }); } catch (e) { return dbFail(e); } }
  const progressMatch = url.pathname.match(/^\/api\/learning\/lessons\/([0-9a-f-]{36})\/progress$/i);
  if (progressMatch && method === 'POST') { const u = await requireRole(request, ['student']); if (u instanceof Response) return u; try { const l = await db.query('SELECT course_id FROM lessons WHERE id=$1', [progressMatch[1]]); if (!l.rowCount) return json(404, { message: 'Lesson not found.' }); const enr = await db.query('SELECT id FROM enrolments WHERE student_id=$1 AND course_id=$2', [u.id, l.rows[0].course_id]); if (!enr.rowCount) return json(403, { message: 'You are not enrolled in this course.' }); const x = await readJson(request); await db.query(`INSERT INTO lesson_progress(student_id,lesson_id,completed,position_seconds,completed_at) VALUES($1,$2,$3,$4,CASE WHEN $3 THEN now() ELSE NULL END) ON CONFLICT(student_id,lesson_id) DO UPDATE SET completed=EXCLUDED.completed,position_seconds=EXCLUDED.position_seconds,updated_at=now(),completed_at=CASE WHEN EXCLUDED.completed THEN COALESCE(lesson_progress.completed_at,now()) ELSE NULL END`, [u.id, progressMatch[1], !!x.completed, Math.max(0, Number(x.positionSeconds || 0))]); const pct = await db.query(`SELECT CASE WHEN count(*)=0 THEN 0 ELSE round(100.0*count(*) FILTER (WHERE p.completed)/count(*)) END pct FROM lessons l LEFT JOIN lesson_progress p ON p.lesson_id=l.id AND p.student_id=$1 WHERE l.course_id=$2`, [u.id, l.rows[0].course_id]); await db.query('UPDATE enrolments SET progress=$1,completed_at=CASE WHEN $1=100 THEN COALESCE(completed_at,now()) ELSE NULL END WHERE student_id=$2 AND course_id=$3', [Number(pct.rows[0].pct || 0), u.id, l.rows[0].course_id]); return json(200, { progress: Number(pct.rows[0].pct || 0) }); } catch (e) { return dbFail(e); } }
  const assessmentCourseMatch = url.pathname.match(/^\/api\/instructor\/courses\/([0-9a-f-]{36})\/assessments$/i);
  if (assessmentCourseMatch && method === 'POST') { const u = await requireRole(request, ['instructor', 'admin']); if (u instanceof Response) return u; try { const courseId = assessmentCourseMatch[1]; if (!(await ownsCourse(u, courseId))) return json(403, { message: 'You do not manage this course.' }); const x = await readJson(request, 150000), title = String(x.title || '').trim(), questions = Array.isArray(x.questions) ? x.questions : []; if (title.length < 2 || !questions.length) return json(400, { message: 'Assessment title and at least one question are required.' }); const id = uuid(); await db.withTransaction(async (client) => { await client.query('INSERT INTO assessments(id,course_id,title,pass_mark) VALUES($1,$2,$3,$4)', [id, courseId, title, Math.max(0, Math.min(100, Number(x.passMark || 50)))]); for (let i = 0; i < questions.length; i++) { const q = questions[i] || {}, options = Array.isArray(q.options) ? q.options.slice(0, 10) : [], correctIndex = Number.isInteger(q.correctIndex) ? q.correctIndex : null, correctAnswers = correctIndex !== null && options[correctIndex] ? [{ index: correctIndex, text: String(options[correctIndex].text || options[correctIndex] || '') }] : []; await client.query('INSERT INTO assessment_questions(id,assessment_id,prompt,options,correct_index,position,question_type,correct_answers) VALUES($1,$2,$3,$4::jsonb,$5,$6,$7,$8::jsonb)', [uuid(), id, String(q.prompt || 'Question ' + (i + 1)), JSON.stringify(options), correctIndex, i, 'single', JSON.stringify(correctAnswers)]); } }); return json(201, { id }); } catch (e) { return e.status ? json(e.status, { message: e.message }) : dbFail(e); } }
  const learningAssessMatch = url.pathname.match(/^\/api\/learning\/courses\/([0-9a-f-]{36})\/assessments$/i);
  if (learningAssessMatch && method === 'GET') { const u = await requireRole(request, ['student']); if (u instanceof Response) return u; try { const e = await db.query('SELECT 1 FROM enrolments WHERE student_id=$1 AND course_id=$2', [u.id, learningAssessMatch[1]]); if (!e.rowCount) return json(403, { message: 'You are not enrolled in this course.' }); const a = await db.query('SELECT id,lesson_id,title,pass_mark,instructions,max_attempts,time_limit_minutes,shuffle_questions,show_answers FROM assessments WHERE course_id=$1 ORDER BY created_at', [learningAssessMatch[1]]); for (const item of a.rows) { const q = await db.query('SELECT id,prompt,options,position,question_type,marks FROM assessment_questions WHERE assessment_id=$1 ORDER BY position', [item.id]); item.questions = q.rows; const att = await db.query('SELECT count(*)::int n FROM assessment_attempts WHERE assessment_id=$1 AND student_id=$2', [item.id, u.id]); item.attemptsUsed = Number(att.rows[0].n); } return json(200, { assessments: a.rows }); } catch (e) { return dbFail(e); } }
  const submitAssessMatch = url.pathname.match(/^\/api\/learning\/assessments\/([0-9a-f-]{36})\/submit$/i);
  if (submitAssessMatch && method === 'POST') {
    const u = await requireRole(request, ['student']); if (u instanceof Response) return u;
    try {
      const a = await db.query('SELECT * FROM assessments WHERE id=$1', [submitAssessMatch[1]]); if (!a.rowCount) return json(404, { message: 'Assessment not found.' });
      const e = await db.query('SELECT 1 FROM enrolments WHERE student_id=$1 AND course_id=$2', [u.id, a.rows[0].course_id]); if (!e.rowCount) return json(403, { message: 'You are not enrolled in this course.' });
      const maxAttempts = Number(a.rows[0].max_attempts || 3); const used = await db.query('SELECT count(*)::int n FROM assessment_attempts WHERE assessment_id=$1 AND student_id=$2', [submitAssessMatch[1], u.id]); if (Number(used.rows[0].n) >= maxAttempts) return json(409, { message: `You have used all ${maxAttempts} attempt${maxAttempts === 1 ? '' : 's'} for this assessment.` });
      const x = await readJson(request), answers = Array.isArray(x.answers) ? x.answers : [], q = await db.query('SELECT correct_index,correct_answers,question_type,marks,position FROM assessment_questions WHERE assessment_id=$1 ORDER BY position', [submitAssessMatch[1]]);
      let earned = 0, possible = 0;
      const results = q.rows.map((v, i) => {
        const marks = Math.max(1, Number(v.marks || 1)); possible += marks;
        const given = answers[i]; const type = v.question_type || 'single'; let ok = false;
        if (type === 'multiple') { const correctIdx = (v.correct_answers || []).map((opt) => Number(opt.index)).sort((p, n) => p - n); const givenIdx = Array.isArray(given) ? [...new Set(given.map(Number))].filter((n) => Number.isInteger(n)).sort((p, n) => p - n) : []; ok = correctIdx.length > 0 && correctIdx.length === givenIdx.length && correctIdx.every((val, idx) => val === givenIdx[idx]); }
        else if (type === 'short_answer') { const accepted = (v.correct_answers || []).map((opt) => String(opt.text || '').trim().toLowerCase()).filter(Boolean); ok = accepted.includes(String(given || '').trim().toLowerCase()); }
        else { ok = v.correct_index !== null && v.correct_index !== undefined && Number(given) === Number(v.correct_index); }
        if (ok) earned += marks;
        return ok;
      });
      const score = possible ? Math.round((earned * 100) / possible) : 0, passed = score >= Number(a.rows[0].pass_mark); const id = uuid(); await db.query('INSERT INTO assessment_attempts(id,assessment_id,student_id,answers,score,passed) VALUES($1,$2,$3,$4::jsonb,$5,$6)', [id, submitAssessMatch[1], u.id, JSON.stringify(answers), score, passed]);
      return json(201, { attemptId: id, score, passed, passMark: Number(a.rows[0].pass_mark), perQuestion: a.rows[0].show_answers !== false ? results : undefined, attemptsUsed: Number(used.rows[0].n) + 1, maxAttempts });
    } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/certificates/issue' && method === 'POST') { const u = await requireRole(request, ['student']); if (u instanceof Response) return u; try { const x = await readJson(request), courseId = String(x.courseId || ''); const e = await db.query('SELECT progress FROM enrolments WHERE student_id=$1 AND course_id=$2', [u.id, courseId]); if (!e.rowCount || Number(e.rows[0].progress) < 100) return json(409, { message: 'Complete all lessons before requesting a certificate.' }); const existing = await db.query('SELECT * FROM certificates WHERE student_id=$1 AND course_id=$2', [u.id, courseId]); if (existing.rowCount) return json(200, { certificate: existing.rows[0] }); const id = uuid(), code = 'EQ-CERT-' + new Date().getFullYear() + '-' + crypto.randomBytes(5).toString('hex').toUpperCase(); const r = await db.query('INSERT INTO certificates(id,certificate_code,student_id,course_id) VALUES($1,$2,$3,$4) RETURNING *', [id, code, u.id, courseId]); return json(201, { certificate: r.rows[0] }); } catch (e) { return dbFail(e); } }

  if (url.pathname === '/api/commerce/config' && method === 'GET') { const paynowReady = !!process.env.PAYNOW_INTEGRATION_ID && !!process.env.PAYNOW_INTEGRATION_KEY; return json(200, { currency: 'USD', instructorSharePercent: INSTRUCTOR_SHARE_PERCENT, paymentSandbox: PAYMENT_SANDBOX, methods: [{ id: 'card', label: 'Visa / Mastercard (via Paynow)', configured: paynowReady }, { id: 'paynow', label: 'EcoCash / OneMoney (via Paynow)', configured: paynowReady }, { id: 'bank_transfer', label: 'Bank transfer', configured: true }] }); }
  if (url.pathname === '/api/coupons/validate' && method === 'POST') { const u = await requireRole(request, ['student']); if (u instanceof Response) return u; try { const x = await readJson(request), result = await couponFor(x.code, u.id, cents(x.subtotalCents)); if (result.error) return json(400, { message: result.error }); return json(200, { code: result.code, discountCents: result.discount }); } catch (e) { return dbFail(e); } }
  if (url.pathname === '/api/orders' && method === 'POST') {
    const u = await requireRole(request, ['student']); if (u instanceof Response) return u;
    try {
      const x = await readJson(request), ids = Array.isArray(x.courseIds) ? [...new Set(x.courseIds.map(String).filter((v) => /^[0-9a-f-]{36}$/i.test(v)))] : [], method2 = String(x.paymentMethod || '');
      if (!ids.length) return json(400, { message: 'Select at least one database-backed course.' });
      if (!['card', 'paynow', 'bank_transfer'].includes(method2)) return json(400, { message: 'Select a supported payment method.' });
      const q = await db.query(`SELECT id,title,price_cents,currency FROM courses WHERE id=ANY($1::uuid[]) AND status='published'`, [ids]);
      if (q.rowCount !== ids.length) return json(400, { message: 'One or more courses are unavailable.' });
      const subtotal = q.rows.reduce((a, c) => a + Number(c.price_cents), 0), cp = await couponFor(x.couponCode, u.id, subtotal);
      if (cp.error) return json(400, { message: cp.error });
      const discount = cp.discount || 0, total = Math.max(0, subtotal - discount), id = uuid(), status = total === 0 ? 'paid' : method2 === 'bank_transfer' ? 'awaiting_payment' : 'pending_gateway';
      await db.withTransaction(async (client) => {
        await client.query(`INSERT INTO orders(id,student_id,status,payment_method,currency,total_cents,subtotal_cents,discount_cents,coupon_code) VALUES($1,$2,$3,$4,'USD',$5,$6,$7,$8)`, [id, u.id, status, method2, total, subtotal, discount, cp.code]);
        for (const c of q.rows) await client.query('INSERT INTO order_items(id,order_id,course_id,unit_price_cents) VALUES($1,$2,$3,$4)', [uuid(), id, c.id, c.price_cents]);
        if (cp.coupon) await client.query('INSERT INTO coupon_redemptions(coupon_id,user_id,order_id) VALUES($1,$2,$3)', [cp.coupon.id, u.id, id]);
        const txid = uuid(), provider = method2 === 'bank_transfer' ? 'manual_bank' : method2;
        await client.query("INSERT INTO payment_transactions(id,order_id,provider,amount_cents,currency,status) VALUES($1,$2,$3,$4,'USD',$5)", [txid, id, provider, total, status === 'paid' ? 'paid' : 'pending']);
      });
      let order;
      if (total === 0) order = await markOrderPaid(id, 'FREE');
      else if (PAYMENT_SANDBOX && method2 !== 'bank_transfer') order = await markOrderPaid(id, 'SANDBOX-' + Date.now());
      else order = (await db.query('SELECT * FROM orders WHERE id=$1', [id])).rows[0];
      let paynowRedirectUrl = null, paynowError = null;
      if (order.status === 'pending_gateway') { const result = await startPaynowPayment(order, q.rows); if (result.started) paynowRedirectUrl = result.redirectUrl; else paynowError = result.reason; }
      await audit(u, 'order.created', 'order', id, { paymentMethod: method2, totalCents: total, coupon: cp.code || null });
      return json(201, { order, requiresGateway: order.status === 'pending_gateway', paynowRedirectUrl, paynowError, bankInstructions: method2 === 'bank_transfer' ? process.env.BANK_TRANSFER_INSTRUCTIONS || 'Contact EduQuinn support for bank transfer details.' : null });
    } catch (e) { return e.status ? json(e.status, { message: e.message }) : dbFail(e); }
  }
  const retryPaynow = url.pathname.match(/^\/api\/orders\/([0-9a-f-]{36})\/pay\/paynow$/i);
  if (retryPaynow && method === 'POST') {
    const u = await requireRole(request, ['student']); if (u instanceof Response) return u;
    try {
      const o = await db.query('SELECT * FROM orders WHERE id=$1 AND student_id=$2', [retryPaynow[1], u.id]); if (!o.rowCount) return json(404, { message: 'Order not found.' });
      if (o.rows[0].status !== 'pending_gateway') return json(409, { message: 'This order is not awaiting online payment.' });
      const items = await db.query('SELECT oi.unit_price_cents,c.title FROM order_items oi JOIN courses c ON c.id=oi.course_id WHERE oi.order_id=$1', [retryPaynow[1]]);
      const result = await startPaynowPayment(o.rows[0], items.rows);
      if (!result.started) return json(502, { message: 'Online payment could not be started: ' + result.reason + '. You can try again shortly, or choose bank transfer.' });
      return json(200, { redirectUrl: result.redirectUrl });
    } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/payments/paynow/webhook' && method === 'POST') return paynowWebhook(request);
  const refreshOrder = url.pathname.match(/^\/api\/orders\/([0-9a-f-]{36})\/refresh-status$/i);
  if (refreshOrder && method === 'POST') {
    const u = await requireRole(request, ['student']); if (u instanceof Response) return u;
    try {
      const o = await db.query('SELECT * FROM orders WHERE id=$1 AND student_id=$2', [refreshOrder[1], u.id]); if (!o.rowCount) return json(404, { message: 'Order not found.' });
      if (o.rows[0].status === 'pending_gateway') {
        const tx = await db.query("SELECT payload->>'pollUrl' as poll_url,provider_reference FROM payment_transactions WHERE order_id=$1 ORDER BY created_at DESC LIMIT 1", [refreshOrder[1]]);
        const pollUrl = tx.rows[0]?.poll_url; const client = getPaynowClient();
        if (pollUrl && client) { try { const polled = await client.pollTransaction(pollUrl); const paidNow = typeof polled.paid === 'function' ? polled.paid() : ['paid', 'awaiting delivery', 'delivered'].includes(String(polled.status || '').toLowerCase()); if (paidNow) await markOrderPaid(refreshOrder[1], polled.paynowreference || polled.paynowReference || ''); } catch (e) { console.error('paynow poll failed', refreshOrder[1], e.message); } }
      }
      const current = await db.query('SELECT * FROM orders WHERE id=$1', [refreshOrder[1]]);
      return json(200, { order: current.rows[0] });
    } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/orders/mine' && method === 'GET') { const u = await requireRole(request, ['student']); if (u instanceof Response) return u; try { const r = await db.query(`SELECT o.*,COALESCE(json_agg(json_build_object('courseId',c.id,'title',c.title,'priceCents',oi.unit_price_cents)) FILTER (WHERE c.id IS NOT NULL),'[]') items FROM orders o LEFT JOIN order_items oi ON oi.order_id=o.id LEFT JOIN courses c ON c.id=oi.course_id WHERE o.student_id=$1 GROUP BY o.id ORDER BY o.created_at DESC`, [u.id]); return json(200, { orders: r.rows }); } catch (e) { return dbFail(e); } }
  const refundReq = url.pathname.match(/^\/api\/orders\/([0-9a-f-]{36})\/refund-request$/i);
  if (refundReq && method === 'POST') { const u = await requireRole(request, ['student']); if (u instanceof Response) return u; try { const o = await db.query("SELECT * FROM orders WHERE id=$1 AND student_id=$2 AND status='paid'", [refundReq[1], u.id]); if (!o.rowCount) return json(404, { message: 'A paid order eligible for refund was not found.' }); const x = await readJson(request), id = uuid(); await db.query('INSERT INTO refunds(id,order_id,requested_by,amount_cents,reason) VALUES($1,$2,$3,$4,$5)', [id, refundReq[1], u.id, o.rows[0].total_cents, String(x.reason || '').slice(0, 2000)]); await db.query("UPDATE orders SET status='refund_requested',updated_at=now() WHERE id=$1", [refundReq[1]]); await audit(u, 'refund.requested', 'order', refundReq[1], {}); return json(201, { refundId: id, status: 'requested' }); } catch (e) { return dbFail(e); } }
  if (url.pathname === '/api/instructor/earnings' && method === 'GET') { const u = await requireRole(request, ['instructor', 'admin']); if (u instanceof Response) return u; try { const instructorId = u.role === 'admin' && url.searchParams.get('instructorId') ? url.searchParams.get('instructorId') : u.id; const sum = await db.query(`SELECT COALESCE(sum(instructor_cents) FILTER(WHERE status='available'),0) available,COALESCE(sum(instructor_cents) FILTER(WHERE status='pending'),0) pending,COALESCE(sum(instructor_cents),0) lifetime FROM instructor_earnings WHERE instructor_id=$1`, [instructorId]); const rows = await db.query(`SELECT e.*,c.title course_title,o.created_at order_date FROM instructor_earnings e JOIN courses c ON c.id=e.course_id JOIN orders o ON o.id=e.order_id WHERE e.instructor_id=$1 ORDER BY e.created_at DESC LIMIT 100`, [instructorId]); const payouts = await db.query('SELECT * FROM instructor_payouts WHERE instructor_id=$1 ORDER BY created_at DESC LIMIT 50', [instructorId]); return json(200, { summary: sum.rows[0], earnings: rows.rows, payouts: payouts.rows, sharePercent: INSTRUCTOR_SHARE_PERCENT }); } catch (e) { return dbFail(e); } }
  if (url.pathname === '/api/admin/orders' && method === 'GET') { const u = await requireRole(request, ['admin']); if (u instanceof Response) return u; try { const r = await db.query(`SELECT o.*,u.name student_name,u.email student_email,(SELECT count(*) FROM refunds f WHERE f.order_id=o.id AND f.status='requested') refund_requests FROM orders o JOIN users u ON u.id=o.student_id ORDER BY o.created_at DESC LIMIT 500`); return json(200, { orders: r.rows }); } catch (e) { return dbFail(e); } }
  const approveRefund = url.pathname.match(/^\/api\/admin\/refunds\/([0-9a-f-]{36})\/(approve|reject)$/i);
  if (approveRefund && method === 'POST') { const u = await requireRole(request, ['admin']); if (u instanceof Response) return u; try { const r = await db.query('SELECT * FROM refunds WHERE id=$1', [approveRefund[1]]); if (!r.rowCount) return json(404, { message: 'Refund not found.' }); const action = approveRefund[2], x = await readJson(request); if (action === 'reject') { await db.query("UPDATE refunds SET status='rejected',admin_note=$2,resolved_at=now() WHERE id=$1", [approveRefund[1], String(x.note || '')]); await db.query("UPDATE orders SET status='paid',updated_at=now() WHERE id=$1", [r.rows[0].order_id]); } else { await db.query("UPDATE refunds SET status='processed',admin_note=$2,resolved_at=now() WHERE id=$1", [approveRefund[1], String(x.note || '')]); await db.query("UPDATE orders SET status='refunded',updated_at=now() WHERE id=$1", [r.rows[0].order_id]); await db.query("UPDATE instructor_earnings SET status='reversed' WHERE order_id=$1 AND status<>'paid'", [r.rows[0].order_id]); } await audit(u, 'refund.' + (action === 'approve' ? 'processed' : 'rejected'), 'order', r.rows[0].order_id, {}); return json(200, { ok: true }); } catch (e) { return dbFail(e); } }
  if (url.pathname === '/api/admin/refunds' && method === 'GET') { const u = await requireRole(request, ['admin']); if (u instanceof Response) return u; try { const r = await db.query(`SELECT f.*,o.student_id,o.total_cents,u.name student_name,u.email student_email FROM refunds f JOIN orders o ON o.id=f.order_id LEFT JOIN users u ON u.id=o.student_id ORDER BY f.created_at DESC LIMIT 300`); return json(200, { refunds: r.rows }); } catch (e) { return dbFail(e); } }
  const markPaid = url.pathname.match(/^\/api\/admin\/orders\/([0-9a-f-]{36})\/mark-paid$/i);
  if (markPaid && method === 'POST') { const u = await requireRole(request, ['admin']); if (u instanceof Response) return u; try { const x = await readJson(request), order = await markOrderPaid(markPaid[1], String(x.reference || 'MANUAL-' + Date.now())); await audit(u, 'payment.confirmed', 'order', markPaid[1], { reference: x.reference || null }); return json(200, { order }); } catch (e) { return e.status ? json(e.status, { message: e.message }) : dbFail(e); } }
  if (url.pathname === '/api/admin/payouts/approve' && method === 'POST') { const u = await requireRole(request, ['admin']); if (u instanceof Response) return u; try { const x = await readJson(request), instructorId = String(x.instructorId || ''); if (!/^[0-9a-f-]{36}$/i.test(instructorId)) return json(400, { message: 'A valid instructor is required.' }); const available = await db.query("SELECT COALESCE(sum(instructor_cents),0) total,min(created_at)::date period_start,max(created_at)::date period_end FROM instructor_earnings WHERE instructor_id=$1 AND status='available' AND payout_id IS NULL", [instructorId]); const amount = Number(available.rows[0].total || 0); if (amount <= 0) return json(409, { message: 'This instructor has no available earnings.' }); const id = uuid(), ref = 'EQ-PAYOUT-' + Date.now().toString().slice(-10); await db.query("INSERT INTO instructor_payouts(id,instructor_id,amount_cents,currency,status,reference,period_start,period_end,paid_at) VALUES($1,$2,$3,'USD','paid',$4,$5,$6,now())", [id, instructorId, amount, ref, available.rows[0].period_start, available.rows[0].period_end]); await db.query("UPDATE instructor_earnings SET status='paid',payout_id=$1 WHERE instructor_id=$2 AND status='available' AND payout_id IS NULL", [id, instructorId]); await audit(u, 'payout.approved', 'user', instructorId, { payoutId: id, amountCents: amount }); return json(201, { payout: { id, reference: ref, amountCents: amount, status: 'paid' } }); } catch (e) { return dbFail(e); } }
  if (url.pathname === '/api/admin/coupons' && method === 'POST') { const u = await requireRole(request, ['admin']); if (u instanceof Response) return u; try { const x = await readJson(request), code = String(x.code || '').trim().toUpperCase(); if (!/^[A-Z0-9_-]{3,30}$/.test(code)) return json(400, { message: 'Use a coupon code of 3–30 letters, numbers, _ or -.' }); const type = x.discountType === 'fixed' ? 'fixed' : 'percent', value = type === 'percent' ? Math.min(100, Math.max(1, Number(x.discountValue || 0))) : Math.max(1, Number(x.discountValue || 0)); const id = uuid(); await db.query('INSERT INTO coupons(id,code,description,discount_type,discount_value,max_redemptions,created_by,ends_at) VALUES($1,$2,$3,$4,$5,$6,$7,$8)', [id, code, String(x.description || ''), type, Math.round(value), x.maxRedemptions ? Number(x.maxRedemptions) : null, u.id, x.endsAt || null]); await audit(u, 'coupon.created', 'coupon', id, { code }); return json(201, { id, code }); } catch (e) { return dbFail(e); } }
  if (url.pathname === '/api/admin/coupons' && method === 'GET') { const u = await requireRole(request, ['admin']); if (u instanceof Response) return u; try { const r = await db.query(`SELECT c.*,(SELECT count(*) FROM coupon_redemptions cr WHERE cr.coupon_id=c.id) used FROM coupons c ORDER BY c.created_at DESC LIMIT 300`); return json(200, { coupons: r.rows }); } catch (e) { return dbFail(e); } }
  const toggleCoupon = url.pathname.match(/^\/api\/admin\/coupons\/([0-9a-f-]{36})\/(activate|deactivate)$/i);
  if (toggleCoupon && method === 'POST') { const u = await requireRole(request, ['admin']); if (u instanceof Response) return u; try { const active = toggleCoupon[2] === 'activate'; await db.query('UPDATE coupons SET active=$2 WHERE id=$1', [toggleCoupon[1], active]); await audit(u, 'coupon.' + (active ? 'activated' : 'deactivated'), 'coupon', toggleCoupon[1], {}); return json(200, { ok: true }); } catch (e) { return dbFail(e); } }

  // Phase 13 live learning
  if (url.pathname === '/api/live-classes' && method === 'GET') {
    try { const viewer = await currentUser(request); const r = await db.query(`SELECT l.id,l.course_id,l.title,l.description,l.starts_at,l.duration_minutes,l.capacity,l.status,l.session_type,l.timezone,l.meeting_provider,l.recording_url,l.recording_status,u.name instructor_name,c.title course_title,(SELECT count(*)::int FROM live_class_attendance a WHERE a.live_class_id=l.id) reserved_count FROM live_classes l JOIN users u ON u.id=l.instructor_id LEFT JOIN courses c ON c.id=l.course_id WHERE l.status<>'cancelled' ORDER BY CASE WHEN l.status='live' THEN 0 WHEN l.status='upcoming' THEN 1 ELSE 2 END,l.starts_at ASC LIMIT 250`); let reserved = new Set(); if (viewer?.role === 'student') { const a = await db.query('SELECT live_class_id FROM live_class_attendance WHERE student_id=$1', [viewer.id]); reserved = new Set(a.rows.map((x) => x.live_class_id)); } return json(200, { classes: r.rows.map((x) => ({ ...x, reserved: reserved.has(x.id) })) }); } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/instructor/live-classes' && method === 'GET') {
    const u = await requireRole(request, ['instructor', 'admin']); if (u instanceof Response) return u;
    try { const params = [], where = u.role === 'instructor' ? 'WHERE l.instructor_id=$1' : ''; if (u.role === 'instructor') params.push(u.id); const r = await db.query(`SELECT l.*,c.title course_title,(SELECT count(*)::int FROM live_class_attendance a WHERE a.live_class_id=l.id) reserved_count,(SELECT count(*)::int FROM live_class_attendance a WHERE a.live_class_id=l.id AND a.joined_at IS NOT NULL) attended_count FROM live_classes l LEFT JOIN courses c ON c.id=l.course_id ${where} ORDER BY l.starts_at DESC LIMIT 300`, params); return json(200, { classes: r.rows }); } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/instructor/live-classes' && method === 'POST') {
    const u = await requireRole(request, ['instructor', 'admin']); if (u instanceof Response) return u;
    try {
      const x = await readJson(request), title = String(x.title || '').trim(), description = String(x.description || '').trim(), courseId = String(x.courseId || '').trim() || null, startsAt = new Date(x.startsAt), duration = Math.max(10, Math.min(480, Number(x.durationMinutes || 60))), capacity = Math.max(1, Math.min(10000, Number(x.capacity || 50))), provider = String(x.meetingProvider || 'external').trim().slice(0, 40), joinUrl = String(x.joinUrl || '').trim(), hostUrl = String(x.hostUrl || '').trim(), sessionType = String(x.sessionType || 'group_class').trim().slice(0, 40), timezone = String(x.timezone || 'Africa/Harare').trim().slice(0, 80), allowRecording = !!x.allowRecording;
      if (title.length < 3 || Number.isNaN(startsAt.getTime()) || startsAt.getTime() < Date.now() - 300000) return json(400, { message: 'Enter a valid title and future class date/time.' });
      if (courseId && !/^[0-9a-f-]{36}$/i.test(courseId)) return json(400, { message: 'Invalid course.' });
      if (courseId && u.role === 'instructor' && !(await ownsCourse(u, courseId))) return json(403, { message: 'You can only schedule classes for your own courses.' });
      if (joinUrl && !/^https:\/\//i.test(joinUrl)) return json(400, { message: 'The student meeting URL must use https://' });
      if (hostUrl && !/^https:\/\//i.test(hostUrl)) return json(400, { message: 'The host meeting URL must use https://' });
      const id = uuid(); await db.query(`INSERT INTO live_classes(id,instructor_id,course_id,title,description,starts_at,duration_minutes,capacity,status,meeting_provider,meeting_ref,session_type,timezone,join_url,host_url,allow_recording) VALUES($1,$2,$3,$4,$5,$6,$7,$8,'upcoming',$9,$10,$11,$12,$13,$14,$15)`, [id, u.id, courseId, title, description, startsAt.toISOString(), duration, capacity, provider, String(x.meetingRef || '').slice(0, 300), sessionType, timezone, joinUrl || null, hostUrl || null, allowRecording]);
      if (courseId) { try { const learners = await db.query('SELECT student_id FROM enrolments WHERE course_id=$1', [courseId]); for (const row of learners.rows) { await notifyUser(row.student_id, { type: 'live_class', title: 'New live class scheduled', body: `${title} is scheduled for ${startsAt.toLocaleString('en-ZW', { timeZone: 'Africa/Harare' })}.`, link: '/live-classes.html', emailSubject: `EduQuinn live class: ${title}`, emailText: `A new live class has been scheduled.\n\n${title}\n${startsAt.toLocaleString('en-ZW', { timeZone: 'Africa/Harare' })}\n\nSign in to reserve your seat.`, channel: 'live_classes' }); } } catch {} }
      await audit(u, 'live_class.created', 'live_class', id, { title, courseId }); return json(201, { id });
    } catch (e) { return e.status ? json(e.status, { message: e.message }) : dbFail(e); }
  }
  const reserveLive = url.pathname.match(/^\/api\/live-classes\/([0-9a-f-]{36})\/reserve$/i);
  if (reserveLive && method === 'POST') {
    const u = await requireRole(request, ['student']); if (u instanceof Response) return u;
    try { const l = await db.query('SELECT * FROM live_classes WHERE id=$1', [reserveLive[1]]); if (!l.rowCount) return json(404, { message: 'Live class not found.' }); const c = l.rows[0]; if (!['upcoming', 'live'].includes(c.status)) return json(409, { message: 'Reservations are closed for this class.' }); if (c.course_id) { const en = await db.query('SELECT 1 FROM enrolments WHERE student_id=$1 AND course_id=$2', [u.id, c.course_id]); if (!en.rowCount) return json(403, { message: 'Enroll in the related course before reserving this live class.' }); } const count = await db.query('SELECT count(*)::int n FROM live_class_attendance WHERE live_class_id=$1', [c.id]); if (Number(count.rows[0].n) >= Number(c.capacity)) return json(409, { message: 'This live class is full.' }); await db.query('INSERT INTO live_class_attendance(live_class_id,student_id) VALUES($1,$2) ON CONFLICT(live_class_id,student_id) DO NOTHING', [c.id, u.id]); await notifyUser(u.id, { type: 'live_reservation', title: 'Live class reserved', body: `Your seat for ${c.title} is reserved.`, link: '/live-classes.html', emailSubject: `EduQuinn reservation confirmed: ${c.title}`, emailText: `Your seat for ${c.title} has been reserved. Sign in to EduQuinn before the session to join.`, channel: 'live_classes' }); return json(200, { reserved: true }); } catch (e) { return dbFail(e); }
  }
  if (reserveLive && method === 'DELETE') {
    const u = await requireRole(request, ['student']); if (u instanceof Response) return u;
    try { await db.query('DELETE FROM live_class_attendance WHERE live_class_id=$1 AND student_id=$2 AND joined_at IS NULL', [reserveLive[1], u.id]); return json(200, { reserved: false }); } catch (e) { return dbFail(e); }
  }
  const joinLive = url.pathname.match(/^\/api\/live-classes\/([0-9a-f-]{36})\/join$/i);
  if (joinLive && method === 'POST') {
    const u = await requireRole(request, ['student', 'instructor', 'admin']); if (u instanceof Response) return u;
    try {
      const r = await db.query('SELECT * FROM live_classes WHERE id=$1', [joinLive[1]]); if (!r.rowCount) return json(404, { message: 'Live class not found.' });
      const l = r.rows[0]; if (l.status === 'cancelled' || l.status === 'completed') return json(409, { message: 'This live class is not open.' });
      const startMs = new Date(l.starts_at).getTime(), endMs = startMs + Number(l.duration_minutes || 60) * 60000, now = Date.now();
      if (u.role === 'student') {
        if (now < startMs - 30 * 60000) return json(409, { message: 'This classroom opens 30 minutes before the scheduled start.' });
        if (now > endMs + 60 * 60000) return json(409, { message: 'This live class has ended.' });
        const a = await db.query('SELECT 1 FROM live_class_attendance WHERE live_class_id=$1 AND student_id=$2', [l.id, u.id]); if (!a.rowCount) return json(403, { message: 'Reserve your seat before joining.' });
        await db.query(`UPDATE live_class_attendance SET joined_at=COALESCE(joined_at,now()),last_joined_at=now(),join_count=join_count+1 WHERE live_class_id=$1 AND student_id=$2`, [l.id, u.id]);
      } else if (u.role === 'instructor' && l.instructor_id !== u.id) return json(403, { message: 'This class belongs to another instructor.' });
      if (l.status === 'upcoming' && now >= startMs - 30 * 60000) await db.query("UPDATE live_classes SET status='live',updated_at=now() WHERE id=$1", [l.id]);
      const target = u.role === 'instructor' ? l.host_url || l.join_url : l.join_url || null;
      return json(200, { classId: l.id, title: l.title, joinUrl: target, provider: l.meeting_provider || 'external', roomUrl: `/live-class-room.html?id=${l.id}` });
    } catch (e) { return dbFail(e); }
  }
  const leaveLive = url.pathname.match(/^\/api\/live-classes\/([0-9a-f-]{36})\/leave$/i);
  if (leaveLive && method === 'POST') {
    const u = await requireRole(request, ['student', 'instructor', 'admin']); if (u instanceof Response) return u;
    try { if (u.role === 'student') { await db.query(`UPDATE live_class_attendance SET attendance_minutes=attendance_minutes+GREATEST(0,LEAST(480,round(extract(epoch from (now()-COALESCE(last_joined_at,joined_at)))/60)::int)),left_at=now() WHERE live_class_id=$1 AND student_id=$2`, [leaveLive[1], u.id]); } return json(200, { ok: true }); } catch (e) { return dbFail(e); }
  }
  const attendanceLive = url.pathname.match(/^\/api\/instructor\/live-classes\/([0-9a-f-]{36})\/attendance$/i);
  if (attendanceLive && method === 'GET') {
    const u = await requireRole(request, ['instructor', 'admin']); if (u instanceof Response) return u;
    try { const l = await db.query('SELECT * FROM live_classes WHERE id=$1', [attendanceLive[1]]); if (!l.rowCount) return json(404, { message: 'Live class not found.' }); if (u.role === 'instructor' && l.rows[0].instructor_id !== u.id) return json(403, { message: 'This class belongs to another instructor.' }); const r = await db.query(`SELECT a.*,s.name student_name,s.email student_email FROM live_class_attendance a JOIN users s ON s.id=a.student_id WHERE a.live_class_id=$1 ORDER BY s.name`, [attendanceLive[1]]); return json(200, { attendance: r.rows }); } catch (e) { return dbFail(e); }
  }
  const updateLive = url.pathname.match(/^\/api\/instructor\/live-classes\/([0-9a-f-]{36})$/i);
  if (updateLive && method === 'PATCH') {
    const u = await requireRole(request, ['instructor', 'admin']); if (u instanceof Response) return u;
    try { const l = await db.query('SELECT * FROM live_classes WHERE id=$1', [updateLive[1]]); if (!l.rowCount) return json(404, { message: 'Live class not found.' }); if (u.role === 'instructor' && l.rows[0].instructor_id !== u.id) return json(403, { message: 'This class belongs to another instructor.' }); const x = await readJson(request), status = ['upcoming', 'live', 'completed', 'cancelled'].includes(x.status) ? x.status : null, recordingUrl = String(x.recordingUrl || '').trim(); if (recordingUrl && !/^https:\/\//i.test(recordingUrl)) return json(400, { message: 'Recording URL must use https://' }); await db.query(`UPDATE live_classes SET status=COALESCE($2,status),recording_url=COALESCE(NULLIF($3,''),recording_url),recording_status=CASE WHEN NULLIF($3,'') IS NOT NULL THEN 'ready' ELSE recording_status END,ended_at=CASE WHEN $2='completed' THEN now() ELSE ended_at END,cancelled_at=CASE WHEN $2='cancelled' THEN now() ELSE cancelled_at END,updated_at=now() WHERE id=$1`, [updateLive[1], status, recordingUrl]); await audit(u, 'live_class.updated', 'live_class', updateLive[1], { status, recordingUrl: !!recordingUrl }); return json(200, { ok: true }); } catch (e) { return dbFail(e); }
  }
  if (updateLive && method === 'DELETE') {
    const u = await requireRole(request, ['instructor', 'admin']); if (u instanceof Response) return u;
    try { const l = await db.query('SELECT * FROM live_classes WHERE id=$1', [updateLive[1]]); if (!l.rowCount) return json(404, { message: 'Live class not found.' }); if (u.role === 'instructor' && l.rows[0].instructor_id !== u.id) return json(403, { message: 'This class belongs to another instructor.' }); await db.query("UPDATE live_classes SET status='cancelled',cancelled_at=now(),updated_at=now() WHERE id=$1", [updateLive[1]]); return json(200, { ok: true }); } catch (e) { return dbFail(e); }
  }
  const liveMessages = url.pathname.match(/^\/api\/live-classes\/([0-9a-f-]{36})\/messages$/i);
  if (liveMessages && method === 'GET') {
    const u = await requireRole(request, ['student', 'instructor', 'admin']); if (u instanceof Response) return u;
    try { const live = await db.query('SELECT instructor_id FROM live_classes WHERE id=$1', [liveMessages[1]]); if (!live.rowCount) return json(404, { message: 'Live class not found.' }); if (u.role === 'student') { const allowed = await db.query('SELECT 1 FROM live_class_attendance WHERE live_class_id=$1 AND student_id=$2', [liveMessages[1], u.id]); if (!allowed.rowCount) return json(403, { message: 'Reserve this class before using its chat.' }); } else if (u.role === 'instructor' && live.rows[0].instructor_id !== u.id) return json(403, { message: 'This class belongs to another instructor.' }); const r = await db.query(`SELECT m.id,m.message,m.created_at,u.name,u.role FROM live_class_messages m JOIN users u ON u.id=m.user_id WHERE m.live_class_id=$1 ORDER BY m.created_at ASC LIMIT 300`, [liveMessages[1]]); return json(200, { messages: r.rows }); } catch (e) { return dbFail(e); }
  }
  if (liveMessages && method === 'POST') {
    const u = await requireRole(request, ['student', 'instructor', 'admin']); if (u instanceof Response) return u;
    try { const live = await db.query('SELECT instructor_id FROM live_classes WHERE id=$1', [liveMessages[1]]); if (!live.rowCount) return json(404, { message: 'Live class not found.' }); if (u.role === 'student') { const allowed = await db.query('SELECT 1 FROM live_class_attendance WHERE live_class_id=$1 AND student_id=$2', [liveMessages[1], u.id]); if (!allowed.rowCount) return json(403, { message: 'Reserve this class before using its chat.' }); } else if (u.role === 'instructor' && live.rows[0].instructor_id !== u.id) return json(403, { message: 'This class belongs to another instructor.' }); const x = await readJson(request), message = String(x.message || '').trim(); if (!message || message.length > 1500) return json(400, { message: 'Enter a message of up to 1,500 characters.' }); await db.query('INSERT INTO live_class_messages(id,live_class_id,user_id,message) VALUES($1,$2,$3,$4)', [uuid(), liveMessages[1], u.id, message]); return json(201, { ok: true }); } catch (e) { return dbFail(e); }
  }
  const calendarLive = url.pathname.match(/^\/api\/live-classes\/([0-9a-f-]{36})\/calendar\.ics$/i);
  if (calendarLive && method === 'GET') {
    const calUser = await requireRole(request, ['student', 'instructor', 'admin']); if (calUser instanceof Response) return calUser;
    try {
      const r = await db.query(`SELECT l.*,u.name instructor_name FROM live_classes l JOIN users u ON u.id=l.instructor_id WHERE l.id=$1`, [calendarLive[1]]); if (!r.rowCount) return new Response('Not found', { status: 404, headers: securityHeaders });
      const l = r.rows[0], start = new Date(l.starts_at), end = new Date(start.getTime() + Number(l.duration_minutes) * 60000), fmt = (d) => d.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z'), safe = (v) => String(v || '').replace(/[\\;,\n]/g, (m) => ({ '\\': '\\\\', ';': '\\;', ',': '\\,', '\n': '\\n' }[m] || ''));
      const ics = `BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//EduQuinn//Live Classes//EN\r\nBEGIN:VEVENT\r\nUID:${l.id}@eduquinn\r\nDTSTAMP:${fmt(new Date())}\r\nDTSTART:${fmt(start)}\r\nDTEND:${fmt(end)}\r\nSUMMARY:${safe(l.title)}\r\nDESCRIPTION:${safe((l.description || '') + ' Instructor: ' + l.instructor_name)}\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n`;
      return new Response(ics, { status: 200, headers: { ...securityHeaders, 'Content-Type': 'text/calendar; charset=utf-8', 'Content-Disposition': `attachment; filename="eduquinn-live-class.ics"` } });
    } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/admin/system-health' && method === 'GET') {
    const u = await requireRole(request, ['admin']); if (u instanceof Response) return u;
    try { const database = await db.ping(); const tables = await db.query(`SELECT (SELECT count(*)::int FROM users) users,(SELECT count(*)::int FROM courses) courses,(SELECT count(*)::int FROM enrolments) enrolments,(SELECT count(*)::int FROM orders) orders,(SELECT count(*)::int FROM certificates) certificates,(SELECT count(*)::int FROM sessions WHERE expires_at>now()) active_sessions,(SELECT count(*)::int FROM security_events WHERE created_at>now()-interval '24 hours') security_events_24h`); const launch = (await db.query('SELECT status,registrations_open,instructor_applications_open,public_certificate_verification,launched_at FROM platform_launch_state WHERE id=1')).rows[0]; return json(200, { phase: '17', environment: NODE_ENV, database, counts: tables.rows[0], launch, configuration: { appBaseUrl: !!APP_BASE_URL, emailConfigured: !!BREVO_API_KEY, paymentSandbox: PAYMENT_SANDBOX, mediaStorage: 'supabase:' + storage.BUCKET, allowedOrigins: [...ALLOWED_ORIGINS] } }); } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/platform/status' && method === 'GET') {
    try { const r = await db.query('SELECT status,registrations_open,instructor_applications_open,public_certificate_verification,launch_message,launched_at,updated_at FROM platform_launch_state WHERE id=1'); return json(200, { platform: r.rows[0] || { status: 'prelaunch', registrations_open: true, instructor_applications_open: true, public_certificate_verification: true } }); } catch (e) { return dbFail(e); }
  }
  const verifyCert = url.pathname.match(/^\/api\/certificates\/verify\/([^/]+)$/i);
  if (verifyCert && method === 'GET') {
    try { const state = (await db.query('SELECT public_certificate_verification FROM platform_launch_state WHERE id=1')).rows[0]; if (state && state.public_certificate_verification === false) return json(403, { message: 'Public certificate verification is currently unavailable.' }); const code = decodeURIComponent(verifyCert[1]).trim().toUpperCase(); const r = await db.query(`SELECT ce.certificate_code,ce.issued_at,u.name student_name,c.title course_title,c.education_level,c.category,i.name instructor_name FROM certificates ce JOIN users u ON u.id=ce.student_id JOIN courses c ON c.id=ce.course_id LEFT JOIN users i ON i.id=c.instructor_id WHERE upper(ce.certificate_code)=upper($1)`, [code]); if (!r.rowCount) return json(404, { valid: false, message: 'Certificate not found.' }); return json(200, { valid: true, certificate: r.rows[0] }); } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/admin/launch-state' && method === 'GET') {
    const u = await requireRole(request, ['admin']); if (u instanceof Response) return u;
    try { const r = await db.query('SELECT * FROM platform_launch_state WHERE id=1'); return json(200, { platform: r.rows[0] }); } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/admin/launch-state' && method === 'POST') {
    const u = await requireRole(request, ['admin']); if (u instanceof Response) return u;
    try { const x = await readJson(request), status = ['prelaunch', 'live', 'maintenance'].includes(x.status) ? x.status : 'prelaunch', registrations = !!x.registrationsOpen, instructors = !!x.instructorApplicationsOpen, verify = x.publicCertificateVerification !== false, message = String(x.launchMessage || '').trim().slice(0, 300) || 'EduQuinn is preparing for launch.'; const r = await db.query(`UPDATE platform_launch_state SET status=$1,registrations_open=$2,instructor_applications_open=$3,public_certificate_verification=$4,launch_message=$5,launched_at=CASE WHEN $1='live' THEN COALESCE(launched_at,now()) ELSE launched_at END,updated_by=$6,updated_at=now() WHERE id=1 RETURNING *`, [status, registrations, instructors, verify, message, u.id]); await audit(u, 'platform.launch_state_updated', 'platform', '1', { status, registrationsOpen: registrations, instructorApplicationsOpen: instructors, publicCertificateVerification: verify }); return json(200, { platform: r.rows[0] }); } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/support' && method === 'POST') return receiveSupport(request);
  if (url.pathname === '/api/admin/courses' && method === 'GET') { const u = await requireRole(request, ['admin']); if (u instanceof Response) return u; try { const r = await db.query(`SELECT c.id,c.title,c.category,c.education_level,c.price_cents,c.status,c.quality_score,c.created_at,c.updated_at,i.name instructor_name,i.email instructor_email FROM courses c LEFT JOIN users i ON i.id=c.instructor_id ORDER BY c.updated_at DESC LIMIT 500`); return json(200, { courses: r.rows }); } catch (e) { return dbFail(e); } }
  const adminCourseStatus = url.pathname.match(/^\/api\/admin\/courses\/([0-9a-f-]{36})\/status$/i);
  if (adminCourseStatus && method === 'POST') { const u = await requireRole(request, ['admin']); if (u instanceof Response) return u; try { const x = await readJson(request), status = String(x.status || ''); if (!['draft', 'under_review', 'changes_requested', 'published', 'suspended', 'archived'].includes(status)) return json(400, { message: 'Invalid course status.' }); const q = await db.query('UPDATE courses SET status=$2,updated_at=now() WHERE id=$1 RETURNING id,title,status', [adminCourseStatus[1], status]); if (!q.rowCount) return json(404, { message: 'Course not found.' }); await audit(u, 'course.status_updated', 'course', adminCourseStatus[1], { status }); return json(200, { course: q.rows[0] }); } catch (e) { return dbFail(e); } }
  if (url.pathname === '/api/admin/payouts' && method === 'GET') { const u = await requireRole(request, ['admin']); if (u instanceof Response) return u; try { const r = await db.query(`SELECT i.id instructor_id,i.name,i.email,COALESCE(sum(e.gross_cents),0)::bigint gross_cents,COALESCE(sum(e.instructor_cents) FILTER (WHERE e.status='available'),0)::bigint available_cents,COALESCE(sum(e.instructor_cents) FILTER (WHERE e.status='pending'),0)::bigint pending_cents,COALESCE(sum(e.instructor_cents) FILTER (WHERE e.status='paid'),0)::bigint paid_cents FROM users i LEFT JOIN instructor_earnings e ON e.instructor_id=i.id WHERE i.role='instructor' GROUP BY i.id,i.name,i.email ORDER BY available_cents DESC,i.name`); return json(200, { payouts: r.rows }); } catch (e) { return dbFail(e); } }
  if (url.pathname === '/api/admin/reviews' && method === 'GET') { const u = await requireRole(request, ['admin']); if (u instanceof Response) return u; try { const r = await db.query(`SELECT r.id,r.rating,r.review,r.status,r.created_at,s.name student_name,c.title course_title FROM reviews r JOIN users s ON s.id=r.student_id JOIN courses c ON c.id=r.course_id ORDER BY r.created_at DESC LIMIT 500`); return json(200, { reviews: r.rows }); } catch (e) { return dbFail(e); } }
  if (url.pathname === '/api/admin/users' && method === 'GET') { const u = await requireRole(request, ['admin']); if (u instanceof Response) return u; try { const r = await db.query('SELECT id,name,email,role,status,expertise,email_verified,created_at FROM users ORDER BY created_at DESC LIMIT 500'); return json(200, { users: r.rows }); } catch (e) { return dbFail(e); } }
  const adminUserStatus = url.pathname.match(/^\/api\/admin\/users\/([0-9a-f-]{36})\/status$/i);
  if (adminUserStatus && method === 'POST') {
    const u = await requireRole(request, ['admin']); if (u instanceof Response) return u;
    try {
      const x = await readJson(request), status = String(x.status || ''); if (!['active', 'suspended', 'disabled'].includes(status)) return json(400, { message: 'Invalid user status.' });
      if (adminUserStatus[1] === u.id) return json(400, { message: 'You cannot change your own account status.' });
      const target = await db.query('SELECT id,role,status FROM users WHERE id=$1', [adminUserStatus[1]]); if (!target.rowCount) return json(404, { message: 'User not found.' });
      if (target.rows[0].role === 'admin' && status !== 'active') return json(403, { message: 'Other administrator accounts cannot be suspended or disabled from here.' });
      const q = await db.query('UPDATE users SET status=$2,updated_at=now() WHERE id=$1 RETURNING id,name,email,role,status', [adminUserStatus[1], status]);
      if (status !== 'active') await db.query('DELETE FROM sessions WHERE user_id=$1', [adminUserStatus[1]]);
      await audit(u, 'user.status_updated', 'user', adminUserStatus[1], { status });
      try { await notifyUser(adminUserStatus[1], { type: 'account_status', title: status === 'active' ? 'Your account has been reactivated' : 'Your account access has changed', body: status === 'active' ? 'An administrator has reactivated your EduQuinn account.' : `An administrator has set your EduQuinn account to "${status}". Contact support if you believe this is a mistake.`, link: '/support.html', emailSubject: `EduQuinn account status: ${status}`, emailText: status === 'active' ? 'An administrator has reactivated your EduQuinn account. You can sign in again.' : `An administrator has set your EduQuinn account status to "${status}". If you believe this is a mistake, contact ${SUPPORT_EMAIL}.`, channel: 'account' }); } catch {}
      return json(200, { user: q.rows[0] });
    } catch (e) { return dbFail(e); }
  }
  const adminUserReset = url.pathname.match(/^\/api\/admin\/users\/([0-9a-f-]{36})\/reset-password$/i);
  if (adminUserReset && method === 'POST') {
    const u = await requireRole(request, ['admin']); if (u instanceof Response) return u;
    try {
      const target = await db.query('SELECT id,name,email FROM users WHERE id=$1', [adminUserReset[1]]); if (!target.rowCount) return json(404, { message: 'User not found.' });
      const tempPassword = crypto.randomBytes(9).toString('base64').replace(/[+/=]/g, 'x') + '!2'; const hash = await bcrypt.hash(tempPassword, 12);
      await db.query('UPDATE users SET password_hash=$2,updated_at=now() WHERE id=$1', [adminUserReset[1], hash]); await db.query('DELETE FROM sessions WHERE user_id=$1', [adminUserReset[1]]);
      await audit(u, 'user.password_reset', 'user', adminUserReset[1], {});
      try { await notifyUser(adminUserReset[1], { type: 'account_status', title: 'Your password was reset by an administrator', body: 'An administrator reset your EduQuinn password. You will need a new password to sign in — contact support if you did not expect this.', link: '/login.html', emailSubject: 'Your EduQuinn password was reset', emailText: `An administrator reset the password on your EduQuinn account (${target.rows[0].email}). If you did not expect this, contact ${SUPPORT_EMAIL} immediately.`, channel: 'account' }); } catch {}
      return json(200, { tempPassword });
    } catch (e) { return dbFail(e); }
  }
  if (url.pathname === '/api/admin/audit' && method === 'GET') { const u = await requireRole(request, ['admin']); if (u instanceof Response) return u; try { const r = await db.query(`SELECT a.*,u.name actor_name,u.email actor_email FROM audit_log a LEFT JOIN users u ON u.id=a.actor_user_id ORDER BY a.created_at DESC LIMIT 300`); return json(200, { events: r.rows }); } catch (e) { return dbFail(e); } }
  const adminUserProfile = url.pathname.match(/^\/api\/admin\/users\/([0-9a-f-]{36})\/profile$/i);
  if (adminUserProfile && method === 'GET') {
    const u = await requireRole(request, ['admin']); if (u instanceof Response) return u; const targetId = adminUserProfile[1];
    try {
      const target = await db.query('SELECT id,name,email,role,status,expertise,email_verified,created_at,updated_at FROM users WHERE id=$1', [targetId]); if (!target.rowCount) return json(404, { message: 'User not found.' });
      const [enrolments, orders, refunds, notes, activity, instructorCourses, instructorEarnings] = await Promise.all([
        db.query(`SELECT e.course_id,e.progress,e.enrolled_at,e.completed_at,c.title course_title,c.category,c.price_cents FROM enrolments e JOIN courses c ON c.id=e.course_id WHERE e.student_id=$1 ORDER BY e.enrolled_at DESC`, [targetId]),
        db.query(`SELECT o.id,o.status,o.payment_method,o.currency,o.total_cents,o.created_at,o.paid_at,COALESCE((SELECT json_agg(json_build_object('courseId',oi.course_id,'title',c.title,'unitPriceCents',oi.unit_price_cents)) FROM order_items oi JOIN courses c ON c.id=oi.course_id WHERE oi.order_id=o.id),'[]'::json) items FROM orders o WHERE o.student_id=$1 ORDER BY o.created_at DESC LIMIT 200`, [targetId]),
        db.query(`SELECT f.id,f.order_id,f.amount_cents,f.reason,f.status,f.admin_note,f.created_at,f.resolved_at FROM refunds f JOIN orders o ON o.id=f.order_id WHERE o.student_id=$1 ORDER BY f.created_at DESC LIMIT 100`, [targetId]),
        db.query(`SELECT n.id,n.body,n.created_at,a.name author_name FROM admin_notes n LEFT JOIN users a ON a.id=n.author_id WHERE n.user_id=$1 ORDER BY n.created_at DESC LIMIT 200`, [targetId]),
        db.query(`SELECT a.id,a.action,a.entity_type,a.entity_id,a.details,a.created_at,u.name actor_name FROM audit_log a LEFT JOIN users u ON u.id=a.actor_user_id WHERE a.actor_user_id=$1 OR (a.entity_type='user' AND a.entity_id=$1) ORDER BY a.created_at DESC LIMIT 100`, [targetId]),
        db.query(`SELECT c.id,c.title,c.status,c.price_cents,c.created_at,(SELECT count(*) FROM enrolments e WHERE e.course_id=c.id) student_count FROM courses c WHERE c.instructor_id=$1 ORDER BY c.created_at DESC`, [targetId]),
        db.query(`SELECT COALESCE(sum(instructor_cents) FILTER (WHERE status='available'),0)::bigint available_cents,COALESCE(sum(instructor_cents) FILTER (WHERE status='pending'),0)::bigint pending_cents,COALESCE(sum(instructor_cents),0)::bigint lifetime_cents FROM instructor_earnings WHERE instructor_id=$1`, [targetId]),
      ]);
      return json(200, { user: target.rows[0], enrolments: enrolments.rows, orders: orders.rows, refunds: refunds.rows, notes: notes.rows, activity: activity.rows, instructorCourses: instructorCourses.rows, instructorEarnings: instructorEarnings.rows[0] });
    } catch (e) { return dbFail(e); }
  }
  if (adminUserProfile && method === 'POST') {
    const u = await requireRole(request, ['admin']); if (u instanceof Response) return u; const targetId = adminUserProfile[1];
    try {
      const target = await db.query('SELECT id FROM users WHERE id=$1', [targetId]); if (!target.rowCount) return json(404, { message: 'User not found.' });
      const x = await readJson(request), body = String(x.body || '').trim(); if (body.length < 1 || body.length > 4000) return json(400, { message: 'A note must be between 1 and 4000 characters.' });
      const id = uuid(); await db.query('INSERT INTO admin_notes(id,user_id,author_id,body) VALUES($1,$2,$3,$4)', [id, targetId, u.id, body]); await audit(u, 'user.note_added', 'user', targetId, { noteId: id });
      const row = await db.query('SELECT n.id,n.body,n.created_at,a.name author_name FROM admin_notes n LEFT JOIN users a ON a.id=n.author_id WHERE n.id=$1', [id]);
      return json(201, { note: row.rows[0] });
    } catch (e) { return e.status ? json(e.status, { message: e.message }) : dbFail(e); }
  }

  return json(404, { message: 'API route not found.' });
}

module.exports = {
  async fetch(request) {
    const rid = requestId(request);
    try {
      const response = await handle(request);
      response.headers.set('X-Request-Id', rid);
      return response;
    } catch (e) {
      console.error(rid, e);
      return json(500, { message: 'Unexpected server error.', requestId: rid });
    }
  },
};
