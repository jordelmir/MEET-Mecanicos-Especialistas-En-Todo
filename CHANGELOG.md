# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.1-forge-improvements] - 2026-07-02

### Added — Persistencia, export y privacidad

- **Disk persistence de partes de usuario** (`data/JsonFileStore.kt`):
  - `JsonFileStore<T>` genérico con escritura atómica (write-to-temp + rename) y `Mutex` para evitar condiciones de carrera entre coroutines.
  - `JsonFileStoreBridge` para `Map<String, ForgePart>` con `MapSerializer` polimórfico.
  - `ForgeArtifactRepository.loadUserPartsFromDisk(context)` / `saveUserPartsToDisk(context)` como API pública.
  - `ForgeEntryScreen` carga desde disco en `LaunchedEffect` y guarda en `DisposableEffect(ON_STOP)` con `runBlocking(IO)` para garantizar flush antes de destruir el proceso.

- **STL exporter** (`domain/StlExporter.kt`):
  - `toBinaryStl(mesh: CompiledMesh): ByteArray` — formato binario estándar (80 bytes header + uint32 triangle count + per-triangle float32×12 normal+vertices + uint16 attribute).
  - `toAsciiStl(mesh: CompiledMesh): String` — formato texto ASCII (sólido + facet + normal + vertex loops).
  - Apto para slicers (Cura, PrusaSlicer) y re-importación en CAD.

- **Slider throttle**: pitch slider solo se actualiza si han pasado ≥62 ms o el delta acumulado es ≥0.02 rad — limita re-cálculos a ~16 FPS durante drag.

- **Undo/Redo snapshot-based en el editor**:
  - `undoStack` / `redoStack` como `ArrayDeque<ForgePart>` en `ForgePartEditorViewModel`.
  - `pushUndoSnapshot()` invocado por todos los mutadores (`rename`, `setSafety`, `updateDimension`, `assignMaterial`, `addFeature`).
  - `undo()` / `redo()` restauran el snapshot y re-disparan el auto-save.
  - `canUndo` / `canRedo` como `StateFlow<Boolean>` para reflejar el estado del stack en la UI.
  - `MAX_UNDO = 50` (FIFO si excede).
  - Botones `↶ Undo` / `↷ Redo` en la `ActionButtons` row del editor, deshabilitados cuando el stack está vacío.

### Changed

- `ForgePartEditorScreen`: editor con botones Undo/Redo en la action row.

### Fixed

- **RISK-3 GPS leak** en `RideServiceScreen.kt`:
  - Eliminado el patrón `currentGps!!.latitude/longitude` que lanzaba `NullPointerException` o entregaba coordenadas crudas si la geocodificación reversa fallaba.
  - Añadido `sanitizeGpsAddress(raw: String?, fallback: String): String` con regex `^Ubicación GPS \(-?\d+\.?\d*, -?\d+\.?\d*\)$` que limpia strings del geocoder que incluyen coordenadas en plano.
  - Eliminado el truco `+0.05` para forzar un destino ficticio: ahora `destination` es estrictamente la dirección solicitada por el cliente; si no hay destino, distancia = 0.
  - 7 tests en `SanitizeGpsAddressTest` (incluye casos en español, inglés, coordenadas negativas, direcciones reales).

### Tests

- 307 → **336 tests** (+29).
- 0 failures, 0 errors.
- Tests nuevos: `StlExporterTest` (10), `JsonFileStoreTest` (8), `ForgeArtifactRepositoryConcurrencyTest` (4, con 50 coroutines paralelas validando el mutex), `SanitizeGpsAddressTest` (7).

### Not changed

- Versión de la app: `versionCode 16` / `versionName "4.0.0"`. Sin bump.
- Sin nuevas dependencies.
- Sin migraciones de base de datos.

## [0.5.0-forge-improvements] - 2026-07-02

### Added — Forge editor multi-feature por composición

- **`FeaturePlan` sealed interface** (`domain/FeaturePlan.kt`) con 4 implementaciones:
  - `SingleFeaturePlan`: 1 feature desde 1 preset.
  - `LinearArrayPlan`: N copies en eje cartesiano (motores en línea).
  - `CircularPatternPlan`: N copies en círculo, eje Y perpendicular.
  - `CompositePlan`: anida otros planes recursivamente, con `centerOrigin` opcional.
- **ADR 0001** (`docs/adr/0001-multi-feature-plans.md`): arquitectura basada en composición.
- **ADR 0002**: Compose Canvas isométrico vs Filament — decisión documentada.
- **ADR 0003**: IDs deterministas vs UUID — justificación.
- **`EngineCatalog`** con 15 templates pre-compuestos:
  - Motores en V: v6, v8, v10, v12.
  - Motores en línea: inline3, inline4, inline5, inline6.
  - Bóxer: boxer6.
  - Conjuntos: 4 ruedas, powertrain eléctrico, híbrido V8, transmisión 5 velocidades, esquina de suspensión McPherson, conjunto de freno de disco.
- **Catálogo de presets: 8 → 33** piezas automotrices.
- **`PresetId` enum** estable para lookup determinista.
- **JSON serialization** verificada para `FeaturePlan` y todas sus subclases.
- **Diagramas** del flujo `featurePlan → ParametricFeature → CompiledMesh → IsometricMeshRenderer`.

### Added — Editor y UX

- **Header con LED 4-estados**: `BIBLIOTECA CARGADA` / `CARGA PARCIAL` / `ERROR DE ASSETS` / `SIN DATOS`.
- **`bootstrapReport: StateFlow`** en `ForgeArtifactRepository`, distingue "biblioteca vacía" de "bootstrap falló".
- **`SaveStatus` badge** en el editor con 5 estados (IDLE, SCHEDULED, SAVING, SAVED, ERROR).
- **Auto-save con debounce 1500ms**: cambios persisten sin tocar el botón "Guardar".
- **Sync-save en `onCleared`**: `runBlocking(IO)` previene pérdida de cambios al navegar antes del debounce.
- **3D viewport interactivo**:
  - Drag horizontal → rotación yaw.
  - Slider de pitch -60°/+60°.
  - Doble tap → reset a (0, 0).
- **Iluminación Lambertiana** desde normales por vértice existentes en `CompiledMesh`.
- **Backface culling** + **painter's algorithm** + **wireframe opcional**.
- **`RotationState(yaw, pitch)`** con state hoisting para evitar re-render del padre.
- **Accesibilidad**: `contentDescription` en Canvas, Slider y botón reset.
- **Layout responsivo**: `heightIn(220dp-320dp) + aspectRatio(1.6)` en viewport.

### Changed

- `FeaturePreset` ahora tiene campo `id: PresetId` (breaking change interno).
- `FeaturePreset` ahora `@Serializable` para serialización JSON.
- `IsometricMeshRenderer` consume `yaw/pitch` como parámetros (state hoisted).
- `ForgePartEditorScreen`: editor con header LED, save badge, secciones PRESETS y ENGINES.

### Fixed

- IDs duplicados en `SingleFeaturePlan`, `LinearArrayPlan`, `CircularPatternPlan` cuando varios planes comparten parámetros. Ahora incluyen `posKey`, `axis/spacing/count` o `radius` en el ID.
- Pérdida de cambios al navegar antes del debounce de auto-save.

### Tests

- 225 → **307 tests** (+82, +36%).
- 0 failures, 0 errors.
- Coverage extendida: `IsometricMeshRendererTest` (7), `FeaturePlanTest` (21),
  `EngineCatalogTest` (13), `FeaturePlanSerializationTest` (10), más ajustes
  en tests existentes.

### Not changed

- Versión de la app: `versionCode 16` / `versionName "4.0.0"`. El PR no incluye
  bump de versión; queda a decisión de release manager.
- Sin nuevas dependencies. APK size sin cambio.
- Sin migraciones de base de datos.

[0.5.0-forge-improvements]: https://github.com/jordelmir/MEET-Mecanicos-Especialistas-En-Todo/compare/main...feature/forge-editor-improvements-2026-07-02
