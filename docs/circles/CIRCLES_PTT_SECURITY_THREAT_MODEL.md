# ELYSIUM CIRCLES & VANGUARD PTT — SECURITY THREAT MODEL & ABUSE SAFETY
## STRIDE Analysis, Anti-Stalking Protections, and Coercive Control Defense

```
Document ID: SEC-CIRCLES-PTT-001
Status: CANONICAL THREAT MODEL
Target: Elysium Circles & Vanguard PTT Subsystems
Core Doctrine: PRODUCT SAFETY IS ARCHITECTURE, NOT A TERMS OF SERVICE PARAGRAPH.
```

---

## 1. STRIDE Threat Model Matrix

| Threat Category | Target Subsystem | Attack Vector | Security Mitigations & Architectural Controls |
|---|---|---|---|
| **Spoofing** | Presence Ingestion | Fake GPS coordinates injected via mocked provider or modified OS. | Dual-source verification (`clockQuality`, `monotonicDelta`), speed limit checks (Mach-1 rejection), and `SOURCE_UNTRUSTED` classification. |
| **Spoofing** | LiveKit SFU | Modified APK pretends to have valid FloorLease to publish audio. | Server-side `canPublish` gating: LiveKit server only allows audio track publish when authorized by backend `FloorAuthority`. |
| **Tampering** | Location Share Grants | Malicious Circle admin modifies database to broaden member precision. | Strict RLS: `LocationShareGrant` has `CHECK (auth.uid() = owner_principal_id)`. Admins have ZERO update authority on member grants. |
| **Tampering** | PTT Transmissions | Stale floor holder injects late packets after lease expiry. | Monotonic `fencingToken` per channel: server rejects any transmit/release command where `token < currentChannelEpoch`. |
| **Repudiation** | Safe Journey Check-ins | User claims they never checked in or false emergency declared. | Signed check-in records with monotonic timestamps, `EntityRef.EvidenceRef`, and immutable SHA-256 hash. |
| **Information Disclosure** | Approximate Projection | Attacker intercepts network traffic to extract exact coordinates. | Server-side projection: coordinates are snapped to coarse spatial centroid BEFORE transmission. Exact coordinates NEVER leave backend. |
| **Information Disclosure** | Realtime Broadcast | Revoked member maintains open WebSocket connection to capture updates. | `AuthorizationEpoch`: Revocation increments `accessEpoch`, shifting all future events to a new private topic inaccessible to old socket. |
| **Denial of Service** | Floor Arbitration | Flooding floor requests or holding button down indefinitely. | Rate limiting on floor requests, strict lease timeout (`FLOOR_GRANT_DURATION_MS = 30s`), and automatic silence timeout (10s). |
| **Elevation of Privilege** | PTT Floor Control | User attempts priority preemption using manipulated client state. | Semantic priorities (`NORMAL`, `IMPORTANT`, `EMERGENCY`) evaluated server-side. Preemption requires verified role/emergency context. |

---

## 2. Abuse Safety & Anti-Stalking Defense

### A. Coercive Control & Location Pressure
- **Self-Authority**: Only the individual location owner can create, expand, unpause, or extend a `LocationShareGrant`. A Circle admin can only send an invite or request; they have zero UI or API capability to enable tracking remotely.
- **No Stealth Tracking**: Whenever location is shared, Android requires a persistent, visible foreground service notification. Elysium displays prominent in-app status indicators showing exactly who can see the location.
- **"Pause All" Immediate Kill-Switch**: The user can instantaneously pause all sharing across all circles with a single button press.

### B. Remote Microphone & Eavesdropping Prevention
- **Absolute Microphone Boundary**: Remote principals have ZERO ability to activate a microphone on another user's device, even during an emergency.
- **Audio Delivery Truth**: `FLOOR_GRANTED != AUDIO_DELIVERED` and `SERVER_ACCEPTED != HUMAN_HEARD`. The UI never implies a human heard audio without explicit playback attestation.

### C. Anti-Averaging Privacy Defense
- Approximate location is calculated using fixed geographic bins (e.g. 0.01° grid snap) rather than Gaussian jitter. Repeated updates at the same stationary location produce identical coordinates, preventing averaging attacks from reconstructing the true physical position.
