-- Server-enforced admin membership and append-only audit records.
-- No admin is provisioned automatically; owner must explicitly assign a Supabase Auth user.
BEGIN;

CREATE TABLE IF NOT EXISTS public.admin_memberships (
  user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  role text NOT NULL CHECK (role IN ('read_only', 'trust_safety', 'super_admin')),
  is_active boolean NOT NULL DEFAULT true,
  granted_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  granted_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.admin_audit_log (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  actor_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
  action text NOT NULL CHECK (char_length(action) BETWEEN 1 AND 120),
  target_type text,
  target_id text,
  details jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS admin_audit_log_created_at_idx
  ON public.admin_audit_log (created_at DESC);
CREATE INDEX IF NOT EXISTS admin_audit_log_actor_created_at_idx
  ON public.admin_audit_log (actor_id, created_at DESC);

ALTER TABLE public.admin_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_audit_log ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE public.admin_memberships FROM PUBLIC, anon, authenticated, service_role;
REVOKE ALL ON TABLE public.admin_audit_log FROM PUBLIC, anon, authenticated, service_role;
GRANT SELECT ON TABLE public.admin_memberships TO service_role;
GRANT SELECT, INSERT ON TABLE public.admin_audit_log TO service_role;
REVOKE ALL ON SEQUENCE public.admin_audit_log_id_seq FROM PUBLIC, anon, authenticated, service_role;
GRANT USAGE, SELECT ON SEQUENCE public.admin_audit_log_id_seq TO service_role;

DROP POLICY IF EXISTS admin_memberships_deny_clients ON public.admin_memberships;
CREATE POLICY admin_memberships_deny_clients
  ON public.admin_memberships FOR ALL TO anon, authenticated
  USING (false) WITH CHECK (false);

DROP POLICY IF EXISTS admin_audit_log_deny_clients ON public.admin_audit_log;
CREATE POLICY admin_audit_log_deny_clients
  ON public.admin_audit_log FOR ALL TO anon, authenticated
  USING (false) WITH CHECK (false);

COMMIT;
