# Diagnostic Safety, Causal & Conformance Kernel

Status: 4.16 build/migration/install evidence is verified; the 4.17 Vehicle Truth candidate is implemented in source but not executed in this round. Canonical machine-readable state: [`docs/generated/verification-status.json`](../generated/verification-status.json).

## Non-negotiable invariants

1. `DtcScanEngine` plus `DiagnosticFindingFactory` are the only production authority allowed to create an ECU-reported `DtcRecord`.
2. A positive, typed protocol response and raw payload are required before a DTC becomes a finding.
3. Mode 06 produces monitor observations, never inferred DTCs.
4. `diagnostic_findings` owns stable identity. `diagnostic_observations` owns time-varying truth. `diagnostic_exchanges` owns protocol evidence.
5. `dtc_events` is a compatibility/read projection, not diagnostic authority.
6. Absence in a scan is not repair. Resolution requires a `VERIFIED_RESOLVED` observation.
7. Active output/routine tests fail closed. Unknown or stale safety evidence is unsafe.
8. Once activation is requested, STOP runs in `NonCancellable`; an unverified STOP remains a critical state.
9. DoIP diagnostic traffic requires a logical target for each request. Generic targetless DoIP transmission is blocked.
10. Vehicle topology fields independently preserve `Known`, `Unknown` or `Conflicted` state and provenance.
11. DTC spatial/3D navigation requires a persisted `findingId`; textual query-string reconstruction is forbidden.
12. A graph relation is a candidate inspection path, not proof that a part failed.

## Acquisition boundary

The supported DTC entry point is `RunDiagnosticScan`, which delegates to the single application-facing `DiagnosticAcquisitionEngine`; it alone crosses the temporary compatibility boundary into `ObdSession.readProfessionalDtcScan`. Destructive memory operations cross the equivalent `DiagnosticMemoryEngine` boundary and still require a typed `ClearVerificationPlan`. Legacy string readers and the previous module scanner are compile-time deprecated with error severity and cannot issue their former traffic.

The topology screen now projects modules from the same canonical professional scan. The legacy topology routine—which changed UDS sessions, read DTC strings and inferred module authority independently—is compile-time retired.

SAE services `03`, `07` and `0A` and UDS service `19` are decoded only after service validation. UDS preserves the raw 24-bit identity and failure-type byte; the human-readable code is presentation, not the primary binary identity.

Manufacturer probing, guessed `0x31` routine control and `0x2F` input/output control are not discovery mechanisms. Without a reviewed capability pack, ECU address and safety contract, the operation is blocked before bus transmission.

The previous hard-coded active-command catalog now resolves no executable commands. Generic ECU reset, direct UDS clear, raw routine control and raw IO-control helpers fail closed. Capability discovery is limited to read-only identity evidence; a capability not proven by a reviewed pack remains unproven rather than being tested with a potentially state-changing request.

Service-reset UI never animates example TX/RX lines as though they were physical evidence. Until a reviewed capability pack is available for the selected vehicle and ECU, those actions remain visibly pending and transmit nothing.

The adaptations/coding catalog follows the same boundary: it contains educational procedure descriptions but no raw command sequences, and its execution control remains disabled until a vehicle-specific reviewed capability pack exists.

The expert terminal is read-only in production. `DiagnosticRawCommandPolicy` permits a reviewed subset of adapter configuration plus SAE current/freeze-frame/monitor/vehicle-information reads and UDS ReadDataByIdentifier. Direct DTC reads are redirected to the acquisition engine; clear, reset, session, security, communication-control, write, transfer, routine and IO-control services are blocked before transport.

Clear planning, post-clear resolution evidence and freeze-frame association read stable identity and timeline only from `diagnostic_findings` plus `diagnostic_observations`. The old `dtc_events` table may still receive synchronized presentation updates, but it cannot select targets, prove absence, authorize resolution or own snapshot identity.

## Active-test state machine

`IDLE → PRECHECK → READY → ACTIVATION_REQUESTED → ACTIVE → STOP_REQUESTED → STOP_VERIFIED`

Failure states are `ABORTED` and `STOP_FAILED`. Activation is never retried because a lost acknowledgement does not prove the actuator remained inactive. Safety telemetry must be real OBD data, valid and at most two seconds old. Transmission-in-P currently remains unverified unless an authoritative source is introduced; therefore any test requiring it is blocked.

A non-empty `capabilityPackId` never authenticates itself. `ActiveDiagnosticCapabilityRegistry` verifies ECDSA signature, content hash, expiry, revocation, vehicle/ECU/hardware/software/calibration applicability and per-operation safety requirements. The trust store defaults empty, so built-in generic definitions remain safely non-executable until a reviewed signed pack is loaded.

## Evidence and replay

Every new exchange records wall-clock and monotonic time, session sequence, request/response hashes, previous and current exchange hashes, parser/canonicalization version, retention class and expiry. Each scan batch also records a Merkle root.

`RecordedDiagnosticTransport` replays physically captured request/response chunks in strict sequence through the same production parser boundary. A byte mismatch, an unexpected request or a recorded transport fault fails explicitly; the replay layer does not synthesize responses, skip frames or retry commands.

Observations have an independent append-only hash chain. `DiagnosticEvidenceIntegrity` is the common byte-stable verifier for replay, export and future CI conformance fixtures. Legacy imported evidence is labeled as limited and is not silently upgraded to modern authority.

Expired raw exchanges are removed only when no observation or diagnostic snapshot references them. Linked forensic evidence remains preserved so retention cannot destroy the audit chain.

## Privacy

Remote diagnostic telemetry defaults to `DISABLED`. Settings expose explicit local-only, anonymous/redacted and consented-redacted modes. Vehicle/device identifiers use an app-scoped Keystore HMAC; location, personal and secret classes are dropped, while raw diagnostics require explicit consent and redaction. Local evidence retention and remote telemetry consent are separate policies.

## Causal and spatial projection

The navigation contract is:

`findingId → canonical finding/timeline → cited knowledge-graph edges → spatial system/path → candidate 3D component`

Each projected relation carries source references, graph review state, applicability, vehicle constraints and required confirmation evidence. The UI explicitly says that the projection does not confirm a damaged part. If there is no applicable structured relation, the app does not substitute an unrelated general atlas. Repair and DTC screens disable the 3D route when the finding has not yet been persisted instead of fabricating identity from display text.

## Deliberately unresolved release work

The source kernel does not claim capabilities that have not been demonstrated:

- Room schema exports for versions 53 through 55 must be generated by the authorized build toolchain and reviewed before release;
- physical replay fixtures remain empty until byte-exact captures are obtained from approved hardware;
- the temporary `ObdSession` compatibility facade still owns transport details behind acquisition and memory engines and should be decomposed only with conformance evidence;
- macrobenchmark and baseline-profile acceptance require measured Android artifacts, not source-only assertions.

## Verification gate

The following evidence is still required before declaring this kernel released:

- execution of the added Room 50→51→52 adversarial instrumentation tests;
- parser corpus replay for SAE, UDS, CAN multi-frame and DoIP;
- cancellation and STOP verification on approved hardware fixtures;
- malformed/truncated/duplicate/out-of-order frame fuzzing;
- process-death and projection rebuild conformance;
- cross-runtime parity suite;
- Android build, install, launch and crash-log proof.

These checks have deliberately not been run in this implementation session because the project owner instructed that tests and compilation occur only on explicit request.
