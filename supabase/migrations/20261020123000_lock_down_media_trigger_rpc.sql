-- Trigger functions must not be callable through the exposed PostgREST RPC surface.
-- Trigger execution itself does not require callers to have EXECUTE on the trigger function.
revoke all on function public.enqueue_message_media_cleanup() from public, anon, authenticated;
grant execute on function public.enqueue_message_media_cleanup() to service_role;
