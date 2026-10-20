-- Enforce identity-preserving delete boundaries for authenticated clients.
-- Active profiles are not user-deletable; account lifecycle remains owned by Supabase Auth.
-- Messages use deleted_at soft deletion. Client roles cannot hard-delete message rows.
BEGIN;
REVOKE DELETE ON TABLE public.profiles FROM anon, authenticated;
REVOKE DELETE ON TABLE public.messages FROM anon, authenticated;
DROP POLICY IF EXISTS "members can read messages" ON public.messages;
CREATE POLICY "members can read messages" ON public.messages FOR SELECT TO authenticated USING (
  deleted_at IS NULL AND EXISTS (
    SELECT 1 FROM public.conversation_members cm
    WHERE cm.conversation_id = messages.conversation_id AND cm.user_id = (SELECT auth.uid())
  )
);
COMMIT;
