# ADR-017: Cache Strategy & Performance SLOs

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Need to establish quantitative performance budgets to monitor degradations under load.

## Caching Strategy
- **Cachable Data:** Lottery configuration schemas, active draw rates, agent access tokens.
- **TTL Bounds:** Config tables (1 hour), token records (15 minutes).
- **Fallback Cascade:**
  $$\text{Redis Lookup} \longrightarrow \text{If Miss} \longrightarrow \text{PostgreSQL Query} \longrightarrow \text{Populate Cache}$$

## Performance Budget / SLO (v1.0)
The following limits define the monitoring alerting boundaries:
1. **API Latency:** Target average $< 100\text{ ms}$. WARNING threshold $> 150\text{ ms}$, CRITICAL threshold $> 250\text{ ms}$.
2. **Queue size:** Target $< 5,000$ pending batches. WARNING threshold $> 8,000$, CRITICAL threshold $> 10,000$.
3. **Sync batch duration:** Target $< 5\text{ seconds}$ per batch. WARNING threshold $> 10\text{ seconds}$.
4. **Database Query execution:** Target $< 15\text{ ms}$. WARNING threshold $> 30\text{ ms}$.

## Consequences
- Establishes a predictable baseline to alert developers of scalability issues.
