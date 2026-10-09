# Elysium Safety — Auditoría de línea base y conciliación
## Revisión de fuente del 8 de octubre de 2026

**SHA de la línea base inspeccionada:** 76805b54fa62012f9a4bd6457c6698da862d76c0  
**Rama:** feat/elysium-safety-intelligence-plan  
**PR:** https://github.com/jordelmir/MEET-Mecanicos-Especialistas-En-Todo/pull/56  
**Estado del PR al inspeccionarlo:** abierto, en borrador, sin fusionar.  
**Tipo de comprobación:** lectura estática de archivos de GitHub; no es una ejecución local ni una atestación de producción.

> No heredar PASS de un documento o commit anterior. Este documento corresponde a la línea base previa a la corrección visual/funcional de esta iteración. No se ejecutó Gradle local, Vitest local, migraciones PostgreSQL, RPCs, RLS ni pruebas físicas de Android.
>
> **CI observado para esta línea base:** el run [CI #37878488345](https://github.com/jordelmir/MEET-Mecanicos-Especialistas-En-Todo/actions/runs/37878488345) falló en `:app:compileDebugKotlin`. Los errores concretos incluyen el import incorrecto de `rememberSaveable`, imports ausentes de `Box` y `clip`, y la ausencia de `StatusLegendRow`. No se debe distribuir un APK de esta línea base.

## 1. Resultado ejecutivo

El repositorio ya contiene una base sustancial de Safety: formulario de reporte, entidad Room y outbox, almacenamiento local cifrado de adjuntos, hash SHA-256, un verificador de bytes del lado servidor, mapa, expedientes, cronología, observatorio, entidades/afirmaciones/hipótesis/eventos científicos, migraciones de autoridad, una capa de custodia criptográfica y un gateway institucional sujeto a configuración.

Eso no demuestra por sí solo que todos esos componentes estén conectados y funcionando en el entorno desplegado. La evaluación correcta es: **existe código relevante para varios tramos, pero el recorrido completo de extremo a extremo permanece NOT_EXECUTED en esta revisión**.

No se encontró la tabla literalmente llamada safety_scientific_evidence_bridge en las migraciones científicas examinadas. En cambio, los archivos revisados contienen safety_scientific_claim_evidence, safety_scientific_evidence_references, safety_scientific_cases y safety_scientific_case_items. Antes de crear una tabla nueva se debe demostrar la carencia y la relación contractual que falta.

## 2. Componentes revisados

### Captura y persistencia Android

- android/app/src/main/kotlin/com/elysium369/meet/safety/ui/report/SafetyReportViewModel.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/data/local/SafetyReportEntity.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/data/local/SafetyCommandOutboxEntity.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/evidence/SafetyEvidenceEntity.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/evidence/SafetyEvidencePolicy.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/evidence/SafetyEvidenceRepository.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/evidence/SafetyEvidenceVerificationGateway.kt

SafetyReportViewModel modela el flujo de varios pasos, categoría, fuente/relación, relato, fecha de ocurrencia, ubicación, adjuntos, URLs de video y campos científicos opcionales. SafetyReportEntity separa localState, serverState, serverVersion y syncState. SafetyCommandOutboxEntity conserva clave de idempotencia, hash del payload, estado de la orden, contador de intentos y error.

SafetyEvidenceRepository.stage comprueba sesión e ID del propietario, valida MIME contra una allowlist, limita el archivo a 20 MB mediante SafetyEvidencePolicy, lee bytes, cifra el contenido local con AEAD, escribe archivo temporal y lo renombra, calcula SHA-256 sobre los bytes leídos y persiste una entidad local. El modelo contempla uploadState y serverReceipt. SafetyEvidenceVerificationGateway solo transforma MATCH en VERIFIED; MISMATCH/QUARANTINED van a cuarentena y otros resultados continúan pendientes/error.

**Estado:** IMPLEMENTADO EN CÓDIGO, pero flujo completo, descargas, seguridad de Storage, persistencia del recibo y comportamiento de recuperación requieren pruebas de ejecución.

### Mapa territorial y visualización

- android/app/src/main/kotlin/com/elysium369/meet/safety/ui/map/SafetyMapScreen.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/ui/map/SafetyMapViewModel.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/data/local/SafetyPublicPointEntity.kt
- supabase/migrations/20260918100000_safety_public_views.sql
- supabase/migrations/20260920010000_safety_global_citizen_map_and_withdrawal.sql
- supabase/migrations/20260920020000_safety_geographic_timeline_search.sql
- supabase/migrations/20260928090000_safety_publication_firewall_v3.sql
- supabase/migrations/20260928110000_safety_case_publication_authority_v3.sql

En código hay filtros por categoría y rango temporal, separación entre puntos públicos y reportes privados, búsqueda, detalle y visualización de procedencia. SafetyMapViewModel.filterFor aplica reglas locales a geoDisclosure, precisión, límites geográficos, rango y serverVersion. La pantalla muestra datos del punto y vínculos a afirmaciones/hipótesis cuando los encuentra.

**Estado:** PARCIALMENTE IMPLEMENTADO. Deben probarse todas las rutas de publicación y detalle, el origen autoritativo de geoDisclosure, la precisión expuesta, la cobertura y la coincidencia entre política de servidor y presentación Android. La presencia de filtros locales no reemplaza RLS ni la política remota.

### Núcleo científico, estados y relaciones

- android/app/src/main/kotlin/com/elysium369/meet/safety/science/application/SafetyScienceRepository.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/science/data/ScientificAuthorityEntities.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/science/data/SafetyScientificGateway.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/science/data/SupabaseScientificGateway.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/science/domain/EvidenceAssertionState.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/science/domain/TruthStateMapping.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/science/domain/ScientificClaim.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/science/domain/ScientificEntity.kt

En código existen entidades científicas de Room para entidades, afirmaciones, hipótesis y eventos; la capa remota envía comandos mediante RPC y describe PostgreSQL como autoridad. Las migraciones científicas definen tablas de entidades, relaciones, afirmaciones, evidencia, eventos, hipótesis, procedencia, casos, checkpoints y otros registros.

**Hallazgo semántico:** TruthStateMapping convierte ESTIMATED, SIMULATED, NOT_INTEGRATED y NOT_EXECUTED en INSUFFICIENT_EVIDENCE al mapear a EvidenceAssertionState. Es conservador frente a una promoción falsa, pero colapsa diferencias útiles para auditoría. Mantener el estado original y el resultado del gate como campos diferentes antes de ampliar el contrato.

Las migraciones de gates siembran varias funciones científicas desactivadas. La ejecución y configuración de esos gates en un entorno desplegado no se verificó.

### Cadena de custodia e integridad criptográfica

- android/app/src/main/kotlin/com/elysium369/meet/safety/science/provenance/CustodyProtocolV2.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/science/provenance/CustodyChain.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/science/provenance/MerkleTree.kt
- packages/elysium-safety-core/src/ed25519-verifier.ts
- supabase/functions/safety-evidence-verify/handler.ts
- supabase/migrations/20261001010000_safety_evidence_verification_custody_v2.sql
- supabase/migrations/20261004155000_safety_custody_v2_parity.sql

Hay código para canonización de eventos, hashes, raíz de cadena, árbol de Merkle, firma/verificación Ed25519 y comparación de bytes descargados. El verificador TypeScript limita el tamaño y devuelve estados MATCH, MISMATCH, QUARANTINED o ERROR.

**Estado:** IMPLEMENTADO EN CÓDIGO; paridad y pruebas de custodia no se ejecutaron contra este SHA en esta revisión. Se debe verificar también serialización byte-exacta, validación de entrada, clave pública/privada, checkpoint y significado de cada prueba de inclusión. Ningún mecanismo criptográfico prueba la verdad del contenido.

### Seguridad de servidor e interoperabilidad

Archivos relevantes:
- supabase/functions/safety-institutional-gateway/auth.ts
- supabase/functions/safety-institutional-gateway/index.ts
- supabase/functions/safety-device-trust/*
- supabase/migrations/20261004130000_safety_scientific_core_v1.sql
- supabase/migrations/20261004150000_safety_scientific_immutability_and_authority.sql
- supabase/migrations/20261004154000_safety_scientific_authority_infrastructure.sql
- supabase/migrations/20261004160000_safety_scientific_feature_gates_and_rpcs.sql

El gateway de máquina comprueba JWTs con issuer, audience, claves configuradas, firma, duración y una segunda assertion de gateway vinculada. Las migraciones contienen RLS, funciones RPC, restricciones de inmutabilidad y autoridad.

**Estado:** IMPLEMENTADO EN CÓDIGO, INTEGRACIÓN OPERATIVA NO VERIFICADA. Faltan entorno de prueba autorizado, credenciales administradas, pruebas de permisos reales, trazas de ida/vuelta, auditoría de errores y aceptación institucional. No hay base para decir que ya existe intercambio operativo con una autoridad pública.

### Inteligencia financiera de interés público

- android/app/src/main/kotlin/com/elysium369/meet/safety/science/domain/FinancialObservationReviewPolicy.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/science/domain/ProcurementConcentrationRule.kt
- android/app/src/main/kotlin/com/elysium369/meet/safety/science/domain/PublicProcurementRecordNormalizer.kt
- pruebas correspondientes bajo android/app/src/test/kotlin/com/elysium369/meet/safety/science/domain/

La política de revisión excluye observaciones de riqueza visible por sí solas y requiere material documental lícito/verificado, discrepancia específica y dos grupos independientes para ser elegible únicamente a revisión humana. La regla de concentración exige un mínimo de observaciones y ahora detiene la escalada si la procedencia está incompleta o no verificada.

El normalizador actual es lógica de dominio genérica: valida campos y URL y calcula un digest de la representación normalizada. No descarga documentos originales, no tiene planificador de ingesta, no conserva los bytes originales por sí mismo, ni constituye un conector de fuente externo.

**Estado:** PARCIALMENTE IMPLEMENTADO. La ingesta documental real, la autoridad de la procedencia, el almacenamiento y la validación de datasets permanecen pendientes.

## 3. Matriz consolidada

| Requisito | Estado | Evidencia o brecha principal |
|---|---|---|
| Reportes de incidentes estructurados | PARCIALMENTE IMPLEMENTADO | UI/ViewModel, entidad Room, RPC de creación; ejecución E2E no realizada aquí |
| Adjuntos multimedia y cifrado local | IMPLEMENTADO EN CÓDIGO | AEAD, allowlist, límite de tamaño, hash de bytes; prueba física no ejecutada |
| Hash y verificación de bytes en servidor | IMPLEMENTADO EN CÓDIGO | Edge handler de verificación; staging/Storage/RLS no ejecutados |
| Mapa público y reportes privados | PARCIALMENTE IMPLEMENTADO | ViewModel, proyecciones y migraciones; revisión de exposición y prueba negativa pendientes |
| Cronología y expediente | PARCIALMENTE IMPLEMENTADO | Pantallas/modelos; replay y trazabilidad E2E pendientes |
| Cadena evento–claim–fuente–evidencia–hipótesis | PARCIALMENTE IMPLEMENTADO | Modelos, relaciones, referencias y tablas SQL; grafo completo no verificado en ejecución |
| Estados epistemológicos | IMPLEMENTADO EN CÓDIGO, BRECHA SEMÁNTICA | Mapeo conservador colapsa algunos estados del OS |
| Custodia criptográfica y firma | IMPLEMENTADO EN CÓDIGO | Código Kotlin/TS/SQL; pruebas actuales no ejecutadas en esta revisión |
| Reglas contra riqueza visible | IMPLEMENTADO EN CÓDIGO | Policy y tests existentes; resultado de tests en este SHA pendiente |
| Fuente documental externa | DOCUMENTADO / NORMALIZADOR DE DOMINIO | No hay adaptador vivo demostrado |
| AI grounding y defensa de documentos hostiles | PENDIENTE DE VALIDACIÓN | Requiere pruebas adversariales, referencias de fuente y límites de tool |
| Gateway institucional | CÓDIGO DISPONIBLE; NO VERIFICADO EN VIVO | Requiere issuer/JWKS, gateway assertion, autorización y pruebas E2E |
| Preparación del APK actual | NOT EXECUTED EN ESTA AUDITORÍA | No se compiló ni instaló el APK de este SHA |

## 4. Riesgos a priorizar

1. **P0 — Autoridad de estados:** comprobar que ningún cliente ni IA promueve afirmaciones o evidencia sin una transición autorizada de servidor.
2. **P0 — Publicación geográfica:** probar que coordenadas y metadatos sensibles no salen por detalles, exportaciones, notificaciones o endpoints alternativos.
3. **P0 — Recibo remoto:** demostrar que un éxito visible requiere recibo remoto válido; el estado local o una respuesta ambigua nunca bastan.
4. **P1 — Custodia de bytes:** probar original → almacenamiento → verificación → recibo, incluyendo hash incorrecto, archivo corrupto, error y repetición.
5. **P1 — Puente de modelos:** reconciliar los nombres de tablas/relaciones reales antes de crear otro bridge.
6. **P1 — Estado epistemológico:** preservar el estado original y distinguirlo del gate conservador de revisión.
7. **P1 — Fuentes externas:** no afirmar ingestión real hasta que exista fuente legítima, cobertura medida, procedencia de servidor y tests.
8. **P2 — Experiencia visual:** mejorar comprensión de los flujos sin sacrificar accesibilidad, rendimiento o exactitud de métricas.

## 5. Estado de pruebas y CI

En la consulta de línea base, los runs de CI y gates del commit estaban en cola o pendientes y no ofrecían una conclusión final. No se declara PASS en este documento.

Ejecutar localmente cuando haya checkout de la rama:
- cd android && ./gradlew :app:testDebugUnitTest --no-daemon
- cd android && ./gradlew :app:assembleDebug --no-daemon
- cd android && ./gradlew :app:assembleDebugAndroidTest --no-daemon
- bash tests/parity/ci-verify.sh
- npx vitest run tests/safety/*.test.ts

Los comandos anteriores son el protocolo de verificación esperado, no resultados ejecutados durante la auditoría.

## 6. Criterio de cierre de auditoría

La auditoría de código estática establece la línea base y los riesgos; el cierre operativo requiere los tests del SHA final, pruebas SQL/RLS en entorno autorizado, verificación del APK físico y reproducción de un recorrido completo desde el reporte hasta el recibo, mapa y expediente revisable.
