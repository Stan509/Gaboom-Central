# Android Inventory (Phase 0)

This document catalogs the Kotlin Android application codebase architecture.

---

## 1. Clean Architecture Patterns
The mobile app uses:
- **UI Layer:** Jetpack Compose for declarative layout rendering.
- **State Management:** MVVM (Model-View-ViewModel). ViewModels expose immutable states via `MutableStateFlow` (e.g. `VenteViewModel.kt`).
- **Dependency Injection:** Hilt/Dagger (`@HiltViewModel`, `@Inject`).

## 2. Room Database Cache Strategy
- **`AgentDatabase` (`LocalDatabase.kt`):**
  - Table `pending_tickets`: Caches offline transactions (`PendingTicketEntity`) for sync.
  - Table `local_tickets`: Cache of previously created tickets.
  - Table `tirage_session_cache`: Cache mapping `tirageId` to its active `sessionKey`.

## 3. Synchronization Engine (`SyncManager.kt`)
- Monitors connection states using `NetworkMonitor`.
- Triggers ticket synchronization when network is restored.
- Uses exponential backoff (starting at 2s, max 30s) and retry policies.
- Computes HMAC-SHA256 signatures of ticket payloads using the device secret.

## 4. Printer Fallbacks (`BluetoothPrinter.kt`)
- Supports ESC/POS ticket printing.
- Implements cascade checks: Android Print Spooler $\rightarrow$ Direct USB Bulk Transfer $\rightarrow$ Raw Serial Writing to `/dev/ttyS*` $\rightarrow$ RFCOMM Bluetooth SPP connection.
