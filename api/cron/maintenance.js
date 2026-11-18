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
      // Flip matured instructor_earnings rows from 'pending' to 'available'
      // now that their refund-safe hold (see Phase 19 in db/schema.sql) has
      // passed. Every query that sums "available" earnings already treats
      // a still-pending-but-matured row as available on its own, so this
      // is only here to keep the stored status column — which admin.js and
      // the instructor payouts page both display directly — accurate,
      // rather than leaving it stuck on "pending" forever.
      const releasedEarnings = await db.query(
        "UPDATE instructor_earnings SET status='available' WHERE status='pending' AND available_at<=now()"
      );
      return new Response(
        JSON.stringify({
          ok: true,
          expiredSessionsDeleted: expiredSessions.rowCount,
          oldSecurityEventsDeleted: oldSecurityEvents.rowCount,
          rateLimitRowsPruned: prunedRateLimits,
          earningsReleased: releasedEarnings.rowCount,
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
