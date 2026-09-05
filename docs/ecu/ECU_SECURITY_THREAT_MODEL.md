# ECU LAB SECURITY & THREAT MODEL (STRIDE)

**Doctrine:** Raw ECU access is dangerous; safety systems, anti-theft, and emissions are non-negotiable boundaries.

---

## 1. STRIDE Analysis Matrix

| Threat Category | Specific Attack Vector | Mitigating Authority & Architecture |
|---|---|---|
| **Spoofing** | Attacker impersonates legitimate technician or ECU response | Mutual ECDSA challenge on Programming Agent IPC; vehicle VIN & ECU fingerprint binding (`EcuIdentityProfile`). |
| **Tampering** | Modification of calibration binary in transit or memory | Immutable `FirmwareArtifact` bound to SHA-256; `CalibrationChangeSet` rejected if baseline hash diverges by even 1 bit; deterministic checksum verification. |
| **Repudiation** | Operator claims a bricked ECU was caused by the software without proof | Append-only forensic journal in `DiagnosticEvidenceVault` recording all UDS request/response frames, preflight telemetry, voltage history, and operator signature. |
| **Information Disclosure** | Extraction of proprietary OEM algorithms or cryptographic keys | No OEM private keys embedded in APK; diagnostic hex dumps redacted in production logs; proprietary firmware encrypted at rest via platform Keystore. |
| **Denial of Service** | Process kill or power drop during flash erase bricking ECU | Monotonic 27-state machine persists progress before irreversible transitions; boot-pin recovery kernel architecture handles bricked bootloaders. |
| **Elevation of Privilege** | Using raw terminal or UI to bypass security access or disable airbags | `DiagnosticRawCommandPolicy` blocks write services ($10, $11, $27, $2E, $31, $34, $36, $3D); `EcuOperationCompliancePolicy` blocks defeat devices and safety disabling. |

---

## 2. Inviolable Security Boundaries

1. **Terminal Isolation Guarantee:**
   The expert terminal is mathematically proven to be read-only. It cannot be used as an interactive backdoor to issue memory-write or seed-key commands.
2. **Supply Chain & Plugin Signing:**
   All production capability packs and plugins must be signed by the Elysium Vanguard root certificate authority. Unsigned local packs are strictly rejected fail-closed.
3. **Out-of-Process Isolation:**
   Vendor J2534 DLLs run in an isolated child process. Buffer overflows, heap corruption, or access violations in unvetted C/C++ third-party drivers terminate the bridge process without corrupting the JVM memory space.
