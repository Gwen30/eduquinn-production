# EduQuinn Android

This project wraps the production EduQuinn website (https://eduquinn.co.zw)
in a native Android app (a `WebView` shell with file upload, deep-link,
offline-page and splash-screen support). It is not a from-scratch rebuild —
the web app is the actual product; this is the Play Store delivery wrapper
around it.

- **Package name (applicationId):** `zw.co.eduquinn.app` — permanent once
  published, cannot be changed later without publishing as a new app.
- **App label:** EduQuinn
- **Current version:** versionCode 3, versionName "1.1.0"
- **minSdk:** 24 (Android 7.0) &nbsp;·&nbsp; **targetSdk / compileSdk:** 36

See `PLAY-CONSOLE-CHECKLIST.md` in this same folder for the full step-by-step
submission checklist (store listing, Data Safety form, signing, etc).

## Option A — Build locally with Android Studio
1. Open the `EduQuinn-Android-Build-Ready/EduQuinn-Android` folder in
   Android Studio and let Gradle sync finish.
2. For a quick test build: **Build → Build Bundle(s) / APK(s) → Build
   APK(s)** (produces an unsigned/debug APK under
   `app/build/outputs/apk/debug/`).
3. For the real release build you upload to Play Console, you need the
   signing keystore in place first — see "Signing" below — then **Build →
   Generate Signed Bundle / APK → Android App Bundle**.

## Option B — Build with GitHub Actions (recommended)
The workflow at `.github/workflows/build-apk.yml` already builds a signed,
production-ready `.aab` automatically — nothing to copy or move, it's already
wired to this folder.

1. Add four repository secrets (GitHub repo → **Settings → Secrets and
   variables → Actions → New repository secret**):
   - `KEYSTORE_BASE64`
   - `KEYSTORE_PASSWORD`
   - `KEY_ALIAS`
   - `KEY_PASSWORD`

   These four values come from the keystore package delivered to you
   separately (`eduquinn-release-keystore.jks`,
   `eduquinn-release-keystore-base64.txt`, and
   `eduquinn-keystore-PASSWORDS-KEEP-SAFE.txt`). **Keep that keystore and
   those passwords safe — losing them after your first Play Store release
   means you can never publish an update to this app again.**
2. In GitHub, go to **Actions → Build EduQuinn Release → Run workflow**.
3. When the job finishes, download the `EduQuinn-AAB` artifact — that's
   `app-release.aab`, already signed and ready to upload to Play Console.

## Signing

The release keystore (`app/release.jks`) is deliberately **not** committed
to the repository — it's a secret, not source code. The GitHub Actions
workflow decodes it from the `KEYSTORE_BASE64` secret at build time. If you
build locally in Android Studio instead, place the real `release.jks` file
at `app/release.jks` and export `KEYSTORE_PASSWORD` / `KEY_ALIAS` /
`KEY_PASSWORD` as environment variables (or fill them into
`signingConfigs.release` in `app/build.gradle` temporarily — never commit
that change).

## What's already done

- Launcher icon: full adaptive-icon set (foreground/background layers for
  API 26+, flat + round legacy icons for older devices) matching the web
  app's brand mark.
- Splash screen: native Android 12+ `SplashScreen` API via
  `androidx.core:core-splashscreen`, matching the web app's own splash
  badge, with a 4-second safety-net timeout so it can never hang.
- Offline/error handling: a bundled, branded offline page
  (`assets/offline.html`) shown when the device has no connection or the
  server returns a 5xx error — ordinary 404s are left alone.
- Android App Links: `https://eduquinn.co.zw` and `https://www.eduquinn.co.zw`
  are declared as verified deep links, backed by
  `public/.well-known/assetlinks.json` on the live site (see the checklist
  for an important follow-up once Play App Signing is enabled).
- Unused `POST_NOTIFICATIONS` permission removed (no push notifications are
  implemented yet — add it back if/when real push is built).
