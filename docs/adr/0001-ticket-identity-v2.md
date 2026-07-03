# ADR-001: TicketIdentity Evolution v2

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Preparing unique ticket identities to support offline-first distributed ticketing, recovery synchronization, and auditable validation levels.

## Decision
We extend the `TicketIdentity` model to incorporate:
1. `ticket_origin`: Categorizes ticket creation context.
   - Values: `ONLINE`, `OFFLINE` (local print), `RECOVERY` (sync recovery), `MANUAL_IMPORT` (backoffice import).
2. `validation_level`: Defines the trust score of the ticket integrity.
   - Values: `LOCAL` (local hash checked), `SERVER` (database saved), `CRYPTO` (HMAC signatures verified), `FINAL` (validated against final draw results).

## Consequences
- Allows exact tracing of a ticket's origin and current verification status.
- Zero impact on historical ticket lines (additive columns).
