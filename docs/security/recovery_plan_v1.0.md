# Disaster Recovery Plan v1.0

This document defines the metrics and operational steps for disaster recovery failovers.

---

## 1. Core Timelines
- **RPO (Recovery Point Objective):** $< 5$ minutes.
- **RTO (Recovery Time Objective):** $< 15$ minutes.

## 2. Failover Validation Steps
1. Secondary Postgres replica check status.
2. Promote secondary replica to primary.
3. Validate sequence continuity of synchronized batches.
4. Unlock POS sync gateways.
