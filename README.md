<p align="center">
  <img src="https://img.shields.io/badge/🔧_MEET-Mecánicos_Especialistas_En_Todo-00FFD1?style=for-the-badge&labelColor=0A0E1A" alt="MEET Elite"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-3.5_Elite-39FF14?style=flat-square" alt="Version"/>
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

**The most advanced open-source automotive diagnostic system ever built.** MEET ELITE v3.5 is a professional-grade OBD-II diagnostic suite engineered for mechanics, workshops, and enthusiasts who demand dealership-level intelligence without the dealership price tag.

> 🧠 **12,128 expert repair guides** cross-referenced from 10 global automotive authorities — more diagnostic depth than any commercial tool at any price.

---

## ⚡ What Makes MEET Different

MEET isn't just another code reader — it's a **complete vehicle intelligence platform** that replaces a $5,000+ dealer scan tool, a $50/month fleet management subscription, and a $200/year AI diagnostic service with a single app.

### 🏆 Competitive Matrix

| Capability | MEET ELITE | Torque Pro | OBD Fusion | BlueDriver | Dealer Tools |
|:-----------|:----------:|:----------:|:----------:|:----------:|:------------:|
| **Expert repair guides per DTC** | ✅ 12,128 | ❌ 0 | ❌ 0 | ~200 | ~500 |
| **Ranked probable causes** | ✅ High/Med/Low | ❌ | ❌ | ⚠️ Basic | ⚠️ |
| **Urgency + drivability assessment** | ✅ | ❌ | ❌ | ❌ | ⚠️ |
| **Cost estimates (USD + labor hrs)** | ✅ | ❌ | ❌ | ⚠️ Parts only | ❌ |
| **AI diagnostic assistant** | ✅ Gemini Pro | ❌ | ❌ | ❌ | ❌ |
| **Predictive health engine** | ✅ Mode 06 | ❌ | ❌ | ❌ | ⚠️ |
| **SWOT/FODA vehicle analysis** | ✅ Real-time | ❌ | ❌ | ❌ | ❌ |
| **Eco-driving analytics** | ✅ Score + trends | ⚠️ Basic | ❌ | ❌ | ❌ |
| **Maintenance scheduler + alerts** | ✅ | ❌ | ❌ | ⚠️ | ✅ |
| **Pre-purchase vehicle inspection** | ✅ 47-point | ❌ | ❌ | ⚠️ | ✅ |
| **DVIR fleet inspection reports** | ✅ | ❌ | ❌ | ❌ | ⚠️ |
| **HUD (Heads-Up Display) mode** | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Oscilloscope / waveform analysis** | ✅ | ❌ | ❌ | ❌ | ✅ |
| **Readiness monitor drive-cycle guides** | ✅ Per monitor | ❌ | ❌ | ❌ | ⚠️ |
| **OBD terminal (raw commands)** | ✅ | ✅ | ✅ | ❌ | ✅ |
| **Android Terminal (Termux substitute)** | ✅ 100% Real | ❌ | ❌ | ❌ | ❌ |
| **PDF diagnostic report export** | ✅ | ❌ | ⚠️ | ✅ | ✅ |
| **Multi-transport (BLE/BT/WiFi)** | ✅ All 3 | ⚠️ BT only | ⚠️ WiFi only | BLE only | Proprietary |
| **Bilingual (EN/ES)** | ✅ Native | ❌ | ❌ | ❌ | ⚠️ |
| **Cloud sync + web dashboard** | ✅ Supabase | ❌ | ❌ | ⚠️ | ✅ |
| **Open source** | ✅ MIT | ❌ | ❌ | ❌ | ❌ |
| **Price** | **$1/mo** | $6.99 once | $12.99 once | $6/mo | $5,000+ |

> 💡 **Bottom line:** MEET delivers more diagnostic intelligence in a $1/month subscription than tools costing 5,000x more. No other app combines AI diagnostics, SWOT analysis, eco-driving telemetry, fleet inspections, and 12,128 expert repair guides in a single platform.

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────┐
│                    MEET ELITE v3.5                   │
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
- **Readiness Monitor Dashboard** — I/M emission monitor status with SAE J1979 drive-cycle guides per monitor
- **Service Resets** — Oil life, TPMS, SAS, DPF, battery registration and more

### 🧠 AI-Powered Diagnostics
- **Gemini Pro Integration** — Context-aware AI that understands your specific vehicle, active codes, and sensor data
- **Natural Language Queries** — Ask diagnostic questions in plain language (EN/ES)
- **Intelligent Synthesis** — Combines live data + knowledge base + AI for unmatched accuracy
- **BYOK (Bring Your Own Key)** — Users provide their own Gemini API key for unlimited AI queries

### 📊 Real-Time Monitoring
- **Live Sensor Dashboard** — RPM, speed, temperatures, fuel trims, O2 sensors with animated gauges
- **Custom Dashboard Builder** — Drag-and-drop gauge layout editor
- **Oscilloscope Mode** — Multi-channel waveform analysis for sensor signals
- **Mode 06 Deep Telemetry** — Test results with pass/fail thresholds
- **Data Logger** — Record and export sensor sessions to CSV
- **HUD Mode** — Heads-Up Display for windshield projection at night

### 🔧 Maintenance Pro
- **Maintenance Scheduler** — Custom alerts with km-based intervals (oil, filters, brakes, etc.)
- **FODA Vehicular Analysis** — Real-time SWOT dashboard correlating DTCs, battery voltage, coolant temp, and eco-score
- **Odometer Tracking** — Auto-updates from live OBD data

### 🚗 Eco-Trips & Driving Analytics
- **Trip Recording** — Automatic telemetry capture (distance, duration, speed, RPM, temperature)
- **Eco Score Gauge** — Real-time efficiency rating using interactive circular Canvas-rendered gauge
- **Carbon Footprint Tracking** — Automatic CO2 calculation based on EPA standards (Gasoline/Diesel)
- **Wasted Idle Calculator** — Financial & volumetric tracking of wasted fuel during vehicle idling
- **Alternator & Battery Analytics** — Continuous plotting of min/max alternator voltage trends across past 10 trips
- **FODA de Conducción** — SWOT analysis of driving habits (acceleration patterns, RPM discipline, thermal stress)
- **Interactive Bottom Sheet** — Comprehensive telemetry overview per historical trip
- **Mock Trip Simulator** — Instant demo generation for app showcase
- **PDF Export** — Professional, high-fidelity trip reports (Premium)
- **Database Auto-Pruning** — Background worker to prune logs and trip history older than 90 days

### 🏥 Predictive Health
- **Vehicle Health Score** — Algorithmic assessment based on sensor trends
- **Battery Health Analyzer** — Voltage monitoring, cranking analysis, alternator load
- **Smog Check Predictor** — Pre-test probability based on readiness monitors and pending DTCs
- **Proactive Alerts** — Custom threshold engine that warns before failures occur

### 🚛 Fleet & Professional Tools
- **Pre-Purchase Inspection** — 47-point vehicle assessment for used car buyers
- **DVIR (Driver Vehicle Inspection Report)** — DOT-compliant fleet condition reports
- **LiveLink** — Real-time telemetry broadcast to web dashboard viewers
- **Fleet Chat** — In-app messaging between fleet drivers and dispatchers
- **Active Tests** — Bi-directional commands for component actuation
- **OBD Terminal** — Raw AT/OBD command console with response parsing
- **Android Terminal (Termux Substitute)** — 100% real interactive Android shell (`/system/bin/sh`) with Kotlin Coroutine streams, CRT scanlines, and diagnostic quick-action chips (`uname`, `df`, `pm`, `getprop`, `netstat`, `top`, `logcat`)

### 🔗 Connectivity
- **Triple Transport** — Bluetooth Classic, BLE 5.0, WiFi TCP
- **Auto-Detection** — Automatic ELM327 adapter fingerprinting and clone detection
- **Keep-Alive Manager** — Maintains stable connections during long sessions
- **CAN Multi-Frame Parser** — Handles ISO-TP segmented responses
- **USB Oscilloscope Support** — Hantek 6022BE driver for hardware waveform capture

### ☁️ Cloud & Sync
- **Supabase Backend** — Real-time cloud sync for diagnostic history
- **Google Drive Backup** — Automatic encrypted database backup to user's Drive
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
│       │   │   ├── obd/            # OBD-II engine (Session, Decoder, ELM, Mode06,
│       │   │   │                   #   DemoSimulator, SignalAnalyzer, VinDecoder,
│       │   │   │                   #   FuelTracker, MaintenancePredictor, SmogCheck)
│       │   │   ├── ai/             # Gemini AI diagnostic integration
│       │   │   ├── alerts/         # Custom threshold alert engine
│       │   │   ├── audio/          # Voice recorder & playback (fleet chat)
│       │   │   ├── backup/         # Google Drive encrypted backup worker
│       │   │   ├── export/         # PDF report generation
│       │   │   ├── health/         # Predictive health engine
│       │   │   ├── livelink/       # Real-time telemetry broadcast server
│       │   │   ├── sync/           # Supabase cloud sync workers
│       │   │   ├── transport/      # BLE, Bluetooth Classic, WiFi transports
│       │   │   ├── trips/          # Trip manager & eco-score calculator
│       │   │   ├── usb/            # Hantek 6022BE oscilloscope USB driver
│       │   │   └── utils/          # File utilities & helpers
│       │   ├── data/
│       │   │   ├── local/          # Room DB, DAOs, Entities, KnowledgeBase
│       │   │   ├── remote/         # Remote repository interfaces
│       │   │   └── supabase/       # Supabase client & cloud operations
│       │   ├── ui/
│       │   │   ├── screens/        # 34 screens (DTC, Scanner, Maintenance,
│       │   │   │                   #   Trips, HUD, DVIR, PrePurchase, etc.)
│       │   │   ├── components/     # Elite UI (cards, gauges, animations, graphs)
│       │   │   └── theme/          # Neon Cyan cyberpunk design system
│       │   ├── di/                 # Dependency injection (Hilt / Dagger)
│       │   └── widget/             # Home screen widget (MeetWidget)
│       └── assets/
│           ├── dtc_offline_solutions.json   # 12,128 expert guides (29MB)
│           └── common_fixes.json            # Quick-fix lookup table
├── components/                     # Web app shared components (React/TS)
├── services/                       # Web app services (signal analysis, etc.)
├── src/                            # Web app entry (React/Vite/TypeScript)
├── privacy-policy-site/            # Privacy policy static page
├── releases/                       # Pre-built APKs & AABs
│   └── MEET_latest_debug.apk      # ← Latest release (v3.5)
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
adb install releases/MEET_latest_debug.apk
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

MEET uses a **"Neon Cyan" cyberpunk** design language with glassmorphism cards, animated gradients, and micro-interactions:

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

### v3.5 Elite (Current) — June 2026
- ✅ **Android Terminal (Termux Substitute)** — Fully integrated interactive `/system/bin/sh` shell running asynchronously via Kotlin Coroutines. Features quick-execution chips (`uname`, `df`, `pm`, `getprop`, `netstat`, `top`, `logcat`), CRT scanline effects, and clean log exports (copy/share).
- ✅ **Advanced Statistics & Telemetry Dashboard** — Next-level analytics tab featuring:
  - **EcoScore Radial Gauge** with dynamic Canvas arcs & EPA-based CO2 emissions calculator.
  - **Wasted Idle Calculator** detailing fuel volume and financial loss from idle time.
  - **Live Canvas Graphs** for real-time speed and RPM tracking.
  - **Alternator Health Trends** plotting battery voltage min/max history over the last 10 trips.
  - **DTC Efficiency Alert Banner** warning users of open-loop rich fueling due to active fault codes.
  - **Interactive Bottom Sheet** for detailed historical trip inspection.
  - **PDF Export Engine** to generate and share high-fidelity trip reports.
- ✅ **Unit Toggles & Persistent Settings** — Interactive Metric/Imperial converter (km/mi, km/h/mph, L/gal, L/100km/MPG, °C/°F) and configurations for local fuel price, currency, and fuel type.
- ✅ **Database Auto-Pruning** — Background worker to clean up diagnostic logs and trip histories older than 90 days.

### v3.4 Elite — May 2026
- ✅ **Maintenance Pro Module** — Interactive alerts dashboard with FODA vehicular analysis
- ✅ **Eco-Trips Module** — Driving telemetry with SWOT analysis of driving habits
- ✅ **Eco Score Gauge** — Circular Canvas-rendered efficiency gauge per trip
- ✅ **Mock Trip Simulator** — Instant demo generation for prospective buyers
- ✅ **Pre-Purchase 47-Point Inspection** — Complete vehicle assessment for used car buyers
- ✅ **DVIR Fleet Inspection** — DOT-compliant vehicle condition reports
- ✅ **HUD Mode** — Heads-Up Display for windshield projection
- ✅ **Readiness Monitor Drive Cycle Guides** — SAE J1979 professional instructions per monitor

### v3.3 Elite — May 2026
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

## 🛡️ Security & Privacy

- **BYOK Model** — Users provide their own Gemini API key; MEET never stores or proxies API credentials
- All secrets are stored in `.env` (excluded from git via `.gitignore`)
- Vehicle data is encrypted in transit via Supabase RLS (Row-Level Security)
- Google Drive backups are encrypted before upload
- No analytics, no tracking, no telemetry — your diagnostic data stays **yours**
- Full [Privacy Policy](privacy-policy-site/index.html) published for Play Store compliance

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

<p align="center">
  <strong>Built with 🔧 by <a href="https://github.com/jordelmir">Jordelmir</a></strong><br/>
  <em>Making professional automotive diagnostics accessible to everyone.</em><br/><br/>
  <a href="https://github.com/jordelmir/MEET-Mecanicos-Especialistas-En-Todo/releases/latest">
    <img src="https://img.shields.io/badge/⬇️_Download_Latest_APK-39FF14?style=for-the-badge&labelColor=0A0E1A" alt="Download"/>
  </a>
</p>
