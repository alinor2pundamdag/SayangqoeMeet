-- Enforce identity-preserving delete boundaries for authenticated clients.
-- Profiles are managed through account lifecycle; messages use deleted_at soft deletion.
-- This migration is already applied to the live Supabase project as version 20261021135259.

BEGIN;

REVOKE DELETE ON TABLE public.profiles FROM PUBLIC, anon, authenticated;
REVOKE DELETE ON TABLE public.messages FROM anon, authenticated;

DROP POLICY IF EXISTS "members can read messages" ON public.messages;
CREATE POLICY "members can read messages"
ON public.messages FOR SELECT TO authenticated
USING (
    deleted_at IS NULL
    AND EXISTS (
        SELECT 1
        FROM public.conversation_members cm
        WHERE cm.conversation_id = messages.conversation_id
          AND cm.user_id = (SELECT auth.uid())
    )
);

COMMIT;
