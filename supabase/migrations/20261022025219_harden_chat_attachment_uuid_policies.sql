-- Guard storage RLS UUID casts against malformed object paths.
-- CASE prevents invalid path segments from reaching a UUID cast even if the
-- query planner reorders boolean predicates.
BEGIN;

DROP POLICY IF EXISTS "Conversation members can read chat attachments" ON storage.objects;
CREATE POLICY "Conversation members can read chat attachments"
ON storage.objects FOR SELECT TO authenticated
USING (
  bucket_id = 'chat-attachments'
  AND EXISTS (
    SELECT 1 FROM public.conversation_members cm
    WHERE cm.conversation_id = CASE
      WHEN (storage.foldername(name))[1] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
      THEN ((storage.foldername(name))[1])::uuid
      ELSE NULL
    END
    AND cm.user_id = (SELECT auth.uid())
  )
);

DROP POLICY IF EXISTS "Conversation members can upload own chat attachments" ON storage.objects;
CREATE POLICY "Conversation members can upload own chat attachments"
ON storage.objects FOR INSERT TO authenticated
WITH CHECK (
  bucket_id = 'chat-attachments'
  AND (storage.foldername(name))[2] = (SELECT auth.uid())::text
  AND EXISTS (
    SELECT 1 FROM public.conversation_members cm
    WHERE cm.conversation_id = CASE
      WHEN (storage.foldername(name))[1] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
      THEN ((storage.foldername(name))[1])::uuid
      ELSE NULL
    END
    AND cm.user_id = (SELECT auth.uid())
  )
);

DROP POLICY IF EXISTS "Conversation members can delete own chat attachments" ON storage.objects;
CREATE POLICY "Conversation members can delete own chat attachments"
ON storage.objects FOR DELETE TO authenticated
USING (
  bucket_id = 'chat-attachments'
  AND (storage.foldername(name))[2] = (SELECT auth.uid())::text
  AND EXISTS (
    SELECT 1 FROM public.conversation_members cm
    WHERE cm.conversation_id = CASE
      WHEN (storage.foldername(name))[1] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
      THEN ((storage.foldername(name))[1])::uuid
      ELSE NULL
    END
    AND cm.user_id = (SELECT auth.uid())
  )
);

COMMIT;
