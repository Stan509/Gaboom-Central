# Business Rules: Offline Selling v1.0

This document defines the business logic rules governing disconnected POS operations for Gaboom Borlette OS.

---

## 1. Disconnection Durations
- A terminal is permitted to sell tickets offline for a maximum configurable duration of **25 minutes**.
- If the device does not check-in with the Go Gateway within this window, the ticketing engine enters **Read-Only Mode** until automatic or manual sync success verifies integrity.

## 2. Payout and Verification Rules
- Offline tickets cannot be paid out until they have successfully transitioned to the `CONFIRMED` state in the Django central database.
- A grace period of 5 minutes is granted for pending queues to synchronize once connection is re-established.

## 3. Conflict Arbitration
- The server decision always overrides client claims.
- Pricing or session configuration changes that occurred during offline selling are flagged to the backoffice administrator for reconciliation.
