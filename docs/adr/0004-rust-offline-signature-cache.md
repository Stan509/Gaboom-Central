# ADR-004: Rust Offline Signature Cache Preparation

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Need to allow validation of signatures locally when the device is disconnected from the main network for up to 25 minutes.

## Decision
Prepare the Rust validation layer for local caching:
- **Offline Signature Cache:** Cache computed signatures and credentials in device-side local memory.
- **Expiration Limit:** Set cache TTL to exactly 25 minutes.
- **Key Rotation Policy:** Automatically invalidate local cache and request a fresh server-signed key update upon network recovery.
- **Final Validation:** Every ticket validated offline must undergo a final cryptographic verify check on Django database write.

## Consequences
- Enables offline operations while enforcing strict cryptographic validation bounds.
