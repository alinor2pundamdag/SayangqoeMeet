-- Make the service-only contract explicit for tables intentionally hidden
-- from ordinary Data API roles. No client grants are added.
BEGIN;

DROP POLICY IF EXISTS message_media_cleanup_queue_deny_clients ON public.message_media_cleanup_queue;
CREATE POLICY message_media_cleanup_queue_deny_clients
  ON public.message_media_cleanup_queue
  FOR ALL TO anon, authenticated
  USING (false)
  WITH CHECK (false);

DROP POLICY IF EXISTS message_media_download_confirmations_deny_clients ON public.message_media_download_confirmations;
CREATE POLICY message_media_download_confirmations_deny_clients
  ON public.message_media_download_confirmations
  FOR ALL TO anon, authenticated
  USING (false)
  WITH CHECK (false);

COMMIT;
