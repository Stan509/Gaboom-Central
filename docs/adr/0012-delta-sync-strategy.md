# ADR-012: Delta Sync Strategy

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Bandwidth is expensive and connection speeds on POS terminals may be low. We must avoid transmitting unchanged config parameters.

## Decision
We implement a differential sync comparison check:
- POS device transmits local config `version`, `timestamp`, and local payload `hash`.
- The Go Gateway intercepts the packet and queries Redis. If the server config matches, the gateway responds with `304 Not Modified`, skipping JSON body payload parsing and transmission.

## Consequences
- Minimizes network utilization.
