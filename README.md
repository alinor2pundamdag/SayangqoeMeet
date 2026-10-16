# SayangqoeMeet

A clean-start Android communication app guided by [masterplan.md](masterplan.md).

## Current verified implementation

- Kotlin Android app using Jetpack Compose with a dark neon sign-in/registration screen.
- Supabase Kotlin Auth client is wired to email sign-in and registration.
- Supabase project is active at `https://phjwzrrsaemgcedwpyyp.supabase.co`.
- Hosted migration `20261016114000_initial_private_messaging` is applied. The live database contains `profiles`, `conversations`, `conversation_members`, and `messages`; RLS is enabled on all four tables.
- A follow-up migration adds indexes for `conversations.created_by` and `messages.sender_id`.
- The Android CI workflow is configured, but a successful GitHub Actions run/build has not yet been verified in this handoff.

## Supabase configuration

The Android build accepts configuration from environment variables or root `local.properties`. Never put a Supabase service-role/secret key in Android code, Git, or CI build output.

Root `local.properties` (do not commit this file):

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_your_key
```

The current Gradle configuration has defaults for the active project and its publishable client key; environment variables or `local.properties` override them. A publishable key is designed for client use, but database access must still be protected by correct RLS policies.

## Build

Requirements: JDK 17, Android SDK platform 35/build-tools 35.0.0, Gradle 8.11.1.

```sh
gradle assembleDebug
```

If successful, the APK is at `app/build/outputs/apk/debug/app-debug.apk`. The GitHub Actions workflow attempts this build on pushes and pull requests. Check [GitHub Actions](https://github.com/alinor2pundamdag/SayangqoeMeet/actions) for an actual run; workflow configuration alone does not prove a build passed.

## Database migrations

Migrations are stored in `supabase/migrations/`. The initial migration creates the private messaging schema and RLS policies; `20261016134617_add_foreign_key_indexes.sql` adds two foreign-key indexes recommended by the live performance advisor. The index migration was applied to the active Supabase project and must remain in sync with the repository migration history.

## Firebase / push notifications

Firebase Cloud Messaging (FCM) is a roadmap requirement but is **not configured or verified yet**. No `app/google-services.json`, Firebase Gradle plugin, messaging service, device-token registration, or trusted server-side push sender is currently present. To finish live push delivery, configure a Firebase Android app matching package `com.sayangqoemeet.app`, add its non-secret `google-services.json` through the approved repository/configuration workflow, implement token handling and notification channels, and deploy a trusted server-side sender. Never put Firebase service-account credentials or FCM server secrets in the Android app or repository.

## Not yet implemented / verified

- Successful Android CI/debug build and runtime layout verification.
- End-to-end Supabase registration/sign-in/session behavior.
- Profile creation/update UI.
- Secure recipient invitation and conversation membership workflow.
- Conversation list, message history/send, pagination, and Supabase Realtime UI.
- Firebase project setup, FCM token registration, trusted push delivery, and device notification tests.
- WebRTC signaling/media, STUN/TURN configuration, and two-device voice/video tests.
- Automated authorization tests proving both allowed and denied RLS paths.

## Project records

- [Project status](PROJECT_STATUS.md)
- [AI handoff](AI_HANDOFF.md)
- [AI changelog](CHANGELOG_AI.md)
- [Master plan](masterplan.md)
