# MEET — Mecánicos Especialistas En Todo (ELITE)

![MEET Platform](https://img.shields.io/badge/Platform-Web%20%7C%20Android-cyan.svg)
![Status](https://img.shields.io/badge/Status-Production%20Ready-success.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-Coroutines%20%7C%20Flow-blue.svg)
![React](https://img.shields.io/badge/React-Vite%20%7C%20TypeScript-blueviolet.svg)

**MEET (Mecánicos Especialistas En Todo)** is a professional-grade automotive diagnostic suite and workshop management platform. Engineered for high-fidelity communication with vehicle Engine Control Units (ECUs) and designed with a futuristic "Neon Cyan" aesthetic, MEET bridges the gap between hardware-level automotive telemetry and cloud-based management.

## 🚀 Ecosystem Architecture

The MEET platform is divided into three primary modules:

1. **MEET Android OBD2 Engine (`/android`)**
   A robust, Kotlin-based Android application that acts as the hardware bridge.
   - **Multi-Protocol Negotiator**: Advanced fallback strategies for establishing serial connections with standard and low-quality ELM327 Bluetooth clones.
   - **Resilient Telemetry**: Fault-tolerant command queue, active keep-alive watchdog, and asynchronous `StateFlow` data streams.
   - **Offline-First Storage**: Local caching using Room DB, ensuring no trip or diagnostic data is lost in dead-zones.

2. **MEET Web Portal (Root)**
   A React/Vite web application serving as the master control room for the mechanical workshop.
   - **Workshop CRM**: Client, vehicle, and work-order management.
   - **Diagnostic Vault**: Cloud-synchronized access to vehicle histories, DTC (Diagnostic Trouble Codes), and freeze frames.
   - **Premium UI/UX**: "MEET ELITE" branding featuring glassmorphism, cyan glowing highlights, and deep dark modes.

3. **MEET Cloud Backend**
   Powered by Supabase (PostgreSQL), providing secure, real-time synchronization between the mobile diagnostic units and the web portal.

## 🛠 Prerequisites

- **Web Portal**: Node.js (v18+), npm.
- **Android App**: Android Studio, Java 17+, Android SDK (Min SDK 26, Target SDK 34).
- **Hardware**: Bluetooth ELM327 OBD2 Adapter (v1.5+ recommended, clone-compatible).

## 💻 Getting Started

### Running the Web Portal Locally
```bash
# Install dependencies
npm install

# Start the Vite development server
npm run dev
```

### Compiling the Android Application
```bash
cd android

# Build the debug APK
./gradlew assembleDebug

# Install via ADB to connected device
./gradlew installDebug
```

## 📚 Documentation
For a deep dive into the OBD2 communication engine, hardware fallbacks, and the synchronization pipeline, please refer to the [Technical Documentation](DOCUMENTATION.md).

## 📄 License
Proprietary — All rights reserved. MEET Mecánicos Especialistas En Todo.
