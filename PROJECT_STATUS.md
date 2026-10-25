# SayangqoeMeet — Current Project Status

**Snapshot:** 2026-10-25  
**Repository:** `alinor2pundamdag/SayangqoeMeet` (default branch `main`)  
**Requirements authority:** [masterplan.md](masterplan.md)  
**Deduplicated findings/fix register:** [AUDIT_REPORT_2026-10-25.md](AUDIT_REPORT_2026-10-25.md)  
**Scope:** Free-tier services only. Supabase leaked-password protection is intentionally deferred and is not a task.

## Status
**Automated source validation passed on PR #61's exact head; release readiness is not established.** Call-notification, active-call service, FCM token cleanup, retention, cleanup-secret, and read-only admin-console changes are implemented in the open source PR. Physical-device, live-provider, retention-approval, and owner-console checks remain.

The previously reported main/live baseline was 32 repository migrations matching 32 live migration versions, 14 public tables with RLS enabled, and 10 active Edge Functions. PR #61's current source branch contains 36 migration files (four additional forward migrations not assumed deployed). Recheck the exact current commit and live migration ledger before schema/deployment work.

## Open PRs at the inspected snapshot
- [PR #61](https://github.com/alinor2pundamdag/SayangqoeMeet/pull/61) — combined media/call UX, push hardening, retention, and admin-console changes; open, draft, unmerged.
- [PR #62](https://github.com/alinor2pundamdag/SayangqoeMeet/pull/62) — closed as superseded because its UUID-validation changes are incorporated into #61.
- [PR #64](https://github.com/alinor2pundamdag/SayangqoeMeet/pull/64) — documentation/master plan and deduplicated report; open/unmerged.

Re-fetch current PR head SHAs and CI immediately before merge. Green CI on an older/different head is not proof for the current main branch, live deployment, or physical-device behavior.

## Current implementation progress
PR #61's source branch now contains the following changes, not yet merged or release-verified:
1. One call-specific notification identity with Accept/Decline actions, strict payload validation, and authoritative recipient/ringing-state checks.
2. Active-call foreground-service mode with microphone/camera declarations and lifecycle wiring.
3. Provider-confirmed FCM UNREGISTERED token cleanup, plus Deno tests that reject transient and ambiguous errors.
4. Call-signaling indexes/grants and bounded cleanup: stale ringing calls become missed, abandoned accepted/connecting calls become failed, and terminal retention is opt-in (30 days; CALL_RETENTION_ENABLED must remain false/unset until reviewed).
5. Equivalent UUID-validation changes from PR #62 are incorporated into PR #61's source branch.
6. The web admin console now performs real server-verified read-only overview/audit requests and requires TOTP MFA AAL2; moderation/suspension/live media review remain disabled pending approved policies/schema.
7. Cleanup endpoints use a no-early-exit secret comparison helper with unit tests.

## Current highest-priority actions
1. Automated checks on exact source head 0758ea05110fa63ee30329781cca587240199e43 passed; keep the PR draft until remaining gates are satisfied.
2. Run physical-device notification/Accept/Decline and two-device voice/video tests on supported Android versions.
3. Review retention dry-run counts and rollback, approve the 30-day policy, and only then set CALL_RETENTION_ENABLED=true if appropriate.
4. Owner verifies Firebase configuration/signing restrictions, server-only credentials, cleanup scheduler/secret, and provisions an intended admin Auth UUID with MFA out-of-band.
5. Merge the docs-only PR #64 after its latest checks pass; merge PR #61 only after required device and owner-side gates are met.

## Latest exact-head validation
For PR #61 source head 0758ea05110fa63ee30329781cca587240199e43, Android CI run 633 passed (lint, unit tests, debug APK assembly/upload), Phase 2 Database Validation run 294 passed (fresh empty-database migration replay, two-user RLS isolation, Deno type-check/unit tests, and admin-console JavaScript syntax validation), and Secret History Scan run 213 passed.

## Verification limits
This is not a full re-audit of every source file, deployed function, workflow, console setting, or physical device. See the audit report for the exact inspected scope, evidence boundaries, owner-only actions, and release gates. Do not mark unverified features complete.
