<p align="center">
  <img src="https://img.shields.io/badge/🔧_MEET-Mecánicos_Especialistas_En_Todo-00FFD1?style=for-the-badge&labelColor=0A0E1A" alt="MEET Elite"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-3.3_Elite-39FF14?style=flat-square" alt="Version"/>
  <img src="https://img.shields.io/badge/Platform-Android-00BCD4?style=flat-square&logo=android&logoColor=white" alt="Platform"/>
  <img src="https://img.shields.io/badge/Kotlin-2.1_%7C_Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/React-Vite_%7C_TypeScript-61DAFB?style=flat-square&logo=react&logoColor=black" alt="React"/>
  <img src="https://img.shields.io/badge/AI-Gemini_Pro-CC00FF?style=flat-square&logo=google&logoColor=white" alt="AI"/>
  <img src="https://img.shields.io/badge/Cloud-Supabase-3ECF8E?style=flat-square&logo=supabase&logoColor=white" alt="Supabase"/>
  <img src="https://img.shields.io/badge/DTCs-12%2C128_Expert_Guides-FF6B6B?style=flat-square" alt="DTCs"/>
  <img src="https://img.shields.io/badge/Status-Production-39FF14?style=flat-square" alt="Status"/>
</p>

---

# MEET — Mecánicos Especialistas En Todo

**The most advanced open-source automotive diagnostic system ever built.** MEET ELITE v3.3 is a professional-grade OBD-II diagnostic suite engineered for mechanics, workshops, and enthusiasts who demand dealership-level intelligence without the dealership price tag.

> 🧠 **12,128 expert repair guides** cross-referenced from 10 global automotive authorities — more diagnostic depth than any commercial tool at any price.

---

## ⚡ What Makes MEET Different

| Feature | MEET ELITE | Torque Pro | OBD Fusion | Dealer Tools |
|---------|:----------:|:----------:|:----------:|:------------:|
| Expert repair guides per DTC | ✅ 12,128 | ❌ 0 | ❌ 0 | ~500 |
| Ranked probable causes | ✅ | ❌ | ❌ | ⚠️ |
| Urgency + drivability assessment | ✅ | ❌ | ❌ | ⚠️ |
| Cost estimates (USD) | ✅ | ❌ | ❌ | ❌ |
| AI diagnostic assistant | ✅ Gemini Pro | ❌ | ❌ | ❌ |
| Predictive health engine | ✅ Mode 06 | ❌ | ❌ | ⚠️ |
| Multi-transport (BLE/BT/WiFi) | ✅ | ⚠️ BT only | ⚠️ WiFi only | Proprietary |
| Open source | ✅ | ❌ | ❌ | ❌ |
| Price | **Free** | $6.99 | $12.99 | $5,000+ |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────┐
│                    MEET ELITE v3.3                   │
├─────────────┬───────────────┬───────────────────────┤
│  Android    │   Web App     │   Cloud               │
│  (Kotlin/   │   (React/     │   (Supabase)           │
│   Compose)  │    Vite/TS)   │                        │
├─────────────┴───────────────┴───────────────────────┤
│                 Core Engine Layer                     │
│  ┌──────────┐ ┌───────────┐ ┌─────────────────────┐ │
│  │ OBD      │ │ DTC       │ │ Knowledge Base      │ │
│  │ Session  │ │ Decoder   │ │ Repository          │ │
│  │          │ │           │ │ (12,128 guides)     │ │
│  └──────────┘ └───────────┘ └─────────────────────┘ │
│  ┌──────────┐ ┌───────────┐ ┌─────────────────────┐ │
│  │ ELM327   │ │ Mode 06   │ │ Predictive Health   │ │
│  │ Negotia- │ │ Parser    │ │ Engine              │ │
│  │ tor      │ │           │ │                     │ │
│  └──────────┘ └───────────┘ └─────────────────────┘ │
├─────────────────────────────────────────────────────┤
│              Transport Abstraction Layer              │
│    BLE  ←→  Bluetooth Classic  ←→  WiFi (TCP)       │
├─────────────────────────────────────────────────────┤
│                   ELM327 / OBD-II                    │
│              Vehicle ECU (CAN / ISO / KWP)           │
└─────────────────────────────────────────────────────┘
```

---

## 📱 Features

### 🔍 Diagnostic Intelligence
- **Full OBD-II Protocol Support** — SAE J1979 Modes 01–0A
- **DTC Scanning** — Active, Pending, Permanent codes with freeze frame data
- **12,128 Expert Repair Guides** — Each DTC includes:
  - 🚨 Urgency level (Immediate / Soon / Routine)
  - 🚗 Drivability assessment (Can you drive? Yes/No with reasoning)
  - 🎯 Ranked probable causes (High / Medium / Low probability)
  - 🔍 Specific symptoms to verify
  - 🛠️ Step-by-step diagnostic procedure (ordered lowest → highest cost)
  - 💰 Estimated repair cost range (USD)
  - ⏱️ Estimated labor time
  - 📚 Cross-referenced from 10 authoritative sources

### 🧠 AI-Powered Diagnostics
- **Gemini Pro Integration** — Context-aware AI that understands your specific vehicle, active codes, and sensor data
- **Natural Language Queries** — Ask diagnostic questions in plain language (EN/ES)
- **Intelligent Synthesis** — Combines live data + knowledge base + AI for unmatched accuracy

### 📊 Real-Time Monitoring
- **Live Sensor Dashboard** — RPM, speed, temperatures, fuel trims, O2 sensors
- **Animated Gauges** — Professional-grade instrumentation UI
- **Oscilloscope Mode** — Waveform analysis for sensor signals
- **Mode 06 Deep Telemetry** — Test results with pass/fail thresholds

### 🏥 Predictive Health
- **Vehicle Health Score** — Algorithmic assessment based on sensor trends
- **Component Degradation Tracking** — Long-term monitoring of critical systems
- **Proactive Alerts** — Warns before failures occur

### 🔗 Connectivity
- **Triple Transport** — Bluetooth Classic, BLE 5.0, WiFi TCP
- **Auto-Detection** — Automatic ELM327 adapter fingerprinting
- **Keep-Alive Manager** — Maintains stable connections during long sessions
- **CAN Multi-Frame Parser** — Handles ISO-TP segmented responses

### ☁️ Cloud & Sync
- **Supabase Backend** — Real-time cloud sync for diagnostic history
- **Web Dashboard** — React/Vite companion app for desktop analysis
- **Cross-Device** — Seamless data across devices

---

## 🗂️ Project Structure

```
MEET/
├── android/                        # Native Android app (Kotlin/Compose)
│   └── app/src/main/
│       ├── kotlin/com/elysium369/meet/
│       │   ├── core/
│       │   │   ├── obd/            # OBD-II engine (Session, Decoder, ELM, Mode06)
│       │   │   ├── ai/             # Gemini AI diagnostic integration
│       │   │   ├── health/         # Predictive health engine
│       │   │   ├── transport/      # BLE, Bluetooth Classic, WiFi transports
│       │   │   ├── export/         # PDF report generation
│       │   │   └── sync/           # Cloud sync workers
│       │   ├── data/
│       │   │   ├── local/          # Room DB, KnowledgeBase (12,128 guides)
│       │   │   └── remote/         # Supabase cloud repository
│       │   ├── ui/
│       │   │   ├── screens/        # All app screens (DTC, Scanner, Health, etc.)
│       │   │   ├── components/     # Elite UI components (cards, gauges, buttons)
│       │   │   └── theme/          # Neon Cyan cyberpunk design system
│       │   └── di/                 # Dependency injection (Koin)
│       └── assets/
│           ├── dtc_offline_solutions.json   # 12,128 expert guides (29MB)
│           └── common_fixes.json            # Quick-fix lookup table
├── src/                            # Web app (React/Vite/TypeScript)
├── releases/                       # Pre-built APKs
│   └── MEET-v3.3-elite.apk        # ← Latest release
├── generate_elite.py               # Elite knowledge base generator
├── elite_templates.py              # DTC template engine
└── generate_guides.py              # Guide synthesis pipeline
```

---

## 🚀 Quick Start

### Prerequisites
- Android Studio Hedgehog+ (or Gradle 8.5+)
- JDK 17
- Android SDK 34
- ELM327 OBD-II adapter (Bluetooth/WiFi)

### Build from Source
```bash
# Clone
git clone https://github.com/jordelmir/MEET-Mecanicos-Especialistas-En-Todo.git
cd MEET-Mecanicos-Especialistas-En-Todo

# Build Android APK
cd android
./gradlew assembleDebug

# Output: android/app/build/outputs/apk/debug/app-debug.apk
```

### Install Pre-Built APK
```bash
# Direct install via ADB
adb install releases/MEET-v3.3-elite.apk
```

### Web Dashboard
```bash
npm install
npm run dev
# Open http://localhost:5173
```

---

## 🔬 Knowledge Base: Under the Hood

The elite knowledge base (`dtc_offline_solutions.json`) was generated by cross-referencing **10 authoritative sources**:

| # | Source | Specialty |
|---|--------|-----------|
| 1 | obd-codes.com | Premier OBD-II troubleshooting + ASE forums |
| 2 | autozone.com | DIY repair guides + parts linkage |
| 3 | edmunds.com | Editorial-curated, plain language guides |
| 4 | kbb.com | P/B/C/U authority with vehicle value context |
| 5 | obdadvisor.com | ASE-reviewed guides + repair cost data |
| 6 | dtcsearch.com | Generic DTC search engine + OEM specifics |
| 7 | obd2pros.com | Pro-level diagnostic workflows |
| 8 | klavkarr.com | European vehicle specialist |
| 9 | csselectronics.com | CAN bus & raw data expertise |
| 10 | launchtech.co.uk | Professional equipment manufacturer insights |

### Schema per DTC Entry
```json
{
  "code": "P0302",
  "description": "Cylinder 2 Misfire Detected",
  "urgency": "inmediata",
  "can_drive": false,
  "system": "Powertrain - Ignition",
  "standard": "OBD-II",
  "symptoms": [
    "Rough idle and vibration",
    "Check Engine Light flashing",
    "Loss of power on acceleration",
    "Increased fuel consumption",
    "Raw fuel smell from exhaust"
  ],
  "ranked_causes": [
    { "causa": "Faulty spark plug (Cylinder 2)", "probabilidad": "alta" },
    { "causa": "Defective ignition coil pack", "probabilidad": "alta" },
    { "causa": "Damaged fuel injector", "probabilidad": "media" },
    { "causa": "Low compression (head gasket/valve)", "probabilidad": "baja" }
  ],
  "diagnostic_steps": [
    "1. Swap Cyl 2 spark plug with known good — retest ($3-$15)",
    "2. Swap Cyl 2 coil pack to another cylinder — see if misfire follows ($0)",
    "3. Check fuel injector resistance and spray pattern ($0-$50)",
    "4. Perform compression test on Cylinder 2 ($0-$30)"
  ],
  "cost_estimate": { "min": 20, "max": 800 },
  "time_hours": 1.5,
  "sources_count": 8
}
```

---

## 🎨 Design System

MEET uses a **"Neon Cyan" cyberpunk** design language:

| Token | Value | Usage |
|-------|-------|-------|
| `background` | `#0A0E1A` | App background |
| `surfaceCard` | `#111827` | Card surfaces |
| `electricBlue` | `#00FFD1` | Primary accent |
| `neonGreen` | `#39FF14` | Success / positive |
| `warning` | `#FFD700` | Caution states |
| `error` | `#FF3B3B` | Critical / danger |
| `textPrimary` | `#FFFFFF` | Primary text |
| `textSecondary` | `#8B95A5` | Secondary text |

---

## 📋 Changelog

### v3.3 Elite (Current) — May 2026
- ✅ **12,128 expert repair guides** from 10 global sources
- ✅ Urgency badges + drivability assessment per DTC
- ✅ Cost estimates + labor time per repair
- ✅ Ranked probable causes (High/Medium/Low)
- ✅ Professional card-based UI for all diagnostic results
- ✅ Manual DTC search with full elite rendering

### v3.0 Intelligence — May 2026
- Offline knowledge base foundation
- Mode 06 deep telemetry parser
- Predictive health engine

### v2.5 — May 2026
- Oscilloscope mode
- Component locator screen
- Supabase cloud sync

### v2.0 — April 2026
- Full DTC scanning (Active/Pending/Permanent)
- Freeze frame data capture
- AI diagnostic assistant (Gemini Pro)
- Triple transport support (BLE/BT/WiFi)

### v1.0 — April 2026
- Initial release
- Basic OBD-II communication
- Live sensor dashboard

---

## 🛡️ Security

- All API keys are stored in `.env` (excluded from git)
- Vehicle data is encrypted in transit via Supabase RLS
- No telemetry — your diagnostic data stays yours

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

<p align="center">
  <strong>Built with 🔧 by <a href="https://github.com/jordelmir">Jordelmir</a></strong><br/>
  <em>Making professional automotive diagnostics accessible to everyone.</em>
</p>
