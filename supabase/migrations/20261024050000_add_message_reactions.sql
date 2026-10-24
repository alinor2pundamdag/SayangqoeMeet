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

DO $$
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
END $$;

COMMIT;
