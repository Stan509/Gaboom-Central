# ADR-015: Redis Architecture

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** High throughput concurrency on draw closures requires cache offloading and single-access batch queuing locks to avoid race conditions.

## Decision
We deploy Redis as an infrastructure cache and mutex lock registry:
1. **Caching Layer:** Configuration keys and active ticket counts cached to reduce Postgres connections.
2. **Distributed Lock Strategy (`lock()`):**
   - Mutex locks set using `SETNX` (Set if Not Exists) with a configurable TTL (default 30 seconds).
   - If lock is held, requests retry for up to 5 seconds before returning a collision error (locks the worker slot).
   - Dynamic expiration ensures locks release in case of worker thread failure/crash.

## Consequences
- Prevents database deadlocks and double processing of sync batches.
