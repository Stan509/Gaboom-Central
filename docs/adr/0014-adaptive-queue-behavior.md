# ADR-014: Adaptive Queue Behavior

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Preventing peak-draw spikes from overwhelming database connection pools.

## Decision
The Go Gateway implements the `AdaptiveQueue` to control ingress sync requests dynamically:
1. **ACCEPT:** Latency is normal, database handles connections cleanly.
2. **DELAY:** Latency peaks or concurrent connections exceed 8,000. Delay requests in-memory.
3. **PRIORITIZE:** If the ticket is close to the draw close window, bypass standard queuing slots.
4. **RETRY:** Command client to use exponential retry backoff intervals (Server Busy 429 response).

## Consequences
- Insulates the business core against server congestion.
