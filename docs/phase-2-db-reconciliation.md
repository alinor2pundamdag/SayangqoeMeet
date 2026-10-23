# Phase 2 Database Reconciliation and Security Audit

**Audit date:** 2026-10-23  
**Supabase project:** `phjwzrrsaemgcedwpyyp`  
**Target branch:** `main` (Phase 1 PR #11 is merged)  
**Working branch:** `fix/phase-2-db-reconciliation-on-phase-1`  
**Status:** Validation is in progress; this document is not a release approval.

## Migration ledger reconciliation

The live `supabase_migrations.schema_migrations` ledger contains 25 versions:

- `20261016114000`
- `20261016134617`
- `20261016140058`
- `20261016140546`
- `20261017175440`
- `20261018065021`
- `20261018185347`
- `20261018185357`
- `20261018185402`
- `20261019065501`
- `20261020061237`
- `20261020085405`
- `20261020121714`
- `20261020121824`
- `20261021135034`
- `20261021135259`
- `20261021155023`
- `20261021160316`
- `20261022025147`
- `20261022025219`
- `20261022025251`
- `20261022025320`
- `20261022025350`
- `20261022032347`
- `20261022041708`

The Phase 2 branch contains 25 migration files whose version prefixes match this ledger. `main` still has 19 files with historical filenames that do not align with the hosted ledger. The reconciliation retains the SQL effects while normalizing filenames and reconstructing schema pieces needed for a clean replay. The attachment object-path policies compare UUID text safely and do not cast untrusted path segments to UUID.

**Safety:** This audit did not execute production DDL, run `supabase db push`, delete live indexes, or change live RLS grants. Fresh replay and test execution happen against a disposable local Supabase stack in CI.

## Current CI evidence

For commit `670c87ba3fcf27b59490a94dd4dc69d40d62e3d7`:

- Deno type-checking for push Edge Functions: passed.
- Disposable Supabase startup: passed.
- Full migration replay from an empty database: passed.
- `supabase db lint --local --fail-on error` and the two-user authenticated RLS isolation test: passed.
- Job cleanup and the separate Android CI run were still in progress at the time of this update. Do not report the full run as complete until GitHub records a final conclusion for this exact SHA.

Phase 1 PR #11 is merged to `main`. Android CI passed lint, unit tests, debug APK assembly, and artifact upload for its merged head. Physical-device auth, FCM delivery, notification permissions, and two-device calling still require smoke tests.

## Live database authorization findings

- All 12 inspected public application tables have RLS enabled; 26 policies cover those tables.
- The live catalog contains 23 foreign-key constraints. A catalog check found a valid leading index covering each FK column.
- The security advisor flags three authenticated-callable `SECURITY DEFINER` RPCs: `get_my_profile()`, `get_my_conversation_list()`, and `register_device_token(text,text)`. Live ACL inspection found no `anon` EXECUTE privilege on these functions; each has `search_path = public, pg_temp`.
- `get_my_profile()` filters by `auth.uid()`; `get_my_conversation_list()` rejects a null caller and limits rows to the caller's memberships; `register_device_token()` derives the owner from `auth.uid()`, validates token/platform inputs, and atomically transfers an installation token after account switching. Keep these functions only with authenticated identity/negative-path tests; do not change grants merely to silence an advisor warning.
- The security advisor also reports RLS/authorization lint findings for service-only media bookkeeping tables in earlier snapshots. Those tables must remain inaccessible to `anon` and `authenticated`; do not add broad client policies.
- The performance advisor reports 10 unused-index findings. No index was dropped: usage counters alone are insufficient to justify removal. Validate real workload/query plans and foreign-key coverage first.

## Backend-to-frontend checks still required

1. Current-head Android CI must finish successfully.
2. Install the built APK and verify sign-in, session restore, sign-out while offline, email verification callback, notification permission prompts, token reassignment after account switching, incoming-call timeout/decline, and voice/video calls between two real accounts/devices.
3. Confirm the deployed `get-turn-credentials`, `notify-incoming-call`, `send-message-push`, and `create-conversation` Edge Functions match the reviewed branch behavior.
4. Verify the GitHub Actions secret `MEDIA_CLEANUP_CRON_SECRET` matches the Supabase `cleanup-chat-media` function secret without exposing either value. The latest scheduled cleanup workflow failed, but its log artifact was unavailable during this audit, so the exact failure cause remains unverified.
5. Because the repository is public, scan all Git refs/history, tags, branches, and PR diffs for credentials. Rotate any confirmed exposed credential. Restrict the Firebase Android API key to package `com.sayangqoemeet.app`, the correct signing certificate fingerprints, and required APIs; client Firebase configuration is not a service-account private key.
6. Re-run Supabase Security and Performance Advisors after code review; retain unused indexes unless workload evidence proves they are redundant.

## Merge and release gate

PR #13 must be ported onto current `main` to remove its reported merge conflicts. Merge only after the clean branch has green current-head Android CI and Phase 2 database validation, all changed migration effects have been reviewed, and the device/backend smoke tests are recorded. Never run a blanket production migration push to resolve a local replay issue.

Leaked-password protection is deliberately outside this work scope and was not changed by this audit.
