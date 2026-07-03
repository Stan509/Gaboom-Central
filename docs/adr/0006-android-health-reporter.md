# ADR-006: Android Health Reporter Preparation

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Diagnosing terminal issues and verifying sync latency in production.

## Decision
We define the device-side telemetry collected by `HealthMonitor`:
- **System Metrics:** APK version, battery percentage, free storage space, available memory, CPU load estimation.
- **Hardware Metrics:** Bluetooth connection state, print buffer queue size.
- **Sync Metrics:** Network type (WiFi, Cellular, Offline), last synchronization timestamp.

## Privacy & Safety
- Exclude all user personal identifiers, GPS coordinates (unless explicitly authorized for location stats), or ticket values.
- Metrics are compiled and sent only as lightweight headers or compressed periodic packets.
