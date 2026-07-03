# ADR 0002: Compose Canvas isométrico vs Filament para viewport 3D

**Estado**: Aceptado
**Fecha**: 2026-07-02
**Contexto**: Post-PR forge-improvements

## Contexto y problema

El viewport 3D de ForgePartEditor necesita renderizar mallas trianguladas en
Compose. Decisiones pendientes:

1. **Stack 3D**: ¿motor 3D externo (Filament, SceneView) o rendering custom en
   Compose Canvas?
2. **Render quality**: ¿PBR + iluminación realista o aproximación Lambertiana?
3. **Performance**: ¿jank potencial con meshes grandes?

## Opciones evaluadas

### Opción A — Filament directo
Librería oficial de Google. PBR completo. ~5MB APK hit.

- ✅ Calidad visual excepcional.
- ✅ Software optimizado en C++/Skia.
- ❌ Requiere interop con Compose (SurfaceView/TextureView wrapping).
- ❌ API verbosa para casos simples.
- ❌ Rompe el build con `--offline` si no está cacheado (probado).

### Opción B — SceneView 2.x
Wrapper Compose-friendly alrededor de Filament. AR-ready.

- ✅ Composable directo (`<Scene>` en Compose).
- ✅ Comunidad activa.
- ❌ 5–20MB APK hit (Filament + dependencias ARCore).
- ❌ Latest estable compatible con Kotlin 1.9.23 no verificado sin build.

### Opción C — Compose Canvas + isométrico custom
`Canvas` de Compose + proyección isométrica manual en Kotlin puro.

- ✅ Cero deps adicionales. APK sin cambio.
- ✅ Control total sobre la matemática de proyección.
- ✅ Funciona perfectamente offline.
- ❌ Sin materiales PBR. Iluminación = Lambertiana.
- ❌ CPU-bound. Para >500 triángulos puede jankear sin throttling.
- ✅ Suficiente para el caso de uso actual (preview de piezas 1–100 caras).

## Decisión

**Opción C — Compose Canvas isométrico** con las siguientes mejoras incrementales
que ya están implementadas:

- **Lambertiana por vértice**: usa las normales que ya trae `CompiledVertex` (`nx,ny,nz`).
  No requiere materiales ni texturas.
- **Backface culling en 2D**: skip de triángulos con winding clockwise en pantalla.
- **Painter's algorithm**: ordena por avgZ descendente, dibuja back-to-front.
- **State hoisted**: `yaw/pitch` salen del renderer; padre gestiona estado. Evita
  recomposición completa del Canvas cuando solo cambia rotación.
- **`remember(mesh, yaw, pitch)`**: el pre-cómputo de triángulos solo se invalida
  cuando cambia alguna de esas tres keys.

## Plan de migración (futuro) si Swap a Filament es necesario

1. Encapsular `IsometricMeshRenderer` detrás de una interfaz `MeshRenderer`:
   ```kotlin
   sealed interface MeshRenderer {
       @Composable fun Render(mesh: CompiledMesh, modifier: Modifier)
   }
   ```
2. Implementaciones: `IsometricMeshRenderer` (actual) y `FilamentMeshRenderer` (futuro).
3. Selección via composición: el padre elige según `BuildConfig.ENABLE_FILAMENT_RENDER`.
4. Mantener tests de `IsometricMeshRendererTest` para la lógica Canvas.
5. Nuevos tests para `FilamentMeshRenderer` (probablemente snapshot tests).

Mientras esos recursos humanos (semanas de integración + benchmarking +
hardware testing en devices reales) no estén disponibles, el renderer Canvas
sigue siendo la elección correcta: 0 costo, sufficient quality.

## Consecuencias

### Positivas
- APK no crece.
- Build sin red (offline-friendly).
- Edición rápida de la matemática por un humano.
- Decisión reversible: la interfaz propuesta en el plan de migración es viable.

### Negativas
- Materiales PBR (metalness, roughness, texturas) no soportados.
- Sin shadow casting.
- Sin skybox / environment lighting.

### Workarounds en uso real
- **Metalness**: heurística sobre densidad del material (`densityKgM3 > 5000` = metal).
  No implementado aún; ver backlog.
- **Texturas**: omitidas por simplicidad. Color por vértice del compilador es suficiente
  para diferenciar piezas.
