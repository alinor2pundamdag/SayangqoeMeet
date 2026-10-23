# SayangqoeMeet — Project Status

**Status date:** 2026-10-23  
**Repository:** `alinor2pundamdag/SayangqoeMeet`  
**Visibility:** Public  
**Default branch:** `main`

## Current verified status (2026-10-23)

- **Phase 1 — source/build:** PR #11 is merged. Android lint, unit tests, debug APK assembly, and artifact upload passed on exact source commits including `3ba1cabf92a37bc057567bf09b5cec6701beb5b6`. The auth gate, session restore, callback routing, signed-in shell, FCM service, call components, PiP receiver, and non-exported FileProvider are present in source and compile. Real-device auth/session and Android 13–16 compatibility tests remain pending.
- **Phase 2 — migration parity:** PRs #16, #34, and #37 are merged. Repository `supabase/migrations/` and the live Supabase migration ledger each contain **28** ordered versions, and the version lists match exactly. Fresh disposable Supabase replay, SQL lint, two-user RLS regression suite, and Deno Edge Function type-check passed on the exact PR #37 head `e10a91a3d6d69a185e888184e6793b5e68c27f17`; the post-merge main validation also passed on code commit `3ba1cabf92a37bc057567bf09b5cec6701beb5b6`.
- **Live database:** all **14/14** public tables have RLS enabled and at least one policy; **25/25** foreign-key constraints have valid leading indexes. The additive `admin_memberships_granted_by_idx` was applied and verified valid/ready; its migration is recorded as `20261023101814` and the repository filename now matches it. `chat-attachments` remains private; `profile-avatars` remains public as intended. No existing indexes were dropped.
- **RLS/RPC hardening:** all **9** public SECURITY DEFINER functions have pinned `search_path`; none is executable by `anon`. The three authenticated-callable profile/conversation/device-token RPCs derive identity from `auth.uid()` and remain intentionally callable for app functionality. The Phase 2 SQL suite tests cross-user isolation, sensitive profile column grants, message server-owned fields, call lifecycle transitions, storage policies, device-token account switching, pinned-message isolation, admin-table access and idempotency.
- **Performance:** read-only plans use the call-session and media-cleanup indexes; the admin membership lookup uses its primary key. The message table currently has only about 15 estimated rows, so the planner's sequential scan for a dummy-ID query is reasonable; the conversation/created index has recorded use. The advisor currently reports **13 unused-index notices**. Preserve these until representative workload/query statistics prove a specific index is redundant; do not drop an index merely because `idx_scan = 0`.
- **CI/security:** Android CI, fresh database validation and the full Git-history/current-file secret scan passed on the exact PR #37 head. The additional generic schema-hardening assertions in PR #39 are under validation. A clean Gitleaks run means no supported secret pattern was detected; it does not prove that provider settings are correct or that a credential was never exposed.
- **Backend/frontend runtime:** CI proves compile, migrations, Deno types and database authorization assertions, not external push delivery or device behavior. Firebase API-key restrictions, service-account/TURN/cleanup secret presence and matching, FCM delivery, two-device voice/video/TURN, email-confirmation redirects, and Android 13–16 notification/PiP/overlay/FileProvider behavior still require owner dashboard checks and real-device evidence. The cleanup dry-run implementation was merged in PR #36; verify its deployed Edge Function version before relying on dry-run behavior.
- **Admin console:** the server-authorized read-only Android admin overview and audit feed are merged. No admin is provisioned automatically; the owner must explicitly assign the intended Supabase Auth UUID. Moderation/destructive operations remain disabled pending explicit scope and retention approval.
- **Explicit scope exclusion:** leaked-password protection was not changed.

## Current readiness decision

**Phase 1:** automated build/source gate passed; physical-device auth and Android compatibility evidence remains.  
**Phase 2:** migration parity and core database authorization/index checks passed; generic whole-schema regression assertions in PR #39 are being added and must pass before merge. The 13 unused-index warnings require workload-based review, not blind deletion.

Do not call the entire release production-ready until the device and owner-only gates above are evidenced. Historical notes below are retained for traceability and are explicitly superseded by this section.

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
