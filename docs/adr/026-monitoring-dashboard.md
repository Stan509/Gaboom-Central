# ADR-026: Monitoring Dashboard & Health Scores

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Operators need a real-time health indicator of the system status.

## Decision
We implement a `PlatformHealthScore` metric evaluated on the admin dashboard:
- **100:** Normal operations (all services return success).
- **80:** Minor degraded states (e.g., higher worker delays or warning events).
- **50:** Moderate degradation (e.g., Go gateway unreachable).
- **CRITICAL:** Core database connection down or security authentication failures.

## Component Monitoring Coverage
Pings are routed to: API, DB, Gateway, Sync, and Security components.

## Consequences
- Provides a single health index for NOC administrators.
