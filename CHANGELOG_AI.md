# AI Development Changelog

This file records meaningful project milestones. Entries must reflect verified actions, not assumptions. Keep older entries intact and append new entries.

## 2026-10-16 — GitHub repository setup

### Completed
- Created the private `SayangqoeMeet` repository and verified that the connected GitHub tool can access it.
- Confirmed repository metadata reports push permission for the connected GitHub identity.
- Added `PROJECT_STATUS.md`, `AI_HANDOFF.md`, and this changelog.

### Not yet completed / still unverified at that point
- Importing the original Android source (not required for the new clean implementation).
- Retrieving the original `masterplan.md` from the old project.

## 2026-10-16 — New implementation master plan committed

### Completed
- Created `masterplan.md` as the baseline specification for a clean SayangqoeMeet implementation.
- Specified responsive/adaptive UI requirements for screen sizes, densities, insets, orientation, font scaling, and keyboard behavior.
- Specified a premium sci-fi cyan/electric-blue glow system for branded headers and login/registration fields.
- Specified custom per-letter spectral neon brand treatment for in-app wordmarks, with an explicit note that the OS-controlled app label cannot reliably be forced into multicolored text.
- Specified device-default notification sound and vibration where allowed by Android/user settings.
- Specified device-default incoming-call ringtone behavior, separate from ordinary message notifications, subject to Android and user settings.
- Re-fetched the new master plan and verified it contains the responsive UI and ringtone requirements.

### Not yet completed / still unverified
- Android source implementation and build.
- Supabase Auth/database/RLS integration.
- Real messaging and Realtime integration.
- FCM delivery and notification behavior.
- Real WebRTC calling and two-device tests.
- Responsive UI and branding runtime verification.

### Next step
Implement the real Android foundation from the plan and validate it in an Android-capable build environment.

## 2026-10-16 — Initial Android foundation added

### Completed and verified in repository
- Added root Gradle Kotlin DSL settings, Android/Kotlin/Compose plugin configuration, Gradle properties, and ignore rules.
- Added the `app` Android application module, manifest, and launcher activity.
- Added the first Jetpack Compose neon sign-in/registration UI with a per-letter spectral wordmark, scrollable form, safe system-inset/keyboard handling, password visibility toggle, and local input validation.
- Added an Android GitHub Actions workflow configured to attempt a debug build.
- Re-fetched source/configuration files and project records from GitHub to confirm saved contents.

### Not yet verified
- Android build/CI success; no build result has been observed yet.
- Runtime behavior on emulator/device and responsive layout checks.
- Supabase authentication/database/RLS, real messaging, FCM delivery, and WebRTC calling.

### Next step
Inspect the GitHub Actions run, resolve any build errors based on logs, and verify a successful debug build before integrating Supabase.


## 2026-10-16 — Supabase Auth and initial database foundation

### Committed
- Added Supabase Kotlin Auth and Android HTTP dependencies.
- Added a repository wrapper for email registration/sign-in and wired the existing auth screen to it.
- Added a database migration for profiles, conversations, conversation memberships, messages, indexes, grants, and RLS policies.
- Added build-time configuration for the Supabase URL and publishable key; no secret key is committed.
- Updated README and handoff notes to document required configuration and limits.

### Not verified / blocked
- The connected Supabase account lists no accessible projects; access to the historical project reference was denied. No remote migration was applied and no key was retrieved.
- GitHub Actions/build success and runtime authentication are not yet confirmed.
- Real messaging, trusted invitation workflow, realtime, FCM, and WebRTC remain unfinished.


## 2026-10-16 — RLS policy review

- Reviewed the committed migration and corrected the conversation membership read policy to avoid recursive RLS evaluation.
- Restricted membership inserts to the creator of the conversation and required message updates to remain within a conversation where the sender is a member.
- Re-fetched the migration after the fix. These are source-level checks only; hosted database advisors and execution tests remain blocked by Supabase project access.
- GitHub Actions status/run lookup returned no check results, so no build outcome is claimed.

## 2026-10-16 — Hosted Supabase verification and documentation correction

### Completed and verified
- Verified the active Supabase project and the four live public tables with RLS enabled.
- Applied the `add_foreign_key_indexes` migration to add indexes for `conversations.created_by` and `messages.sender_id`; committed the matching migration file as `20261016180000_add_foreign_key_indexes.sql`.
- Rechecked the live security advisor; it returned no findings at this check.
- Confirmed the automatic-RLS helper's execute privilege is not granted to `PUBLIC`, `anon`, or `authenticated`.
- Updated README, PROJECT_STATUS, and AI_HANDOFF to reflect the current hosted project and accurately document the remaining build, Auth, messaging, Firebase/FCM, and WebRTC work.

### Still not verified / incomplete
- No confirmed green Android CI run/build or on-device tests.
- No end-to-end Auth test or automated RLS allowed/denied test.
- Firebase app configuration and FCM delivery are absent; live push notifications are not configured.
- Profile/conversation/messaging UI, Realtime, and WebRTC remain incomplete.


## 2026-10-16 — Migration version alignment correction

- The hosted Supabase migration history assigned version `20261016134617` to `add_foreign_key_indexes`. The repository migration filename was corrected to `20261016134617_add_foreign_key_indexes.sql` and the mismatched filename was removed. This keeps the committed migration version aligned with the version verified in the live project.


## 2026-10-16 — Signed-in screens and secure conversation creation

### Completed in source / deployed
- Added the signed-in app shell and Compose screens for profile, conversation list, message history/send, call controls, and a locked admin-console entry.
- Connected profile operations and message history/send to authenticated Supabase REST requests using the active session access token.
- Added `supabase/functions/create-conversation/index.ts` and deployed the `create-conversation` Edge Function to the active project with JWT verification enabled. It resolves the recipient by username and creates both membership rows using server-side credentials.
- Added an explicit Hang Up / Cancel control. This is a UI control only; WebRTC is not implemented.
- Added the separate secure admin console to the master plan because it was not explicitly included in the previous baseline.

### Still unverified / incomplete
- Android build/CI and device runtime tests for these changes.
- Two-account end-to-end tests and automated RLS allowed/denied tests.
- Supabase Realtime, robust pagination/retry/offline behavior, Firebase FCM, WebRTC, and the separate web admin console.


## 2026-10-16 — FCM client and trusted push sender foundation

### Completed in source / deployed
- Added Firebase Messaging dependency, a Firebase messaging service, default-sound/vibration notification channel behavior, Android notification permission request, and token registration after sign-in when Firebase is initialized.
- Applied the `add_device_tokens` migration and committed the matching `20261016140058_add_device_tokens.sql` migration. The live table has RLS and user-owned token policies.
- Added and deployed JWT-protected `send-message-push` Edge Function. It verifies the caller owns the message and sends only to other members' registered device tokens.
- Message persistence is not failed when push delivery is unavailable; push is best-effort.

### External setup / verification still required
- Firebase Android app config (`app/google-services.json`) and Google Services Gradle plugin are not present.
- The server-only `FIREBASE_SERVICE_ACCOUNT_JSON` Supabase Edge Function secret is not configured.
- No FCM delivery or Android build has been verified. Do not claim notifications are working until both external configuration and device tests pass.


## 2026-10-16 — Admin console UI shell

- Added `admin-console/index.html`, a responsive dark/neon operations-console shell for user management, safety reports, service health, and audit history.
- The shell intentionally displays no fabricated metrics and keeps privileged actions disabled. Authentication, server-side role enforcement, live data, audit logging, and operational endpoints are still required.


## 2026-10-16 — Recipient-controlled private chat invitations

- Applied and committed `20261016140546_add_conversation_invites.sql`, creating an RLS-protected invitation table visible only to the inviter and recipient.
- Updated and redeployed `create-conversation` so it creates the conversation, adds only the creator as a member, and records a pending invite. The recipient does not gain access to the conversation before accepting.
- Added and deployed JWT-protected `respond-conversation-invite`; only the addressed recipient can accept or decline a pending invite. Acceptance adds their membership through a trusted server-side operation.
- Updated the Android chat screen to show pending invitations and accept/decline controls. Two-account end-to-end tests and abuse/rate-limit review remain required.
\n\n## 2026-10-19 — Dynamic Cloudflare TURN credentials for WebRTC\n\n- Modified `app/src/main/java/com/sayangqoemeet/app/WebRtcCallManager.kt` to fetch temporary ICE servers from the authenticated `get-turn-credentials` Edge Function before peer connection creation.\n- Added parsing for `iceServers[].urls` as a string or array, plus optional `username` and `credential`, and injected parsed servers into `PeerConnection.RTCConfiguration`.\n- Added failure handling that logs the retrieval issue and uses Google STUN as a best-effort fallback. No static TURN secret was added to the Android source.\n- Verified Supabase Edge Function `get-turn-credentials` is ACTIVE version 1 with JWT verification enabled.\n- Verified Android CI run 182 passed lintDebug, testDebugUnitTest, assembleDebug, and artifact upload for commit `cac0720f755cd7a39fc78b5cdff66073eadc2ab8`. Artifact SHA-256: `62038d6bbbdba3d4a39c1f3aba037ec583a9ab3a7ef1866ba65ad66dc7a169a2`.\n- Physical two-device calling and relay-path validation remain outstanding.\n

## 2026-10-19 — Profile avatars, live presence, floating chat bubble, and system insets

- Added Coil Compose image rendering and a system image picker for profile photos.
- Added authenticated Storage upload code with JPEG/PNG/WebP MIME validation and an 8 MiB limit; introduced migration 20261019190000_profile_avatars_and_presence_realtime.sql with per-user Storage policies and conditional publication of public.user_presence to supabase_realtime.
- Added avatar URL to registered-user records and circular avatar/initial fallback UI. Registered-users screen now subscribes to Realtime presence changes while retaining polling fallback.
- Added an explicitly user-enabled draggable overlay bubble that opens the app's Chats screen, plus Settings flow for overlay permission and a disable action.
- Applied root WindowInsets.safeDrawing to protect main screens from status/navigation bars. Launcher icon assets and manifest icon references were not modified.
- Verified CI run 193 for source commit 7fcba991a1a6757e5aedda9a4bf618b6e743869d: lintDebug, testDebugUnitTest, assembleDebug, and artifact upload passed. Artifact SHA-256: 7a6098cc204d63c12c54432fd77beba52626cbee39e67496a1f19ef62f221096.
- Live Supabase migration execution and physical-device behavior remain pending.


## 2026-10-19 — Floating bubble launch fix (CI verified)

- Follow-up commit 22da77c4acad7067c01ee209930eb04e9a832ca4 changes the bubble launch flags to CLEAR_TOP so an existing MainActivity is recreated with the open_chats extra instead of remaining on its prior destination.
- Android CI run 195 passed lintDebug, testDebugUnitTest, assembleDebug, and artifact upload: https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/39258139369
- Artifact SayangqoeMeet-Android-APK: 37,075,511 bytes, SHA-256 a4725eedb51353a6cd04a80912309117079a1fe0247a058cdcbe03b8317bb039, expires 2026-11-02.
- Live Storage/Realtime migration and physical-device tests are still pending.
