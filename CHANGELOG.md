# Changelog

All notable changes to **Gaboom Central / Borlette OS** will be documented in this file.

## [2.4.0] - 2026-07-01
### Added
- **Phase 8.1 Database Migration:** Additive-only PostgreSQL/SQLite migrations introducing `SyncAttempt`, `SyncBatch`, `SyncConflict`, and `SyncAuditLog`. Added v2 tracking fields to `TicketIdentity` (`ticket_origin`, `validation_level`).
- **Phase 8.2 Safe Multi-Service Release:** Integrated background processors, cache locking mechanisms, and adaptive admission controllers inside Go Gateway and Django services.
- **Phase 8.3 Safe APK Rebuild:** Recompiled Android POS client target v2.4.0 (Build 24) with backward-compatible Room structures and conditional JNI/rust bindings.

### Changed
- Bumped Android POS version to `2.4.0` (Build `24`).
- Updated landing page download target links to `app-debug.apk?v=2.4.0`.

### Security
- Cryptographic traits compiled into Rust and JNI configurations (Inactive behind feature flags).

### Compatibility
- 100% backward compatible. All feature flags remain set to `FALSE` (disabled).
