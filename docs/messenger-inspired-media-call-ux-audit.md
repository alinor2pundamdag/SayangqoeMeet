# Messenger-inspired media and call UX audit

Audit baseline: `main` at `c4823c263c12f99f339ff1ab733272fa2e0a2162` (2026-10-24).

## Current validation baseline

- Android CI: passed for the baseline commit.
- Phase 2 database validation: passed for the baseline commit.
- Secret history scan: passed for the baseline commit.
- Live migration ledger matches the repository through `20261024041355_allow_plain_text_chat_attachments`.
- Live `send-message-push` v5 and repository source are byte-for-byte identical.
- Live `notify-incoming-call` v6 and repository source are byte-for-byte identical.
- Live `get-turn-credentials` v4 and repository source are byte-for-byte identical.
- Supabase security advisor still reports three authenticated SECURITY DEFINER functions and the Free-tier leaked-password warning. The latter is intentionally not enabled.

## Evidence-based feature comparison (two supplied videos)

| Feature | Existing implementation | Gap / action |
|---|---|---|
| Attachment tray | Basic camera, video, voice note, video picker, file picker | Add image/gallery album navigation, type-aware selection and validation. |
| Album directory navigation | Generic Android content picker only | Add a local album resolver and explicit album list without requesting broad storage access unless required. |
| In-app camera | Photo and video capture launch external camera activities | Replace with an in-app CameraX capture flow, with runtime permission checks and local-only capture. |
| Capture editing | Preview, discard and send only | Preserve existing preview; implement only real local editing controls, no fake filter/trim controls. |
| Voice recording | MediaRecorder, duration, slide-left cancel/right send | Preserve gesture; current waveform is decorative and not driven by mic amplitude. |
| Voice playback | Attachments show generic OPEN FILE action | Add inline audio playback and seeking for supported audio MIME types. |
| Chat header | Contact label, INFO/MEDIA, voice/video call actions | Missing online/last-active state and reaction controls; requires backend contract and additional design verification. |
| PiP | Controller with mute, camera and end-call actions; activity declares PiP | Verify live video lifecycle and action state on real devices. Do not use system-alert overlays as PiP. |
| Floating chat bubble | Explicit opt-in draggable overlay that launches the chat list | Clamp movement to display bounds; it is not yet a conversation-specific chat head. |
| Screen-recording privacy | No supported detector for arbitrary third-party recorder attempts found | Do not claim such detection. Evaluate supported Android capture protections and their user-visible tradeoffs. |

## Backend contract

- The `chat-attachments` bucket is private, with a 50 MiB Storage limit and a MIME allow-list. Android currently imposes a stricter 25 MiB upload limit.
- `uploadChatAttachment` normalizes MIME types against `supportedChatAttachmentMimeType`, rejects empty files, and uses authenticated Storage requests.
- Existing Storage policies and message RLS must remain intact. No client-side change may widen bucket access or relax membership/non-deleted-message checks.
- Live Edge Function source parity is confirmed for the three critical deployed functions. Version numbers alone were not treated as proof.

## Platform constraints

- Android PiP is a framework-managed activity mode. The Android developer guidance explicitly advises against using `TYPE_APPLICATION_OVERLAY` to implement PiP.
- Android does not provide a general public callback that tells an app when an arbitrary third-party screen recorder is attempting to capture its audio specifically. A reliable implementation must use supported platform controls and explain what those controls actually protect.
- Messenger-like interaction patterns can be implemented, but proprietary Messenger code, assets, or exact branding are not copied.

## Validation required after changes

1. Android lint, unit tests and debug APK build on the new feature branch head.
2. Deno type-check of every Edge Function and fresh migration replay / RLS tests remain green.
3. Tests for MIME allow-list, unsupported types, empty/oversized files, album query edge cases and voice playback lifecycle.
4. Real-device verification of camera permission denial/grant, album selection, audio recording/playback, call PiP and overlay bounds.
5. No production migration, Edge Function deployment, PR merge, or admin-console implementation is included in this feature branch.
