BEGIN;

-- The trusted cleanup worker only needs to inspect the stale-call predicate
-- and set the terminal status/end timestamp. Do not grant service_role broad
-- table-level read/write access to private call signaling fields.
GRANT SELECT (id, status, created_at)
  ON TABLE public.call_sessions TO service_role;
GRANT UPDATE (status, ended_at)
  ON TABLE public.call_sessions TO service_role;

COMMIT;
