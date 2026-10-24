# SayangqoeMeet — Master Plan

**Document status:** Baseline requirements, version 1.0  
**Established:** 2026-10-16  
**Repository:** `alinor2pundamdag/SayangqoeMeet`  
**Project type:** Android communication application  
**Authority:** This document is the project's requirements source of truth. Implementation and tests must follow it.

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

## 15A. Messenger-inspired profile, settings, menu, and compose flows — user-requested addendum

**Status:** Approved requirement for research and implementation planning; individual features remain unverified until source, backend, tests, and supported-device behavior are audited.

### Scope and exclusions

Use the current public Messenger Android experience as a behavioral and visual reference, not as source code to copy. Preserve SayangqoeMeet's own name, original sci-fi cyan/blue identity, and accessible design tokens. Do not use Meta/Messenger logos or imply affiliation. Do not add Meta Account / Accounts Center settings. Marketplace and previously excluded Meta-specific features remain out of scope. Notifications are not to be expanded as part of this addendum unless an existing included flow technically depends on them.

### Required feature inventory

1. **Profile/account switcher:** Research Messenger's current account/profile switching behavior and supported constraints. For SayangqoeMeet, specify a secure in-app account/profile switcher only where the app's own authentication model supports it. Define account list, active-profile indicator, add/remove account, session restoration, switch confirmation/reauthentication where appropriate, per-account state isolation, error/offline handling, and sign-out behavior. Do not confuse multiple app accounts with Android OS multi-user profiles or Meta personal profiles.
2. **Settings/menu entry and navigation:** Audit the current Messenger menu button, profile entry, settings hierarchy, section ordering, icons, labels, dividers, spacing, sheet/page transitions, back navigation, and accessibility. Implement a Messenger-inspired information architecture while retaining SayangqoeMeet branding and not importing excluded Meta Account/Marketplace entries.
3. **Dark mode/theme:** Provide a real app-level theme setting with explicit preference persistence and deterministic application on app restart. Audit system/default, light, dark, and any existing project-specific theme requirement for conflicts before implementation. Theme changes must update all included screens consistently and preserve contrast/readability.
4. **Editable username/profile:** Provide an authenticated profile-edit flow with validation, loading/success/error states, duplicate/invalid username handling, and persistence to the authoritative Supabase profile record. Confirm how username changes affect chat identity, search, existing conversations, and realtime UI. Never treat a local-only display-name change as a successful backend update.
5. **App icon behavior:** Research what Messenger currently offers and distinguish the Android launcher icon/branding from an in-app icon setting. Document which behaviors Android actually permits. Only implement a supported SayangqoeMeet-owned launcher icon or icon-selection capability if technically appropriate; do not copy Meta's protected branding or claim that an in-app choice changes the launcher icon unless it really does.
6. **Photo & Media settings:** Add a dedicated settings destination for the user's photos/media-related app preferences and media management that are genuinely supported by SayangqoeMeet. Audit existing chat attachments, image/video selection, preview, download/open, storage access, cache and cleanup behavior. Clearly separate per-device settings from server-stored media and from conversation-specific media. Any deletion must have explicit scope/confirmation and must not silently delete other users' shared content.
7. **Log out:** Add a clearly discoverable logout action in Settings, with confirmation and correct session cleanup. Verify Supabase sign-out, local persisted session removal, active-account state reset, FCM token unregister best-effort behavior, navigation to authentication, and offline/server-failure behavior. Report local sign-out separately from server revocation if remote confirmation fails.
8. **Header and menu styling:** Study current Messenger header/menu composition (layout, icon placement, typography hierarchy, touch targets, scrolling, status/navigation bar insets, surfaces and transitions). Adapt the useful patterns to SayangqoeMeet's established neon visual system. Do not replace the existing brand system with a generic or copied Meta theme.
9. **New chat / new message:** Document and implement the complete flow from the chat-list action through recipient discovery/username lookup, recipient selection, direct-conversation open/create, pending invitation/acceptance where required by current product rules, conversation navigation, first-message compose/send, and visible result/error handling. Prevent duplicate direct conversations and unauthorized membership. Verify both users' view of the same conversation and message.
10. **Cross-layer integration:** For every included feature, map UI event/state → ViewModel/repository or current equivalent → Supabase Auth/REST/Realtime/Edge Function/Storage as applicable → database tables/RLS/functions → response/error → UI update. Include backend-to-backend dependencies such as Edge Function authorization, service-role-only operations, FCM/media integrations only where the included feature requires them, retries/idempotency, and audit/security considerations.

### File-by-file audit and implementation map

Start by inspecting the actual current tree and call sites; the paths below are starting points, not an assertion that all behavior belongs in a single file.

- `app/src/main/java/com/sayangqoemeet/app/AppScreens.kt`: inspect the signed-in navigation, chat list, profile/settings entry points, new-chat action and conversation route.
- `app/src/main/java/com/sayangqoemeet/app/MainActivity.kt`: inspect app-level theme state/persistence, authentication/session restoration, navigation intents, and sign-out lifecycle.
- `app/src/main/java/com/sayangqoemeet/app/SupabaseAuthRepository.kt`: inspect login/session restore/sign-out semantics and account switching boundaries.
- `app/src/main/java/com/sayangqoemeet/app/SupabaseDataRepository.kt`: inspect profile reads/writes, username lookup, conversation/message operations, attachment metadata, and error mapping.
- `app/src/main/java/com/sayangqoemeet/app/SupabaseRealtime.kt` and `MessageRealtimeReconciler.kt`: inspect realtime profile/conversation/message updates and duplicate/out-of-order reconciliation where relevant.
- `app/src/main/AndroidManifest.xml` and `app/build.gradle.kts`: inspect launcher icon resources, supported SDK/permissions, activity/navigation configuration, media dependencies, and build/test setup.
- `supabase/migrations/*.sql` and `supabase/tests/phase2_two_user_rls.sql`: inspect authoritative schema, RLS, uniqueness/idempotency, membership rules, and authorization tests before proposing any schema change.
- `supabase/functions/create-conversation/index.ts`, `open-private-conversation/index.ts`, and `respond-conversation-invite/index.ts`: inspect the current secure new-chat/open/create/invitation path before altering it.
- `supabase/functions/confirm-message-media-download/index.ts`, `cleanup-chat-media/index.ts`, attachment-related Kotlin files, and storage policies: inspect the existing media lifecycle before adding Photo & Media settings.
- Relevant Compose UI, repository, Edge Function, SQL migration, unit/integration test, documentation, and CI files must be added to this map as the audit discovers them. Prefer focused changes and additive migrations; never rewrite applied migration history.

### Required audit deliverables and completion gate

- A source-backed feature matrix: Messenger behavior/reference, inclusion/exclusion decision, current SayangqoeMeet status, target behavior, exact files and backend dependencies, security/privacy risks, and tests.
- A step-by-step user journey for profile switching, settings/menu, username editing, Photo & Media, logout, and new chat/message.
- A per-feature frontend-to-backend sequence and, where applicable, backend-to-backend sequence, including payload/data shape, authorization boundary, failure cases, and state reconciliation.
- A gap list that distinguishes already implemented, partially implemented, absent, blocked by platform/configuration, and not yet verified.
- Focused implementation commits on a separate branch, with no unapproved changes to excluded scope.
- Verification evidence: source review, available unit/static tests, Android build/CI result, backend/RLS tests, and physical-device/two-account checks where available. Never mark a feature complete based only on a screen or successful source edit.


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


### 2026-10-24 — Messenger-inspired settings and messaging-flow requirements added

- Added the user's requested scope for profile/account switching, the settings/menu structure and styling, dark mode, username editing, supported app-icon behavior, Photo & Media, logout, header/menu styling, and the complete new-chat/new-message journey.
- Explicitly excluded Meta Account/Accounts Center settings and retained previously excluded Marketplace and Meta-specific scope; the addendum preserves SayangqoeMeet's own brand identity.
- Required a source-backed feature matrix and file-by-file, frontend-to-backend/backend-to-backend implementation map, with authorization, persistence, failure states, and test evidence.
- This is a requirements/documentation update only. It does not claim that these features have been fully audited, implemented, built, or runtime-tested.
