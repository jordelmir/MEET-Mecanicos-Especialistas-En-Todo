# ADR-005: Single Application-Level WebSocket Multiplexing

## Status: ACCEPTED
## Context:
Multiple ViewModels opening multiple independent sockets exhausts client resources and server connections.
## Decision:
Maintain a single application-scoped connection multiplexing channel subscriptions.
