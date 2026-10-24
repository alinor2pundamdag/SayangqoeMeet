-- Permit plain-text chat attachments while preserving the private bucket,
-- existing MIME allow-list, size limit, and row-level storage policies.
BEGIN;

DO $$
DECLARE
  current_types text[];
BEGIN
  SELECT allowed_mime_types
    INTO current_types
    FROM storage.buckets
   WHERE id = 'chat-attachments'
     AND public IS FALSE
   FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Private chat-attachments bucket is missing';
  END IF;

  IF NOT ('text/plain' = ANY(COALESCE(current_types, ARRAY[]::text[]))) THEN
    UPDATE storage.buckets
       SET allowed_mime_types = array_append(
         COALESCE(current_types, ARRAY[]::text[]),
         'text/plain'
       )
     WHERE id = 'chat-attachments';
  END IF;
END
$$;

COMMIT;
