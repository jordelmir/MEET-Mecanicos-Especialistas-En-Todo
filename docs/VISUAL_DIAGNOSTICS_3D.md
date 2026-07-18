# Diagnostico Visual 3D

El modulo 3D deja de ser una ilustracion aislada y queda conectado a una base tecnica por componente:

- tipo de motor: L4, V6, V8 o EV;
- pieza, categoria, ubicacion fisica y llave de malla 3D;
- DTCs relacionados con severidad y peso;
- PIDs OBD relacionados con rango esperado;
- pruebas de taller, flujo de reparacion, herramientas, especificaciones y seguridad;
- contexto listo para IA con vehiculo, DTCs activos y lecturas vivas.
- atlas BOM semantico para mapear sistemas, subsistemas, meshes 3D,
  DTCs, PIDs, evidencia requerida y origen Parts Marketplace sin
  afirmar compatibilidad exacta.

## Flujo Real

1. La pantalla detecta el tipo de motor desde el vehiculo activo.
2. `VisualDiagnosticRepositoryImpl` carga componentes desde `VisualDiagnosticSeedData`.
3. `VisualBomAtlas` agrupa piezas reales por sistema BOM: motor,
   transmision, frenos, direccion, red electrica, ADAS, HV/EV, etc.
4. El visor 3D envia `meshId` al tocar una pieza.
5. `ComponentLocatorScreen` mapea `meshId` a `DiagnosticComponent` y,
   cuando aplica, a un `VisualBomNode`.
6. La ficha muestra DTCs activos, PIDs vivos y guia tecnica.
7. Si no hay escaner conectado o no existe lectura, la UI muestra `Sin lectura en vivo`.
8. El boton `ARMAR CONTEXTO IA DE ESTA PIEZA` genera contexto tecnico verificable para la consulta.

## Como Agregar Una Pieza

Agregar un `DiagnosticComponent` en:

```txt
android/app/src/main/kotlin/com/elysium369/meet/data/visualdiagnostics/VisualDiagnosticSeedData.kt
```

Campos minimos:

- `id`: estable y unico, por ejemplo `alternator`;
- `meshKey`: id de la malla 3D, por ejemplo `alternator`;
- `verificationLevel`: nivel de verdad visual (`GENERIC_REPRESENTATION`,
  `PROBABLE_LOCATION`, `VEHICLE_VALIDATED`, `VIN_OEM_VALIDATED`,
  `VISUAL_CONFIRMED`);
- `evidenceRequirements`: pruebas necesarias antes de cotizar o afirmar
  compatibilidad;
- `relatedPids`: PIDs OBD que la app puede leer;
- `relatedDtcs`: codigos que deben resaltar la pieza;
- `workshopTests`: pruebas fisicas de taller, no conclusiones simuladas;
- `repairFlow`: pasos de reparacion con confirmacion;
- `safetyWarnings`: riesgos reales antes de intervenir.

## Reglas

- Un DTC nunca confirma una pieza danada por si solo.
- La UI no debe fingir valores OBD; si no hay lectura, se muestra como falta de dato.
- Todo fusible/rele debe incluir amperaje, alimentacion esperada, continuidad, funcion y prueba bajo carga cuando aplique.
- EV/HV debe exigir advertencias de alto voltaje, desenergizacion OEM y confirmacion de ausencia de tension.
- La calidad visual depende del mesh/procedural renderer, pero la verdad diagnostica vive en la ficha tecnica.
- Las piezas 3D genericas no equivalen a compatibilidad exacta sin VIN/OEM/foto/manual.
- Un nodo `VisualBomNode` puede originar un flujo de Parts Marketplace,
  pero debe conservar `evidenceRequirements` y `exactnessDisclaimer`.

## Atlas mecanico D3 de inspeccion implementado

El visor dispone de seis conjuntos 3D detallados y seleccionables:

| Conjunto | Sistemas cubiertos | Activo |
|---|---|---|
| Motor L4 | motor de combustion | `models/engine_inline4_generic/generic_inline4_engine.glb` |
| Admision y carga | admision de aire, turbo y supercharger | `models/vehicle_systems/intake_boost/generic_intake_boost.glb` |
| Transmision | caja automatica y tren motriz | `models/vehicle_systems/transmission_drivetrain/generic_transmission_drivetrain.glb` |
| Suspension | puntales, brazos, barra y eje trasero | `models/vehicle_systems/suspension/generic_suspension.glb` |
| Chasis rodante | direccion, frenos y ruedas | `models/vehicle_systems/steering_brakes_wheels/generic_steering_brakes_wheels.glb` |
| Electrico y control | bateria, fusibles, reles, arneses, ECU, sensores y actuadores | `models/vehicle_systems/electrical_control/generic_electrical_control.glb` |

Los cinco conjuntos de sistemas usan autoridad `L2_GENERIC_CUTAWAY` y detalle
visual `D3_RECOGNIZABLE_INTERNALS`: muestran
internos reconocibles de servicio, fijaciones, rodamientos, estrias, terminales,
conectores y superficies funcionales sin declarar cotas OEM. Se cargan bajo
demanda; el visor no mantiene
todos los GLB pesados en memoria simultaneamente. Cada familia renderizable usa
`system_mesh__<part-key>__<detail>` y se enlaza mediante
`GenericVehicleSystemsAssetContract` a un nombre literal y un `systemId` del
indice propietario.

### Comportamiento de inspeccion

- `RAYOS X` sustituye la carroceria de referencia por el conjunto detallado.
- El picking nativo de Filament resuelve la malla y abre la entidad fuente exacta.
- La seleccion aplica un material cian sin alterar el material original persistente.
- `DESPIECE 3D` ejecuta seis etapas acotadas y vuelve al origen sin drift.
- Un GLB compartido solo muestra familias respaldadas por el alcance activo; por
  ejemplo, turbo/supercharger no se presentan como instalados al inspeccionar la
  admision atmosferica del Hyundai.
- Las mallas de contexto sin alias ayudan a comprender el ensamble, pero no
  fabrican una seleccion ni autoridad de datos.

### Autoridad y regeneracion

Todos estos activos declaran `L2_GENERIC_CUTAWAY`,
`D3_RECOGNIZABLE_INTERNALS` y `ILLUSTRATIVE_PROPORTIONS_ONLY`. El nivel D3
describe detalle visual de inspeccion y es independiente de la autoridad
geometrica L0-L5. No son
geometria Hyundai, OEM, medida ni apta para fabricar. La aplicabilidad instalada
sigue dependiendo de evidencia de la base, VIN/OEM cuando corresponda y
confirmacion fisica.

| Activo D3 | Mallas | Triangulos | Tamano |
|---|---:|---:|---:|
| Admision y sobrealimentacion | 157 | 70,864 | 4,108,748 bytes |
| Transmision y tren motriz | 242 | 75,868 | 4,814,260 bytes |
| Suspension | 126 | 46,392 | 1,647,520 bytes |
| Direccion, frenos y ruedas | 269 | 120,064 | 10,296,452 bytes |
| Electrico, ECU y actuadores | 295 | 109,248 | 9,656,352 bytes |

```bash
cd tools/engine-asset-generator
npm ci
npm run generate
npm run generate:systems
```

Cada directorio de modelo incluye `manifest.json`, SHA-256, conteos de mallas y
triangulos, procedencia del generador y advertencia de autoridad.
