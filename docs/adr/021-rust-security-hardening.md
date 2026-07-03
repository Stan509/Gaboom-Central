# ADR-021: Rust Security Hardening

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Strengthening mobile ticket integrity requires state-of-the-art cryptography.

## Decision
We integrate cryptographic routines within the Rust validator layer:
- **Encryption:** AES-256 binary cipher envelopes for device-to-gateway metadata storage.
- **Signatures:** Ed25519 public-key signature verifications (high speed, small signature size).
- **Hashing:** SHA-3 secure hashes for payload integrity verification.

## Consequences
- Protects the system against ticket tampering and replay attacks.
