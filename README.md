# SayangqoeMeet

A privacy-conscious Android communication app guided by [masterplan.md](masterplan.md).

## Implemented in source

- Kotlin Android app using Jetpack Compose with a sci-fi neon sign-in/registration screen.
- Successful sign-in routes to an app shell with Home, Profile, Chats, Conversation, Call, and a locked Admin Console entry.
- Profile loading/saving uses the authenticated Supabase session and PostgREST.
- Conversation list and message history/sending use authenticated PostgREST requests protected by RLS.
- Private chat invitations use the deployed `create-conversation` and `respond-conversation-invite` Supabase Edge Functions. The creator becomes a member and the recipient remains outside the conversation until accepting the invitation; both functions verify the caller's JWT and use server-side credentials for privileged changes.
- Supabase project is active at `https://phjwzrrsaemgcedwpyyp.supabase.co`. The live schema contains `profiles`, `conversations`, `conversation_members`, `messages`, `device_tokens`, and `conversation_invites`, all with RLS enabled.
- Migrations are committed under `supabase/migrations/`, including `20261016134617_add_foreign_key_indexes.sql`, `20261016140058_add_device_tokens.sql`, and `20261016140546_add_conversation_invites.sql`.
- Edge Function sources are committed at `supabase/functions/create-conversation/index.ts`, `supabase/functions/respond-conversation-invite/index.ts`, and `supabase/functions/send-message-push/index.ts`; all are deployed to the active project with JWT verification enabled.
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

FCM client code is now present: Firebase Messaging dependency, notification service/channel, Android notification permission request, and device-token registration after sign-in when Firebase is initialized. The live `public.device_tokens` table has RLS enabled; its migration is `supabase/migrations/20261016140058_add_device_tokens.sql`.

A JWT-protected `send-message-push` Edge Function is deployed and its source is committed at `supabase/functions/send-message-push/index.ts`. It is designed to verify that the caller owns the message and target only other conversation members' registered device tokens. The repository currently contains `app/google-services.json`, which is Firebase Android client configuration; it is not a substitute for, and must never contain, a Firebase service-account private key. Restrict the Firebase API key in Google Cloud Console to this Android package and only the APIs it needs. The presence of the config file does not prove that the Firebase service-account secret is configured in Supabase or that push delivery works; those settings and end-to-end delivery must be verified separately. Never commit Firebase service-account credentials, FCM server secrets, or Supabase service-role/secret keys. See [SECURITY.md](SECURITY.md).

## Admin console

The master plan now explicitly requires a separate web-based admin console with server-verified roles, user/report management, operational health, and audit history. The current Android entry is only a locked navigation placeholder, and `admin-console/index.html` is a static locked UI shell—not a functional admin dashboard. Privileged operations must run through trusted backend endpoints; never expose service-role credentials to Android or a browser.

## Not yet implemented / verified

- Successful Android CI/debug build and runtime layout verification.
- End-to-end authentication/session restoration and device tests.
- Supabase Realtime subscriptions, pagination, message retry/deduplication, and robust offline behavior.
- Automated RLS authorization tests for allowed and denied user paths.
- Firebase Android project config, Google Services plugin configuration, service-account secret setup, trusted push delivery, and device notification tests.
- WebRTC signaling/media, STUN/TURN configuration, and two-device voice/video tests.
- Separate web admin console, server-verified admin role assignment, audit log, and privileged endpoints.
- Production release hardening and responsive UI test matrix.

## Project records

- [Project status](PROJECT_STATUS.md)
- [AI handoff](AI_HANDOFF.md)
- [AI changelog](CHANGELOG_AI.md)
- [Master plan](masterplan.md)
