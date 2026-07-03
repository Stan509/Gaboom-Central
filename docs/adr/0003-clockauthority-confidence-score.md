# ADR-003: ClockAuthority Confidence Score

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Preventing agents from manipulating local system clocks to issue backdated tickets.

## Decision
The `ClockAuthority` evaluates trust using a score model:
- **100%:** Direct synchronization with NTP/Server Signed time (within 1 minute, drift < 5s).
- **95%:** Verified offset (sync age < 1 hour, drift < 30s).
- **80%:** Active session time continuity (sync age < 24 hours, drift < 2m).
- **60%:** System clock fallback (sync age > 24 hours or drift > 2m).
- **INVALID:** Drift exceeds 25 minutes or tampering detected.

## Trust Hierarchy Rules
Android local system clock must never act as the primary authority. Time resolution cascade:
$$\text{Primary Authority (NTP)} \longrightarrow \text{Server Signed Time} \longrightarrow \text{Last Sync} \longrightarrow \text{LotteryClock} \longrightarrow \text{Android System Clock}$$

## Consequences
- Protects the system against clock-rollback exploits.
