# Sync Failure Handling v1.0

This document defines the retry limits and error handling loops for synchronization failures.

---

## 1. Retry Classifications
- **NETWORK_ERROR:** Automatic retry using exponential backoff.
- **SERVER_BUSY:** Automatic retry using gateway throttling headers.
- **INVALID_SIGNATURE:** Critical security quarantine. Automatic retries are locked.
- **CONFLICT:** High-severity reject. Locked to prevent database duplication.

## 2. Recovery Loops
- If a device crashes mid-synchronization, the transaction logs reconstruct the state using the cached `recovery_id` upon app restart.
