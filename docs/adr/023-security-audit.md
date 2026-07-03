# ADR-023: Security Audit Logs & Severities

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Need to log and route security incidents systematically for operator inspection.

## Decision
The `SecurityAuditService` records security events using the following severity levels:
- **INFO:** Keys rotated successfully, minor health check pings.
- **WARNING:** Debug mode active on a terminal, sync timeout occurred.
- **HIGH:** Device state drops below Trust Score 50, sequence broken events.
- **CRITICAL:** Invalid signature repeated, root bypass attempt, clock tamper detected.

## Consequences
- Simplifies routing of critical security alerts to the operator dashboard.
