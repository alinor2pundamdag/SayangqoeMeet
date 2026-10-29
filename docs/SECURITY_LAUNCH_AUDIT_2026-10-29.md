# SayangqoeMeet Security & Launch Audit — 2026-10-29

## Scope and evidence boundary

This is a read-only hosted Supabase / GitHub source audit plus one CI workflow correction on branch `remove/admin-console-scope`. It is not a release certification. No production database schema, Auth Dashboard settings, Edge Function deployment, Firebase Console setting, credential, or user data was changed.

Repository: `alinor2pundamdag/SayangqoeMeet`
Supabase project: `phjwzrrsaemgcedwpyyp`
Audit branch: `remove/admin-console-scope`

## Confirmed findings

### Android authentication
- `SupabaseAuthRepository.signIn()` calls Supabase Auth email/password sign-in directly.
- `AuthGateScreen.kt` invokes `signIn` / `signUp` from the UI and reports the SDK error message.
- No five-failure / one-hour account lockout is implemented in the inspected auth repository or screen.
- Do not treat a client-only counter as an enforceable security boundary: a modified client or direct Auth API request can bypass it. A local counter could also let an attacker deny service to a victim if keyed only by email. The free-tier-safe enforcement design must be decided before implementing a UI-only approximation.
- Supabase Auth manages password hashing; the app does not implement its own password storage.

### Database and privileged RPCs
- The live public schema has 14 tables; all 14 report RLS enabled.
- Live Security Advisor reports three authenticated-callable `SECURITY DEFINER` functions:
  - `public.get_my_conversation_list()`
  - `public.get_my_profile()`
  - `public.register_device_token(text,text)`
- Inspected function bodies derive the caller from `auth.uid()`, check for a null caller, and pin `search_path` to `public, pg_temp`. This is positive evidence, but does not replace least-privilege EXECUTE-grant review and cross-user tests.
- Live Security Advisor also reports leaked-password protection disabled. This remains explicitly excluded because the project owner has stated the feature requires a paid plan.
- Performance Advisor reports 13 unused-index notices. These are informational; do not drop indexes without representative workload evidence.
- Live migration ledger returned 32 entries, including two entries with the same migration name but different versions. Verify the repository contains both exact migration files and that clean replay passes before changing the ledger.

### Edge Functions / deployment
- Live inventory returned 10 active Edge Functions, including `admin-console`, `notify-incoming-call`, `get-turn-credentials`, push, media cleanup, and stale-call cleanup.
- Removing the admin console from Android/source scope does not itself remove the hosted admin function or database objects. They must remain until dependencies, grants, schedules, and live callers are mapped; do not delete them blindly.
- Hosted function versions and hashes must be compared against the approved source commit before deployment parity is marked complete.
- The audit did not deploy or modify any Edge Function.

### Firebase
- `app/google-services.json` exists and the Android package is `com.sayangqoemeet.app`.
- The presence of the config file does not prove API-key restrictions, Firebase rules, FCM service-account configuration, quota controls, or successful push delivery. These require Firebase/Google Cloud Console evidence and end-to-end testing.
- Client-side Firebase API keys are identifiers, not substitutes for server-side secrets. Never place Firebase service-account private keys or Supabase service-role/secret keys in the Android app or repository.

### CI correction
- The Phase 2 workflow contained a step that read `admin-console/index.html` and syntax-checked its inline JavaScript.
- The admin-removal branch deletes that file, so the workflow step would fail on this branch. The obsolete step was removed in commit `033588d7d965e5dffa7e3c55bc9fe95c463fed79`.
- This workflow edit is not yet verified by a successful run. Check CI against the exact current PR head before merge.

### Git history / secret scanning
- A full-history Gitleaks workflow exists and checks all reachable refs with full history plus current files.
- A successful scan result for the current branch head has not been verified in this audit. Workflow presence is not proof of a clean scan.
- If a real credential is found in history, rotate/revoke it first; history rewriting alone does not invalidate an exposed credential.

## Required implementation / verification backlog

1. Verify exact PR #66 head SHA and run Android CI, Phase 2 database validation, and Secret History Scan on that SHA.
2. Inspect full live grants and policy predicates for every exposed table, view, storage object policy, and RPC; run two-user negative tests for reads, writes, message ownership, profiles, media, call participants, and device-token account switching.
3. Compare all repository migration filenames and SQL contents to all 32 live migration versions; clean-reset a disposable Supabase stack and run the two-user RLS suite. Do not use blind `db push`.
4. Inspect all ten deployed Edge Function sources/config and compare hashes/source against the exact approved repository commit; verify `verify_jwt`, service-role usage, input validation, bounded work, and secrets are server-only.
5. In Firebase/Google Cloud Console, verify package/app identity, API key application/API restrictions, quotas, FCM service account, Firebase products/rules actually in use, and test push delivery to two devices.
6. In Supabase Auth Dashboard, record actual rate limits for password sign-in, token endpoints, signup and email sends; confirm site URL / redirect allowlist, email confirmation and recovery flow.
7. Design and test the requested five-failed-sign-in / one-hour cooldown. Do not mark this complete with a client-only counter. Verify the selected server-side enforcement is available on the project's current plan and cannot be bypassed through direct Auth API requests; prevent attackers from locking out another user's account by submitting their email.
8. Add auth-abuse controls for signup/resend email, invite creation, message sends, call creation, media uploads/downloads, TURN credential requests, and cleanup endpoints. Use bounded quotas and avoid unbounded database writes on every failed login.
9. Verify HTTPS/TLS, Android network security config, backup policy, app permissions, privacy disclosures, retention behavior, and dependency/supply-chain scans.
10. Run physical-device tests: sign-in/signup/recovery/cooldown, two-account RLS isolation, FCM incoming messages/calls, notification permissions, foreground service, media permissions, WebRTC/TURN, logout/session expiry, and screen-size/keyboard accessibility.

## Status rules

- **Completed** only when direct evidence and the relevant tests pass on the exact commit/config.
- **Partial** when source/config exists but hosted configuration, deployment parity, or end-to-end tests are missing.
- **Not verified** when the relevant account console or device evidence is unavailable.
- Do not claim production-ready while any required launch gate above remains open.
