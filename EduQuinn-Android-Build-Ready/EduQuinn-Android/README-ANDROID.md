# EduQuinn Android

This project wraps the production EduQuinn website at https://eduquinn.co.zw in an Android application.

## Build locally
Open the `android-app` folder in Android Studio, allow Gradle sync to finish, then choose:
Build > Build Bundle(s) / APK(s) > Build APK(s)

The APK is created under:
app/build/outputs/apk/debug/app-debug.apk

## Build with GitHub Actions
Copy `android-app/` and `.github/workflows/build-apk.yml` into the EduQuinn production repository.
Then open GitHub > Actions > Build EduQuinn APK > Run workflow.

Download the `EduQuinn-APK` artifact when the job finishes.

## Production release
The first artifact is a debug/testing APK. Before Google Play release, create a permanent upload keystore and build a signed AAB.
