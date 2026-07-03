# ADR-010: Offline Security Model

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Preventing extraction of client keys and securing validation transactions.

## Decision
The cryptographic boundaries are designed as follows:
- **Go Cryptographic Isolation:** Go gateway must not direct-access the `device_secret`. Secret verification calls are routed strictly via JNI/gRPC to the Rust validation layer.
- **Client Security:** Rust validator JNI binds are stored in native code, preventing simple decompilation.
- **Sequence Continuity:** sequential hashes check integrity across subsequent sequence numbers via cumulative hash chaining.

## Consequences
- Elevates anti-tampering resistance on POS terminals.
