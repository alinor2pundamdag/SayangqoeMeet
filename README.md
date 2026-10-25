# SayangqoeMeet

Privacy-conscious Android chat and calling app (Kotlin + Jetpack Compose) with a sci-fi neon identity. Backend: Supabase (Auth, Postgres + RLS, Realtime, Storage, Edge Functions). Push: Firebase Cloud Messaging. Calls: WebRTC with Cloudflare TURN.

**All plans, requirements, verified status, backlog and history live in [masterplan.md](masterplan.md).** Security policy: [SECURITY.md](SECURITY.md).

## Build

Requirements: JDK 17, Android SDK 36 / build-tools 36.0.0, Gradle 8.11.1.

```sh
gradle lintDebug testDebugUnitTest assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. CI (`Android CI`) builds it on every push/PR and uploads the artifact.

## Client configuration

Optional overrides via environment variables or an uncommitted root `local.properties`:

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_your_key
```

The publishable key is public by design; protection comes from RLS. Never put a service-role key, Firebase service-account JSON or TURN token in Android code or Git.

## Backend layout

- `supabase/migrations/` — ordered SQL migrations (must match the live ledger; do not blanket `db push`).
- `supabase/functions/` — Edge Functions.
- `supabase/tests/` — RLS/SQL tests run in CI.
- `admin-console/` — static web prototype (not yet a live console).

## Status

Not production-ready. See masterplan section 5 (verified state) and section 7 (backlog).
