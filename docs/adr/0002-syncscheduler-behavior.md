# ADR-002: SyncScheduler Behavior Specification

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Need to establish a reliable, self-healing scheduling engine on Android terminals to synchronize offline tickets without battery drain or data loss.

## Decision
We define the `SyncScheduler` execution policy:
- **Frequency:** Every 15 minutes (default periodic interval) or triggered immediately on connection restoration.
- **Constraints:** Executed only when:
  - Network is `CONNECTED`.
  - Battery state is `NOT_LOW` (or connected to power).
- **Retry Strategy:** Exponential backoff (initial delay: 2s, factor: 2, maximum: 30s).
- **Queuing Priority Hierarchy:**
  1. **Priority 1 (High):** Clock Synchronization (`ClockAuthority`).
  2. **Priority 2 (High):** Draw Closing Information (Fetch closing times).
  3. **Priority 3 (Medium):** Pending Tickets (Upload local fiches).
  4. **Priority 4 (Low):** Configuration Updates (Fetch new limits/quotas).
  5. **Priority 5 (Low):** Device Logs & Local Statistics.

## Consequences
- Protects device battery life while ensuring prompt upload of high-priority transactions.
