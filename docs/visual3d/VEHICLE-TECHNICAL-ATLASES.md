# Atlas técnicos vehiculares 3D/360

Fecha: 2026-07-27
Versión Android: 4.4.0 (`versionCode 22`)

## Alcance

MEET incorpora cuatro atlas adicionales para el perfil de referencia Hyundai
Accent/Verna 2005 1.6 DOHC automático. Se suman al atlas G4ED existente; no lo
reemplazan.

| Atlas | Elementos | Sistemas | Repuestos o componentes | Regiones semánticas | Variantes condicionales |
|---|---:|---:|---:|---:|---:|
| Transmisión e hidráulica | 838 | 13 | 792 | 46 | 9 |
| Sistema eléctrico | 1.529 | 34 | 1.425 | 104 | 78 |
| Carrocería e interior | 1.665 | 38 | 1.663 | 2 | 55 |
| Chasis y periféricos | 1.953 | 25 | 1.922 | 31 | 84 |
| **Total adicional** | **5.985** | **110** | **5.802** | **183** | **226** |
| **Total con G4ED** | **6.405** | **130** | **6.135** | **270** | **234** |

Las variantes condicionales no son una confirmación de equipamiento instalado.
ABS, EBD, SRS, A/C, cruise control, carrocería y opciones regionales permanecen
en `PENDING_PHYSICAL_CONFIRMATION`.

## Fuentes y trazabilidad

Cada elemento conserva el SHA-256 de su archivo aportado, sección, ordinal
canónico y, cuando la fuente reinicia la numeración por sistema, ordinal local.

| Dominio | SHA-256 de fuente | SHA-256 canónico del atlas |
|---|---|---|
| Transmisión/hidráulica | `77973385cceafee8cb5c35f01463264df816501d81ed060e390cbb36cd226b2d` | `49d03589da5bb1f848d8facfc41c0f0b023668cbe1a1a7778a4daf012315c03c` |
| Eléctrico | `b511b2085fc96a1c2d2cd23066ca63ab553af5791529084dc1a28579c36c6efb` | `368e61afddca8461026b80257a8b657365988ca0f5affc66fe02c444e7126b48` |
| Carrocería | `719fbb72f6994d1e37a6072395a23ad84caa82b2bedc4b65b1a06586ed568e5f` | `fbc45bbacd051909754336b64512a7b20b8e675dea18a5910800641ed52d498d` |
| Chasis/periféricos | `e9d82c61d08bfda44867666ecf5a7b4ba0d3bf67ced2fc32ba0271eeee3d9364` | `b1889977beca8d1d8a221391ef40052fc9fd8006e8455da53981cf1889cae059` |

El generador rechaza cambios de fuente, ordinales no contiguos, IDs
duplicados, regiones huérfanas y cualquier promoción visual a compatibilidad
exacta.

## Corrección de arquitectura trasera

El atlas de chasis conserva la indicación aportada de tratar la suspensión
trasera como arquitectura con brazo superior, brazo inferior, `assist link` y
strut trasero. No se simplifica automáticamente como eje torsional. La forma,
las dimensiones y la aplicabilidad exacta siguen pendientes de VIN, EPC y
verificación física.

## Normalización de aplicabilidad

Cada elemento técnico incluye:

- lado: izquierdo, derecho, ambos o no específico;
- condición de carrocería;
- condiciones de equipamiento;
- techo de compatibilidad `REQUIRES_VERIFICATION`;
- estado OEM `PENDING_VIN_EPC`;
- OEM, cantidad y supersesión nulos mientras no exista evidencia;
- estado pendiente para relaciones exactas de fijaciones.

Una pieza `HATCHBACK_ONLY` no se presenta como aplicable al sedán seleccionado.
Una opción condicional nunca se transforma en equipamiento confirmado por
aparecer en el corpus.

## Contrato 3D

Los 5.985 elementos están distribuidos en 110 paquetes GLB:

- un paquete por sistema;
- 6.774 mallas;
- 4.987.212 triángulos;
- 436.317.184 bytes de GLB;
- un grupo `asset_part__<nodeKey>` por elemento;
- al menos una malla `asset_mesh__<nodeKey>__<detail>`;
- manifest con SHA-256 del GLB y hash canónico del atlas;
- `dimensional=false`, `oemClaim=false`,
  `vehicleSpecificClaim=false`.

El visor ofrece órbita 360, zoom, aislamiento, contexto, despiece/reensamble,
rotación automática y reset. Flujo hidráulico y corriente eléctrica usan
trazas funcionales; las piezas de rotación usan eje explícito; fijaciones,
consumibles y regiones integradas usan recetas diferenciadas.

## Integración de producto

- **Piezas:** selector de cuatro atlas, búsqueda literal, filtros por sistema y
  detalle técnico.
- **Motor 3D:** acceso directo sin eliminar el motor universal ni el G4ED.
- **IA:** recibe atlas, ID, nombre original, sistema, conocimiento, autoridad y
  la prohibición de afirmar compatibilidad exacta.
- **DTC:** cada ficha abre el diagnóstico para conectar evidencia OBD.
- **Repuestos:** conserva el ID canónico, prellena la solicitud y muestra la
  referencia 3D separada de las fotos reales del vendedor.

## Reproducción y verificación

```bash
python3 -m unittest tools.knowledge.test_build_vehicle_technical_atlases
python3 tools/knowledge/build_vehicle_technical_atlases.py --verify

cd tools/engine-asset-generator
npm run generate:technical-atlases
npm run verify:technical-atlases

cd ../../android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Límite de autoridad

Estas geometrías sirven para reconocimiento, educación, navegación, diagnóstico
y comparación visual. No son CAD OEM, no contienen dimensiones certificadas y
no autorizan compra o montaje sin confirmar VIN, número OEM, mercado, código
aplicable, equipamiento, fotos, conectores y medidas.
