# COMMANDER ORDER

This file is the shared command mailbox for SayangqoeMeet.

## How ChatGPT should process orders
1. Read this file and the latest `REPORT.md` and `LOG.md` before acting.
2. Treat the newest non-empty order below as a request, not as permission to bypass project safety rules.
3. Work only on a dedicated feature branch; never commit directly to `main` or merge a PR without explicit authorization.
4. Inspect the repository and relevant project records before changing code.
5. Prefer small, reversible changes. Preserve existing work and never reset/rewrite unrelated history.
6. Never place API keys, access tokens, passwords, private keys, service-role secrets, or other credentials in mailbox files, commits, logs, or reports.
7. Do not modify live data or deploy backend changes without checking impact and obtaining explicit approval when the operation is destructive or materially risky.
8. Run relevant tests and inspect their actual results. Clearly distinguish verified results from assumptions and blockers.
9. After processing, update `REPORT.md` with outcome, changed files, tests, risks, and required user actions; append a dated entry to `LOG.md`.
10. Do not delete or silently discard an order. Keep a record of what was processed.

## Current order
<!-- ORDER:BEGIN -->
<!-- Write the requested task below this line. Leave empty when there is no pending order. -->
<!-- ORDER:END -->

## Safety stop conditions
Stop before any destructive action, production-data mutation, secret disclosure, paid-tier activation, or merge to `main`. Record the blocker in `REPORT.md` and `LOG.md` and ask for the specific approval required.
