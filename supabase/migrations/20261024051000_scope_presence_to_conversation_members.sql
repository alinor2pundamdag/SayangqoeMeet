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
AS $
    SELECT p_target_user_id = (SELECT auth.uid())
        OR EXISTS (
            SELECT 1
            FROM public.conversation_members AS viewer_member
            JOIN public.conversation_members AS target_member
              ON target_member.conversation_id = viewer_member.conversation_id
            WHERE viewer_member.user_id = (SELECT auth.uid())
              AND target_member.user_id = p_target_user_id
        );
$;

REVOKE ALL ON FUNCTION public.is_conversation_contact(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.is_conversation_contact(uuid) TO authenticated;

DROP POLICY IF EXISTS user_presence_read_authenticated ON public.user_presence;
DROP POLICY IF EXISTS user_presence_read_conversation_participants ON public.user_presence;
CREATE POLICY user_presence_read_conversation_participants
    ON public.user_presence
    FOR SELECT TO authenticated
    USING (public.is_conversation_contact(user_id));

COMMIT;
