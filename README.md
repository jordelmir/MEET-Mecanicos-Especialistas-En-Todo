<p align="center">
  <img src="https://img.shields.io/badge/Elysium%20Vanguard-AI%20OS-00FFD1?style=for-the-badge&labelColor=0A0E1A" alt="Elysium Vanguard AI OS" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-4.26.1%20%7C%20code%2060-00BCD4?style=flat-square&logo=android&logoColor=white" alt="Android version" />
  <img src="https://img.shields.io/badge/Room-schema%2082-39FF14?style=flat-square" alt="Room schema" />
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

## Estado del código en este checkpoint

- **Android:** `versionName 4.26.1`, `versionCode 60`.
- **Web/package:** `4.26.1`.
- **Persistencia local:** Room schema `82`, con las migraciones y esquemas
  `81.json` y `82.json` exportados en el repositorio.
- **Seguridad (Safety V2):** Suite completa de 9 pantallas elevadas al estándar
  visual y de movimiento con 8 componentes dedicados (`SafetyCategoryIcons`,
  `SafetyEmptyState`, `SafetyHaptics`, `SafetyShimmer`, `SafetyPulse`,
  `AccountabilityGauge`, `SafetyTimeline`, `SafetyOfflineBanner`).
- **Backend / Supabase:** Migraciones acumulativas incluyendo autoridad de
  precios `20260922090000_crc_ride_pricing_authority_parity.sql`, demografía del
  observatorio de seguridad `20260923000000_safety_observatory_demographics.sql`,
  y solicitudes unificadas de movilidad v4.
- **Nombre comercial:** Elysium Vanguard AI OS. No se deben renombrar los
  contratos técnicos heredados `MEET` durante una actualización normal.

Este checkpoint es código sincronizado, no una declaración de lanzamiento en
producción. Un APK debug, una compilación local o un workflow de firma efímera
no sustituyen una firma de producción, una prueba física de dos cuentas ni una
evidencia remota de PostgreSQL.

## Capacidades integradas

### Diagnóstico y reparación automotriz

- Conexión OBD-II por Bluetooth Classic, BLE, Wi-Fi TCP y DoIP/UDS.
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

## Límites de publicación

No publiques un `app-debug.apk` como artefacto de producción. Una entrega
oficial necesita un tag sobre el SHA final de `main`, una AAB/APK firmada con la
clave de producción, manifiesto, hashes, SBOM, provenance y los gates requeridos
en verde. Los secretos de Supabase y firma nunca pertenecen al repositorio.

## Licencia

MIT
