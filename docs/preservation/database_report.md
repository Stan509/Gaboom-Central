# Database Report (Phase 0)

This report provides a structural review of the PostgreSQL database schema for the Gaboom Borlette OS backend.

---

## 1. Schema Analysis
The system uses the following critical database tables:
- `accounts_user`: Core agent/admin identity mapping.
- `accounts_borlette`: Master settings of the borlette (offline permission, free marriage amount).
- `accounts_agent`: Financial cache and location updates.
- `accounts_tirage`: Draw definition and active session key mappings.
- `accounts_tiragenumerostats`: Real-time boule quotas.
- `accounts_tiragecombistats`: Real-time marriage and loto quotas.
- `agent_portal_ticket` & `agent_portal_ticketline`: Transaction records.

## 2. Constraints and Indexes
- **Unique Constraints:** `uniq_tirage_numero` on table `accounts_tiragenumerostats` and `uniq_tirage_combi` on `accounts_tiragecombistats`.
- **Database Index Coverage:**
  - `idx_tline_ticket_jeu` on `agent_portal_ticketline(ticket_id, jeu)`.
  - `idx_ticket_agent_dt` on `agent_portal_ticket(agent_id, created_at)`.
  - `idx_ledger_agent_dt` on `agent_portal_agentledgerentry(agent_id, created_at)`.

## 3. Database Locking and Transaction Behaviors
- The method `RiskManagementService.apply_bet` locks stats records via `select_for_update` to prevent race conditions during concurrently submitted tickets.
- Under high loads, these lock acquisitions must be closely monitored. Any modifications to transaction scopes should release locks as quickly as possible.
