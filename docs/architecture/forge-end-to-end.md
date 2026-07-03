# Arquitectura Forge — Vista end-to-end

Diagrama del flujo completo desde `FeaturePlan` (definido en código o JSON)
hasta píxeles en pantalla, con render isométrico 3D interactivo.

```
┌──────────────────────────────────────────────────────────────┐
│                        Catálogo                               │
│                                                               │
│  domain/FeaturePresets.kt   PresetId.ENGINE_BLOCK, ... (33)   │
│  domain/FeaturePlan.kt      Single, Linear, Circular,         │
│                             Composite (4 implementaciones)      │
│  domain/EngineCatalog.kt    v8, v12, electric, hybrid, ...    │
│                             (15 templates pre-compuestos)       │
└─────────────────────────────────┬────────────────────────────┘
                                  │
                                  ▼
┌──────────────────────────────────────────────────────────────┐
│                     Plan Application                         │
│                                                               │
│  engine.instantiate()                                         │
│      │                                                        │
│      ├─ SingleFeaturePlan.instantiate()  →  [ParametricFeature]│
│      ├─ LinearArrayPlan.instantiate()   →  [ParametricFeature]│
│      ├─ CircularPatternPlan.instantiate() → [ParametricFeature]│
│      └─ CompositePlan.instantiate()                       │
│            └─ flatMap { it.instantiate() }                  │
│                                                               │
│  → result: List<ParametricFeature>                             │
│     - type: FeatureType                                       │
│     - parameters: Map<String, Double>                          │
│     - position: Vector3Data                                    │
│     - id: "{type}_{display}_{axis?}_@x_y_z"  (determinista)   │
└─────────────────────────────────┬────────────────────────────┘
                                  │
                                  ▼
┌──────────────────────────────────────────────────────────────┐
│                   ForgePart state                             │
│                                                               │
│  ForgePart.featureTree: List<ParametricFeature>               │
│  ForgeArtifactRepository._parts: StateFlow<Map<id, ForgePart>>│
│    ↑ auto-save con debounce 1500ms                              │
│    ↑ onCleared() sync save                                     │
└─────────────────────────────────┬────────────────────────────┘
                                  │
                                  ▼
┌──────────────────────────────────────────────────────────────┐
│              ForgeGeometryCompiler (existente)                │
│                                                               │
│  compilePart(part): GeometryCompileResult                      │
│      ├─ validateGeometry(part) → errors + warnings             │
│      ├─ generate primitives (box/cylinder/cone/...)           │
│      ├─ MeshAccumulator.merge(faces, vertices)                │
│      └─ → CompiledMesh { vertices, faces, min, max }          │
│                                                               │
│  → result.mesh.vertices: List<CompiledVertex>                 │
│  → result.mesh.faces: List<CompiledFace>                      │
└─────────────────────────────────┬────────────────────────────┘
                                  │
                                  ▼
┌──────────────────────────────────────────────────────────────┐
│                  UI: Viewport3D                               │
│                                                               │
│  remember(part) { compiler.compilePart(part).mesh }           │
│                                                               │
│  ┌─────────────────────────┐                                  │
│  │  IsometricMeshRenderer   │                                  │
│  │  - yaw / pitch (state)  │                                  │
│  │  - drag gesture → yaw   │                                  │
│  │  - slider → pitch       │                                  │
│  │  - double-tap → reset   │                                  │
│  └─────────────┬───────────┘                                  │
│                ▼                                                │
│  ┌─────────────────────────┐                                  │
│  │  prepareTriangles(mesh)  │                                  │
│  │  - center mesh            │                                  │
│  │  - rotate yaw + pitch     │                                  │
│  │  - project isometric      │                                  │
│  │  - sort by avgZ desc      │                                  │
│  │  - apply Lambert lighting │                                  │
│  └─────────────┬───────────┘                                  │
│                ▼                                                │
│  ┌─────────────────────────┐                                  │
│  │  Canvas DrawScope        │                                  │
│  │  - backface cull (cross) │                                  │
│  │  - drawPath(fill)        │                                  │
│  │  - drawPath(wireframe)   │                                  │
│  └─────────────────────────┘                                  │
└──────────────────────────────────────────────────────────────┘


═══════════════════════════════════════════════════════════════════════
            PERSISTENCIA (en memoria — ver ADR backlog)
═══════════════════════════════════════════════════════════════════════

  ForgeArtifactRepository (singleton, thread-safe)
    - _parts: MutableStateFlow<Map<String, ForgePart>>
    - _assemblies, _vehicles, _materials, _processes, _manuals
    - bootstrapReport: MutableStateFlow<BootstrapReport?>
    - savePart(part) / deleteArtifact(id) / getPart(id)
    - Mutex.withLock en cada write
    - ⚠️ LIMITACIÓN V0: vive en RAM. Kill process = data lost.
       → ADR backlog "persistencia disco"

═══════════════════════════════════════════════════════════════════════
            SERIALIZACIÓN JSON
═══════════════════════════════════════════════════════════════════════

  FeaturePlan @Serializable
    ├─ SingleFeaturePlan:    { preset, positionOffset }
    ├─ LinearArrayPlan:      { preset, count, spacing, axis }
    ├─ CircularPatternPlan:  { preset, count, radius, axis, startAngleRad }
    └─ CompositePlan:        { name, children, centerOrigin }

  ←→ JSON via kotlinx.serialization
  ←→ roundtrip preserva todas las features (test: 10/10)

═══════════════════════════════════════════════════════════════════════
            TESTING
═══════════════════════════════════════════════════════════════════════

  Unit tests (JVM):  307 ✅
    - FeaturePresetsTest         (16 tests)
    - FeaturePlanTest             (21 tests)
    - FeaturePlanSerializationTest (10 tests)
    - EngineCatalogTest           (13 + 8 nuevos)
    - IsometricMeshRendererTest    (7 tests)
    - ForgeArtifactRepositoryTest (+3 nuevos bootstrapReport)
    - Knowledge OS (pre-session)   (39 tests)

  Pendientes (ver backlog):
    - UI snapshot tests (Compose)
    - Property-based tests
    - Integration tests (save → kill → restore)
    - Benchmarks de performance
```

## Cómo extender (para nuevos tipos de plan)

```kotlin
// 1. Crear nuevo FeaturePlan
@Serializable
internal data class HelicalPatternPlan(
    val preset: FeaturePreset,
    val turnCount: Int,
    val pitchMm: Double,
    val radius: Double,
    val axis: LinearArrayPlan.Axis
) : FeaturePlan {
    override fun instantiate(position: Vector3Data): List<ParametricFeature> {
        // Implementación siguiendo el patrón de las 4 implementaciones existentes.
    }
}

// 2. Agregar al EngineCatalog (si aplica)
// 3. Test en FeaturePlanTest (siguiendo el patrón)
// 4. Si necesita UI: agregar tile en EnginesRow via PresetsAndPlansRow
// 5. Si usa ID uniqueness: añadir parámetros al idKey()
// 6. Si va a disco: agregar a forge-seed-library.json o similar
```

## Cómo debuggear

1. **Feature no aparece**: verificar `forge_engine_logcat`. Logs en `ForgeEntry`
   y `ForgeArtifactRepository` muestran bootstrap, save failures.
2. **Mesh no se compila**: `ForgeGeometryCompiler.compilePart` valida primero.
   Errores van en `GeometryValidationResult.errors`.
3. **Renderer glitch**: el pre-cómputo de triángulos está memoizado por
   `(mesh, yaw, pitch, enableLighting)`. Para forzar recompute, cambiar
   algún parámetro o rotar el part.
4. **ID collision**: `FeaturePlan.instantiate()` loggea el ID generado. Si
   ves duplicados, falta un parámetro en el `idKey()` correspondiente.
