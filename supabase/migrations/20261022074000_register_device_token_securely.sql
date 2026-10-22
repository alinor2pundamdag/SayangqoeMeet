-- Register the installation's current FCM token under the authenticated account.
-- A token is unique per installation; reassign it atomically after account changes
-- so a failed offline sign-out cannot leave push delivery attached to the prior user.
BEGIN;

CREATE OR REPLACE FUNCTION public.register_device_token(
    p_token text,
    p_platform text DEFAULT 'android'
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_token text := btrim(p_token);
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required' USING ERRCODE = '28000';
    END IF;
    IF v_token IS NULL OR v_token = '' OR length(v_token) > 4096 THEN
        RAISE EXCEPTION 'Invalid device token' USING ERRCODE = '22023';
    END IF;
    IF p_platform IS DISTINCT FROM 'android' THEN
        RAISE EXCEPTION 'Unsupported device platform' USING ERRCODE = '22023';
    END IF;

    INSERT INTO public.device_tokens (user_id, token, platform, updated_at)
    VALUES (v_user_id, v_token, p_platform, now())
    ON CONFLICT (token) DO UPDATE
      SET user_id = EXCLUDED.user_id,
          platform = EXCLUDED.platform,
          updated_at = now();
END;
$$;

REVOKE ALL ON FUNCTION public.register_device_token(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.register_device_token(text, text) TO authenticated;

-- Registration must go through the identity-derived RPC. Leaving direct INSERT
-- or UPDATE grants would let older clients bypass atomic reassignment semantics.
REVOKE INSERT, UPDATE ON TABLE public.device_tokens FROM PUBLIC, anon, authenticated;
GRANT SELECT, DELETE ON TABLE public.device_tokens TO authenticated;
DROP POLICY IF EXISTS "users register own device tokens" ON public.device_tokens;
DROP POLICY IF EXISTS "users update own device tokens" ON public.device_tokens;

COMMIT;
