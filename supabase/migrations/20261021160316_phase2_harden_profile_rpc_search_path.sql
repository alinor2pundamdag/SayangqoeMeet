-- Keep the SECURITY DEFINER profile RPC caller-scoped and prevent temporary
-- schema objects from shadowing names if the function grows in the future.
ALTER FUNCTION public.get_my_profile() SET search_path = public, pg_temp;
