# AI Handoff — SayangqoeMeet

## Start here

1. Read `masterplan.md`, `PROJECT_STATUS.md`, and the latest `CHANGELOG_AI.md` entries.
2. Inspect the current GitHub source and latest GitHub Actions run before changing code.
3. Preserve the clean-start design and keep every status claim tied to actual evidence.

## Current verified state

- Private repository: `alinor2pundamdag/SayangqoeMeet`, default branch `main`.
- Android Compose foundation, neon auth UI, signed-in app shell, profile screen, chat list, conversation history/send screen, call-control screen, and locked admin entry exist in source.
- Supabase project `phjwzrrsaemgcedwpyyp` is active and healthy.
- Four application tables exist with RLS enabled: `profiles`, `conversations`, `conversation_members`, `messages`.
- Live migration history includes `20261016114000_initial_private_messaging` and `20261016134617_add_foreign_key_indexes`.
- Deployed Edge Function `create-conversation` is active with JWT verification enabled. It verifies the caller, resolves a recipient by username, creates a conversation, and inserts both membership rows using server-side credentials. Source: `supabase/functions/create-conversation/index.ts`.
- Android repository methods use the current Supabase access token for profile CRUD, conversation queries, message history, and message sending.
- Admin console is now an explicit master-plan requirement. The Android admin item is not a working admin dashboard; a separate web console and server-verified admin roles are still needed.
- No successful Android build, end-to-end two-account test, FCM delivery, Realtime test, or WebRTC call test has been confirmed.

## Highest-priority next steps

1. Run a real Android debug build from GitHub Actions or AndroidIDE. Resolve compile errors using actual logs.
2. End-to-end test auth/profile and conversation invitation with two separate accounts; verify cross-user reads/writes are denied.
3. Add Realtime subscriptions and message pagination/retry handling.
4. Configure Firebase FCM, then test notifications on devices.
5. Implement WebRTC signaling/media and test two authorized devices.
6. Build the separate admin web console with backend role enforcement and an audit log.

## Rules

- Do not say build, auth, messaging, push delivery, or calling tests passed without evidence.
- Never place Supabase service-role/secret keys or Firebase service-account credentials in Android or Git.
- Admin privileges must be verified by a trusted backend; a hidden button or client-side flag is not authorization.
- Update status/changelog/master plan after verified milestones.
