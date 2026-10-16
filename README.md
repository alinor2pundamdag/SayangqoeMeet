# SayangqoeMeet

A clean-start Android communication app guided by [masterplan.md](masterplan.md).

## Current implementation

- Kotlin Android app using Jetpack Compose with the dark neon sign-in/registration screen.
- Supabase Kotlin Auth client is wired to the sign-in and registration controls.
- A secure initial Postgres schema/RLS migration is committed under `supabase/migrations/`; it has **not** been applied to the hosted database.
- Build and runtime status remain unverified until GitHub Actions/device tests confirm them.

## Supabase configuration

The Android build reads the URL and publishable key from environment variables or root `local.properties`. Never use a service-role/secret key in the Android app.

Root `local.properties` (do not commit this file):

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_your_key
```

Alternatively set `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` in the build environment. The app intentionally refuses to submit authentication requests when the publishable key is empty.

## Build

Requirements: JDK 17, Android SDK platform 35/build-tools 35.0.0, Gradle 8.11.1.

```sh
gradle assembleDebug
```

If successful, the APK is at `app/build/outputs/apk/debug/app-debug.apk`. The GitHub Actions workflow attempts this build on pushes and pull requests; do not assume success without checking the run.

## Database migration

Review `supabase/migrations/20261016000100_initial_private_messaging.sql` and apply it only to the intended Supabase project after confirming project access. It creates profiles, conversations, conversation memberships, messages, and row-level security policies. Membership insertion is deliberately restricted; trusted recipient-invitation logic is still required before real one-to-one messaging can be used.

## Not yet implemented/verified

- Hosted Supabase database migration application and live Auth request verification.
- Profile creation/update UI, conversation creation/invitation workflow, and message/realtime UI.
- Firebase Cloud Messaging delivery, WebRTC voice/video calling, and two-device tests.
- Android build success and runtime layout verification.

## Project records

- [Project status](PROJECT_STATUS.md)
- [AI handoff](AI_HANDOFF.md)
- [AI changelog](CHANGELOG_AI.md)
