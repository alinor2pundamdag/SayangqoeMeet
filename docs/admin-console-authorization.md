# Admin console authorization contract

## Initial safe scope

The first implementation is **read-only**. It exposes aggregate counts for profiles, conversations, messages, active calls and users active in the last 90 seconds, plus recent admin audit events. It does not expose profile address, birthday, religion, phone number, message bodies, attachment URLs, account suspension, bans, or destructive moderation actions.

Every request to `/functions/v1/admin-console` must:
1. Carry a valid Supabase user access token.
2. Resolve the user with Supabase Auth `getUser()`.
3. Find an active row in `public.admin_memberships` for that exact Auth UUID.
4. Record an audit event before returning protected data.
5. Reject all unsupported actions. The service-role key remains server-side.

The mobile UI never compares a locally entered admin password and cannot grant itself privileges. A 403 means the backend has not provisioned the signed-in account.

## Owner-controlled bootstrap

No admin membership is seeded by migration. After the migration is reviewed/applied, the owner must identify the correct Supabase Auth user UUID in **Authentication → Users**, then execute a reviewed statement in the Supabase SQL editor as the project owner:

```sql
INSERT INTO public.admin_memberships (user_id, role, is_active)
VALUES ('REPLACE_WITH_VERIFIED_AUTH_USER_UUID'::uuid, 'read_only', true)
ON CONFLICT (user_id) DO UPDATE
SET role = EXCLUDED.role,
    is_active = EXCLUDED.is_active;
```

Do not use an email guess or a user-provided UUID without confirming the intended account. Only the database owner should manage membership rows; client roles have no direct table privileges. To revoke access, set `is_active = false` in the SQL editor.

## Audit and retention

Audit rows include the actor UUID, action, timestamp, and minimal role metadata. The service role may read/append events but may not update/delete them. No automatic retention purge is implemented yet. The owner must choose a retention period and approve any purge workflow before one is added; no audit history should be deleted to make tests pass.

## Explicitly not enabled

Trust-and-safety moderation, account bans/suspensions, user-content inspection, profile editing, and any destructive admin action are intentionally unavailable until their policy scope, approval process, audit requirements and retention rules are approved. A UI control must not be enabled before the corresponding server-side authorization and negative tests exist.

## Verification requirements

- Fresh migration replay and SQL lint pass.
- SQL regression asserts RLS, explicit client-deny policies, no client grants, and service-role least privilege.
- Deno type-check passes for the Edge Function.
- Test a non-admin JWT receives 403 and a provisioned admin receives only the approved read-only actions.
- Verify audit writes happen before protected data is returned.
- Keep the function JWT-verified. Never ship `SUPABASE_SERVICE_ROLE_KEY` to Android or a browser.
