-- Reconcile integrity and authorization issues found in the live schema.
-- Deliberately does not modify public.profiles or any of its columns.

BEGIN;

-- Pinned records must not outlive their conversation or message.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.pinned_messages'::regclass AND conname = 'pinned_messages_conversation_id_fkey') THEN
    ALTER TABLE public.pinned_messages
      ADD CONSTRAINT pinned_messages_conversation_id_fkey
      FOREIGN KEY (conversation_id) REFERENCES public.conversations(id) ON DELETE CASCADE NOT VALID;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.pinned_messages'::regclass AND conname = 'pinned_messages_message_id_fkey') THEN
    ALTER TABLE public.pinned_messages
      ADD CONSTRAINT pinned_messages_message_id_fkey
      FOREIGN KEY (message_id) REFERENCES public.messages(id) ON DELETE CASCADE NOT VALID;
  END IF;
END $$;

ALTER TABLE public.pinned_messages VALIDATE CONSTRAINT pinned_messages_conversation_id_fkey;
ALTER TABLE public.pinned_messages VALIDATE CONSTRAINT pinned_messages_message_id_fkey;

-- Keep one policy per user_presence operation.
DROP POLICY IF EXISTS "Authenticated users can view presence" ON public.user_presence;
DROP POLICY IF EXISTS "Users can insert their own presence" ON public.user_presence;
DROP POLICY IF EXISTS "Users can update their own presence" ON public.user_presence;
-- Canonical policies user_presence_read_authenticated, user_presence_insert_self,
-- and user_presence_update_self remain in place.

-- Soft deletion is the supported messages deletion model (deleted_at exists).
-- Remove the conflicting hard-delete policy; clients must set deleted_at instead.
DROP POLICY IF EXISTS "senders can delete own messages" ON public.messages;

-- Participants may exchange SDP/status, but may not rewrite call identity or creation time.
CREATE OR REPLACE FUNCTION public.guard_call_session_updates()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_uid uuid := auth.uid();
BEGIN
  IF NEW.id IS DISTINCT FROM OLD.id
     OR NEW.caller_id IS DISTINCT FROM OLD.caller_id
     OR NEW.callee_id IS DISTINCT FROM OLD.callee_id
     OR NEW.call_type IS DISTINCT FROM OLD.call_type
     OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
    RAISE EXCEPTION 'Call identity fields are immutable';
  END IF;

  IF v_uid IS NULL OR v_uid NOT IN (OLD.caller_id, OLD.callee_id) THEN
    RAISE EXCEPTION 'Only call participants may update signaling';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS guard_call_session_updates ON public.call_sessions;
CREATE TRIGGER guard_call_session_updates
BEFORE UPDATE ON public.call_sessions
FOR EACH ROW EXECUTE FUNCTION public.guard_call_session_updates();

COMMIT;
