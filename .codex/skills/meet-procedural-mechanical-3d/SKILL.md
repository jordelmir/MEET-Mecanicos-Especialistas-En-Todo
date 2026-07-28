---
name: meet-procedural-mechanical-3d
description: Build, extend, verify, or integrate traceable procedural automotive 3D/360 assets for MEET. Use this skill for engine, transmission, hydraulics, electrical, body, interior, suspension, brakes, HVAC, SRS, component meshes, GLB generation, Filament picking, exploded views, semantic regions, flow/current traces, PBR materials, 3D parts commerce, or any of MEET's 6,405 canonical experiences—even when the user only asks to “add a part in 3D.”
compatibility: Node.js, Three.js 0.185.1, Kotlin, Android assets, Filament
---

# MEET Procedural Mechanical 3D

Create mechanical experiences that remain useful for diagnosis, repair and
parts commerce. A visually convincing reconstruction is not dimensional
evidence, so preserve the distinction between appearance and engineering
authority in geometry, manifests and UI.

## Read first

1. Read the repository `AGENTS.md`.
2. Read the active atlas or knowledge entity before naming geometry.
3. Read [authority-and-contracts.md](references/authority-and-contracts.md).
4. For unfamiliar geometry or animation types, read
   [recipe-patterns.md](references/recipe-patterns.md).

## Required workflow

### 1. Establish the evidence ceiling

- Record the source SHA-256 and canonical entity ID.
- Determine whether the requested object is a sellable component, assembly,
  service hardware, consumable, conditional variant, integrated feature or
  reference mark.
- Use `REFERENCE_RECONSTRUCTION` for isolated procedural parts.
- Use `SCHEMATIC_REGION` for bores, galleries, journals, faces and other
  inseparable regions.
- Keep `dimensional=false`, `oemClaim=false` and
  `vehicleSpecificClaim=false` unless separately licensed evidence proves the
  stronger claim.
- Keep conditional equipment pending physical confirmation.

### 2. Design the experience contract before geometry

Every entity needs:

- stable `canonicalId`, ordinal and `nodeKey`;
- one pack ID and deterministic group node;
- camera preset and finite bounds;
- orbit, zoom, isolate, X-ray context, explode/reassemble and reset;
- one truthful functional animation mode;
- original transform plus deterministic explode vector;
- parent redirection for non-sellable regions;
- source, atlas and GLB hashes.

Name groups `asset_part__<nodeKey>` and meshes
`asset_mesh__<nodeKey>__<detail>`. Picking must never use display text.

### 3. Choose the smallest truthful geometry recipe

- Components and assemblies: recognizable silhouette, mounting interfaces,
  service-facing subfeatures and appropriate PBR materials.
- Integrated regions: a selectable overlay or submesh located on the parent.
- Fluid passages: emissive transparent tube paths with directional flow
  semantics.
- Rotating components: explicit axis, journals/teeth/rotor where relevant.
- Hardware: head, shank, thread/seat cues; never invent exact thread size.
- Seals and consumables: cross-section and placement context; never infer
  material or dimensions without evidence.

Procedural fallback geometry may establish full interaction coverage, but label
it as reference geometry and improve high-value parts with dedicated recipes.

### 4. Generate deterministically

For the G4ED atlas:

```bash
cd tools/engine-asset-generator
npm ci
npm run generate:g4ed -- --range START-END
npm run verify:g4ed -- --range START-END
```

For the multidomain vehicle atlases:

```bash
python3 tools/knowledge/build_vehicle_technical_atlases.py --verify
cd tools/engine-asset-generator
npm run generate:technical-atlases
npm run verify:technical-atlases
```

Keep generation deterministic: stable element order, seeded explode vectors,
fixed material names, fixed segment counts and canonical JSON output. Rerunning
the same generator with the same atlas must preserve GLB and manifest hashes.

### 5. Validate before Android integration

The verifier must reject:

- missing or duplicate bindings;
- wrong ordinal, ID, pack or node key;
- a manifest hash that differs from the GLB;
- nodes absent from the GLB JSON chunk;
- non-finite camera bounds or transforms;
- OEM, dimensional or vehicle-specific claims without evidence;
- directly sellable semantic regions;
- visual resemblance promoted to exact compatibility.

Run:

```bash
./android/gradlew -p android testDebugUnitTest \
  --tests '*G4edAtlas3dContractTest' \
  --tests '*GenericInlineFour*'
```

### 6. Integrate into the closed loop

- Show literal knowledge and 3D together.
- Preserve the reconstruction authority warning in the detail view.
- Carry vehicle, DTC and evidence context into parts requests.
- Show the canonical render beside, never instead of, seller photographs.
- Require VIN/OEM/photo/connector/measurements before exact compatibility.
- Redirect integrated regions to their parent part or a machining/service flow.
- Lazy-load pack GLBs; release Filament resources when leaving the viewer.
- Preserve search/filter/navigation state after returning from 3D.

### 7. Prove the result

1. Run the atlas generator verifier.
2. Run Kotlin contract tests.
3. Build the APK.
4. Install and launch on Android when a device is available.
5. Inspect picking, orbit, isolate, explode/reset and crash logs.
6. Report exact coverage, hashes and any remaining authority limitation.

## Definition of done

- Every scoped entity resolves to exactly one selectable group or region.
- Search, detail, 3D, diagnosis and commerce share the same canonical ID.
- Explode/reassemble returns to the stored original transform without drift.
- GLB bytes match the manifest SHA-256.
- The existing generic L4 contract remains green.
- The UI states that the asset is a non-dimensional reference reconstruction.
- No visual result alone produces `EXACT` compatibility.

## Full-atlas production lessons (v2)

### Regenerate complete shared packs

Range generation is useful for recipe development, but a partial range can
share a `packId` with elements outside that range. Before release, regenerate
`1-420` so the final pack manifest contains every member of each system:

```bash
cd tools/engine-asset-generator
npm run generate:g4ed -- --range 1-420
npm run verify:g4ed -- --range 1-420
```

Never publish a partial manifest over a previously complete pack.

### Bound runtime memory

- Keep one GLB per mechanical system rather than one 420-part monolith.
- Load the selected pack lazily from Android assets.
- Cache parsed manifests, not native Filament model instances.
- Dispose scene nodes and material instances when the composable leaves.
- Default the product showroom to the selected part in isolation.
- Reveal pack context only on demand.

### Preserve commerce separation

The canonical atlas describes the reference entity and visualization. A parts
request or seller listing stores its canonical reference ID but keeps seller
photos, price, condition, declared OEM and evidence separate. A canonical 3D
reference may open a request for quotes; it does not authorize purchase or
promote compatibility.

### Carry cited AI context

When opening AI from an atlas element, include:

- canonical ID and original name;
- system title and literal knowledge;
- visual authority and warning;
- current vehicle/DTC/OBD evidence when available;
- an explicit instruction not to claim exact fit without the evidence gate.

### Release gate

Run the v2 skill evaluation and the complete project gates:

```bash
python3 .codex/skills/meet-procedural-mechanical-3d/scripts/run_evals.py
python3 tools/knowledge/build_g4ed_engine_atlas.py --verify
cd tools/engine-asset-generator && npm run verify:g4ed -- --range 1-420
cd ../../android && ./gradlew testDebugUnitTest assembleDebug
```

Read [system-pack-matrix.md](references/system-pack-matrix.md) when changing
pack routing, generation batches or Android lazy-loading behavior.

## Proven examples

The first verified G4ED milestone provides four reference patterns:

- component: ordinal 1, cylinder block with service-facing deck and bore cues;
- region: ordinal 2, selectable cylinder bore tied to the block parent;
- fluid path: ordinal 7, emissive internal lubrication gallery;
- rotating assembly: ordinals 27–30, crankshaft and semantic journal/weight
  regions.

Use these as patterns, not as dimensions for unrelated parts.

## Multidomain production lessons (v3)

### Preserve source numbering and canonical numbering separately

Some owner-provided corpora restart at ordinal 1 for every system. Keep a
single contiguous canonical ordinal for IDs and bindings, and preserve the
local source ordinal separately. Never discard the source section/subsection
needed to reconstruct provenance.

### Normalize applicability before geometry

Every technical element must explicitly encode:

- side: left, right, both or not side-specific;
- body-style condition;
- installed-equipment conditions;
- `REQUIRES_VERIFICATION` compatibility ceiling;
- `PENDING_VIN_EPC` OEM resolution when VIN/EPC evidence is absent;
- null OEM, quantity and supersession rather than invented placeholders.

Hatchback-only parts must never appear as applicable to a selected sedan.
ABS, EBD, SRS, A/C, cruise control and regional variants remain pending
physical confirmation.

### Use domain-specific functional language

- hydraulics, lubrication, fuel and refrigerant: `FLOW_TRACE`;
- electrical conductors and harnesses: `CURRENT_TRACE`;
- shafts, hubs, pulleys and rotors: `ROTATIONAL_FUNCTION`;
- struts, valves, pedals and actuators: `RECIPROCATING_MOTION`;
- inseparable surfaces and passages: `REGION_PULSE`;
- service hardware and consumables: `REMOVE_INSTALL`.

Animations teach function. They do not simulate live pressure, current, force,
temperature or motion unless measured data is present.

### Treat safety-critical domains conservatively

- Do not simplify the documented Accent rear suspension to a torsion beam:
  preserve upper arm, lower arm, assist link and rear strut relationships until
  VIN and physical verification establish the exact assembly.
- SRS and pretensioner geometry must remain inert, non-service-operational
  reference content.
- Brake, steering, suspension and structural-body visuals require explicit
  inspection/torque/alignment/manual gates before service.
- Electrical diagnosis must prioritize voltage drop under load, connector
  keying, pin count and ground integrity instead of visual similarity.

### Full vehicle-atlas release gate

A complete 4.4+ release contains:

- 20 G4ED packs with 420 bindings;
- 110 multidomain packs with 5,985 bindings;
- 6,405 unique experiences across 130 packs;
- source, canonical-atlas and GLB hashes verified;
- Piezas and Motor 3D entry points;
- cited canonical context for AI;
- DTC navigation and reference-safe parts requests;
- zero unsupported OEM, dimensional or exact-compatibility claims.

Run:

```bash
python3 .codex/skills/meet-procedural-mechanical-3d/scripts/run_evals.py
python3 -m unittest tools.knowledge.test_build_vehicle_technical_atlases
python3 tools/knowledge/build_vehicle_technical_atlases.py --verify
node tools/engine-asset-generator/verify-vehicle-technical-atlases.mjs
bash tests/parity/ci-verify.sh
./android/gradlew -p android testDebugUnitTest assembleDebug
```

Read [system-pack-matrix.md](references/system-pack-matrix.md) for the combined
130-pack routing contract.

## Universal inline coverage lessons (v4)

### Keep the 3D experience inside the selected part

The detail screen for a physical component must always resolve to an inline
experience. Use a canonical GLB when its binding and bytes validate; otherwise
use a deterministic semantic reconstruction in the same screen. Never redirect
a selected part to an unrelated general atlas and never replace the viewer with
an empty placeholder.

Derive geometry identity from the physical component name, not from appended
procedure or corpus text. Strip tabs, newlines and other literal detail before
canonical matching so identical parts remain stable as knowledge improves.

### Distinguish two independent coverage numbers

- Canonical technical-atlas coverage: the verified 6,405 experiences and pack
  contracts described above.
- Principal-database inline coverage: every physical `COMPONENT` record exposed
  by the proprietary catalogue, currently proven as 4,753 of 4,753.

Do not add these counts or describe semantic fallback as newly verified OEM
geometry. It is interactive reference coverage.

### Choose semantic fallback by mechanical archetype

Route normalized names into stable recipes such as panel/glass,
harness/hose, ECU/fuse/relay, sensor, valve/injector, gear/bearing,
shaft, pump/alternator, suspension, brake, wheel, fastener, seal or generic
component. Seed proportions and detail placement from stable component
identity. Include mounting or service cues that make the class recognizable,
without inventing exact dimensions.

### Couple 3D with cited repair evidence

The selected component screen must keep these layers distinct and visible:

1. literal source identity and hashes;
2. inline 3D/360 with its authority label;
3. cited diagnostic and repair workflow;
4. compatibility and physical-test gates.

Extract repair evidence from source-neighbourhood blocks and preserve block
order plus content hash. Universal safety checklists may fill process gaps, but
must say when a specific value, test or procedure is absent from the source.

### Prove universal coverage

Run the repository coverage and source-contract tests in addition to atlas
verification:

```bash
./android/gradlew -p android testDebugUnitTest \
  --tests '*ProprietaryInline3dCoverageTest' \
  --tests '*PrincipalRepairSourceContractTest' \
  --tests '*PartRepairWorkflowTest'
```

The release fails if any physical component returns no inline experience,
source bytes or block hashes drift, or a procedural fallback is promoted to
OEM/dimensional authority.
