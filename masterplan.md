# SayangqoeMeet — Unified Master Plan (v2.0)

**Updated:** 2026-10-25 · **Repo:** `alinor2pundamdag/SayangqoeMeet` (public, branch `main`) · **Package:** `com.sayangqoemeet.app`
**Supabase:** `phjwzrrsaemgcedwpyyp` (Free tier) · **Firebase:** `sayangqoemeet-6b008` (Spark/free)

This is the ONLY planning/status document. It replaces the former `PROJECT_STATUS.md`, `AI_HANDOFF.md`, `CHANGELOG_AI.md` and `addedto.txt` (their full text remains in Git history). Update this file after every milestone; never claim a result without evidence.

---

## 0. Quick start for the next person/AI

1. Read this file top to bottom, then inspect the latest GitHub Actions run and `supabase/migrations/`.
2. Work from section 7 (backlog) in order. Section 6 (new plan) has priority over older items.
3. Never commit service-role keys, Firebase service-account JSON, keystore passwords or TURN tokens. Secret *names* only (section 9).
4. Do not run a blanket `supabase db push`; compare migrations with the live ledger first.
5. After finishing work: update sections 5, 7 and append to section 12.

## 1. Mission and integrity rule

Build a polished, privacy-conscious Android communication app (sci-fi neon identity): registration/sign-in, profiles, private 1:1 chat with realtime, voice/video calls (WebRTC), push notifications (FCM), Supabase backend with RLS, and a separate admin console. No fake screens, simulated messages or pretend calls. A feature is **done** only when behaviour is implemented, failure states are handled, and checks/tests actually passed. A green CI run proves only what it runs.

## 2. Governance and fixed decisions

- This file is the requirements source of truth. Changes requested by the owner are recorded here (section 12) — no silent rewrites.
- **Free-tier decision (owner, 2026-10-25):** the project stays on Supabase Free and Firebase Spark. **Leaked-password protection will NOT be enabled** (paid feature); accepted risk. Mitigation: enforce a strong minimum password length/strength in the app UI and keep email confirmation on. Do not re-raise this item. Design everything to fit free-tier limits (Edge Function invocations, 500 MB DB, 1 GB storage, no pg_cron — scheduled jobs use GitHub Actions).
- **Privacy wording (truthful):** no ads, no analytics/telemetry SDKs, no AI features. Push uses Firebase Cloud Messaging (Google) — this is the one external service on the device and must be disclosed, not described as "zero Google".
- Android cannot guarantee that screen recording excludes call audio. Use `FLAG_SECURE` where appropriate and show an honest privacy notice; do not promise OS-level audio blocking.
- The launcher/app label is OS-controlled; the per-letter neon treatment applies to in-app branding and icon artwork only.

## 3. Visual identity (v2)

- Midnight/navy surfaces, cyan/electric-blue light, restrained violet accents; design tokens centralized (colors, spacing, type, shapes, glow, motion timing) in a single Theme/tokens file.
- **Wordmark:** per-letter spectral neon (cyan, ice blue, electric blue, teal, restrained violet/magenta) with luminous shadow; static fallback.
- **Header animation (NEW, supersedes the old "no literal rainbow" rule for the animated header only):** continuous left-to-right gradient sweep Neon Blue → Neon Green → Neon Yellow → Neon Pink → Neon Red, infinite smooth loop, fully client-side (no network/Supabase load). Must honour Android reduced-motion (static gradient), keep text contrast, never flash rapidly.
- **Auth panel:** dark rounded fields, visible labels, cyan focus glow, distinct error/disabled/valid states, password toggle, IME actions, autofill, scroll with keyboard open.
- **Card-deck slot inputs (NEW):** email/username and password fields presented as fluid stacked/slotting cards (inspired by the owner's OTP-card video reference); must stay accessible (≥48dp targets, TalkBack labels).
- **Green verification state (NEW):** on tapping Sign in, the form morphs into a rounded verification state; border and spinner glow neon green with the text "Verifying credentials securely…"; on failure return to the form with a recoverable error. The state must reflect the real Auth call, never a fake delay.
- Launcher icon: adaptive, readable at small sizes. Splash via Android SplashScreen API, no fake blocking loader.

## 4. Product requirements (kept, condensed)

**Responsive UI (mandatory):** window-size-aware layouts, insets/cutouts/gesture nav, keyboard handling, portrait/landscape, font scaling, long text, ≥48dp touch targets. Record which configurations were actually tested.

**Auth/profile:** Supabase Auth (register, sign-in, sign-out, session restore, deep link `com.sayangqoemeet.app://login-callback`). Private profile fields (address, birthday, phone, religion, bio) are readable only by their owner (via `get_my_profile`). Never log credentials/tokens/message text.

**Messaging:** authenticated 1:1 conversations via invitation (recipient joins only after accepting); history, realtime, idempotent send (`client_request_id`), retry without duplicates, pagination, pinned messages, soft delete, attachments (images, audio, video, text/plain) in the private `chat-attachments` bucket with download-confirmation + TTL cleanup (14 days).

**Calls:** real WebRTC, signaling via `call_sessions` / `call_ice_candidates`, short-lived TURN from `get-turn-credentials` (participant-checked), accept/decline/end/timeout, cleanup of camera/mic/audio resources, never show "Connected" before a real ICE connection. Release gate = real two-device test over different networks.

**Notifications:** FCM data-only push with recipient guard; separate channels for messages and calls; default notification sound + vibration (respect DND/silent); incoming call uses the system default ringtone, stops on answer/decline/cancel/timeout, no duplicate ringing; content must stay private on the lock screen.

**Architecture:** separate UI / domain / data / platform layers; no 85 KB single-file screens; centralized theme, navigation, config, errors; lifecycle-aware coroutines; tests for critical logic and RLS.

**Admin console (separate, access-controlled):** server-verified roles (`admin_memberships`), user lookup, reports/moderation queue with reasons, operational health, append-only audit log (`admin_audit_log`), pagination, destructive-action safeguards, role assignment only by the owner. Never ship the service-role key to Android/browser.

**Repo/release:** focused branches/PRs, GitHub Actions for build/test/migration replay/secret scan, no secrets in Git, no publishing or visibility change without owner approval.

## 5. Verified state (audit 2026-10-25)

| Area | State |
|---|---|
| Migrations | 32 in repo = 32 in live ledger (exact versions) |
| Database | 14/14 public tables RLS-enabled with policies; private profile columns not SELECT-able by other users |
| Edge Functions | 10 ACTIVE (create-conversation, respond-conversation-invite, open-private-conversation, send-message-push, notify-incoming-call, get-turn-credentials, confirm-message-media-download, cleanup-chat-media, cleanup-stale-calls, admin-console) |
| Storage | `profile-avatars` public (8 MB, jpeg/png/webp); `chat-attachments` private (50 MB, image/audio/video/text) |
| Realtime publication | messages, user_presence, call_sessions, pinned_messages |
| CI | Android lint+unit+debug APK; migration replay + RLS tests; secret-history scan; daily cleanup workflow (03:30 UTC) |
| Real usage | 3 auth users; 70 call sessions (27 ended, 23 cancelled, 12 missed, 8 declined); TURN credential calls return HTTP 200 from a physical Android 16 device |
| Admin | read-only overview + audit feed only; `admin_memberships` has 0 rows |
| Advisors | 3 intentional authenticated SECURITY DEFINER RPCs; 13 unused-index INFO (do not drop yet); leaked-password = accepted risk (section 2) |

**Not yet evidenced:** FCM delivery on devices, call media over restrictive networks, responsive/keyboard matrix, Android 13–16 matrix, release build. Whole app is **not production-ready**.

## 6. NEW PLAN (priority 1) — owner-requested features

Status key: ☐ not started · ◐ partial/unverified · ✔ verified

1. ☐ Neon sweep header animation (section 3), reduced-motion fallback.
2. ☐ Card-deck slot sign-in/registration inputs.
3. ☐ Green "Verifying credentials securely…" state tied to the real Auth request.
4. ◐ Floating chat head overlay (`SYSTEM_ALERT_WINDOW`, user-enabled, draggable, opens Chats) and PiP for video calls exist in source; device-verify and show the honest screen-recording notice.
5. ☐ Dynamic multi-album media picker (All, Messenger, Screenshots, Camera, Pictures, Viber, Download).
6. ☐ In-app CameraX capture (photo/video).
7. ☐ Voice notes: live waveform, scrubbing, slide-to-cancel.
8. ☐ Rich chat header (avatar, name, presence, call buttons).
9. ☐ Quick reactions (default 👍) — needs a `message_reactions` table with RLS + tests.
10. ☐ Settings screen (theme, notifications, bubble toggle, profile management).
11. ☐ No ad/tracker/analytics SDKs; keep dependency list minimal and documented.

Acceptance for each: implemented behaviour, empty/error/offline states, responsive check, unit/UI test where practical, evidence recorded in section 12.

## 7. Backlog from the 2026-10-25 audit (priority 2, in order)

**P1 — calls & notifications (AI agent)**
- B1. Merge the two incoming-call notifications into ONE (Accept/Decline actions, tap action, full-screen intent where permitted); a single ringing source (either channel sound OR `Ringtone`, not both); give the two channels distinct names.
- B2. Active-call foreground service with proper types (`microphone`, `camera`, `phoneCall` as applicable + matching permissions) so audio/video survive background/lock; keep it running until the call ends.
- B3. Prune stale FCM tokens: delete a token when FCM returns UNREGISTERED/invalid; cap tokens per user; share FCM code in `_shared/` instead of duplicating in two functions. (Live DB currently holds 15 tokens for 2 users.)
- B4. Notification-permission rationale + denial handling (replace deprecated `requestPermissions`).

**P2 — data retention & server robustness (AI agent)**
- B5. Extend `cleanup-stale-calls`: close calls stuck in accepted/connecting/connected after N hours, null out SDP and delete ICE candidates for terminal calls after 24 h, purge old terminal sessions; run it more often than daily (GitHub cron every ~15 min, or on-demand). Add a retention note per table.
- B6. Send push from a database trigger/webhook instead of relying on the sender's app staying alive.
- B7. Chat bubbles from notifications: `allowEmbedded`, `resizeableActivity`, `documentLaunchMode=always` on `ChatBubbleActivity` + MessagingStyle; otherwise remove the dead code.

**P3 — build/release (AI agent + owner)**
- B8. Fixed debug keystore (or keystore secret) so CI APKs update in place and a stable SHA-1 exists; then add the SHA-1 to Firebase.
- B9. Release build: signing config, `isMinifyEnabled` + ProGuard rules, `versionCode` bump, verify Play's required target API before any store release, release workflow (publish only with owner approval).
- B10. Split `AppScreens.kt` into feature packages; central Theme/tokens; add UI tests and a first batch of instrumentation tests; keep adding unit tests with every fix.

**P4 — admin (owner + AI agent)**
- B11. Provision the first admin (owner step, section 8). Then build: user lookup, `reports` table + RLS, report/block actions in the app, moderation queue, ops-health view, wired web console (replace static `admin-console/index.html`).

**P5 — polish**
- B12. Voice calls default to earpiece (speaker for video), audio focus/Bluetooth routing, ICE restart + disconnect timeout, notification grouping/replace and foreground suppression, constant-time secret compare, narrower CORS on JWT functions, subscribe to `call_ice_candidates` via Realtime instead of 1.5 s polling, optional "hide last seen" setting, confirm whether `bio` should be public.

## 8. Owner-only checklist (no code)

1. Restrict the Firebase Android API key in Google Cloud Credentials to package `com.sayangqoemeet.app` + SHA-1 (after B8) and to FCM/Installations APIs. The key is in a public file, so this is required.
2. Confirm Supabase Edge secret *names* exist: `FIREBASE_SERVICE_ACCOUNT_JSON`, `CLOUDFLARE_TURN_KEY_ID`, `CLOUDFLARE_TURN_API_TOKEN`, `MEDIA_CLEANUP_CRON_SECRET`; GitHub secret `MEDIA_CLEANUP_CRON_SECRET` (cleanup dry-run already returned 200).
3. Supabase Auth → URL Configuration: add redirect `com.sayangqoemeet.app://login-callback`.
4. Provision the first admin: `insert into public.admin_memberships (user_id, role, granted_by) values ('<YOUR_AUTH_UUID>','super_admin','<YOUR_AUTH_UUID>');`
5. GitHub: review collaborator `Bannerism1` (write access) and enable branch protection on `main` (require CI).
6. Optional token cleanup until B3 ships: keep only the 2 newest tokens per user in `device_tokens`.

## 9. Secret names (values never in Git)

Supabase Edge: `FIREBASE_SERVICE_ACCOUNT_JSON`, `CLOUDFLARE_TURN_KEY_ID`, `CLOUDFLARE_TURN_API_TOKEN`, `MEDIA_CLEANUP_CRON_SECRET` (+ platform `SUPABASE_*`). GitHub Actions: `MEDIA_CLEANUP_CRON_SECRET`. Android build: optional `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` (publishable key is public by design; RLS is the protection).

## 10. Two-device release test matrix (record real results)

Sign-up + email confirm + deep link · session restore · invite accept/decline · message send/history/realtime · push with app killed · voice + video on Wi-Fi vs mobile data (TURN relay) · incoming call on locked screen · decline from notification · ringtone start/stop, no double ring · attachment upload/download/expiry · PiP · overlay bubble · notification/camera/mic permission denial · font scale, landscape, keyboard · Android 13/14/15/16.

## 11. Roadmap order

A. New plan (section 6) → B. Backlog P1 → P2 → P3 → P4 → P5 → C. Matrix (section 10) → D. Release readiness (owner approval to publish).

## 12. Completion history (append-only)

- **2026-10-16** — Baseline spec; Compose foundation; Supabase Auth + initial RLS schema; signed-in shell; invitation Edge Functions; FCM client + `device_tokens`; admin UI shell.
- **2026-10-17/19** — Atomic conversation RPC; presence; profile details; WebRTC signaling + lifecycle guards; dynamic TURN credentials; avatars bucket; floating bubble; chat list RPC.
- **2026-10-20/22** — Attachments, pinned messages, realtime; media download confirmation + TTL cleanup; RLS/RPC hardening (search_path, grants, soft-delete boundaries, device-token RPC).
- **2026-10-23** — Message idempotency; admin memberships + audit log + read-only `admin-console` function; stale-call cleanup; service-role least-privilege grants; migration ledger parity; CI: replay, RLS isolation tests, Deno type-check, secret-history scan.
- **2026-10-24** — Profile save fix; `text/plain` attachments migration; owner added new-feature blueprint (`addedto.txt`).
- **2026-10-25** — Full audit (frontend vs backend vs docs). Consolidated all records into this unified master plan, merged the new-feature plan, recorded the free-tier decision (leaked-password protection not pursued), and removed the superseded files. No application code was changed in this entry.
