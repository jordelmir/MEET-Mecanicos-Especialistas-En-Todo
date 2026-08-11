# MEET 4.17 Vehicle Truth Operating System

Baseline: `main` at `6909569c9adf47e1e5f9aaae2553f48f4a8cc561` (MEET 4.16.0, Room 52).

This document is the implementation ledger for the 33-phase audit. `IMPLEMENTED` means source work is
present on the feature branch; it does not mean compiled or lab-certified. `EXTERNAL GATE` means the code
intentionally refuses to manufacture evidence that only a device, vehicle, reviewed dataset or protected
release environment can provide.

| Phase | State | Delivered authority boundary |
|---:|---|---|
| 0 | IMPLEMENTED | Production Vanguard no-ops removed; polling, quality, metrics, failure intelligence, recorder and outbox have real state transitions; build guard rejects stub markers. |
| 1 | IMPLEMENTED | `VehicleSessionBinding` owns UNBOUND/VIN_VERIFIED/USER_CONFIRMED/CONFLICTED and blocks persistence/active operations outside a valid binding. |
| 2 | IMPLEMENTED | Latest scan projection is keyed by binding and selected vehicle; projection is cleared on vehicle changes. |
| 3 | IMPLEMENTED | ECU finding constructors are restricted and creation is routed through `DiagnosticFindingFactory`; production guard rejects direct construction. |
| 4 | TRANSITIONAL | Canonical findings are authoritative in persistence/DTC UI; legacy presentation APIs still expose `List<String>`. AI context marks codes presentation-only. Remaining consumers are listed below and may not gain authority. |
| 5 | IMPLEMENTED | Fabricated repair minutes/difficulty/success percentages removed; estimates require `EvidenceMetric`; CTA is verification workflow. |
| 6 | IMPLEMENTED | Typed `TelemetrySample` prevents null-as-zero physical conclusions; missing RPM cannot become MOTOR OFF. |
| 7 | IMPLEMENTED | Signed, expiring capability packs are canonicalized and checked per vehicle/ECU/hardware/software/calibration/signal; default deny. |
| 8 | IMPLEMENTED | Active-test state machine rechecks freshness and prerequisites, sends STOP across cancellation paths, and records durable evidence. |
| 9 | IMPLEMENTED | Strict read-only terminal grammar with exclusive transaction ownership and adapter restoration verification. |
| 10 | IMPLEMENTED | Snapshot/exchange references normalized relationally; Room 53 migration and FK indexes. |
| 11 | IMPLEMENTED | Scan manifests have canonical signatures and hardware-protected key requirement; cross-session roots are rejected. |
| 12 | TRANSITIONAL | EVP2 AES-GCM protects compiled evidence packages with Keystore custody, AAD, rotation policy and plaintext cleanup. Raw exchange rows still require migration to encrypted blob storage before this phase can be closed. |
| 13 | IMPLEMENTED | Structured privacy gateway defaults disabled, HMAC pseudonymizes identifiers, drops location/personal/secret fields and distinguishes consent modes. |
| 14 | TRANSITIONAL, NOT EXECUTED | Explicit 50→52 instrumentation tests preserve duplicate observations, raw UDS identity/ECU separation and check foreign keys. Remaining malicious fixtures and Room 53–55 schema export await the authorized compile gate. |
| 15 | EXTERNAL GATE | Versioned hardware-lab manifest/schema and fail-closed verifier added. No physical conformance is claimed without real reviewed captures. |
| 16 | EXTERNAL GATE | Golden-trace corpus structure exists; promotion requires immutable hashes and review. Corpus is intentionally empty. |
| 17 | TRANSITIONAL, NOT EXECUTED | Bounded coverage-guided entrypoint covers PDU, CAN multi-frame, Mode 06, SAE/UDS DTC and freeze-frame identity. Raw DoIP envelope targeting and physical golden seeds remain pending; runner requires a pinned external Jazzer binary. |
| 18 | IMPLEMENTED | Strict `vehicleConstraints` evaluator supports identity/ECU/hardware/software/calibration, preserves UNKNOWN, and rejects contradictions. |
| 19 | IMPLEMENTED | Directional typed causal grammar replaces unconstrained BFS; invalid transitions and conflicted edges are not explored. |
| 20 | IMPLEMENTED | Knowledge lookup starts from namespace/raw identity/failure type; display-code lookup is explicit generic fallback. |
| 21 | IMPLEMENTED | Per-field VIN/Garage/OBD/OEM/user/history fusion preserves evidence sets and exposes conflicts instead of overwriting. |
| 22 | IMPLEMENTED | Diagnostic Twin V2 projects OBSERVED/RELATED/UNTESTED/VERIFIED_OK/ANOMALOUS/NOT_APPLICABLE/UNKNOWN; anomalous coloring requires evidence. |
| 23 | IMPLEMENTED | Guided diagnosis uses decision-theory only with reviewed calibration and tool availability; otherwise it labels heuristic priority. |
| 24 | EXTERNAL GATE | Calibration lab computes Brier, ECE, top-k, macro metrics, Wilson intervals and stratification; percentages remain unpublished below reviewed holdout gates. |
| 25 | IMPLEMENTED | Repair verification closes the loop with pre/post identity, test evidence and recurrence rules; no button alone declares success. |
| 26 | IMPLEMENTED | Append-only Vehicle Evidence Graph binds nodes/edges/evidence to one vehicle and one binding. |
| 27 | IMPLEMENTED | AI policy allows bounded explanation/summarization and rejects creation of DTC/failure/repair/active-operation authority. |
| 28 | EXTERNAL GATE | No risky `ObdSession` rewrite is performed before golden traces exist. New scheduler/recorder/terminal/safety services establish extraction seams. |
| 29 | EXTERNAL GATE | No risky `ObdViewModel` rewrite is performed before golden traces and UI parity evidence. Binding and projection ownership have been extracted first. |
| 30 | TRANSITIONAL, NOT EXECUTED | CI builds APK+AAB, validates signed AAB container, hashes artifacts and enforces reviewed distribution budgets. Play Asset Delivery/dynamic feature extraction remains pending because it requires measured installation compatibility and cannot delete product fidelity. |
| 31 | EXTERNAL GATE | Tiered scenario budgets and measurement contract exist; no benchmark number is invented. Macrobenchmark execution awaits lab/CI authorization. |
| 32 | IMPLEMENTED, EXTERNAL RELEASE GATE | Pinned actions, dependency review, secret scans and artifact manifest are wired; protected signing, resolved SBOM and provenance attestation remain release-environment gates. |

## Remaining canonical-finding migration

Legacy string lists are retained only as presentation/compatibility projections while these surfaces are migrated
incrementally: LiveLink payloads, rule/copilot engines, parts and service suggestions, legacy 3D scene builders,
pre-purchase/smog helpers, mechanic request summaries, HUD and fleet messages. They must not create ECU evidence,
declare a part failed, verify a repair or authorize an active operation. The production guard protects the highest-risk
constructors now; the migration is not misrepresented as complete.

## Release gates before merge

No build or test was run during this implementation round, following the owner's standing instruction. Before merge:

1. generate and review Room schemas 53, 54 and 55 with the authorized Android build;
2. run unit, instrumentation migration, lint, parity and production guards;
3. build APK and AAB, verify artifact manifests and scan both binaries;
4. install and launch on the real Android device, then inspect crash logs;
5. obtain real hardware fixtures before enabling conformance-dependent refactors or calibrated percentages.

The physical end-to-end truth test remains the final acceptance gate: same vehicle, binding, finding identity, ECU
and evidence chain from connect through verified post-repair scan, with no invented claim and no false resolution.
