# Rollback Strategy (Phase 0)

This document outlines the recovery protocols in the event of an deployment failure or database corruption.

---

## 1. Automated Database Rollback
If a Django database migration fails to run completely during deployment:
- The pipeline will trigger `python manage.py migrate <app_name> <previous_migration_name>`.
- The migration must cleanly restore column names and states without destroying user records.
- In production, if automatic rollback fails, the database will be restored from the pre-deployment pg_dump snapshot.

## 2. Container and Service Rollback
- **Go and Rust services:** Build metadata tags are pinned to SemVer releases (e.g. `gateway:v1.0.0`). In case of service failure, `docker-compose.yml` will be reverted to use the previous stable tags, followed by `docker-compose up -d --force-recreate`.
- **Nginx & Ingress:** Maintain a copy of the previous active Nginx configuration files to immediately reload routing if a configuration change breaks active SSL/websocket proxying.

## 3. Verification Criteria after Rollback
Following a rollback, developers must verify:
- Django admin portal login is functional.
- APK clients can synchronize tickets successfully.
- Active draws stats (`TirageNumeroStats` / `TirageCombiStats`) match pre-deployment counts.
