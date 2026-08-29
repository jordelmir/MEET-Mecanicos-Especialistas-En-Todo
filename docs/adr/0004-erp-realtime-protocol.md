# ADR-004: Elysium Realtime Protocol (ERP/1)

## Status: ACCEPTED
## Context:
Clients require bidirectional multiplexed event streams for rides, chats, workshop Kanbans, and telemetry.
## Decision:
Standardize on ERP/1 over WebSockets with explicit control frames, resumption tokens, and deduplication cursors.
