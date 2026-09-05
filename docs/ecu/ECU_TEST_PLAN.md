# ECU LAB VERIFICATION TEST PLAN & LADDER

**Doctrine:** Never skip verification levels verbally.
**Ladder:** `UNIT` → `PROTOCOL_GOLDEN_VECTOR` → `SIMULATOR` → `VCAN` → `HIL` → `BENCH` → `VEHICLE` → `PRODUCTION`

---

## 1. Five-Level Verification Hierarchy

### Level 0: Unit & Property Invariants (Host JVM)
- **Target:** Domain state machines, math, checksum algorithms, and immutability invariants.
- **Suites:**
  - `ProgrammingSessionTest`: 27-state monotonic lifecycle progression and abort boundary enforcement.
  - `EcuComplianceAndArtifactTest`: Defeat device blocking, baseline hash matching, and preflight requirements.
  - `ProgrammingLeaseConcurrencyTest`: CyclicBarrier race proving 1 active lease and $N-1$ typed rejections.
  - `ChecksumGoldenVectorTest`: Endian-accurate block summation and complement validation.
  - `AdapterClassificationTest`: Generic ELM327 rejection vs J2534 / SocketCAN approval.

### Level 1: Protocol Conformance Golden Vectors
- **Target:** Byte-exact decoding of ISO 15765-2 (ISO-TP) and ISO 14229-1 (UDS).
- **Corpus:** `fixtures/ecu-protocol-vectors/uds-response-vectors.json` and `isotp-conformance-vectors.json`.
- **Suite:** `ProtocolGoldenVectorTest` verifying Single Frame, First Frame, Consecutive Frame, Flow Control (CTS, BS, STmin), and typed NRCs ($12, $22, $33, $78).

### Level 2: Virtual ECU Simulation & Interruption Matrix
- **Target:** State recovery under injected physical faults.
- **Suite:** `ProgrammingInterruptionMatrixTest` executing `SimulatedProgrammingEcu`:
  - Erase timeout → `BRICKED_BOOTLOADER_ONLY` → `RECOVERY_REQUIRED`.
  - Transfer power loss → halt writes → `RECOVERY_REQUIRED`.
  - Checksum mismatch → deny reset.
  - Reset disconnect → `FAILED_UNCERTAIN`.
  - Boot-pin grounding → restore operational state.

### Level 3: Linux vcan & SocketCAN Virtual Bus
- **Target:** Multi-process ISO-TP communication over Linux virtual CAN interface (`vcan0`).
- **Tools:** `can-utils` (`isotpsend`, `isotprecv`, `candump`), `ICSim`.
- **Precondition:** Isolated lab VM or Linux test harness.

### Level 4: Hardware-in-the-Loop (HIL) & Sacrificial Bench
- **Target:** Physical bench setup with current-limited regulated 13.8V power supply, sacrificial Bosch ME7.5 ECU, and Tactrix OpenPort 2.0 J2534 adapter.
- **Validation:** Boot-pin 24 recovery validation, real K-Line/CAN baud rate negotiation, read-back byte verification.

### Level 5: Controlled In-Vehicle Field Validation
- **Target:** Dedicated test vehicle under controlled conditions with verified battery maintainer.
- **Preconditions:** Levels 0 through 4 completely green, signed capability pack loaded, immutable double-read backup verified.
