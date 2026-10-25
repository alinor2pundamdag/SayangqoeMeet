# AI Handoff — SayangqoeMeet

## Read first
1. [masterplan.md](masterplan.md) — sole source of truth for approved requirements, priorities, constraints, and change history.
2. [AUDIT_REPORT_2026-10-25.md](AUDIT_REPORT_2026-10-25.md) — deduplicated findings, evidence, owners, implementation locations, cross-layer dependencies, and acceptance criteria.
3. [PROJECT_STATUS.md](PROJECT_STATUS.md) — concise verified snapshot and open risks.
4. [CHANGELOG_AI.md](CHANGELOG_AI.md) — append-only dated history.

## Current context
- Repository: `alinor2pundamdag/SayangqoeMeet`; default branch `main`.
- Supabase project reference: `phjwzrrsaemgcedwpyyp`.
- The latest audit summary reports 32 repository migrations matching 32 live migration versions, 14 public tables with RLS enabled, and 10 active Edge Functions. These are audit-reported baselines and must be rechecked against the exact current source/live environment before deployment.
- Source includes Android Compose UI, Supabase-backed messaging/invitations, FCM integration, WebRTC call signaling, temporary TURN credential retrieval, and media cleanup. Source presence is not proof of end-to-end behavior.
- PRs #61, #62, and #64 were open/unmerged at the latest inspected snapshot. Re-fetch their exact head SHAs, changed files, and CI immediately before integration or merge.

## Hard constraints
- Free-tier services only. Supabase leaked-password protection is intentionally excluded; do not list it as a fix, blocker, or owner action.
- Latest approved header requirement: a local, continuous, smooth infinite loop through neon blue → neon green → neon yellow → neon pink → neon red. This supersedes earlier statements rejecting literal rainbow animation. No network/backend request is needed for the animation.
- Keep the broader sci-fi cyan/blue identity for other UI surfaces where appropriate; do not make every component animate.
- Preserve features, validation, RLS, and least-privilege grants. Never run a blind production migration push.
- Never commit or paste service-role keys, Firebase service-account JSON, private tokens, or signing keystores.
- Do not claim a feature passes without exact-commit CI, source/deployment parity, or appropriate device evidence.
- Admin privileges must be enforced by trusted backend authorization, not hidden UI/client flags.
- Android cannot guarantee that OS-level screen recording never captures call audio. Explain platform limitations accurately.

## Execution protocol
Inspect exact source, branch, open PRs, and current CI first. Consolidate duplicate symptoms into one finding ID and one owner/action path. Make focused, reviewable changes; check frontend-to-backend and backend-to-frontend flows; run targeted tests and exact-head CI; verify live configuration/deployment only when authorized and safe. Report changed files, commit/PR, commands and actual results, evidence gaps, owner-only actions, and rollback. Do not mark planned work as fixed.

The full deduplicated issue/fix matrix and acceptance gates are in [AUDIT_REPORT_2026-10-25.md](AUDIT_REPORT_2026-10-25.md). Do not create a competing roadmap in this file.
