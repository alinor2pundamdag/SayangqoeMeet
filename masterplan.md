# SayangqoeMeet — Master Plan

**Document status:** Living requirements source of truth; updated 2026-10-25  
**Established:** 2026-10-16; current decisions appended below  
**Repository:** `alinor2pundamdag/SayangqoeMeet`  
**Project type:** Android communication application  
**Authority:** This document is the sole authoritative product/technical plan. README, PROJECT_STATUS, and AI_HANDOFF are summaries, not competing plans.

> **Current-state rule (2026-10-25):** Older completion-history entries are historical records, not current status. Use the latest dated status and decision addendum below whenever an older statement conflicts with it. Never claim a feature is verified from source presence alone.

---

## 1. Project identity and mission

**Project name:** SayangqoeMeet

Build a polished, privacy-conscious Android communication app with a distinctive futuristic visual identity and dependable everyday communication features. The project must be real software, not a collection of decorative screens, placeholder buttons, fake authentication, simulated messages presented as real, or call screens that pretend to connect.

The new GitHub repository is a clean start for new source code. Do not require copying the old app's implementation. Use verified requirements in this master plan to design a new implementation.

### Core product goals

- Account registration and sign-in.
- User profiles.
- Private conversations and real-time messaging.
- Voice and video calling using WebRTC.
- Push notifications using Firebase Cloud Messaging (FCM).
- Supabase-backed authentication and application data, with proper authorization and Row Level Security (RLS).
- Responsive layouts that fit different supported screen sizes, orientations, densities, font scales, and system insets.
- A recognizable sci-fi neon identity with accessible contrast and restrained motion.
- Maintainable architecture, meaningful error states, tests, and reliable handoff documentation.

### Product integrity rule

Never mark a feature complete just because a screen or button exists. A feature is complete only when its actual behavior is implemented, its failure states are handled, and the relevant checks/tests have passed. Clearly identify external configuration or credentials required for live integration.

---

## 2. Requirements governance and change control

This document is the baseline specification.

1. Treat the current approved requirements as stable. Do not casually rewrite, remove, weaken, or reinterpret them.
2. When the user explicitly requests a new requirement or a change, update the relevant section of this master plan and record the change in the completion history at the end of this file.
3. If a requirement is technically impossible, platform-controlled, unsafe, or incompatible with another requirement, explain the limitation and document the closest truthful supported behavior. Do not promise impossible behavior.
4. Do not make unrelated requirement changes merely because a new implementation idea seems attractive.
5. Keep requirements, implementation, tests, and project status aligned. If implementation reveals a conflict or missing decision, document it and ask only when a decision genuinely belongs to the user.
6. A request to improve implementation does not authorize deleting historical completion records.
7. Before major changes, inspect the relevant source and Git history. Prefer focused, reviewable commits.

### Completion records

Append new dated entries at the end of this document. Do not edit or delete previous completion entries; append a correction if needed. Use the actual available date/time and timezone when recording timestamps. Do not invent timestamps, test results, or completion claims.

---

## 3. Visual identity and brand system

### 3.1 Overall style

Use a premium futuristic sci-fi interface: deep midnight/navy surfaces, controlled cyan and electric-blue light, subtle indigo/violet accents, restrained gradients, soft luminous edges, and readable typography. The interface should feel distinctive and polished rather than like a generic template or an oversaturated gaming dashboard.

Design tokens should be centralized (colors, spacing, typography, shapes, elevations, glow strengths, animation timing) so the visual system can be tuned consistently without scattered magic values.

### 3.2 Brand wordmark and glowing headers — required

The app name **SayangqoeMeet** must be visually prominent in branded UI, especially the app's welcome/sign-in experience and key top-level screens.

- Use a custom sci-fi wordmark treatment with a cool cyan-to-electric-blue glow, soft luminous shadow, and carefully controlled bloom.
- A subtle indigo/violet secondary highlight may be used to add depth, but cyan/blue remains the primary light identity.
- Use restrained pulse/shimmer animation where it improves the experience; never make text continuously flash rapidly or distract from reading.
- Preserve legibility and adequate contrast over the background.
- Provide a reduced-motion or static treatment when Android's animation preferences indicate that motion should be reduced, and avoid relying on glow alone to communicate information.
- Keep brand typography consistent across the sign-in, registration, profile, conversation, and calling experiences.

### 3.3 Login and registration panel — required

The sign-in and registration UI must feel integrated with the sci-fi brand rather than like unstyled default form fields.

- Username/email and password fields must have a polished dark surface, clear borders, rounded corners, and subtle cyan/blue luminous focus states.
- Focused, error, disabled, and valid states must be visually distinct and accessible.
- The application name in the authentication panel should use the same custom neon wordmark as the rest of the app.
- Provide visible labels or accessible equivalents; do not depend on placeholder text alone.
- Password visibility controls, validation feedback, loading state, and recoverable error messages should be included where appropriate.
- Keyboard appearance, IME actions, autofill, password managers, and scrolling when the keyboard is open must work properly.
- Visual effects must not reduce field readability, tap target size, or accessibility.

### 3.4 Distinctive per-letter app branding — required

The SayangqoeMeet brand should use a custom **sci-fi spectral neon** color treatment for individual letters, not a conventional literal rainbow.

- Choose a coordinated palette using cyan, ice blue, electric blue, teal, and restrained violet/magenta highlights. The result should feel futuristic and premium, not like a basic rainbow gradient.
- Apply per-letter colors or a carefully composed multicolor wordmark in the app's splash/welcome/authentication branding and other suitable in-app brand surfaces.
- Keep the treatment coherent with the cyan/blue luminous shadow and overall design tokens.
- Ensure sufficient contrast and provide a readable static fallback.

**Android platform limitation:** The OS-controlled app label shown under the launcher icon or in system installation/settings screens is normally plain text styled by Android, the launcher, or the device manufacturer. An app cannot reliably force each letter of that external system label to have its own color. Do not promise or attempt to override system UI. Instead, apply the custom per-letter neon treatment to the app icon artwork where supported and to the in-app splash/welcome/brand wordmark. Keep the actual package/application label correct and recognizable.

### 3.5 App icon and launch experience

Create original, clean launcher icon assets that match the sci-fi neon identity and remain recognizable at small sizes. Respect Android adaptive-icon and launcher requirements. The system may crop, mask, tint, or otherwise render launcher icons according to device settings, so verify on supported configurations where possible.

Use an intentional launch/splash experience consistent with Android's supported splash-screen APIs. Do not use a fake loading screen that blocks access unnecessarily.

---

## 4. Responsive UI and screen fitting — mandatory

Every app screen must detect and respond to the available layout space so content fits the user's device instead of being designed for only one fixed screen.

### Requirements

- Use responsive/adaptive Android layouts and window-size-aware decisions rather than hard-coded assumptions for one phone resolution.
- Support the range of Android screen sizes and densities targeted by the project, including compact phones and larger phones/tablets where the chosen architecture supports them.
- Respect system bars, display cutouts/notches, edge-to-edge insets, gesture navigation, and safe content areas.
- Ensure headers, forms, buttons, conversation lists, message composer, profile controls, and call controls do not become clipped or overlap.
- Support portrait and landscape where appropriate; preserve usable controls and hierarchy when the available width or height changes.
- Handle the on-screen keyboard opening and closing. Forms and chat composers must remain reachable, and important actions must not be hidden behind the keyboard.
- Avoid fixed pixel dimensions. Use density-independent sizing, constraints, flexible containers, scrolling when needed, and appropriate minimum/maximum widths.
- Handle long names, long messages, translated/large text, Android font scaling, and accessibility settings without clipping essential content.
- Respect Android's display and font scaling; do not force the user's device to a fixed resolution or disable system scaling.
- Maintain adequate touch targets and contrast across screen sizes.
- Use reusable responsive components and shared layout rules so screens behave consistently.

### Verification and acceptance criteria

A screen is not considered responsive merely because it compiles. Verify layouts at a minimum across representative compact, standard, and large/window-size configurations, different density settings where practical, portrait/landscape where supported, and with the keyboard visible. Use Android emulator/device screenshots or UI tests when available. Record which configurations were actually tested; never claim tests that were not run.

---

## 5. Authentication, profiles, and account security

- Use Supabase Auth for real registration, sign-in, sign-out, and session restoration.
- Validate user input both in the UI and at the trusted backend boundary where applicable.
- Store only the minimum profile data required by the product.
- Enforce user ownership and access rules on the backend, not only by hiding UI controls.
- Configure Supabase Row Level Security policies for every exposed table and verify both allowed and denied access paths.
- Never embed a Supabase service-role key, private API secret, FCM server credential, or other privileged secret in the Android client or repository.
- Public client configuration must be clearly separated from server-only secrets; use environment/secrets management for CI and backend deployment.
- Provide useful loading, empty, success, and recoverable error states.
- Protect private profile and account information and avoid logging credentials, tokens, message contents, or other sensitive data unnecessarily.

---

## 6. Messaging and conversation behavior

- Implement actual authenticated one-to-one conversation creation and messaging backed by the selected Supabase data model.
- Provide conversation history and real-time updates with clear loading, empty, offline, sending, sent, and failure states appropriate to the implementation.
- Verify conversation membership and message access with RLS. A user must not be able to read or write another user's private conversations by changing client requests.
- Use stable IDs and timestamps from trusted data sources; handle pagination and ordering consistently.
- Provide retry behavior for recoverable send failures without silently duplicating messages.
- Render long messages and different text sizes without breaking the responsive layout.
- Do not present local demo data or a simulated response as a real remote message.

---

## 7. Voice and video calls — WebRTC

- Use a real WebRTC implementation for audio/video media.
- Implement authenticated call signaling, call state transitions, accept/decline/end behavior, permission handling, and connection/error states.
- Use STUN/TURN configuration appropriate for reliable connections across different networks. TURN service credentials must not be hard-coded as long-lived secrets in the app.
- Handle network changes, failed negotiation, disconnection, timeouts, cancellation, and resource cleanup.
- Request camera and microphone access only when needed and explain the relevant permission context.
- Ensure the user can clearly see call status and can end a call reliably.
- Do not label a call as connected until a real connection state supports that claim.
- Test real calls between two authorized devices for release readiness; a single-device build or mock call screen does not verify end-to-end calling.

---

## 8. Notifications, vibration, notification sound, and ringtone — required

The application must provide useful notifications while respecting Android permissions and the user's system settings.

### 8.1 Message and general notifications

- Use Firebase Cloud Messaging for remote push notifications where configured.
- Use appropriate Android notification channels for message notifications and other distinct notification categories.
- Use the device's **default notification sound** for message/general notifications by default, unless the user chooses a supported in-app alternative.
- Enable vibration for relevant notifications by default where Android's notification channel and user settings permit it.
- Respect notification permission requirements, channel settings, device vibration settings, silent mode, Do Not Disturb, and other system controls. Do not try to bypass the user's choices.
- Do not assume a sound or vibration will occur when the user or system has disabled it.
- Keep notification content privacy-conscious; allow sensitive previews to be controlled where appropriate.
- Ensure notification taps open the correct conversation or relevant screen and handle stale/deep links safely.

### 8.2 Incoming-call ringtone

- Incoming calls should use a recognizable ringtone, defaulting to the device's **system default ringtone** unless the user selects a supported alternative.
- Treat call ringing separately from ordinary message notification sound and configure the implementation according to Android's current notification/call APIs and permission rules.
- Respect silent mode, Do Not Disturb, notification permissions, and platform restrictions. Do not promise audible ringing in situations where Android or the user settings suppress it.
- Stop ringing promptly when the call is answered, declined, cancelled, or times out.
- Prevent duplicate ringing for the same incoming call and clean up sound/vibration resources after every terminal call state.
- Test the behavior on supported Android versions and document limitations for foreground/background states.

### 8.3 Verification

Test notification permission grant/denial, channel settings, default sound behavior, vibration enabled/disabled, and incoming-call start/stop behavior where device/emulator capabilities permit. Record actual test results. Do not claim FCM delivery works until the Firebase project and server-side sending path are configured and tested.

---

## 9. Architecture and code quality

- Prefer a clear separation between UI, domain/business logic, data access, and platform integrations.
- Use maintainable Kotlin code and Android-supported APIs; avoid unnecessary dependencies.
- Centralize theme/design tokens, navigation, configuration, and error handling.
- Keep asynchronous work lifecycle-aware and avoid leaking activities, views, audio sessions, camera, microphone, or WebRTC resources.
- Include automated tests for critical logic and security-sensitive behavior where practical.
- Use clear naming, small focused components, and documented setup requirements.
- Pin or constrain dependency versions intentionally and avoid adding libraries without a concrete need.
- Do not create empty placeholder modules or dummy implementations merely to make the project look complete.
- If a service requires credentials or external infrastructure, implement the integration path and document the exact setup required without committing secrets.

---

## 10. Data model and backend requirements

The final schema must be derived from actual product flows and security requirements. At minimum, evaluate the need for:
- User profile records linked to Supabase Auth identities.
- Conversations and conversation membership.
- Messages with sender, conversation, content, and timestamps.
- Call/signaling records or a suitable secure signaling transport.
- Notification/device registration records only where needed.

For each table or backend endpoint:
- Define ownership, relationships, constraints, indexes, and retention expectations.
- Enable RLS on exposed tables and write least-privilege policies.
- Verify that unauthorized users cannot access or mutate records.
- Never rely solely on client-side filtering for privacy.
- Do not invent a schema and claim it is deployed; record migration files and deployment/verification status separately.

---

## 11. Admin console — required operations capability

The product requires a separate, access-controlled admin console for authorized operations and safety work. This console is distinct from the public Android user experience; an Android navigation item or hidden button is not an admin security boundary.

### Required capabilities
- Server-verified administrator authentication and role checks; never trust a client-side boolean or user-editable profile field as proof of admin status.
- User/account lookup and account-state overview with least-privilege access to personal data.
- User reports and moderation queue, with reasoned actions and an auditable action history.
- Operational health view for Supabase, messaging, notification delivery, and call infrastructure without exposing secrets.
- Clear permission-denied, loading, empty, and failure states; pagination and safeguards for destructive actions.
- Admin actions must run through trusted server-side endpoints/Edge Functions with explicit authorization checks. Never ship the service-role key to Android or a browser.
- Audit log records who performed an action, what was changed, when, and the reason, while minimizing sensitive content retention.
- Admin role assignment must be restricted to a trusted owner/operator workflow, not self-service registration.

### Verification and rollout
- Define admin-role storage/claims and least-privilege policies before exposing real admin data.
- Test both authorized and unauthorized access to every privileged endpoint.
- Keep admin console deployment separate from the Android app; do not show demo data as real operational data.
- The current Android admin entry is only a locked navigation placeholder, not a production admin console. A separate web console and its secure backend remain to be implemented.

## 12. Repository, secrets, and release workflow

- Keep source code and useful setup documentation in GitHub.
- Use branches and focused commits for coherent changes when practical.
- Review diffs and check that no secrets, private signing files, generated build output, or unrelated user files are committed.
- Use GitHub Actions for build/test automation when a suitable workflow is configured and the environment supports the required Android toolchain.
- A green workflow proves only the checks it actually runs; it does not prove real-world messaging, FCM delivery, or two-device WebRTC behavior unless those are explicitly tested.
- Keep build and setup instructions reproducible.
- Do not publish a release or change repository visibility without explicit authorization.
- Record dependency setup, external service setup, required secrets by name only (never values), and known limitations.

---

## 13. Required project records

Maintain these documents as implementation progresses:

- `masterplan.md`: stable requirements and append-only completion history.
- `PROJECT_STATUS.md`: current verified state, completed/in-progress/blocked work, tests run, and next priority.
- `AI_HANDOFF.md`: exact continuation instructions for the next session.
- `CHANGELOG_AI.md`: chronological record of meaningful development changes.

Update the status and handoff after significant milestones. Append changelog and master-plan completion entries without rewriting old history. Avoid claiming a milestone is complete unless the implementation and evidence support it.

---

## 14. Sequential implementation roadmap

Work in dependency order, adapting only when source/build evidence requires it.

1. **Foundation:** Android Gradle project, package/application identity, buildable app entry point, theme tokens, adaptive layouts, navigation, launcher icon and launch branding.
2. **Authentication:** Supabase setup, registration/sign-in/session lifecycle, profile flow, backend schema and RLS.
3. **Messaging:** conversation data model, secure queries, send/history/realtime behavior, pagination and error/retry states.
4. **Notifications:** FCM registration and delivery path, notification channels, default notification sound and vibration, privacy-aware tap routing.
5. **Calls:** WebRTC media, secure signaling, default system ringtone behavior, permissions, cleanup and failure recovery.
6. **Quality and security:** unit/UI/integration tests, RLS authorization tests, responsive-layout checks, dependency review and secret scanning.
7. **Admin console:** separate web operations console, server-verified administrator roles, user/report management, operational health, audit history, and authorization tests.
8. **Release readiness:** reproducible build, setup guide, verified test matrix, known limitations, final status/handoff and release preparation (publishing only with user approval).

At every phase, build on real working code and verify the result before advancing. Do not substitute decorative mockups for required functionality.

---

## 15. Definition of done

The project is not complete until all applicable criteria are met:

- The Android project builds successfully using documented instructions.
- Core screens adapt to supported screen sizes, orientation, insets, and keyboard behavior without essential clipping.
- Brand wordmark, login fields, icon, and launch experience follow the approved sci-fi neon visual system.
- Authentication and profile operations work against the configured Supabase project.
- Private messaging works between authorized accounts and is protected by verified backend access policies.
- FCM notifications work with configured infrastructure and respect system/user sound and vibration settings.
- Voice/video calls work between two authorized devices over real networks, with appropriate signaling and STUN/TURN configuration.
- Critical error, permission-denied, offline, and cleanup states are handled.
- Tests and checks have been run and their actual results recorded.
- No secrets are committed.
- Project status, handoff, changelog, and this master plan's append-only completion history accurately reflect the verified state.

Do not describe the entire app as finished while any required feature remains unimplemented, unconfigured, or unverified.

---

## 16. Append-only master-plan completion history

### 2026-10-16 — Initial baseline established

- Created this master plan in the new private GitHub repository as the baseline specification for a clean, new implementation.
- Recorded the user's requirements for responsive screen fitting, sci-fi cyan/blue glowing headers and brand wordmark, custom per-letter neon colors for in-app branding, styled login fields, device-default notification sound, vibration where system settings allow, and device-default ringtone for incoming calls.
- Documented the Android platform limitation that the application cannot reliably force multi-colored individual letters in the operating system's external launcher/install label; the intended custom treatment belongs in app-controlled branding and icon artwork.
- This entry records the specification only. It does not claim that any Android source code or feature has been implemented or tested.


### 2026-10-16 — Initial Android foundation added

- Added the initial Gradle/Kotlin/Jetpack Compose Android application foundation and launcher activity.
- Added a first sci-fi neon sign-in/registration UI with a spectral wordmark, scrollable form, password visibility toggle, and local input validation.
- Added a GitHub Actions workflow configured to attempt a debug build.
- Source and configuration files were re-fetched from GitHub to verify their saved contents.
- This milestone does not claim a successful build, working Supabase authentication, real messaging, FCM delivery, WebRTC calling, or runtime UI verification. Those remain pending until independently tested.


### 2026-10-16 — Supabase Auth source and initial RLS migration committed

- Added Supabase Kotlin Auth dependencies and connected the email sign-in/registration controls to a real Auth client, configured through a publishable key.
- Added a versioned SQL migration for profile, conversation, membership, and message tables with RLS.
- Tightened membership/message policies to avoid recursive membership-policy evaluation and arbitrary conversation joining.
- Hosted Supabase access is blocked in the current connector session: no projects are listed and access to the historical project reference was denied. The migration has not been applied remotely, and live authentication has not been verified.
- GitHub Actions status queries did not return a run or check result for the latest commits. Build success remains unverified.


## 2026-10-16 — Hosted Supabase verification and integration status correction

- Verified that the active Supabase project is reachable and healthy, with the initial private-messaging migration recorded and the `profiles`, `conversations`, `conversation_members`, and `messages` tables present with RLS enabled.
- Applied and committed a follow-up migration adding indexes for `conversations.created_by` and `messages.sender_id`, addressing the two foreign-key index recommendations observed in the performance advisor.
- Verified that the security advisor returned no findings at the time of this check and that API roles do not have execute permission on `public.rls_auto_enable()`.
- Corrected project documentation that had become stale and explicitly recorded Firebase Cloud Messaging as not configured.
- These checks do not certify production readiness: Android CI success, end-to-end Auth, automated RLS authorization tests, messaging/Realtime, FCM delivery, and WebRTC remain unverified or incomplete.


## 2026-10-16 — Migration version alignment correction

- Confirmed the hosted migration history version for `add_foreign_key_indexes` is `20261016134617` and aligned the committed SQL filename to `20261016134617_add_foreign_key_indexes.sql`. The earlier mismatched filename was removed; the live schema and migration history were not changed by this filename correction.


### 2026-10-16 — Admin console requirement clarified

- The user confirmed that an admin console is expected. The prior baseline did not explicitly specify an admin console, so this requirement is added rather than retroactively claiming it was already documented.
- Defined a separate web operations console with server-verified admin authorization, user/report management, operational health, audit history, and least-privilege trusted endpoints.
- The Android admin navigation currently provides only a locked placeholder; it is not a working or authorized admin console.


### 2026-10-16 — Signed-in app screens and secure conversation creation path

- Added the signed-in Compose shell and screens for profile, chat list, conversation history/sending, call controls, and a locked admin entry.
- Connected profile reads/writes and conversation/message list/send operations to authenticated Supabase REST calls using the active user session.
- Deployed the `create-conversation` Edge Function with JWT verification. It validates the authenticated caller, resolves the recipient username, and creates the conversation and both membership rows using server-side credentials. The source is committed in `supabase/functions/create-conversation/index.ts`.
- Added a visible Hang Up / Cancel control, while explicitly retaining WebRTC media/signaling as unfinished.
- Added the admin console as an explicit requirement. The current mobile entry is only a locked placeholder; the separate web console and backend authorization remain outstanding.
- These source/deployment changes still require Android build verification, two-account integration tests, and RLS negative tests.


### 2026-10-16 — FCM client foundation and admin console shell

- Applied and committed the RLS-protected `device_tokens` schema; live Supabase confirms the table exists with RLS enabled.
- Added Firebase Messaging client code, conditional Google Services Gradle plugin setup, a default notification channel, Android notification permission request, and best-effort device token registration.
- Added and deployed JWT-protected `send-message-push` Edge Function. It checks message ownership and sends only to other conversation members' registered devices. Actual delivery remains blocked until the Firebase Android app config and server-only `FIREBASE_SERVICE_ACCOUNT_JSON` secret are configured.
- Added `admin-console/index.html`, a responsive locked operations-console UI shell with no fabricated data. It is not a working admin console until server-verified role checks, privileged endpoints, and audit logging are implemented.
- Android build verification, FCM delivery tests, and device-level integration tests remain outstanding.


### 2026-10-16 — Secure invitation acceptance flow

- Added an RLS-protected `conversation_invites` table and matching committed migration.
- Changed the deployed `create-conversation` function to create a pending invitation and creator membership only; the recipient receives no conversation membership before accepting.
- Added and deployed `respond-conversation-invite`, which verifies the authenticated recipient and handles accept/decline. Updated the Android chat UI with pending invitation cards and recipient controls.
- The flow still requires two-account runtime tests and review of duplicate-invitation/rate-limit behavior before production use.


### 2026-10-16 — Invitation flow hardening follow-up

- Added duplicate pending/accepted invite checks to `create-conversation`.
- Updated `respond-conversation-invite` so the pending invitation is conditionally claimed before membership is added, reducing accept/decline race conditions; a failed membership write attempts to return the invite to pending.
- Redeployed both Edge Functions with JWT verification still enabled.
- This is source/deployment verification only; two-account runtime tests and rate-limit/abuse testing remain outstanding.


---

## 15. Current plan, verified baseline, and decisions — 2026-10-25

This section is the latest approved plan and supersedes contradictory earlier requirements/status statements. Keep prior completion history intact. **When an older requirement conflicts with an explicitly newer user-approved requirement, the newer requirement wins.** Edit or replace the old requirement so the operative plan is unambiguous; retain old completion records only as historical facts, not active requirements.

### 15.1 Verified baseline from the 2026-10-25 audit summary

- Repository: public GitHub repository `alinor2pundamdag/SayangqoeMeet`, default branch `main`.
- Live Supabase project reference: `phjwzrrsaemgcedwpyyp`.
- The audit reports 32 repository migrations and 32 matching live migration versions, 14 public tables with RLS enabled, and 10 active Edge Functions. Treat these as audit-reported results as of 2026-10-25; re-check the exact current head/live state before any migration or release.
- WebRTC call sessions and dynamic TURN credential retrieval exist in source; repeated HTTP 200 responses were observed from `get-turn-credentials`. This does not by itself prove reliable two-device audio/video, background behavior, or push delivery.
- Cleanup workers returned HTTP 200 in the reported dry-run. This is not proof that all retention edge cases are handled.
- FCM push delivery and UI responsiveness remain unverified end-to-end unless a dated test record provides device-level evidence.
- The repo's old README/status/handoff statements about 31 migrations, six tables, three functions, private visibility, or no WebRTC are superseded by this current baseline. Historical changelog entries remain historical.

### 15.2 Governance and documentation consolidation

- This file is the single source of truth for product requirements, priority, constraints, and plan changes.
- `README.md` is only an accurate user-facing overview/build guide. `PROJECT_STATUS.md` is only a concise snapshot of verified state and open risks. `AI_HANDOFF.md` is only an execution handoff pointing back here. `CHANGELOG_AI.md` records dated, evidence-based changes; it must not define a separate roadmap.
- The conflicting standalone `addedto.txt` feature proposal is retired and its compatible requirements are incorporated here. Do not recreate it or treat it as an authority.
- New requirements must be added to this file and recorded in its completion history. Do not delete prior completion records; add corrections when facts change.
- Never say a change is implemented, deployed, secure, or tested until the relevant source, deployment, and verification evidence has been inspected.
- Preserve existing functionality, authorization, validation, and RLS. Prefer additive, reviewable changes. Do not run blanket migration pushes against a live database without checking ledger parity, dependencies, and the exact migration plan.

### 15.3 Free-tier constraint

- The project must remain within available free-tier services wherever practical. Do not propose a paid-plan upgrade as a prerequisite.
- **Do not pursue Supabase leaked-password protection.** It is intentionally out of scope because the project is on the free tier. Record this as a consciously deferred plan-dependent control, not as an unnoticed task or release blocker. Continue to use strong password handling, secure Auth flows, and all other available security controls.
- Do not paste secrets, service-account JSON, access tokens, or private credentials into chat, commits, issue comments, or client-side Android code.

### 15.4 Approved visual and interaction direction

- The canonical visual direction remains the premium sci-fi/neon design defined in Sections 3.x: dark midnight/navy surfaces, cyan/electric-blue as the dominant brand lighting, restrained indigo/violet accents, readable typography, accessible contrast, centralized design tokens, reduced-motion support, and no rapid distracting flashing.
- **Latest decision (2026-10-25): implement the literal local infinite-looping rainbow header.** This explicitly supersedes the older cyan/blue-only restriction for the header animation. The animation must run locally on the Android UI/rendering pipeline, require no network/backend calls, loop continuously and smoothly through neon blue → neon green → neon yellow → neon pink → neon red, and avoid unnecessary recomposition/work. Keep other surfaces and typography readable; do not imply that the rainbow is required on every UI component. Respect reduced-motion preferences with a restrained/static fallback if required for accessibility, while keeping the default header's approved infinite loop.
- Integrate compatible interaction ideas only where they improve usability and remain consistent with the canonical design: polished card-like auth fields, clear loading/verification state, accessible focus/error/disabled states, media album selection, CameraX capture where needed, voice-note waveform/scrubbing and cancel gesture, message reactions, profile/settings, and user-enabled chat bubble plus PiP calling. These are planned requirements, not claims that every feature already exists.
- Do not promise that Android screen recording will never capture call audio. Capture behavior is OS/device/API dependent and cannot be guaranteed by the app. Explain any supported privacy controls accurately.
- FCM/Firebase is an intentional service dependency for push notifications. Therefore do not claim “zero third-party services” or “zero telemetry” as an absolute guarantee. Avoid adding analytics/ad-tracking SDKs; document Firebase/FCM data handling and any unavoidable platform/service processing transparently.

### 15.5 Prioritized engineering backlog

**P0 — call reliability and privacy**
1. Inspect current call notification code and manifest. Consolidate duplicate incoming-call channels/notifications into one user-visible incoming-call experience with a valid tap action and Accept/Decline actions, following Android API/permission constraints. Avoid duplicate ringtone playback.
2. Ensure an active call has a correctly declared and started foreground service for the actual microphone/camera/media use cases, with Android 14+ foreground-service types and permissions matching current platform rules. Do not claim reliability until tested with screen locked/backgrounded on supported Android versions.
3. Add bounded lifecycle/retention handling for `call_sessions` and `call_ice_candidates`, including stale ringing, accepted, connecting, and abandoned states; minimize retention of SDP and IP-bearing ICE records. Use a new reviewed migration and idempotent cleanup function/scheduler. Never delete production data without a tested dry-run and explicit reviewed retention policy.
4. In both `send-message-push` and `notify-incoming-call`, handle provider responses for invalid/unregistered FCM tokens and remove only tokens conclusively reported as invalid. Keep transient provider failures retryable; do not delete tokens on generic errors.

**P1 — notifications, build/release, and integration**
5. Fix notification permission rationale/denial handling and provide a graceful non-push fallback.
6. Inspect `ChatBubbleActivity` and call/message notification behavior. Implement only the Android manifest launch modes/resize/embed properties that are actually supported and needed; use `MessagingStyle` where appropriate. Verify on devices rather than relying on manifest guesses.
7. Review CI signing/build outputs. Keep CI debug APKs clearly labeled as debug. Define a separate release-signing path only when the owner supplies secrets; never commit a keystore. Verify install/upgrade behavior and Firebase SHA fingerprints for the signing configuration actually used.
8. Consider server-originated push triggering (database webhook/trigger or another trusted backend path) so delivery does not depend solely on the sender app remaining alive. Design idempotency, authorization, and retry behavior before implementing.
9. Test Android notification permission denial, attachment download/cleanup, network changes, call timeout/ICE failure, and two-device voice/video calling across Wi-Fi and mobile data.

**P2 — admin, quality, and maintainability**
10. Admin console: inspect current web and Android code before changes. Keep all privileged operations server-authorized; build user lookup, reports/moderation, operational health, and audit history only with a defined schema, RLS, and backend enforcement. Do not grant admin access by guessing a UUID or running an unreviewed SQL statement.
11. Expand unit/integration/UI tests around auth, RLS, notification routing, call state transitions, media cleanup, and responsive Compose UI. Inspect `AppScreens.kt`, repository, call screen, resources, docs, Supabase tests, workflows, and open PRs before asserting coverage is complete.
12. Evaluate call polling/ICE restart and notification grouping after reliability issues are fixed. Keep existing indexes unless query plans and evidence justify a change; unused-index notices alone are not sufficient reason to drop them.
13. Consolidate duplicate FCM logic only after behavior parity and tests are established. Use constant-time secret comparison for cleanup endpoints where applicable.

### 15.6 Owner-only console/configuration work

- Firebase Android API-key restrictions, signing fingerprints, and service-account setup require access to the appropriate Firebase/Google Cloud console and a verified signing identity. Do not invent the SHA-1 or lock the key to a guessed certificate.
- Supabase Auth redirect configuration and secrets require dashboard access. Do not expose secret values.
- Admin membership provisioning requires confirming the owner's authenticated UUID and the exact schema/policy. Do not execute a guessed UUID or blanket privilege grant.
- GitHub collaborator access and branch protection require the correct repository owner/admin authority. A connected account with push permission does not imply admin permission.

### 15.7 Required execution protocol

For each engineering task:
1. Inspect current source, relevant tests, migrations, latest CI, and open PR dependencies.
2. State exact scope, risk, and rollback before editing.
3. Make one focused branch/commit-sized change; preserve existing features and security controls.
4. Run the narrowest relevant tests first, then the required full validation on the exact resulting commit.
5. Report changed files, commit/PR, actual test commands/results, unresolved blockers, and rollback. Separate verified facts from assumptions.
6. Never merge or apply a production migration merely because the source compiles. Require review and explicit evidence of the relevant checks.

### 15.8 Change record — 2026-10-25

- Consolidated the latest audit findings and current priorities into the master plan.
- Retired the conflicting `addedto.txt` proposal; canonical cyan/electric-blue branding remains authoritative, and literal rainbow branding is not approved.
- Recorded free-tier scope: leaked-password protection is intentionally deferred and must not be pursued as a task.
- Replaced stale current-state claims in summary documents with a single-source-of-truth policy.
- This is a documentation/governance change only. It does not claim that the listed engineering backlog has been implemented or that unverified device behavior now passes.


## 15.9 Latest user direction — 2026-10-25 (supersedes earlier conflicting plan)

- Apply the latest approved feature decisions even when they conflict with older master-plan text. Edit or replace stale requirements; do not leave contradictory active rules. Historical records remain intact only as history.
- Implement the **literal local infinite-looping rainbow header** by default: neon blue → neon green → neon yellow → neon pink → neon red, continuously and smoothly animated entirely on-device with no network/backend requests. Preserve accessibility and readability.
- Treat incoming-call notification deduplication/actions, correct foreground-service lifecycle and types, invalid/stale FCM token cleanup, and call-session/ICE retention as implementation work to complete—not merely backlog bullets. Make focused changes, test, and report actual results; do not mark complete without verification.
- Free-tier constraint remains binding. Supabase leaked-password protection remains intentionally excluded.
- This latest decision supersedes the prior 15.4 statement that rejected literal rainbow branding. The prior wording is corrected by this addendum; the latest requirement is operative.
