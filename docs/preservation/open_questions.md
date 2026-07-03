# Open Questions (Phase 0)

This document lists key open questions regarding business rules and system architecture.

---

## 1. Questions Registry

### Q-01: Offline Ticket Time Manipulation
* **Question:** If a client goes offline, recules their device clock, sells a ticket, and syncs it after the draw closes, how should the backend detect and handle this fraud vector?
* **Options:**
  - Option A: Rejet strictly based on server closing times (if synced post-closure, reject).
  - Option B: Use server-validated network time sync checks in the APK.

### Q-02: Go Gateway WebSocket Integration
* **Question:** Is the Go WebSocket handler (`readPump`) intended to write directly to a Redis queue, or will the client application fall back to HTTP-only REST APIs in production?
* **Options:**
  - Option A: Implement robust Redis queuing to decouple writes.
  - Option B: Keep current REST framework as the primary pathway.

### Q-03: POS Printer Compatibilities
* **Question:** Should the print pipeline support generic ESC/POS serial byte protocols, or do we need native SDK bindings for Sunmi/Telpo devices?
  - *Current Status:* Handled via standard cascade pings in `BluetoothPrinter.kt`.
