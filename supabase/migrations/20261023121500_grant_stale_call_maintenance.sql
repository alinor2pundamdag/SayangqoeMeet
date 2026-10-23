BEGIN;

-- The stale-call maintenance Edge Function uses the service-role client to
-- inspect and transition ringing calls older than its cutoff. The table's
-- authenticated grants do not imply service_role grants in this project.
-- Add only the two privileges that worker requires; keep client grants/RLS
-- policies unchanged.
GRANT SELECT, UPDATE ON TABLE public.call_sessions TO service_role;

COMMIT;
