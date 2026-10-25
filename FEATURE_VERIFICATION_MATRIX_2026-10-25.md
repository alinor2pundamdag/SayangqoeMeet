# SayangqoeMeet — Feature-by-Feature Verification Matrix

Prepared 2026-10-25. Requirements authority: `masterplan.md`.
This is an evidence register, not a claim that all features work.

## Status definitions
- **PASS (automated scope only):** named automated checks passed on the exact commit; no inference of production/device success.
- **PARTIAL:** implementation exists, but important acceptance evidence is missing.
- **FAIL / GAP:** inspected mismatch or missing capability.
- **NOT VERIFIED:** insufficient evidence.
- **OWNER GATE:** requires owner/provider console or physical devices.

## Latest validation boundary
Previously verified head `5b1e96be25092775016f7804fea055f665a95641` passed Android CI #654, Phase 2 Database Validation #299, and Secret History Scan #234. Follow-up head `e99ed575f3d448a3c2a7f7e0ccbdeb16db1f9032` added failed media-cleanup queue counts. At last check, Secret History Scan #236 passed, while Android CI #656 and Phase 2 Database Validation #300 were still running. Latest-head status must be refreshed before relying on it:
- https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/40169736627
- https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/40169736626
- https://github.com/alinor2pundamdag/SayangqoeMeet/actions/runs/40169736651

## Feature matrix

| ID | Required feature (masterplan) | Evidence inspected | Current status | Required closure evidence |
|---|---|---|---|---|
| V-01 | Android project identity, reproducible build, package | Android manifest; Android CI workflow | PARTIAL | Current-head CI passes; install APK and launch on real supported device. |
| V-02 | Responsive layouts, insets, keyboard, font scaling, orientation | MainActivity and Compose call screen inspected; no full UI matrix | NOT VERIFIED | Compact/standard/large screens, landscape, keyboard, cutouts, gesture nav and large font screenshots/tests. |
| V-03 | Branded login/register, accessible fields, validation and error/loading states | Masterplan §§3–4; complete auth UI inventory not yet verified | NOT VERIFIED | Verify labels, password toggle, focus/error/disabled/loading states, autofill, IME, contrast and reduced-motion. |
| V-04 | Continuous local rainbow header: blue→green→yellow→pink→red | Approved requirement in masterplan; all header instances not yet inventoried | NOT VERIFIED | Trace target components; verify continuous local animation, no network dependency, static reduced-motion fallback and contrast. |
| V-05 | Supabase registration, login, logout, session restore, profiles | MainActivity and auth repository references | PARTIAL | Run lifecycle tests against configured project using test accounts; verify token expiry, profile access and logout. |
| V-06 | Backend validation, privacy, no privileged client secrets | Auth/backend code and secret-scan workflow | PARTIAL | Inspect client assets/history; verify no service-role or FCM private credential; test denied access and privacy-safe logs. |
| V-07 | Secure conversations and membership authorization | Repositories, migrations, two-user RLS test | PARTIAL | Compare live schema/ledger; test two unrelated users and anon across select/insert/update/delete. |
| V-08 | Message send/history, pagination, ordering, retries/idempotency | Messaging repositories and push function inspected | PARTIAL | Test retry after lost response, duplicate prevention, pagination, offline recovery, ordering and cross-user denial. |
| V-09 | Realtime messages/presence privacy and lifecycle | Realtime source/migrations; no dedicated end-to-end evidence | NOT VERIFIED | Test two-user isolation, reconnect, background/foreground, sign-out cleanup and duplicate subscriptions. |
| V-10 | Media upload/download, recipient confirmation, TTL and Storage cleanup | confirm-download and cleanup Edge Functions; path validator and retry migration | PARTIAL | Verify private bucket, conversation-scoped paths, RPC grants, concurrent/retry behavior and controlled end-to-end deletion; dry-run first. |
| V-11 | Call signaling and state transitions | CallCenterScreen, MainActivity, call migrations | PARTIAL | Two-device ringing→accept/connect/end and decline/cancel/timeout/failure races; assert final server state. |
| V-12 | Real WebRTC audio/video, STUN/TURN, network recovery | Call UI/manager references; no verified two-device evidence | NOT VERIFIED | Two devices on different networks; test audio/video, TURN, mute/camera, network handover and cleanup. |
| V-13 | One incoming-call alert/ringtone, vibration and privacy | Messaging service, call foreground service, manifest | PARTIAL | Verify one alert/ring per call; test Android 13–16 foreground/background/locked screen, silent/DND and permission denied. |
| V-14 | Notification tap, Accept/Decline and stale payload validation | Messaging service, MainActivity, receiver, UUID tests | PARTIAL | Cold/warm app, duplicate intents, invalid/stale UUID, wrong recipient and races; confirm real server state. |
| V-15 | Active-call foreground service and PiP/background operation | Manifest permissions, service and call screen | PARTIAL | Validate service types against target SDK; test Android 11–16 screen lock/background, mic/camera, PiP and termination. |
| V-16 | FCM token rotation and invalid-token cleanup | Push functions and shared FCM error classifier | PARTIAL | Test UNREGISTERED vs quota/auth/5xx/timeouts; delete only confirmed invalid token; verify live provider and deployed function parity. |
| V-17 | Message notification default sound, vibration, channels and tap routing | FCM service/channel source; device matrix absent | NOT VERIFIED | Grant/deny permission, channel settings, sound/vibration/DND, privacy preview and correct deep links on device. |
| V-18 | Stale call/ICE retention cleanup | cleanup-stale-calls, call migrations and SQL assertions | PARTIAL | Review live ledger, FK/RLS/grants, dry-run counts and rollback; prove active calls survive; owner approves before destructive enablement. |
| V-19 | Secure web admin console, MFA, operations/reporting/audit | admin console and server assurance code/tests | PARTIAL | Browser smoke test with admin MFA AAL2; prove anon/non-admin denied; verify audit actions and no service key in browser. |
| V-20 | Database integrity, RLS, RPC grants and migration parity | ordered migrations, phase2 SQL test/workflow | PARTIAL | Fresh replay and configured two-user test passed on earlier head; compare live ledger/schema and test every exposed table/RPC/Storage path. No blind db push. |
| V-21 | Firebase package/signing/API restrictions and server secrets | Source config cannot prove console state | OWNER GATE | Owner checks package `com.sayangqoemeet.app`, signing fingerprints, API restrictions, FCM project and server-only secrets. Never share secret values. |
| V-22 | CI, dependency review, secret scanning and release discipline | Android, Phase 2 and Secret Scan workflows | PARTIAL | All relevant workflows must pass on current exact SHA; green CI is not live/provider/device proof. |
| V-23 | Status, handoff, changelog and traceability | Masterplan and project records; PR #64 merged | PARTIAL | Keep matrix/status/handoff synchronized with exact SHA and evidence; source PR #61 remains separate until merged. |
| V-24 | Accessibility, long text, touch targets, empty/offline/error states | Not fully inventoried in this pass | NOT VERIFIED | Compose/UI/accessibility tests, TalkBack, reduced motion, large fonts, long content, offline and failure-state checks. |
| V-25 | Free-tier-only services and cost limits | Masterplan free-tier rule; full provider cost inventory not performed | PARTIAL | Inventory every provider/feature/limit and document free-tier constraints. Supabase leaked-password protection is explicitly excluded from tasks. |

## Closure rules
1. Do not mark PASS from source presence, compilation, or a screenshot alone.
2. Tie every CI result to the exact commit SHA. Any source change requires checking the new head.
3. Record source review, automated tests, hosted-provider checks and physical-device tests separately.
4. No destructive production migration or cleanup until live ledger/schema, dry-run counts, retention policy and rollback plan are reviewed.
5. Do not claim live FCM, hosted Supabase parity, two-device calls or admin MFA without actual evidence.
6. Do not include Supabase leaked-password protection in the action list.
7. Keep PR #61 draft until critical device/provider/owner gates are evidenced.

## Execution order
1. Refresh PR #61 exact head and Android/Phase 2/Secret Scan runs; fix failures and re-check latest head.
2. Complete the source inventory against every masterplan requirement and add any missing subfeatures to this matrix.
3. Compare all migrations against the hosted Supabase ledger/schema; no blind production push.
4. Run two-account RLS/Realtime/media tests and two-device notifications/Accept/Decline/WebRTC.
5. Complete owner-side Firebase, secret, cleanup scheduler/retention and admin MFA checks without exposing secret values.
6. Update PROJECT_STATUS.md, AI_HANDOFF.md, CHANGELOG_AI.md with exact evidence; merge/release only after all required gates pass.
