# MEET Automotive Knowledge Fabric

Fecha de corte: 2026-07-26
Versión Android: `4.1.0` (`versionCode 17`)
Estado: implementado; la evidencia final de APK/ADB se documenta en la nota de release.

## Propósito

MEET usa un solo contrato de conocimiento para conectar:

`vehículo → DTC → pruebas → candidatos → reparación → repuestos → 3D → IA`

El contrato no convierte una coincidencia textual, un DTC, una probabilidad o
una malla visual en prueba de falla o compatibilidad. La autoridad siempre
queda limitada por aplicabilidad, procedencia y citas.

## Artefacto canónico

El compilador determinista
`tools/knowledge/build_automotive_knowledge_graph.py` produce dos copias
byte-idénticas:

- `public/knowledge/graph/automotive_knowledge_graph.json`;
- `android/app/src/main/assets/knowledge/graph/automotive_knowledge_graph.json`.

Estado del artefacto:

| Métrica | Valor |
| --- | ---: |
| schema | 1 |
| tamaño | 18,821,087 bytes |
| SHA-256 del archivo | `e432ee19c98d632d63abc5da8971734d67156016dab5924a3379e5a966f888be` |
| hash de contenido canónico | `2617bfa199a0e5b88f9ccb03ed46741d657f7f9fe00ba8aefe8f17926d4ab466` |
| hash del corpus fuente | `7a4a2f2f328bf422ea1c4d987f88eb093e664d6cf4e53609282506d4261d960f` |
| bloques fuente | 74,648 |
| referencias fuente calificadas | 74,648 |
| nodos | 5,446 |
| componentes | 4,759 |
| aristas | 5,411 |
| perfiles | 1 |
| reglas de aplicabilidad | 8 |

Cada cita calificada usa la tupla:

`(sourceDocumentId, blockId, textHash)`

Un `blockId` aislado no puede elevar autoridad. El repositorio Android valida
schema, hash fijado, hash de contenido, IDs, aristas, citas, perfiles y reglas
antes de exponer una consulta. Ante error, devuelve estado inválido y las
acciones materiales fallan cerradas.

## Perfil vehicular de referencia

El perfil revisado es:

`hyundai_accent_verna_2005_1_6_at`

Corresponde al Hyundai Accent/Verna 2005 1.6 automático descrito por el corpus.
No se transfiere automáticamente a otra variante, mercado o motorización.

Reglas negativas importantes:

- MAF: no documentado; el corpus describe MAP + IAT;
- APP electrónico: condicionado por la arquitectura de acelerador;
- EPS y EPB: no documentados para la unidad de referencia;
- turbo, DPF y diésel: no aplican únicamente después de cerrar la variante;
- ADAS: no documentado; inventario aftermarket requiere inspección.

`NOT_DOCUMENTED` no significa ausencia física demostrada. La UI conserva modo
educativo, pero bloquea diagnóstico específico, reemplazo y compra.

## Resolución de aplicabilidad

`VehicleApplicabilityResolver` evalúa:

- identidad activa del vehículo;
- perfil y reglas revisadas;
- VIN estructuralmente válido;
- mercado, motor y transmisión;
- OEM o inventario físico;
- pruebas diagnósticas ligadas al componente;
- conflictos entre evidencia positiva y negativa.

Nombres, aliases y selecciones 3D nunca cuentan como evidencia. Evidencia
`UNVERIFIED` se ignora para decisiones materiales. `EXACT` requiere VIN + OEM
verificados, una tupla cerrada marca/modelo/año/motor/OEM, o una coincidencia
física aprobada conforme al gate del componente.

## Orquestador de reparación

`RepairKnowledgeOrchestrator` entrega un `RepairKnowledgeBundle` estable con:

- observaciones separadas de claims;
- DTC válidos y entradas rechazadas;
- claims citados e inferencias explícitas;
- candidatos canónicos;
- siguiente secuencia de pruebas;
- avisos “no reemplazar todavía”;
- procedimientos, herramientas y seguridad;
- gate de reemplazo/compra;
- objetivos visuales;
- integridad y advertencias.

Para P0230 la secuencia es circuito primero:

1. capturar contexto;
2. comprobar alimentación y tierra;
3. revisar fusible/feed;
4. probar relé y comando;
5. revisar conector/arnés;
6. medir caída de voltaje bajo carga;
7. confirmar corriente, presión y retención;
8. considerar PCM/TSB únicamente al final.

P0230 nunca confirma por sí solo una bomba dañada.

## IA

`ProprietaryGroundedContextBuilder` genera un JSON allowlist, acotado y citado.
Mantiene colecciones separadas para observaciones, claims e inferencias y
declara la política:

`OBSERVATIONS_ARE_NOT_SOURCE_CLAIMS; INFERENCES_REQUIRE_CITATIONS; EXACT_VALUES_REQUIRE_REVIEWED_EVIDENCE`

El contexto remoto no incluye VIN completo, placa, teléfono, GPS, paths locales
ni payloads crudos. Tanto el proveedor principal como el fallback reciben el
mismo contexto estructurado cuando está disponible.

La IA explica y organiza evidencia; no autoriza pruebas activas, no confirma
fallas y no abre compras.

## Repuestos

Las sugerencias heredadas son `INFORMATIONAL` y `requestAllowed = false`.
`PartRequestPublicationPolicy` permite publicar una solicitud DTC/3D únicamente
cuando:

- existe vehículo activo;
- la sugerencia coincide por `canonicalKey`;
- el bundle abre `purchaseAllowed`;
- la compatibilidad es `EXACT`;
- no queda una advertencia `BLOCK`, salvo que el gate exacto del mismo
  componente la sustituya con evidencia más fuerte.

Ofertas sin pruebas declaradas fallan cerradas. Un número de parte escrito o
aftermarket no equivale a OEM verificado.

## Motor 3D

`RepairKnowledgeVisualNavigator` traduce objetivos del grafo a meshes
semánticos existentes. No usa probabilidades del mapa DTC heredado.

La autoridad actual es como máximo:

- `PROCEDURAL_DIAGNOSTIC`;
- `GENERIC_SERVICE_ASSET`;
- `VisualAuthority.GENERIC_SCHEMATIC`.

Todos los objetivos producidos son `isDimensionalModel = false` y muestran
disclaimer. Una aplicabilidad `NOT_DOCUMENTED`, `NOT_APPLICABLE` o
`CONFLICTED` solo permite visualización educativa y no enfoque accionable.

El motor L4 360° restaurado se conserva; esta integración añade autoridad
semántica, no reemplaza el GLB ni lo presenta como CAD OEM.

## Superficies Android

El bundle se carga fuera del hilo principal y se comparte por proceso. Está
visible en:

- DTC;
- Diagnóstico IA;
- Solicitud de repuestos;
- Red de reparación.

La tarjeta común muestra integridad, citas, pruebas pendientes, avisos de no
reemplazo, estado del gate y autoridad 3D.

## Verificación reproducible

```bash
python3 -m unittest tools.knowledge.tests.test_automotive_knowledge_graph -v
npm test
npm run build
bash tests/parity/ci-verify.sh
cd android
./gradlew --no-parallel :app:testDebugUnitTest
./gradlew --no-parallel :app:assembleDebug
```

Prueba física:

```bash
adb devices -l
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.elysium369.meet/.MainActivity
adb shell dumpsys activity activities
adb shell pidof com.elysium369.meet
adb logcat -d | rg "FATAL EXCEPTION|AndroidRuntime|com.elysium369.meet"
```

## Límites honestos

- El perfil Accent/Verna es una referencia, no un VIN decodificado OEM.
- No hay CAD OEM dimensional.
- `observedEvidenceCount = 0`: el grafo no reclama observaciones físicas
  revisadas que no fueron aportadas.
- Los valores exactos siguen requiriendo fuente revisada, condición,
  instrumento y tolerancia.
- Una compilación debug instalada no sustituye una firma de distribución Play.
