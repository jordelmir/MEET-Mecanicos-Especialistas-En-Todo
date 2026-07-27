---
name: meet-procedural-mechanical-3d
description: Build, extend, verify, or integrate procedural mechanical 3D/360 assets for MEET. Use this skill whenever work mentions engine parts, component meshes, GLB generation, Filament picking, exploded views, semantic regions, fluid paths, PBR mechanical materials, 3D parts commerce, or the G4ED 420-element atlas—even when the user only asks to “add a part in 3D.”
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

## Proven examples

The first verified G4ED milestone provides four reference patterns:

- component: ordinal 1, cylinder block with service-facing deck and bore cues;
- region: ordinal 2, selectable cylinder bore tied to the block parent;
- fluid path: ordinal 7, emissive internal lubrication gallery;
- rotating assembly: ordinals 27–30, crankshaft and semantic journal/weight
  regions.

Use these as patterns, not as dimensions for unrelated parts.

