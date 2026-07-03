# ADR-011: Sync Engine v2

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** A robust offline synchronization mechanism is required to process pending local transactions reliably when network connection transitions.

## Decision
We implement a WorkManager-based `SyncWorker` on Android with the following states:
- `IDLE`: Not currently synchronizing.
- `SYNCING`: Actively sending local payloads to Go Gateway.
- `RETRY_PENDING`: Enqueued to retry after a transient network drop.
- `FAILED`: Blocked due to conflict or validation failure.
- `COMPLETED`: Batch successfully persisted on central database.

## Consequences
- Enables background task durability even if the application process is terminated.
