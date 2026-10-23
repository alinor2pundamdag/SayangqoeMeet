# SayangqoeMeet

A privacy-conscious Android communication app guided by [masterplan.md](masterplan.md).

## Implemented in source

- Kotlin Android app using Jetpack Compose with a sci-fi neon sign-in/registration screen.
- Successful sign-in routes to an app shell with Home, Profile, Chats, Conversation, Call, and a locked Admin Console entry.
- Profile loading/saving uses the authenticated Supabase session and PostgREST.
- Conversation list and message history/sending use authenticated PostgREST requests protected by RLS.
- Private chat invitations use the deployed `create-conversation` and `respond-conversation-invite` Supabase Edge Functions. The creator becomes a member and the recipient remains outside the conversation until accepting the invitation; both functions verify the caller's JWT and use server-side credentials for privileged changes.
- Supabase project is active at `https://phjwzrrsaemgcedwpyyp.supabase.co`. The current live schema has 12 inspected public application tables; all have RLS enabled and at least one policy.
- The 25 executable SQL migrations under `supabase/migrations/` match the 25 versions recorded in the live Supabase migration ledger. Eleven old timestamp variants are preserved under `docs/legacy-migrations/` and are not replayed.
- Eight Supabase Edge Functions are active in the project. The repository sources for `notify-incoming-call`, `get-turn-credentials`, `send-message-push`, and `cleanup-chat-media` were compared with their deployed versions and matched exactly. The first three require JWT verification; `cleanup-chat-media` uses its custom cleanup-secret header instead.
- WebRTC signaling, SDP/ICE exchange, call-session lifecycle guards, and temporary TURN credential retrieval are implemented in source. Real voice/video connectivity across two physical devices remains unverified.

## Supabase client configuration

The Android build accepts configuration from environment variables or root `local.properties`. Never put a Supabase service-role/secret key in Android code, Git, or client build output.

Root `local.properties` (do not commit this file):

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_your_key
```

The current Gradle configuration has defaults for the active project and its publishable client key; environment variables or `local.properties` override them. A publishable key is designed for client use, but every database operation still depends on correct RLS policies.

## Build and verification

Requirements: JDK 17, Android SDK platform 36/build-tools 36.0.0, Gradle 8.11.1.

```sh
gradle assembleDebug
```

If successful, the APK is at `app/build/outputs/apk/debug/app-debug.apk`. The combined Phase 1/2 source passed lint, unit tests, debug APK assembly, and artifact upload on tested commit [d3814a9](https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/39923672216). Fresh local migration replay and two-user RLS tests also passed on that commit [in this run](https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/39923672196). Current `main` commit `60a734072bf7aa852f26d65d770a1ee385329260` has all three required GitHub Actions gates green: [Android CI](https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/39927349144), [fresh Supabase migration replay and two-user RLS tests](https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/39927349041), and [full-history secret scan](https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/39927348989). The current debug APK artifact expires 2026-10-30. These CI results do not replace physical-device tests.

## Firebase / push notifications

FCM client code is now present: Firebase Messaging dependency, notification service/channel, Android notification permission request, and device-token registration after sign-in when Firebase is initialized. The live `public.device_tokens` table has RLS enabled; its migration is `supabase/migrations/20261016140058_add_device_tokens.sql`.

A JWT-protected `send-message-push` Edge Function is deployed and its source is committed at `supabase/functions/send-message-push/index.ts`. It verifies that the caller owns the message and targets only other conversation members' registered device tokens. The repository contains `app/google-services.json`, which is Firebase Android client configuration—not a service-account private key. Restrict its API key in Google Cloud Console to package `com.sayangqoemeet.app`, correct signing-certificate fingerprints, and required APIs. The sender requires the server-only `FIREBASE_SERVICE_ACCOUNT_JSON` secret; its presence cannot be proven through source inspection, and physical-device push delivery has not been verified. Never commit Firebase service-account credentials or FCM server secrets.

## Admin console

The master plan now explicitly requires a separate web-based admin console with server-verified roles, user/report management, operational health, and audit history. The current Android entry is only a locked navigation placeholder, and `admin-console/index.html` is a static locked UI shell—not a functional admin dashboard. Privileged operations must run through trusted backend endpoints; never expose service-role credentials to Android or a browser.

## Remaining release gates

- Run real-device tests with two accounts: sign-up/sign-in/session restoration, invite acceptance, message send/history, and denied cross-user access.
- Verify FCM push delivery on devices after the owner confirms `FIREBASE_SERVICE_ACCOUNT_JSON` is configured in Supabase secrets.
- Verify voice/video calls on two physical devices, including TURN relay behavior on restrictive networks.
- Restrict the Firebase Android API key in Google Cloud Console and confirm required GitHub/Supabase secret names are present/matched without exposing their values.
- Full-history Gitleaks scan passed on current `main`; continue scanning future changes and rotate any credential if one is confirmed exposed.
- Secure admin-console authentication, server-verified roles, privileged endpoints, and audit history remain separate implementation work.
- Complete responsive layout, orientation, keyboard, and production-release checks.

## Project records

- [Project status](PROJECT_STATUS.md)
- [AI handoff](AI_HANDOFF.md)
- [AI changelog](CHANGELOG_AI.md)
- [Master plan](masterplan.md)
