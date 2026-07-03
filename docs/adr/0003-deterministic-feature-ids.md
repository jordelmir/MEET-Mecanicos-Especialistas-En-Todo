# ADR 0003: IDs deterministas vs UUID para ParametricFeature

**Estado**: Aceptado
**Fecha**: 2026-07-02
**Contexto**: Post-PR forge-improvements

## Contexto

`ParametricFeature.id: String` es el identificador único de cada feature
dentro de una `ForgePart.featureTree`. Necesita ser:

1. **Único** dentro de la pieza (no colisiones).
2. **Determinista**: misma configuración → mismo ID.
3. **Estable** bajo operaciones reversibles (undo/redo no debería cambiar IDs
   si los params no cambiaron).
4. **Serializable** a JSON para que `ForgePart` viaje a backend sin transformación.

## Opciones evaluadas

### Opción A — UUID v4 generado al crear la feature
```kotlin
val feature = ParametricFeature(
    id = UUID.randomUUID().toString(),  // "f47ac10b-58cc-4372-a567-0e02b2c3d479"
    ...
)
```

- ✅ Garantía criptográfica de unicidad global.
- ❌ No determinista: misma feature generada 2 veces → IDs distintos.
- ❌ Rompe diffs JSON (cada save produce IDs nuevos).
- ❌ Backend no puede deduplicar por contenido.

### Opción B — UUID v5 derivado de hash del contenido
```kotlin
val feature = ParametricFeature(
    id = UUID.nameUUIDFromBytes("$type$name$params$position".toByteArray()).toString(),
    ...
)
```

- ✅ Determinista si derivamos del contenido.
- ❌ Nombre ilegible para humanos.
- ❌ Backend depende de la convención de hashing.

### Opción C — ID semántico legible
```kotlin
val feature = ParametricFeature(
    id = "cylinder_piston_r80_3@0_120_0",
    type = FeatureType.CYLINDER,
    ...
)
```

- ✅ Legible: el ID describe la posición y el preset.
- ✅ Determinista: el contenido (preset + position + spacing + axis + ...)
  define el ID unívocamente.
- ✅ Diffs JSON estables entre saves.
- ✅ Backend puede deduplicar trivialmente.
- ✅ Debug-friendly: el ID en logs te dice qué feature es.
- ❌ Si dos features tienen contenido idéntico (mismo preset, mismo params,
  misma posición), chocan. Pero ese caso es degenerado — el compilador
  geométrico los colapsaría en una sola cara.

## Decisión

**Opción C — IDs semánticos legibles** basados en `(preset + position + geometry_params)`.

### Formato concreto por tipo de plan

| Plan | Formato de ID |
|------|---------------|
| `SingleFeaturePlan` | `{type}_{displayName}_single_{x}_{y}_{z}` |
| `LinearArrayPlan`   | `{type}_{displayName}_{axis}_s{spacing}_n{count}@{index}@{x}_{y}_{z}` |
| `CircularPatternPlan` | `{type}_{displayName}_circ_r{radius}_${index}@{x}_{y}_{z}` |

### Reglas

- **Mismo input → mismo output**: cumple "determinismo" sin ambigüedad.
- **Distinto input → IDs distintos**: garantiza unicidad dentro de un composite.
- **`displayName` sanitizado**: lowercase, guiones bajos, sin tildes (a→a, é→e, ñ→n).
- **PosKey**: `x_y_z` con `.toInt()`. Suficiente para distinguir; precisión sub-pixel
  no relevante para human-readable IDs.

### Trade-offs documentados

- **Diff size**: los IDs son más largos que UUIDs (50–80 chars vs 36). Acceptable.
- **Hash collisions**: dos features con `(preset, params, position)` idénticas tendrán
  el mismo ID. Esto es deseable: si dos features son equivalentes, son la misma feature.

## Consecuencias

- Backend ahora puede deduplicar por ID.
- Logs legibles (`"cylinder_piston_3@120_0_0"` en lugar de UUIDs).
- Tests verifican que instancias múltiples produzcan IDs distintos dentro de un composite.
- JSON snapshots más estables (mismas features = mismos IDs = mismos diffs).

## Histórico

- **2026-07-02**: Implementado en `domain/FeaturePlan.kt`. Tests en
  `FeaturePlanTest` (21 tests, todos verdes) + `FeaturePlanSerializationTest`
  (10 tests). Bug colateral descubierto y arreglado: el ID inicialmente no
  incluía posición ni parámetros, causando colisiones en transmisiones y
  hybrid powertrains.
