# Architecture Preservation Report (Phase 0)

This report details the architectural bounds of the Gaboom Borlette OS project. It establishes guidelines to prevent structural degradation, API contract breaks, and schema mismatches during the upcoming development cycles.

---

## 1. Scope of Architecture Preservation
The preservation scope spans four major execution environments:
- **Django Core (Port 8000):** Legacy ORM models, validation rules, JWT structures, and API signatures must be kept untouched.
- **Go Gateway (Port 8080):** Concurrency hubs and HTTP routers must remain operational.
- **Rust Validator (Port 50051):** gRPC signatures and HMAC-SHA256 hash checks must be preserved.
- **Android APK Client:** Existing API Retrofit routes, SQLite Room structures, local HMAC logic, and print controllers must be backward-compatible.

## 2. Technical Guidelines for Developers
- **No In-place Refactoring:** Existing models or business methods cannot be edited directly to change parameters or behaviors.
- **Feature Isolation:** Any experimental behavior or rewrite must be placed behind a Feature Flag (`is_feature_enabled()`).
- **N+1 Query Constraints:** No changes to Django ORM prefetching or select-related targets must occur in legacy view controllers to prevent performance regressions.
