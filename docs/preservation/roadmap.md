# Evolutionary Roadmap (Phase 0)

This roadmap outlines the staged progression of development phases for the Gaboom Borlette OS platform.

---

## 1. Evolution Stages

### Phase 0: Preservation & Skeletons (Current)
- Initialize centralized feature flags and testing frameworks.
- Establish baseline profiling parameters.
- No behavioral modifications.

### Phase 1: Security & Sync Repairs
- Resolve Go-Rust signature bypass (relaying `X-DEVICE-ID` and `X-PAYLOAD-SIGN` headers).
- Add support for offline ticket identifiers in database models and searches (`numero_ticket_offline`).
- Deprecate dynamic retroactive agent commission calculations.
- Switch production Docker CMD to Uvicorn/Daphne (ASGI) to support real-time websockets.
- Enable local database encryption (SQLCipher) on APK.

### Phase 2: Performance Scalability
- Migrate real-time quota calculations and locking mechanism (`RiskManagementService`) to atomic Redis counters.
- Optimize Go Gateway websocket concurrency buffers and pipeline tickets to Redis.
- Deploy Prometheus and Grafana dashboards for cluster-level metrics monitoring.

### Phase 3: Staging & Stressed Verification
- Deploy the updated container cluster to staging on DigitalOcean.
- Run load-testing suites simulating 10,000 to 100,000 active mobile terminals.
- Verify that latencies and lock durations satisfy the non-regression criteria.

### Phase 4: Production Rollout
- Progressive release via Feature Flags.
- Final deprecation of legacy interfaces.
