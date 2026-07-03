# ADR-008: Offline Ticket Lifecycle

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Tracking ticket states on the client and central server through intermediate validation and synchronization states.

## Decision
Every local ticket moves through the official life cycle:
- `LOCAL_ONLY`: Written in local Room DB cache.
- `PENDING_SYNC`: Enqueued in scheduler for upload.
- `VALIDATION_PENDING`: Uploaded to Go gateway buffer, awaiting validation outcomes from Django backend.
- `SYNCED` / `CONFIRMED`: Successfully written in central PostgreSQL DB.
- `REJECTED`: Validation failure (late ticket, invalid hash, or drift timeout).

## State Transition Schema
```
[LOCAL_ONLY] ──► [PENDING_SYNC] ──► [VALIDATION_PENDING] ──► [CONFIRMED]
                                                      │
                                                      └──► [REJECTED]
```

## Consequences
- Prevents double processing and ensures trace capabilities for audit runs.
