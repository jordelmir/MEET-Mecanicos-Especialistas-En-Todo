# Master Authority Map — MEET Edge & Elysium Cloud

**Governing Law:** ONE DOMAIN, ONE AUTHORITY. MANY PROJECTIONS, MANY TRANSPORTS, MANY CLIENTS.

---

## 1. Authority Separation Matrix

```
┌───────────────────────────────────────────────┬───────────────────────────────────────────────┐
│                   MEET EDGE                   │                 ELYSIUM CLOUD                 │
│         (Immediate Local Authority)           │          (Global Canonical Authority)         │
├───────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ • Physical Vehicle Protocol & Bus Timing      │ • User Identity & Master Profile Claims       │
│ • OBD-II / UDS / KWP PID Decodes              │ • Multi-Tenant Roles & Verification Tiers     │
│ • Local DTC Scan & Freeze Frames              │ • Platform Trust Center & Owner Decisions     │
│ • Physical Telemetry Ring Buffers             │ • Ride Dispatch, Fares & Escrow Settlement    │
│ • Realtime Anomaly Episode Detection          │ • Communications Event Ordering & Audit       │
│ • Oscilloscope Waveforms & Acoustic DSP       │ • Work Order State Machines & Approvals       │
│ • Local Drafts & Outbox Queues                │ • Market Reverse Auction Bid Acceptance       │
│ • Hardware Key Signatures (Android Keystore)  │ • Multi-Device Realtime ERP/1 Fan-out         │
│ • Local Diagnostic Terminal                   │ • Long-term Merkle Proof Ledger               │
│ • Certified Offline Reports                   │ • Media Plane Signaling (LiveKit WebRTC)      │
└───────────────────────────────────────────────┴───────────────────────────────────────────────┘
```

---

## 2. Command & Event Causal Flow

```
PHYSICAL EVENT / USER INPUT
           │
           ▼
       MEET EDGE
           │
       TRUTH GATE (PidDecodeResult / TwinTruthState / etc.)
           │
     LOCAL ROOM DB (Working Copy / Local Projection / Outbox)
           │
     CONTROLLED SYNC (SyncWorker with Backoff / REST / ERP/1)
           │
     ELYSIUM SERVER (Authentication & Authorization Validation)
           │
   POSTGRES DATA PLANE (ACID Transaction + Domain State + Outbox)
           │
   TRANSACTIONAL OUTBOX (`elysium_event_outbox`)
           │
   REALTIME GATEWAY (ERP/1 Multiplexed WebSocket)
           │
  AUTHORIZED PROJECTIONS (Web, Other Phones, Workshop Kanban, Fleet Dashboards)
```
