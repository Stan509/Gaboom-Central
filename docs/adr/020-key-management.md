# ADR-020: Key Management

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** A secure key management lifecycle is required to rotates device keys periodically.

## Decision
We implement a key management lifecycle handled by `KeyManagementService`:
- **States:**
  - `ACTIVE`: Key is valid and in use.
  - `ROTATING`: Key is undergoing scheduled updates.
  - `EXPIRED`: Key has exceeded validity duration (TTL).
  - `REVOKED`: Key is compromised and blacklisted.

## Consequences
- Elevates key security without interrupting active device operations.
