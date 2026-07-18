# MEET generic mechanical asset generators

Generates the bundled `generic_inline4_engine.glb` and its manifest. The model
is a recognizable, selectable L2 teaching assembly. Its proportions are
illustrative and are not Hyundai, OEM, measured, or manufacturing geometry.

The same package also generates five staged vehicle-system assets for intake
and boost, automatic transmission and drivetrain, suspension, steering/brakes/
wheels, and electrical/control architecture.

```bash
cd tools/engine-asset-generator
npm ci
npm run generate
npm run generate:systems
```

Every renderable node uses `asset_mesh__<part-key>__<detail>` so Android can
bind a stable mesh family to an exact proprietary catalog entity. Context-only
mesh families remain visible but cannot silently acquire source authority.

System assets use `system_mesh__<part-key>__<detail>` and are validated against
`GenericVehicleSystemsAssetContract`. Every generated directory includes a
manifest with SHA-256, geometry counts, generator version, and the explicit
`L2_GENERIC_ASSEMBLY` / non-dimensional truth boundary.

Generate one system during asset development:

```bash
node generate-vehicle-systems.mjs suspension
```
