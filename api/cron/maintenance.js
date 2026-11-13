// Vercel Cron Job target — replaces scripts/maintenance.js's manual
// "npm run maintenance" invocation, which had no scheduler wired to it on
// Render either. Configured in vercel.json's `crons` array (default: once
// daily). Vercel sends `Authorization: Bearer $CRON_SECRET` automatically
// when it invokes a scheduled function and CRON_SECRET is set as a project
// env var — verify this still matches current Vercel docs at
// vercel.com/docs/cron-jobs/manage-cron-jobs#securing-cron-jobs when you
// set this up, since it wasn't independently re-confirmed while writing
// this file. Add CRON_SECRET to Vercel's env vars (any long random string)
// to enable the check.
const db = require('../../db');
const { pruneRateLimits } = require('../../lib/rateLimit');

module.exports = {
  async fetch(request) {
    const secret = process.env.CRON_SECRET;
    if (secret) {
      const auth = request.headers.get('authorization') || '';
      if (auth !== `Bearer ${secret}`) {
        return new Response(JSON.stringify({ message: 'Unauthorized.' }), {
          status: 401,
          headers: { 'Content-Type': 'application/json; charset=utf-8' },
        });
      }
    }
    try {
      const expiredSessions = await db.query('DELETE FROM sessions WHERE expires_at<=now()');
      const oldSecurityEvents = await db.query(
        "DELETE FROM security_events WHERE created_at < now() - interval '180 days'"
      );
      const prunedRateLimits = await pruneRateLimits();
      return new Response(
        JSON.stringify({
          ok: true,
          expiredSessionsDeleted: expiredSessions.rowCount,
          oldSecurityEventsDeleted: oldSecurityEvents.rowCount,
          rateLimitRowsPruned: prunedRateLimits,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json; charset=utf-8' } }
      );
    } catch (e) {
      return new Response(JSON.stringify({ ok: false, message: e.message }), {
        status: 500,
        headers: { 'Content-Type': 'application/json; charset=utf-8' },
      });
    }
  },
};
