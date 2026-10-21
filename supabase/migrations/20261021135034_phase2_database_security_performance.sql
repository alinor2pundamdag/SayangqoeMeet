-- Phase 2: verified FK-index gaps, RLS initplan optimizations, and trigger RPC hardening.
-- Forward-only; no rows are deleted or rewritten. Unused indexes are intentionally retained.

BEGIN;

-- Verified by the live Supabase performance advisor: these FK columns lack a leading index.
CREATE INDEX IF NOT EXISTS message_media_cleanup_queue_conversation_id_idx
    ON public.message_media_cleanup_queue (conversation_id);
CREATE INDEX IF NOT EXISTS message_media_download_confirmations_recipient_id_idx
    ON public.message_media_download_confirmations (recipient_id);
CREATE INDEX IF NOT EXISTS pinned_messages_message_id_idx
    ON public.pinned_messages (message_id);

-- Keep the existing authorization predicates unchanged while evaluating auth.uid() once per statement.
DROP POLICY IF EXISTS user_presence_insert_self ON public.user_presence;
CREATE POLICY user_presence_insert_self
    ON public.user_presence FOR INSERT TO authenticated
    WITH CHECK ((SELECT auth.uid()) = user_id);

DROP POLICY IF EXISTS user_presence_update_self ON public.user_presence;
CREATE POLICY user_presence_update_self
    ON public.user_presence FOR UPDATE TO authenticated
    USING ((SELECT auth.uid()) = user_id)
    WITH CHECK ((SELECT auth.uid()) = user_id);

DROP POLICY IF EXISTS call_sessions_caller_insert ON public.call_sessions;
CREATE POLICY call_sessions_caller_insert
    ON public.call_sessions FOR INSERT TO authenticated
    WITH CHECK ((SELECT auth.uid()) = caller_id AND caller_id <> callee_id);

DROP POLICY IF EXISTS call_sessions_participant_select ON public.call_sessions;
CREATE POLICY call_sessions_participant_select
    ON public.call_sessions FOR SELECT TO authenticated
    USING ((SELECT auth.uid()) = caller_id OR (SELECT auth.uid()) = callee_id);

DROP POLICY IF EXISTS call_sessions_participant_update ON public.call_sessions;
CREATE POLICY call_sessions_participant_update
    ON public.call_sessions FOR UPDATE TO authenticated
    USING ((SELECT auth.uid()) = caller_id OR (SELECT auth.uid()) = callee_id)
    WITH CHECK ((SELECT auth.uid()) = caller_id OR (SELECT auth.uid()) = callee_id);

DROP POLICY IF EXISTS call_ice_participant_insert ON public.call_ice_candidates;
CREATE POLICY call_ice_participant_insert
    ON public.call_ice_candidates FOR INSERT TO authenticated
    WITH CHECK (
        sender_id = (SELECT auth.uid())
        AND EXISTS (
            SELECT 1
            FROM public.call_sessions s
            WHERE s.id = call_id
              AND ((SELECT auth.uid()) = s.caller_id OR (SELECT auth.uid()) = s.callee_id)
        )
    );

DROP POLICY IF EXISTS call_ice_participant_select ON public.call_ice_candidates;
CREATE POLICY call_ice_participant_select
    ON public.call_ice_candidates FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1
            FROM public.call_sessions s
            WHERE s.id = call_id
              AND ((SELECT auth.uid()) = s.caller_id OR (SELECT auth.uid()) = s.callee_id)
        )
    );

-- Trigger-only function: do not expose it as a PostgREST RPC to anon/authenticated.
-- Trigger execution does not require callers to have EXECUTE on the trigger function.
REVOKE ALL ON FUNCTION public.guard_call_session_updates() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.guard_call_session_updates() TO service_role;

COMMIT;
