-- Idempotent client retries for message creation.
-- The nullable key preserves compatibility with already-installed older clients.
-- A non-partial unique index allows PostgREST ON CONFLICT inference while
-- PostgreSQL continues to allow multiple NULL keys for legacy clients.
BEGIN;

ALTER TABLE public.messages
  ADD COLUMN IF NOT EXISTS client_request_id uuid;

CREATE UNIQUE INDEX IF NOT EXISTS messages_sender_client_request_uidx
  ON public.messages (sender_id, client_request_id);

GRANT INSERT (
  conversation_id,
  sender_id,
  body,
  attachment_path,
  attachment_mime_type,
  attachment_name,
  client_request_id
) ON public.messages TO authenticated;

COMMIT;
