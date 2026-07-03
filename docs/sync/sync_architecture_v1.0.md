# Synchronization Architecture v1.0

This document defines the interface boundaries and flow of the offline synchronization pipeline.

---

## 1. Batch Payload Specification
```
[ POS Local room DB ] ──(JSON payload)──► [ Go Gateway ] ──(gRPC check)──► [ Rust Validator ]
                                                │
                                                ▼ (verified forward)
                                         [ Django core ]
```

## 2. Sync Lifecycle
1. `SyncScheduler` checks battery/network limits.
2. Local sequence logs are bundled.
3. Signature verified.
4. Idempotency validation (checks global UUID).
5. Persisted to database.
