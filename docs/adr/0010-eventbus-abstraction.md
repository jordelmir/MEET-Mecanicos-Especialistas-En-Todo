# ADR-010: EventBus Port and Adapter Abstraction

## Status: ACCEPTED
## Context:
Event fabric begins with PostgreSQL Outbox but may evolve as throughput expands.
## Decision:
Define `EventBus` interface in application layer, decoupling domain logic from event transport.
