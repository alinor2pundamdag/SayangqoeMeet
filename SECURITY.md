# Security and repository hygiene

## Current repository posture

This repository is private. Keep it private until the owner completes the manual checks below and explicitly decides whether public visibility is appropriate. This document is operational guidance, not proof that every historical commit or external account setting has been audited.

## What may be committed

- Android Firebase client configuration (`app/google-services.json`) is not a Firebase service-account credential. Its API key is a client identifier, but it must be restricted in Google Cloud Console to the Android application package and required APIs. Do not treat an unrestricted key as safe for public release.
- A Supabase publishable key (or legacy anon key) may be embedded in an Android client only when Row Level Security and authorization checks are correctly enforced.
- Database migrations, non-sensitive source code, and public project identifiers can be committed when they contain no private user data or credentials.

## Never commit

- Supabase `service_role` or `sb_secret_*` keys.
- Firebase service-account JSON, private keys, FCM server credentials, or OAuth client secrets.
- GitHub personal access tokens, deploy keys, signing keystores/private signing keys, passwords, session tokens, or production secrets.
- `.env`, local override files, production dumps, user data, logs containing tokens, or private backups.

Store CI credentials in GitHub Actions Secrets and server credentials in Supabase Edge Function secrets. Use least-privilege permissions and rotate any credential confirmed to have been exposed. Removing a value from the current file does not remove it from Git history.

## Required manual checks before changing visibility

1. In Google Cloud Console, inspect the API key used by the Firebase Android configuration. Restrict it to Android applications, the package `com.sayangqoemeet.app`, the correct signing-certificate SHA fingerprints, and only required APIs. Confirm the restrictions match debug and release signing needs.
2. In Supabase project settings, verify Auth leaked-password protection is enabled.
3. In Supabase Edge Function secrets, verify `MEDIA_CLEANUP_CRON_SECRET`, `SUPABASE_SERVICE_ROLE_KEY`, and `FIREBASE_SERVICE_ACCOUNT_JSON` are configured only as server-side secrets where needed. Never paste their values into issues, chats, or logs. The available repository inspection cannot read or prove the values of these secrets.
4. Confirm that `MEDIA_CLEANUP_CRON_SECRET` in GitHub Actions Secrets matches the secret configured for the `cleanup-chat-media` function. Re-run the scheduled/manual cleanup only after verifying configuration.
5. Review all branches, tags, pull-request diffs, and Git history for accidental credentials or private data. If a real credential was exposed, rotate/revoke it first; history rewriting is a separate coordinated operation.
6. Validate database RLS policies and SECURITY DEFINER RPCs with two-user authorization tests before release. Do not change a SECURITY DEFINER function to SECURITY INVOKER or revoke its execution grant merely to silence an advisor warning without proving the app and authorization tests still work.
7. Run Android lint, unit tests, debug build, database migration replay, and two-user RLS tests when GitHub Actions minutes are available. A failed or unstarted CI run is not evidence that code passed.

## Actions-minute conservation

The Android CI workflow skips Markdown/docs-only changes, cancels superseded runs on the same ref, runs lint/tests/build in one Gradle invocation, and keeps APK artifacts for seven days. These reduce unnecessary use but do not restore an exhausted GitHub Actions quota.

## Findings that require account-owner action

- Firebase API-key restrictions must be applied in Google Cloud Console; repository code cannot enforce them.
- Supabase Auth leaked-password protection is a dashboard-level setting and must be enabled by an authorized project owner.
- GitHub and Supabase secret values are intentionally not readable through this repository audit. Confirm their presence and rotation status in their respective secret settings without sharing values.
