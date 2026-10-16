# AI Handoff — SayangqoeMeet

## Start here

1. Read `masterplan.md` when it is imported. Preserve its existing text and append-only completion history.
2. Read `PROJECT_STATUS.md` for the latest verified project state.
3. Read this file and the latest entries in `CHANGELOG_AI.md`.
4. Inspect repository status, branches, recent commits, and relevant source files before editing.

## Current state

- The private GitHub repository and connector access have been verified.
- The repository currently contains project-tracking documentation only; the original Android source has not yet been imported.
- The original `masterplan.md` is not yet present in this repository.
- Do not claim application features or build success until verified against the actual source and tests.

## Next task

Safely recover and inspect the existing AndroidIDE project. Preserve modified and untracked files. Identify the original `masterplan.md`, actual Android package/version, Gradle configuration, manifest, source code, and any Supabase, Firebase Cloud Messaging, or WebRTC integration. Then plan a careful import into this repository.

## Working rules

- Do not delete, overwrite, rename, or discard existing project files or local changes without explicit approval.
- Do not commit secrets, API keys, tokens, passwords, signing credentials, or private configuration.
- Keep changes focused; verify changes and report what was actually tested.
- Update `PROJECT_STATUS.md`, this handoff, and `CHANGELOG_AI.md` after meaningful verified milestones.
- Preserve the original master plan and its existing completion history. Add new history entries at the end rather than rewriting old entries.
