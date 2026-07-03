# Security Architecture v1.0

This document defines the security parameters of the enterprise layer.

---

## 1. Cryptographic Schema
```
[ Android POS Device ] ──(Ed25519 signature)──► [ Go Gateway ] ──► [ Rust Validator JNI ]
                                                                             │
                                                                             ▼
                                                                     [ Django Core ]
```

## 2. Key Lifecycle States
- Generated keys transition through: `ACTIVE` $\rightarrow$ `ROTATING` $\rightarrow$ `EXPIRED` $\rightarrow$ `REVOKED`.
