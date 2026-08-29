# ADR-001: Modular Monolith Architecture for Elysium Server

## Status: ACCEPTED
## Context:
MEET and Elysium require unified backend capabilities for identity, rides, market, communications, and telemetry without premature distributed system complexity.
## Decision:
Build `elysium-server` as a modular monolith in Kotlin JVM / Ktor with strict boundary interfaces between domains.
## Consequences:
Simple single-process deployment, high developer velocity, zero microservice network latency, clear path to future extraction if required by measured load.
