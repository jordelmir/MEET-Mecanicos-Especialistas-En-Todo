# Atlas G4ED 3D/360 — contrato técnico

Fecha: 2026-07-27  
Versión Android: 4.3.0 (`versionCode 21`)

## Resultado

MEET incorpora 420 experiencias mecánicas individuales para el perfil de
referencia Hyundai Accent/Verna 2005, 1.6 DOHC, automático. El código de motor
Alpha II G4ED y el equipamiento físico siguen sujetos a confirmación por sello
de motor, VIN e inspección.

El alcance verificable es:

- 420 ordinales contiguos y 420 IDs canónicos únicos;
- 20 sistemas y 20 paquetes GLB offline;
- 333 componentes/repuestos solicitables;
- 87 regiones integradas o marcas de referencia no vendibles;
- ocho variantes condicionales pendientes de confirmación física;
- 793 mallas procedurales;
- aproximadamente 431.584 triángulos;
- 29 MB de assets G4ED sin comprimir por el repositorio;
- controles de órbita 360, zoom, aislamiento, contexto, despiece y reset.

## Autoridad

La fuente aportada por el propietario define nombres, orden y conocimiento,
pero no contiene CAD Hyundai licenciado ni dimensiones verificadas. Por eso:

- piezas aisladas: `REFERENCE_RECONSTRUCTION`;
- rasgos inseparables: `SCHEMATIC_REGION`;
- `dimensional=false`;
- `oemClaim=false`;
- `vehicleSpecificClaim=false`;
- compatibilidad: `REQUIRES_VERIFICATION`.

Ni el realismo, ni los polígonos, ni una foto de vendedor elevan esa autoridad.

## Trazabilidad

Fuente:

`99a2dc92a2acd5364d9f85e257b382b93998065647617fed4ddd11165785a89f`

Contenido canónico:

`17f41f9f18a4dddf07433e5252b5b8742679354b6d95debfc435b956a87bc3de`

El pipeline es:

```text
fuente del propietario
  -> tools/knowledge/build_g4ed_engine_atlas.py
  -> knowledge/g4ed/g4ed_engine_atlas.json
  -> tools/engine-asset-generator/generate-g4ed-atlas.mjs
  -> 20 GLB + 20 manifest.json
  -> parser/contratos Android
  -> Piezas / Motor 3D / IA / Repuestos
```

Cada binding 3D contiene ordinal, ID, `nodeKey`, grupo, prefijo de malla,
padre, autoridad, cámara, interacción, animación, transform original, vector
de despiece, bounds y estado comercial.

## UI y lógica comercial

### Piezas

El explorador permite buscar sin acentos, filtrar por sistema y mostrar solo
repuestos. La ficha combina 3D, conocimiento literal, autoridad, controles de
comparación y redirección al componente padre cuando el resultado es una
región integrada.

### Motor 3D

El selector `G4ED · 420 PIEZAS` abre el atlas sin reemplazar el motor universal
ni el vehículo 3D existente.

### IA y DTC

IA recibe ID, nombre, sistema, conocimiento, autoridad y advertencia del
elemento. Ese contexto se combina con vehículo, DTC y lecturas reales cuando
existen. Sin lectura OBD no se inventan valores.

### Repuestos

La solicitud conserva `canonicalReferenceId` y prellena pieza, sistema, origen
3D y advertencias. El showroom usa el render canónico como referencia y lo
separa de fotos, precio, condición y declaraciones del vendedor.

Una referencia 3D puede abrir una solicitud de cotizaciones; no autoriza una
compra ni convierte compatibilidad desconocida en exacta. Los bloqueos reales
de compatibilidad siguen vigentes.

## Reproducibilidad

```bash
python3 tools/knowledge/build_g4ed_engine_atlas.py --verify

cd tools/engine-asset-generator
npm ci
npm run generate:g4ed -- --range 1-420
npm run verify:g4ed -- --range 1-420

cd ../..
python3 .codex/skills/meet-procedural-mechanical-3d/scripts/run_evals.py
./android/gradlew -p android testDebugUnitTest
./android/gradlew -p android assembleDebug
```

La skill reusable vive en:

`.codex/skills/meet-procedural-mechanical-3d/`

Su versión enriquecida exige 20 paquetes, 420 bindings y continuidad de
Piezas–IA–Repuestos.

