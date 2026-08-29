# Eventing & Transactional Outbox Architecture

## 1. Outbox Pattern
All domain state transitions write to `elysium_event_outbox` in the same atomic database transaction as the domain entities.

## 2. Event Distribution
The outbox worker polls and streams events to the Realtime Gateway for authorized WebSocket fan-out, ensuring at-least-once delivery with idempotent consumer handling.
