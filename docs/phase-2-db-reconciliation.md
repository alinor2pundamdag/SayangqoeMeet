# Phase 2 Database Reconciliation and Security Audit

Date: 2026-10-21
Supabase project: `phjwzrrsaemgcedwpyyp`
Target branch: `main`
Working branch: `fix/phase-2-db-reconciliation`

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

Six already-applied historical migrations were missing from `main` under the versions recorded by Supabase. This branch restores them using the previously committed SQL sources, renaming them to match the live ledger versions. The recovered definitions were checked against live table columns, constraints, policies, indexes, and triggers. These historical migrations were **not re-applied**.

The two new Phase 2 migrations were applied directly to the live project after read-only inspection. Their filenames match the versions generated in the live migration ledger. No `supabase db push` was run.

## A43–A55 — Authorization and RLS

Verified and retained:
- RLS is enabled on all inspected public application tables.
- `message_media_cleanup_queue` and `message_media_download_confirmations` have no client policies and no anon/authenticated table privileges; access is service-role-only. This is intentional, not a reason to add broad policies.
- `get_my_profile()` is SECURITY DEFINER but takes no user argument and filters by `id = auth.uid()`; authenticated users need this RPC to read their own profile fields withheld from public directory column grants.
- `get_my_conversation_list()` is SECURITY DEFINER, rejects a null caller, and filters memberships by the caller's `auth.uid()`. Its authenticated EXECUTE grant is intentional.
- Call identity immutability is enforced by existing triggers. The trigger-only `guard_call_session_updates()` function is no longer executable by anon/authenticated roles; the trigger remains installed.
- Sensitive profile columns (`address`, `birthday`, `religion`, `phone_number`, and `bio`) have no authenticated SELECT column grants; only the directory fields are selectable directly.
- Client hard DELETE on `profiles` and `messages` is revoked. Message SELECT policy now requires conversation membership and `deleted_at IS NULL`.

Supabase Security Advisor still reports two intentional SECURITY DEFINER RPCs above, two RLS-enabled service-only tables with no client policies, and leaked-password protection disabled. The first two categories were manually checked against grants and function bodies. Enabling leaked-password protection requires dashboard/Auth configuration approval and remains outstanding.

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

## Applied migration summary

### `20261021135034_phase2_database_security_performance`
Adds the three missing FK indexes, optimizes seven RLS predicates without widening access, and revokes direct EXECUTE access to the trigger-only update guard.

### `20261021135259_phase2_enforce_soft_delete_boundaries`
Revokes client hard DELETE on profiles/messages and makes the membership-scoped message SELECT policy exclude soft-deleted rows.

## Completion status

- Migration ledger reconciled in the branch: **done**.
- Verified database security/performance fixes applied and catalog-verified: **done**.
- Security/Performance Advisor re-run: **done**; only the documented intentional/configuration findings remain.
- Fresh-database replay/CI validation of the reconstructed migration chain: **pending**.
- Supabase Auth leaked-password protection dashboard setting: **pending dashboard approval**.
- Production-like authenticated two-user RLS behavior tests: **pending**.
- This work is submitted as an open PR; no merge performed.
