-- Bound retries for media cleanup and permanently quarantine repeatedly failing jobs.
BEGIN;

ALTER TABLE public.message_media_cleanup_queue
  ADD COLUMN IF NOT EXISTS attempts integer NOT NULL DEFAULT 0;

ALTER TABLE public.message_media_cleanup_queue
  DROP CONSTRAINT IF EXISTS message_media_cleanup_queue_status_check;
ALTER TABLE public.message_media_cleanup_queue
  ADD CONSTRAINT message_media_cleanup_queue_status_check
  CHECK (status IN ('pending', 'claimed', 'purged', 'failed'));

ALTER TABLE public.message_media_cleanup_queue
  DROP CONSTRAINT IF EXISTS message_media_cleanup_queue_attempts_check;
ALTER TABLE public.message_media_cleanup_queue
  ADD CONSTRAINT message_media_cleanup_queue_attempts_check CHECK (attempts >= 0);

CREATE INDEX IF NOT EXISTS message_media_cleanup_retry_idx
  ON public.message_media_cleanup_queue (status, attempts, expires_at)
  WHERE status IN ('pending', 'claimed');

CREATE OR REPLACE FUNCTION public.finish_chat_media_cleanup(
  p_message_id uuid,
  p_success boolean,
  p_error text DEFAULT NULL
) RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
  IF p_success IS TRUE THEN
    UPDATE public.message_media_cleanup_queue
    SET status = 'purged',
        purged_at = COALESCE(purged_at, now()),
        claimed_at = NULL,
        last_error = NULL
    WHERE message_id = p_message_id AND status = 'claimed';

    IF NOT FOUND THEN
      RETURN FALSE;
    END IF;

    UPDATE public.messages
    SET attachment_purged_at = COALESCE(attachment_purged_at, now())
    WHERE id = p_message_id;
    RETURN TRUE;
  END IF;

  UPDATE public.message_media_cleanup_queue
  SET attempts = attempts + 1,
      status = CASE WHEN attempts + 1 >= 5 THEN 'failed' ELSE 'pending' END,
      claimed_at = NULL,
      last_error = LEFT(COALESCE(p_error, 'Storage deletion failed'), 500)
  WHERE message_id = p_message_id AND status = 'claimed';
  RETURN FOUND;
END;
$$;

REVOKE ALL ON FUNCTION public.finish_chat_media_cleanup(uuid, boolean, text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.finish_chat_media_cleanup(uuid, boolean, text) TO service_role;

COMMIT;
