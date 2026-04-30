# MEET ELITE — Technical Architecture & Documentation

## 1. System Overview

The MEET platform operates on a localized edge-to-cloud architecture. The **Android Application** functions as the edge device, running computationally heavy tasks (protocol negotiation, stream decoding) locally. The **Web Application** serves as the administrative cloud interface, consuming data synchronized via **Supabase**.

---

## 2. Android OBD2 Engine Architecture

The core of the Android application is a highly resilient OBD2 communication engine, designed to handle extreme variance in hardware quality (specifically ELM327 clones).

### 2.1. Bluetooth Transport Layer (`BtClassicTransport.kt`)
Cloned ELM327 devices often feature corrupted or non-standard Bluetooth firmware. To ensure a connection, MEET implements a **Multi-Method Fallback Strategy**:
1. **Standard SPP (Serial Port Profile)**: Uses standard `UUID 00001101-0000-1000-8000-00805F9B34FB`.
2. **Channel 1 Reflection**: Uses Java Reflection to force connection on Bluetooth Channel 1 (highest success rate for Chinese clones).
3. **Insecure RFCOMM**: Bypasses strict Android pairing requirements for legacy adapters.

### 2.2. Session & Queue Management (`ObdSession.kt`)
The session manages state (`DISCONNECTED`, `CONNECTING`, `NEGOTIATING`, `CONNECTED`, `ERROR`) using Kotlin `StateFlow`.
- **Command Queue (`ObdCommandQueue`)**: Commands are queued asynchronously. A dedicated background coroutine processes them sequentially to prevent bus collisions.
- **Strict I/O Handling**: Bluetooth sockets on clones often report `available() == 0` even when data is in transit. The engine uses a custom blocking-read loop with 15ms-25ms CPU yields to prevent dropping bytes.
- **Fatal Error Recovery**: If an `IOException` occurs on write (Broken Pipe), the session immediately triggers `notifyChannelDead()`, gracefully halting the queue and updating the UI instead of endlessly retrying.

### 2.3. Protocol Negotiation (`ElmNegotiator.kt` & `ObdSession.kt`)
When a generic `ATSP0` (Auto Search) command fails on low-end adapters, MEET performs an aggressive manual protocol forcing:
- **Attempt 1 & 2**: Standard automatic handshake.
- **Attempt 3**: Forced `ATSP6` (CAN 11-bit / 500K) — The standard for >90% of post-2008 vehicles.
- **Attempt 4**: Forced `ATSP7` (CAN 29-bit / 500K) — Heavy-duty and specific OEM protocols.
- **Fallback**: Iterates through ISO and SAE legacy protocols.

### 2.4. Anti-Timeout Protection (`KeepAliveManager.kt`)
Many ECUs and ELM chips enter a sleep state if no commands are sent for 2-3 seconds. The `KeepAliveManager` monitors the read buffer. If 1800ms pass without traffic, it injects an `0100` ping directly into the transmission stream, bypassing the queue to keep the CAN bus awake.

---

## 3. Data Flow & Persistence

### 3.1. Live Telemetry Pipeline
- Commands are dispatched to request PIDs (e.g., `01 0C` for RPM).
- Responses are cleaned (removing `\r`, `>`, and `SEARCHING` artifacts).
- The hex payloads are routed to `DtcDecoder` and emitted to the `ObdSession.liveData` map as `Float` values.
- `ObdForegroundService` collects this `StateFlow` to update the Android Notification bar in real-time, even when the app is backgrounded.

### 3.2. Trip Analytics (`TripManager.kt`)
The `TripManager` listens to the telemetry stream and tracks:
- **Duration & Distance**
- **Peak Metrics** (Max RPM, Max Speed, Max Temperature).
- **Eco-Score Engine**: Analyzes harsh accelerations (speed delta > 10km/h per 2s) and high RPM percentages to grade driving behavior mathematically.

### 3.3. Database & Cloud Synchronization
- **Local (Room DB)**: Vehicles and Sessions are immediately written to an encrypted local SQLite database.
- **Remote (Supabase)**: `SupabaseClient` manages the synchronization layer. When a trip ends, the `SessionLogRepository` marshals the JSON snapshot of the diagnostic data and pushes it to PostgreSQL via the PostgREST API.

---

## 4. UI/UX Design System (MEET ELITE)

Both platforms strictly adhere to the "Neon Cyan" futuristic design language.
- **Color Palette**: Dark charcoal backgrounds (`#0a0a0a`), deep blues (`#111827`), and striking cyan accents (`#00f0ff` / `rgb(6, 182, 212)`).
- **Glassmorphism**: Translucent panels with background blur to convey a sophisticated, high-tech interface.
- **Feedback**: Immediate visual reactivity. Buttons feature glow-on-hover, and connection statuses pulse (Connecting: Yellow, Connected: Green/Cyan, Error: Red).

---
 
## 5. PDF Report Generation & AI Diagnostics (v2.2.0)
 
MEET v2.2.0 introduces the **Elite Diagnostic Report**, a professional-grade PDF document generated directly on the device using the Android `PdfDocument` API.
 
### 5.1. Waveform Analysis Engine
- **Real-time Plotting**: The engine converts telemetry history into high-fidelity SVG-like paths on the PDF canvas.
- **AI-Driven Anomaly Highlighting**: If MEET AI detects a non-linear behavior or sensor drift, the specific graph is automatically rendered in **Neon Red** with an "AI DETECTED" tag and a predictive insight summary.
- **Multi-Page Management**: A dynamic pagination system ensures that long telemetry streams and multiple detected DTCs are gracefully distributed across pages with consistent headers and footers.
 
### 5.2. MEET AI Elite Predictor
- **Predictive Health Score**: A weighted algorithm calculates a score from 0-100 based on active DTCs, pending codes, and live sensor anomalies.
- **Anomalous PID Detection**: Using Gemini-integrated analysis, the system identifies "silent" failures (e.g., fuel trims trending lean before a P0171 is set) and provides actionable repair recommendations.
 
---
 
## 6. Production Workflow & Deployment
 
### 6.1. Build & Versioning
- **Android Build**: Compiled via `./gradlew assembleDebug` for high-performance testing.
- **Binary Storage**: Latest stable APKs are archived in the `/releases` directory of the repository for internal distribution.
- **Version Sync**: The project uses unified versioning across Android and Web components (v2.2.0-stable).
 
### 6.2. CI/CD Integration
- **Git Flow**: All feature patches are synchronized to the `main` branch.
- **Verification**: Every production-grade build undergoes a local compilation check to ensure logic integrity before being pushed to the global repository.
