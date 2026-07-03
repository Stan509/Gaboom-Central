# Go Inventory (Phase 0)

This document catalogs the Go Gateway service (`gateway_go`).

---

## 1. Routing and Concurrency
- **HTTP Server:** Starts standard net/http listener on port `8080`.
- **WebSocket Protocol:** Upgrades HTTP connection using `github.com/gorilla/websocket`.
- **WebSocket Hub:** `ws.Hub` runs a central concurrent event loop registering clients and managing incoming/outgoing websocket streams via channels.

## 2. Integrated Clients
- **gRPC Client:** Communicates with Rust Validator (`google.golang.org/grpc`) on port `50051`.
- **Redis Client:** Connects to cache server using `github.com/redis/go-redis/v9`.
- **Django HTTP Forwarder:** Dynamic reverse proxy client forwarding payloads to Django port `8000`.

## 3. Discovered Vulnerabilities
- The `/api/agent/ticket/create_multi` forwarder omits header fields `X-DEVICE-ID` and `X-PAYLOAD-SIGN`, causing security checks bypass.
- The WebSocket handler (`readPump`) does not persist websocket ticket messages to Redis and discards them. Corrective reconnections are scheduled for Phase 1.
