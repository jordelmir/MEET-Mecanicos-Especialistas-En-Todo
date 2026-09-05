# OPEN-SOURCE ECU LAB DUE DILIGENCE & LICENSING AUDIT

**Policy:** Open-source components are evaluated to learn architectures, validate conformance, and build laboratory tooling.  
**Strict Boundary:** Proprietary runtime code MUST NOT embed GPL code without an explicit dual-licensing or process-boundary isolation contract.

---

## 1. Classification Taxonomy

- `PRODUCTION_DEPENDENCY`: Integrated directly into runtime APK / desktop agent binary (Apache-2.0, MIT, BSD).
- `CONFORMANCE_ORACLE`: Used in CI or test suites to independently verify our encoder/decoder correctness (MIT, BSD).
- `LAB_TOOL`: Executed outside production vehicles in laboratory, Docker, or development machines.
- `REFERENCE_ONLY`: Studied for domain knowledge and architectural understanding; zero code copied (GPL, proprietary).
- `REJECTED`: Fails licensing, security, or safety standards.

---

## 2. Comprehensive Open-Source Evaluation Register

| Project | Upstream Repository | License | Role for Elysium | Decision | Key Takeaway & Architecture Value |
|---|---|---|---|---|---|
| **odxtools** | `github.com/mercedes-benz/odxtools` | MIT | `LAB_TOOL` / Generator | **APPROVED** | Authoritative parser for ASAM ODX / MCD-2 D (PDX archives). Converts ODX XML into normalized diagnostic models. Used to compile signed MEET capability packs offline. |
| **python-udsoncan** | `github.com/pylessard/python-udsoncan` | MIT | `CONFORMANCE_ORACLE` | **APPROVED** | Gold standard UDS test oracle. Used in CI to generate expected requests/responses for ISO 14229 services ($10, $22, $27, $31, $34, $36, $37). |
| **python-can-isotp** | `github.com/pylessard/python-can-isotp` | MIT | `CONFORMANCE_ORACLE` | **APPROVED** | ISO 15765-2 transport layer oracle for verifying multi-frame segmentation, Flow Control (FC), BlockSize (BS), and STmin handling. |
| **automotive_diag** | `github.com/oxibus/automotive_diag` | MIT / Apache-2.0 | `LAB_TOOL` / Native Agent | **APPROVED** | Memory-safe Rust implementation of UDS, KWP2000, OBD, and DoIP. Prime candidate for high-speed desktop Programming Agent. |
| **j2534-bridge** | `github.com/mickeyl/j2534-bridge` | MIT | `LAB_TOOL` / Bridge | **APPROVED** | Solves 32-bit / 64-bit DLL bitness mismatch and crash isolation by running vendor J2534 DLLs in an isolated worker process over local IPC. |
| **J2534-Sharp** | `github.com/BrianH/J2534-Sharp` | MIT | `REFERENCE_ONLY` | **APPROVED** | C# P/Invoke mapping of SAE J2534 Pass-Thru API v04.04. Clean reference for timing parameters, PassThruIoctl, and channel configuration. |
| **panda** | `github.com/commaai/panda` | MIT | `LAB_TOOL` / Reference | **APPROVED** | Safety-first CAN / CAN-FD transceiver hardware and firmware. Safety model ensures a crashed client never floods the bus with malicious frames. |
| **linux-can / can-utils** | `github.com/linux-can/can-utils` | GPL-2.0 / BSD | `LAB_TOOL` (Kernel) | **APPROVED (CLI/OS)** | Native Linux SocketCAN tooling (`candump`, `cansend`, `cangen`, `isotpsend`, `isotprecv`). Used with `vcan` for continuous integration tests without hardware. |
| **cantools** | `github.com/cantools/cantools` | MIT | `LAB_TOOL` | **APPROVED** | Python DBC, ARXML, and KCD decoding and encoding. |
| **canmatrix** | `github.com/ebroecker/canmatrix` | BSD-2-Clause | `LAB_TOOL` | **APPROVED** | Universal matrix converter between DBC, ARXML, ODX, FIBEX, and JSON. |
| **Binwalk v3** | `github.com/ReFirmLabs/binwalk` | MIT | `LAB_TOOL` / Sandbox | **APPROVED** | Fast Rust-based firmware extraction and entropy analysis tool for offline research sandbox. |
| **Ghidra** | `github.com/NationalSecurityAgency/ghidra` | Apache-2.0 | `LAB_TOOL` / Offline | **APPROVED** | Static disassembly and decompiler platform for reverse engineering ECU MCU architectures (C167, TriCore, MPC5xx, SH705x, RH850). Offline sandbox only. |
| **ICSim** | `github.com/zombieCraig/ICSim` | MIT | `LAB_TOOL` | **APPROVED** | Instrument Cluster and CAN simulator over `vcan0`. Deterministic virtual dashboard for bench testing. |
| **python-doipclient** | `github.com/jacobschaer/python-doipclient`| MIT | `CONFORMANCE_ORACLE` | **APPROVED** | ISO 13400 client used as conformance test oracle for validating MEET's native DoIP socket implementation. |
| **PCM Hammer** | `github.com/PcmHammer/PcmHammer` | GPL-3.0 | `REFERENCE_ONLY` | **RESTRICTED** | Real-world flashing and recovery implementation for GM Delco P01/P59. Architectural reference for family-specific recovery kernels. Zero code copied. |
| **OpenVehicleDiag**| `github.com/rnd-ash/OpenVehicleDiag` | GPL-3.0 | `REFERENCE_ONLY` | **RESTRICTED** | Desktop diagnostic scanner written in Rust/TypeScript. Reference for J2534 cross-platform handling. |
| **RomRaider** | `github.com/RomRaider/RomRaider` | GPL-2.0 | `REFERENCE_ONLY` | **RESTRICTED** | Open-source tuning suite. Studied for 2D/3D map visualization and table interpolation algorithms. Zero code copied. |
| **rusEFI** | `github.com/rusefi/rusefi` | GPL-3.0 | `REFERENCE_ONLY` | **RESTRICTED** | Open-source standalone engine management system. Excellent for HIL bench ECU simulation. |
| **pyA2L** | `github.com/avval/pyA2L` | GPL-2.0 | `REFERENCE_ONLY` | **RESTRICTED** | ASAM MCD-2 MC (A2L) grammar reference. Our A2L parser will be built clean-room in Kotlin/Rust under Apache-2.0. |
| **pyXCP** | `github.com/christoph2/pyXCP` | LGPL-3.0+ | `LAB_TOOL` (Out-of-proc) | **RESTRICTED** | Universal measurement and calibration protocol (XCP on CAN/Ethernet). Evaluated as external lab process. |
| **OpenBLT** | `github.com/feaser/openblt` | GPL-2.0 / Commercial | `REFERENCE_ONLY` | **RESTRICTED** | Open-source microcontroller bootloader. Reference for checksum verification, seed-key exchange, and flash memory layout protection. |

---

## 3. Clean-Room Architectural Boundary Policy

1. **GPL Isolation Guarantee:**
   - Any GPL or LGPL tools (e.g. `can-utils`, `PCM Hammer`, `RomRaider`) are strictly isolated to external OS processes, CLI test harnesses, or reference documentation.
   - The Elysium runtime (Android Kotlin app, Supabase migrations, server and native desktop agent) remains 100% proprietary under clean-room Apache-2.0 / MIT compatible terms.
2. **Deterministic Golden Vectors:**
   - All conformance test vectors placed into `fixtures/ecu-protocol-vectors/` are synthetically generated or derived from public standards (ISO 14229, ISO 15765, SAE J1979). No proprietary OEM secrets or confidential calibration binaries are stored in the repo.
