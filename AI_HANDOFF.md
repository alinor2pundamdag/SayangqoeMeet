# AI Handoff — SayangqoeMeet

## Start here

1. Read `masterplan.md` first; it is the baseline requirements source of truth.
2. Read `PROJECT_STATUS.md` for the latest verified state.
3. Read this file and the latest entries in `CHANGELOG_AI.md`.
4. Inspect the repository tree, Git history, and build/test configuration before editing.

## Current state

- The private GitHub repository and connected access have been verified.
- The new `masterplan.md` is committed and has been re-fetched to verify its content.
- The master plan defines a clean implementation from scratch, including responsive/adaptive UI, sci-fi cyan/blue glowing brand headers, per-letter neon in-app wordmark, login-field styling, device-default notification sound, vibration subject to system settings, and device-default incoming-call ringtone.
- Project-tracking documents are present.
- No Android source code or build has been completed or verified yet.

## Next task

Implement the actual Android foundation described in masterplan.md, beginning with a valid Gradle/Kotlin project and buildable app entry point, then adaptive branded UI. Avoid creating decorative-only screens or fake functionality. Verify the project builds in an Android-capable environment and report the exact checks performed.

## Working rules

- Treat `masterplan.md` as the requirements source of truth.
- Do not casually change approved requirements. Change them when the user explicitly asks; document changes in the master plan's append-only history.
- Do not delete, overwrite, rename, or discard existing project files or local changes without explicit approval.
- Never commit secrets, API keys, tokens, passwords, signing credentials, or private configuration.
- Keep changes focused; verify changes and report what was actually tested.
- Update `PROJECT_STATUS.md`, this handoff, and `CHANGELOG_AI.md` after meaningful verified milestones.
- Preserve old history entries; append new entries instead of rewriting previous completion records.
