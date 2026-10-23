-- Keep RLS as the authorization boundary while granting the minimum table
-- privileges required for member-scoped pin/list/unpin operations.
REVOKE ALL ON TABLE public.pinned_messages FROM anon, authenticated;
GRANT SELECT, INSERT, DELETE ON TABLE public.pinned_messages TO authenticated;
