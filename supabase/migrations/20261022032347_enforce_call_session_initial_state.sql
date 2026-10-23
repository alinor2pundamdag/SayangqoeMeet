-- Prevent authenticated callers from creating calls in a forged lifecycle state.
-- Existing call creation inserts a ringing row; all later state changes must pass
-- through the participant-scoped UPDATE policy and lifecycle trigger.
BEGIN;

CREATE OR REPLACE FUNCTION public.guard_call_session_insert()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
  v_user_id uuid := auth.uid();
  v_role text := coalesce(auth.role(), '');
BEGIN
  -- Authenticated clients may only create their own initial ringing call.
  -- Direct trusted SQL (v_role='') and service-role server workflows remain
  -- operational; anonymous/unknown API roles cannot create call sessions.
  IF v_role = 'authenticated' THEN
    IF v_user_id IS NULL OR NEW.caller_id IS DISTINCT FROM v_user_id THEN
      RAISE EXCEPTION 'Only the authenticated caller may create a call'
        USING ERRCODE = '42501';
    END IF;
    IF NEW.status IS DISTINCT FROM 'ringing'
       OR NEW.answer_sdp IS NOT NULL
       OR NEW.answered_at IS NOT NULL
       OR NEW.ended_at IS NOT NULL THEN
      RAISE EXCEPTION 'A client-created call must start in ringing state without an answer or end time'
        USING ERRCODE = '42501';
    END IF;
  ELSIF v_role NOT IN ('service_role', '') THEN
    RAISE EXCEPTION 'This role may not create call sessions'
      USING ERRCODE = '42501';
  END IF;

  IF NEW.caller_id IS NOT DISTINCT FROM NEW.callee_id THEN
    RAISE EXCEPTION 'Caller and callee must be different users'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS call_sessions_initial_state_guard ON public.call_sessions;
CREATE TRIGGER call_sessions_initial_state_guard
BEFORE INSERT ON public.call_sessions
FOR EACH ROW EXECUTE FUNCTION public.guard_call_session_insert();

REVOKE ALL ON FUNCTION public.guard_call_session_insert() FROM PUBLIC, anon, authenticated;

DROP POLICY IF EXISTS call_sessions_caller_insert ON public.call_sessions;
CREATE POLICY call_sessions_caller_insert
  ON public.call_sessions FOR INSERT TO authenticated
  WITH CHECK (
    caller_id = (SELECT auth.uid())
    AND caller_id <> callee_id
    AND status = 'ringing'
    AND answer_sdp IS NULL
    AND answered_at IS NULL
    AND ended_at IS NULL
  );

COMMIT;
