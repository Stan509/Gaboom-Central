# ADR-025: Centralized Logging Strategy

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Operating a multi-service lottery framework makes tracking transactions difficult without correlated IDs.

## Decision
We implement a unified logging schema using the `CentralLogService`:
- **Context Identifiers:**
  - `correlation_id`: Unique transaction identifier.
  - `request_id`: Identifies the client HTTP session.
  - `device_id`: POS identifier payload.
  - `timestamp`: Epoch milliseconds.

## Consequences
- Enables uniform grep indexing across Android, Go, Rust, and Django log archives.
