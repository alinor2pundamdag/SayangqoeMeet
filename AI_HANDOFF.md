# AI Handoff — SayangqoeMeet

## Start here

1. Read `masterplan.md`, `PROJECT_STATUS.md`, and the latest `CHANGELOG_AI.md` entries.
2. Inspect the current GitHub source and the latest CI run before changing code.
3. Preserve the clean-start design and do not import the old Android app wholesale.

## Current verified state

- Private repository: `alinor2pundamdag/SayangqoeMeet`, default branch `main`.
- Android Compose foundation and neon auth UI exist.
- Supabase Kotlin Auth client/repository and UI calls are committed. The client reads `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` from environment or root `local.properties`.
- A Postgres schema/RLS migration is committed in `supabase/migrations/`, but **not applied** to the hosted database.
- Supabase MCP currently shows no accessible projects and access to the historical project ref was denied. Restore access before any remote database action.
- Build status is unverified; no successful CI/build or device test has been confirmed.
- Messaging/realtime, invitation flow, FCM, WebRTC and profile UX remain incomplete.

## Next steps

1. Verify the intended Supabase project and publishable key via the authenticated Supabase connection; never use service-role keys in Android.
2. Apply the migration to the correct project, run security advisors, and verify tables/RLS with read-only checks.
3. Inspect GitHub Actions status/logs and repair any actual compile failures.
4. Finish profile creation, safe conversation invitation, and private realtime messaging before adding FCM/WebRTC.

## Rules

- Do not say authentication, migration, CI, or runtime tests succeeded without evidence.
- Keep secrets out of Git; update project status/changelog after verified milestones.
- Follow `masterplan.md` and maintain private-by-default row-level security.
