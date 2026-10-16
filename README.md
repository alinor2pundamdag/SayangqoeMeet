# SayangqoeMeet

A new Android communication app built from scratch, guided by [masterplan.md](masterplan.md).

## Current implementation

- Kotlin Android application module using Jetpack Compose.
- Initial dark sci-fi/neon sign-in and registration UI with a spectral neon wordmark.
- Scrollable form layout, email/password fields, password visibility toggle, and basic local input validation.
- Honest integration status: the form does **not** authenticate users yet. Supabase is not connected in this foundation milestone.

## Build requirements

- JDK 17
- Android SDK with platform 35 and compatible build tools
- Gradle 8.11.1

From the repository root, run:

```sh
gradle assembleDebug
```

The debug APK, if the build succeeds, is generated at `app/build/outputs/apk/debug/app-debug.apk`.

The GitHub Actions workflow at `.github/workflows/android-ci.yml` is configured to attempt a debug build on pushes and pull requests. A successful build has not yet been verified.

## External services not configured yet

Supabase Auth/database/RLS, Firebase Cloud Messaging, and WebRTC signaling/media are not integrated. Never add privileged keys or secrets to this repository. See the master plan for requirements.

## Project records

- [Project status](PROJECT_STATUS.md)
- [AI handoff](AI_HANDOFF.md)
- [AI changelog](CHANGELOG_AI.md)
