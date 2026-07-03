# Redis Inventory (Phase 0)

This document catalogs the Redis deployment within the Gaboom Borlette OS backend.

---

## 1. Role in Django Channels
- **Broker:** Redis is used as the backing channel layer (`channels_redis.core.RedisChannelLayer`) to route websocket notifications to connected clients.
- **WebSocket Routing Channels:**
  - `agent_{agent_id}`: Channels status updates.
  - `borlette_{borlette_id}`: Broadcast updates.
  - `tirage_{tirage_id}`: Real-time draws closure notifications.

## 2. Caching Layer Configuration
- **Backend:** `django.core.cache.backends.redis.RedisCache`.
- **Config:** Connects to `REDIS_URL` in container setup.
- **Active Keys:** Stores temporary session variables and Django rate limit counters.
- **Persistency Policy:** Redis is configured with no-eviction policy (or volatile-lru) in `docker-compose.yml` to preserve memory states.
