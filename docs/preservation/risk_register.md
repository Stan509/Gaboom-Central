# Risk Register (Phase 0)

This registry lists all identified operational, performance, and security risks of the Gaboom Borlette OS platform.

---

## 1. Active Risk Matrix

| Risk ID | Risk Category | Description | Level | Mitigation Strategy |
| :--- | :--- | :--- | :---: | :--- |
| **R-01** | Security | Secret HMAC key bypass on Go Gateway. | <span style="color:red">**CRITICAL**</span> | Relay all cryptographic headers from Go to Django. |
| **R-02** | Transactional | Offline ticket identifiers (`HL-XXXX`) are overridden by Django during sync, blocking client payment searches. | <span style="color:red">**CRITICAL**</span> | Persist the offline ticket ID in a dedicated database column. |
| **R-03** | Performance | Lock contention on `TirageNumeroStats` during high-volume concurrent bets blocks connections. | <span style="color:orange">**HIGH**</span> | Migrate real-time quota validation to Redis atomic operations. |
| **R-04** | Security | Hardcoded HMAC secret key in Rust validator. | <span style="color:orange">**HIGH**</span> | Inject secret key through environmental variable bindings on runtime. |
| **R-05** | Infrastructure| WSGI server (Gunicorn) is used in production instead of ASGI (Daphne), breaking Websockets. | <span style="color:orange">**HIGH**</span> | Update production Dockerfile CMD to use Daphne/Uvicorn. |
| **R-06** | Financial | Retroactive commission adjustments alter agent historical balance stats. | <span style="color:orange">**HIGH**</span> | Enforce ledger-based balance calculation as the single source of truth. |
