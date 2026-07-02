# Mavis Build Diagnosis Report

> Generado por Loop D-adjacent (build verification) ejecutado el 2026-07-02 ~03:12 CST.
> Tipo: **No-fix diagnosis** — el bloqueo identificado requiere creación de 19 entity classes
> que NO existen en el repo. Mavis no las inventa.

## Resumen ejecutivo

**El build de MEET Android está ROTO** desde hace días (presumiblemente desde el merge del branch
con las features de "Vanguard Telemetry Intelligence" y "Vanguard Commerce Trust Core").

- **Síntoma**: `:app:kaptDebugKotlin FAILED` en 32 segundos
- **Causa raíz**: `MeetDatabase.kt` referencia 19 `@Entity` classes que no existen en el código
- **Severidad**: **High** (bloquea todo el ciclo de desarrollo Android)
- **Tipo de fix requerido**: Crear 19 entity classes nuevas + verificar si hay DAO que las usen

## Comando ejecutado

```bash
cd android && ./gradlew test --no-daemon --offline
```

(Modo `--offline` para no depender de red; primer build con cache local.)

## Resultado

```
> Task :app:kaptDebugKotlin FAILED
> Task :app:kaptGenerateStubsReleaseKotlin

BUILD FAILED in 32s
35 actionable tasks: 24 executed, 11 up-to-date
```

## Diagnóstico técnico

`MeetDatabase.kt` declara 19 entidades Room con **fully-qualified names** (sin `import`) que
no tienen archivo Kotlin correspondiente en `data/local/entities/`.

### Entidades faltantes (las 19)

**Elysium Vanguard Telemetry Intelligence** (15):
- `VanguardObdSessionEntity`
- `ObdPidSampleEntity`
- `ObdCommandLogEntity`
- `EcuFailureEventEntity`
- `CompatibilityRuleEntity`
- `VehicleProfileSnapshotEntity`
- `Mode06ResultEntity`
- `FreezeFrameEntity`
- `DerivedMetricEntity`
- `HealthScoreEntity`
- `RepairRecommendationEntity`
- `AiDiagnosticResultEntity`
- `VehicleHistoryEntity`
- `PdfReportEntity`
- `AuditLogEntity`
- `FixRolloutEntity`

**Elysium Vanguard Commerce Trust Core** (3):
- `VanguardEventEntity`
- `MarketplaceLedgerEntryEntity`
- `VanguardOutboxEntity`

### Causa probable

Estas entidades fueron declaradas en `MeetDatabase.kt` como **schema planning** o como parte
de un WIP inconcluso. El proyecto **MEET** tiene 42 archivos modificados + 47 untracked
(guardados en `stash@{0}: wip-pre-mavis-bootstrap-2026-07-02`) que probablemente incluyen
los archivos de entidades — pero como el stash no se ha popeado, el build falla.

**Hipótesis a verificar** (no confirmada):

1. Los archivos `.kt` de estas entidades existen en el working tree del usuario pero
   fueron stasheados al inicio del bootstrap.
2. Los archivos nunca se crearon (schema especulativo).

### Verificación recomendada para Jor

```bash
git stash show -p stash@{0} | grep -E "AuditLogEntity|VanguardOutboxEntity|FixRolloutEntity" | head
```

Si los archivos están en el stash → `git stash pop` los restaura y el build puede pasar.

Si NO están → hay que crearlos desde cero (no es un fix de 1 línea, es un sprint).

## Verificaciones realizadas

1. ✅ Rama actual: `mavis/diagnose/build-broken-telemetry-entities` (creada)
2. ✅ Java 17 + Android SDK presente en `~/Library/Android/sdk`
3. ✅ Gradle wrapper 8.5 funcional
4. ✅ Build ejecutado: FAILED en `:app:kaptDebugKotlin`
5. ✅ Entidades faltantes catalogadas: 19
6. ✅ Working tree limpio (solo `?? android/app/src/main/kotlin/com/elysium/` untracked)

## NO se modificó código de producto

**Razón**: Crear 19 entity classes sin entender:
- Qué columnas necesita cada una
- Si tienen DAO asociado
- Si el schema es estable o todavía está cambiando

...sería **inventar archivos**, lo que viola la regla dura del master prompt:

> *"Si Mavis inventa archivos, falla."* — `.mavis/AGENT.md` sección 17

## Acción recomendada para Jor

**Opción A — Verificar stash** (5 min):
```bash
git stash show stash@{0} | grep -c "Entity.kt"
```

Si el resultado es > 15 → los archivos están ahí. Basta con `git stash pop` y re-correr
`./gradlew test`. Probablemente el build pasa.

**Opción B — Si el stash NO los tiene** (varias horas):
Trabajo de Android engineer real:
1. Diseñar schema de cada entidad (qué columnas, qué índices, qué FKs)
2. Crear las 19 entity classes con sus `@Entity`, `@PrimaryKey`, `@ColumnInfo`
3. Verificar si hay DAOs ya escritos que las referencien
4. Crear migrations de Room si la versión de DB cambió
5. Agregar tests de DAO

**No es un fix de Loop C.** Es un sprint entero.

## Recomendación Mavis

**Esperar a que Jor verifique el stash primero.** Es la respuesta más probable (5 min)
y evita crear 19 archivos especulativos.

Si Jor confirma que NO están en el stash, **entonces** se planifica el sprint de creación.

## Riesgos asociados a este build roto

- **RIESGO-009 — Build completamente roto** — `.mavis/memory/known-risks.md`
  - **Severity**: Critical
  - **Impact**: No se puede correr `./gradlew test`, no se puede validar CI, no se puede
    producir APK release, no se puede validar ningún PR mergeado a `main`.
  - **Detection**: Cualquier intento de build falla en <30s
  - **Mitigation propuesta**: Verificar stash → o sprint de entity creation
  - **Owner**: Jor (decisión sobre stash) + Android engineer (ejecución si no está en stash)

## Lo que SÍ funcionó del bootstrap

Aclaración importante: **El bootstrap del Loop System (`.mavis/`, `.github/`, `scripts/`)
NO causa este build failure.** Esos archivos están en su propia rama (`feature/mavis-loop-bootstrap`)
y solo tocan:

- `.mavis/AGENT.md` y derivados
- `.github/workflows/*.yml`
- `scripts/mavis-loop.sh`

Cero archivos Kotlin modificados. El bootstrap **no rompe nada**.

## Reporte generado por

Mavis — Continuous Engineering Loop Agent
Loop: D-adjacent (build verification, no security)
Branch: `mavis/diagnose/build-broken-telemetry-entities`
PR: NO creado (no hay fix que PR-ificar)