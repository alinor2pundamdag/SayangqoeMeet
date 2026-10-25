# SayangqoeMeet — Current Project Status

**Snapshot:** 2026-10-25  
**Repository:** `alinor2pundamdag/SayangqoeMeet` (default branch `main`)  
**Requirements authority:** [masterplan.md](masterplan.md)  
**Deduplicated findings/fix register:** [AUDIT_REPORT_2026-10-25.md](AUDIT_REPORT_2026-10-25.md)  
**Scope:** Free-tier services only. Supabase leaked-password protection is intentionally deferred and is not a task.

## Status
**Release readiness is not established.** The audit identified source-level incoming-call notification duplication, an active-call foreground-service lifecycle/type gap to resolve, provider-side invalid FCM token cleanup missing from the inspected Edge Functions, call/ICE retention requiring schema-led review, and unverified end-to-end/device and owner-console configuration.

The audit-reported baseline is 32 repository migrations matching 32 live migration versions, 14 public tables with RLS enabled, and 10 active Edge Functions. Recheck these against the exact current commit and live environment before schema/deployment work.

## Open PRs at the inspected snapshot
- [PR #61](https://github.com/alinor2pundamdag/SayangqoeMeet/pull/61) — media/call UX plus database changes; open/unmerged. Its exact head had reported-success Android CI, Phase 2 database validation, and secret scan.
- [PR #62](https://github.com/alinor2pundamdag/SayangqoeMeet/pull/62) — push payload UUID validation; open/unmerged. Its exact head had reported-success Android CI and secret scan.
- [PR #64](https://github.com/alinor2pundamdag/SayangqoeMeet/pull/64) — documentation/master plan; open/unmerged. The docs-only branch is being updated with this deduplicated report and the latest approved rainbow-header requirement.

Re-fetch current PR head SHAs and CI immediately before merge. Green CI on an older/different head is not proof for the current main branch, live deployment, or physical-device behavior.

## Current implementation progress
PR #61's source branch now contains the following changes, not yet merged or release-verified:
1. One call-specific notification identity with Accept/Decline actions, strict payload validation, and authoritative recipient/ringing-state checks.
2. Active-call foreground-service mode with microphone/camera declarations and lifecycle wiring.
3. Provider-confirmed FCM UNREGISTERED token cleanup, plus Deno tests that reject transient and ambiguous errors.
4. Call-signaling indexes/grants and bounded cleanup: stale ringing calls become missed, abandoned accepted/connecting calls become failed, and terminal retention is opt-in (30 days; CALL_RETENTION_ENABLED must remain false/unset until reviewed).
5. Equivalent UUID-validation changes from PR #62 are incorporated into PR #61's source branch.
6. The web admin console now performs real server-verified read-only overview/audit requests; moderation/suspension/live media review remain disabled pending approved policies/schema.
7. Cleanup endpoints use a no-early-exit secret comparison helper with unit tests.

## Current highest-priority actions
1. Pass latest exact-head Android CI, Phase 2 fresh migration replay/RLS suite, and Secret History Scan.
2. Fix any newly reported CI failures; do not treat the earlier FCM helper test failure as current once the corrected head's run is complete.
3. After PR #61 checks pass, close #62 as superseded and reconcile/merge documentation PR #64 without conflicting source changes.
4. Run physical-device notification/Accept/Decline and two-device voice/video tests on supported Android versions.
5. Review retention dry-run counts and rollback, approve the 30-day policy, and only then set CALL_RETENTION_ENABLED=true if appropriate.
6. Owner verifies Firebase configuration/signing restrictions, server-only credentials, cleanup scheduler/secret, and provisions an intended admin Auth UUID out-of-band.

## Verification limits
This is not a full re-audit of every source file, migration, deployed function, workflow, console setting, or physical device. See the audit report for the exact inspected scope, evidence boundaries, owner-only actions, and release gates. Do not mark unverified features complete.
