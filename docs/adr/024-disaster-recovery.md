# ADR-024: Disaster Recovery Strategy

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Preventing system outages and data loss in the event of hardware or host infrastructure crash.

## Decision
The database backup and restoration framework is governed by:
- **RPO (Recovery Point Objective):** $< 5$ minutes. Automated cron processes replicate database transactions.
- **RTO (Recovery Time Objective):** $< 15$ minutes. Direct standby databases failover in staging and production hosts.
- **Recovery Plan Steps:**
  1. Detect master database crash.
  2. Promote standby replica to primary.
  3. Validate schema integrity pings.
  4. Resume batch queue synchronizer workers.

## Consequences
- Protects financial ledger states against infrastructure crashes.
