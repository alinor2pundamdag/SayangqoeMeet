-- Cover the nullable granted_by foreign key for admin membership maintenance.
-- The index is retained even before admin provisioning so FK checks remain efficient.
BEGIN;

CREATE INDEX IF NOT EXISTS admin_memberships_granted_by_idx
  ON public.admin_memberships (granted_by);

COMMIT;
