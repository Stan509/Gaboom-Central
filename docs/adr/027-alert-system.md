# ADR-027: Alert System & Incident Lifecycle

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Triggered alerts require tracking state transitions to prevent ignored notifications.

## Decision
We implement an `AlertManager` with a formal incident resolution loop:
- **Incident States:**
  - `OPEN`: Triggered when threshold is exceeded.
  - `ACKNOWLEDGED`: Operator assigns the incident.
  - `INVESTIGATING`: Debugging in progress.
  - `RESOLVED`: Fix applied, awaiting automatic verification.
  - `CLOSED`: Verification successful, archive log.

## Severity Levels
- `INFO`, `WARNING`, `CRITICAL`.

## Consequences
- Guarantees accountability and auditing of production alerts.
