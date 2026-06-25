<p align="center">
  <img src="https://img.shields.io/badge/MEET-Mecanicos%20Especialistas%20En%20Todo-00FFD1?style=for-the-badge&labelColor=0A0E1A" alt="MEET"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-00BCD4?style=flat-square&logo=android&logoColor=white" alt="Platform"/>
  <img src="https://img.shields.io/badge/Kotlin-2.1%20%7C%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/SQLite-Offline%20First-39FF14?style=flat-square" alt="SQLite"/>
  <img src="https://img.shields.io/badge/Status-Active%20Build-39FF14?style=flat-square" alt="Status"/>
</p>

# MEET

MEET es una plataforma Android de diagnostico automotriz offline-first orientada a talleres, mecanicos independientes y usuarios avanzados. Su objetivo no es solo leer DTCs: busca unir escaneo real OBD/UDS/DoIP, conocimiento mecanico utilizable, red de reparacion y flujos de solicitud tipo marketplace en una sola app.

## Que es real hoy

- Topologia real de ECUs: la pantalla de topologia ya no ofrece simulacion. Solo dibuja modulos que responden fisicamente al sondeo.
- Borrado de DTCs, pruebas activas y adaptaciones: requieren enlace OBD real. Ya no se marcan como exitosas sin vehiculo conectado.
- Escaneo DTC y registro de viajes: ya no fabrican resultados cuando no hay vehiculo enlazado.
- Bluetooth Classic: transporte RFCOMM/SPP real.
- BLE: escaneo real con `BluetoothLeScanner` y conexion GATT real via `BleTransport`.
- WiFi TCP: conexion real a adaptadores ELM por socket.
- DoIP: activacion de routing ISO 13400 real y sondeo real del gateway/servidor UDS cuando se usa `:13400`.

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

La idea es que una solicitud no sea solo "el carro falla", sino una orden de triage util para cotizar y resolver.

## DIY Gauges y marketplace

- el editor DIY ahora puede persistir configuraciones completas de gauge
- el marketplace de gauges ya consume listados reales desde Supabase
- las tarjetas y previews renderizan el gauge real a partir del `config_json`
- el flujo de compra se engancha con Google Play Billing y registra la compra en Supabase cuando el build y los productos estan configurados

Limitacion honesta:

- las compras in-app requieren un build distribuido por Google Play y productos `gauge_tier_*` activos
- en debug local el flujo mostrara errores reales de Billing si el servicio o los productos no estan disponibles

## Estructura del proyecto

```text
MEET/
├── android/
│   └── app/src/main/
│       ├── kotlin/com/elysium369/meet/
│       │   ├── core/obd/              # Sesion OBD, UDS, DoIP, PIDs, DTCs
│       │   ├── core/transport/        # BT classic, BLE, WiFi
│       │   ├── data/local/            # Room, entidades, conocimiento mecanico
│       │   ├── data/supabase/         # Reparacion/red/sync cloud
│       │   └── ui/screens/            # Scanner, topologia, marketplace, repair network
│       └── assets/
│           ├── dtc_database_es.json
│           └── databases/meet_dtc.db
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

- `:app:compileDebugKotlin` exitoso
- APK debug instalable via `adb`
- app abierta en Android via `am start`
- `generate_db.py --include-graph` ejecutado y base regenerada

## Fuentes tecnicas

### SQLite y Android

- SQLite JSON functions and operators:
  [sqlite.org/json1.html](https://www.sqlite.org/json1.html)
- SQLite `CREATE INDEX`:
  [sqlite.org/lang_createindex.html](https://www.sqlite.org/lang_createindex.html)
- Android SQLite performance best practices:
  [developer.android.com/topic/performance/sqlite-performance-best-practices](https://developer.android.com/topic/performance/sqlite-performance-best-practices)

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
