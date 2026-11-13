// Postgres-backed replacement for the old in-memory `rateBuckets` Map in
// server.js. That Map worked because server.js was one long-running
// process; on Vercel, each invocation (and each concurrent one) has its own
// memory, so an in-memory counter is not just "reset more often" — it's
// silently ineffective. This moves the fixed-window counter into the same
// Supabase Postgres database, using a single atomic UPSERT so concurrent
// requests racing on the same key still count correctly (no read-then-write
// gap).
const db = require('../db');

/**
 * @param {string} key - e.g. `${group}:${clientIp}`
 * @param {number} limit - max requests allowed in the window
 * @param {number} windowMs - window size in milliseconds
 * @returns {Promise<{ok:boolean, retry:number, remaining:number}>}
 */
async function hitRate(key, limit, windowMs) {
  const r = await db.query(
    `INSERT INTO rate_limits(key, window_start, count)
     VALUES ($1, now(), 1)
     ON CONFLICT (key) DO UPDATE SET
       count = CASE
         WHEN now() - rate_limits.window_start >= ($2 || ' milliseconds')::interval
         THEN 1 ELSE rate_limits.count + 1 END,
       window_start = CASE
         WHEN now() - rate_limits.window_start >= ($2 || ' milliseconds')::interval
         THEN now() ELSE rate_limits.window_start END
     RETURNING count, window_start`,
    [key, windowMs]
  );
  const row = r.rows[0];
  const elapsedMs = Date.now() - new Date(row.window_start).getTime();
  const retry = Math.max(1, Math.ceil((windowMs - elapsedMs) / 1000));
  return {
    ok: Number(row.count) <= limit,
    retry,
    remaining: Math.max(0, limit - Number(row.count)),
  };
}

// Called from the maintenance cron (api/cron/maintenance.js), not on every
// request — rows older than a couple of windows are safe to discard.
async function pruneRateLimits(maxAgeMs = 24 * 60 * 60 * 1000) {
  const r = await db.query(
    `DELETE FROM rate_limits WHERE window_start < now() - ($1 || ' milliseconds')::interval`,
    [maxAgeMs]
  );
  return r.rowCount;
}

module.exports = { hitRate, pruneRateLimits };
