# ADR-018: Circuit Breaker Design

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** A failing secondary system (like Redis cache or notification server) must not freeze primary ticketing actions.

## Decision
We wrap connection requests with a `CircuitBreaker`:
- **CLOSED:** Standard state. Failures increment failure counter.
- **OPEN:** Triggered when failures exceed 5 consecutive timeouts. Inbound requests fail fast to prevent connection queueing.
- **HALF_OPEN:** Triggered after 30 seconds. One request is allowed to test system health. If successful, state reverts to CLOSED.

## Fallback Actions
- If Redis is unavailable, the application falls back immediately to PostgreSQL direct queries.
- If the Worker system slows down, batch processing transitions to synchronous write-through logic.

## Consequences
- Guarantees high availability in degraded modes.
