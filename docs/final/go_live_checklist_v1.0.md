# Go-Live Checklist v1.0

Operational check items required for production deployment:

---

- `[x]` **Infrastructure:** Go gateway, Django core, standby replicas active.
- `[x]` **Security:** Device integrity checked, keys rotation ready.
- `[x]` **Database:** Additive migrations validated and rollback tested.
- `[x]` **Android:** App compiled under feature flag isolation.
- `[x]` **Monitoring:** AlertManager states and health score pings running.
- `[x]` **Backup:** Postgres backup cron verified.
- `[x]` **Rollback:** Verified database rollback path.
