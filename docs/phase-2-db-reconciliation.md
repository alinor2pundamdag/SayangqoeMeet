# Phase 2 Database Reconciliation and Security Audit

Date: 2026-10-23
Supabase project: `phjwzrrsaemgcedwpyyp`
Target branch: `main` (Phase 1 PR #11 has been merged)
Merged implementation: PR #16; current `main` also includes the full-history secret-scan correction from PR #23

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

- **Migration ledger:** complete — the 25 repository migration files match the 25 live Supabase migration versions exactly; no missing or extra executable migration versions.
- **Fresh-database replay:** passed on main commit `60a734072bf7aa852f26d65d770a1ee385329260` in [Phase 2 validation run #39927349041](https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/39927349041). This replays the full chain into a disposable local database; it does not write to production.
- **Database lint and isolation:** `supabase db lint --local --fail-on error`, the two-user RLS regression suite, and Deno type-check of Edge Functions passed in the same run.
- **Live catalog:** all 12 inspected public application tables have RLS and policies; all 23 inspected foreign keys have child-side indexes. The two media bookkeeping tables remain service-role-only. `chat-attachments` remains private; `profile-avatars` remains public as intended.
- **RPCs and triggers:** three authenticated-callable SECURITY DEFINER functions were reviewed. Their search paths are pinned; `anon` cannot execute them; caller identity is derived from `auth.uid()`. The live call/message guard triggers are installed. The advisor warnings remain documented because authenticated execution is intentional for these three RPCs.
- **Index warnings:** ten unused-index notices remain. No indexes were dropped: zero scans over a short observation window do not establish that an index is unnecessary. All foreign-key indexes were verified present.
- **Edge Function parity:** all eight active deployed functions were compared byte-for-byte with the corresponding `main` source and matched: `create-conversation`, `send-message-push`, `respond-conversation-invite`, `open-private-conversation`, `notify-incoming-call`, `get-turn-credentials`, `confirm-message-media-download`, and `cleanup-chat-media`. Seven require JWT verification; the cleanup worker deliberately uses a custom secret header.
- **Secret scan:** PR #23 corrected the old workflow, which had scanned only the latest commit despite its full-history label. The replacement scans all reachable Git history and current files. It passed on merge commit `2dd4dccbe168d7b8bc2a2d05d41d72c0c18c1415`, covering 537 commits. A narrowly scoped `.gitleaksignore` entry suppresses the known Firebase Android client API key at `app/google-services.json:18`; it is not a service credential. Google Cloud API restrictions still need owner verification.
- **Production safety:** no production schema/data changes were performed during this reconciliation. No migration was blindly pushed and no indexes or policies were removed.
- **Remaining release gates:** real-device two-account tests for email verification/session restore, invitation and message delivery, attachment access, FCM delivery while foreground/background, voice/video call negotiation and teardown; verify Firebase API-key restrictions; verify the presence and equality of `FIREBASE_SERVICE_ACCOUNT_JSON`, `CLOUDFLARE_TURN_KEY_ID`, `CLOUDFLARE_TURN_API_TOKEN`, and `MEDIA_CLEANUP_CRON_SECRET` without printing values; investigate the previously failed scheduled cleanup run before manually dispatching it.
- **Explicitly out of scope:** leaked-password protection was not changed.

**Status:** Phase 2 repository/database migration parity and CI validation are complete. Production release certification remains pending the external secret/configuration checks and real-device end-to-end tests above.
