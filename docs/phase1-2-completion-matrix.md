# Phase 1–2 Completion Matrix — SayangqoeMeet

**Audit date:** 2026-10-23  
**Repository:** `alinor2pundamdag/SayangqoeMeet`  
**Package:** `com.sayangqoemeet.app`  
**Supabase project:** `phjwzrrsaemgcedwpyyp`  
**Audit scope:** Phase 1 A01–A33 and Phase 2 A34–A62 only.

## Evidence rules

- “Verified” means backed by the current source/catalog, a successful exact-head CI run, or a reproducible automated test.
- “Runtime pending” means automated/source checks are insufficient to prove behavior on real devices or with external provider delivery.
- No indexes or product features were removed just to clear an advisor warning.
- Leaked-password protection remains explicitly out of scope and was not changed.

## Phase 1 — Android build, authentication, navigation and manifest (A01–A33)

| Task IDs | Scope | Current evidence | Status | Remaining action |
|---|---|---|---|---|
| A01–A10 | Gradle, lint, unit tests, APK artifact and CI reliability | Phase 1 PR #11 is merged. Android CI on PR #37 exact head `e10a91a3d6d69a185e888184e6793b5e68c27f17` passed lint, unit tests, debug APK assembly and artifact upload. Post-merge Android CI and Phase 2 database validation also passed on code commit `3ba1cabf92a37bc057567bf09b5cec6701beb5b6`; the later test-only PR #39 adds whole-schema assertions and passed all checks on its exact head. The post-merge main run for `cbd8bad30fc99eb371d1fc12d2d407f1ea877e75` passed Android CI, Phase 2 Database Validation and Secret History Scan. Later commits after that source/test SHA changed documentation only. | **Automated gate passed on exact PR heads and post-merge main source/test SHA** | Confirm the current main SHA checks finish green; do not substitute an older green run for a newer commit. |
| A11–A23 | Auth gate, sign-in/sign-up, session restore, callback routing, sign-out and error states | Auth repository/root navigation and signed-in shell are merged on main; CI compiles them. | **Implemented; real-device runtime pending** | Test signup, email verification/resend, sign-in, process restart/session restore, sign-out/offline sign-out, expired session and recoverable error states with a test account. |
| A24–A33 | Manifest, exported components, FCM, call service, PiP/bubbles, FileProvider and runtime permissions | Manifest declarations are present and compile in Android CI. | **Static/CI verified; device compatibility pending** | On Android 13–16, test notification permission, camera/mic denial, PiP, overlay/bubble behavior, lock-screen incoming call, and FileProvider open/share paths. |

## Phase 2 — Supabase migration parity, RLS/RPC, triggers and performance (A34–A62)

| Task IDs | Scope | Current evidence | Status | Remaining action |
|---|---|---|---|---|
| A34–A42 | Migration history, ledger parity and safe replay | Main has **28** timestamped migration files; live Supabase has **28** ledger versions. The sorted version lists match exactly after PR #37. Fresh disposable Supabase migration replay, SQL lint, two-user RLS tests and Deno Edge Function type-check passed on PR #37 exact head `e10a91a3d6d69a185e888184e6793b5e68c27f17`. PR #39 then added generic assertions for RLS/policies across public tables, valid leading indexes for every public FK, pinned `search_path` and no `anon` EXECUTE for every public SECURITY DEFINER function, and validity of the admin FK index; its exact-head database workflow passed. | **Verified on PR #37, PR #39 and post-merge main source/test SHA** | Confirm the post-merge main Phase 2 validation run succeeds. Never run `supabase db push` blindly or replay old unledgered files from `docs/legacy-migrations/`. |
| A43–A55 | RLS, table grants, SECURITY DEFINER functions, identity and private data | Live catalog query: **14/14 public tables have RLS enabled; 0 without policies**. All **9** public SECURITY DEFINER functions have a pinned `search_path`; **0** are executable by `anon`. The three authenticated-callable profile/conversation/device-token RPCs require authentication and derive identity from `auth.uid()`. The two-user isolation suite passes in disposable CI. Private `chat-attachments` bucket and service-only media tables remain protected. | **Catalog and automated regression checks verified; three intentional advisor warnings documented** | Keep the negative cross-user and server-owned-column tests. Do not revoke the three RPCs or convert them to invoker without validating the app flows they serve. |
| A56–A62 | FK indexes, representative query plans, triggers and unused-index warnings | Live catalog query: **25/25 foreign keys have valid leading indexes; 0 missing**. The new `admin_memberships_granted_by_idx` is valid/ready. Read-only plans use call-session and media-cleanup indexes; admin membership lookup uses its primary key. Message paging chose a sequential scan for the current tiny table (about 15 estimated rows), while `messages_conversation_created_idx` has recorded use. Call lifecycle, message integrity and media cleanup triggers are present. | **Core schema/performance checks verified; workload review remains** | Preserve the **13** “unused index” notices until representative production traffic/query statistics establish whether any specific index is redundant. Do not delete indexes based only on `idx_scan = 0`. |

### Exact migration parity verified

The repository and live ledger both contain these 28 version prefixes, in order:

`20261016114000`, `20261016134617`, `20261016140058`, `20261016140546`, `20261017175440`, `20261018065021`, `20261018185347`, `20261018185357`, `20261018185402`, `20261019065501`, `20261020061237`, `20261020085405`, `20261020121714`, `20261020121824`, `20261021135034`, `20261021135259`, `20261021155023`, `20261021160316`, `20261022025147`, `20261022025219`, `20261022025251`, `20261022025320`, `20261022025350`, `20261022032347`, `20261022041708`, `20261023100834`, `20261023100920`, `20261023101814`.

## Owner/device gates that automation cannot truthfully complete

1. **Physical Android smoke tests:** auth/session and Android 13–16 notification, PiP, call, permission and FileProvider behavior. CI is not a physical-device substitute.
2. **Provider/dashboard checks:** verify Firebase Android API-key restrictions for package `com.sayangqoemeet.app` and correct debug/release signing SHA fingerprints. Never expose service-account credentials.
3. **Final release sign-off:** only after the device checks and provider settings are confirmed.

These gates are explicitly separated from the source/database tasks; no one should claim they passed without the required evidence.

## Changes performed during this audit

- Merged Phase 1 PR #11 and Phase 2 reconciliation PR #16.
- Merged PR #34 to align the idempotency migration and add the admin membership foreign-key index migration; merged UI-state fix PR #35 and safe cleanup dry-run PR #36.
- Applied the additive `admin_memberships_granted_by_idx` migration to Supabase, then merged PR #37 so the repository migration version matches the live ledger's `20261023101814` version.
- Verified the live migration ledger and repository migration version lists are identical (28/28).
- Rechecked RLS, SECURITY DEFINER grants/search paths, FK index coverage, selected query plans, Storage bucket privacy and triggers.
- Ran full Git-history and current-file secret scanning successfully on the exact PR #37 head. A clean Gitleaks run is evidence of no detected patterns, not proof that credentials were never exposed or that provider settings are correct.
- No database objects or app features were deleted. No index was dropped. No production data was rewritten. Leaked-password protection was not modified.

## Completion decision

**Phase 1:** source/build gate is passed; **real-device auth and Android compatibility checks remain**.  
**Phase 2:** migration parity and core catalog/RLS/performance checks are passed; **workload-aware review of unused indexes remains**.

Do not label either phase “100% production-ready” until its stated gates are evidenced. The remaining work is now narrowed to the explicit gates above rather than unresolved migration divergence or missing FK indexes.
