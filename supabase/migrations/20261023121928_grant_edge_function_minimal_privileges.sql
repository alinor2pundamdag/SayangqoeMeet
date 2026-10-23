BEGIN;

-- Idempotent ledger reconciliation: this version was also recorded by the
-- live Supabase migration ledger. GRANT statements are safe to repeat.
-- Keep the same least-privilege server-only column grants; no client grants
-- or RLS policies are changed.
GRANT SELECT (id, username, display_name)
  ON TABLE public.profiles TO service_role;
GRANT SELECT (id, conversation_id, inviter_id, recipient_id, status, created_at)
  ON TABLE public.conversation_invites TO service_role;
GRANT INSERT (conversation_id, inviter_id, recipient_id, status)
  ON TABLE public.conversation_invites TO service_role;
GRANT UPDATE (status, responded_at)
  ON TABLE public.conversation_invites TO service_role;
GRANT SELECT (id, created_at)
  ON TABLE public.conversations TO service_role;
GRANT INSERT (is_group, title, created_by)
  ON TABLE public.conversations TO service_role;
GRANT DELETE ON TABLE public.conversations TO service_role;
GRANT SELECT (conversation_id, user_id)
  ON TABLE public.conversation_members TO service_role;
GRANT INSERT (conversation_id, user_id)
  ON TABLE public.conversation_members TO service_role;
GRANT SELECT (id, conversation_id, sender_id, deleted_at, attachment_path, attachment_purged_at)
  ON TABLE public.messages TO service_role;
GRANT SELECT (token, user_id)
  ON TABLE public.device_tokens TO service_role;
GRANT SELECT (user_id, last_seen_at)
  ON TABLE public.user_presence TO service_role;
GRANT SELECT (id, caller_id, callee_id, call_type, status, created_at)
  ON TABLE public.call_sessions TO service_role;
GRANT UPDATE (status, ended_at)
  ON TABLE public.call_sessions TO service_role;

COMMIT;
