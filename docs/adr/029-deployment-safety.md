# ADR-029: Deployment Safety & Automation

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Prevent human error during version migrations.

## Decision
We enforce a pre-deployment verification check via a `DeploymentValidator`:
1. Validate database migration rollbacks.
2. Confirm Phase Feature Flags are disabled.
3. Check version compatibility maps.
4. Execute automated Postgres and configuration backup routines prior to applying updates.

## Consequences
- Insulates the production deployment pipeline against syntax or mismatch errors.
