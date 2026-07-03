# ADR-007: Offline Data Model

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Local Room database tables layout to execute transaction cache, offline sales logs, configuration updates, and clock snapshot records safely on Android.

## Decision
We define the following database structures within the APK local Room database schema:
1. `TicketLocal`: Local copies of tickets containing UUID, draw key, ticket status, sequence number, and cryptographically verified payload hashes.
2. `TransactionLocal`: Monotonic log of device actions. Includes `recovery_id` to reconstruct incomplete states in case of app crash.
3. `ConfigLocal`: Local variables representing active limits and draw prices.
4. `ClockSnapshot`: Cached signed authority timestamps and offset values.
5. `OfflineSession`: Context data to track device connection uptime (`start_time`, `last_sync`, `device_state`, `clock_confidence`).

## Consequences
- Guarantees data durability on the device even during terminal power loss or runtime crashes.
