# ADR-007: LiveKit for Media Plane Isolation

## Status: ACCEPTED
## Context:
Audio/video calling requires WebRTC, SFU, and TURN handling separate from business WebSocket eventing.
## Decision:
Isolate media plane onto dedicated LiveKit SFU instance; ERP/1 coordinates signaling only.
