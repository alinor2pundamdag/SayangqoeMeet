# Phase 2 Database Reconciliation and Security Audit

Date: 2026-10-23
Supabase project: `phjwzrrsaemgcedwpyyp`
Target branch: `main` (Phase 1 PR #11 has been merged)
Working branch: `fix/phase-2-main-rebased` (PR #16)

## A34–A42 — Migration ledger reconciliation

The live Supabase migration ledger was read before any DDL changes. It contains the following versions, in order:

- `20261016114000_initial_private_messaging`
- `20261016134617_add_foreign_key_indexes`
- `20261016140058_add_device_tokens`
- `20261016140546_add_conversation_invites`
- `20261017175440_atomic_direct_conversation_rpc`
- `20261018065021_user_presence_heartbeat`
- `20261018185347_add_profile_details`
- `20261018185357_webrtc_call_sessions`
- `20261018185402_protect_call_session_identity`
- `20261019065501_install_get_my_conversation_list_rpc`
- `20261020061237_finalize_chat_attachments_pinned_realtime`
- `20261020085405_backend_integrity_cleanup`
- `20261020121714_media_download_tracking_and_cleanup`
- `20261020121824_lock_down_media_trigger_rpc`
- `20261021135034_phase2_database_security_performance`
- `20261021135259_phase2_enforce_soft_delete_boundaries`
- `20261021155023_phase2_pinned_messages_authenticated_access`
- `20261021160316_phase2_harden_profile_rpc_search_path`
- `20261022025147_register_device_token_securely`
- `20261022025219_harden_chat_attachment_uuid_policies`
- `20261022025251_enforce_call_session_lifecycle`
- `20261022025320_harden_message_update_boundaries`
- `20261022025350_explicit_service_only_media_policies`
- `20261022032347_enforce_call_session_initial_state`
- `20261022041708_harden_message_insert_columns`

Seventeen migration versions present in the live Supabase ledger were missing from `main`; the branch restores the exact ledger-versioned migration chain so fresh local replay follows the same 25 version IDs. Eleven repository-only migration files whose version IDs are absent from the live ledger are preserved under `docs/legacy-migrations/` and excluded from `supabase db reset`; this is a replay-chain reconciliation, not loss of source content. Their effects must remain represented by the canonical ledger-versioned SQL and are subject to fresh replay validation.

The live ledger and production catalog were inspected read-only. **No production migration, DDL change, data rewrite, or `supabase db push` was executed as part of this PR work.** CI is responsible for testing the complete 25-file chain against a disposable local Supabase instance before merge.

## A43–A55 — Authorization and RLS

Verified and retained:
- RLS is enabled on all inspected public application tables.
- `message_media_cleanup_queue` and `message_media_download_confirmations` have no client policies and no anon/authenticated table privileges; access is service-role-only. This is intentional, not a reason to add broad policies.
- `get_my_profile()` is SECURITY DEFINER but takes no user argument and filters by `id = auth.uid()`; authenticated users need this RPC to read their own profile fields withheld from public directory column grants.
- `get_my_conversation_list()` is SECURITY DEFINER, rejects a null caller, and filters memberships by the caller's `auth.uid()`. Its authenticated EXECUTE grant is intentional.
- Call identity immutability is enforced by existing triggers. The trigger-only `guard_call_session_updates()` function is no longer executable by anon/authenticated roles; the trigger remains installed.
- Sensitive profile columns (`address`, `birthday`, `religion`, `phone_number`, and `bio`) have no authenticated SELECT column grants; only the directory fields are selectable directly.
- Client hard DELETE on `profiles` and `messages` is revoked. Message SELECT policy now requires conversation membership and `deleted_at IS NULL`.

Supabase Security Advisor reports three authenticated-callable SECURITY DEFINER functions (`get_my_profile`, `get_my_conversation_list`, and `register_device_token`) and two RLS-enabled service-only tables without client policies. The three RPCs intentionally support authenticated client workflows; their bodies derive identity from `auth.uid()`, and `anon` EXECUTE is denied. The media bookkeeping tables remain inaccessible to `anon` and `authenticated`. Leaked-password protection is explicitly outside the requested scope and was not changed.

## A56–A62 — Indexes, RLS performance, triggers, and plans

Fixed live advisor findings:
- Added index on `message_media_cleanup_queue(conversation_id)`.
- Added index on `message_media_download_confirmations(recipient_id)`.
- Added index on `pinned_messages(message_id)`.
- Rewrote seven presence/call RLS predicates to use scalar `(SELECT auth.uid())` while preserving their authorization conditions.

Post-change verification:
- All three indexes exist in `pg_indexes`.
- Supabase Performance Advisor no longer reports unindexed foreign keys or RLS initplan warnings.
- Representative `EXPLAIN (FORMAT JSON)` plans use `messages_conversation_created_idx`, `call_sessions_callee_status_created_idx`, and `message_media_cleanup_due_idx`.
- Ten indexes are flagged as unused by the advisor after adding the three indexes. They were retained because usage history is short and index removal is not justified by the advisory alone.
- PostgreSQL logs in the inspected 24-hour window contained no duration-log lines; `pg_stat_statements` was reviewed. The highest mean times were dominated by platform/catalog metadata queries, not a verified application hot query.

### Pinned-message schema recovery

Fresh replay exposed that `pinned_messages` existed in the hosted schema but had no creation statement in the repository migration chain. The historical `20261020061237_finalize_chat_attachments_pinned_realtime.sql` now creates the table before its realtime publication, indexes, and RLS policies are configured. This fresh-database reconstruction was not reapplied to production. The same reconstruction now restores the live profile-avatar bucket/policies and the four Realtime publication tables. Chat-attachment storage policies compare the path's conversation-folder segment to normalized UUID text, avoiding a throwing UUID cast for malformed object paths. Eleven repository-only migration files whose versions are absent from the live ledger are retained under `docs/legacy-migrations/` rather than executed as migrations; this prevents duplicate/alternate timestamp histories from being replayed while preserving their source content. The live table's missing client DML grants were fixed by forward migration `20261021155023_phase2_pinned_messages_authenticated_access`, granting only SELECT/INSERT/DELETE to `authenticated` while retaining RLS and denying `anon` and UPDATE.

## Applied migration summary

### `20261021135034_phase2_database_security_performance`
Adds the three missing FK indexes, optimizes seven RLS predicates without widening access, and revokes direct EXECUTE access to the trigger-only update guard.

### `20261021135259_phase2_enforce_soft_delete_boundaries`
Revokes client hard DELETE on profiles/messages and makes the membership-scoped message SELECT policy exclude soft-deleted rows.

### `20261021155023_phase2_pinned_messages_authenticated_access`
Grants only SELECT/INSERT/DELETE to authenticated users for RLS-scoped pinned messages; anon access and authenticated UPDATE remain denied.

### `20261021160316_phase2_harden_profile_rpc_search_path`
Sets `get_my_profile()` to `search_path = public, pg_temp`, matching the already-hardened conversation-list RPC while preserving its caller filter and authenticated EXECUTE grant.

### `20261022025147_register_device_token_securely`
Adds authenticated-only `register_device_token(text,text)`. It derives the owner from `auth.uid()` and atomically reassigns an installation's unique FCM token to the currently signed-in account. This fixes the RLS-blocked upsert that could leave a token attached to the previous account after offline sign-out. Anonymous execution is revoked.

## Completion status

- Migration ledger reconciled in the branch to the 25 live version IDs: **prepared; CI replay pending**.
- Live database security/performance catalog review: **read-only verification completed**; no production DDL was executed in this PR.
- Security/Performance Advisor re-run: **done**; three authenticated SECURITY DEFINER warnings are documented for review, and ten unused-index notices are retained without index deletion.
- Fresh-database migration replay: **pending final CI** after removal of unledgered duplicate migrations and restoration of the hosted avatar/Realtime schema into the exact-version reconstruction.
- `supabase db lint --local --fail-on error`: **pending final CI** for the normalized migration chain.
- Authenticated two-user message RLS isolation test: **pending final CI** for the normalized migration chain; it uses the actual `authenticated` role and distinct JWT subject identities for users A and B.
- FCM account-switch regression: **added**; the test reuses one installation token as two authenticated users and asserts the token is assigned only to the second user. It also verifies anonymous EXECUTE is denied.
- Fresh-schema regression assertions now verify the profile-avatar bucket is public, chat attachments remain private, messages/calls/presence/pinned messages are all in the Realtime publication, and all three chat-attachment policies guard UUID casts.
- Push delivery regression: FCM payloads now include `recipient_id`; Android drops payloads that do not explicitly target the active user. Message push payloads are data-only so Android can validate identity before showing a notification.
- TURN credential requests now include `call_id`; the Edge Function verifies the authenticated user is a participant in that active call before asking Cloudflare to mint temporary ICE credentials. Legacy clients without `call_id` are temporarily supported only when RLS exposes an active call for that user.
- Message-push authorization now rejects soft-deleted messages and callers who are no longer members of the conversation. Android app backup is disabled to avoid restoring authentication/session state onto another device.
- Live pinned-message grants verified: authenticated SELECT/INSERT/DELETE allowed, UPDATE denied, anon SELECT denied.
- Live SECURITY DEFINER RPC review confirmed caller-scoped bodies, no anon EXECUTE, and `search_path = public, pg_temp` for both authenticated RPCs.
- Supabase Free-tier leaked-password protection limitation: **documented, non-blocking**.
- This work is submitted as an open PR; no merge performed.
