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

## 11. Repository, secrets, and release workflow

- Keep source code and useful setup documentation in GitHub.
- Use branches and focused commits for coherent changes when practical.
- Review diffs and check that no secrets, private signing files, generated build output, or unrelated user files are committed.
- Use GitHub Actions for build/test automation when a suitable workflow is configured and the environment supports the required Android toolchain.
- A green workflow proves only the checks it actually runs; it does not prove real-world messaging, FCM delivery, or two-device WebRTC behavior unless those are explicitly tested.
- Keep build and setup instructions reproducible.
- Do not publish a release or change repository visibility without explicit authorization.
- Record dependency setup, external service setup, required secrets by name only (never values), and known limitations.

---

## 12. Required project records

Maintain these documents as implementation progresses:

- `masterplan.md`: stable requirements and append-only completion history.
- `PROJECT_STATUS.md`: current verified state, completed/in-progress/blocked work, tests run, and next priority.
- `AI_HANDOFF.md`: exact continuation instructions for the next session.
- `CHANGELOG_AI.md`: chronological record of meaningful development changes.

Update the status and handoff after significant milestones. Append changelog and master-plan completion entries without rewriting old history. Avoid claiming a milestone is complete unless the implementation and evidence support it.

---

## 13. Sequential implementation roadmap

Work in dependency order, adapting only when source/build evidence requires it.

1. **Foundation:** Android Gradle project, package/application identity, buildable app entry point, theme tokens, adaptive layouts, navigation, launcher icon and launch branding.
2. **Authentication:** Supabase setup, registration/sign-in/session lifecycle, profile flow, backend schema and RLS.
3. **Messaging:** conversation data model, secure queries, send/history/realtime behavior, pagination and error/retry states.
4. **Notifications:** FCM registration and delivery path, notification channels, default notification sound and vibration, privacy-aware tap routing.
5. **Calls:** WebRTC media, secure signaling, default system ringtone behavior, permissions, cleanup and failure recovery.
6. **Quality and security:** unit/UI/integration tests, RLS authorization tests, responsive-layout checks, dependency review and secret scanning.
7. **Release readiness:** reproducible build, setup guide, verified test matrix, known limitations, final status/handoff and release preparation (publishing only with user approval).

At every phase, build on real working code and verify the result before advancing. Do not substitute decorative mockups for required functionality.

---

## 14. Definition of done

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

## 15. Append-only master-plan completion history

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
