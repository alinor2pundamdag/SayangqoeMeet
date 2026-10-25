# SayangqoeMeet

SayangqoeMeet is an Android communication app built with Kotlin/Jetpack Compose, Supabase, Firebase Cloud Messaging, and WebRTC.

**Requirements authority:** [masterplan.md](masterplan.md). See [AUDIT_REPORT_2026-10-25.md](AUDIT_REPORT_2026-10-25.md) for the deduplicated cross-layer audit, issue ownership, fix list, dependencies, and acceptance gates; [PROJECT_STATUS.md](PROJECT_STATUS.md) for a concise snapshot; and [AI_HANDOFF.md](AI_HANDOFF.md) for execution rules.

## Current implementation areas

- Android authentication and signed-in app navigation.
- Supabase-backed profiles, private conversations, invitations, and messaging.
- Row Level Security (RLS) and server-side Supabase Edge Functions.
- FCM device-token registration and push-notification functions.
- WebRTC call signaling and temporary Cloudflare TURN credential retrieval.
- Private media handling and cleanup workflows.
- An admin-console shell; full administrative functionality remains subject to backend authorization and the current plan.

Source presence or a successful HTTP response does not alone prove end-to-end behavior. Review the current status document and latest CI/device-test evidence before relying on any feature. Two-device push/call behavior, background/locked-screen calling, responsive UI coverage, and other open items are tracked in the master plan.

## Free-tier and security constraints

- Keep the project within available free-tier services; do not treat Supabase leaked-password protection as an action item.
- Never commit Supabase service-role keys, Firebase service-account JSON, private tokens, or signing keystores.
- Do not use client-side UI state as authorization. Enforce privileged operations on trusted backend code and preserve RLS.
- Firebase/FCM is an intentional third-party dependency for push; do not claim that the app has no third-party service processing.

## Android build

Use the JDK, Android SDK, and Gradle versions declared by the current project configuration and CI workflow. From the repository root:

```sh
./gradlew lintDebug testDebugUnitTest assembleDebug
```

The debug APK, if the build succeeds, is normally generated at `app/build/outputs/apk/debug/app-debug.apk`. Treat the exact latest CI run and its commit SHA as the verification source; do not infer a pass from an older run.

## Supabase changes

Inspect the complete migration ledger and repository migrations before any schema operation. Never run a blanket production `db push` without checking migration parity, dependencies, and the exact proposed SQL. Review changes, run available SQL/RLS tests, and verify the exact deployed function source and runtime behavior.

## Project status

See [PROJECT_STATUS.md](PROJECT_STATUS.md). Older dated changelog entries are historical records, not a statement of current status.
