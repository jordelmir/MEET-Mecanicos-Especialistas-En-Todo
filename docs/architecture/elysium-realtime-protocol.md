# Elysium Realtime Protocol (ERP/1) Specification

## 1. Protocol Overview
ERP/1 is a bidirectional, multiplexed protocol over WebSockets (`wss://realtime.<domain>/v1` or `/v1/realtime`).

## 2. Envelope Schema
```json
{
  "protocolVersion": 1,
  "eventId": "UUID",
  "eventType": "ride.state.changed",
  "eventClass": "DURABLE_DOMAIN",
  "occurredAt": "2026-08-29T00:00:00Z",
  "aggregateType": "RIDE",
  "aggregateId": "UUID",
  "aggregateVersion": 27,
  "streamSequence": 981,
  "correlationId": "UUID",
  "causationId": "UUID",
  "traceId": "HEX",
  "payloadVersion": 1,
  "payload": {}
}
```

## 3. Control Frames
- `HELLO` / `WELCOME` / `AUTH_REFRESH`
- `SUBSCRIBE` / `SUBSCRIBED` / `UNSUBSCRIBE`
- `EVENT`
- `COMMAND` / `COMMAND_ACK` / `COMMAND_RESULT`
- `RESUME` / `RESUMED`
- `PING` / `PONG`
- `ERROR` / `RATE_LIMITED` / `SERVER_DRAINING`
