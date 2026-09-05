# ECU SUPPORT MATRIX & EVIDENCE REGISTER

**Rule:** No capability is marked verified without deterministic empirical evidence.  
**Levels:** `DECLARED` → `ENCODER_IMPLEMENTED` → `READ_ONLY_VERIFIED` → `SIMULATOR_VERIFIED` → `BENCH_VERIFIED` → `VEHICLE_VERIFIED` → `PRODUCTION_VERIFIED`

---

## 1. ECU Family Matrix

| Manufacturer | Platform / Engine | ECU Family | Typical HW / MCU | Protocol | Verified Adapters | Read Identity | Read DTC / Telemetry | Full Backup (Read) | Calibration (A2L/Maps) | Checksum | Flashing (Write) | Flash Verification | Recovery | Bench Verified | Vehicle Verified | Status & Evidence |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **Universal (SAE)** | All J1979 / OBD-II compliant | Generic OBD-II / UDS | Any | ISO 15765-4 / CAN 11/29b / DoIP | ELM327 v1.4+, STN11xx, vLinker, OBDLink | ✅ `VERIFIED` | ✅ `VERIFIED` | ❌ `BLOCKED` | ❌ `N/A` | ❌ `N/A` | ❌ `BLOCKED` | ❌ `N/A` | ❌ `N/A` | ✅ Yes | ✅ Yes | Production Read-Only Diagnostics |
| **Simulated (Lab)** | Elysium Virtual Bench | `SimulatedProgrammingEcu` | Pure Kotlin / vcan | ISO-TP / UDS / KWP2000 | In-Memory / vcan / Loopback | ✅ `VERIFIED` | ✅ `VERIFIED` | ✅ `VERIFIED` | ✅ `VERIFIED` | ✅ `VERIFIED` | ✅ `VERIFIED` | ✅ `VERIFIED` | ✅ `VERIFIED` | ✅ Yes | ❌ Simulated | Lab Gate / Concurrency & Interruption |
| **VAG (Volkswagen / Audi)** | 1.8T / 2.7T (ME7.5 / ME7.1) | Bosch ME7.x | C167 / 29F800 (800KB/1MB) | KWP2000 / K-Line & CAN | J2534, K-Line Pass-Thru, FTDI | ⚠️ `ENCODER` | ✅ `VERIFIED` | 🔬 `LAB_PLANNED` | 🔬 `A2L_AVAILABLE` | 🔬 `ALGO_KNOWN` | ❌ `BLOCKED` | 🔬 `CRC_READY` | 🔬 `BOOTMODE_PIN24` | 🔬 Planned HIL | ❌ Not Verified | Candidate #1 for Bench HIL Qualification |
| **General Motors** | LS1 / 4.8 / 5.3 / 6.0 V8 | Delco P01 / P59 | Motorola 68HC16 / 512KB-1MB | VPW (J1850) / CAN bench | AllPro, OBDLink EX, J2534 | ⚠️ `ENCODER` | ✅ `VERIFIED` | 🔬 `LAB_PLANNED` | 🔬 `COMMUNITY` | 🔬 `ALGO_KNOWN` | ❌ `BLOCKED` | 🔬 `SUM32` | 🔬 `RECOVERY_KERNEL`| 🔬 Planned HIL | ❌ Not Verified | PCM Hammer open reference architecture |
| **BMW** | E46 / E39 (M54) | Siemens MS43 / MS42 | Siemens C167 / 512KB | KWP2000 / K-Line & DS2 | INPA K+DCAN, J2534 | ⚠️ `ENCODER` | ✅ `VERIFIED` | 🔬 `LAB_PLANNED` | 🔬 `COMMUNITY` | 🔬 `ALGO_KNOWN` | ❌ `BLOCKED` | 🔬 `COMPARE` | 🔬 `BOOTMODE` | 🔬 Planned HIL | ❌ Not Verified | Reference bench architecture |
| **Ford** | Focus / Fiesta 2.0 Duratec | Visteon BlackOak | MPC5xx / 1MB | CAN / UDS | J2534, OBDLink EX | ⚠️ `ENCODER` | ✅ `VERIFIED` | ❌ `UNSUPPORTED`| ❌ `UNSUPPORTED` | ❌ `UNSUPPORTED`| ❌ `BLOCKED` | ❌ `N/A` | ❌ `N/A` | ❌ No | ❌ Not Verified | Ingestion Pending |
| **Hyundai / Kia** | 1.6 / 2.0 CRDi Diesel | Bosch EDC15C2 / EDC16 | C167 / MPC555 | KWP2000 / CAN | K-Line, J2534 | ⚠️ `ENCODER` | ✅ `VERIFIED` | 🔬 `LAB_PLANNED` | 🔬 `DAMOS_KNOWN` | 🔬 `ALGO_KNOWN` | ❌ `BLOCKED` | 🔬 `CRC32` | 🔬 `BENCH_KLINE` | 🔬 Planned HIL | ❌ Not Verified | CRDi Bench Research |

---

## 2. Hardware Selection for Physical Proof (Wave ECU-15)

**Selected Benchmark Family for Physical Proof:**
- **ECU:** Bosch ME7.5 (Audi/VW 1.8T)
- **Rationale:** 
  1. Complete disassembly & hardware documentation widely accessible in public domain.
  2. Inexpensive sacrificial units readily available ($30 - $50).
  3. C167 boot-pin 24 grounding provides guaranteed, unbrickable bench recovery even if flash transfer is completely corrupted.
  4. Supports both K-Line and CAN physical communication.
  5. Checksum algorithm (CRC16/32 over defined memory blocks) is deterministically verifiable with known golden vectors.
  6. ASAM A2L definition files (Damos) are verifiable for parameter mapping.
