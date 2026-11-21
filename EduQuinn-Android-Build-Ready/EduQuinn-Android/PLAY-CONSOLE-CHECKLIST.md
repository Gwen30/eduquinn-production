# EduQuinn — Google Play Console submission checklist

A practical, in-order checklist for taking EduQuinn from "code is ready"
to "live on Google Play." Items are grouped the way Play Console presents
them. This is not legal advice — see the note at the bottom about the
Privacy Policy and Data Safety sections in particular.

## 1. One-time account setup
- [ ] Create (or use an existing) Google Play Console developer account.
      New accounts currently require a one-time **US $25** registration fee
      and identity verification, and **new accounts must run a closed test
      with at least 12 testers for 14 continuous days** before they're
      allowed to apply for production access on their first app. Plan this
      lead time into your launch date — start the closed test as soon as
      you have a working signed build, even before the rest of this
      checklist is finished.
- [ ] Decide who the "Developer" will be (an individual or your
      organization) — this cannot be changed later without re-verification.

## 2. Build the signed release bundle
- [ ] Add the four GitHub Actions secrets (`KEYSTORE_BASE64`,
      `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) from the keystore
      package you were given. See `README-ANDROID.md` in this folder.
- [ ] Run the **Build EduQuinn Release** workflow and download the
      `EduQuinn-AAB` artifact (`app-release.aab`).
- [ ] Double-check `applicationId` is `zw.co.eduquinn.app` before your very
      first upload — **this cannot be changed after you publish**. If you
      ever want a different package name, you'd need to publish as a brand
      new app and lose all existing installs/ratings.

## 3. Create the app in Play Console
- [ ] App name: **EduQuinn** (or your final choice — this one can be changed
      later, unlike the package name).
- [ ] Default language, app/game type ("App"), free or paid (free, since
      course purchases happen inside the web checkout, not as an Android
      in-app purchase).
- [ ] Declarations: export compliance, ads (EduQuinn shows no ads — answer
      "No"), government app (No), etc.

## 4. Play App Signing
- [ ] Accept **Play App Signing** when prompted on first upload (Google's
      standard, recommended path — Google re-signs your app for
      distribution and keeps your upload key safe as a backup).
- [ ] **Important follow-up for deep links:** once Play App Signing is
      active, go to **Release → Setup → App signing** and copy the **App
      signing key certificate's SHA-256 fingerprint** (this is *different*
      from your own upload-key fingerprint already in
      `public/.well-known/assetlinks.json`). Add it as a second entry in
      that file's `sha256_cert_fingerprints` array and redeploy the site.
      Without this, Android App Links verification for
      `https://eduquinn.co.zw` will silently fail on real user devices,
      even though it works fine on your own test builds signed with your
      upload key.

## 5. Store listing
- [ ] Short description (≤80 characters) and full description (≤4000
      characters) — write these to describe EduQuinn's actual courses,
      live classes, and certificates; avoid keyword-stuffing, which Play
      now penalizes.
- [ ] **App icon:** `eduquinn-play-store-icon-512.png` (512×512, provided).
- [ ] **Feature graphic:** `eduquinn-feature-graphic-1024x500.png` (1024×500,
      provided).
- [ ] **Phone screenshots:** at least 2 (Play recommends 4–8), minimum
      320px on the short side, maximum 3840px on the long side. Not yet
      generated — easiest path is screenshots of the live site at a phone
      viewport (e.g. 412×915), taken through the installed app itself once
      you have a test build on a device/emulator.
- [ ] App category (Education) and tags.
- [ ] Contact details: an email address is required; website and phone are
      optional but recommended.

## 6. Privacy Policy (hard blocker — now in place)
- [ ] A general Privacy Policy page now exists at
      `/privacy-policy.html` (linked from the homepage footer and
      cross-linked from the instructor policy page). Once deployed, the
      public URL will be **https://eduquinn.co.zw/privacy-policy.html** —
      enter that exact URL in Play Console's "Privacy policy" field.
- [ ] **Have this policy reviewed by a qualified lawyer before relying on
      it.** It was drafted to be accurate to what the codebase actually
      collects and stores (see the schema in `db/schema.sql`), but it is a
      starting draft, not legal sign-off — especially given EduQuinn
      serves school-age learners and handles payment data.

## 7. Data Safety form
Play Console asks you to declare what data the app collects and why. Based
on what EduQuinn's database and codebase actually store, the honest answers
are approximately:

| Data category | Collected? | Shared? | Notes |
|---|---|---|---|
| Name | Yes | No | Account registration |
| Email address | Yes | No | Account registration, login, notifications |
| User IDs | Yes | No | Internal account identifier |
| Purchase history | Yes | Yes (processor only) | Order records; Paynow processes the actual payment |
| Financial info (payment details) | No | — | Card/mobile-money details go directly to Paynow; EduQuinn's own servers never see or store them |
| Messages (in-app) | Yes | No | Course messaging, live-class chat, support tickets |
| Photos/files | Yes (optional) | No | Only if a user uploads a profile photo or coursework file |
| App activity (app interactions) | Yes | No | Course progress, enrolments, quiz/assessment results |
| Device/other IDs | Minimal | No | Basic request logs (IP, user agent) for security |

- [ ] Declare data is encrypted in transit (HTTPS only — the manifest sets
      `android:usesCleartextTraffic="false"`).
- [ ] Account deletion: a dedicated page now exists at
      **https://eduquinn.co.zw/delete-account.html** — enter that exact URL
      in the Data Safety section's account-deletion field. It explains how
      to request deletion (a pre-filled Support ticket) and what is and
      isn't removed. There's still no *in-app* self-service delete button —
      if Play's review flags that, building one is the next step up from
      this page.
- [ ] Re-check this table against the live app before submitting — this
      checklist describes the schema as of November 2026; if new features
      (e.g. real push notifications) are added later, update this section
      and the Privacy Policy together.

## 8. Content rating questionnaire
- [ ] Complete the IARC content rating questionnaire. EduQuinn is an
      education marketplace with no user-generated public content feed,
      violence, or mature themes — expect an "Everyone" rating, but answer
      every question honestly rather than assuming.
- [ ] Target audience / "Designed for Families": EduQuinn serves learners
      of multiple age groups including school-age students, but the app
      itself is a general learning platform, not a program designed
      specifically and exclusively for children. Unless you intend to opt
      into Google's "Designed for Families" program (which carries extra
      requirements — no behavioral ads, stricter data rules, etc.), select
      the target age range that reflects your actual audience (e.g. 13+ or
      a mixed range) rather than opting in to that program by default.

## 9. Closed testing track (new accounts)
- [ ] Create a **Closed testing** release, upload the `.aab`, and add at
      least 12 tester email addresses (colleagues, friends, or yourself
      with alternate accounts/devices).
- [ ] Testers must opt in via the testing link and actually install the
      app — Play counts installed, active testers toward the 14-day/12
      -tester requirement, not just invited ones.
- [ ] Once 14 continuous days have passed with the minimum testers, the
      "Request production access" option becomes available for your first
      app on a new account.

## 10. Production release
- [ ] Promote the tested release to **Production** (or create a new
      production release from the same `.aab`).
- [ ] Fill in the rollout percentage (Google recommends a staged rollout —
      e.g. start at 20% — for your very first release too, in case an
      unexpected device-specific issue appears).
- [ ] Submit for review. First-time app review commonly takes a few hours
      to a few days.

## Known follow-ups not yet built
- No real push notifications (`POST_NOTIFICATIONS` was removed rather than
  implemented — revisit if/when you want push).
- No *in-app* account-deletion flow yet — deletion is a web page
  (`/delete-account.html`) plus a Support-ticket request, which satisfies
  the Data Safety form's URL field but not necessarily a stricter in-app
  requirement if Play's review calls for one.
- Store listing screenshots (icon, feature graphic, 5 phone screenshots)
  have already been generated — see the files delivered earlier in chat.
