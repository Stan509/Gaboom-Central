# Definition of Done (DoD)

This document defines the mandatory criteria that every pull request (PR), feature branch, or hotfix must satisfy before it can be merged into the `develop` or `main` branches and promoted to production.

---

## 1. Code Quality & Standards
- [ ] Code complies with standard style guides:
  - **Python:** PEP 8 compliance checked via `flake8`.
  - **Kotlin:** Checked via `ktlint`.
  - **Go:** Code formatted via `gofmt` and checked via `golangci-lint`.
  - **Rust:** Code formatted via `rustfmt` and checked via `clippy`.
- [ ] No secrets, passwords, API tokens, or hardcoded HMAC keys are committed in cleartext.
- [ ] Existing code structure, namespaces, and documentations are preserved.

## 2. Testing Gates
- [ ] **Unit Tests:** All new classes and functions have corresponding unit tests. Coverage must not decrease.
- [ ] **Integration Tests:** REST/WebSocket API endpoints and device synchronization logic are covered by integration tests.
- [ ] **Regression Tests:** Execution of the preservation suite passes successfully without breaking legacy behaviors.
- [ ] **Security Checks:** Cryptographic signatures (HMAC), authorization JWT, and role-based permissions must remain verified.

## 3. Compatibility & Migrations
- [ ] **Expand Before Replace:** No existing database tables, columns, or routes are deleted or modified destructively.
- [ ] **Backward Compatibility:** Staged clients (old APK builds, PWA) can communicate successfully with the new deployment.
- [ ] **Idempotence:** Every database migration is fully idempotent and tested both forwards (upgrade) and backwards (rollback) without data loss.

## 4. Performance Gates
- [ ] Staging load tests show that API latencies remain within 10% of the established performance baseline.
- [ ] No database lock durations on critical tables (e.g. `TirageNumeroStats`) exceed the baseline stats.
- [ ] CPU and RAM consumption profiles under simulation show no memory leaks or unexpected spikes.

## 5. Feature Flagging
- [ ] The new feature is isolated behind a centralized feature flag.
- [ ] By default, the flag is configured to `False` (disabled).
- [ ] The system behaves identically to the legacy baseline when the flag is disabled.

## 6. Infrastructure & Deployment
- [ ] Dockerfiles and `docker-compose.yml` configurations build cleanly.
- [ ] All environment configurations are documented in `.env.example`.
- [ ] Database backups and verification tests run successfully before migration deployment.

## 7. Documentation
- [ ] The Technical Debt Register, Risk Register, and API inventory are updated to reflect any introduced changes.
- [ ] Architecture Decision Records (ADRs) are compiled for any significant design decisions.
