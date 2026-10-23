-- Enforce the call lifecycle at the database boundary.
-- Forward-only: do not rewrite the already-applied call-session migrations.
BEGIN;

ALTER TABLE public.call_sessions
  DROP CONSTRAINT IF EXISTS call_sessions_status_check;

ALTER TABLE public.call_sessions
  ADD CONSTRAINT call_sessions_status_check
  CHECK (status IN (
    'ringing', 'accepted', 'connecting', 'connected',
    'declined', 'cancelled', 'ended', 'missed', 'failed'
  ));

-- Preserve immutable identity checks while allowing trusted server-side
-- cleanup jobs to update lifecycle state without a user JWT.
CREATE OR REPLACE FUNCTION public.guard_call_session_updates()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $guard$ 
DECLARE
  v_uid uuid := auth.uid();
  v_role text := coalesce(auth.role(), '');
BEGIN
  IF NEW.id IS DISTINCT FROM OLD.id
     OR NEW.caller_id IS DISTINCT FROM OLD.caller_id
     OR NEW.callee_id IS DISTINCT FROM OLD.callee_id
     OR NEW.call_type IS DISTINCT FROM OLD.call_type
     OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
    RAISE EXCEPTION 'Call identity fields are immutable'
      USING ERRCODE = '23514';
  END IF;

  IF v_role <> 'service_role'
     AND (v_uid IS NULL OR v_uid NOT IN (OLD.caller_id, OLD.callee_id)) THEN
    RAISE EXCEPTION 'Only call participants may update signaling'
      USING ERRCODE = '42501';
  END IF;
  RETURN NEW;
END;
$guard$;

REVOKE ALL ON FUNCTION public.guard_call_session_updates() FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.enforce_call_session_lifecycle()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
  v_user_id uuid := auth.uid();
  v_role text := coalesce(auth.role(), '');
  v_is_caller boolean;
  v_is_callee boolean;
BEGIN
  -- Server-side service-role workflows may perform explicitly trusted cleanup.
  -- Ordinary requests must always resolve to one of the two immutable participants.
  IF v_role <> 'service_role' THEN
    IF v_user_id IS NULL OR (v_user_id <> OLD.caller_id AND v_user_id <> OLD.callee_id) THEN
      RAISE EXCEPTION 'Only call participants may update a call'
        USING ERRCODE = '42501';
    END IF;
  END IF;

  v_is_caller := v_user_id = OLD.caller_id;
  v_is_callee := v_user_id = OLD.callee_id;

  IF NEW.offer_sdp IS DISTINCT FROM OLD.offer_sdp THEN
    IF v_role <> 'service_role'
       AND (NOT v_is_caller OR OLD.status <> 'ringing' OR NEW.status <> 'ringing'
            OR NEW.offer_sdp IS NULL) THEN
      RAISE EXCEPTION 'Only the caller may publish an offer while ringing'
        USING ERRCODE = '42501';
    END IF;
  END IF;

  IF NEW.answer_sdp IS DISTINCT FROM OLD.answer_sdp THEN
    IF v_role <> 'service_role'
       AND (NOT v_is_callee OR OLD.status <> 'ringing' OR NEW.status <> 'accepted'
            OR NEW.answer_sdp IS NULL OR NEW.answered_at IS NULL) THEN
      RAISE EXCEPTION 'Only the callee may accept a ringing call with an answer'
        USING ERRCODE = '42501';
    END IF;
  END IF;

  IF NEW.answered_at IS DISTINCT FROM OLD.answered_at
     AND v_role <> 'service_role'
     AND (NOT v_is_callee OR OLD.status <> 'ringing' OR NEW.status <> 'accepted'
          OR NEW.answered_at IS NULL) THEN
    RAISE EXCEPTION 'Only the callee may set the call answer time'
      USING ERRCODE = '42501';
  END IF;

  IF NEW.ended_at IS DISTINCT FROM OLD.ended_at
     AND NEW.status NOT IN ('declined', 'cancelled', 'ended', 'missed', 'failed') THEN
    RAISE EXCEPTION 'ended_at may only be set for a terminal call'
      USING ERRCODE = '23514';
  END IF;

  IF OLD.status IN ('declined', 'cancelled', 'ended', 'missed', 'failed')
     AND NEW.ended_at IS DISTINCT FROM OLD.ended_at
     AND v_role <> 'service_role' THEN
    RAISE EXCEPTION 'A terminal call end time is immutable'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.status IS DISTINCT FROM OLD.status THEN
    IF OLD.status IN ('declined', 'cancelled', 'ended', 'missed', 'failed') THEN
      RAISE EXCEPTION 'A terminal call cannot be reopened'
        USING ERRCODE = '23514';
    END IF;

    IF v_role <> 'service_role' THEN
      CASE OLD.status
        WHEN 'ringing' THEN
          IF NEW.status = 'accepted'
             AND (NOT v_is_callee OR NEW.answer_sdp IS NULL OR NEW.answered_at IS NULL) THEN
            RAISE EXCEPTION 'Only the callee may accept with an SDP answer and answer timestamp'
              USING ERRCODE = '42501';
          ELSIF NEW.status = 'declined' AND NOT v_is_callee THEN
            RAISE EXCEPTION 'Only the callee may decline a ringing call' USING ERRCODE = '42501';
          ELSIF NEW.status = 'cancelled' AND NOT v_is_caller THEN
            RAISE EXCEPTION 'Only the caller may cancel a ringing call' USING ERRCODE = '42501';
          ELSIF NEW.status = 'missed' AND NOT v_is_callee THEN
            RAISE EXCEPTION 'Only the callee may mark a ringing call missed' USING ERRCODE = '42501';
          ELSIF NEW.status NOT IN ('accepted', 'declined', 'cancelled', 'missed', 'failed') THEN
            RAISE EXCEPTION 'Invalid call transition from ringing to %', NEW.status
              USING ERRCODE = '23514';
          END IF;
        WHEN 'accepted' THEN
          IF NEW.status = 'cancelled' AND NOT v_is_caller THEN
            RAISE EXCEPTION 'Only the caller may cancel before media connects' USING ERRCODE = '42501';
          ELSIF NEW.status NOT IN ('connecting', 'connected', 'cancelled', 'ended', 'failed') THEN
            RAISE EXCEPTION 'Invalid call transition from accepted to %', NEW.status
              USING ERRCODE = '23514';
          END IF;
        WHEN 'connecting' THEN
          IF NEW.status = 'cancelled' AND NOT v_is_caller THEN
            RAISE EXCEPTION 'Only the caller may cancel before media connects' USING ERRCODE = '42501';
          ELSIF NEW.status NOT IN ('connected', 'cancelled', 'ended', 'failed') THEN
            RAISE EXCEPTION 'Invalid call transition from connecting to %', NEW.status
              USING ERRCODE = '23514';
          END IF;
        WHEN 'connected' THEN
          IF NEW.status NOT IN ('ended', 'failed') THEN
            RAISE EXCEPTION 'Invalid call transition from connected to %', NEW.status
              USING ERRCODE = '23514';
          END IF;
        ELSE
          RAISE EXCEPTION 'Unsupported call transition from % to %', OLD.status, NEW.status
            USING ERRCODE = '23514';
      END CASE;
    END IF;

    IF NEW.status IN ('declined', 'cancelled', 'ended', 'missed', 'failed')
       AND NEW.ended_at IS NULL THEN
      NEW.ended_at := now();
    END IF;
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS call_sessions_lifecycle_guard ON public.call_sessions;
CREATE TRIGGER call_sessions_lifecycle_guard
BEFORE UPDATE ON public.call_sessions
FOR EACH ROW EXECUTE FUNCTION public.enforce_call_session_lifecycle();

REVOKE ALL ON FUNCTION public.enforce_call_session_lifecycle() FROM PUBLIC, anon, authenticated;

-- ICE signaling is allowed only while a call can still negotiate or carry media.
DROP POLICY IF EXISTS call_ice_participant_insert ON public.call_ice_candidates;
CREATE POLICY call_ice_participant_insert
  ON public.call_ice_candidates FOR INSERT TO authenticated
  WITH CHECK (
    sender_id = (SELECT auth.uid())
    AND EXISTS (
      SELECT 1
      FROM public.call_sessions s
      WHERE s.id = call_id
        AND s.status IN ('ringing', 'accepted', 'connecting', 'connected')
        AND ((SELECT auth.uid()) = s.caller_id OR (SELECT auth.uid()) = s.callee_id)
    )
  );

COMMIT;
