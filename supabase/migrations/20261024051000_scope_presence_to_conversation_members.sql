-- Presence is visible only to the signed-in user and users who share a
-- conversation. This supports an accurate chat-header last-active label without
-- exposing the entire registered-user population's activity history.
BEGIN;

CREATE OR REPLACE FUNCTION public.is_conversation_contact(p_target_user_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $is_conversation_contact$
    SELECT p_target_user_id = (SELECT auth.uid())
        OR EXISTS (
            SELECT 1
            FROM public.conversation_members AS viewer_member
            JOIN public.conversation_members AS target_member
              ON target_member.conversation_id = viewer_member.conversation_id
            WHERE viewer_member.user_id = (SELECT auth.uid())
              AND target_member.user_id = p_target_user_id
        );
$is_conversation_contact$;

REVOKE ALL ON FUNCTION public.is_conversation_contact(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.is_conversation_contact(uuid) TO authenticated;

-- The directory UI needs a binary online indicator for every registered user.
-- Expose only currently-online user IDs through a narrow authenticated RPC;
-- do not restore broad direct reads of raw last_seen_at timestamps.
CREATE OR REPLACE FUNCTION public.get_registered_online_user_ids()
RETURNS TABLE(user_id uuid)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $get_registered_online_user_ids$
    SELECT presence.user_id
    FROM public.user_presence AS presence
    JOIN public.profiles AS profile ON profile.id = presence.user_id
    WHERE auth.uid() IS NOT NULL
      AND presence.last_seen_at >= now() - interval '90 seconds';
$get_registered_online_user_ids$;

REVOKE ALL ON FUNCTION public.get_registered_online_user_ids() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_registered_online_user_ids() TO authenticated;

DROP POLICY IF EXISTS user_presence_read_authenticated ON public.user_presence;
DROP POLICY IF EXISTS user_presence_read_conversation_participants ON public.user_presence;
CREATE POLICY user_presence_read_conversation_participants
    ON public.user_presence
    FOR SELECT TO authenticated
    USING (public.is_conversation_contact(user_id));

DO $presence_publication$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename = 'user_presence'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.user_presence;
    END IF;
END $presence_publication$;

COMMIT;
