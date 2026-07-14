# Task Plan: Fix Offline Ticket Synchronization

## Root Causes Identified

### Bug 1: Session Key Mismatch in Batch Sync (MAIN BUG)
- `SyncManager.buildCreateMultiRequest()` uses a single `sessionKey` (from first ticket) for ALL tirages in a batch
- Different tirages can have different session keys after rotation
- Server rejects with "Session expirée" when session key doesn't match
- **Fix**: Add `session_key` to `MultiTicketOverride` and pass per-ticket session key

### Bug 2: Override `session_key` Not Used By Server
- `TicketBatchService.create_tickets()` only checks top-level `body["session_key"]` against each draw
- Never reads per-override `session_key`
- **Fix**: Server should prefer override-level session_key when available

### Bug 3: Combined Batches Mix Tickets With Different Session Keys
- `chunkPendingTicketsIntoBatches()` creates "combined_" batches that may mix tickets from different session keys
- **Fix**: Group by session key per batch

## Changes Required

### Android App (Kotlin)
1. `Models.kt` - Add `sessionKey` to `MultiTicketOverride`
2. `SyncManager.kt` - Pass per-ticket sessionKey to override in `buildCreateMultiRequest()`

### Server (Python)
3. `TicketBatchService.py` - Read per-override `session_key` when checking draw session

### Build & Deploy
4. Bump version to 6.1.0
5. Rebuild APK
6. Update landing page version
7. Deploy APK to static/downloads/
8. Commit to GitHub

## Steps
- [x] Analyze sync flow and identify root causes
- [ ] Fix 1: Android - Add sessionKey to MultiTicketOverride (Models.kt)
- [ ] Fix 2: Android - Pass per-ticket sessionKey to override (SyncManager.kt)
- [ ] Fix 3: Server - Use per-override session_key (TicketBatchService.py)
- [ ] Bump version to 6.1.0 (build.gradle.kts)
- [ ] Build the APK
- [ ] Update landing page with new version
- [ ] Deploy APK to static/downloads/
- [ ] Commit and push to GitHub