# SayangqoeMeet

A privacy-conscious Android communication app guided by [masterplan.md](masterplan.md).

## Implemented in source

- Kotlin Android app using Jetpack Compose with a sci-fi neon sign-in/registration screen.
- Successful sign-in routes to an app shell with Home, Profile, Chats, Conversation, Call, and a locked Admin Console entry.
- Profile loading/saving uses the authenticated Supabase session and PostgREST.
- Conversation list and message history/sending use authenticated PostgREST requests protected by RLS.
- Private chat invitations use the deployed `create-conversation` and `respond-conversation-invite` Supabase Edge Functions. The creator becomes a member and the recipient remains outside the conversation until accepting the invitation; both functions verify the caller's JWT and use server-side credentials for privileged changes.
- Supabase project is active at `https://phjwzrrsaemgcedwpyyp.supabase.co`. The live schema has 12 public application tables; all inspected tables have RLS enabled and policies. The two-user disposable RLS isolation suite is part of Phase 2 CI.
- The canonical `supabase/migrations/` chain contains 25 migration versions matching the live Supabase ledger exactly. Eleven repository-only legacy migrations are preserved under `docs/legacy-migrations/` outside the executable migration directory.
- Edge Function sources are committed at `supabase/functions/create-conversation/index.ts`, `supabase/functions/respond-conversation-invite/index.ts`, and `supabase/functions/send-message-push/index.ts`; all are deployed to the active project with JWT verification enabled.
- WebRTC signaling, peer-connection setup, call lifecycle handling, and dynamic TURN credential retrieval are implemented in source. Real two-device voice/video connectivity remains unverified until physical-device smoke tests pass.

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

If successful locally, the APK is at `app/build/outputs/apk/debug/app-debug.apk`. Combined Phase 1/Phase 2 validation passed on commit `d3814a9620dfe4bd06b65144cc58b3f033b0bd20`: [Android CI](https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/39923672216) and [Phase 2 database validation](https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/39923672196). Physical-device behavior remains unverified.

## Firebase / push notifications

FCM client code is now present: Firebase Messaging dependency, notification service/channel, Android notification permission request, and device-token registration after sign-in when Firebase is initialized. The live `public.device_tokens` table has RLS enabled; its migration is `supabase/migrations/20261016140058_add_device_tokens.sql`.

A JWT-protected `send-message-push` Edge Function is deployed and its source is committed at `supabase/functions/send-message-push/index.ts`. It is designed to verify that the caller owns the message and target only other conversation members' registered device tokens. The repository currently contains `app/google-services.json`, which is Firebase Android client configuration; it is not a substitute for, and must never contain, a Firebase service-account private key. Restrict the Firebase API key in Google Cloud Console to this Android package and only the APIs it needs. The presence of the config file does not prove that the Firebase service-account secret is configured in Supabase or that push delivery works; those settings and end-to-end delivery must be verified separately. Never commit Firebase service-account credentials, FCM server secrets, or Supabase service-role/secret keys. See [SECURITY.md](SECURITY.md).

## Admin console

The master plan now explicitly requires a separate web-based admin console with server-verified roles, user/report management, operational health, and audit history. The current Android entry is only a locked navigation placeholder, and `admin-console/index.html` is a static locked UI shell—not a functional admin dashboard. Privileged operations must run through trusted backend endpoints; never expose service-role credentials to Android or a browser.

## Not yet implemented / verified

- Physical-device runtime/layout verification, including compact/large layouts, orientation, and keyboard behavior.
- End-to-end authentication/session restoration and device tests.
- Supabase Realtime subscriptions, pagination, message retry/deduplication, and robust offline behavior.
- Additional production-account authorization tests beyond the disposable two-user RLS isolation suite that passed in CI.
- Restrict the Firebase Android API key in Google Cloud Console; verify the server-only `FIREBASE_SERVICE_ACCOUNT_JSON` secret; and test actual FCM delivery/notification behavior on devices.
- Verify dynamic TURN credential issuance and successful voice/video media connectivity between two authorized physical devices, including restrictive networks.
- Separate web admin console, server-verified admin role assignment, audit log, and privileged endpoints.
- Production release hardening and responsive UI test matrix.

## Project records

- [Project status](PROJECT_STATUS.md)
- [AI handoff](AI_HANDOFF.md)
- [AI changelog](CHANGELOG_AI.md)
- [Master plan](masterplan.md)
