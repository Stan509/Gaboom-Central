# Performance Architecture v1.0

This document defines the structural parameters of the scaling layer.

---

## 1. Data Path Options
```
[ Android Terminal ] ────► [ Go Gateway ] ────► [ Redis Queue ] ────► [ Workers ]
                                                                            │
                                                                            ▼
                                                                     [ Django Backend ]
                                                                            │
                                                                            ▼
                                                                     [ PostgreSQL ]
```

## 2. Key-Value Caching
- Config tables: Cached on Redis to prevent heavy PostgreSQL queries under concurrent pings.
- Locks: Distributed locks prevent multi-instance queue worker collisions.
