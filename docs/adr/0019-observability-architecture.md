# ADR-019: Observability Architecture

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Operating a distributed lottery system requires real-time monitoring of business indicators and technical bottlenecks.

## Decision
We deploy the `MetricsCollector` to capture telemetry:
- **Business Metrics:** `tickets_per_minute`, `sales_per_day`, `sync_success_rate`.
- **Technical Metrics:** `api_latency_ms`, `queue_size_batches`, `worker_time_seconds`, `db_latency_ms`.

## Execution
Metrics are collected as lightweight events. During high throughput, metrics are aggregated in memory to prevent performance overhead.

## Consequences
- Enables dashboard reporting and prompt alerting of SLO violations.
