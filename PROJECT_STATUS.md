# SayangqoeMeet — Project Status

**Status date:** 2026-10-23  
**Repository:** `alinor2pundamdag/SayangqoeMeet`  
**Visibility:** Public  
**Default branch:** `main`

## Current verified status (2026-10-23)

- **Phase 1 — source/build:** PR #11 is merged. Android lint, unit tests, debug APK assembly, and artifact upload pass on the current app source. Auth/session restoration, callback routing, signed-in navigation, FCM service, call components, PiP receiver, and non-exported FileProvider are present and compile. Real-device sign-in/session and Android 13–16 compatibility checks remain owner/device tests.
- **Phase 2 — migration parity:** PRs #16, #34, #37, #39, #52, and #55 are merged. The repository and live Supabase ledger each contain **31 ordered migration versions**, with exact version parity (31/31; no missing or unrecorded versions). The original pre-apply grant migration filename is preserved in `docs/legacy-migrations/`; the two idempotent grant entries match the two versions recorded in the live ledger.
- **Fresh validation:** PR #55 exact head `8ef223de7bc1538042ac13403941c9c87948448b` passed Android CI, full migration replay from an empty database, SQL lint, two-user RLS isolation tests, Deno Edge Function type-check, and full-history secret scan. Post-merge main checks are tracked on the [main Actions page](https://github.com/alinor2pundamdag/SayangqoeMeet/actions?query=branch%3Amain).
- **Live database:** all **14/14** public tables have RLS enabled and at least one policy; **25/25** foreign-key constraints have valid leading indexes. `chat-attachments` remains private; `profile-avatars` remains public as intended. No existing indexes were dropped.
- **Edge Function privileges:** PR #52 added narrowly scoped `service_role` column grants for the fields used by conversation creation/invites, push notifications, incoming-call/TURN flows, media verification, admin overview, and stale-call cleanup. Existing client grants and RLS policies were preserved. Live catalog checks confirm private profile fields, message body, and call SDP remain ungranted to `service_role`; anonymous clients cannot update call end state.
- **Live maintenance verification:** after the additive grant migration was applied, the scheduled maintenance dry-run passed for both `cleanup-chat-media` and `cleanup-stale-calls`. The response reported `queue_rows: 0`, `failed: 0`, `stale_ringing_calls: 0`, and `marked_missed: 0`. This was dry-run-only; no media deletion or call-state mutation occurred.
- **RLS/RPC hardening:** all **9** public SECURITY DEFINER functions have pinned `search_path`; none is executable by `anon`. The three authenticated-callable profile/conversation/device-token RPCs derive identity from `auth.uid()` and remain intentionally callable for app functionality. The SQL suite covers cross-user isolation, sensitive profile grants, message server-owned fields, call lifecycle, storage policies, device-token account switching, pinned-message isolation, admin-table access, idempotency, and stale-call worker execution.
- **Edge Function deployment parity:** all **10** active deployed Edge Functions have source that matches the corresponding `main` `index.ts` byte-for-byte. Runtime push delivery still requires a two-device test; source parity alone does not prove Firebase/FCM provider configuration.
- **Performance:** representative plans use the call-session polling index, recipient/status invite index, admin audit created-at index, and media-cleanup queue index. The message table is currently small, so the planner's sequential scan for a dummy-ID query is reasonable despite the existing conversation/created index. The advisor reports **13 unused-index notices**. Preserve these until representative workload/query statistics prove a specific index redundant; do not drop an index solely because `idx_scan = 0`.
- **CI/security:** full-history secret scan passed on PR #55 exact head. This detects supported secret patterns but does not prove provider settings or credential history are risk-free. No service-account/private key value was exposed in this audit.
- **Owner-only release gates:** Firebase Android API-key restrictions; confirmation of `FIREBASE_SERVICE_ACCOUNT_JSON` and TURN secret configuration; assignment of the intended admin Auth UUID if admin access is needed; and physical-device tests for auth, invitations, messages, FCM delivery, voice/video/TURN, attachment access, PiP/overlay/notification permissions, and Android 13–16 behavior.
- **Explicit scope exclusion:** leaked-password protection was not changed.

## Current readiness decision

**Phase 1:** automated source/build gate passed. Device-level authentication, notification, and Android compatibility evidence remains outstanding.

**Phase 2:** live migration parity is 31/31; database replay/RLS tests passed on the exact PR #55 head; required server-side grants are applied; both cleanup dry-runs pass; all foreign-key indexes are present. The 13 unused-index notices are informational and must not trigger blind index deletion.

**Overall release:** not yet production-ready until the owner-only provider settings and real-device end-to-end tests above are evidenced.

## Historical snapshot (2026-10-16; superseded by the current status above)

- [x] Android Compose foundation and neon sign-in/registration UI are committed.
- [x] Sign-in success now routes to an app shell; sign-out calls Supabase Auth.
- [x] Profile screen loads and saves the signed-in user's profile through authenticated PostgREST requests.
- [x] Chat list loads real conversation memberships from Supabase.
- [x] Conversation screen loads real message history and sends messages through PostgREST under RLS.
- [x] Deployed Supabase Edge Function `create-conversation` is active with JWT verification enabled. It verifies the caller and creates a conversation and pending invitation; the recipient is added as a member only after accepting.
- [x] Added and committed the matching Edge Function source at `supabase/functions/create-conversation/index.ts`.
- [x] Added and deployed `respond-conversation-invite`, which permits only the addressed recipient to accept or decline a pending invitation.
- [x] Added a Hang Up / Cancel control to the call UI; it is only a UI control until real WebRTC call state exists.
- [x] Added Firebase Messaging client dependency/service, notification permission request, device-token registration code, and the live RLS-protected `device_tokens` table.
- [x] Deployed JWT-protected `send-message-push` Edge Function; it checks the caller's message ownership and targets other conversation members' tokens.
- [x] Active Supabase project `phjwzrrsaemgcedwpyyp` is healthy; six application tables exist with RLS enabled.
- [x] All three live migrations—initial messaging schema, foreign-key indexes, device tokens, and conversation invitations—are recorded in Supabase and matched by repository filenames.
- [x] Supabase security advisor returned no findings at the latest check. This is not a substitute for RLS integration tests.
- [x] README, handoff, and changelog are being aligned with current implementation status.
- [x] Added an explicit admin-console requirement to `masterplan.md`.
- [x] Expanded `admin-console/index.html` into a responsive sci-fi command-center UI with overview metrics, online-user directory, grouped chat sessions, video/voice review layouts, safety case queue, audit trail, settings, responsive navigation, and working UI-level search/navigation. All live values remain explicitly unconnected; no fabricated user activity.
- [x] Added a mobile admin-access UI with a three-dot overflow menu, password field, locked verification button, and module cards. It does not check or store an admin password locally.

## Historical incomplete / unverified list (superseded by the current status above)

- **Android build/CI:** No confirmed successful GitHub Actions run or local Android build result has been observed for these latest UI/data changes.
- **Auth lifecycle:** Session restoration, expired-token handling, and full sign-up/sign-in/sign-out device tests remain unverified.
- **Messaging:** REST history/send are implemented, but Realtime subscriptions, pagination UI, retry/deduplication, offline queueing, and comprehensive failure handling are not complete.
- **Invitations:** The deployed Edge Functions create a pending invitation and allow only the recipient to accept/decline; the recipient receives conversation membership only after acceptance. This still needs end-to-end two-account testing and abuse/rate-limit review before production use.
- **Firebase/FCM:** Client/token code and server sender function are present, but no `app/google-services.json` or Firebase service-account secret has been configured. Google Services plugin application is conditional on the app config file existing, so it does not block the clean-start build. The sender function cannot deliver push until `FIREBASE_SERVICE_ACCOUNT_JSON` is set in Supabase Edge Function secrets. No push delivery test has passed.
- **Calls:** The Hang Up / Cancel UI exists, but WebRTC signaling/media, STUN/TURN, call invitations, and two-device tests are not implemented.
- **Admin console UI:** The web command-center and Android access-entry UIs are implemented as static UI. No secure login or live operations are active yet; server-verified admin roles, presence/call feeds, moderation workflows, audit storage, and privileged endpoints remain to be built.
- **Security tests:** Automated RLS allowed/denied tests have not been run. Do not call the full app production-ready.
- **Responsive verification:** Build, emulator screenshots, compact/large layout, orientation, and keyboard test matrix are still pending.

## Historical next actions (superseded by the current status above)

1. Build the latest Android source in GitHub Actions or AndroidIDE and fix actual compiler errors from logs.
2. Test registration, sign-in, profile creation, conversation invitation, message send/history, and denied cross-user access with two test accounts.
3. Add Supabase Realtime and resilient message state handling.
4. Configure Firebase/FCM and verify push delivery on devices.
5. Implement WebRTC and test calls between two authorized devices.
6. Implement secure admin authentication, role-checked endpoints, live presence/call-session feeds, authorized moderation workflows, and server-side audit history behind the completed UI.

## Accuracy and security

Never claim a successful build, live Auth test, push delivery, or call unless evidence confirms it. Never commit passwords, signing credentials, service-role keys, Firebase service-account files, or other privileged secrets.
\n\n## 2026-10-19 — Dynamic TURN credentials integrated (CI verified)\n\n- Updated `WebRtcCallManager.kt` to POST to the active JWT-protected `get-turn-credentials` Supabase Edge Function before creating a WebRTC peer connection.\n- Sends the current Supabase access token as `Authorization: Bearer` and the public publishable key as `apikey`; no Cloudflare TURN API token or TURN password is embedded in Android.\n- Parses the returned `iceServers` list, including either string or array `urls`, and applies each supported STUN/TURN/TURNS URL with temporary username/credential fields to `PeerConnection.RTCConfiguration`.\n- If the request fails or returns malformed/empty ICE server data, the app logs the failure and falls back to Google STUN. TURN-dependent networks can still fail under this fallback.\n- GitHub Actions Android CI run 182 passed for commit `cac0720f755cd7a39fc78b5cdff66073eadc2ab8`: lintDebug, testDebugUnitTest, assembleDebug, and artifact upload all succeeded. Debug APK artifact `SayangqoeMeet-Android-APK`, 36,300,910 bytes, SHA-256 `62038d6bbbdba3d4a39c1f3aba037ec583a9ab3a7ef1866ba65ad66dc7a169a2`, expires 2026-11-02.\n- Supabase project inspection confirms `get-turn-credentials` is ACTIVE, version 1, with JWT verification enabled. Live credential issuance and two-device media connectivity are not yet runtime-tested.\n- Next: test caller and callee using two signed-in devices; verify the function returns temporary credentials without logging them, and confirm connection succeeds with relay candidates on restrictive networks.\n

## 2026-10-19 — Profile avatars, live presence, floating chat bubble, and insets (CI verified)

- Added a system photo picker to Profile; selected JPG/PNG/WebP images (maximum 8 MiB) upload using the signed-in user's access token to the profile-avatars Supabase Storage bucket. The UI shows a circular image or an initial-letter fallback. The profile must still be saved after upload to persist the returned public URL.
- Added a migration for the public-read avatar bucket and per-user folder insert/update/delete policies. Storage access is constrained to the authenticated user's UUID folder.
- Registered-user cards now load avatar URLs and subscribe to user_presence Realtime changes, with the existing polling refresh retained as fallback.
- Added an explicitly user-enabled, draggable Android overlay bubble. Android's special overlay permission is requested through Settings; tapping the bubble opens Chats. The user can disable the bubble from the overflow menu.
- Applied WindowInsets.safeDrawing at the app root so auth, signed-in screens, and call screens respect status/navigation bars without changing the launcher icon or manifest icon references.
- Source commit: 7fcba991a1a6757e5aedda9a4bf618b6e743869d.
- GitHub Actions run 193 succeeded: lintDebug, testDebugUnitTest, assembleDebug, and APK upload. Artifact: SayangqoeMeet-Android-APK, 37,075,571 bytes, SHA-256 7a6098cc204d63c12c54432fd77beba52626cbee39e67496a1f19ef62f221096, expires 2026-11-02.
- The new Storage/Realtime migration is committed but has not been applied to live Supabase. Device-level upload, realtime presence, overlay permission, and insets behavior remain unverified. Do not clear physical-device testing until the migration is applied and two-account testing passes.


## 2026-10-19 — Floating bubble launch fix (CI verified)

- Follow-up commit 22da77c4acad7067c01ee209930eb04e9a832ca4 changes the bubble launch flags to CLEAR_TOP so an existing MainActivity is recreated with the open_chats extra instead of remaining on its prior destination.
- Android CI run 195 passed lintDebug, testDebugUnitTest, assembleDebug, and artifact upload: https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/39258139369
- Artifact SayangqoeMeet-Android-APK: 37,075,511 bytes, SHA-256 a4725eedb51353a6cd04a80912309117079a1fe0247a058cdcbe03b8317bb039, expires 2026-11-02.
- Live Storage/Realtime migration and physical-device tests are still pending.


## 2026-10-20 — Media cleanup backend implemented

- Dedicated branch: feat/media-download-cleanup; PR #10 is open. Source commit currently includes the media schema, authenticated download-confirmation function, cleanup worker, and rollout/test plan.
- Live Supabase migration applied successfully. The ledger records media_download_tracking_and_cleanup as version 20261020121714 and lock_down_media_trigger_rpc as 20261020121824. Attachment metadata columns and attachment_purged_at are present.
- New confirmation and cleanup tables have RLS enabled and no client policies; service-role-only RPC grants were verified. The enqueue trigger function was also revoked from anon/authenticated after the security advisor flagged public execution.
- Edge Function confirm-message-media-download is ACTIVE v1 with JWT verification enabled. cleanup-chat-media is ACTIVE v1 with platform JWT verification disabled because it validates x-cleanup-secret; it will not run until MEDIA_CLEANUP_CRON_SECRET is configured and a trusted scheduler is installed.
- Migration history still has older filename/version drift. The atomic RPC migration filename is aligned to live version 20261017175440, but do not run blanket supabase db push until all remaining entries are reconciled.
- Not yet release-ready: Android must call confirmation only after a full successful download; cleanup secret/scheduler, controlled worker test, two-account media test, and two-device WebRTC/TURN matrix remain pending.
