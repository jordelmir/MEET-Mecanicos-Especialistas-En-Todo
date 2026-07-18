# Auditoria de Automotive Knowledge OS

**Fecha:** 2026-07-16  
**Estado:** cortes 1, 2 y 3 implementados; sync de tres agentes y APK verificados  
**Alcance:** Android, assets offline, TypeScript/RAG, Room, reportes, marketplace, IA, 3D, seguridad y pruebas.

## Resumen Ejecutivo

MEET ya contiene piezas valiosas para construir el Automotive Knowledge OS sin iniciar una arquitectura paralela: `KnowledgePackImporter`, `DtcEngine`, `PriorityEngine`, `MarketplaceGating`, motores de reportes certificados, contratos de marketplace, Room v42 y enlaces 3D. Sin embargo, el conocimiento aun no puede considerarse canonico ni seguro para reparacion porque la trazabilidad de fuentes, aplicabilidad, mediciones y politicas de riesgo no se aplica de manera uniforme.

La estrategia aprobada es aditiva: endurecer el nucleo existente, poner gates deterministas delante de IA/marketplace/hardware y migrar contenido a packs revisables. Ningun dato de los DOCX o de los seeds se publica como verdad tecnica por el hecho de existir en el repositorio.

## Estado De Coordinacion

- Rama activa: `main`.
- El worktree contiene cambios modificados y archivos nuevos de varios frentes: IA, OBD, servicios, monetizacion, 3D, marketplace, reportes y web.
- Las ramas de los worktrees activos `feature/reports-sync-verifier` y `feature/cross-runtime-parity` ya son ancestros de `main`; no existe una union pendiente entre ellas.
- Se restauro y valido `~/.mavis/skills/codex-mavis-sync/` para Codex, Mavis y Google Antigravity. Antigravity consume la misma implementacion canonica desde `~/.gemini/config/skills/codex-mavis-sync`.
- La ejecucion real de `sync.sh --auto` devolvio `SYNC_STATUS=ALREADY_INTEGRATED`. Rama, `HEAD` y hash del estado Git permanecieron identicos antes y despues; el arbol sucio no fue alterado.

## Mapa De Arquitectura Actual

| Superficie | Estado observado | Decision |
| --- | --- | --- |
| Packs Android | `pack_00_core_ontology.json` y `pack_06_dtc_P0230.json` versionados | Extender el formato y validar antes de activar |
| Importador | Valida licencia, IDs, nodos y edges basicos | Agregar aplicabilidad, fuente, mediciones, conflictos e inyeccion documental |
| DTC | Normalizacion y perfiles estructurados | Mantener; corregir serializacion de `profiles` y causas rankeadas |
| Diagnostico | `PriorityEngine`, arboles, freeze frame y live data | Conectar a evidence gates; no convertir ranking en confirmacion |
| Marketplace | Gates basicos y contratos V2 en progreso | Exigir evidencia completa y bloquear `EXACT` sin VIN/OEM |
| Reports | Room, hash, QR, historial y V2 presentes | Reutilizar; no alterar contratos byte-exact sin parity |
| Room | `MeetDatabase` version 42, migraciones centralizadas en `AppModule.kt` | Zona de alto riesgo; no agregar tablas en el primer corte |
| IA | Contexto redaccionado y contratos parciales | La IA explica; no autoriza comandos ni escribe conocimiento canonico |
| 3D | Repositorio visual y `VisualBomAtlas` presentes/en progreso | Resolver aplicabilidad y evidencia antes de abrir Parts |
| Parts/procedures | Assets y modelos nuevos aun no versionados | Tratar como review queue, no como especificacion verificada |
| TypeScript RAG | Seed local amplio en `services/automotiveKnowledgeEngine.ts` | Poner en cuarentena hasta eliminar datos simulados y fuentes no demostradas |

## Hallazgos Por Severidad

### P0 - Bloqueantes De Verdad Tecnica

1. `KnowledgePack.profiles` estaba fuera del constructor serializable. El JSON P0230 incluia el perfil, pero Kotlin lo descartaba silenciosamente por `ignoreUnknownKeys`.
2. El servicio TypeScript de conocimiento contiene seeds que aparentan manuales OEM, licencias adquiridas, hashes, URLs, torques, presiones y pinouts sin evidencia verificable en el repositorio. Tambien tiene un fallback llamado `sha256_mock_*`. Esto viola las reglas de no inventar datos y hash real.
3. Los assets de procedimientos incluyen torques exactos para Accent/Verna sin `source_claim_id`, condicion, tolerancia, edicion o pagina verificable. No deben mostrarse como especificaciones definitivas.
4. El pack P0230 contenia MAF en la ruta usada como caso de Accent/Verna 2005, pese a que el perfil de control exige MAP + IAT y MAF no documentado.
5. El pack P0230 presentaba umbrales numericos conceptuales como criterios de decision. Se deben sustituir por `MeasurementSpecification` revisadas o por mensajes de validacion pendiente.

### P1 - Seguridad Y Autoridad

1. La politica IA observada es un conjunto de booleanos y no modela acciones, targets, evidencia ni reglas de bloqueo.
2. No existe una frontera suficientemente explicita entre explicacion IA, decision diagnostica, autorizacion de prueba activa y escritura CAN/UDS.
3. No habia reglas ejecutables para SRS, rieles/asientos, ISOFIX, top tether, cableado en zonas de airbag, ACC/B+, amplificadores, interfaces de depuracion o alta tension.
4. El importador no inspeccionaba prompt injection embebida en contenido tecnico.

### P1 - Datos Y Migraciones

1. Room llego a version 42 y concentra una cadena extensa de migraciones dentro de `AppModule.kt`; cualquier nueva tabla tiene alto radio de impacto.
2. Hay multiples familias de entidades de conocimiento (`DtcKnowledgeGraphEntities`, `MechanicalKnowledgeEntities`, assets y motores en memoria) sin un contrato canonico comun de fuente/aplicabilidad.
3. `exportSchema = false` reduce la capacidad de probar migraciones y revisar historico de esquema.

### P2 - Calidad Y Operacion

1. El build base estaba bloqueado por una referencia eliminada a `apiKey` en `AiDiagnosticScreen.kt`.
2. El primer Gradle local requirio descargar la distribucion y compilar el modulo completo; las pruebas focalizadas dependen de que toda la app compile.
3. La paridad TS/Kotlin debe mantenerse, aunque este primer corte no cambia contratos byte-exact.
4. La APK integrada ya fue ensamblada; la instalacion/launch en hardware queda pendiente porque `adb devices -l` no lista ningun dispositivo conectado.
5. `npm audit --omit=dev` reporta 0 vulnerabilidades de produccion. La cadena de desarrollo de Vitest 2.1.9 reporta 5 vulnerabilidades y requiere una actualizacion mayor para corregirse; debe tratarse como un cambio separado con pruebas de compatibilidad.

## Corte Vertical 1 - Contrato De Verdad Y Seguridad

Este corte implementa y verifica:

- enums canonicos de aplicabilidad, alcance, confianza y autoridad;
- `TechnicalClaim`, `SourceCitation`, `MeasurementSpecification` y `KnowledgeConflict`;
- serializacion real de perfiles DTC y causas rankeadas;
- `MeasurementSpecValidator` para bloquear valores `VERIFIED` sin fuente, unidad, condicion, instrumento y tolerancia;
- `AutomotiveApplicabilityResolver` que nunca promueve un vehiculo de referencia a hecho del vehiculo objetivo;
- `KnowledgeConflictDetector` para presencia/ausencia contradictoria;
- `DiagnosticTruthEngine` para mantener un DTC como hipotesis hasta completar evidencia;
- `ProcedureSafetyEngine` para SRS, asientos, interior, infotainment, combustible, refrigerante y HV;
- `ActiveTestAuthorizationEngine` separado de la IA;
- defensa inicial contra prompt injection y PII en nombres de nodos importados;
- gate P0230 que exige electricidad, conector, tierra, presion y corriente antes de ofrecer bomba.

## Corte Vertical 2 - Ingesta DOCX Y Perfil Accent

Se implemento `tools/knowledge/` como pipeline reproducible y sin dependencias externas:

- snapshot inmutable y SHA-256 real del DOCX original;
- extraccion ordenada de parrafos, tablas, revisiones eliminadas y media embebida;
- normalizacion conservadora: todo entra como `UNVERIFIED` y ninguna medicion se publica;
- deteccion de prompt injection que trata el documento como dato no confiable;
- polaridad por entidad para no confundir, por ejemplo, ausencia de EPS con presencia de direccion hidraulica;
- alcance tabular ambiguo que impide transferir el ano o vehiculo de una columna de referencia;
- contradicciones abiertas, sin resolucion automatica, y cola con roles de revision;
- schema y validador semantico compartido por los packs Android;
- `pack_07_hyundai_accent_verna_2005_profile.json` sin valores numericos, sin claims `VERIFIED` y con VIN/OEM/inspeccion donde corresponde.

Resultado de los documentos aportados:

| Fuente | SHA-256 | Candidatos | Mediciones candidatas |
| --- | --- | ---: | ---: |
| `Document (16).docx` | `09f2926a22542a4e7be24e50f2a4f4c42674f32958e8e541683fbb0cf76352d7` | 10,322 | 417 |
| `Document (17).docx` | `baf4add3f22202fc7d66f7b7f4aee549d90780f1891da6fa66ffbc2db1820824` | 7,134 | 147 |

La cola final tiene 17,456 elementos, cero publicables y cero contradicciones demostradas. Hay 62 claims de presencia/ausencia sin alcance explicito; el pipeline no les asigna por defecto el perfil Accent/Verna. Que no haya contradicciones detectadas no equivale a aprobacion tecnica: todo permanece en `REVIEW_REQUIRED`.

## Corte Vertical 3 - Compatibilidad, Cotizaciones E IA Externa

Este corte cierra tres rutas por las que una inferencia podia aparentar mayor certeza que la evidencia:

- VIN validado con exactamente 17 caracteres permitidos en Kotlin y TypeScript; un VIN parcial o con `I`, `O` o `Q` genera bloqueo y nunca cuenta como evidencia de `EXACT`;
- cotizaciones `EXACT` limitadas a VIN valido + OEM/numero de parte, o tupla cerrada marca/modelo/ano/motor/OEM;
- evidencia estructurada del vehiculo conectada desde la solicitud al formulario de cotizacion, sin usar notas libres como prueba;
- sugerencias P0230 sin sensor de presion inventado para el perfil Accent y sin afirmaciones estadisticas o de costo no citadas;
- P0171 sin la recomendacion incorrecta de tapa de combustible y con MAF/MAP condicionado al equipamiento real;
- salida externa de IA migrada a un contrato JSON cerrado con claves, tipos, limites y enums estrictos;
- rechazo de campos extra, comandos de sistema, VIN completo, TSB/OEM no sustentados, procedimientos desconocidos, sobreconfianza, mediciones inventadas y reemplazos de piezas bloqueadas;
- parser de IA basado en `kotlinx.serialization.json`, verificable tanto en JVM como en la APK, en lugar del stub Android de `org.json` usado por las pruebas locales.

## Siguientes Cortes Recomendados

1. Migrar `parts_ontology_es.json` y `suspension_procedures.json` al contrato canonico; eliminar torques no demostrados o marcarlos pendientes.
2. Retirar todos los seeds tecnicos ficticios del RAG TypeScript y prohibir fallbacks de hash no criptograficos.
3. Integrar applicability/evidence/safety con `VisualBomAtlas`, Parts Marketplace, reportes e historial.
4. Extraer las migraciones de Room a una estrategia verificable con schemas exportados y pruebas de upgrade.
5. Conectar un dispositivo Android autorizado y ejecutar instalacion, launch, proceso vivo y revision de `logcat` sobre la APK integrada.

## Criterio De Rollback

El corte es aditivo y no migra datos. Para revertirlo basta retirar los nuevos contratos/motores y restaurar las extensiones del pack. No toca hashes de reportes, tablas Room, Supabase ni contratos de parity.

## Verificacion Acumulada

- Packs 00, 06 y 07 validados contra schema y reglas semanticas.
- Pipeline documental y validador: 14 pruebas verdes.
- Suite `core.knowledge.*`: verde, incluido el perfil Accent y los safety gates.
- Build web Vite de produccion: verde.
- TypeScript `tsc --noEmit`: verde.
- Suite web Vitest: 164 pruebas verdes.
- Suite Android completa `:app:testDebugUnitTest`: verde.
- Validador IA: acepta mediciones locales citadas y rechaza schema extra, comandos, TSB inventado, sobreconfianza, mediciones fabricadas, VIN completo y reemplazo prematuro.
- Paridad TS/Kotlin: hash P0230 `71b393aeb4ddbb23dc4fdeb3720450a91734ebf567a0698620b273f4b545072e`, coincidencia exacta.
- Auditoria npm de produccion: 0 vulnerabilidades. Permanecen 5 vulnerabilidades en tooling de desarrollo de Vitest/Vite; corregirlas exige una actualizacion mayor separada.
- Skill de sync: validador oficial, sintaxis Bash y prueba aislada de tres worktrees verdes; instalado tambien para Google Antigravity mediante enlace a la implementacion canonica.
- Sync real: `ALREADY_INTEGRATED` para ambos worktrees activos, sin cambios en rama, `HEAD` ni estado Git.
- APK debug ensamblada correctamente en `android/app/build/outputs/apk/debug/app-debug.apk` (75 MB), SHA-256 `de4fe9e9647fb10c99b15ed44872a50bc6fb0adde665c24742f7dff3fb007fe2`.
- Prueba ADB pendiente: no habia dispositivo Android conectado al momento de la verificacion.
