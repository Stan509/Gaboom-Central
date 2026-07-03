# Docker Inventory (Phase 0)

This document catalogs the containerized infrastructure of Gaboom Borlette OS.

---

## 1. Container Configuration Mappings
The `docker-compose.yml` config defines the following containers:
- **`gaboom_postgres` (Postgres 16 Alpine):** Exposes port `5432:5432`. Holds all relational data.
- **`gaboom_redis` (Redis 7 Alpine):** Exposes port `6379:6379`. Broker for Daphne ASGI routing and cache layers.
- **`gaboom_django` (Django 5.1):** Exposes port `8000:8000`. Runs Django development server (`runserver`) under Daphne.
- **`gaboom_gateway` (Go Gateway):** Exposes port `8080:8080`.
- **`gaboom_validator` (Rust Validator):** Exposes port `50051:50051` (internal gRPC).

## 2. Production Dockerfile
- **Base Image:** `python:3.12-slim`.
- **Exposed Port:** `8000`.
- **Production Server Command:** Runs Gunicorn WSGI server:
  `gunicorn centralborlette.wsgi:application --bind 0.0.0.0:8000 --workers 2 --timeout 120`
  *Note:* As identified in the technical debt audit, this WSGI server setup lacks support for Daphne ASGI websockets, which must be updated in Phase 1.
