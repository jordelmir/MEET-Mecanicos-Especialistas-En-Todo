---
name: meet-apk-operator
description: >
  Master Autonomous Operator harness for the MEET Android application (com.elysium369.meet).
  Empowers AI agents (Antigravity, Mavis, Codex, Claude) to operate, navigate, inspect,
  test, and debug the APK with 100% proficiency across ALL 5 verticals: Viajes, Grúa, Mecánicos,
  Repuestos, and Servicios Elysium, plus Platform Trust Center and Specialist Wallets.
  Features: dynamic ADB discovery (Honor Magic V2 / VER-N49, Xiaomi / M2101K6R, and emulators),
  multi-device wireless bridging, 5% global commission verification, ₡15,000 gift balance inspection,
  SINPE top-up lifecycle, GPS simulation, and full ecosystem test suites.
  Trigger on: "meet apk", "operate apk", "test meet app", "meet adb", "test ride", "grua", "mecanicos",
  "repuestos", "servicios elysium", "trust center", "inspect meet", "drive ride flow", "meet mobile",
  "honor magic", "xiaomi", "connect wireless adb".
---

# MEET APK Master Autonomous Operator Skill

This skill provides an authoritative, world-class guide and operational toolkit for any AI agent to interact with, navigate, inspect, test, and debug the **MEET (Mecánicos Especialistas En Todo)** Android application on physical devices (such as the Honor Magic V2 / `VER-N49` and Xiaomi Redmi Note 10 Pro / `M2101K6R`) or emulators.

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
| **Wireless Bridge Connector** | `tools/android/wireless_adb.py` / `tools/android/connect-wireless.sh` |
| **Auto-Connect Daemon** | `com.meet.adb.wireless` LaunchAgent (`meet_adb_auto_daemon.py`) |
| **Target Hardware References** | **Honor Magic V2** (`VER-N49` / Foldable OLED), **Xiaomi** (`M2101K6R` / `sweet`) |

---

## 2. Platform Core Invariants & Constitutional Economics

Every AI agent operating MEET must uphold and verify these non-negotiable platform rules:

1. **Fixed 5% Platform Commission Across All Verticals:**
   - Platform commission is strictly **5%** (500 bps / `0.05`).
   - Specialists retain **95%** net of all gross earnings.
   - Verified across Viajes, Grúa, Mecánicos, Repuestos, and all Servicios Elysium.
   - In Viajes: If a driver arrives at the pickup point and then cancels, a **5% cancellation penalty fee** is automatically deducted from their wallet. If the passenger cancels, the driver is never penalized.

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

## 3. Fast Device Discovery & Wireless ADB Connection

The macOS Sequoia Local Network Privacy bypass operates via an Apple-signed Python bridge on `127.0.0.1:PORT -> Phone_IP:PORT`.

### Auto-Connect Both Devices in 1 Step:
```bash
# Auto-detects and connects both Honor Magic V2 and Xiaomi simultaneously
python3 scripts/meet_operator.py connect

# Or connect specifically:
python3 scripts/meet_operator.py connect honor   # Honor Magic V2 (VER-N49)
python3 scripts/meet_operator.py connect xiaomi  # Xiaomi (M2101K6R)
```

### Inspect Connected Target Devices:
```bash
python3 scripts/meet_operator.py devices
```
Output example:
```
==================== ACTIVE ADB DEVICES ====================
 • 127.0.0.1:40869      -> XIAOMI REDMI NOTE 10 PRO (M2101K6R)
 • 127.0.0.1:43049      -> HONOR MAGIC V2 (VER-N49)  <-- [TARGET]

Current active target: 127.0.0.1:43049
============================================================
```

### Install APK onto Target Devices in 1 Step:
```bash
python3 scripts/meet_operator.py install honor    # Installs to Honor Magic V2
python3 scripts/meet_operator.py install xiaomi   # Installs to Xiaomi
python3 scripts/meet_operator.py install all      # Installs to both phones concurrently!
```

---

## 4. Multi-Device Targeting (`--device` flag)

Any command can target a specific phone using `--device honor` or `--device xiaomi`:
```bash
# Inspect Honor Magic screen
python3 scripts/meet_operator.py --device honor screen

# Inspect Xiaomi screen
python3 scripts/meet_operator.py --device xiaomi screen

# Navigate Honor to Driver Cockpit, Xiaomi to Passenger Ride Request
python3 scripts/meet_operator.py --device honor nav ride
python3 scripts/meet_operator.py --device honor switch-role driver
python3 scripts/meet_operator.py --device xiaomi nav ride
python3 scripts/meet_operator.py --device xiaomi switch-role passenger
```

---

## 5. Master CLI Command Reference (`meet_operator.py`)

```bash
# 1. Device Management
python3 scripts/meet_operator.py devices
python3 scripts/meet_operator.py connect [honor|xiaomi|all]
python3 scripts/meet_operator.py install [honor|xiaomi|all]

# 2. Live Screen Inspection
python3 scripts/meet_operator.py screen

# 3. Reactive State Snapshot (JSON)
python3 scripts/meet_operator.py status

# 4. Semantic Navigation
python3 scripts/meet_operator.py nav ride          # Viajes
python3 scripts/meet_operator.py nav tow           # Grúa
python3 scripts/meet_operator.py nav mechanic      # Mecánicos
python3 scripts/meet_operator.py nav parts         # Repuestos
python3 scripts/meet_operator.py nav universal     # Ferretería / Servicios
python3 scripts/meet_operator.py nav trust         # Trust Center

# 5. Role & Specialist Cockpit Toggle
python3 scripts/meet_operator.py switch-role driver
python3 scripts/meet_operator.py switch-role passenger
python3 scripts/meet_operator.py switch-mode specialist
python3 scripts/meet_operator.py switch-mode client

# 6. SINPE Móvil Top-Up Automation
python3 scripts/meet_operator.py topup 20000 "SINPE-REF-12345"
python3 scripts/meet_operator.py approve-topup
python3 scripts/meet_operator.py reject-topup

# 7. Tap & Text Input
python3 scripts/meet_operator.py tap "ACEPTAR OFERTA"
python3 scripts/meet_operator.py type "BAC-COMPROBANTE-7721"

# 8. GPS Simulation
python3 scripts/meet_operator.py gps 9.9333 -84.0833

# 9. Screenshot Capture
python3 scripts/meet_operator.py screenshot docs/screenshots/live_test.png

# 10. Complete Multi-Vertical Test Flow
python3 scripts/meet_operator.py test-all-verticals
```

---

## 6. End-to-End Mobility Verification Flow (Driver + Passenger Pair)

With both **Honor Magic V2** and **Xiaomi** connected:
1. `python3 scripts/meet_operator.py --device xiaomi create-ride "Parque Central San José" "Escazú Village" 4500`
2. `python3 scripts/meet_operator.py --device honor submit-offer <requestId> 4500 8 "En camino en Toyota Corolla"`
3. `python3 scripts/meet_operator.py --device xiaomi accept-offer <requestId>`
4. `python3 scripts/meet_operator.py --device honor advance-ride <requestId> ARRIVED`
5. Validate in-app live audio call and realtime messaging between Honor and Xiaomi.
6. Verify driver cancellation penalty (5% deducted if cancelled after arrival).
