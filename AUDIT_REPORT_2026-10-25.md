# SayangqoeMeet — Deduplicated Cross-Layer Audit & Fix Register

**Audit date:** 2026-10-25  
**Repository:** `alinor2pundamdag/SayangqoeMeet`  
**Audit branch:** `docs/consolidate-current-masterplan-2026-10-25`  
**Authority:** [masterplan.md](masterplan.md)  
**Purpose:** One deduplicated list of findings, accountable owners, implementation locations, dependencies, and acceptance evidence. This report is an evidence/action register, not a second requirements source.

## 1. Executive result

**Release readiness: NOT ESTABLISHED.** This audit confirms several source-level integration risks and documentation/PR inconsistencies. It does not certify production deployment parity, database security, live push delivery, or two-device calling.

The audited documentation branch is based on `main` and does not include the application changes in PRs #61 and #62. Those PRs are still open and unmerged. Their successful CI runs are evidence only for their respective exact head commits—not for `main`, PR #64, deployed Supabase functions, or real devices.

### Scope and evidence boundaries

Inspected in this pass:
- PR metadata and changed-file lists for #61, #62, and #64.
- Exact-head workflow summaries returned for PR #61 head `39a956d021477a5d5fa738c1b043e93765d476df`, PR #62 head `e4100fc4f891fc7421d19a5c273496ba8fcad202`, and PR #64 head `60ddd796167eb434686132e37a456f19722714c3`.
- `masterplan.md`, `README.md`, `PROJECT_STATUS.md`, `AI_HANDOFF.md`, and `CHANGELOG_AI.md` on the audit branch.
- `AndroidManifest.xml`, `AppScreens.kt`, `CallCenterScreen.kt`, `CallForegroundService.kt`, `SayangqoeMessagingService.kt`, `supabase/functions/notify-incoming-call/index.ts`, `supabase/functions/send-message-push/index.ts`, and `supabase/functions/cleanup-stale-calls/index.ts` on the audit branch.

Not fully re-audited in this pass: every Kotlin file/resource/test/workflow, the entire migration history and live schema, every deployed Edge Function's byte parity, current Firebase/Supabase console configuration, live logs, and physical devices. Those remain explicit verification tasks below; no absence is inferred where source was not inspected.

## 2. Duplicate cleanup rules

Merge duplicate symptoms into the canonical findings below. Do not create separate tickets for the same root cause unless implementation ownership or acceptance tests genuinely differ.

- “Duplicate incoming call alert”, “two incoming-call notifications”, and “service notification plus app notification” → **F-01**.
- “Accept button only opens app”, “tap does not enter the right call”, and “notification intent is not the actual call action” → **F-02**; verify the complete intent-to-authorized-call path before asserting the specific failure.
- “Foreground call drops in background”, “wrong FGS type”, and “service stops at call start” → **F-03**.
- “Stale FCM tokens”, “unregistered FCM token”, and “push failures due to dead tokens” → **F-04**.
- “Old call sessions”, “stale ICE/SDP”, and “retention cleanup” → **F-05**.
- “Old CI pass used as current proof”, “PR conflict”, and “merge order uncertainty” → **F-06**.
- “Rainbow header approved vs rejected” across masterplan, handoff, and changelog → **F-07**.
- “Admin page exists” vs “admin system is complete” → **F-08**.
- “RLS enabled” vs “cross-user access is denied” → **F-09**.
- “Media cleanup endpoint exists” vs “end-to-end cleanup works” → **F-10**.
- “Firebase config/API key restrictions/signing fingerprints” → **F-11**.
- “UI responsive/permission/bubble behavior” without exact-device evidence → **F-12**.

## 3. Canonical fix list (priority order)

| ID / Priority | Issue and verified evidence | Who owns the work | Where to change | Required action | Acceptance / double-check |
|---|---|---|---|---|---|
| **F-01 — P0** | Two separate incoming-call notification paths are present in the inspected source: `CallForegroundService` creates a persistent notification on its own channel/ID, while `SayangqoeMessagingService.showIncomingCallNotification()` posts a second notification using a call-specific ID and another channel. This can create duplicate visible alerts. | ChatGPT/code implementer; owner only for device tests | `CallForegroundService.kt`, `SayangqoeMessagingService.kt`, notification channel definitions, `AndroidManifest.xml` | Consolidate to one notification identity and one source of visible call actions. Preserve a separate service only if required for the ringtone/lifecycle. Make repeated delivery of the same call idempotent. | Unit/instrumentation tests for repeated same-call FCM; verify one visible alert per call on Android 13–16, foreground/background/locked screen; verify notification and ringtone both stop on accept, decline, missed, cancelled, ended, timeout, and logout. |
| **F-02 — P0** | Notification tap/Accept/Decline crosses FCM → PendingIntent/receiver → MainActivity → CallCenterScreen → Supabase call authorization. Source shows an activity PendingIntent for the call action and a separate broadcast receiver for decline; source presence alone does not prove Accept is an actual one-tap answer. | ChatGPT/code implementer; owner supplies two authenticated accounts/devices | `SayangqoeMessagingService.kt`, `MainActivity.kt`, `CallActionReceiver.kt`, `CallCenterScreen.kt`, call signaling repository | Trace each intent and call-state transition. Make Accept perform exactly one authorized accept/start flow; make notification body tap open the correct call without accidental duplicate accept. Decline must be idempotent and update the same session. Preserve UUID and recipient validation from PR #62. | Tests for cold/warm Activity, duplicate PendingIntent, malformed IDs, wrong recipient, expired/missed call, accept/decline race, and two-device live tests. Assert final server status and that media/ringing actually stop/start as expected. |
| **F-03 — P0** | Inspected manifest declares `CallForegroundService` as `mediaPlayback`; inspected service code is for incoming ringtone/vibration. CallCenterScreen calls `CallForegroundService.stop(...)` before setting up WebRTC. This does not establish a correctly running foreground service for an active microphone/camera call. | ChatGPT/code implementer; owner runs physical-device checks | `AndroidManifest.xml`, `CallForegroundService.kt`, `CallCenterScreen.kt`, `WebRtcCallManager.kt` | Separate incoming-ring and active-call lifecycle if appropriate. Determine valid foreground service types/permissions for target SDK and supported Android versions; start/stop at correct state transitions, including audio-only vs video, PiP, failures, and process/task transitions. Do not blindly add manifest types without checking SDK constraints. | Android lint/build; tests for start/stop/failure cleanup; physical Android 14–16 checks with screen locked, app backgrounded, camera/mic active, permission denial, call end, and notification visibility. |
| **F-04 — P1** | Both inspected Edge Functions send FCM requests and count `response.ok`, but do not distinguish permanent invalid/unregistered tokens from transient provider errors or remove only definitively invalid token rows. Client-side token rotation/sign-out cleanup exists but does not cover provider-rejected tokens. | ChatGPT/code implementer; owner configures Firebase secrets out-of-band | `supabase/functions/send-message-push/index.ts`, `supabase/functions/notify-incoming-call/index.ts`, device-token repository/table and tests | Parse FCM HTTP status and structured error code. Delete only the exact token conclusively marked unregistered/invalid; retain token on quota, auth/config, network, 5xx, or ambiguous errors. Log counts/codes without logging token values or message contents. Handle multiple device tokens independently. | Deno tests for success, UNREGISTERED, invalid token, quota, 401/403 credentials, timeout/5xx, mixed multi-device results, idempotent deletion, and cross-user token ownership. Verify deployed source parity before claiming live fix. |
| **F-05 — P1** | Existing stale-call cleanup marks old ringing sessions missed after a short cutoff; the inspected function does not delete ICE rows or define retention for accepted/connecting/connected/abandoned calls. Full live schema/foreign-key behavior has not been revalidated in this pass. | ChatGPT/code implementer; owner approves retention policy and production rollout | New reviewed `supabase/migrations/*.sql`, `supabase/functions/cleanup-stale-calls/index.ts`, call/ICE repositories, SQL/RLS tests and scheduler | First map actual call/ICE columns, foreign keys, status transitions, RLS and service-role grants. Define bounded retention for terminal/stale states; never delete active sessions. Minimize retention of SDP and IP-bearing ICE candidates. Add indexed, idempotent cleanup with dry-run, batch limits, and safe scheduling. | Replay migration on an empty database; test against a snapshot/representative data; prove active calls survive and stale states are cleaned; dry-run counts and rollback plan reviewed; verify live migration ledger before deployment. No blind `db push`. |
| **F-06 — P0** | PR #61, #62, #64 are open and unmerged. #61 and #62 both modify `MainActivity.kt`; PR #61 also changes app/call UX and database validation files, while #62 changes push validation and `SayangqoeMessagingService.kt`. Their interaction/merge order must be tested. | ChatGPT/code implementer; repo owner approves merge | PR branches, changed files, GitHub Actions and migration review | Compare exact diffs and common files; rebase/retarget as appropriate without force-overwriting work. Define dependency order; do not merge stale branches or use a prior head's green checks. PR #64 is docs-only and must not imply source fixes. | CI on each exact proposed merge head; migration replay/SQL/RLS/Edge Function checks when relevant; review conflicts and post-merge main CI. Physical tests remain separate. |
| **F-07 — P1** | Conflicting active statements were found: masterplan section 3.4 and historical section 15.8/old handoff/changelog described the literal rainbow as rejected, while section 15.4/15.9 approved it. Masterplan is being corrected on this branch; handoff/changelog still need the correction reflected. | ChatGPT/document maintainer | `masterplan.md`, `AI_HANDOFF.md`, append correction to `CHANGELOG_AI.md`, update `PROJECT_STATUS.md` link | One operative rule: header animates locally and continuously neon blue → green → yellow → pink → red; other surfaces may retain the sci-fi cyan/blue palette. Mark old rejection as superseded, not current. Keep history without leaving contradictory active guidance. | Search all current docs for “rainbow”, “retired”, “not approved”, “superseded”; inspect resulting contexts, not only keyword counts. Confirm no network calls are involved in the animation. |
| **F-08 — P1** | Admin shell/locked entry is not proof of a functional admin system. Trusted role assignment, privileged backend endpoints, schema, moderation/report workflow, and audit history require separate verification. | ChatGPT for implementation/review; owner authorizes real admin UUID/roles | `admin-console/`, Android admin entry, Supabase schema/RLS/RPC/Edge Functions, tests | Define exact admin use cases and data model; enforce authorization server-side and least privilege. No guessed UUIDs or client-only role flags. Keep admin work separate from call fixes. | Negative tests for anon/non-admin and cross-user access; positive test for intended admin; audit events verified; deployment parity and no service-role secret in client. |
| **F-09 — P1** | Audit summaries report RLS coverage, but this pass did not rerun every SQL policy/privilege test against the latest exact head/live database. “RLS enabled” alone does not prove isolation. | ChatGPT/code implementer; owner authorizes linked project operations | All tables, RLS policies, RPCs, grants, migration history and `supabase/tests` | Reconcile every repository migration with the live ledger/schema; test ownership, membership, private profile fields, messages, calls/SDP/ICE, storage objects, device tokens, admin tables, and SECURITY DEFINER functions. | Fresh database replay + SQL lint + tests with two distinct authenticated identities and anon; live catalog checks; exact deployed source comparison. Do not use guessed users or blind production pushes. |
| **F-10 — P1** | Media cleanup source and worker exist, but prior records state Android post-download confirmation and trusted scheduler configuration were not proven end-to-end. A successful dry-run is not proof of real cleanup. | ChatGPT/code implementer; owner configures secret/scheduler out-of-band | Media download confirmation client flow, `confirm-message-media-download`, `cleanup-chat-media`, cleanup migrations/queue, scheduler | Trace download completion → authenticated confirmation → recipient gating/TTL → queue claim → storage deletion → retry/final state. Verify service-only RPC grants and private bucket path restrictions. | Tests for one-to-one/group recipients, duplicates, retries, TTL fallback, invalid paths, concurrent workers, auth denial; controlled end-to-end test and dry-run before any production deletion. |
| **F-11 — P1** | Firebase app config, API-key restrictions, SHA fingerprints, and service-account secret availability are owner-side configuration items; source cannot prove they are configured. Debug signing is not a release-signing strategy. | Owner for console/secrets; ChatGPT provides exact checklist and verifies non-secret evidence | Firebase Console, GitHub Actions secrets, Supabase Edge Function secrets, Gradle signing configuration | Verify package `com.sayangqoemeet.app`, real debug/release signing fingerprints, API restrictions, FCM sender project and server-only credentials. Never paste secrets in chat or commit keystores. | Confirm safe configuration status without exposing values; send real test push to two devices; distinguish debug artifact from release build; verify no secret-history findings on exact head. |
| **F-12 — P2** | Responsive UI, notification permission rationale, chat bubbles/PiP, camera/media permissions, and Android 13–16 behavior need exact-device validation. Source and CI do not establish all UX behavior. | ChatGPT for source/tests; owner for physical device matrix | Compose screens, `MainActivity.kt`, `FloatingChatBubbleService.kt`, camera/media flows, resources and UI tests | Audit screen sizes, font scaling, keyboard, insets, permission denial/re-prompt, bubble opt-in, PiP transitions, media picker, and accessibility/reduced motion. Fix only demonstrated defects without deleting features. | Automated layout/unit tests where possible plus device matrix with recorded OS/API, permission state, steps, expected/actual result, screenshots/logs as appropriate. |

## 4. Cross-layer integration map

| User-visible flow | Frontend/client | Backend/provider | Data/security dependencies | End-to-end proof required |
|---|---|---|---|---|
| Sign in/session restore | Auth gate, MainActivity, session repository | Supabase Auth | user identity, session persistence, redirect/deep link | Fresh install, callback, cold start, expired/revoked session, sign-out/account switch |
| Message send and notify | Compose chat, data repository, FCM token registrar, messaging service | Supabase messages/membership, `send-message-push`, Firebase FCM | RLS, sender ownership, conversation membership, device_tokens, privacy-safe payload | Two accounts, push to correct devices only, stale token behavior, offline/retry, notification tap |
| Incoming call | FCM service, notification PendingIntents, CallActionReceiver, CallCenterScreen, WebRtcCallManager, foreground service/PiP | `notify-incoming-call`, call signaling, TURN credentials, FCM | caller/callee ownership, valid call state, call_sessions, ICE candidates, notification/mic/camera permissions | One alert, one-tap accept/decline, two distinct accounts/devices, locked screen, audio/video, STUN/TURN, terminal cleanup |
| Call cleanup/retention | Call state transitions and reconnection handling | stale-call worker/scheduler | schema, foreign keys, RLS/service grants, retention policy | Dry-run, active-call protection, stale-call reconciliation, ICE cleanup, idempotency |
| Media upload/download/cleanup | picker/capture, upload, download confirmation | Supabase Storage + confirmation/cleanup functions | private bucket, membership checks, queue, TTL, scheduler secret | authorized recipient access, invalid-path denial, download confirmation, group gating, retry/expiry |
| Admin/reporting | locked admin UI and web console | trusted role-gated RPC/Edge Functions | admin role source, audit log, table grants/RLS | non-admin denial and admin success using approved identity, auditable actions |
| Build/release | Gradle/manifest/resources | GitHub Actions, Firebase project config | signing fingerprints, secrets, migration ledger | exact-head CI, release build strategy, install/upgrade, runtime provider tests |

## 5. Pull request and CI reconciliation

| PR | Current state at audit | Exact head / evidence observed | Overlap / risk | Required next step |
|---|---|---|---|---|
| [#61 — Messenger-inspired media/call UX](https://github.com/alinor2pundamdag/SayangqoeMeet/pull/61) | Open, unmerged | Head `39a956d021477a5d5fa738c1b043e93765d476df`; Android CI run 580, Phase 2 Database Validation run 254, Secret History Scan run 160 all reported success for that SHA | Large mixed UI/media/call and migration/SQL scope; touches `MainActivity.kt`, `CallCenterScreen.kt`, manifest, repository, WebRTC and two migrations. Includes the local rainbow header. | Review exact diff and DB/RLS changes; preserve the now-approved rainbow requirement; test integration with PR #62; run two-device and media tests not covered by CI. |
| [#62 — Push payload UUID validation](https://github.com/alinor2pundamdag/SayangqoeMeet/pull/62) | Open, unmerged | Head `e4100fc4f891fc7421d19a5c273496ba8fcad202`; Android CI run 575 and Secret History Scan run 155 reported success for that SHA | Overlaps #61 on `MainActivity.kt`; modifies `SayangqoeMessagingService.kt` and adds UUID validation tests. Does not fix provider-side invalid-token cleanup by itself. | Reconcile shared-file changes with #61, preserve validation tests, verify exact combined head and real FCM navigation. |
| [#64 — Consolidate current audit and master plan](https://github.com/alinor2pundamdag/SayangqoeMeet/pull/64) | Open, unmerged | Current audited head before this report: `60ddd796167eb434686132e37a456f19722714c3`; Android CI run 582 and Secret History Scan run 162 reported success. Docs-only scope; no Phase 2 DB validation run shown for this head. | Its original PR description and handoff text incorrectly said literal rainbow was retired; the branch has now been corrected to the latest approved requirement and gains this deduplicated audit report. It does not include #61/#62 source changes. | Re-fetch updated docs, update PR description to reflect current contents, verify no contradictory active guidance, and do not describe source backlog as fixed. |

**Merge rule:** Do not merge based solely on this table. Refresh PR head SHAs and workflow statuses immediately before merge. Run integration checks on the combined result and verify the default branch afterward. PR #12 remains superseded/closed per prior project context; do not reopen it as a shortcut.

## 6. Consolidated documentation cleanup

- `masterplan.md` is the single source of truth for requirements and priorities.
- `AUDIT_REPORT_2026-10-25.md` is the deduplicated findings/evidence register and owner/action matrix.
- `PROJECT_STATUS.md` is only a short current snapshot with a link to the report.
- `AI_HANDOFF.md` contains execution rules and hard constraints, not a duplicate backlog.
- `README.md` is user-facing project overview/build instructions, not a competing status report.
- `CHANGELOG_AI.md` is append-only history. Add a correction entry instead of silently erasing historical statements.
- `addedto.txt` was removed on PR #64 branch; compatible requirements should live in the master plan. Do not restore it as a second authority.
- Supabase leaked-password protection is excluded because the project is constrained to free-tier services. It is not a fix, blocker, or owner action.

## 7. Owner-only actions (do not guess or ask for secrets)

1. Run physical-device checks with two distinct authenticated accounts and two devices; provide only non-secret outcomes/logs.
2. Confirm Firebase Console package/API-key restrictions and the signing fingerprints for the artifact actually installed.
3. Configure/confirm the Firebase service-account secret and any cleanup scheduler/secret out-of-band; never paste secret values into chat or Git.
4. Approve the retention period and deployment window after reviewing the dry-run output and rollback.
5. Approve the intended admin Auth UUID/role only if admin functionality is being implemented.

## 8. Release gates

Do not label the project fully complete until all applicable gates are evidenced on the relevant exact commit/environment:

- [ ] Current main/combined PR head has passing Android lint, unit tests, and APK assembly.
- [ ] Database migration replay, SQL lint, RLS isolation and migration-ledger parity pass on the exact change set.
- [ ] Edge Function type-check/tests pass and deployed source parity is checked.
- [ ] FCM provider responses and stale-token deletion are covered by tests.
- [ ] One incoming-call notification/action path is verified on real devices.
- [ ] Active-call foreground-service behavior is verified on supported Android versions.
- [ ] Two-device voice/video calling, ICE/TURN, failure/timeout, and terminal cleanup are tested.
- [ ] Call/ICE retention dry-run, policy, and rollback are reviewed before production deletion.
- [ ] Media cleanup end-to-end behavior and scheduler are verified.
- [ ] Firebase configuration/signing/API restrictions are checked by the owner.
- [ ] Admin functionality is either securely implemented and tested or clearly labelled incomplete/out of scope.
- [ ] All documentation statements agree with the latest requirements and evidence.

**No claim of “all fixed” is warranted until these gates are satisfied.**
