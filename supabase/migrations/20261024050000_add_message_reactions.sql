-- Per-message emoji reactions are scoped to live messages in conversations
-- the signed-in user belongs to. Reactions never grant access to message content.
BEGIN;

CREATE TABLE IF NOT EXISTS public.message_reactions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id uuid NOT NULL REFERENCES public.messages(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    reaction text NOT NULL CHECK (reaction IN ('👍', '❤️', '😂', '😮', '😢', '🙏')),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT message_reactions_message_user_reaction_key UNIQUE (message_id, user_id, reaction)
);

CREATE INDEX IF NOT EXISTS message_reactions_message_created_idx
    ON public.message_reactions (message_id, created_at DESC);

ALTER TABLE public.message_reactions ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE public.message_reactions FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, DELETE ON TABLE public.message_reactions TO authenticated;

DROP POLICY IF EXISTS message_reactions_select_live_member ON public.message_reactions;
CREATE POLICY message_reactions_select_live_member
    ON public.message_reactions
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1
            FROM public.messages AS m
            JOIN public.conversation_members AS cm
              ON cm.conversation_id = m.conversation_id
            WHERE m.id = message_reactions.message_id
              AND m.deleted_at IS NULL
              AND cm.user_id = (SELECT auth.uid())
        )
    );

DROP POLICY IF EXISTS message_reactions_insert_own_live_member ON public.message_reactions;
CREATE POLICY message_reactions_insert_own_live_member
    ON public.message_reactions
    FOR INSERT TO authenticated
    WITH CHECK (
        user_id = (SELECT auth.uid())
        AND EXISTS (
            SELECT 1
            FROM public.messages AS m
            JOIN public.conversation_members AS cm
              ON cm.conversation_id = m.conversation_id
            WHERE m.id = message_reactions.message_id
              AND m.deleted_at IS NULL
              AND cm.user_id = (SELECT auth.uid())
        )
    );

DROP POLICY IF EXISTS message_reactions_delete_own_live_member ON public.message_reactions;
CREATE POLICY message_reactions_delete_own_live_member
    ON public.message_reactions
    FOR DELETE TO authenticated
    USING (
        user_id = (SELECT auth.uid())
        AND EXISTS (
            SELECT 1
            FROM public.messages AS m
            JOIN public.conversation_members AS cm
              ON cm.conversation_id = m.conversation_id
            WHERE m.id = message_reactions.message_id
              AND m.deleted_at IS NULL
              AND cm.user_id = (SELECT auth.uid())
        )
    );

CREATE OR REPLACE FUNCTION public.toggle_message_reaction(
    p_message_id uuid,
    p_reaction text
)
RETURNS boolean
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $
DECLARE
    v_uid uuid := auth.uid();
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Sign in to react to a message'
            USING ERRCODE = '42501';
    END IF;
    IF p_reaction NOT IN ('👍', '❤️', '😂', '😮', '😢', '🙏') THEN
        RAISE EXCEPTION 'Unsupported reaction'
            USING ERRCODE = '22023';
    END IF;

    -- Serialize toggles for this user/message/reaction to prevent duplicate
    -- rows and inconsistent double-tap behavior under concurrent requests.
    PERFORM pg_advisory_xact_lock(
        hashtextextended(p_message_id::text || ':' || v_uid::text || ':' || p_reaction, 0)
    );

    DELETE FROM public.message_reactions
    WHERE message_id = p_message_id
      AND user_id = v_uid
      AND reaction = p_reaction;
    IF FOUND THEN
        RETURN false;
    END IF;

    INSERT INTO public.message_reactions (message_id, user_id, reaction)
    VALUES (p_message_id, v_uid, p_reaction);
    RETURN true;
END;
$;

REVOKE ALL ON FUNCTION public.toggle_message_reaction(uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.toggle_message_reaction(uuid, text) TO authenticated;

DO $
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime'
    ) AND NOT EXISTS (
        SELECT 1
        FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename = 'message_reactions'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.message_reactions;
    END IF;
END $;

COMMIT;
