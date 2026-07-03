# ADR 0001: Strategy for Architecture Preservation and Safe Migration

* **Status:** Approved
* **Date:** 2026-07-01
* **Context:** Preparing the Gaboom Borlette OS for enterprise scaling. Need to prevent database corruption, API breakages, and functional regressions during rapid code evolution.

## Context and Problem Statement
The Gaboom Borlette OS platform spans Django, Go, Rust, and Android Kotlin, running on PostgreSQL, Redis, and SQLite. Moving to subsequent developmental phases introduces risk of:
1. Retroactive financial modifications in agent commissions.
2. Incompatibilities between updated REST/WebSocket endpoints and active offline/online clients.
3. Lock contention on PostgreSQL database tables due to concurrent high-volume transactions.

We require a standardized strategy to execute schema migrations, API edits, and code updates without affecting existing users or operational baselines.

## Decision
We adopt the following guidelines for all subsequent engineering work:
1. **Expand Before Replace:** Any schema migration or code updates must coexist with the legacy implementation. Deletion of legacy fields or pathways is prohibited until all clients are successfully migrated and verified.
2. **Centralized Feature Flags:** All new features must be hidden behind Feature Flags, disabled by default. Changes to flags must be persistable in database registries, cached in memory (Redis), and propagate dynamically without service restarts.
3. **No-Regression Testing Gate:** Establish measured baselines for API performance, transaction processing, and device synchronisation. Any change that exceeds the baseline latency by more than 10% under load will fail the quality gate.
4. **Git Enterprise workflow:** Direct commits to `main`, `release/*`, or `develop` are prohibited. Every modification must go through branch PRs with automated verification checks.

## Consequences
* **Pros:** Complete backward compatibility, zero regression, and robust fallback capabilities.
* **Cons:** Slower deprecation of old pathways, additional storage/routing overhead during migration states.
