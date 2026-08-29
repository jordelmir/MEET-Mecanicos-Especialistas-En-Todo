# Current Capability Baseline — MEET / Elysium Vanguard

**Audit Reference:** Baseline `d1cecb50ef06ab178b4bdc42cc44a186e48baed6`  
**Current Main:** Synchronized with V6 Vehicle Systems Convergence  
**Status:** `SOFTWARE_GATES_GREEN` | `EDGE_LOCAL_AUTHORITATIVE`

---

## 1. Subsystem Capability Inventory & Classification

| Subsystem | Scope | Classification | Local Authority | Global Authority | Truth Doctrine Status |
|---|---|---|---|---|---|
| **OBD & CAN Acquisition** | ELM327, STN, USB Serial, CandleLight CAN, BLE, BT Classic, WiFi | `SOFTWARE_VERIFIED` | `TransportFabricV2`, `FormulaEvaluator`, `AdaptiveProtocolNegotiatorV2` | N/A (Edge Physical Only) | `NO_SYNTHETIC_SUCCESS`, `PID_DECODE_TYPED` |
| **Diagnostic Truth & PIDs** | Standard & OEM Extended PIDs, Mode 06, Readiness, DTC Scan | `SOFTWARE_VERIFIED` | `DtcScanEngine`, `Mode06Parser`, `PidRegistry`, `VehicleCapabilityPackV2` | N/A (Edge Physical Only) | `UNKNOWN != ZERO`, `INVALID != ZERO`, `MISSING != ZERO` |
| **Digital Twin Engine** | Temporal anomaly episodes, regime estimation, drift tracking | `SOFTWARE_VERIFIED` | `VehicleTwinEngine` (`TwinTruthState`), `PredictiveRegimeEngineV2` | N/A (Edge Derived Only) | `NO_BASELINE != 100% HEALTH`, `EPISODE_DEDUPLICATED` |
| **Telemetry Storage** | High-rate compressed binary framing, Merkle verification | `SOFTWARE_VERIFIED` | `CompressedTelemetryStorageEngineV2`, `DataLogger` | Elysium Cloud (Async Export) | `NO_VIN_IN_FILENAMES`, `MERKLE_ROOT_VERIFIED` |
| **Evidence & Passport** | Manifest signer, P-256 hardware Keystore, ML-DSA post-quantum envelope | `SOFTWARE_VERIFIED` | `DiagnosticManifestSigner`, `VehicleEvidencePassportV2` | Supabase / Elysium Server | `CRYPTO_AGILE`, `ATTESTED_SIGNATURE` |
| **Bus Lab & Instrumentation** | CAN-FD/DBC decoding, Scope (Hantek), Thermal UVC, Acoustic FFT | `SOFTWARE_VERIFIED` | `CanBusFrameAnalyzer`, `InstrumentationLabV2` | N/A (Edge Instrumentation) | `DERIVED_ADVISORY`, `NOT_PHYSICALLY_CONFIRMED_ALONE` |
| **Simulation & Sandbox** | Bus/ECU fault injector, signed WASM runtime | `SOFTWARE_VERIFIED` | `VirtualVehicleBusSimulator`, `WasmDiagnosticExtensionRuntime` | N/A (Deterministic Sandbox) | `SIMULATED != OBSERVED`, `NO_ECU_WRITES_IN_EXTENSIONS` |
| **Identity & Trust Center** | Multi-role platform access, MFA, Owner queue | `SOFTWARE_VERIFIED` | `ActivePrincipalKernel` (Local Projection) | Supabase Auth + RLS RPCs | `CLIENT_STATE != SERVER_AUTHORITY` |
| **Ride OS** | Dispatch, offers, lifecycle, location presence, metered fares | `SOFTWARE_VERIFIED` | Room `ride_*` tables + Outbox | Supabase RPC + Realtime Gateway | `OPTIMISTIC_CONCURRENCY`, `STALE_GPS_REJECTED` |
| **Communications** | Encrypted peer messages, delivery receipts, typing, presence | `SOFTWARE_VERIFIED` | Room `communication_*` tables | Supabase + Elysium Server ERP/1 | `DELIVERED_REQUIRES_RECIPIENT_ACK` |
| **Market OS & Commerce** | RFQ, reverse auction, quotes, parts compatibility, validation | `SOFTWARE_VERIFIED` | Room `market_*` tables | Supabase RPC + Outbox | `ATOMIC_BID_ACCEPTANCE`, `NO_DTC_SOLO_PURCHASE` |
| **Legal Vanguard** | AI triage, reverse auction, conflict check, disclosure levels | `SOFTWARE_VERIFIED` | Room `legal_*` tables | Supabase RPC + AI Gateway V2 | `BOUNDED_AI_HYPOTHESIS`, `AUTHORITATIVE_ESCROW` |
| **Offline Sync & Storage** | WorkManager outbox, retry backoff, dead-letter quarantine | `SOFTWARE_VERIFIED` | `SyncWorker` (`SyncBatchResult`), Room `sync_dead_letters` | Elysium Server Outbox | `NO_FALSE_SUCCESS_ON_RETRYABLE` |
| **Observability** | OpenTelemetry spans, structured JSON events, redaction | `SOFTWARE_VERIFIED` | `MeetTelemetry` | OTLP Collector | `NO_PII_LOGGING`, `TRACED_CORRELATION` |

---

## 2. Supreme Invariant Validation

1. **Cloud Loss Independence**: If internet connection drops completely, OBD scanning, DTC reading, freeze frames, local sensor graphing, local evidence capture, and Room persist 100% unimpeded.
2. **Deterministic Verification**: Every critical transition is backed by typed states (`RemoteResult`, `SyncItemResult`, `PidDecodeResult`, `TwinTruthState`).
3. **No Synthetic Truth**: Missing, unread, or invalid signals never default to `0f`, `100% health`, or `NO_DTC`.
