> **Current audit addendum — 2026-10-25 (PR #61 branch snapshot)**
>
> PR #61 (`feat/messenger-inspired-media-call-ux`) is OPEN and DRAFT; it is not merged into `main`. The source snapshot `8ea556b9f0174f952d5ae6e11a9b5dd48d86ed22` passed Android CI #659, Phase 2 Database Validation #303, and Secret History Scan #239. Two audit-document corrections followed; CI was restarted for those documentation commits and must be checked against the final branch head before calling the latest head green.
>
> **Do not treat older status/handoff statements below as current evidence when they conflict with this addendum.** Automated migration replay/SQL tests are not proof of hosted Supabase parity. Still requires owner/device/provider evidence: live migration/function parity, two-account RLS/Realtime/media checks, live FCM, Android notification/foreground-service behavior, two-device WebRTC, admin MFA/AAL2 and Firebase console restrictions. No production migration/deployment/data deletion has been performed by this audit. Do not enable call-retention deletion until dry-run and owner approval. Keep Supabase leaked-password protection excluded.

---

# AI Handoff — SayangqoeMeet

## Start here

1. Read `masterplan.md`, `PROJECT_STATUS.md`, and the latest `CHANGELOG_AI.md` entries.
2. Inspect the current GitHub source and latest GitHub Actions run before changing code.
3. Preserve the clean-start design and keep every status claim tied to actual evidence.

## Current verified state

- Private repository: `alinor2pundamdag/SayangqoeMeet`, default branch `main`.
- Android Compose foundation, neon auth UI, signed-in app shell, profile screen, chat list, conversation history/send screen, call-control screen, and locked admin entry exist in source.
- Supabase project `phjwzrrsaemgcedwpyyp` is active and healthy.
- Six application tables exist with RLS enabled: `profiles`, `conversations`, `conversation_members`, `messages`, `device_tokens`, and `conversation_invites`.
- Live migration history includes `20261016114000_initial_private_messaging`, `20261016134617_add_foreign_key_indexes`, `20261016140058_add_device_tokens`, and `20261016140546_add_conversation_invites`.
- Deployed Edge Functions `create-conversation`, `respond-conversation-invite`, and `send-message-push` are active with JWT verification enabled. The creator becomes a member and the recipient is added only after accepting a pending invitation. Sources: `supabase/functions/create-conversation/index.ts` and `supabase/functions/respond-conversation-invite/index.ts`.
- Android repository methods use the current Supabase access token for profile CRUD, conversation queries, message history, and message sending.
- Admin console is now an explicit master-plan requirement. A static locked web UI shell exists at `admin-console/index.html`; the Android admin item is also a locked placeholder. Neither is a working admin dashboard until server-verified roles, privileged endpoints, and audit history are implemented.
- FCM client/token registration code, the RLS-protected `device_tokens` table, and JWT-protected `send-message-push` Edge Function exist. Firebase app configuration and `FIREBASE_SERVICE_ACCOUNT_JSON` are still missing, so push delivery is not configured or verified.
- No successful Android build, end-to-end two-account test, FCM delivery, Realtime test, or WebRTC call test has been confirmed.

## Highest-priority next steps

1. Run a real Android debug build from GitHub Actions or AndroidIDE. Resolve compile errors using actual logs.
2. End-to-end test auth/profile and conversation invitation with two separate accounts; verify cross-user reads/writes are denied.
3. Test the invitation accept/decline path with two accounts, then add Realtime subscriptions and message pagination/retry handling.
4. Register the Firebase Android app for package `com.sayangqoemeet.app`, add the project-specific `app/google-services.json` (the Google Services plugin is conditionally applied when this file exists), configure the server-only `FIREBASE_SERVICE_ACCOUNT_JSON` secret in Supabase, then test push notifications on devices.
5. Implement WebRTC signaling/media and test two authorized devices.
6. Build the separate admin web console with backend role enforcement and an audit log.

## Rules

- Do not say build, auth, messaging, push delivery, or calling tests passed without evidence.
- Never place Supabase service-role/secret keys or Firebase service-account credentials in Android or Git.
- Admin privileges must be verified by a trusted backend; a hidden button or client-side flag is not authorization.
- Update status/changelog/master plan after verified milestones.
\n\n## Latest verified update — 2026-10-19\n\n- Current PR branch head: `cac0720f755cd7a39fc78b5cdff66073eadc2ab8`.\n- Dynamic TURN integration is in `app/src/main/java/com/sayangqoemeet/app/WebRtcCallManager.kt`. It calls `POST /functions/v1/get-turn-credentials` using the current Auth bearer token plus public apikey, parses the `iceServers` response, and passes the result into `PeerConnection.RTCConfiguration`. STUN-only fallback is retained for service errors.\n- Supabase project `phjwzrrsaemgcedwpyyp`: Edge Function `get-turn-credentials` is ACTIVE version 1 and `verify_jwt=true`; its implementation obtains Cloudflare TURN credentials server-side from `CLOUDFLARE_TURN_KEY_ID` and `CLOUDFLARE_TURN_API_TOKEN`. Do not expose or move these secrets into the Android app.\n- Android CI run 182 succeeded: lint, unit tests, debug APK assembly, and artifact upload. Artifact: `SayangqoeMeet-Android-APK`, 36,300,910 bytes, SHA-256 `62038d6bbbdba3d4a39c1f3aba037ec583a9ab3a7ef1866ba65ad66dc7a169a2`.\n- Still pending: physical two-account/two-device call test, confirm returned ICE credentials are accepted, confirm relay candidate selection, and test function failure/expired session behavior. Do not mark calling production-ready based on CI alone.\n

## Latest verified update — 2026-10-19

- Source commit: 7fcba991a1a6757e5aedda9a4bf618b6e743869d on feat/auth-directory-menu.
- Android CI run 193 passed lintDebug, testDebugUnitTest, assembleDebug, and artifact upload. Artifact SayangqoeMeet-Android-APK: 37,075,571 bytes, SHA-256 7a6098cc204d63c12c54432fd77beba52626cbee39e67496a1f19ef62f221096.
- Implemented profile photo selection/upload to Storage bucket profile-avatars, circular avatar rendering with letter fallback, Realtime subscription for user_presence with polling fallback, user-enabled draggable overlay bubble, and root safe-drawing insets.
- Migration supabase/migrations/20261019190000_profile_avatars_and_presence_realtime.sql is committed only; it has not been applied to the live project. Do not claim live avatar uploads or Realtime presence work until this migration is deployed and tested.
- Next: apply migration with Supabase CLI after reviewing linked project, install this verified APK on two accounts/devices, test photo upload and profile persistence, confirm green/gray presence transitions, test overlay permission enable/disable and bubble launch, and inspect layouts under system bars/keyboard. Keep launcher icon resources untouched.


## 2026-10-19 — Floating bubble launch fix (CI verified)

- Follow-up commit 22da77c4acad7067c01ee209930eb04e9a832ca4 changes the bubble launch flags to CLEAR_TOP so an existing MainActivity is recreated with the open_chats extra instead of remaining on its prior destination.
- Android CI run 195 passed lintDebug, testDebugUnitTest, assembleDebug, and artifact upload: https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/39258139369
- Artifact SayangqoeMeet-Android-APK: 37,075,511 bytes, SHA-256 a4725eedb51353a6cd04a80912309117079a1fe0247a058cdcbe03b8317bb039, expires 2026-11-02.
- Live Storage/Realtime migration and physical-device tests are still pending.


## 2026-10-20 — Media cleanup implementation handoff

- Work branch: feat/media-download-cleanup; PR #10: https://github.com/alinor2pundamdag/SayangqoeMeet/pull/10.
- Commit: 094497dc1a52aeb665134471c5cfba937432a644 (media backend implementation plus migration version alignment).
- Live migrations applied: 20261020121714_media_download_tracking_and_cleanup and 20261020121824_lock_down_media_trigger_rpc. Verify with Supabase migration listing before any further migration action.
- Live functions: confirm-message-media-download ACTIVE v1 verify_jwt=true; cleanup-chat-media ACTIVE v1 verify_jwt=false with x-cleanup-secret validation. Configure MEDIA_CLEANUP_CRON_SECRET out-of-band and add trusted scheduling before worker use.
- Important: no Android code currently invokes confirmation after media download. Until that is implemented, immediate purge cannot be driven by real app download events; TTL fallback is the only automatic eligibility path. Do not claim end-to-end media cleanup is tested.
- Migration drift remains beyond atomic RPC version naming. Do not run supabase db push blindly; compare each repository migration's effects to live schema and migration ledger. Existing live data was not deleted or rewritten by these changes.
- Next: wait for PR CI; inspect Supabase security advisor (the two new RLS-no-policy notices are intentional service-role-only tables; leaked-password protection remains disabled, and unrelated SECURITY DEFINER warnings remain); configure secret/scheduler; implement Android post-download confirmation; test auth denial, idempotency, group all-recipient gating, TTL fallback, worker retry and concurrent claims; then two-device WebRTC ICE/TURN/STUN/expired-credential tests.
