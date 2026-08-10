# MEET 4.15 — Diagnostic Evidence Engine v2

Estado: implementado en código, pendiente de la ejecución de pruebas, compilación y validación física que autorice el propietario.

## Invariantes de verdad

1. Un `OK` del adaptador nunca demuestra que una ECU borró memoria.
2. Un finding solo se resuelve cuando la misma identidad ECU/namespace/DTC crudo obtiene cobertura post-borrado autoritativa y ausencia verificada.
3. Mode 03 vacío no permite resolver DTC pendientes, permanentes, UDS, ABS, SRS ni otros módulos no cubiertos.
4. `overlaps` sirve para exploración; `fullyCovers` es obligatorio para afirmar ausencia.
5. `P0300-01` y `P0300-02` son findings diferentes si su identidad UDS de 24 bits difiere.
6. Estado, severidad y urgencia son dimensiones independientes.
7. Un hallazgo anterior no desaparece de la experiencia por un escaneo parcial posterior.
8. `7DF` es un scope funcional, no una ECU.
9. DoIP es transporte IP diagnóstico y se enruta a UDS; no cae en Mode 03 legado.
10. Las representaciones 3D son relaciones/candidatos. Nunca prueban por sí solas una pieza dañada.

## Modelo de identidad

```text
DiagnosticFinding
  vehicleId
  bus / ECU endpoint
  namespace
  rawDtcIdentity
  displayCode (presentación)

DiagnosticObservation
  findingId
  timestamp
  semantics / status byte
  source service
  exchangeId
```

La identidad estable excluye el estado temporal. Pending, confirmed y test-failed son observaciones del mismo finding cuando ECU y DTC crudo coinciden.

## Borrado y verificación

```text
snapshot previo inmutable
  → solicitud Mode 04 / UDS 14
  → PDU positiva decodificada
  → scan post-borrado completo
  → cobertura por misma ECU + namespace + semántica
  → ausencia de la misma rawDtcIdentity
  → VERIFIED / PARTIALLY_VERIFIED / ACCEPTED_NOT_VERIFIED
```

Resultados de dominio:

- `Verified`
- `PartiallyVerified`
- `AcceptedButNotVerified`
- `Rejected`
- `Inconclusive`
- `Cancelled`

Solo los `findingId` presentes en `verifiedFindingIds` reciben `resolvedAt`.
Para UDS físico, la aceptación positiva debe pertenecer al mismo endpoint ECU del finding; una respuesta positiva del ECM no autoriza resolver TCM, ABS ni otra ECU aunque su código de presentación sea idéntico.
El motor rechaza planes vacíos y nunca ejecuta un borrado ciego. Mode 04 se emite solo si el plan contiene findings SAE; UDS Service 14 se limita a los endpoints UDS demostrados y nombrados por el snapshot previo.

## Capas diagnósticas

```text
PhysicalBusActor
  → DiagnosticTransport (CAN / DoIP / K-Line)
  → DiagnosticApplicationProtocol (SAE / UDS / KWP / OEM)
  → EcuEndpoint + DiagnosticRequestScope
  → ScanPlan
  → DiagnosticExchange append-only
  → Finding + Observation
  → Snapshot Evidence
  → Knowledge/Spatial Graph
  → Hypothesis Engine
  → Guided test
  → Repair verification
```

`ObdSession` conserva temporalmente el rol de fachada de compatibilidad. Las nuevas responsabilidades ya tienen contratos/use cases independientes para continuar la extracción sin romper integraciones existentes.

## NRC UDS

- `0x78`: espera P2* acotada y continuidad de solicitud.
- `0x21`: backoff y reintento acotado.
- `0x33/0x35/0x36/0x37`: barrera de seguridad; nunca reintento ciego.
- `0x22`: precondiciones no satisfechas.
- `0x31/0x11/0x12`: capability no soportada para la solicitud.
- rechazo general/desconocido: evidencia inconclusa, nunca lectura limpia.

## Persistencia Room 51

Nuevos datos:

- `dtc_events.rawDtcIdentity/rawDtc24/failureType/dtcFormat`
- `diagnostic_findings` como identidad longeva canónica; `dtc_events` permanece como proyección compatible
- `diagnostic_exchanges`
- `diagnostic_observations`
- `finding_diagnostic_snapshots`
- provenance, confidence, inputs y formula version en métricas derivadas
- autoridad, versión, applicability y verification status en definiciones DTC

Los exchanges, observations y finding snapshots son append-only.

El Freeze Frame Mode 02 conserva la respuesta cruda de identidad y la de cada PID consultado como `DiagnosticExchange`; el snapshot del finding enlaza esos IDs y declara origen, confianza y versión del parser. Una respuesta perteneciente a otro DTC nunca se adjunta al finding solicitado.

## UX diagnóstica

- Cuenta findings ECU-specific, no strings DTC distintos.
- Secciones `OBSERVADO AHORA` y `NO VERIFICADO EN ESTE ESCANEO`.
- Mensaje correcto: `SIN DTC PERMANENTES OBSERVADOS`.
- Tarjetas estáticas por defecto; se eliminaron animaciones infinitas por finding.
- Fondo, estado vacío y transición de escaneo respetan la escala de animación reducida/desactivada del sistema Android.
- Progreso determinista de módulos y servicios.
- El Digital Twin recibe clave estable, ECU, namespace y DTC crudo.

## Powertrain

`EngineType` queda como perfil de renderizado. La autoridad es `VehiclePowertrainTopology`:

- combustion type
- cylinder layout/count
- electrification
- forced induction
- displacement
- transmission
- drive layout
- voltage architecture
- provenance/confidence por propiedad

PHEV se evalúa antes que EV, por lo que `PHEV` no puede colapsar en `ELECTRIC` por coincidencia textual.

## Seguridad de release

- R8 y resource shrinking activados para release.
- MiniMax y Car2DB se fuerzan vacíos en release; deben usar backend/BYOK.
- La clave Supabase de release requiere declaración `ANON` o `PUBLISHABLE`.
- Escaneo de secretos de release disponible en `tools/scan-release-secrets.sh`.
- Los gates de 4.15 están en `workflow_dispatch` y no se ejecutarán hasta autorización explícita.

## Presupuestos de rendimiento 4.15

Estos son gates objetivo, no resultados medidos todavía:

- tamaño release: no crecer más de 10 % contra el artefacto base sin justificación de assets/evidencia;
- cold start P95: ≤ 2.0 s en flagship soportado y ≤ 3.5 s en gama media;
- warm start P95: ≤ 800 ms;
- interacción DTC normal: frame P95 ≤ 16.6 ms en flagship, sin animación continua por tarjeta;
- jank visible: < 5 % con 100 findings;
- memoria: sin crecimiento sostenido después de cerrar escaneo/3D; comparar RSS antes y después;
- overhead de orquestación OBD: ≤ 25 ms por comando, excluyendo transporte/ECU;
- latencia de escaneo: sin demoras decorativas; cada espera debe corresponder a P2/P2*, backoff o transporte real.

Las mediciones se deben capturar en el benchmark autorizado antes de declarar cumplimiento.

## Validación pendiente y límites honestos

No se ejecutaron pruebas ni compilación durante esta implementación por orden explícita del propietario. Tampoco se inventaron trazas ECU.

Antes de declarar equivalencia con scanner profesional se requiere capturar y contrastar hardware real según `tests/diagnostics/hardware-conformance/manifest.json`, incluyendo CAN 11/29-bit, ISO-TP, ISO9141, KWP2000, DoIP, ICE, híbrido, PHEV y BEV.

No se declara implementada cobertura OEM/KWP/OBDonUDS sin packs y fixtures respaldados por fuentes.
