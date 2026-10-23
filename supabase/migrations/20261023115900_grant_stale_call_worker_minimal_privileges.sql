BEGIN;

-- The stale-call maintenance worker uses the service_role key, but Supabase
-- service_role does not automatically bypass PostgreSQL table/column grants.
-- Grant only the columns required to inspect stale ringing calls and mark
-- those calls missed; do not expose SDP or participant identity columns.
GRANT SELECT (id, status, created_at)
  ON TABLE public.call_sessions TO service_role;
GRANT UPDATE (status, ended_at)
  ON TABLE public.call_sessions TO service_role;

COMMIT;
