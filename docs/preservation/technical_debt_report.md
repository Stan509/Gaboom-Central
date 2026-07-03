# Technical Debt Report (Phase 0)

This report logs the technical debts identified in the Gaboom Borlette OS codebases.

---

## 1. Registered Debts

### TD-01: Dual Source of Truth for Agent Commissions
- **Description:** Two distinct classes calculate agent balances. `AgentLedgerEntry` aggregates ledger rows. `AgentCommissionService` calculates dynamic commissions based on current rates.
- **Consequences:** Modifying an agent's commission rate recalculates historical sales retroactively, corrupting past ledger reports.
- **Risk:** High.
- **Priority:** High.
- **Target Phase:** Phase 1.

### TD-02: Bypassed Signature Verification
- **Description:** Go Gateway discards the signature string returned from Rust and forwards the request to Django without signature headers.
- **Consequences:** Standard online tickets require HMAC validation, but tickets sent via Go bypass it.
- **Risk:** Critical.
- **Priority:** High.
- **Target Phase:** Phase 1.

### TD-03: Offline Ticket Identifier Loss
- **Description:** When syncing offline tickets, the client's `HL-XXXXXXXX` identifier is lost and overridden by server-side `CB-XXXX` generation.
- **Consequences:** Agents cannot search or pay for offline-printed tickets.
- **Risk:** Critical.
- **Priority:** High.
- **Target Phase:** Phase 1.
