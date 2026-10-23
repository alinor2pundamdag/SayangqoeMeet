# SayangqoeMeet — Phase 1–4 Completion Matrix

**Audit date:** 2026-10-23  
**Repository:** `alinor2pundamdag/SayangqoeMeet`  
**Default branch:** `main`  
**Package:** `com.sayangqoemeet.app`  
**Supabase project:** `phjwzrrsaemgcedwpyyp`

## Status rules

- **Verified** means there is current source/catalog/test/CI evidence for the exact stated scope.
- **Implemented; runtime pending** means source exists and automated checks may pass, but live device/backend behavior has not been demonstrated.
- **Partial** means some functionality exists but one or more requirements in the group are missing.
- **Owner/device gate** means the remaining check requires a dashboard account, secret configuration, admin-policy decision, physical device, second test account, or release approval.
- No task is complete merely because a screen, method, migration, or workflow file exists.

## Phase 1 — Android build, authentication, navigation, manifest (A01–A33)

| IDs | Scope from the task brief | Current finding | Status / evidence |
|---|---|---|---|
| A01–A10 | CI failures, lint, Gradle/build config, APK artifact | Android CI runs lint, unit tests, debug assembly, renames and uploads the APK. Current main had a successful run at `97c18d4`; PR #28 also passed on its exact head before merge. | **Automated gate verified**. Post-merge CI should be checked against the latest main SHA after all merges. |
| A11–A23 | Root composable, auth navigation, sign-in/up, session restore, sign-out, errors | Auth gate/repository, callback routing and signed-in shell exist in source. | **Implemented; runtime pending**. Must smoke-test signup, email verification/resend, sign-in, restore, sign-out/offline sign-out, expired session and error states on device. |
| A24–A33 | Manifest, FCM, call service, bubbles, FileProvider, permissions/exported/version behavior | Manifest declares the launcher/deep-link activity, FCM service, call/bubble components and non-exported FileProvider; Android CI compiles it. | **Static/CI verified; device compatibility pending**. Verify Android 13–16 notification, camera/mic denial, overlay permission, PiP, lock-screen incoming call and FileProvider opening on real devices. |

## Phase 2 — Supabase migration parity, RLS/RPC and performance (A34–A62)

| IDs | Scope from the task brief | Current finding | Status / evidence |
|---|---|---|---|
| A34–A42 | Repository migration files vs live ledger, drift and safe migration plan | The live ledger has 25 versions. The merged PR #32 adds one additive executable migration (`20261023100000_message_client_request_idempotency.sql`), so `main` now has 26 executable migrations while production remains at 25 until that migration is applied. Eleven unledgered historical files are preserved verbatim under `docs/legacy-migrations/`, outside the executable migration directory. | **Replay verified; production parity pending.** PR #32's exact head passed a fresh 26-migration replay and RLS/idempotency suite. No production DDL/data migration has been executed; apply the additive migration in order after review, then verify the live ledger and column/index/grant. |
| A43–A55 | RLS, SECURITY DEFINER, EXECUTE grants, identity and sensitive access | All 12 inspected public application tables have RLS enabled and policies. Three authenticated-callable SECURITY DEFINER RPCs were reviewed; search paths are pinned and anon execution is denied. The two-user isolation suite passed on commit `60a7340`. | **Core checks verified; advisor disposition documented**. Keep the three RPCs only while their authenticated app behavior is required; retain negative cross-user tests. |
| A56–A62 | FK indexes, RLS query plans, unused indexes, triggers and slow queries | All 23 inspected foreign keys have valid child-side indexes. Read-only plans used the expected conversation-message, call-session and media-cleanup indexes. Ten unused-index advisor notices remain. | **Mostly verified; workload review pending**. Do not drop indexes from `idx_scan=0` alone; review workload window and representative production query stats first. |

**Phase 2 CI evidence:** [PR #32 fresh migration replay, SQL lint, RLS/idempotency isolation and Deno checks](https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/39935772550) passed on exact head `e21eacf9225227833e3993fae1f96a14191a81b8`. PR #33 adds the admin migration and must pass the same checks before merge. Production still requires ordered application of the additive migrations and live catalog verification.

## Phase 3 — Messaging, Realtime, FCM and WebRTC (A63–A99)

| IDs | Scope from the task brief | Current finding | Status / next action |
|---|---|---|---|
| A63–A73 | Conversations/invitations/messages, deletion boundaries, pagination, retry and duplicate prevention | Conversation/invitation APIs, authenticated send, Realtime refresh and soft-delete boundary exist. PR #28 merged newest-first 100-message loading, stable cursor paging for older messages, sender-only soft-delete UI, and honest unknown-presence status. PR #32 added idempotent text-message retry using `client_request_id`; its Android CI and fresh database replay/RLS suite passed before merge. | **Partial**. Add/verify idempotency for ambiguous message retries; test invite accept/decline races, offline retry, cross-user denial, deletion propagation and long histories with two accounts. |
| A74–A80 | Realtime subscriptions, presence, reconnect, lifecycle and stale presence | Reconnecting WebSocket subscriptions use bounded exponential backoff and are closed on disposal. Presence heartbeat is 30 seconds and online freshness is 90 seconds. UI now distinguishes unverified presence from confirmed offline in PR #28. | **Implemented; runtime pending**. Test network loss/restore, token expiry, app background/foreground, disposal and stale rows on real devices. |
| A81–A89 | Firebase config/token registration/push authorization/payload/refresh/incoming-call and message notifications | FCM service, device-token RPC and active JWT-verified push functions exist; full-history secret scan passes. | **Implemented; external configuration and delivery pending**. Owner must verify Firebase Android key restrictions and correct SHA fingerprints; verify service-account secret configuration without exposing values; test foreground/background/killed-app delivery and token rotation. |
| A90–A99 | WebRTC signaling, ICE, TURN, mic/camera permissions, foreground behavior and cleanup | Call sessions, ICE candidate polling, native WebRTC manager and active JWT-protected TURN credential function exist. Call-state triggers and participant checks are present. | **Implemented; runtime pending**. Two-device voice/video tests, restrictive-NAT relay-candidate verification, permission denial, timeout, reconnect, hang-up and resource cleanup are not proven by CI. |

### User-dependent Phase 3 tests

- **U08–U15: Pending** — two test accounts/devices are required for invitation, messaging, Realtime, FCM and voice/video/TURN verification. CI cannot substitute for delivery or media connectivity.

## Phase 4 — Media lifecycle, frontend integration, admin and release evidence (A100–A136)

| IDs | Scope from the task brief | Current finding | Status / next action |
|---|---|---|---|
| A100–A110 | Private attachments, avatars, upload/download authorization, confirmation, cleanup and retry | `chat-attachments` is private; Storage policies check conversation membership and uploader UUID path; downloads use short-lived signed URLs and private cache; recipient confirmation occurs only after a complete atomic download. PR #32 adds rollback for a definite rejected message insert after upload, while preserving files when commit outcome is ambiguous. | **Partial; media rollback and idempotency are merged, live cleanup verification pending.** Verify cleanup cron secret presence/match without reading its value, inspect scheduler execution, test upload/download/unauthorized denial/failed upload/retry and media purge on test data. |
| A111–A119 | Compose navigation/screens, backend integration, loading/error/empty/offline states, accessibility and lifecycle | Core Compose screens connect to Auth, profiles, people, chats and calls. Some loading/error/empty states exist. | **Partial**. Run a screen-by-screen UI audit for offline/retry semantics, accessibility labels/focus, keyboard/insets, lifecycle leaks and misleading status indicators; add automated UI tests where practical. |
| A120–A126 | Admin screen, admin authorization, privileged operations and audit logging | Android AdminConsoleScreen is being replaced in PR #33 with a server-authorized read-only overview and audit feed. The PR adds service-only membership/audit tables and a JWT-verified Edge Function; no admin is seeded. Web `admin-console/index.html` remains a static preview, not a live-connected web console. | **In implementation; owner bootstrap pending.** PR #33 must pass exact-head Android, Deno, fresh migration replay, SQL lint, RLS/admin-grant assertions and secret scan. After merge/deployment, the owner must provision the intended Supabase Auth UUID. The console remains read-only; moderation writes and retention purge stay disabled until separately approved. |
| A127–A136 | Regression tests, setup/migration/CI/troubleshooting docs and final audit | Android unit tests currently cover call URL routing and push-recipient guard; Phase 2 has a two-user SQL isolation suite; CI includes Android, database replay/lint/RLS and secret-history scan. | **Partial**. Add regression tests for paging/delete/retry, storage rollback, auth/session, Realtime reconnect, call state transitions and admin authorization; update release evidence after those tests run. |

### User-dependent Phase 4 tests and decisions

- **U16–U18: Pending** — two-account media upload/download, unauthorized denial and cleanup verification; provide sanitized Logcat only if a failure requires it.
- **U19–U22: Owner decision/approval pending** — define admin scope, admin account/role bootstrap, audit-log retention, and final release approval. These must not be guessed or silently enabled.

## Cross-cutting security and release gates

1. **Firebase client API key:** `google-services.json` contains Android client configuration, not automatically a server secret. Owner must verify Google Cloud restrictions for package `com.sayangqoemeet.app` and the correct debug/release signing SHA fingerprints.
2. **Server secrets:** verify the presence and matching configuration of `FIREBASE_SERVICE_ACCOUNT_JSON`, `CLOUDFLARE_TURN_KEY_ID`, `CLOUDFLARE_TURN_API_TOKEN`, and `MEDIA_CLEANUP_CRON_SECRET` in the relevant services. Never print values or commit them.
3. **Cleanup:** do not manually dispatch production cleanup until the secret is verified and a controlled test confirms the retention/authorization rules.
4. **Unused indexes:** ten advisories remain; preserve indexes until workload-aware evidence supports a specific change.
5. **Leaked-password protection:** explicitly out of scope; do not modify it as part of these phases.
6. **Production approval:** no unresolved critical authorization issue, no failed exact-head CI, and no unverified destructive operation. Physical-device evidence and owner release approval are separate from CI.

## Current decision

**Not 100% production-ready yet.** Phase 1 has green automated build evidence; Phase 2 migration parity and core RLS checks are verified. Phase 3 and Phase 4 remain partially implemented because idempotent retries, real-device communication/media tests, cleanup-secret verification, admin authorization/audit contract, accessibility/regression coverage and owner approvals are not all complete. This matrix is intentionally conservative: only verified results are marked verified.
