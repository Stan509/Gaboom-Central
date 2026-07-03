# ADR-028: Distributed Tracing Architecture

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Requests traverse multiple boundaries (Android POS $\rightarrow$ Go Gateway $\rightarrow$ Django), masking latency bottlenecks.

## Decision
We introduce distributed trace telemetry tags:
- **Keys:**
  - `trace_id`: Global execution request tree ID.
  - `span_id`: Local component execution block ID.
- Context injection headers pass trace keys dynamically across JNI, gRPC, and REST calls.

## Consequences
- Allows developers to profile latencies component-by-component.
