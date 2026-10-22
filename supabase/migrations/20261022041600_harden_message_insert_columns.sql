-- Prevent authenticated clients from forging server-owned message state at INSERT.
-- Keep only the columns used by the Android text/attachment send paths writable.
BEGIN;

CREATE OR REPLACE FUNCTION public.guard_message_insert_integrity()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_role text := coalesce(auth.role(), '');
BEGIN
  IF v_role = 'authenticated' THEN
    IF v_uid IS NULL OR NEW.sender_id IS DISTINCT FROM v_uid THEN
      RAISE EXCEPTION 'Messages may only be created by the authenticated sender'
        USING ERRCODE = '42501';
    END IF;
    IF NEW.deleted_at IS NOT NULL
       OR NEW.edited_at IS NOT NULL
       OR NEW.attachment_purged_at IS NOT NULL THEN
      RAISE EXCEPTION 'Clients cannot create messages with server-owned state'
        USING ERRCODE = '42501';
    END IF;
  ELSIF v_role NOT IN ('service_role', '') THEN
    RAISE EXCEPTION 'This role may not create messages'
      USING ERRCODE = '42501';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS messages_insert_integrity_guard ON public.messages;
CREATE TRIGGER messages_insert_integrity_guard
BEFORE INSERT ON public.messages
FOR EACH ROW EXECUTE FUNCTION public.guard_message_insert_integrity();

REVOKE ALL ON FUNCTION public.guard_message_insert_integrity() FROM PUBLIC, anon, authenticated;

REVOKE INSERT ON TABLE public.messages FROM PUBLIC, anon, authenticated;
GRANT INSERT (conversation_id, sender_id, body, attachment_path, attachment_mime_type, attachment_name)
  ON TABLE public.messages TO authenticated;

COMMIT;
