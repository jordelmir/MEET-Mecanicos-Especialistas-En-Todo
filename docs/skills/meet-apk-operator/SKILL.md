---
name: meet-apk-operator
description: >
  Master Autonomous Operator harness for the MEET Android application (com.elysium369.meet).
  Empowers AI agents (Antigravity, Mavis, Codex, Claude) to operate, navigate, inspect,
  test, and debug the APK with 100% proficiency across ALL 5 verticals: Viajes, Grúa, Mecánicos,
  Repuestos, and Servicios Elysium, plus Platform Trust Center and Specialist Wallets.
  Features: dynamic ADB discovery (Honor Magic V2 / VER-N49 / emulators), 5% global commission verification,
  ₡15,000 gift balance inspection, SINPE top-up lifecycle, GPS simulation, and full ecosystem test suites.
  Trigger on: "meet apk", "operate apk", "test meet app", "meet adb", "test ride", "grua", "mecanicos",
  "repuestos", "servicios elysium", "trust center", "inspect meet", "drive ride flow", "meet mobile".
---

# MEET APK Master Autonomous Operator Skill

This skill provides an authoritative, world-class guide and operational toolkit for any AI agent to interact with, navigate, inspect, test, and debug the **MEET (Mecánicos Especialistas En Todo)** Android application on physical devices (such as the Honor Magic V2 / `VER-N49`) or emulators.

---

## 1. System Topology & Architecture

| Component | Identifier / Detail |
|---|---|
| **Package Name** | `com.elysium369.meet` |
| **Main Activity** | `com.elysium369.meet.MainActivity` |
| **Automation Broadcast Receiver** | `com.elysium369.meet.automation.AiAutomationReceiver` |
| **Broadcast Action (Actions)** | `com.elysium369.meet.AI_ACTION` |
| **Broadcast Action (Navigation)** | `com.elysium369.meet.AI_NAVIGATE` |
| **Reactive State Snapshot** | `/data/local/tmp/meet_state.json` (also in app internal storage) |
| **CLI Operator Tool** | `scripts/meet_operator.py` (auto-detects connected active ADB devices) |
| **Target Hardware Reference** | Honor Magic V2 (`VER-N49`), Foldable OLED, ADB Wireless / USB |

---

## 2. Platform Core Invariants & Constitutional Economics

Every AI agent operating MEET must uphold and verify these non-negotiable platform rules:

1. **Fixed 5% Platform Commission Across All Verticals:**
   - Platform commission is strictly **5%** (500 bps / `0.05`).
   - Specialists retain **95%** net of all gross earnings.
   - Verified across Viajes, Grúa, Mecánicos, Repuestos, and all Servicios Elysium.

2. **₡15,000 Promotional Starter Gift Balance:**
   - Every specialist account receives an initial gift of **₡15,000 CRC** granted by Jorge David Del Valle Miranda.
   - Displayed prominently in the Specialist Wallet Card: `"REGALO BIENVENIDA · ₡15,000 REGALADOS"`.

3. **Official SINPE Móvil Configuration:**
   - Official Top-up Number: **63194029**
   - Official Beneficiary: **Jorge David Del Valle Miranda**
   - Official Notification Email: **jordelmir@gmail.com**

4. **Trust Center Closed-Loop Validation:**
   - Specialists submit top-up receipts via `SpecialistSinpeTopupDialog` (parsed via `SinpeReceiptParser`).
   - Top-ups enqueue with status `PENDIENTE TRUST CENTER ⏳`.
   - The platform owner validates the bank transfer in the **Trust Center** (`trust_center`) and performs 1-tap accreditation (`ACREDITAR`).
   - Specialist wallet balance updates immediately and top-up chip marks `ACREDITADA ✓`.

---

## 3. The 5 Ecosystem Verticals & Navigation Routes

| Vertical | Semantic Alias | Jetpack Compose Route | Screen Implementation |
|---|---|---|---|
| **Viajes** | `ride` / `viajes` | `ride_service` | `RideServiceScreen.kt` |
| **Grúa y Rescate** | `tow` / `grua` | `tow_truck` / `tow_truck_service` | `TowTruckServiceScreen.kt` |
| **Mecánicos y Talleres** | `mechanic` / `mecanicos` | `mechanic_service` | `MechanicServiceScreen.kt` |
| **Repuestos y Catálogo** | `parts` / `repuestos` | `part_request` | `PartRequestScreen.kt` |
| **Servicios Elysium** | `universal` / `ferreteria` | `universal_services` / `universal_activity/<id>` | `UniversalActivityWorkflowScreen.kt` |
| **Platform Trust Center** | `trust` / `trust_center` | `trust_center` | `PlatformTrustCenterScreen.kt` |
| **Scanner & OBD-II** | `scanner` | `scanner` | `LiveScannerScreen.kt` |
| **Diagnóstico DTC** | `dtc` / `dtcs` | `dtc` | `DtcScreen.kt` |
| **Garage** | `garage` | `garage` | `GarageScreen.kt` |
| **Inicio** | `home` | `home` | `HomeScreen.kt` |

---

## 4. Fast-Start Master CLI Reference (`meet_operator.py`)

All commands auto-detect the active connected ADB device (no need to manually set serials):

```bash
# 1. Inspect live screen elements (text, center coordinates, IDs, bounding boxes)
python3 scripts/meet_operator.py screen

# 2. Inspect reactive internal state (JSON)
python3 scripts/meet_operator.py status

# 3. Semantic navigation to any vertical
python3 scripts/meet_operator.py nav tow          # Grúa
python3 scripts/meet_operator.py nav mechanic     # Mecánicos
python3 scripts/meet_operator.py nav parts        # Repuestos
python3 scripts/meet_operator.py nav universal hardware_store # Ferretería
python3 scripts/meet_operator.py nav trust        # Trust Center
python3 scripts/meet_operator.py nav ride         # Viajes

# 4. Universal Specialist Cockpit Toggle
python3 scripts/meet_operator.py switch-mode specialist   # Cockpit Pro / Conductor
python3 scripts/meet_operator.py switch-mode client       # Modo Cliente

# 5. SINPE Móvil Top-Up Automation
python3 scripts/meet_operator.py topup 20000 "SINPE-REF-12345"

# 6. Trust Center 1-Tap Approval / Rejection
python3 scripts/meet_operator.py approve-topup    # Taps ACREDITAR in Trust Center
python3 scripts/meet_operator.py reject-topup     # Taps RECHAZAR in Trust Center

# 7. Smart Tap on any visible button or text
python3 scripts/meet_operator.py tap "COCKPIT GRUISTA (PRO)"
python3 scripts/meet_operator.py tap "RECARGAR SALDO CON SINPE MÓVIL"

# 8. Type text into active focused field
python3 scripts/meet_operator.py type "BAC-COMPROBANTE-7721"

# 9. Realtime GPS Simulation
python3 scripts/meet_operator.py gps 9.9333 -84.0833

# 10. High-Resolution Screenshot Capture
python3 scripts/meet_operator.py screenshot docs/screenshots/my_test.png

# 11. Complete Ecosystem Autonomous Verification (All 5 Verticals + Trust Center)
python3 scripts/meet_operator.py test-all-verticals
```

---

## 5. Automated Multi-Vertical Verification Protocol

To verify that the entire MEET ecosystem is working seamlessly:

```bash
python3 scripts/meet_operator.py test-all-verticals
```

This automated sequence executes:
1. Launches `MainActivity` on the connected physical device.
2. Navigates to **Grúa**, switches to **Cockpit Gruista (PRO)**, validates ₡15k gift balance and 5% commission.
3. Navigates to **Mecánicos**, switches to **Cockpit Taller (PRO)**, validates catalog and 5% commission.
4. Navigates to **Repuestos**, switches to **Especialista en Repuestos**, validates graph and 5% commission.
5. Navigates to **Servicios Elysium**, switches to **Ferretería Afiliada**, validates cockpit and 5% commission.
6. Submits a test SINPE topup, navigates to **Platform Trust Center**, verifies pending queue, and performs 1-tap `ACREDITAR`.
7. Navigates back to Grúa and confirms accredited balance increase (`ACREDITADA ✓`).
8. Saves all timestamped proof screenshots to `docs/screenshots/meet_ecosystem_verification/`.

---

## 6. Development & Deployment Protocol

Before committing or releasing any changes:
1. **Clean compilation**: `./android/gradlew -p android compileDebugKotlin`
2. **Assemble APK**: `./android/gradlew -p android assembleDebug`
3. **Install to device**: `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`
4. **Verify Parity**: `bash tests/parity/ci-verify.sh` (TS ≡ Kotlin match required!)
5. **Run test flow**: `python3 scripts/meet_operator.py test-all-verticals`
