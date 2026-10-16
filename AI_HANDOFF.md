# AI Handoff — SayangqoeMeet

## Start here

1. Read `masterplan.md`, `PROJECT_STATUS.md`, and the latest `CHANGELOG_AI.md` entries.
2. Inspect current GitHub source and the latest GitHub Actions run before changing code.
3. Preserve the clean-start design and never import the old Android app wholesale.

## Current verified state

- Private repository: `alinor2pundamdag/SayangqoeMeet`, default branch `main`.
- Android Compose foundation and neon email sign-in/registration UI exist.
- Supabase Kotlin Auth repository is wired to the UI. Runtime sign-up/sign-in/session behavior is not end-to-end verified.
- Active Supabase project: `phjwzrrsaemgcedwpyyp`, URL `https://phjwzrrsaemgcedwpyyp.supabase.co`, status `ACTIVE_HEALTHY`.
- Live schema contains `profiles`, `conversations`, `conversation_members`, and `messages`; RLS is enabled on all four and they currently contain zero rows.
- Live migrations: `20261016114000_initial_private_messaging` and `20261016180000_add_foreign_key_indexes`. The latter adds the two indexes recommended by the performance advisor.
- Supabase security advisor currently has no findings; still run real RLS allowed/denied tests before treating private messaging as secure.
- Firebase/FCM is not configured: no `app/google-services.json`, Firebase Gradle plugin, messaging service/token workflow, or trusted server-side sender.
- CI/build success and device tests remain unverified.

## Highest-priority next steps

1. Check [GitHub Actions](https://github.com/alinor2pundamdag/SayangqoeMeet/actions) for the latest push-triggered Android build and fix failures based on logs.
2. Add authorization integration tests for profiles, conversations, membership, and messages; confirm users cannot join/read another user's conversation by changing request IDs.
3. Build profile creation, trusted recipient invitations, conversation list/create, message history/send, pagination, and Realtime behavior.
4. Configure Firebase Android app for package `com.sayangqoemeet.app`, then implement FCM registration, notification channels, and a trusted backend sender. Keep service-account and server credentials server-side.
5. Implement WebRTC signaling/media and test real calls between two devices.

## Rules

- Do not say authentication, migration, CI, push delivery, or runtime tests succeeded without evidence.
- Never use Supabase service-role/secret keys in Android. Keep passwords, signing keys, Firebase service-account credentials, and FCM server secrets out of Git.
- Treat the master plan as the requirements source of truth. Keep status/changelog aligned with verified work.
