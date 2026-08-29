# ADR-011: Deferral of NATS JetStream / Kafka

## Status: ACCEPTED
## Context:
Current event volume is efficiently served by PostgreSQL transactional outbox.
## Decision:
Defer introducing external message brokers (NATS/Kafka) until performance metrics prove a database bottleneck.
