<p align="center">
  <img src="https://img.shields.io/badge/Elysium%20Vanguard-AI%20OS-00FFD1?style=for-the-badge&labelColor=0A0E1A" alt="Elysium Vanguard AI OS" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-4.28.0%20%7C%20code%2062-00BCD4?style=flat-square&logo=android&logoColor=white" alt="Android version" />
  <img src="https://img.shields.io/badge/Room-schema%2088-39FF14?style=flat-square" alt="Room schema" />
  <img src="https://img.shields.io/badge/Architecture-offline--first-7F52FF?style=flat-square" alt="Offline first" />
</p>

# Elysium Vanguard AI OS

Elysium Vanguard AI OS coordina diagnóstico automotriz, conocimiento técnico,
reparación, comercio, movilidad y evidencia verificable. `MEET` se conserva
solamente como identificador técnico de compatibilidad: paquete Android,
protocolos, rutas, tablas, base Room y repositorio.

La regla del producto es simple: no se presenta una intención local, una
estimación o un dato de demostración como un hecho físico o una confirmación
del servidor.

## Estado del código en este checkpoint (Safety V3 & Convergence)

- **Android:** `versionName 4.28.0`, `versionCode 62`.
- **Contrato de versión del paquete:** `4.28.0` (sin cambios funcionales web en esta ronda).
- **Seguridad Ciudadana (Safety V3 - Red Abierta & Sincronización Mundial):**
  - **Sincronización en Línea Inmediata:** Subida directa y auto-proyectada vía RPC `safety_create_report_v3`, actualizando en tiempo real todas las secciones (Mapa, Casos Públicos, Líneas de Tiempo y Rendición de Cuentas).
  - **Doble Capa de Resiliencia:** Persistencia transaccional local en Room previa al envío, ejecución inmediata online en `SafetyRepository` con refresco automático de proyecciones, y respaldo background vía `SafetyCommandSyncWorker` (WorkManager) para operación 100% offline-first.
  - **Hub de Seguridad Reactivo:** `SafetyHomeViewModel` observa flujos de Room en tiempo real, reflejando conteos precisos de `MY REPORTS`, reportes pendientes y estado de red activo.
  - **Visualización Cartográfica en Vivo:** Eliminación de filtros que restringían coordenadas reales, permitiendo visualizar todos los incidentes geolocalizados en el mapa MapLibre con selección de marcadores y hoja inferior de narrativa completa.
  - **Módulo Independiente TIMELINES:** Pantalla dedicada (`MeetDestinations.SAFETY_TIMELINES`, `SafetyTimelinesScreen`) con navegación propia, espina cronológica vertical, nodos de color según la categoría del incidente y desglose forense de fuentes/evidencia.
  - **Enlaces Multimedia Optimizados:** Enlaces directos a videos (YouTube, TikTok, Instagram, Facebook, Drive) con botones de acción rápida, colores temáticos de alto contraste y lanzamiento nativo 1-click para preservar espacio en servidor.
- **Persistencia Local (Room Schema 88):**
  - Tablas locales dedicadas para proyecciones públicas: `safety_public_points_local`, `safety_public_cases_local`, `safety_public_timeline_local`, `safety_public_claims_local`.
  - Cola outbox `safety_command_outbox` con ACK directo y transiciones idempotentes.
  - Migración Room `88` verificada e integrada en `MeetDatabase.kt`.
- **Backend & Migraciones Supabase:**
  - `20261001070000_safety_worldwide_reopen_v3.sql`: Apertura mundial de políticas de lectura pública en proyecciones de seguridad.
  - `20261001080000_safety_auto_project_reports_v3.sql`: Eliminación de bloqueos de moderación previa y auto-proyección atómica de reportes ciudadanos a proyecciones públicas con backfill histórico.
- **Elysium Vanguard Master Order Genesis:**
  - **Infraestructura de Outbox en Producción:** `PostgresOutboxRepository` conectado en `Application.kt` con bloqueo a nivel de fila `FOR UPDATE SKIP LOCKED`, leases criptográficos atómicos y DLQ. Prohibición estricta de stubs en memoria fuera de tests.
  - **EVAIR Living Companion & Instant Shift:** Acompañante vivo persistente con canal único de audio (`VoiceInteractionBus.default`), eliminando fugas `ERROR_RECOGNIZER_BUSY`. Teletransportación instantánea 3D al botón objetivo mediante comandos `"SECCIÓN <nombre>"` y `"SELECCIONA <nombre>"`, compresión cuántica (`0.18f`), pulso de iluminación y ejecución determinista.
  - **Control Plane Semántico de UI:** Cobertura de controles interactivos instrumentados con `Modifier.agentAction` y `Modifier.agentTextInput`. Blindaje de campos confidenciales (`AgentUiSensitivity.SECRET` para PINs/claves) que deniegan inspección o inyección por voz.
  - **Resolución de lugares:** el flujo nuevo de servicios no fabrica precios ni ubicaciones; las herramientas históricas todavía requieren auditoría independiente. Lugares no resolubles devuelven estrictamente `PlaceResolutionResult.NotFound`. Cotizaciones versionadas con TTL (`CR_METRO_V3_2026`).
  - **Suite de Verdad & Release Gate:** `GoldenJourneyTruthSuite` (5 tests de verdad obligatorios) y `AgentUiCoverageReleaseGate` integrados; distinguir sus pruebas automatizadas de recorridos físicos y verificación de producción.
  - **Paridad Cross-Runtime Parity:** Paridad byte-a-byte exacta de firmas SHA-256 entre TypeScript, Kotlin y PostgreSQL en `ci-verify.sh`.
- **Servicios Elysium unificados:** ambas entradas históricas abren la misma experiencia cliente/proveedor con estados autorizados, métricas/calificaciones reales y comisión del 5% idempotente mediante saldo.
- **Navegación 1-Click con Waze (`WazeNavigationButton`):**
  - Protocolo nativo `waze://?ll=lat,lng&navigate=yes` integrado en todas las tarjetas de servicios (viajes, misiones courier, rescate técnico y radar en vivo).
  - Fallback automático en cascada: Geo Intent (Google Maps / navegador vehicular) y Waze Live Map Web.
- **Hub Universal de Servicios Activos, Historial y Oferta:**
  - `⚡ SERVICIOS ACTIVOS`: Seguimiento simultáneo de Viajes, Misiones de Entrega y Servicios Técnicos para Clientes y Prestadores.
  - `📜 HISTORIAL FORENSE`: Balance financiero acumulado en ₡ CRC, métricas de actividad y verificación de integridad SHA-256.
  - `🛠️ CONFIGURACIÓN DE OFERTA & MATERIALES`:
    - Definición formal de especialidad (Mecánica, Grúas, Pulpería, Soda, Plomería, Electricidad, Cerrajería, Hogar).
    - Fórmulas de cobro matemáticas en colones costarricenses (mano de obra ₡/hora, tarifa base, costo ₡/km y recargos).
    - Políticas de suministro de materiales (`MATERIALS_INCLUDED`, `CLIENT_SUPPLIED`, `AT_COST_WITH_MARGIN`).
    - Sub-servicios con horas estimadas, materiales requeridos y equipamiento certificado.
- **Cancelación Autoritativa de Viajes:**
  - Cancelación local inmediata garantizada (`LOCAL_CANCELLED`) y sincronización outbox resiliente con Supabase.
  - Limpieza atómica de selecciones activas para evitar estados residuales o bloqueos.
- **Comercio Local & Delivery Triangular:**
  - Pulperías & Minisúper: Abarrotes, recargas y canasta básica con despacho local.
  - Sodas & Restaurantes: Comida típica y bebidas preparadas en tiempo real.
  - Mensajería & Courier: Asignación de repartidor, seguimiento de trayecto y custodia de entrega por PIN.
- **Laboratorio de Emisiones & Pre-ITV Costa Rica:** Subsistema completo de evaluación
  regulatoria COSEVI / CITA con contrato de verdad inquebrantable (`MEASURED` vs `PHYSICS_DERIVED`
  vs `MODEL_ESTIMATED` vs `UNKNOWN`).
- **Nombre comercial:** Elysium Vanguard AI OS. No se deben renombrar los
  contratos técnicos heredados `MEET` durante una actualización normal.

Este checkpoint es código sincronizado, no una declaración de lanzamiento en
producción. Un APK debug, una compilación local o un workflow de firma efímera
no sustituyen una firma de producción, una prueba física de dos cuentas ni una
evidencia remota de PostgreSQL.

## Capacidades integradas

### Diagnóstico y reparación automotriz

- Conexión OBD-II por Bluetooth Classic, BLE, Wi-Fi TCP y DoIP/UDS.
- **Laboratorio de Emisiones y Pre-ITV Costa Rica:**
  - Decodificador estricto SAE J1979 / SAE J1979-DA de Modo $06 (sin límites inventados ni pases falsos sin límites).
  - Estimador físico de Flujo de Combustible y CO₂ (Speed-Density MAP/MAF + estequiometría de gasolina/etanol).
  - Analizador Virtual de Gases con etiquetado explícito de proveniencia (`MODEL_ESTIMATED` para CO/HC sin sonda física de 4/5 gases).
  - Osciloscopio dual O₂ en tiempo real con monitoreo de cruces por segundo y eficiencia catalítica.
  - Asistente guiado de inspección Pre-ITV con tacómetro dinámico (Ralentí 700-1000 RPM vs Acelerado 2500 RPM) y verificación de corte de fecha Lambda (Decreto 37372-MOPT).
- Lectura de DTC, telemetría y acciones bidireccionales sólo cuando existe un
  enlace físico válido; el modo de entrenamiento se identifica como demo.
- Guías de diagnóstico, reparación, piezas y visualización 3D enlazadas por
  identificadores técnicos. La compatibilidad no se marca como exacta sin
  evidencia VIN/OEM o una tupla técnica cerrada.
- Reportes certificados con cadena hash y QR de seis campos sin VIN, placa ni
  teléfono completos.

### Viajes

- PostgreSQL/Supabase es la autoridad para solicitud, asignación, PIN,
  transiciones, comisión y liquidación. Android usa Room como proyección local
  y outbox durable; no puede proyectar un `ACCEPTED` optimista.
- La aceptación usa control de versión, idempotencia y una identidad de actor
  validada en el servidor. PIN, inicio y finalización vuelven al cliente sólo
  después de confirmación remota.
- Los datos de ruta, ubicación, saldo, vehículo y estado remoto se muestran
  con su fuente y disponibilidad. La interfaz no inventa GPS, vehículos,
  calificaciones ni resultados de pago.
- Preferencias de pasajeros, calificaciones idempotentes y Objetos Olvidados
  están conectados al viaje y chat protegido correspondiente. Objetos Olvidados
  no revela teléfonos ni inventa compensaciones o cobros de entrega.
- La programación de viaje conserva una intención local con lugares geográficos
  válidos, fecha/hora, recurrencia e indicaciones. No afirma un conductor
  asignado hasta que el servidor lo confirme.

### Seguridad, identidad y dinero

- Las proyecciones de seguridad y los centros restringidos fallan cerrados;
  una pantalla visible no concede autoridad.
- Billeteras, comisiones, ingresos y recargas sólo se muestran como disponibles
  tras el recibo correspondiente de Supabase. No se fabrican saldos locales.
- Los pagos externos requieren una capacidad de proveedor confirmada. Los
  asientos financieros usan importes enteros y un ledger inmutable.

## Arquitectura de verdad

```text
Intención local
  → outbox durable
  → RPC autenticado y validación de servidor
  → recibo/evento/ledger cuando corresponda
  → proyección Supabase
  → Room
  → interfaz
```

La interfaz puede mostrar `pendiente`, `sin conexión`, `dato no capturado` o
`requiere prueba física`. Nunca debe convertir esas condiciones en éxito.

## Evidencia física registrada

Las rondas anteriores registran instalación, apertura y proceso estable en
**Honor VER-N49** y **Xiaomi M2101K6R**. La
[nota 4.26.0](docs/releases/2026-09-18-android-4.26.0-rides-lost-found-sync.md)
documenta la instalación y apertura en ambos equipos; las notas de
[4.25.0](docs/releases/2026-09-17-android-4.25.0-rides-v9-recovery.md) y
[4.26.1](docs/releases/2026-09-20-elysium-vanguard-ai-os-4.26.1-authority-hardening.md)
registran también arranque Android en Honor. Esta evidencia pertenece a esos
artefactos y fechas, no prueba automáticamente un APK posterior.

El recorrido económico Golden E2E de dos cuentas —descubrimiento, doble claim,
dos recuperaciones de proceso, PIN, inicio, cierre e invariantes
exactly-once— debe repetirse sobre el SHA y el artefacto que se vaya a lanzar.

## Verificación

Ejecuta las verificaciones desde una copia limpia del repositorio. En esta Mac,
Gradle debe usar un solo worker y no mantener un daemon persistente.

```bash
npm run check:versions
bash tests/parity/ci-verify.sh

cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  --no-daemon --max-workers=1
```

Para cambios de Viajes, añade los gates del dominio:

```bash
bash tests/ride/verify-ride-android-authority.sh
bash tests/ride/verify-ride-command-authority-postgres.sh
```

La validación física requiere una instalación en un dispositivo real, apertura
verificada, proceso en primer plano y revisión de logs. Para un lanzamiento de
movilidad se requieren dos cuentas independientes y evidencia PostgreSQL de
idempotencia y liquidación exactamente una vez.

## Estructura

```text
android/                         Aplicación Android, Room y Compose
supabase/migrations/             Esquema, RLS y RPCs autoritativos
supabase/functions/              Edge Functions
tests/                           Paridad, integración y gates de dominio
docs/                            Contratos, arquitectura, seguridad y releases
tools/                           Verificadores y utilidades de release
```

## Documentación principal

- [Visión de producto](docs/PRODUCT_VISION.md)
- [Reglas de producto](docs/PRODUCT_OS_ROADMAP.md)
- [Contrato de paridad TypeScript/Kotlin](docs/architecture/CROSS-RUNTIME-PARITY.md)
- [Constitución de seguridad](docs/safety/SAFETY_CONSTITUTION.md)
- [Especificación de recuperación de Viajes](docs/rides/MEET_RIDES_V9_APK_RECOVERY_SPEC.md)
- [Ruta vial y programación](docs/rides/ROAD_ROUTE_AND_SCHEDULING_2026-09-21.md)
- [Hardening de autoridad 4.26.1](docs/releases/2026-09-20-elysium-vanguard-ai-os-4.26.1-authority-hardening.md)
- [Comercio Local y Delivery Triangular V1](docs/commerce/MEET_LOCAL_COMMERCE_AND_DELIVERY_V1.md)
- [Release 4.27.0 — Comercio Local, Delivery y Hub de Servicios Activos](docs/releases/2026-09-25-elysium-vanguard-ai-os-4.27.0-commerce-and-active-services.md)

## Descarga y Releases

[**DESCARGAR ELYSIUM PARA ANDROID**](https://github.com/jordelmir/MEET-Mecanicos-Especialistas-En-Todo/releases/download/v4.28.0/elysium-v4.28.0-android.apk)

Entrega 4.28.0 para pruebas internas. Consultar [release y SHA256SUMS](https://github.com/jordelmir/MEET-Mecanicos-Especialistas-En-Todo/releases/tag/v4.28.0) para comprobar publicación y archivos. La evidencia y límites de servicios/mensajes están en `android/docs/ascension/`.

## Límites de publicación

No publiques un `app-debug.apk` como artefacto de producción. Una entrega
oficial necesita un tag sobre el SHA final de `main`, una AAB/APK firmada con la
clave de producción, manifiesto, hashes, SBOM, provenance y los gates requeridos
en verde. Los secretos de Supabase y firma nunca pertenecen al repositorio.

## Licencia

MIT
