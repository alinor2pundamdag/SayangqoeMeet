# Archived repository-only migrations

These SQL files are preserved for audit/history but are intentionally outside `supabase/migrations/`.

Reason: their timestamp versions do not exist in the active Supabase migration ledger. Keeping them in the executable migration directory would make a fresh replay run an alternate/duplicate history in addition to the canonical 25 ledger-matched migrations.

This is not permission to discard their behavior. Before merging, the fresh local replay and RLS tests must pass, and the canonical ledger-versioned migrations must provide the required final schema/policies/triggers. Do not apply these archived files to production or manually re-run them.

The original main-branch files are retained here verbatim so the migration-chain reconciliation does not lose their source content.
