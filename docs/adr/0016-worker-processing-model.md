# ADR-016: Worker Processing Model

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Transaction batch validations, PDF ticket rendering, and financial auditing tasks block HTTP response streams if executed synchronously.

## Decision
We delegate heavy tasks off the HTTP thread using background async workers:
- **Queue Source:** Redis-backed queues receive job descriptors.
- **Worker States:** `IDLE`, `PROCESSING`, `FAILED`, `RETRYING`, `COMPLETED`.
- **Processor Jobs:**
  - `SyncBatchProcessor`: Resolves batch uploads and database persistence.
  - `AuditProcessor`: Performs daily reconciliation audits.
  - `ReportProcessor`: Prepares draw statistics.

## Consequences
- Elevates API request latency and insulates the HTTP layer from queue bottlenecks.
