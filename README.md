<p align="center">
  <img src="https://img.shields.io/badge/🔧_MEET-Mecánicos_Especialistas_En_Todo-00FFD1?style=for-the-badge&labelColor=0A0E1A" alt="MEET Elite"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_%7C_Web-00BCD4?style=flat-square&logo=android&logoColor=white" alt="Platform"/>
  <img src="https://img.shields.io/badge/Status-Production_Ready-39FF14?style=flat-square" alt="Status"/>
  <img src="https://img.shields.io/badge/Kotlin-2.1_%7C_Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/React-Vite_%7C_TypeScript-61DAFB?style=flat-square&logo=react&logoColor=black" alt="React"/>
  <img src="https://img.shields.io/badge/AI-Multi--Provider-CC00FF?style=flat-square" alt="AI"/>
  <img src="https://img.shields.io/badge/Cloud-Supabase-3ECF8E?style=flat-square&logo=supabase&logoColor=white" alt="Supabase"/>
  <img src="https://img.shields.io/badge/Deploy-Vercel-000000?style=flat-square&logo=vercel&logoColor=white" alt="Vercel"/>
</p>

---

# MEET — Mecánicos Especialistas En Todo

**MEET ELITE** is a professional-grade automotive diagnostic suite and workshop management platform. Engineered for high-fidelity communication with vehicle Engine Control Units (ECUs) via OBD2 protocols, and designed with a futuristic **"Neon Cyan"** cyberpunk aesthetic.

> **Latest Release**: `v2.5.0` — Multi-Provider AI Engine, Force Clone Mode, Professional Terminal  
> **APK Download**: See [Releases](https://github.com/jordelmir/MEET-Mecanicos-Especialistas-En-Todo/releases)

---

## 🏗️ Ecosystem Architecture

```
┌─────────────────────────────────────────────────────┐
│                  MEET ELITE PLATFORM                │
├─────────────┬──────────────────┬────────────────────┤
│  📱 Android │   🌐 Web Portal  │  ☁️  Cloud Backend │
│  OBD2 Engine│   Workshop CRM   │  Supabase + AI     │
├─────────────┼──────────────────┼────────────────────┤
│ Kotlin/     │ React + Vite +   │ PostgreSQL (RLS)   │
│ Compose +   │ TypeScript       │ Real-time Sync     │
│ Hilt DI     │ Neon Cyan UI     │ 2,300+ DTC Codes   │
│ Room DB     │ Recharts         │ OEM PID Vault      │
│ Multi-      │ Glassmorphism    │ Stripe Billing     │
│ Transport   │ Analytics        │                    │
└─────────────┴──────────────────┴────────────────────┘
```

### 1. 📱 MEET Android OBD2 Engine (`/android`)

The core diagnostic unit — a native Kotlin Android application.

| Feature | Description |
|---------|-------------|
| **Multi-Protocol Negotiator** | Automatic fallback through ISO 9141, ISO 14230 (KWP2000), ISO 15765 (CAN), SAE J1850 protocols |
| **Clone Adapter Compatibility** | Force Clone Mode overrides adapter detection for generic ELM327 v1.5/v2.1 adapters |
| **Multi-Transport Layer** | WiFi, Bluetooth Classic, and BLE support with automatic reconnection |
| **Real-Time Telemetry** | Live gauges (RPM, Speed, Temps, Fuel) via Kotlin `StateFlow` streams |
| **Multi-Provider AI Engine** | Diagnostic AI supporting **Gemini, OpenAI, Anthropic, Ollama, and Custom endpoints** |
| **Professional Terminal** | Raw OBD2 command interface with timestamps, color-coded responses, quick commands |
| **Clone Detection Tests** | Automated adapter quality verification with PASS/WARN/FAIL dashboard |
| **DTC Analysis** | 2,300+ diagnostic trouble codes with AI-powered root cause analysis |
| **Offline-First** | Room DB local cache — zero data loss in dead zones |
| **Cloud Sync** | Background sync via WorkManager to Supabase cloud |

### 2. 🌐 MEET Web Portal (Root `/`)

A React/Vite web application serving as the workshop control center.

| Feature | Description |
|---------|-------------|
| **Workshop CRM** | Client, vehicle, and work-order lifecycle management |
| **Diagnostic Vault** | Cloud-synced vehicle histories, DTC reports, freeze frames |
| **Analytics Dashboard** | Revenue, completion rates, and service performance metrics |
| **Service Manager** | Real-time work order tracking with status workflows |
| **Premium UI/UX** | MEET ELITE branding — glassmorphism, neon glows, dark mode |

### 3. ☁️ MEET Cloud Backend

| Component | Technology |
|-----------|-----------|
| **Database** | Supabase (PostgreSQL) with Row-Level Security |
| **DTC Database** | 2,300+ codes across P0xxx-U3xxx ranges |
| **OEM PID Vault** | Manufacturer-specific PIDs (Toyota, GM, Ford, BMW, VAG) |
| **Auth** | Supabase Auth with JWT + role-based access |
| **Real-time** | Supabase Realtime for live sync |

---

## 🚀 Quick Start

### Prerequisites

| Component | Requirement |
|-----------|------------|
| Web Portal | Node.js 18+, npm |
| Android App | Android Studio, Java 17+, SDK 26+ (Target 34) |
| Hardware | ELM327 OBD2 Adapter (WiFi/BT/BLE) |

### Web Portal

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Production build
npm run build
```

### Android Application

```bash
cd android

# Build debug APK
./gradlew assembleDebug

# Install to device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🤖 AI Configuration

MEET supports **any AI provider** via the Settings screen:

| Provider | Endpoint | Auth |
|----------|----------|------|
| **Google Gemini** | Built-in | API Key from [aistudio.google.com](https://aistudio.google.com) |
| **OpenAI** | `https://api.openai.com/v1/chat/completions` | Bearer Token |
| **Anthropic** | `https://api.anthropic.com/v1/messages` | `x-api-key` header |
| **Ollama** | `http://localhost:11434/v1/chat/completions` | None (local) |
| **Custom** | Any OpenAI-compatible URL | Bearer Token |

> Configure in the app: **Settings → Inteligencia Artificial → Select Provider → Enter API Key → Save**

---

## 📁 Project Structure

```
MEET-Mecanicos-Especialistas-En-Todo/
├── android/                    # Native Android OBD2 App
│   └── app/src/main/kotlin/
│       ├── core/
│       │   ├── ai/             # Multi-provider AI diagnostic engine
│       │   ├── obd/            # OBD2 session, PID registry, protocol negotiation
│       │   ├── transport/      # WiFi, BT Classic, BLE transport layers
│       │   ├── sync/           # Background cloud sync (WorkManager)
│       │   └── trips/          # Trip recording & analysis
│       ├── data/
│       │   ├── local/          # Room DB entities, DAOs, migrations
│       │   └── remote/         # Supabase cloud sync repository
│       ├── di/                 # Hilt dependency injection modules
│       └── ui/
│           ├── screens/        # All Compose UI screens
│           ├── components/     # Reusable gauge, graph, card widgets
│           └── theme/          # MEET Elite design system
├── components/                 # React web components
├── lib/                        # Supabase client, API utilities
├── releases/                   # APK distribution
├── App.tsx                     # Main web app entry
├── index.html                  # Web entry point
├── vite.config.ts              # Vite configuration
└── package.json                # Web dependencies
```

---

## 🔐 Environment Variables

Create a `.env` file in the project root:

```env
VITE_SUPABASE_URL=https://your-project.supabase.co
VITE_SUPABASE_ANON_KEY=your-anon-key
```

---

## 📦 Releases

Pre-built APKs are available in the [GitHub Releases](https://github.com/jordelmir/MEET-Mecanicos-Especialistas-En-Todo/releases) page.

| Version | Highlights |
|---------|-----------|
| **v2.5.0** | Multi-Provider AI, Force Clone Mode, Professional Terminal, UI Hardening |
| **v2.2.0** | OBD stability, PDF reports, cloud sync |
| **v2.0.0** | MEET Elite rebrand, premium UI, Supabase integration |

---

## 🌐 Live Deployment

- **Web Portal**: Deployed on [Vercel](https://vercel.com) — auto-deploys from `main` branch
- **Cloud Backend**: [Supabase](https://supabase.com) — PostgreSQL with real-time capabilities

---

## 📄 License

Proprietary — All rights reserved.  
**MEET Mecánicos Especialistas En Todo** © 2024-2026
