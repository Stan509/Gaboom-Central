# ADR-013: Conflict Resolution Engine

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** A formal conflict categorization model is required to alert operators based on transaction drift and fraud events.

## Decision
Conflict incidents are indexed by severity in the central database:
- **LOW:** Duplicate ticket uploads (UUID already exists in server).
  - *Action:* Discard silently and log success (Idempotence).
- **MEDIUM:** Sequence number break or mismatch.
  - *Action:* Log warning and queue POS for device-state auditing.
- **HIGH:** Late ticket sync (upload occurs after draw has closed).
  - *Action:* Reject transaction, refund the agent ledger, and issue error message.
- **CRITICAL:** Cryptographic signature verification failure or clock rollback detected.
  - *Action:* Quarantine terminal, lock all local sales pathways, and alert administrator.

## Consequences
- Elevates operator awareness of network issues versus malicious tampering attempts.
