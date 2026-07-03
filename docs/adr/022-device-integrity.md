# ADR-022: Device Integrity & Trust Scores

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Preventing compromised or emulator-based devices from executing tickets.

## Decision
The `DeviceSecurityManager` evaluate client states on a scale (`DeviceTrustScore`):
- **100:** Fully secure (no modifications, debugger off, signature match).
- **80:** Debug port open or developer tools enabled.
- **50:** Package modified or unrecognized integrity hashes.
- **UNTRUSTED:** Root access binaries detected (`su` executable pings) or debugger attached.

## Consequences
- Allows operators to progressively quarantine or alert suspicious devices.
