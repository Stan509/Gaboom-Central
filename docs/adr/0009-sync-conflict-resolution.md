# ADR-009: Sync Conflict Resolution

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Defining final business logic rules on the Django server when sync conflicts or pricing discrepancies occur.

## Decision
The central server acts as the final source of truth. Conflicts are resolved as follows:
1. **Duplicate ticket submissions:** Checked by `global_uuid`. Duplicate payloads are discarded/ignored (idempotence).
2. **Late Ticket Sync (Draw Closed):** Tickets uploaded *after* a draw is closed and results are drawn are flagged as `REJECTED`, and the transaction is cancelled/refunded.
3. **Configuration Drift (Scenario 7):** If a ticket is sold offline using an outdated price config (e.g. price was updated on server during disconnect), validation fails, the ticket is rejected, and the admin panel issues a recovery reconciliation ticket.

## Consequences
- Protects draw fairness and isolates active sales from historical configuration changes.
