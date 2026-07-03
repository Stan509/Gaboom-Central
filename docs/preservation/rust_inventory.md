# Rust Inventory (Phase 0)

This document catalogs the Rust Validator service (`validator_rust`).

---

## 1. Concurrency and Framework
- **Runtime:** Tokio async engine.
- **gRPC Framework:** Tonic (based on hyper HTTP/2 implementation).
- **Protobuf Compiler:** Compiled via `build.rs` using `tonic-build` from `proto/validator.proto`.

## 2. Interface Definitions (gRPC methods)
- **`SignTicket(SignTicketRequest) -> SignTicketResponse`**
  - Receives ticket JSON data, generates HMAC-SHA256 signature, returns signature.
- **`VerifyTicket(VerifyTicketRequest) -> VerifyTicketResponse`**
  - Compares computed signature with payload.
- **`HealthCheck(HealthCheckRequest) -> HealthCheckResponse`**
  - Tonic-based service status checker.

## 3. Cryptography
- **Library:** `ring::hmac`.
- **Secrets:** Currently uses a hardcoded secret key `const HMAC_SECRET: &[u8] = b"gaboom-validator-secret-key-2026"`. Setting this via environmental parameters is planned for future phases.
