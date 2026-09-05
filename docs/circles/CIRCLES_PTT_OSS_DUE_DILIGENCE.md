# ELYSIUM CIRCLES & VANGUARD PTT — OPEN SOURCE DUE DILIGENCE
## Third-Party Component Audit & Legal/Security Clean-Room Analysis

```
Document ID: OSS-CIRCLES-PTT-001
Audited Date: 2026-09-05
Classification Standard: Apache-2.0 / MIT / BSD Allowlist. Strict GPL Exclusion.
```

---

## 1. Third-Party Stack Due Diligence Matrix

| Component | Canonical Upstream | License | Classification | Role in Elysium Architecture | Decision & Rationale |
|---|---|---|---|---|---|
| **LiveKit Android SDK** | `livekit/client-sdk-android` | Apache-2.0 | `PRODUCTION_DEPENDENCY` | Realtime WebRTC audio SFU transport, client-side room connection, dynamic track publication. | **APPROVED (P0)**: Already integrated in `ElysiumCallTransport`. Zero licensing conflicts. |
| **LiveKit Server** | `livekit/livekit` | Apache-2.0 | `INFRASTRUCTURE` | WebRTC SFU server managing room participants, audio tracks, and server-side dynamic permission enforcement (`canPublish`). | **APPROVED (P0)**: Core media transport. Tokens minted exclusively by backend. |
| **Supabase Realtime** | `supabase/realtime` | Apache-2.0 | `INFRASTRUCTURE` | Private channels with RLS for presence event propagation and low-latency broadcast. | **APPROVED (P0)**: Private channels + `AuthorizationEpoch` to protect against cached socket sessions. |
| **PostGIS** | `postgis/postgis` | GPL-2.0+ (Server-side) | `INFRASTRUCTURE` | Geospatial spatial indexes (`GIST`), `ST_DWithin`, spatial queries for Places and SafeJourney corridors. | **APPROVED (P0)**: Server-side database extension only; zero client linkage, clean-room GPL boundary maintained. |
| **MapLibre Native** | `maplibre/maplibre-native` | BSD-2-Clause | `PRODUCTION_DEPENDENCY` | Cross-platform vector map rendering, tile management, custom avatars, polyline rendering. | **APPROVED (Conditional)**: Use existing map stack; do not replace working map implementation without cause. |
| **Uber H3 (H3-Java)** | `uber/h3-java` | Apache-2.0 | `PRODUCTION_DEPENDENCY` | Discrete global hexagonal hierarchical spatial index for spatial clustering and coarse approximate regions. | **APPROVED (P1)**: Used as privacy projection helper for approximate regions. Never treated as exact location truth. |
| **Valhalla** | `valhalla/valhalla` | MIT | `INFRASTRUCTURE` | Multimodal routing engine, map-matching, corridor evaluation, ETA calculations. | **APPROVED (P1 Candidate)**: High-performance routing backend; evaluated against existing Ride routing. |
| **coturn** | `coturn/coturn` | BSD-3-Clause | `INFRASTRUCTURE` | TURN/STUN relay server for symmetric NAT traversal and fallback connectivity. | **APPROVED (Conditional)**: Supplementary relay infrastructure if LiveKit built-in TURN requires dedicated nodes. |
| **Toxiproxy** | `Shopify/toxiproxy` | MIT | `TEST_TOOL` | Network chaos injection (latency, jitter, packet loss, socket cutting) for automated resilience testing. | **APPROVED (P0 for Testing)**: Industry-standard resilience tool for network degradation testing. |
| **OpenMLS / RFC 9420** | `openmls/openmls` | Apache-2.0 / MIT | `R&D` | Messaging Layer Security (MLS) for decentralized group key management and forward secrecy in private circles. | **APPROVED (R&D Only)**: Retained for future post-quantum / private circle E2EE research. No unreviewed custom crypto. |
| **3GPP TS 24.380 (MCPTT)** | 3GPP / ETSI | Standard / Specification | `REFERENCE_ONLY` | Floor arbitration, preemption, and queue state machine conceptual reference. | **APPROVED (Reference Oracle)**: Conceptual oracle only; do NOT market as 3GPP-certified. |

---

## 2. Strict Licensing & Supply Chain Compliance

1. **GPL Isolation**: No GPL-licensed code is compiled or linked into the client Android APK or backend services. PostGIS is utilized strictly through standard PostgreSQL driver boundaries.
2. **Deterministic Versioning**: All production dependencies are pinned with exact versions and verified checksums in Gradle version catalogs.
3. **No Unaudited Cryptography**: Cryptographic operations strictly utilize platform keystores (`AndroidKeyStore`, AES-256-GCM) or standard TLS/WebRTC implementations.
