BEGIN;

-- The Edge Functions use a server-only service_role client for explicitly
-- validated cross-user workflows. Custom public tables do not automatically
-- grant service_role SQL privileges. Keep these grants column-scoped wherever
-- PostgreSQL supports it; do not grant client roles additional privileges.

-- Profile lookup/push display only; private profile fields remain ungranted.
GRANT SELECT (id, username, display_name)
  ON TABLE public.profiles TO service_role;

-- Create/respond to conversation invitations.
GRANT SELECT (id, conversation_id, inviter_id, recipient_id, status, created_at)
  ON TABLE public.conversation_invites TO service_role;
GRANT INSERT (conversation_id, inviter_id, recipient_id, status)
  ON TABLE public.conversation_invites TO service_role;
GRANT UPDATE (status, responded_at)
  ON TABLE public.conversation_invites TO service_role;

-- Create private conversations, add memberships, and roll back a newly-created
-- empty conversation if a later step fails.
GRANT SELECT (id, created_at)
  ON TABLE public.conversations TO service_role;
GRANT INSERT (is_group, title, created_by)
  ON TABLE public.conversations TO service_role;
GRANT DELETE ON TABLE public.conversations TO service_role;
GRANT SELECT (conversation_id, user_id)
  ON TABLE public.conversation_members TO service_role;
GRANT INSERT (conversation_id, user_id)
  ON TABLE public.conversation_members TO service_role;

-- Push/media verification and admin overview use metadata only, never message
-- body or unrelated private fields.
GRANT SELECT (id, conversation_id, sender_id, deleted_at, attachment_path, attachment_purged_at)
  ON TABLE public.messages TO service_role;
GRANT SELECT (token, user_id)
  ON TABLE public.device_tokens TO service_role;
GRANT SELECT (user_id, last_seen_at)
  ON TABLE public.user_presence TO service_role;

-- Incoming-call push, TURN authorization, admin overview, and stale-call
-- maintenance. The cleanup worker may only change terminal status/end time.
GRANT SELECT (id, caller_id, callee_id, call_type, status, created_at)
  ON TABLE public.call_sessions TO service_role;
GRANT UPDATE (status, ended_at)
  ON TABLE public.call_sessions TO service_role;

COMMIT;
