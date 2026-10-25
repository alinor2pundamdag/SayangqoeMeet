# SayangqoeMeet — Current Project Status

**Snapshot date:** 2026-10-25  
**Repository:** `alinor2pundamdag/SayangqoeMeet` (public; default branch `main`)  
**Requirements authority:** [masterplan.md](masterplan.md)  
**Scope:** Free-tier services only. Supabase leaked-password protection is intentionally deferred and is not a task.

## Audit-reported baseline

The 2026-10-25 audit summary reports 32 repository migrations matching 32 live Supabase migration versions, 14 public tables with RLS enabled, and 10 active Edge Functions. It also reports active WebRTC/TURN integration and successful maintenance-worker dry-run responses. These facts should be rechecked against the exact current commit/live environment before release.

## High-priority unresolved risks

1. Incoming-call notification/channel duplication and missing/incorrect notification actions.
2. Active-call foreground-service lifecycle and Android 14+ microphone/camera/media service requirements; background/locked-screen behavior requires device tests.
3. Call-session and ICE-candidate retention for stale accepted/connecting/connected calls, including IP/SDP data.
4. Stale FCM token handling must distinguish provider-confirmed invalid tokens from transient failures.
5. Firebase API-key restrictions and signing fingerprints are not verified in this snapshot.
6. Admin console remains incomplete; all privileged access requires trusted backend authorization, a defined reports/moderation model, and audit logging.
7. Debug-only CI signing/release build strategy needs a separate owner-approved signing setup.
8. Notification permission denial/rationale, chat-bubble behavior, sender-dependent push triggers, and test coverage need inspection and validation.

## Verification limits

The audit summary did not inspect all of `AppScreens.kt`, `SupabaseDataRepository.kt`, `CallCenterScreen.kt`, resources, docs, Supabase tests, all workflows, and open PRs. Responsive UI, actual FCM delivery, and reliable two-device/background calling are therefore **not verified** by this snapshot. Do not mark them complete until inspected and tested on the exact current code.

## Next execution order

1. Inspect open PRs, current CI, and relevant Android notification/call code.
2. Fix and test the single incoming-call notification/action path.
3. Fix active-call foreground-service lifecycle and validate on Android 14–16 devices.
4. Implement reviewed stale-call/ICE retention migration and cleanup behavior, with dry-run and rollback plan.
5. Harden FCM invalid-token cleanup and test push delivery with two devices.
6. Resolve Firebase signing/API restrictions with the real signing fingerprint.
7. Improve notification permission handling, chat bubble/PiP, release build strategy, and automated tests.
8. Scope admin-console work separately after schema and authorization review.

Detailed acceptance criteria, constraints, risks, and governance live only in [masterplan.md](masterplan.md). This file is a snapshot, not a competing roadmap.
