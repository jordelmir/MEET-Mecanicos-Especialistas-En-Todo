<p align="center">
  <img src="https://img.shields.io/badge/🔧_MEET-Mecánicos_Especialistas_En_Todo-00FFD1?style=for-the-badge&labelColor=0A0E1A" alt="MEET"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-00BCD4?style=flat-square&logo=android&logoColor=white" alt="Platform"/>
  <img src="https://img.shields.io/badge/Kotlin-2.1_%7C_Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/License-MIT-39FF14?style=flat-square" alt="License"/>
  <img src="https://img.shields.io/badge/Status-Production-39FF14?style=flat-square" alt="Status"/>
</p>

---

# MEET — Mecánicos Especialistas En Todo

Aplicación Android de diagnóstico automotriz OBD-II de código abierto. Permite leer datos en tiempo real de la ECU, diagnosticar códigos de falla (DTC), registrar viajes y llevar historial de mantenimiento a través de un adaptador ELM327.

---

## Arquitectura

```
┌─────────────────────────────────────────────────────┐
│                       MEET                          │
├─────────────┬───────────────┬───────────────────────┤
│  Android    │   Web App     │   Cloud               │
│  (Kotlin/   │   (React/     │   (Supabase)          │
│   Compose)  │    Vite/TS)   │                       │
├─────────────┴───────────────┴───────────────────────┤
│                   Core Engine                        │
│  ┌──────────┐ ┌───────────┐ ┌─────────────────────┐ │
│  │ OBD      │ │ DTC       │ │ Base de Datos       │ │
│  │ Session  │ │ Decoder   │ │ de Soluciones       │ │
│  └──────────┘ └───────────┘ └─────────────────────┘ │
│  ┌──────────┐ ┌───────────┐ ┌─────────────────────┐ │
│  │ ELM327   │ │ Mode 06   │ │ Motor de Salud      │ │
│  │ Negoti-  │ │ Parser    │ │ Predictiva          │ │
│  │ ator     │ │           │ │                     │ │
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

## Características

### Diagnóstico DTC
- Lectura y borrado de códigos Activos, Pendientes y Permanentes (Modos 01–0A).
- Base de datos local con guías de reparación offline para cada código, incluyendo:
  - Nivel de urgencia y evaluación de seguridad para conducir.
  - Causas probables ordenadas por relevancia.
  - Síntomas, procedimientos de diagnóstico y costos estimados.
- Verificación de monitores de preparación (I/M Readiness).

### Datos en Tiempo Real
- Medidores animados (46 estilos + editor DIY) para RPM, velocidad, temperaturas, fuel trims, sensores O2, voltaje, etc.
- Editor de dashboards con disposición personalizable.
- Modo osciloscopio para visualización de señales de sensores.
- Modo HUD (Heads-Up Display) para proyección en parabrisas.

### Asistente IA
- Consultas en lenguaje natural sobre códigos de falla y anomalías de sensores.
- Modelo BYOK (Bring Your Own Key) — la clave API se almacena localmente.

### Mantenimiento
- Calendario con alertas para intervalos de servicio (aceite, filtros, frenos, bujías, etc.).
- Análisis FODA del vehículo cruzando fallas activas, estado de batería, temperatura y eficiencia.

### Viajes y Eficiencia
- Registro de rutas con kilometraje, velocidad promedio y duración.
- Indicador de eco-conducción en tiempo real.
- Monitoreo de ralentí y consumo estimado.
- Exportación de reportes en PDF.

### Herramientas
- Inspección pre-compra (checklist de 47 puntos para autos usados).
- Reportes DVIR para vehículos comerciales.
- Terminal OBD2 para comandos AT/OBD directos.
- Terminal Android con utilidades del sistema.
- LiveLink — servidor local para transmitir telemetría a un panel web.

### Conectividad
- Bluetooth Classic, BLE y WiFi (TCP).
- Autodetección de velocidad del puerto serie y análisis de respuestas CAN segmentadas (ISO-TP).

---

## Estructura del Proyecto

```
MEET/
├── android/                        # App Android (Kotlin/Compose)
│   └── app/src/main/
│       ├── kotlin/com/elysium369/meet/
│       │   ├── core/
│       │   │   ├── obd/            # Motor OBD-II (sesiones, PIDs, decodificadores)
│       │   │   ├── ai/             # Integración IA
│       │   │   ├── alerts/         # Alertas por umbral de sensor
│       │   │   ├── audio/          # Notas de voz
│       │   │   ├── backup/         # Respaldo en la nube
│       │   │   ├── export/         # Generador de reportes PDF
│       │   │   ├── health/         # Algoritmos de salud vehicular
│       │   │   ├── livelink/       # Servidor HTTP/Sockets local
│       │   │   ├── sync/           # Sincronización Supabase
│       │   │   ├── transport/      # Controladores BLE/BT/WiFi
│       │   │   └── trips/          # Registro de viajes
│       │   ├── data/
│       │   │   ├── local/          # Base de datos Room
│       │   │   └── supabase/       # Conectores Supabase
│       │   └── ui/
│       │       ├── screens/        # Pantallas (DTC, Dashboards, DVIR, etc.)
│       │       ├── components/     # Widgets de medición y gráficos
│       │       └── theme/          # Sistema de diseño
│       └── assets/
│           ├── dtc_offline_solutions.json   # Guías locales de reparación
│           └── common_fixes.json            # Consultas rápidas
├── src/                            # App web (React/Vite/TS)
├── releases/                       # APKs pre-construidos
└── generate_guides.py              # Scripts auxiliares
```

---

## Inicio Rápido

### Requisitos
- Android Studio Jellyfish+ (Gradle 8.5+)
- JDK 17
- Android SDK 34+
- Adaptador OBD-II ELM327 (Bluetooth o WiFi)

### Compilar
```bash
git clone https://github.com/jordelmir/MEET-Mecanicos-Especialistas-En-Todo.git
cd MEET-Mecanicos-Especialistas-En-Todo/android
./gradlew assembleDebug
# APK → android/app/build/outputs/apk/debug/app-debug.apk
```

### App Web
```bash
npm install
npm run dev
# http://localhost:5173
```

---

## Privacidad

- **BYOK** — Las claves API se almacenan solo en el dispositivo.
- **Sincronización Cloud** — Protegida con RLS (Row-Level Security) en Supabase.
- **Sin rastreo** — Los datos pertenecen al usuario y se guardan localmente por defecto.

---

## Licencia

MIT

---

<p align="center">
  <strong>Creado por <a href="https://github.com/jordelmir">Jordelmir</a></strong><br/>
  <a href="https://github.com/jordelmir/MEET-Mecanicos-Especialistas-En-Todo/releases/latest">
    <img src="https://img.shields.io/badge/⬇️_Descargar_APK-39FF14?style=for-the-badge&labelColor=0A0E1A" alt="Download"/>
  </a>
</p>
