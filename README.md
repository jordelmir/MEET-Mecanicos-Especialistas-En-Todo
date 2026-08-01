<p align="center">
  <img src="https://img.shields.io/badge/MEET-Mecanicos%20Especialistas%20En%20Todo-00FFD1?style=for-the-badge&labelColor=0A0E1A" alt="MEET"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-00BCD4?style=flat-square&logo=android&logoColor=white" alt="Platform"/>
  <img src="https://img.shields.io/badge/Kotlin-1.9.23%20%7C%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/SQLite-Offline%20First-39FF14?style=flat-square" alt="SQLite"/>
  <img src="https://img.shields.io/badge/Status-Active%20Build-39FF14?style=flat-square" alt="Status"/>
</p>

# MEET

MEET es una plataforma Android de diagnostico automotriz offline-first orientada a talleres, mecanicos independientes y usuarios avanzados. Su objetivo no es solo leer DTCs: busca unir escaneo real OBD/UDS/DoIP, conocimiento mecanico utilizable, red de reparacion y flujos de solicitud tipo marketplace en una sola app.

## Versión actual: 4.9.1

La versión 4.9.1 restringe los reportes colaborativos de tránsito a una ruta
realmente iniciada. El botón sólo aparece al conductor durante `IN_PROGRESS`,
la aplicación vuelve a validar rol, GPS y proyección del servidor antes de
registrar, y Supabase rechaza inserciones que no pertenezcan al conductor y
vehículo verificado asignados al viaje activo.

La versión 4.9.0 añade identidad visual personalizable al mapa de Viajes: cuatro
emblemas originales para conductor y cuatro avatares originales para pasajero,
con selector accesible desde Perfil e Iconos del mapa. La preferencia se guarda
localmente y el renderizador procedural mantiene bordes, neón y sombras nítidos
en distintas densidades de pantalla sin alterar ubicación, seguridad ni datos
del viaje.

La versión 4.7.0 convierte Viajes en un vertical distribuido verificable:
PostgreSQL es autoridad de asignación, PIN, ciclo de vida y comisión; Room es
proyección y outbox. Implementa dinero entero, comisión exacta de 500 basis
points, ledger de doble entrada, concurso transaccional, flujo pasajero/chofer,
routing vial, Realtime recuperable, alta piloto honesta, Elysium Guardian,
casos de soporte, aislamiento tenant y observabilidad sin PII.

El flujo no inventa ruta, verificación, saldo, contacto con autoridades ni
éxito remoto. La app ya no revela teléfonos reales durante el viaje y redacta
el payload sensible del outbox después del acuse del servidor.

El Atlas G4ED cubre un universo técnico de 6.405
experiencias 3D/360 offline: 420 elementos de motor y 5.985 elementos de
transmisión/hidráulica, sistema eléctrico, carrocería/interior, chasis y
periféricos. Los 130 paquetes GLB comparten IDs canónicos con Motor 3D,
Piezas, IA, DTC y Repuestos.

Cada detalle permite orbitar, aislar, ver contexto, activar auto-rotación y
despiece. La aplicabilidad registra lado, carrocería y equipamiento condicional;
OEM, cantidad, supersesión y relaciones de fijación quedan explícitamente
pendientes de VIN/EPC cuando la fuente no permite afirmarlos.

La integración Master Automotive Knowledge añade un grafo determinista y
citado que conecta DTC, pruebas, reparación, repuestos, IA y 3D sin eliminar el
Motor 360° restaurado. Las decisiones de reemplazo y compra fallan cerradas sin
evidencia canónica; la geometría actual sigue declarada como
procedural/genérica y no dimensional.

La acción `BUSCAR EN GOOGLE` de cada resultado DTC abre el navegador
predeterminado del dispositivo. La consulta contiene únicamente el DTC cuando
no hay vehículo activo y añade marca, modelo, año, transmisión y cilindrada
cuando el usuario sí tiene uno seleccionado; nunca envía VIN ni placa.

Documentación técnica:

- [`docs/knowledge/AUTOMOTIVE_KNOWLEDGE_FABRIC.md`](docs/knowledge/AUTOMOTIVE_KNOWLEDGE_FABRIC.md)
- [`docs/releases/2026-07-26-android-4.1.0-master-automotive-knowledge.md`](docs/releases/2026-07-26-android-4.1.0-master-automotive-knowledge.md)
- [`docs/releases/2026-07-26-android-4.1.1-dtc-browser-search.md`](docs/releases/2026-07-26-android-4.1.1-dtc-browser-search.md)
- [`docs/visual3d/G4ED-420-ATLAS.md`](docs/visual3d/G4ED-420-ATLAS.md)
- [`docs/visual3d/VEHICLE-TECHNICAL-ATLASES.md`](docs/visual3d/VEHICLE-TECHNICAL-ATLASES.md)
- [`docs/releases/2026-07-27-android-4.3.0-g4ed-420-atlas.md`](docs/releases/2026-07-27-android-4.3.0-g4ed-420-atlas.md)
- [`docs/releases/2026-07-27-android-4.4.0-vehicle-technical-atlases.md`](docs/releases/2026-07-27-android-4.4.0-vehicle-technical-atlases.md)
- [`docs/releases/2026-07-28-android-4.6.0-ride-profiles-search-dtc-3d.md`](docs/releases/2026-07-28-android-4.6.0-ride-profiles-search-dtc-3d.md)
- [`docs/releases/2026-07-28-android-4.6.1-data-surface-hardening.md`](docs/releases/2026-07-28-android-4.6.1-data-surface-hardening.md)
- [`docs/security/ANDROID-DATA-SURFACE-4.6.1.md`](docs/security/ANDROID-DATA-SURFACE-4.6.1.md)
- [`docs/releases/2026-07-28-android-4.6.2-rides-reactive-locale.md`](docs/releases/2026-07-28-android-4.6.2-rides-reactive-locale.md)
- [`docs/releases/2026-07-29-android-4.6.3-rides-primitive-compose-state.md`](docs/releases/2026-07-29-android-4.6.3-rides-primitive-compose-state.md)
- [`docs/releases/2026-07-29-android-4.6.4-rides-atomic-offer-acceptance.md`](docs/releases/2026-07-29-android-4.6.4-rides-atomic-offer-acceptance.md)
- [`docs/releases/2026-07-29-android-4.6.5-rides-lifecycle-guards.md`](docs/releases/2026-07-29-android-4.6.5-rides-lifecycle-guards.md)
- [`docs/releases/2026-07-29-android-4.6.6-rides-actor-authorization.md`](docs/releases/2026-07-29-android-4.6.6-rides-actor-authorization.md)
- [`docs/releases/2026-07-29-android-4.7.0-rides-mobility-authority.md`](docs/releases/2026-07-29-android-4.7.0-rides-mobility-authority.md)
- [`docs/releases/2026-08-01-android-4.9.0-map-avatar-catalog.md`](docs/releases/2026-08-01-android-4.9.0-map-avatar-catalog.md)
- [`docs/releases/2026-08-01-android-4.9.1-route-only-road-reports.md`](docs/releases/2026-08-01-android-4.9.1-route-only-road-reports.md)

## Que es real hoy

- Topologia real de ECUs: la pantalla de topologia ya no ofrece simulacion. Solo dibuja modulos que responden fisicamente al sondeo.
- Borrado de DTCs, pruebas activas y adaptaciones: requieren enlace OBD real. Ya no se marcan como exitosas sin vehiculo conectado.
- Escaneo DTC y registro de viajes: ya no fabrican resultados cuando no hay vehiculo enlazado.
- La pantalla DTC muestra automaticamente los codigos del ultimo escaneo real aunque todavia no haya un vehiculo seleccionado para historial persistente.
- DTC incluye una seccion `Hallazgos` con resumen de activos/pendientes/permanentes/historicos, modulos que respondieron y guia directa por codigo.
- Bluetooth Classic: transporte RFCOMM/SPP real.
- BLE: escaneo real con `BluetoothLeScanner` y conexion GATT real via `BleTransport`.
- WiFi TCP: conexion real a adaptadores ELM por socket.
- DoIP: activacion de routing ISO 13400 real y sondeo real del gateway/servidor UDS cuando se usa `:13400`.
- Diagnostico Visual 3D: el visor ya se alimenta de fichas tecnicas por componente con DTCs, PIDs, pruebas, flujo de reparacion, herramientas, seguridad y contexto listo para IA.
- Atlas G4ED: 420 experiencias 3D/360 offline enlazadas por ID canónico con Piezas, IA y solicitudes de Repuestos.
- Atlas técnicos: 5.985 experiencias adicionales, 110 sistemas y 110 paquetes
  GLB trazables para transmisión, eléctrico, carrocería, chasis y periféricos.
- Monetizacion: la APK actual opera con acceso completo temporal sin paywalls; Google Play Billing 9.1.0 queda integrado para reactivacion futura.
- Analytics web: eventos estructurados, consentimiento, cola offline, retencion y panel debug opcional para medir embudos reales sin depender de logs sueltos.
- Onboarding y Home: perfil de uso, adaptador preferido, centro de mando con siguiente accion y demo de entrenamiento rotulada.
- Seguridad bidireccional: pruebas activas bloqueadas si no hay conexion real, si el enlace es inestable o si el voltaje esta bajo.

## Arquitectura

MEET usa una estrategia offline-first con dos capas de conocimiento complementarias:

1. `Room` para la app viva.
   Guarda vehiculos, sesiones, DTCs, marketplace, conocimiento mecanico y la matriz hibrida local.

2. `SQLite` preconstruida para el seed pesado.
   El archivo `android/app/src/main/assets/databases/meet_dtc.db` se genera con `generate_db.py`. Por limite duro de GitHub para blobs mayores a 100 MB, el repositorio conserva un seed liviano y la base completa se reconstruye localmente antes de empaquetar releases.

### Arquitectura hibrida de conocimiento

MEET ya usa el patron que mejor calza para edge computing:

- Columnas indexadas para busquedas rapidas:
  `dtcCode`, `componentName`, `systemCategory`, `urgencyLevel`
- Payloads JSON para conocimiento profundo:
  `layerDiagnosticsJson`, `layerRebuildSpecsJson`, `layerTrenchKnowledgeJson`, `layerAdvancedEngJson`

Eso permite evolucionar la base sin romper el esquema cada vez que aparezca una capa nueva de conocimiento de taller.

## Base de datos actual

### Grafo DTC offline generado

La base completa generada localmente con `/usr/bin/python3 generate_db.py --include-graph` contiene:

- `18,805` definiciones DTC
- `36,363` sintomas ligados a DTC
- `78,274` causas probables
- `95,088` pasos de procedimiento
- `87,769` PIDs relacionados
- `71,445` co-ocurrencias entre codigos
- `18,805` filas de costos de reparacion

### Seed hibrido mecanico dentro del `.db`

El seed mecanico que inyecta el generador SQLite incluye:

- `3` filas en `meet_knowledge_matrix`
- `4` `symptom_guides`
- `5` `mechanical_procedures`
- `2` `component_rebuild_guides`
- `3` `trench_knowledge`
- `4` entradas de `automotive_chemistry`
- `3` `tool_usage_guides`
- `3` `safety_protocols`

### Seed mecanico en Room

La app tambien crea y migra tablas de conocimiento mecanico para:

- `symptom_guides`
- `mechanical_procedures`
- `component_rebuild_guides`
- `trench_knowledge`
- `automotive_chemistry`
- `tool_usage_guides`
- `safety_protocols`
- `meet_knowledge_matrix`
- `parts_stores`
- `part_requests`
- `part_offers`

Esto permite que el conocimiento mecanico viva dentro del dispositivo, incluso con mala conectividad en taller.

## Diagnostico y conectividad

### OBD / UDS / DoIP

- OBD-II modos estandar y DTCs
- hub avanzado para OBD-II, Mode $05, Mode $06, VIN/freeze frame y UDS OEM
- UDS sobre CAN para identificacion de ECU y servicios extendidos cuando el modulo responde
- DoIP ISO 13400 para gateway Ethernet y servidor diagnostico
- Sondeo topologico real por direcciones fisicas
- Lectura de VIN/DID por ECU cuando el modulo soporta `22 F190`, `22 F187`, `22 F189`, `22 F191`, `22 F18C`

### Topologia

La topologia actual:

- ya no muestra nodos demo
- ya no trata el broadcast funcional como si fuera una ECU real
- solo publica modulos que responden
- reconoce nodos DoIP como `ETHERNET`
- conserva latencia, protocolo, DTCs por modulo y soporte UDS cuando existe

Limitacion importante:

- un ELM generico no expone el mismo nivel de cobertura que un VCI OEM
- en muchos vehiculos genericos solo se vera powertrain y algunos modulos adicionales
- DoIP completo depende del gateway del vehiculo, la ruta fisica y los permisos del sistema OEM

## Motor de conocimiento mecanico

MEET ya no se limita a DTCs. El motor de conocimiento local esta tomando forma para cubrir:

- guias por sintoma:
  fuga de aceite, fuga de refrigerante, alternador que no carga, arranque dificil, pedal de freno esponjoso
- procedimientos:
  alternador, arranque, frenos, tapa de valvulas, carter, bomba de agua, parabrisas
- reconstruccion:
  alternadores y motores de arranque
- tacticas avanzadas:
  tornilleria trabada, esparragos rotos, reparacion de rosca en aluminio
- quimica aplicada:
  penetrantes, ATF + acetona, tinte UV, grasa dielectrica, limpiadores de sensor

## Repair Network y marketplace

Se mejoraron flujos para que la informacion entre mejor desde el principio:

- la red de reparacion ahora muestra guia de busqueda y vista previa de conocimiento offline
- publicar un caso pide mejor contexto tecnico y valida formato DTC real
- el marketplace del cliente ahora explica que evidencia minima conviene publicar
- el dashboard de taller ahora guia mejor la oferta: tiempo, garantia, enfoque diagnostico
- el marketplace agrega subasta real de repuestos: el cliente crea una solicitud de pieza ligada a una solicitud de servicio, DTC y vehiculo
- las repuesteras pueden ofertar marca, numero de parte, condicion, precio, envio, ETA y garantia
- el cliente puede aceptar una oferta de repuesto; Room guarda la solicitud y la oferta aceptada para operar offline y sincronizar cuando exista backend

La idea es que una solicitud no sea solo "el carro falla", sino una orden de triage util para cotizar, diagnosticar, conseguir la pieza correcta y resolver en una sola visita.

## DIY Gauges y marketplace

- el editor DIY ahora puede persistir configuraciones completas de gauge
- el marketplace de gauges ya consume listados reales desde Supabase
- las tarjetas y previews renderizan el gauge real a partir del `config_json`
- el flujo de compra se engancha con Google Play Billing 9.1.0 y verifica cada `purchaseToken` en Supabase antes de activar el entitlement
- en la APK actual, `MonetizationPolicy.PAYWALLS_ENABLED = false`: los gauges se pueden aplicar sin Google Play Billing mientras se decide el modelo comercial

Limitacion honesta:

- cuando se reactive monetizacion, las compras in-app requeriran un build distribuido por Google Play y productos `gauge_tier_*` activos
- si `PAYWALLS_ENABLED` vuelve a `true`, el flujo mostrara errores reales de Billing si el servicio o los productos no estan disponibles

## Monetizacion y entitlements

Estado actual de la APK: sin restricciones de pago. La politica central vive en `android/app/src/main/kotlin/com/elysium369/meet/core/monetization/MonetizationPolicy.kt` y deja `PAYWALLS_ENABLED = false` con acceso PRO local completo.

La app queda preparada para un modelo gratis + PRO sin cobrar la descarga inicial:

- compras unicas: `pro_lifetime`, `gauge_pack_elite`, `report_pack`, `gauge_tier_1` a `gauge_tier_10`
- suscripciones: `pro_monthly`, `pro_yearly`, `workshop_monthly`
- verificador Supabase Edge Function: `verify-google-play-purchase`
- tablas con RLS: `billing_products`, `google_play_purchase_receipts`, `user_entitlements`
- el cliente Android no activa PRO por si solo; primero envia `productId`, `productType` y `purchaseToken` al backend

Esto permite manejar renovaciones, cancelaciones, reembolsos y restauracion de acceso desde servidor, no solo desde UI modificable.

## Analytics profesional web

El frontend web ahora incluye:

- `analytics.track(...)` tipado por evento
- `anonymous_id`, `session_id`, rol y modo admin
- cola IndexedDB/localStorage con reintento exponencial
- eventos de pantalla, modulo, embudo, paywall y compra
- retencion D1/D3/D7/D14/D30
- consentimiento: `enabled`, `essential_only`, `disabled`
- panel `/analytics-debug` cuando `VITE_ENABLE_ANALYTICS_DEBUG=true`

Documentacion completa: `docs/ANALYTICS_WEB.md`.

## Diagnostico Visual 3D

El modulo 3D se organizo alrededor de dominio tecnico, no solo dibujo:

- `DiagnosticComponent`: pieza, categoria, ubicacion, mesh 3D, DTCs, PIDs, pruebas y specs
- `VisualDiagnosticRepository`: fuente de componentes por motor L4/V6/V8/EV
- `DiagnosticAiContextBuilder`: paquete de contexto con vehiculo, DTCs activos y PIDs vivos
- la UI muestra `Sin lectura en vivo` cuando el escaner no entrega dato real
- fusibles/reles incluyen amperaje, alimentacion esperada, continuidad, funcion y procedimiento de prueba
- EV/HV incluye advertencias de alto voltaje y desenergizacion OEM

Documentacion completa: `docs/VISUAL_DIAGNOSTICS_3D.md`.

## Sistema operativo automotriz

MEET esta avanzando hacia un flujo completo:

```text
Detectar problema -> diagnosticar -> validar con datos reales -> guiar reparacion
-> cotizar -> documentar -> cobrar -> aprender del caso
```

La app ahora conserva esta regla: si no hay adaptador real, el usuario puede explorar con demo de entrenamiento, pero los datos se rotulan y no se venden como lectura fisica.

Documentacion de producto: `docs/PRODUCT_OS_ROADMAP.md`.

## Estructura del proyecto

```text
MEET/
├── android/
│   └── app/src/main/
│       ├── kotlin/com/elysium369/meet/
│       │   ├── ai/                    # Contexto IA especializado
│       │   ├── core/obd/              # Sesion OBD, UDS, DoIP, PIDs, DTCs
│       │   ├── core/billing/          # Google Play Billing y verificacion
│       │   ├── core/transport/        # BT classic, BLE, WiFi
│       │   ├── data/visualdiagnostics/# Seed/repositorio diagnostico 3D
│       │   ├── domain/visualdiagnostics/# Modelo tecnico 3D
│       │   ├── data/local/            # Room, entidades, conocimiento mecanico
│       │   ├── data/supabase/         # Reparacion/red/sync cloud
│       │   └── ui/screens/            # Scanner, topologia, marketplace, repair network
│       └── assets/
│           ├── dtc_database_es.json
│           └── databases/meet_dtc.db
├── docs/                              # Billing, analytics y diagnostico visual
├── src/analytics/                     # SDK local de analytics web
├── supabase/functions/                # Edge Functions
├── supabase/migrations/               # RLS, entitlements, analytics
├── generate_db.py                     # Generador del SQLite enriquecido
└── README.md
```

## Build rapido

### Compilar APK debug

```bash
cd android
./gradlew assembleDebug
```

APK:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

### Regenerar la base SQLite completa

```bash
/usr/bin/python3 generate_db.py --include-graph
```

Nota: el `.db` completo supera 100 MB y no se empuja a GitHub como blob normal. Para publicar ese binario en linea se debe usar Git LFS o adjuntarlo como release asset.

## Estado verificado en esta iteracion

- `npm run build` exitoso
- `:app:compileDebugKotlin` exitoso
- `:app:testDebugUnitTest` exitoso
- Google Play Billing 9.1.0 compila usando el artefacto Java `com.android.billingclient:billing` para mantener compatibilidad con Kotlin 1.9.23

## Fuentes tecnicas

### SQLite y Android

- SQLite JSON functions and operators:
  [sqlite.org/json1.html](https://www.sqlite.org/json1.html)
- SQLite `CREATE INDEX`:
  [sqlite.org/lang_createindex.html](https://www.sqlite.org/lang_createindex.html)
- Android SQLite performance best practices:
  [developer.android.com/topic/performance/sqlite-performance-best-practices](https://developer.android.com/topic/performance/sqlite-performance-best-practices)

### Google Play Billing

- Google Play Billing release notes:
  [developer.android.com/google/play/billing/release-notes](https://developer.android.com/google/play/billing/release-notes)
- Integrate Google Play Billing:
  [developer.android.com/google/play/billing/integrate](https://developer.android.com/google/play/billing/integrate)
- Migrate to Play Billing Library 9:
  [developer.android.com/google/play/billing/migrate-gpblv9](https://developer.android.com/google/play/billing/migrate-gpblv9)
- Google Play payments policy:
  [support.google.com/googleplay/android-developer/answer/10281818](https://support.google.com/googleplay/android-developer/answer/10281818)

### Diagnostico y topologia

- ISO 13400 / DoIP overview:
  [iso.org/standard/74785.html](https://www.iso.org/standard/74785.html)
- Softing DoIP summary:
  [automotive.softing.com/standards/protocols/doip-iso-13400.html](https://automotive.softing.com/standards/protocols/doip-iso-13400.html)
- ISO 14229 / UDS overview:
  [iso.org/standard/72439.html](https://www.iso.org/standard/72439.html)
- Vector UDS overview:
  [vector.com/us/en/products/solutions/diagnostic-standards/uds-unified-diagnostic-services-iso14229/](https://www.vector.com/us/en/products/solutions/diagnostic-standards/uds-unified-diagnostic-services-iso14229/)
- Autel topology references:
  [autel.us/ultra-series-toplogy-update/](https://autel.us/ultra-series-toplogy-update/)
  [autel.us/new-autel-topology-helps-techs-diagnose-hidden-module-problems/](https://autel.us/new-autel-topology-helps-techs-diagnose-hidden-module-problems/)

### DTC y regulacion

- ISO 15031-6:
  [iso.org/es/contents/data/standard/06/63/66369.html](https://www.iso.org/es/contents/data/standard/06/63/66369.html)
- EPA readiness best practices:
  [epa.gov/system/files/documents/2022-08/diesel-obd-im-readiness-14k-pounds-gwr-best-practices.pdf](https://www.epa.gov/system/files/documents/2022-08/diesel-obd-im-readiness-14k-pounds-gwr-best-practices.pdf)
- CARB OBD II:
  [ww2.arb.ca.gov/sites/default/files/barcu/regact/2021/obd2021/fro-obdii.pdf](https://ww2.arb.ca.gov/sites/default/files/barcu/regact/2021/obd2021/fro-obdii.pdf)
- CARB J1979-2 attachment:
  [ww2.arb.ca.gov/sites/default/files/barcu/regact/2021/obd2021/15dayattc.pdf](https://ww2.arb.ca.gov/sites/default/files/barcu/regact/2021/obd2021/15dayattc.pdf)
- NHTSA vPIC:
  [vpic.nhtsa.dot.gov](https://vpic.nhtsa.dot.gov/)

## Licencia

MIT

<p align="center">
  <strong>MEET sigue creciendo hacia una base mecanica industrial, offline y util de verdad.</strong>
</p>
