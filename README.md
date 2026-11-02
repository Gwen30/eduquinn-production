# EduQuinn Phase 16 — Launch & Production Release

Phase 16 is the final launch layer built on the Phase 15 production-hardened platform. It preserves the approved interface and existing PostgreSQL data while adding controlled go-live settings, public certificate verification, launch checks and deployment templates.

## New in Phase 16

- Admin Launch Control page at `/admin-launch.html`
- Platform launch states: Pre-launch, Live, Maintenance
- Administrator controls for student registration and instructor applications
- Public certificate verification at `/verify-certificate.html`
- Public certificate verification API
- Launch-state audit logging
- Registration endpoints respect Admin launch settings
- Phase 16 launch readiness command
- First-run schema command
- Render Blueprint (`render.yaml`)
- Docker deployment files
- Phase 16 system-health reporting
- Production support-form parsing fix

## Upgrade from Phase 15

Copy your working `.env` from Phase 15 into the Phase 16 folder, then run:

```powershell
npm install
npm run migrate
npm start
```

Your existing users, administrators, courses, messages, payments, enrolments, certificates and live classes remain in the same PostgreSQL database.

## Launch control

Administrator only:

`http://localhost:8080/admin-launch.html`

The administrator can control:

- Platform status
- Student registrations
- Instructor applications
- Public certificate verification
- Public launch message

The launch state is stored in PostgreSQL and changes are written to the audit log.

## Public certificate verification

`http://localhost:8080/verify-certificate.html`

A learner, employer or institution can enter an EduQuinn certificate code and receive the verified learner name, course, instructor, education level and issue date. The administrator can disable public verification from Launch Control.

## Phase 16 migration

Run:

```powershell
npm run migrate
```

This adds `platform_launch_state` without deleting or resetting existing data.

For a first deployment you can also run:

```powershell
npm run first-run
```

## Final launch checks

First run the Phase 15 production checks:

```powershell
npm run check:production
```

Then run:

```powershell
npm run check:launch
```

The launch checker confirms production mode, HTTPS base URL, PostgreSQL, payment sandbox status, media storage, active administrator, published courses and the Phase 16 launch schema.

## Recommended production `.env`

```env
PORT=8080
NODE_ENV=production
DATABASE_URL=postgresql://...
DATABASE_SSL=true
SESSION_DAYS=14

APP_BASE_URL=https://eduquinn.co.zw
ALLOWED_ORIGINS=https://eduquinn.co.zw,https://www.eduquinn.co.zw
TRUST_PROXY=true

SUPPORT_EMAIL=support@eduquinn.co.zw
SUPPORT_FROM_EMAIL=support@eduquinn.co.zw
BREVO_API_KEY=your_real_key

PAYMENT_SANDBOX=false
INSTRUCTOR_SHARE_PERCENT=75

MEDIA_ROOT=/persistent/path/media
BACKUP_RETENTION=14

CERTIFICATE_VERIFY_BASE_URL=https://eduquinn.co.zw/verify-certificate.html
```

Keep all real secrets only in the hosting platform's environment-variable settings. Do not commit `.env`.

## Deployment templates

`render.yaml` provides a starting Render Blueprint with a web service and PostgreSQL database.

A `Dockerfile` and `.dockerignore` are also included for container deployment.

Before using the Render template, choose the database/web plans appropriate to your traffic and confirm the current Render plan names/pricing in the Render dashboard.

The Render blueprint's `buildCommand` runs `npm run check:production` as part of every build (after migrations), so a deploy with a missing/insecure configuration — no HTTPS `APP_BASE_URL`, payment sandbox off without real Paynow credentials, an unchanged admin password, an unreachable database, etc. — fails the build instead of going live broken. Fill in the `sync: false` environment variables (`APP_BASE_URL`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `PAYNOW_INTEGRATION_ID`, `PAYNOW_INTEGRATION_KEY`, and optionally `BREVO_API_KEY`) in the Render dashboard before the first deploy. `npm run check:launch` is deliberately **not** part of the automated build — it checks business/content readiness (an active administrator, a published course) that will not exist before the very first successful deploy, so it stays a manual step you run once, right before flipping Launch Control to **Live** (see the launch sequence below).

The blueprint's `healthCheckPath` points at `/ready`, which checks database connectivity, rather than `/health`, which only confirms the Node process is up. This means Render will correctly treat an instance that is running but cannot reach PostgreSQL as unhealthy instead of routing live traffic to it. `/health` is kept as a lightweight liveness probe for external uptime monitors that just need to know the process is alive.

## Final launch sequence

1. Copy the production environment variables to the host.
2. Run `npm install` / deploy dependencies.
3. Run `npm run migrate`.
4. Confirm the administrator account exists.
5. Confirm at least one verified instructor and published course.
6. Configure Brevo email.
7. Configure real payment credentials and set `PAYMENT_SANDBOX=false`.
8. Configure persistent/object media storage.
9. Run `npm run check:production` (this also runs automatically during the Render build; a failing check fails the deploy).
10. Run `npm run check:launch`.
11. Sign into `/admin-access.html`.
12. Open `/admin-launch.html` and set Platform Status to **Live**.
13. Test student signup, instructor signup, checkout, learning, messaging, live classes, support and certificate verification from a separate device/browser.
14. Take a database backup.
15. Announce the public launch.


## Production-clean user data
This release contains no seeded user-facing course catalogue, fake testimonials, fake marketplace statistics, demo coupons, or demo learner/instructor records. Public and authenticated pages show live database data or an empty state until real records are created.

## Security notes

- **Password policy**: student/instructor self-registration (`POST /api/auth/register`) requires a password of at least 10 characters containing both a letter and a number, enforced both in the browser (`public/signup.html`) and on the server (`weakPassword()` in `server.js`) — the server check is what actually matters since the client-side `pattern`/`minlength` attributes are only a UX convenience. The platform administrator account is held to a stricter bar (12+ characters, must differ from the packaged default) via `scripts/seed-admin.js` and enforced on every deploy by `scripts/production-check.js`. There is currently no self-service "change password" flow for students/instructors; an administrator can force a reset from Admin → Users, which generates a one-time temporary password and signs the account out everywhere.
- **Content-Security-Policy**: `script-src` does not include `'unsafe-inline'`. The small number of static inline `<script>` blocks that ship in `public/*.html` are allowlisted by exact SHA-256 hash in the `Content-Security-Policy` header (`server.js`), so an attacker-injected `<script>` tag (stored XSS) cannot execute even on pages that legitimately need a little inline bootstrap code. If you ever edit one of those inline scripts (currently in `admin-access.html`, `login.html`, `signup.html`, `support.html`, `help-center.html`, `instructor-announcements.html`, `instructor-messages.html`, `instructor-payouts.html`), you must recompute its hash and update it in `server.js` or the page will silently stop running in the browser. `style-src` still allows `'unsafe-inline'`, since the UI sets inline `style=""` attributes dynamically from JavaScript throughout (progress bars, chart widths, etc.) and removing it would require a broader refactor.
- **Live class calendar export** (`GET /api/live-classes/:id/calendar.ics`) now requires an authenticated session, matching the rest of the learner-facing API surface; `public/live.js` only renders the "Add to calendar" link for signed-in visitors and prompts signed-out visitors to sign in instead.
