# Migration Strategy (Phase 0)

This document establishes the official migration strategy for the database schemas of Gaboom Borlette OS, adhering to the **Expand Before Replace** pattern.

---

## 1. Schema Migration Workflow
Every database modification must follow these steps:
1. **Expand Step:** Add new columns, tables, or foreign keys. Keep existing columns. Set new fields as nullable or with safe defaults.
2. **Coexistence Step:** Update the application write path to populate both old and new fields simultaneously.
3. **Backfill Step:** Create background scripts to migrate legacy records to the new fields asynchronously in batches.
4. **Deprecate Step:** Update reading paths to read from new fields. Mark old fields as deprecated in code.
5. **Contract Step:** Remove the old columns and triggers only after a complete verification period.

## 2. Rules of Safe Database Migrations
- **Idempotence:** Every migration script must be runnable multiple times without raising exceptions or altering final states.
- **Rollback Blocks:** Every migration file must include a matching rollback script (`db.backward()`) that restores schemas to the previous state.
- **Online Execution:** Avoid schema operations that require table locks (`ALTER TABLE ... RENAME`, long-running index creations) during active lottery hours. Create indexes as `CONCURRENTLY` (PostgreSQL).
- **Data Integrity:** Run transactional checks before and after to verify counts and sums.
