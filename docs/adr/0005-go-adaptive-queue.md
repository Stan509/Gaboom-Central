# ADR-005: Go Adaptive Queue Preparation

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Scaling the gateway to handle 10,000+ concurrent active POS devices without causing service outages on the Django backend.

## Decision
We define the **Adaptive Queue** system in Go Gateway:
- **Admission Decisions:**
  - `Accept`: Under standard server load.
  - `Wait`: Throttle requests and enqueue in memory if Django latency spikes.
  - `Prioritize`: Prioritize ticket submissions closer to draw close times.
  - `Slow Down`: Trigger backpressure metrics to instruct devices to increase retry backoff times.
- **Decision Engine Metrics:** Based on:
  - Django server load/latency (from Health Check pings).
  - Draw time proximity (criticality multiplier).
  - Local connection state (quarantine or reputation bounds).

## Consequences
- Protects the database against sudden traffic spikes.
