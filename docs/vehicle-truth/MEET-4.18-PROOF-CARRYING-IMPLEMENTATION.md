# MEET 4.18 — Proof-Carrying Vehicle Operating System

Estado del código: **implementado, no verificado**. Baseline: `4054ffc87605fd8eb744d65fc013266024c319f2`.
Identidad candidata: MEET 4.17.0 (`versionCode 45`), Room 56. La última
release verificada continúa siendo 4.16.0 hasta que la evidencia automatizada
demuestre lo contrario.

Este documento registra la implementación de la auditoría 4.17 y separa tres
estados que no deben confundirse:

- **IMPLEMENTADO**: existe código o infraestructura revisable.
- **PENDIENTE DE EJECUCIÓN**: requiere build, test, CI o dispositivo; no es PASS.
- **PENDIENTE EXTERNO**: necesita hardware real, autoridad humana, claves o datos
  que no pueden inventarse desde el repositorio.

Por orden del propietario no se compiló ni se ejecutaron pruebas en esta ronda.
Los schemas Room 53–56 tampoco se fabricaron manualmente; deben ser emitidos por
Room durante una compilación autorizada.

## Matriz completa de la orden maestra

| Fase | Entrega | Estado honesto |
|---:|---|---|
| 0 | Estado de prueba generado desde outcomes y bytes | IMPLEMENTADO; `docs/generated/4.17-proof-status.json` está NOT_VERIFIED |
| 1 | Gradle DSL: imports SHA-256/UUID | IMPLEMENTADO; ejecución pendiente |
| 2 | Production guard fail-closed + casos negativos | IMPLEMENTADO; ejecución pendiente |
| 3 | Preflight explícito de SDK/emulador/ADB | IMPLEMENTADO; CI pendiente |
| 4 | Schemas Room 53–56 | PENDIENTE DE BUILD; CI falla si Room no los emite |
| 5 | Migraciones 49/50/52/53/54/55→56 y fixture failure-type | IMPLEMENTADO EN FUENTE; instrumentación pendiente |
| 6 | VersionName 4.17.0 / code 45 | IMPLEMENTADO |
| 7 | Main green-only | DEFINIDO; ruleset remoto pendiente de aplicar con autoridad GitHub |
| 8 | Proof run total y hashes | PENDIENTE DE AUTORIZACIÓN DE TEST/BUILD |
| 9 | Hash exchange v1 histórico + v2 completo | IMPLEMENTADO |
| 10 | `findingSequence` causal monotónica | IMPLEMENTADO; prueba de rollback creada, pendiente de ejecución |
| 11 | Clave pública/certificados/nivel y verificador offline | IMPLEMENTADO; attestation remota pendiente |
| 12 | Vault AES-256-GCM para payload crudo | IMPLEMENTADO; rotación operativa pendiente |
| 13 | Cola limitada, clases de escritura y salud | IMPLEMENTADO; estrés pendiente |
| 14 | Incidentes de comunicación y calidad métrica separados | IMPLEMENTADO |
| 15 | Capability Trust Manifest, revocación y anti-rollback | IMPLEMENTADO; trust root productivo pendiente |
| 16 | JSON canónico versionado y digest domain-separated | IMPLEMENTADO; property tests pendientes |
| 17 | Signed Calibration Artifact + deny-by-default | IMPLEMENTADO; dataset firmado/revisado pendiente externo |
| 18 | Repair Verification Bundle tipado | IMPLEMENTADO |
| 19 | Entrada UX explícita de verificación | IMPLEMENTADO PARCIAL; captura/derivación completa pendiente |
| 20 | Knowledge Use Policy por propósito | IMPLEMENTADO |
| 21 | Contexto raw DTC, ECU, direcciones, calibración y vehículo | IMPLEMENTADO |
| 22 | Repositorio/proyección determinista del evidence graph | IMPLEMENTADO BASE; más fuentes canónicas por integrar |
| 23 | Twin V2 separa evidencia/referencia/hash | IMPLEMENTADO BASE; renderer integral pendiente |
| 24 | UI path-first: circuito/señal/PID/test/medición | IMPLEMENTADO BASE con acción `MEDIR AQUÍ` |
| 25 | Heurísticas visibles como genéricas, no OEM | IMPLEMENTADO; corpus técnico estructurado pendiente |
| 26 | BlackBox usa findings canónicos | IMPLEMENTADO; superficies legacy restantes inventariadas |
| 27 | Contrato de corpus físico | IMPLEMENTADO; capturas certificadas pendientes externas |
| 28 | Validador diferencial fail-closed | IMPLEMENTADO; reportes físicos pendientes |
| 29 | Seeds reales para fuzzing | PENDIENTE DEL CORPUS FÍSICO; fuzz aleatorio existente se conserva |
| 30 | SBOM v2 con hashes, scope, licencias declaradas y relaciones | IMPLEMENTADO; validación/artefactos pendientes de build |
| 31 | Bundletool, splits y medición de tamaño | PENDIENTE DESPUÉS DE BUILD VERDE |
| 32 | Macrobenchmark multi-dispositivo | PENDIENTE DE LABORATORIO FÍSICO |
| 33 | Modularización por strangler | BLOQUEADA INTENCIONALMENTE hasta disponer de golden traces |

## Invariantes incorporadas

### Evidencia y criptografía

- Un hash v2 cubre transporte, protocolo, scope, direcciones, payload hashes,
  outcome, latencia, reintentos, NRC, adaptador, parser y hash anterior.
- Los registros v1 conservan su algoritmo histórico; no se reescriben.
- El orden causal de un finding utiliza `findingSequence`, no wall clock.
- El payload crudo persistente queda cifrado en Android Keystore; Room conserva
  metadatos normalizados y referencia al blob.
- Una firma puede verificarse fuera del teléfono con la clave/cadena registrada;
  sin trust registration el resultado es `UNTRUSTED_KEY`, no válido por omisión.

### Verdad diagnóstica

- La identidad estable incluye vehículo, ECU, namespace, raw identity y
  failure type.
- Presencia/reintentos no redefinen `PERSISTENT`, `PENDING` o `INTERMITTENT`.
- Una fórmula ejecutada no recibe confianza 1.0: calidad de entrada, autoridad,
  completitud e incertidumbre viajan por separado.
- `CALIBRATED` exige artefacto firmado autorizado; cualquier string creado por
  el caller permanece `HEURISTIC`.
- `CONDITIONAL` puede informar, pero no autoriza operación activa, procedimiento
  OEM ni compatibilidad exacta.

### Reparación

- “Procedimiento completado” y “falla resuelta” son verdades distintas.
- `TERMINÉ · VERIFICAR` abre un workflow dedicado y no marca el finding resuelto.
- Solo el motor de verificación puede emitir `VERIFIED_RESOLVED`, y exige mismo
  vehículo/binding, cobertura post-scan y evidencia comparable.

## Gating de CI requerido

El workflow `diagnostic-v2-release-gates.yml` preflighta Gradle y herramientas
Android, ejecuta guards positivos/negativos, unit/property tests, lint, Room,
APK/AAB, parity, secretos, SBOM y migraciones en emulador. El agregador final
solo puede producir `VERIFIED` cuando todos los outcomes son PASS y todos los
artefactos requeridos existen y tienen SHA-256 calculado desde sus bytes.

Protección recomendada de `main`: requerir `release-gates`,
`migration-conformance-emulator` y `proof-status`; bloquear FAILED, CANCELLED y
SKIPPED. El bypass debe ser restringido y auditable. Configurarlo en GitHub es
una mutación administrativa separada y no se representa falsamente como hecha.

## Próxima ejecución autorizada

1. `./gradlew help` y `./gradlew :app:tasks`.
2. Ejecutar unit/property tests y guards negativos.
3. Compilar para que Room emita 53–56 y comprometer únicamente los schemas reales.
4. Ejecutar migraciones instrumentadas y parity.
5. Construir APK/AAB/SBOM, validar, instalar splits y generar hashes.
6. Publicar el proof-status del mismo run.
7. Solo después evaluar la promoción de 4.17 a última release verificada.

El corpus físico, la calibración revisada, la attestation remota y el laboratorio
de rendimiento seguirán pendientes hasta que exista evidencia externa auténtica.
