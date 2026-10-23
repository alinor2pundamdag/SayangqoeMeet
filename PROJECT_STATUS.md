# SayangqoeMeet — Project Status

**Status date:** 2026-10-23  
**Repository:** `alinor2pundamdag/SayangqoeMeet`  
**Visibility:** Public  
**Default branch:** `main`

## Current audit status — 2026-10-23 (authoritative)

### Phase 1 — code and CI gate passed
- PR #11 is merged into `main`.
- The combined Phase 1/Phase 2 branch passed Android lint, unit tests, debug APK assembly, and artifact upload on commit `d3814a9620dfe4bd06b65144cc58b3f033b0bd20` ([run](https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/39923672216)).
- Auth/session restoration, email callback, FCM delivery, notification permission UX, and real two-device calling still require physical-device/backend smoke tests.

### Phase 2 — code and disposable database gate passed; merge pending
- PR #16 is open and mergeable; it must be merged only after final review ([PR #16](https://github.com/alinor2pundamdag/SayangqoeMeet/pull/16)).
- The live ledger has 25 versions; the branch has 25 canonical migration files, with no missing, extra, or duplicate version prefixes.
- Deno Edge Function type-check, fresh disposable Supabase migration replay, SQL lint, and two-user RLS isolation all passed on the same tested commit ([database run](https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/39923672196)).
- All 12 inspected public application tables have RLS enabled and policies. All 23 foreign-key constraints are covered by valid leading indexes. Representative query plans were reviewed; no index was dropped.
- Three authenticated-callable SECURITY DEFINER RPC warnings remain documented exceptions: get_my_profile(), get_my_conversation_list(), and register_device_token(text,text). Their search_path is pinned, anon execution is denied, and tests assert caller-scoped behavior. Ten unused-index advisories remain under review; short usage history alone does not justify removal.
- Eleven repository-only migration files are preserved under `docs/legacy-migrations/` outside the executable migration directory. This preserves source history while avoiding an alternate replay chain.

### Owner/device release checks still required
1. Restrict the Firebase Android API key in Google Cloud Console to package `com.sayangqoemeet.app`, correct debug/release signing fingerprints, and required APIs.
2. Verify `FIREBASE_SERVICE_ACCOUNT_JSON`, `SUPABASE_SERVICE_ROLE_KEY`, and `MEDIA_CLEANUP_CRON_SECRET` exist in the correct server-side settings; confirm the GitHub Actions and Supabase cleanup secret match without exposing values.
3. Scan all branches, tags, PR diffs, and Git history for leaked credentials/private data; rotate any confirmed exposed credential. Current-branch source search found no common high-confidence secret patterns, but it is not a full history scan.
4. Test registration/sign-in/session restore, email verification callback, push delivery, notification permissions, call accept/decline/hang-up, and voice/video between two authorized physical devices.
5. The separate web admin console remains a static UI shell; server-verified roles, privileged endpoints, audit storage, and live admin operations are outside the current Phase 1/2 completion gate.

No production database migration/DDL/data rewrite was run. No indexes were dropped. Leaked-password protection was not changed because it is explicitly outside this task's scope. Do not declare the full product production-ready until the owner/device checks above are evidenced.

---

## Historical verified items (initial implementation; current state is summarized above)

- [x] Android Compose foundation and neon sign-in/registration UI are committed.
- [x] Sign-in success now routes to an app shell; sign-out calls Supabase Auth.
- [x] Profile screen loads and saves the signed-in user's profile through authenticated PostgREST requests.
- [x] Chat list loads real conversation memberships from Supabase.
- [x] Conversation screen loads real message history and sends messages through PostgREST under RLS.
- [x] Deployed Supabase Edge Function `create-conversation` is active with JWT verification enabled. It verifies the caller and creates a conversation and pending invitation; the recipient is added as a member only after accepting.
- [x] Added and committed the matching Edge Function source at `supabase/functions/create-conversation/index.ts`.
- [x] Added and deployed `respond-conversation-invite`, which permits only the addressed recipient to accept or decline a pending invitation.
- [x] WebRTC signaling, peer connection setup, dynamic TURN credentials, and call lifecycle integration are implemented in source; two-device media connectivity remains a device-test gate.
- [x] Added Firebase Messaging client dependency/service, notification permission request, device-token registration code, and the live RLS-protected `device_tokens` table.
- [x] Deployed JWT-protected `send-message-push` Edge Function; it checks the caller's message ownership and targets other conversation members' tokens.
- [x] Active Supabase project `phjwzrrsaemgcedwpyyp` is healthy; the current audit found 12 public application tables with RLS enabled and policies.
- [x] The Phase 2 branch now has 25 canonical migration files matching the 25 live ledger versions exactly; PR #16 remains open and not merged.
- [x] Current security review documents three authenticated-callable SECURITY DEFINER RPC warnings; all three pin search_path, derive caller identity from auth.uid(), and deny anon execution. The warnings are reviewed, not suppressed.
- [x] README, handoff, and changelog are being aligned with current implementation status.
- [x] Added an explicit admin-console requirement to `masterplan.md`.
- [x] Expanded `admin-console/index.html` into a responsive sci-fi command-center UI with overview metrics, online-user directory, grouped chat sessions, video/voice review layouts, safety case queue, audit trail, settings, responsive navigation, and working UI-level search/navigation. All live values remain explicitly unconnected; no fabricated user activity.
- [x] Added a mobile admin-access UI with a three-dot overflow menu, password field, locked verification button, and module cards. It does not check or store an admin password locally.

## Historical incomplete list (as of 2026-10-16; superseded by current audit below)

- **Android build/CI:** The combined Phase 1/Phase 2 branch passed lint, unit tests, debug APK assembly, and artifact upload on commit d3814a9620dfe4bd06b65144cc58b3f033b0bd20; device behavior still needs physical testing.
- **Auth lifecycle:** Session restoration, expired-token handling, and full sign-up/sign-in/sign-out device tests remain unverified.
- **Messaging:** REST history/send are implemented, but Realtime subscriptions, pagination UI, retry/deduplication, offline queueing, and comprehensive failure handling are not complete.
- **Invitations:** The deployed Edge Functions create a pending invitation and allow only the recipient to accept/decline; the recipient receives conversation membership only after acceptance. This still needs end-to-end two-account testing and abuse/rate-limit review before production use.
- **Firebase/FCM:** `app/google-services.json` is tracked client configuration. Restrict its API key in Google Cloud Console; verify `FIREBASE_SERVICE_ACCOUNT_JSON` exists as a server-only secret; test end-to-end push delivery. Secret values are not readable through the repository connector.
- **Calls:** WebRTC signaling/media setup, call invitations, and dynamic TURN credential retrieval are in source; real two-device call quality/connectivity tests remain pending.
- **Admin console UI:** The web command-center and Android access-entry UIs are implemented as static UI. No secure login or live operations are active yet; server-verified admin roles, presence/call feeds, moderation workflows, audit storage, and privileged endpoints remain to be built.
- **Security tests:** Disposable two-user RLS isolation tests passed in CI; production-account and physical-device integration checks remain pending.
- **Responsive verification:** Build, emulator screenshots, compact/large layout, orientation, and keyboard test matrix are still pending.

## Historical next actions (superseded by current audit below)

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
