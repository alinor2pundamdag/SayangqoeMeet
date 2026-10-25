# AI Handoff — SayangqoeMeet

## Read first

1. [masterplan.md](masterplan.md) — sole source of truth for requirements, priorities, constraints, and change history.
2. [PROJECT_STATUS.md](PROJECT_STATUS.md) — concise snapshot of verified baseline and unresolved risks.
3. [CHANGELOG_AI.md](CHANGELOG_AI.md) — dated evidence-based history.
4. Inspect the current source, open PRs, and latest CI for the exact commit before changing code.

## Current context

- Public repository: `alinor2pundamdag/SayangqoeMeet`, default branch `main`.
- Supabase project reference: `phjwzrrsaemgcedwpyyp`.
- Latest audit summary reports 32 matching migration versions, 14 public tables with RLS, and 10 active Edge Functions. Reverify before schema/deployment work.
- Source includes Android Compose UI, Supabase-backed messaging/invitations, FCM integration, WebRTC call signaling, temporary TURN credentials, and media cleanup. Do not infer full end-to-end reliability from source presence.
- Highest-risk work: incoming-call notification actions/duplication, foreground service during calls, stale call/ICE retention, invalid FCM token handling, Firebase key/signing restrictions, and device-level validation.
- Admin console is not to be treated as complete without trusted server-side role enforcement, defined data/schema, and audit logging.

## Hard constraints

- Free-tier services only. Do **not** pursue Supabase leaked-password protection; it is intentionally deferred for the current plan.
- Preserve features, validation, RLS, and least-privilege grants. Never run a blind production migration push.
- Never commit or paste secrets, service-account JSON, private tokens, or keystores.
- Do not claim a feature passes without exact-commit CI, source/deployment parity, or device evidence appropriate to the claim.
- Canonical brand remains dark sci-fi with cyan/electric-blue lighting; the conflicting literal rainbow proposal is retired.
- Android cannot guarantee that OS-level screen recording never captures call audio. Document platform limitations honestly.

## Execution protocol

Inspect first; identify dependencies and rollback; make one focused change; run targeted tests then exact-head CI; report changed files, commit/PR, commands and actual results, unresolved blockers, and rollback. Owner-only console configuration must use verified project/account values and must never require secrets pasted into chat.

The prioritized backlog and acceptance criteria are in the master plan. Do not create a separate roadmap in this file.
