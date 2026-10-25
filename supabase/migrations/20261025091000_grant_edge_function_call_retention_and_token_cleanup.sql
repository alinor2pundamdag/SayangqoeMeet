-- Least-privilege grants required by provider-confirmed FCM token cleanup
-- and the call-signaling retention worker.
BEGIN;

GRANT DELETE ON TABLE public.device_tokens TO service_role;

GRANT SELECT (answered_at, ended_at)
  ON TABLE public.call_sessions TO service_role;
GRANT DELETE ON TABLE public.call_sessions TO service_role;

COMMIT;
