# MEET generic mechanical asset generators

Generates the bundled `generic_inline4_engine.glb` and its manifest. The model
is a recognizable, selectable L2 teaching assembly. Its proportions are
illustrative and are not Hyundai, OEM, measured, or manufacturing geometry.

The same package also generates eighteen staged D3-detail cutaway vehicle-system assets.
The first generator covers intake and boost, automatic transmission and drivetrain,
suspension, steering/brakes/wheels, and electrical/control architecture. The extended
generator covers lighting, HVAC, passive safety, ADAS, body, wipers, interior,
infotainment, access, hybrid/EV, fluids/wear, hardware, and the functional overview.
D3 refers to
recognizable inspection internals and remains separate from L0-L5 geometry authority.

```bash
cd tools/engine-asset-generator
npm ci
npm run generate
npm run generate:systems
npm run generate:extended-systems
# or regenerate the complete system atlas:
npm run generate:all-systems
```

Every renderable node uses `asset_mesh__<part-key>__<detail>` so Android can
bind a stable mesh family to an exact proprietary catalog entity. Context-only
mesh families remain visible but cannot silently acquire source authority.

System assets use `system_mesh__<part-key>__<detail>` and are validated against
`GenericVehicleSystemsAssetContract`. Every generated directory includes a
manifest with SHA-256, geometry counts, generator version, and the explicit
`L2_GENERIC_CUTAWAY`, `D3_RECOGNIZABLE_INTERNALS`, and the non-dimensional truth boundary.

Generate one system during asset development:

```bash
node generate-vehicle-systems.mjs suspension
node generate-extended-vehicle-systems.mjs hybrid_ev
```
# Plataformas originales MEET

Ejecute `npm run generate:meet-platforms` para reconstruir las nueve plataformas
seleccionables en `android/app/src/main/assets/models/meet_platforms`. El generador
produce un GLB distinto por concepto y un manifiesto con hashes, piezas y límites
de autoridad. Estos activos son conceptos procedurales originales de MEET; no son
CAD dimensional, diseño OEM ni evidencia suficiente para fabricación.
