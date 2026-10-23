-- Harden message identity and soft-delete invariants at the database boundary.
-- Clients may edit message content/attachment metadata and soft-delete, but may
-- not move a message across conversations, impersonate a sender, rewrite creation
-- time, restore deleted rows, or set server-owned media-cleanup fields.
BEGIN;

CREATE OR REPLACE FUNCTION public.guard_message_update_integrity()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
BEGIN
  IF NEW.id IS DISTINCT FROM OLD.id
     OR NEW.conversation_id IS DISTINCT FROM OLD.conversation_id
     OR NEW.sender_id IS DISTINCT FROM OLD.sender_id
     OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
    RAISE EXCEPTION 'Message identity and creation time are immutable'
      USING ERRCODE = '42501';
  END IF;

  IF OLD.deleted_at IS NOT NULL AND NEW.deleted_at IS DISTINCT FROM OLD.deleted_at THEN
    RAISE EXCEPTION 'A soft-deleted message cannot be restored or re-deleted'
      USING ERRCODE = '42501';
  END IF;

  IF OLD.deleted_at IS NOT NULL
     AND (NEW.body IS DISTINCT FROM OLD.body
       OR NEW.edited_at IS DISTINCT FROM OLD.edited_at
       OR NEW.attachment_path IS DISTINCT FROM OLD.attachment_path
       OR NEW.attachment_mime_type IS DISTINCT FROM OLD.attachment_mime_type
       OR NEW.attachment_name IS DISTINCT FROM OLD.attachment_name) THEN
    RAISE EXCEPTION 'A soft-deleted message cannot be edited'
      USING ERRCODE = '42501';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS messages_update_integrity_guard ON public.messages;
CREATE TRIGGER messages_update_integrity_guard
BEFORE UPDATE ON public.messages
FOR EACH ROW EXECUTE FUNCTION public.guard_message_update_integrity();

REVOKE ALL ON FUNCTION public.guard_message_update_integrity() FROM PUBLIC, anon, authenticated;

-- Do not grant authenticated clients writes to server-maintained columns such as
-- attachment_purged_at. Column-level grants preserve the supported edit/delete
-- fields without allowing clients to forge cleanup state or change row identity.
REVOKE UPDATE ON TABLE public.messages FROM PUBLIC, anon, authenticated;
GRANT UPDATE (body, edited_at, deleted_at, attachment_path, attachment_mime_type, attachment_name)
  ON TABLE public.messages TO authenticated;

COMMIT;
