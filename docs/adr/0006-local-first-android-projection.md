# ADR-006: Local-First Android Room Projection

## Status: ACCEPTED
## Context:
Mechanics and drivers operate in low or zero connectivity areas.
## Decision:
UI always reads from local Room database. Realtime and background sync update Room projections.
