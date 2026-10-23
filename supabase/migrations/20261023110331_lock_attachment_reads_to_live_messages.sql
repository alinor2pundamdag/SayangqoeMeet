BEGIN;

-- A conversation membership alone must not grant a read URL for an orphaned,
-- soft-deleted, or already-purged object. Tie every private object read to a
-- currently visible message row whose attachment path exactly matches storage.
DROP POLICY IF EXISTS "Conversation members can read chat attachments" ON storage.objects;
CREATE POLICY "Conversation members can read chat attachments"
  ON storage.objects
  FOR SELECT
  TO authenticated
  USING (
    bucket_id = 'chat-attachments'
    AND EXISTS (
      SELECT 1
      FROM public.messages AS m
      JOIN public.conversation_members AS cm
        ON cm.conversation_id = m.conversation_id
      WHERE m.attachment_path = objects.name
        AND m.sender_id::text = (storage.foldername(objects.name))[2]
        AND m.deleted_at IS NULL
        AND m.attachment_purged_at IS NULL
        AND cm.user_id = (SELECT auth.uid())
        AND m.conversation_id = CASE
          WHEN (storage.foldername(objects.name))[1] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
          THEN ((storage.foldername(objects.name))[1])::uuid
          ELSE NULL::uuid
        END
    )
  );

COMMIT;
