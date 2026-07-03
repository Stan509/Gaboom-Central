# Incident Response Playbook v1.0

This playbook documents recovery steps during service outages.

---

## 1. Outage Classification
- **Level 1 (CRITICAL):** Database down, JNI Rust validator signature failure.
  - *Action:* Trigger primary standby failover, notify NOC immediately.
- **Level 2 (WARNING):** Redis cache down, sync delays $> 10\text{ s}$.
  - *Action:* Revert Go gateway route limits, check worker CPU logs.

## 2. Post-Incident Review
Every Incident State transition from `RESOLVED` to `CLOSED` requires an ADR update log.
