# ECU AUTHORITY MAP & MASTER INVARIANT REGISTRY

**Invariant:** ONE BUSINESS/PHYSICAL FACT → ONE AUTHORITY.  
**Doctrine:** MORE CAPABILITY. FEWER AUTHORITIES.  
**Fail-Closed Principle:** UNKNOWN NEVER BECOMES SUCCESS.

---

## 1. Master Authority Matrix

| Capability | Canonical Authority | Persistence Layer | Transport Layer | Mutation Owner | Evidence Subsystem | Target Action |
|---|---|---|---|---|---|---|
| **Adapter Identity & Fingerprint** | `AdapterFingerprint.kt` | Encrypted Keystore / Room `adapter_profiles` | Serial / BLE / TCP / J2534 | `ConnectionSupervisor` | `DiagnosticEvidenceVault` | Verified adapter capability profile |
| **Vehicle Identity & Binding** | `VehicleIdentityAcquisition.kt` | Room `vehicles` + Supabase `vehicles` | SAE Mode $09 / UDS $22 $F190 | `VehicleRepository` | `VehicleTruth` | VIN verification & physical lease binding |
| **ECU Identity & Profiling** | `EcuIdentityProfile.kt` | Room `ecu_identities` | UDS $22 ($F180, $F187-$F189, $F191-$F195) | `EcuIdentityManager` | `DiagnosticSnapshotEvidence` | HW/SW/CALID/CVN immutable profile |
| **Diagnostic Session Lifecycle** | `ObdSession.kt` | Memory `StateFlow` + Session Journal | TransportFabricV2 / DoIP / SocketCAN | `ObdSession` | `SessionAuditLog` | Fail-closed session transitions |
| **ECU Capability Certification** | `SignedDiagnosticCapabilityPack` | Signed assets + Trust Manifest | Cryptographic Verification | `ActiveDiagnosticSafety` | `DiagnosticManifestSigner` | Anti-rollback verified capability packs |
| **Programming Capability Certification** | `ProgrammingCapabilityPack.kt` | Signed Assets / Local Secure Store | ECDSA SHA-256 + Trust Root | `ProgrammingTrustRegistry` | `DiagnosticEvidenceVault` | Verified flashing / memory write rules |
| **Original Firmware Artifact** | `FirmwareArtifact.kt` (Type: `ORIGINAL_READBACK`) | Encrypted Immutable Vault (`.bin` + SHA-256) | Isolated Local File Store | `FirmwareVaultManager` | `EvidenceVault` (Immutable SHA-256) | Read-only double-read backup |
| **Derivative Firmware Artifact** | `FirmwareArtifact.kt` (Type: `DERIVED_CALIBRATION`) | Encrypted Vault (Parent Lineage Hash) | Isolated Local File Store | `CalibrationEngine` | `EvidenceVault` (Lineage Hash) | Signed, validated modification |
| **Calibration Changeset** | `CalibrationChangeSet.kt` | Room `calibration_changesets` | In-memory JSON / IPC | `CalibrationEditorEngine` | `EvidenceVault` | Semantic change review & limits |
| **Checksum Strategy** | `ChecksumStrategy.kt` | Family-specific deterministic algorithms | Pure Domain Calculation | `ChecksumRegistry` | `VerificationEvidence` | Golden-vector verified checksums |
| **Programming Session Aggregate** | `ProgrammingSession.kt` | Room `programming_sessions` (Durable CAS) | Local Isolated IPC | `ProgrammingOrchestrator` | `DiagnosticEvidenceVault` | Monotonic 27-state flashing machine |
| **Single Programmer Lease** | `ProgrammingLease.kt` | Atomic memory mutex + Room CAS | Local System Mutex | `ProgrammingLeaseManager` | `AuditLog` | Exclusivity: 1 lease per ECU |
| **Emergency Recovery Engine** | `RecoveryPlan.kt` | Durable Recovery Journal | Low-level Bootloader / Bench | `EcuRecoveryOrchestrator` | `DiagnosticEvidenceVault` | Physical-state reconciliation & restore |
| **Post-Flash Verification** | `BeforeAfterComparator.kt` | Room `diagnostic_snapshots` | UDS $22 / Routine $31 | `PostFlashVerifier` | `VehicleTruth` | CRC/CALID/CVN/DTC Before-After truth |
| **Compliance & Jurisdiction Policy**| `EcuOperationCompliancePolicy.kt` | Static Regulatory Ruleset | Pure Domain Evaluation | `CompliancePolicyEngine` | `AuditLog` | Emissions/Anti-theft/Safety veto |

---

## 2. Inviolable Architectural Boundaries

1. **The God-Object Prohibition:**
   - `ObdViewModel` SHALL NOT contain ECU flashing or memory-modification business logic.
   - `ObdSession` SHALL NOT be expanded into an ECU flasher. It remains a diagnostic communication and telemetry stream.
   - ECU flashing runs inside `ProgrammingOrchestrator` and `ProgrammingExecutor`, registered in `ActiveOperationsRegistry`.

2. **The Terminal Isolation:**
   - `DiagnosticRawCommandPolicy` remains strictly read-only.
   - Any attempt to route write services ($10 programming sessions, $11, $2E, $2F, $31, $34, $36, $37, $3D) through the terminal fails closed.

3. **Fail-Closed Progression:**
   - If power is uncertain: `UNKNOWN` -> `BLOCK`.
   - If recovery is unavailable: `NO_FLASH`.
   - If artifact baseline hash does not match: `BLOCK`.
   - If ECU does not reconnect after reset: `FAILED_UNCERTAIN` or `RECOVERY_REQUIRED` (Never `COMPLETED`).
