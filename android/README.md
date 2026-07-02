# 🔧 MEET OBD-II — Android Native App Core (v3.4 Elite)

Welcome to the native Android core module of **MEET (Mecánicos Especialistas En Todo)**. This directory contains the complete Kotlin-based mobile application that interfaces directly with vehicle ECUs using ELM327 adapters (Bluetooth, BLE, and WiFi) and USB oscilloscopes.

Built from the ground up with **Jetpack Compose, Kotlin Coroutines, Flow, Room Database, and Dagger Hilt**, this module provides dealership-level diagnostics, predictive analytics, and real-time telemetry rendering.

---

## 🏗️ Core Architecture & Subsystems

MEET Android is structured around a low-latency reactive pipeline designed to handle the challenges of noisy OBD-II networks and inconsistent clone adapter behaviors.

```
                  ┌─────────────────────────────────────────┐
                  │          Jetpack Compose UI             │
                  │   (34 Elite Screens / Canvas Gauges)    │
                  └────────────────────┬────────────────────┘
                                       │ (StateFlow / UI State)
                  ┌────────────────────▼────────────────────┐
                  │             Dagger Hilt DI              │
                  │    (ViewModels & Core Injectors)        │
                  └────────────────────┬────────────────────┘
                                       │ (Reactive Commands)
                  ┌────────────────────▼────────────────────┐
                  │       OBD-II Engine Layer (Core)        │
                  │  ┌───────────────────────────────────┐  │
                  │  │ ObdSession (Thread-Safe Queue)    │  │
                  │  ├───────────────────────────────────┤  │
                  │  │ ElmNegotiator (Auto-Baud / Filter)│  │
                  │  ├───────────────────────────────────┤  │
                  │  │ CanMultiFrameParser (ISO-TP)      │  │
                  │  └───────────────────────────────────┘  │
                  └────────────────────┬────────────────────┘
                                       │ (Raw Socket/Serial IO)
                  ┌────────────────────▼────────────────────┐
                  │     Hardware Transports (Abstraction)    │
                  │  ┌──────────────┬───────────┬────────┐  │
                  │  │ Classic BT   │    BLE    │  WiFi  │  │
                  │  │ (Rfcomm)     │  (GATT)   │ (TCP)  │  │
                  │  └──────────────┴───────────┴────────┘  │
                  └─────────────────────────────────────────┘
```

### 1. The OBD-II Communication Engine (`core/obd/`)
*   **Thread-Safe Command Queue (`ObdSession.kt`)**: Implements an asynchronous command loop using Kotlin Channels. Ensures that OBD commands are executed sequentially to prevent message collision and buffer overflow on slow ELM327 processors.
*   **Baud Rate & Clone Negotiator (`ElmNegotiator.kt`)**: Automatically detects the adapter's hardware baud rate, resets the buffer, and applies optimal protocol parameters (headers off, spaces off, echoes off). It filters out protocol noise, enabling compatibility with Chinese ELM327 clones (v1.5 and v2.1).
*   **ISO-TP Multi-Frame Parser (`CanMultiFrameParser.kt`)**: Reconstructs segmented OBD-II responses (multi-frame CAN data) by tracking flow control frames, allowing the app to query large diagnostic payloads like VIN numbers, custom PIDs, and freeze frame data.
*   **Predictive Diagnostics & Analytics**:
    *   `BatteryHealthAnalyzer.kt`: Captures cranking voltage dips and alternator charging ripple.
    *   `FuelEconomyTracker.kt`: Dynamically calculates instantaneous MPG / L-100km using Mass Air Flow (MAF) or Manifold Absolute Pressure (MAP) sensors.
    *   `SmogCheckPredictor.kt`: Inspects readiness monitor parameters to verify emission test compliance.
    *   `MaintenancePredictor.kt` & `MaintenanceAdvisor.kt`: Analyzes vehicle DTC history and operating conditions to recommend scheduled maintenance.

### 2. Fleet & Remote Diagnostics Subsystem
*   **Ktor Telemetry Server (`core/livelink/`)**: An embedded Ktor WebSocket server. When active, it broadcasts live vehicle telemetry (RPM, speed, engine load) to any local or remote web browser client via a dashboard interface.
*   **Fleet Chat & Audio Services (`core/audio/` & `ui/screens/chat/`)**: Supports voice recording, compression, and playback for dispatchers and drivers using a native audio pipeline.
*   **Google Drive Backup (`core/backup/`)**: An encrypted backup worker that packages the local Room database and uploads it to the user's personal Google Drive account.

---

## 🗂️ Code Repository Structure

```
android/app/src/main/kotlin/com/elysium369/meet/
│
├── core/
│   ├── ai/            # Gemini AI models & API integrations
│   ├── alerts/        # Real-time threshold engine for custom PID warnings
│   ├── audio/         # Compressed voice record & playback for Fleet Chat
│   ├── backup/        # Google Drive database backup worker
│   ├── export/        # Dynamic PDF diagnostic report generator
│   ├── health/        # Predictive vehicle health algorithm
│   ├── livelink/      # Embedded Ktor WebSockets server for telemetry streaming
│   ├── obd/           # OBD-II physical protocol, parsing, and simulators
│   ├── sync/          # Cloud sync workers linking to Supabase RLS
│   ├── transport/     # Classic Bluetooth, BLE GATT, and WiFi TCP sockets
│   ├── trips/         # Trip recorders, GPS logs, and Eco-driving metrics
│   ├── usb/           # Hantek 6022BE USB Oscilloscope driver
│   └── utils/         # Helper classes (file sharing, crypto, dates)
│
├── data/
│   ├── local/         # Room Database configuration (Entities & DAOs)
│   ├── remote/        # Supabase API clients and web endpoints
│   └── supabase/      # Real-time remote cloud repository
│
├── di/                # Dependency Injection Modules (Dagger Hilt)
│   ├── AppModule.kt   # System services, Room DB, and repository bindings
│   └── ObdModule.kt   # OBD sessions, services, and hardware controllers
│
├── ui/
│   ├── components/    # Cyberpunk design system widgets (Gauges, Canvas graphs)
│   ├── screens/       # 34 Jetpack Compose screen layouts
│   └── theme/         # Color palettes (Neon Green, Deep Navy, Electric Blue)
│
└── widget/            # Android Home Screen widgets (MeetWidget)
```

---

## 🚀 Setup & Build Instructions

### Prerequisites
*   **Android Studio Ladybug+** (supporting Kotlin 2.1+)
*   **Java Development Kit (JDK) 17**
*   **Android SDK 35** (Platform tools & build tools installed)

### 1. Project Configuration
Ensure you open the project by pointing Android Studio to the **`android/` directory specifically**, rather than the repository root.

Create a `local.properties` file in the `android/` directory and configure the environment variables:
```properties
MEET_SUPABASE_URL=https://your-supabase-project.supabase.co
MEET_SUPABASE_KEY=your-supabase-anonymous-key
```

### AI Provider Configuration
The APK supports user-supplied AI APIs from inside `Ajustes > Motor de Inteligencia Artificial`.

Supported providers:
* `gemini` — Gemini native generateContent.
* `openai` — OpenAI Chat Completions compatible.
* `anthropic` — Anthropic Messages API.
* `ollama` — local OpenAI-compatible endpoint.
* `mavis` — configurable Mavis/vendor endpoint using OpenAI-compatible payloads.
* `custom` — any compatible third-party API.

Do not commit real API keys. Use the in-app settings for user keys or ignored local files for developer testing. Remote AI calls bypass stale local DTC cache when configured; offline/local expert fallback remains available and is labeled as local.

### 2. Building from Command Line
Run the following Gradle wrapper commands:

```bash
# Clean the project build directories
./gradlew clean

# Compile and build the debug APK
./gradlew assembleDebug
# Resulting file: app/build/outputs/apk/debug/app-debug.apk

# Compile and build the release AAB (App Bundle) for Play Store
./gradlew bundleRelease
# Resulting file: app/build/outputs/bundle/release/app-release.aab
```

### 3. Monetization Mode
The current APK build is intentionally open while the product is being polished:

```kotlin
MonetizationPolicy.PAYWALLS_ENABLED = false
```

This grants local full access to PRO features, trip PDF exports, MEET Perito certification surfaces, and gauge marketplace previews/apply actions without launching Google Play Billing. Billing, Supabase verification, and entitlement tables remain in the codebase so production monetization can be restored later by turning the policy back on and publishing the Play products.

---

## 🧪 Hardware Compatibility & Tests

The core OBD communication stack has been exhaustively tested against various hardware setups:

| Hardware Class | Transport | Protocol Support | Performance Rating | Recommended Use Case |
| :--- | :--- | :--- | :--- | :--- |
| **vLinker MC+ / FD+** | Bluetooth / BLE | CAN, K-Line, J1850 | 🌟 🌟 🌟 🌟 🌟 (Excellent) | High-speed PID polling, graph analysis |
| **Vgate iCar Pro** | BLE / WiFi | CAN, ISO9141 | 🌟 🌟 🌟 🌟 (Great) | Daily driving telemetry, Eco-Trips |
| **OBDLink MX+** | Bluetooth | CAN, SW-CAN, MS-CAN | 🌟 🌟 🌟 🌟 🌟 (Excellent) | Ford/GM proprietary diagnostic sessions |
| **ELM327 Chinese Clones (v1.5)** | Bluetooth / WiFi | Standard OBD-II | 🌟 🌟 🌟 (Good) | Basic DTC scanning, sensor reading |
| **ELM327 Chinese Clones (v2.1)** | Bluetooth | Standard OBD-II | 🌟 🌟 (Fair) | Use only with `ElmNegotiator` recovery active |
| **Hantek 6022BE** | USB OTG | Raw Oscilloscope | 🌟 🌟 🌟 🌟 (Great) | Physical sensor signal logging (O2, CAM, CRANK) |

---

## 🎨 Design System Tokens

MEET implements a tailored dark-themed cyberpunk user interface. The color tokens defined in `theme/MeetColors.kt` are:

*   **Deep Background**: `#0A0E1A` — High contrast reduction of eye strain in garage environments.
*   **Card Surface**: `#111827` — Semi-transparent glassmorphic panels.
*   **Neon Cyan**: `#00FFD1` — Brand highlights, active states, and connection success.
*   **Neon Green**: `#39FF14` — Premium system indicator and successful verification.
*   **Neon Orange / Yellow**: `#FFD700` — Warnings, pending DTC indicators, and upcoming maintenance.
*   **Electric Red**: `#FF3B3B` — Overdue maintenance alerts and active diagnostic trouble codes.

---
*Developed with dedication by the MEET Engineering Group.*
