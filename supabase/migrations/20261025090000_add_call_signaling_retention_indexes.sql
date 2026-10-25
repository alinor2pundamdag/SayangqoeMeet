-- Query indexes for bounded call-signaling retention. No production rows are changed by this migration.
BEGIN;

CREATE INDEX IF NOT EXISTS call_sessions_terminal_retention_idx
  ON public.call_sessions (ended_at)
  WHERE status IN ('declined', 'cancelled', 'ended', 'missed', 'failed')
    AND ended_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS call_sessions_negotiating_retention_idx
  ON public.call_sessions (answered_at)
  WHERE status IN ('accepted', 'connecting')
    AND answered_at IS NOT NULL;

COMMENT ON INDEX public.call_sessions_terminal_retention_idx IS
  'Supports bounded retention of terminal calls and their cascading ICE candidate rows.';
COMMENT ON INDEX public.call_sessions_negotiating_retention_idx IS
  'Supports reconciliation of abandoned accepted/connecting calls without expiring connected calls.';

COMMIT;
