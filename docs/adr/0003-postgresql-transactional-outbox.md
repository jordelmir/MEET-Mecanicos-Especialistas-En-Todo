# ADR-003: PostgreSQL Transactional Outbox Pattern

## Status: ACCEPTED
## Context:
Domain changes must reliably produce distributed events without dual-write inconsistencies.
## Decision:
Implement `elysium_event_outbox` table updated in the exact same transaction as domain data.
