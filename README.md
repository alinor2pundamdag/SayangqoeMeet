# SayangqoeMeet

A privacy-conscious Android communication app guided by [masterplan.md](masterplan.md).

## Implemented in source

- Kotlin Android app using Jetpack Compose with a sci-fi neon sign-in/registration screen.
- Successful sign-in routes to an app shell with Home, Profile, Chats, Conversation, Call, and a locked Admin Console entry.
- Profile loading/saving uses the authenticated Supabase session and PostgREST.
- Conversation list and message history/sending use authenticated PostgREST requests protected by RLS.
- Private conversation creation/invitation uses the deployed `create-conversation` Supabase Edge Function. The function verifies the caller's JWT and uses server-side credentials only to create both membership rows.
- Supabase project is active at `https://phjwzrrsaemgcedwpyyp.supabase.co`. The live schema contains `profiles`, `conversations`, `conversation_members`, and `messages`, with RLS enabled on all four.
- Migrations are committed under `supabase/migrations/`, including `20261016134617_add_foreign_key_indexes.sql`.
- The Edge Function source is committed at `supabase/functions/create-conversation/index.ts` and deployed to the active project.
- A Hang Up / Cancel control is present in the call screen, but it does not end a real call because WebRTC is not implemented yet.

## Supabase client configuration

The Android build accepts configuration from environment variables or root `local.properties`. Never put a Supabase service-role/secret key in Android code, Git, or client build output.

Root `local.properties` (do not commit this file):

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_your_key
```

The current Gradle configuration has defaults for the active project and its publishable client key; environment variables or `local.properties` override them. A publishable key is designed for client use, but every database operation still depends on correct RLS policies.

## Build and verification

Requirements: JDK 17, Android SDK platform 35/build-tools 35.0.0, Gradle 8.11.1.

```sh
gradle assembleDebug
```

If successful, the APK is at `app/build/outputs/apk/debug/app-debug.apk`. The GitHub Actions workflow attempts this build on pushes and pull requests. A successful build and device test have not yet been confirmed in this development session. Check [GitHub Actions](https://github.com/alinor2pundamdag/SayangqoeMeet/actions) for actual workflow results.

## Firebase / push notifications

Firebase Cloud Messaging (FCM) is not configured yet. There is no `app/google-services.json`, Firebase Gradle plugin, FCM service/token registration, or trusted push sender. Register the Android app matching package `com.sayangqoemeet.app` in Firebase and configure a trusted server-side sender before claiming push delivery works. Never commit Firebase service-account credentials or FCM server secrets.

## Admin console

The master plan now explicitly requires a separate web-based admin console with server-verified roles, user/report management, operational health, and audit history. The current Android entry is only a locked navigation placeholder, not a functional admin dashboard. Privileged operations must run through trusted backend endpoints; never expose service-role credentials to Android or a browser.

## Not yet implemented / verified

- Successful Android CI/debug build and runtime layout verification.
- End-to-end authentication/session restoration and device tests.
- Supabase Realtime subscriptions, pagination, message retry/deduplication, and robust offline behavior.
- Automated RLS authorization tests for allowed and denied user paths.
- Firebase project setup, FCM token registration, trusted push delivery, and device notification tests.
- WebRTC signaling/media, STUN/TURN configuration, and two-device voice/video tests.
- Separate web admin console, server-verified admin role assignment, audit log, and privileged endpoints.
- Production release hardening and responsive UI test matrix.

## Project records

- [Project status](PROJECT_STATUS.md)
- [AI handoff](AI_HANDOFF.md)
- [AI changelog](CHANGELOG_AI.md)
- [Master plan](masterplan.md)
