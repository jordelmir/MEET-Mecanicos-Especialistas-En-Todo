# ADR 0001: Multi-Feature Plans via Composición

**Estado**: Propuesto
**Fecha**: 2026-07-02
**Contexto**: Post-PR #2 (forge-editor-improvements)

## Contexto y problema

El modelo actual de `FeaturePreset` cubre 33 piezas automotrices single-feature (un `ParametricFeature` por preset). Esto es suficiente para piezas simples pero no modela sistemas reales:

- **Motores V6 / V8 / V10 / V12**: necesitan N cilindros en patrón circular.
- **Motores bóxer**: cilindros opuestos horizontalmente.
- **Cigüeñal + bielas + pistones**: conjuntos repetitivos.
- **Transmisión**: engranajes, ejes sincronizados.
- **Suspensión**: conjuntos de muelle + amortiguador.

Hoy no hay forma de crear estas piezas sin escribir código Kotlin nuevo cada vez. El usuario pidió "todos las de un carro" — necesitamos arquitectura que crezca sin reescritura.

## Opciones consideradas

### Opción A — Colección de casos especiales
Agregar `FeatureType.CIRCULAR_PATTERN` (ya existe) + `LINEAR_PATTERN` + `COMPOUND_PART` y un nuevo método en el compilador por cada uno.

- ❌ N tipos, N compiladores, N tests. Explosión combinatoria.
- ❌ Componer un motor V8 con cigüeñal requiere reescribir lógica.
- ❌ No extensible a "transmisión 6-velocidades" sin código nuevo.

### Opción B — DSL interno
Mini-lenguaje embebido en JSON, tipo:

```json
{ "compile": { "for_each": { "in": "cylinders", "do": { "type": "CYLINDER", ... } } } }
```

- ❌ Parser propio, debugger propio, sintaxis a diseñar.
- ❌ Errores difíciles de reportar al usuario final.
- ✅ Potencialmente poderoso.

### Opción C — `FeaturePlan` sealed interface + composición (elegida)
Cada "plan" sabe cómo generar sus `ParametricFeature`s. Los planes se componen:

```kotlin
sealed interface FeaturePlan {
    fun instantiate(position: Vector3Data, rotation: Quaternion): List<ParametricFeature>
}

data class SingleFeaturePlan(val preset: FeaturePreset) : FeaturePlan { ... }
data class CircularPatternPlan(val preset: FeaturePreset, val count: Int, val radius: Double, val axis: Axis) : FeaturePlan { ... }
data class LinearArrayPlan(val preset: FeaturePreset, val count: Int, val spacing: Double, val axis: Axis) : FeaturePlan { ... }
data class CompositePlan(val name: String, val children: List<FeaturePlan>) : FeaturePlan {
    override fun instantiate(...) = children.flatMap { it.instantiate(...) }
}
```

- ✅ Composición recursiva: `CompositePlan(CompositePlan(LinearArray, CircularPattern))`.
- ✅ Extensible: añadir `HelicalPatternPlan`, `MirrorPlan`, etc. sin tocar los existentes.
- ✅ Testeable: cada `Plan` es función pura sobre parámetros.
- ✅ Serializable: `@Serializable` permite guardar/cargar templates JSON.
- ❌ Costo inicial: 4 data classes + tests.

## Decisión

**Opción C — `FeaturePlan` con composición**.

### API pública

```kotlin
sealed interface FeaturePlan {
    /**
     * Genera la lista de ParametricFeature que este plan representa.
     * Determinista: mismas entradas → misma salida.
     */
    fun instantiate(position: Vector3Data = Vector3Data.ZERO): List<ParametricFeature>
}

data class SingleFeaturePlan(
    val preset: FeaturePreset,
    val positionOffset: Vector3Data = Vector3Data.ZERO
) : FeaturePlan

data class LinearArrayPlan(
    val preset: FeaturePreset,
    val count: Int,
    val spacing: Double,
    val axis: LinearArrayPlan.Axis
) : FeaturePlan {
    enum class Axis(val unit: Vector3Data) {
        X(Vector3Data(1.0, 0.0, 0.0)),
        Y(Vector3Data(0.0, 1.0, 0.0)),
        Z(Vector3Data(0.0, 0.0, 1.0))
    }
}

data class CircularPatternPlan(
    val preset: FeaturePreset,
    val count: Int,
    val radius: Double,
    val axis: CircularPatternPlan.Axis,
    val startAngleRad: Double = 0.0
) : FeaturePlan {
    enum class Axis {
        Y_PERPENDICULAR  // circulo en plano XZ, eje Y arriba (motores V)
        // Otros ejes si se necesitan
    }
}

data class CompositePlan(
    val name: String,
    val children: List<FeaturePlan>,
    val centerOrigin: Boolean = false  // si true, los offsets se centran en (0,0,0)
) : FeaturePlan
```

### Ejemplo: motor V8

```kotlin
val v8 = CompositePlan(
    name = "V8 4-stroke engine block",
    children = listOf(
        SingleFeaturePlan(engineBlockPreset),
        CircularPatternPlan(
            preset = cylinderBorePreset,
            count = 8,           // V8
            radius = 80.0,       // mm
            axis = CircularPatternPlan.Axis.Y_PERPENDICULAR,
            startAngleRad = 0.0  // first cylinder arriba
        )
    )
)

// Aplicar: v8.instantiate() devuelve 9 ParametricFeatures (1 block + 8 cylinders)
```

### Ejemplo: motor en línea 4 cilindros

```kotlin
val inline4 = CompositePlan(
    name = "Inline 4 engine",
    children = listOf(
        SingleFeaturePlan(engineBlockPreset),
        LinearArrayPlan(
            preset = cylinderBorePreset,
            count = 4,
            spacing = 88.0,
            axis = LinearArrayPlan.Axis.X
        )
    )
)
```

### Reglas arquitectónicas

1. **Pureza**: `instantiate()` es pura (sin I/O, sin random, sin estado mutable).
2. **Determinismo**: mismos inputs → mismo output (cubierto por tests).
3. **Composicionalidad**: cualquier `Plan` puede contener otros planes vía `CompositePlan`.
4. **Serializabilidad**: cada `data class` lleva `@Serializable` para JSON.
5. **No new deps**: vive 100% en el módulo `forge` existente.
6. **Backward-compat**: `FeaturePreset` (single) sigue funcionando. `FeaturePlan.SingleFeaturePlan(preset)` envuelve uno.

### Plan de tests

- `SingleFeaturePlan` → 1 feature, offset respetado.
- `LinearArrayPlan` → N features, posiciones equiespaciadas en eje.
- `CircularPatternPlan` → N features, posiciones en círculo, primer ángulo respetado.
- `CompositePlan` → suma de children, orden preservado.
- **Determinismo**: mismo plan, 100 invocaciones → mismo output.
- **Composicionalidad**: `CompositePlan(ListOfComposedPlans)` funciona.
- Casos límite: `count=0`, `radius=0`, `spacing=0`, planes vacíos.

## Consecuencias

### Positivas
- Motores V6/V8/V10/V12 sin código nuevo.
- Block, transmission, suspension bodies compuestos.
- Templates compartibles entre piezas (definir `engineV8` una vez, reusar en proyectos).
- Serialización a JSON: el catálogo de templates crece por contenido, no por código.

### Negativas / Riesgos
- Curva de aprendizaje: el usuario de la UI necesita entender "planes" vs "presets".
- Backend sync: si los planes viven en JSON, hay que pensar validación.
- Riesgo de "feature creep": los planes podrían querer demasiada lógica (constraints, dependencies entre features). Se empieza con composición simple; si crece, se introduce `Plan.withConstraints(...)` en un ADR futuro.

### Compatibilidad
- `FeaturePreset` (single) sigue existiendo.
- La UI existente (FeaturePresetsRow + taps) sigue igual.
- Plan UI es un slider/tab adicional — no rompe el flujo existente.
EOF
