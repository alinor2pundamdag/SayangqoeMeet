# SayangqoeMeet vs Meta Messenger — Feature Parity Audit

**Audit date:** 2026-10-25  
**SayangqoeMeet source branch:** `feat/messenger-inspired-media-call-ux`  
**Requirements:** repository `masterplan.md`; feature comparison uses public Meta announcements as examples of Messenger functionality, not as a claim that every Messenger account/region has every feature.  
**Important:** This is a source-evidence comparison, not a live-device or production certification. “Found in source” means a relevant implementation symbol/path exists, not that the feature is complete or works end-to-end.

## Current repository CI evidence

Latest PR #61 head inspected: `4bd16829fe7d812e53b47b3c854563dd094e255b`. All three repository workflows passed on that exact SHA:
- Android CI #658: https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/40170847936
- Phase 2 Database Validation #302: https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/40170847944
- Secret History Scan #238: https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/40170847972

This only establishes those automated checks passed. PR #61 is still open/draft; the changes are not on `main`. No live Supabase parity, deployed Edge Function parity, live FCM delivery, physical-device notification, or two-device WebRTC test is claimed here.

## Comparison status definitions

- **Implemented in source (unverified):** code/data model exists, but full end-to-end acceptance is not evidenced.
- **Partial:** some required pieces exist; important behavior or UX is missing/unverified.
- **Gap / not found in inspected source:** no convincing implementation was identified in the files inspected; a full repository search/test is needed before calling it definitively absent.
- **Not verified:** needs device, provider, production configuration, or security tests.

## Feature-by-feature comparison

| Messenger feature / user expectation | SayangqoeMeet source evidence | Parity assessment | Verification / action required |
|---|---|---|---|
| One-to-one private messaging | SupabaseDataRepository, conversation creation Edge Functions, private messaging migrations | Implemented in source; end-to-end PARTIAL | Test two real accounts, authorization, send/receive, history, offline and retry. |
| Real-time message updates | SupabaseRealtime and MessageRealtimeReconciler | Implemented in source; PARTIAL | Verify subscription lifecycle, reconnect, ordering, deduplication and two-user privacy on live backend. |
| Message history and pagination | Message repository and Compose chat UI | PARTIAL | Confirm cursor/page boundaries, no duplicate/missing rows, scroll preservation and long-history performance. |
| Delivery/read receipts and controls | No complete end-to-end receipt/control flow confirmed in this review | Gap / not verified | Define required semantics; implement server-authoritative delivery/read timestamps and privacy toggle only if in approved scope. |
| Typing indicator | No convincing typing-state implementation identified in inspected core files | Gap / not found in inspected source | Full repo search; if required, add ephemeral realtime typing with debounce, expiry and membership checks. |
| Per-message emoji reactions | `MessageReactionRecord`, `message_reactions` migration and `toggle_message_reaction` RPC | Backend/source exists; UI and end-to-end parity PARTIAL | Verify reaction tray/summary in UI, realtime refresh, toggle semantics, member-only RLS and concurrent toggles. |
| Reply to a specific message / swipe-to-reply | No complete reply target/quoted-message model confirmed in inspected core code | Gap / not found in inspected source | Full schema/UI search; add stable referenced message ID and safe quoted preview if in scope. |
| Edit sent messages with time window/history | No confirmed edit timestamp/version/history flow in inspected core code | Gap / not found in inspected source | Decide product policy; implement author-only time-bounded edit, revision audit and realtime update if required. |
| Delete/unsend message | Realtime reconciler handles deletion events and repository mentions soft-delete boundaries | Partial | Verify UI action, author/authorization rules, recipient behavior, retention/audit and realtime consistency. |
| Forward message to another chat/share sheet | No confirmed forward flow in inspected core UI/repository | Gap / not found in inspected source | Search full repo; implement explicit forward provenance and recipient membership validation if required. |
| Photo/video attachments | Attachment MIME/path helpers, local picker/camera, private Storage download/cleanup functions | Implemented in source; PARTIAL | Test upload, previews, MIME/size limits, private paths, download authorization, expiry and deletion on devices. |
| Photo/video editing, HD quality, collections/layouts | Picker/camera exists; no full Messenger-like editing/HD collection flow confirmed | Partial/gap | Specify which subset is intended; do not equate basic attachment support with editing or HD media parity. |
| Voice notes | InlineVoiceNotePlayer and audio recording references in AppScreens | Implemented in source; PARTIAL | Verify record/send/download/playback, permission denial, background playback and resume behavior. 1.5×/2× playback not confirmed. |
| Stickers and GIF search | No complete picker/provider/send flow confirmed in inspected core files | Gap / not found in inspected source | Decide scope; provider/license/cost and moderation implications need review before implementation. |
| One-to-one voice/video calls | CallCenterScreen, CallSignalingRepository, WebRtcCallManager, TURN credentials function | Implemented in source; end-to-end NOT VERIFIED | Test real devices on different networks, TURN relay, audio/video controls, interruption and teardown. |
| Group chats and group calls | Schema includes group-conversation concepts, but complete group member management/call UX not proven | Partial / not verified | Verify group creation/invites, role/membership changes, fanout, RLS and group call capacity; otherwise mark out of scope. |
| Incoming call notifications, tap, Accept/Decline | SayangqoeMessagingService, CallForegroundService, receivers and UUID validation | Partial; known duplicate-path risk requires device proof | Test one notification/ringtone only, cold/warm app, locked screen, malformed/stale payload and server state races. |
| Push message notifications and token cleanup | send-message-push / notify-incoming-call functions, token-error classifier | Source exists; production behavior PARTIAL | Test real FCM and distinguish invalid-token errors from quota/auth/network errors; verify deployed function matches repo. |
| Online status / active presence | SupabaseRealtime, presence heartbeat migration, scoped presence migration | Source exists; PARTIAL | Verify membership-only visibility, heartbeat expiry, reconnect, sign-out and no cross-conversation leakage. |
| Chat heads / floating bubbles | ChatBubbleActivity and FloatingChatBubbleService | Partial platform parity | Test Android overlay permission, Android 11–16 behavior, dismiss/drag, correct conversation routing and accessibility. This is not guaranteed equivalent to Messenger's OS integrations. |
| End-to-end encryption by default | No Signal-protocol or equivalent client-to-client key management verified in inspected code; Supabase server stores message records | **Major security parity gap** | Do not claim E2EE. Decide whether it is in scope; if required, threat-model and implement vetted E2EE/key lifecycle, multi-device recovery and safety-number/key-change UX. Never invent cryptography. |
| Secure history restore across devices | Supabase-backed history exists; E2EE secure-storage/key-recovery equivalent not verified | Partial / different security model | Define backup/restore model. If E2EE is introduced, add encrypted key backup/recovery and loss/recovery tests. |
| Disappearing messages / message expiry | Media TTL and cleanup exist; message-level disappearing timer semantics not confirmed | Gap / not found in inspected source | Do not confuse attachment cleanup with disappearing messages. Specify per-message expiry, client UX, server enforcement and audit constraints if required. |
| Screenshot alerts for disappearing messages | No implementation found in inspected core files | Gap / platform-limited | Android cannot reliably detect every screenshot scenario; specify platform limits and never promise universal detection. |
| Message requests, block/report controls | Invitation/report-related backend pieces exist, but full Messenger-style inbox/request/block/report UX and enforcement not proven | Partial / not verified | Verify backend enforcement, recipient controls, report audit and abusive-user denial. |
| App lock / private chat controls | No full biometric/app-lock flow confirmed in inspected source | Gap / not found in inspected source | Decide if required; implement platform biometric/device credential gate and secure task-switcher previews if approved. |
| Themes and per-chat customization | Local theme/branding code exists; complete per-conversation themes/reactions not verified | Partial | Separate global brand theme from per-chat theme customization; test persistence and accessibility. |
| Search messages, archive, mute, pin | Pinned-message schema/code references exist; full search/archive/mute feature parity not established | Partial / not verified | Verify each action independently; implement indexed search, archive state, mute notification policy and pin permissions if in scope. |
| Admin moderation and operational console | Admin console Edge Function and audit records exist | Partial | Test MFA/AAL2, role provisioning, least privilege, audit events, moderation flows and non-admin denial in browser. |
| Responsive UI and accessibility | Compose UI uses flexible layouts in places; no full device/accessibility matrix recorded | NOT VERIFIED | Test compact/large screens, landscape, keyboard, font scaling, TalkBack, touch targets, contrast and reduced motion. |
| Default sound, vibration, notification channels | Notification and manifest code exists | PARTIAL | Physical-device matrix across OS versions, permission states, DND, channel settings, lock screen and duplicate notification cases. |

## Important distinction: “Messenger-inspired” is not “Messenger-equivalent”

The repository contains a credible base for private messaging, attachments, realtime updates, reactions, voice/video call plumbing, presence, push notifications, and chat bubbles. That is not evidence of complete Messenger parity. In particular, E2EE, typing indicators, message edits, replies, forwarding, stickers/GIFs, message expiry, read-receipt controls, and polished media editing were not verified as complete in the inspected source.

Meta's official feature announcements provide examples of Messenger capabilities such as default end-to-end encryption and secure storage, message editing, disappearing messages, read-receipt controls, higher-quality media, and voice-note speed/resume; additional encrypted-chat features include reactions, replies, typing indicators, forwarding, stickers/GIFs and saving media. Feature availability may vary by rollout and product context. References:
- https://about.fb.com/news/2024/03/end-to-end-encryption-on-messenger-explained/
- https://about.fb.com/news/2023/12/default-end-to-end-encryption-on-messenger/
- https://about.fb.com/news/2022/01/updates-to-end-to-end-encrypted-chats-messenger/

## Recommended implementation sequence

1. **P0 — security and reliability:** resolve duplicate call notification/ringtone ownership; validate Accept/Decline; confirm call service lifecycle; compare hosted migration/function parity; prove RLS with distinct accounts.
2. **P1 — core Messenger-like UX:** complete reaction UI/realtime; reply-to-message; edit/delete policy; typing indicator; read/delivery states; forwarding; message search and notification preferences, ordered by the approved product scope.
3. **P1 — media/calls:** end-to-end attachment upload/download/expiry; voice-note playback UX; real two-device WebRTC/FCM tests; cleanup retries and safe retention.
4. **P1/P2 — privacy differentiators:** explicitly decide E2EE, disappearing messages, secure history recovery, block/report and app lock. E2EE is a substantial cryptographic architecture change, not a cosmetic ticket.
5. **P2 — media richness and polish:** stickers/GIFs, photo/video editing, HD/collections, per-chat themes, accessibility and responsive device matrix.
6. For each step: create acceptance tests, implement only approved scope, run CI on exact current SHA, verify backend/provider/device evidence, then update this matrix. Keep PR #61 draft until critical gates are satisfied.

## Scope and safety
- Do not claim parity based on a file name or successful build.
- Do not implement proprietary branding/assets or copy Messenger's exact UI; use familiar interaction patterns with SayangqoeMeet's own design.
- No destructive production migration/cleanup without live-schema review, dry-run and rollback plan.
- Free-tier-only constraint remains. Supabase leaked-password protection is intentionally excluded from action items.
