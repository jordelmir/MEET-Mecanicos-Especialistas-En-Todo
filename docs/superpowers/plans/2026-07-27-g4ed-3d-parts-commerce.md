# MEET G4ED 3D/360 Parts Commerce — Implementation Plan

Date: 2026-07-27  
Branch: `codex/g4ed-3d-commerce`  
Owner constraint: no subagents  
Source SHA-256: `99a2dc92a2acd5364d9f85e257b382b93998065647617fed4ddd11165785a89f`

## Outcome

Ship all 420 source elements as traceable, individually inspectable 3D/360
experiences inside the Android APK. Integrate them with `Motor 3D`, `Piezas`
and `Repuestos`, preserve the literal automotive corpus, and provide a
commercial showroom that never overstates compatibility or OEM geometry.

## Scope

### In scope

- Structured canonical pack for ordinal elements 1–420.
- Deterministic geometry recipes, bindings, manifests and hashes.
- First high-detail milestone for elements 1–30.
- Complete coverage of elements 31–420 by twenty system packs.
- Individual focus, orbit, isolate, explode/flow/function behavior.
- Knowledge, authority, applicability and commerce metadata.
- `Piezas` discovery/detail integration.
- `Repuestos` request/listing showroom integration.
- DTC, PID, AI context and repair handoff where evidence exists.
- Skill v1 after the first 30 and enriched skill v2 after all 420.
- Tests, parity, APK, ADB, performance and crash evidence.

### Out of scope

- Claiming dimensional Hyundai CAD without licensed CAD evidence.
- Copying the third-party G4ED scan or catalog artwork into the APK.
- Promoting any listing to `EXACT` based only on visual resemblance.
- Activating unapproved payment methods.
- Replacing the existing 3D, reports, marketplace or knowledge engines.

## Assumptions and dependencies

- The source enumerates 420 elements and is authoritative as user-provided
  knowledge, not as dimensional OEM evidence.
- The physical engine code and CVVT variant still require confirmation.
- The existing Three.js generator, GLTFExporter, Filament viewer and
  proprietary catalog remain the implementation foundation.
- Generated assets may be large; correctness and fidelity take priority, while
  runtime memory remains bounded through lazy loading.
- All changes preserve TS/Kotlin hash parity.

## Phase 0 — Stabilize the current Android baseline

### Deliverables

- Finish camera runtime permission handling.
- Finish pilot verification evidence policy.
- Preserve the existing valid passenger record and unblock the ride flow.
- Ensure the branch compiles before atlas work expands.

### Verification

```bash
cd android
./gradlew testDebugUnitTest \
  --tests '*VerificationCameraPolicyTest' \
  --tests '*RideVerificationPolicyTest' \
  --tests '*RideVerificationEvidencePolicyTest'
./gradlew assembleDebug
```

### Acceptance

- Camera permission is requested before capture.
- Cancelled/empty files do not count as evidence.
- Complete pilot evidence grants `PILOT_APPROVED`.
- Incomplete evidence never grants access.
- APK builds without introducing a Room migration.

## Phase 1 — Canonical 420-element knowledge pack

### Files

- `tools/knowledge/build_g4ed_engine_atlas.py`
- `android/app/src/main/assets/knowledge/g4ed/g4ed_engine_atlas.json`
- `android/app/src/main/kotlin/com/elysium369/meet/core/catalog/G4edEngineAtlas.kt`
- Python and Kotlin contract tests.

### Work

1. Parse every numbered entry 1–420.
2. Preserve original Spanish names and section membership.
3. Assign stable IDs, aliases, parents, `elementKind`, sellability and
   conditional applicability.
4. Bind each element to one of twenty system packs.
5. Add authority, evidence, DTC/PID and commerce fields without inventing
   unknown values.
6. Embed source SHA-256 and compute canonical content SHA-256.
7. Add validators for contiguous ordinals, unique IDs, parent integrity,
   sellability and conditional variants.

### Verification

```bash
python3 tools/knowledge/build_g4ed_engine_atlas.py --verify
cd android
./gradlew testDebugUnitTest --tests '*G4edEngineAtlas*'
```

### Acceptance

- Exactly 420 entries with ordinals `1..420`.
- No duplicate IDs or orphan parents.
- Integrated features are not directly sellable.
- CVVT elements remain conditional.
- Automatic variant does not expose clutch/disc/pressure-plate as installed.
- Pack hash is stable across two regenerations.

## Phase 2 — First 30 high-detail 3D experiences

### Files

- `tools/engine-asset-generator/g4ed-atlas-common.mjs`
- `tools/engine-asset-generator/generate-g4ed-atlas.mjs`
- generated assets under `android/app/src/main/assets/models/g4ed_atlas/`
- Android contracts under `visual3d/domain/`.

### Work

1. Extract shared materials and geometry helpers without changing the verified
   generic L4 output unexpectedly.
2. Build the structure/crankcase pack for elements 1–30.
3. Model sellable components as isolated mesh families.
4. Model cylinders, galleries, deck, journals and counterweights as semantic
   regions/submeshes with individual cameras and behavior.
5. Add PBR materials, bounds, LOD, camera presets, explode stages, flow paths
   and functional animations.
6. Emit GLB, manifest, SHA-256, mesh/triangle counts and thumbnails.
7. Map every ordinal 1–30 to a selectable node or region.

### Verification

```bash
cd tools/engine-asset-generator
npm ci
npm run generate:g4ed -- --range 1-30
npm run verify:g4ed -- --range 1-30
cd ../../android
./gradlew testDebugUnitTest --tests '*G4ed*3d*' --tests '*GenericInlineFour*'
```

### Acceptance

- All 30 elements open an individual 360 experience.
- Parent/region picking is deterministic.
- No two ordinals silently resolve to the wrong component.
- Explode/reassemble returns to the original transform without drift.
- Manifest states `oemClaim=false` and correct authority per element.
- Existing `generic_inline4_engine.glb` contract remains green.

## Phase 3 — Piezas integration

### Files

- `ProprietaryPartsBrowser.kt`
- new focused G4ED browser/detail composables.
- `ComponentLocatorScreen.kt` and 3D contracts.

### Work

1. Merge G4ED elements into discovery without replacing the literal corpus.
2. Add system/kind/sellability/authority filters.
3. Add glassmorphic cards with generated thumbnails.
4. Add detail tabs for `3D`, knowledge, diagnosis, repair, compatibility,
   replacement parts and evidence.
5. Add full-screen orbit, isolate, X-ray, explode, flow and reset controls.
6. Preserve navigation state when returning from DTC, AI or Repuestos.
7. Label integrated features and redirect to the parent component.

### Verification

- Search `galería`, `cigüeñal`, `tapa de bancada` and `cárter`.
- Open each result, inspect 360 and return without losing filters.
- Rotate/zoom/isolate all first 30 on device.
- Confirm motion reduction and readable contrast.

### Acceptance

- Source text and 3D are visible together.
- The UI distinguishes reconstruction from verified OEM geometry.
- `Sin lectura en vivo` appears when OBD is absent.
- No element claims exact compatibility without evidence.

## Phase 4 — Repuestos commercial showroom

### Files

- Parts request/listing domain bindings.
- `PartRequestScreen.kt` integration.
- reusable `Part3dShowroom` composable.

### Work

1. Add canonical atlas ID to a request/listing without merging seller data into
   the canonical entity.
2. Show reference 3D beside real seller photos.
3. Display comparison checkpoints: connector, pins, teeth, mounting, dimensions,
   position, variant and OEM declaration.
4. Restrict direct listing for non-sellable regions.
5. Route integrated features to the parent component or machining service.
6. Carry DTC/vehicle/evidence context into the request.
7. Preserve compatibility disclaimers and marketplace ranking contracts.

### Verification

```bash
cd android
./gradlew testDebugUnitTest \
  --tests '*PartRequestPublicationPolicyTest' \
  --tests '*Compatibility*' \
  --tests '*G4edCommerce*'
```

### Acceptance

- Seller photos are never presented as the canonical render.
- A visual match alone cannot produce `EXACT`.
- Non-sellable elements cannot create misleading standalone listings.
- The buyer can inspect the reference 360 offline.

## Phase 5 — Skill v1 after the verified first 30

### Work

1. Use the available `skill-creator` workflow.
2. Create a MEET-specific skill for procedural mechanical asset production.
3. Include proven templates, commands, authority rules and examples.
4. Add evaluation prompts for a component, region, fluid path and hardware.
5. Validate the skill before using it for elements 31–420.

### Acceptance

- Skill instructions reproduce the naming and manifest contracts.
- Examples come from assets that passed Android tests.
- The skill forbids invented OEM geometry and silent compatibility promotion.
- At least four representative evaluations pass.

## Phase 6 — Complete elements 31–420

### Work

Generate and integrate the remaining nineteen knowledge systems in bounded
batches. Every batch must pass generation, contract and Android tests before
the next:

1. rotating assembly and pistons;
2. cylinder head and combustion;
3. DOHC/valvetrain;
4. timing;
5. lubrication;
6. cooling;
7. intake;
8. crankcase ventilation;
9. fuel injection;
10. ignition;
11. sensors;
12. exhaust/emissions;
13. alternator;
14. starter;
15. accessories;
16. automatic-transmission coupling;
17. mounts;
18. electronic control;
19. seals/gaskets/hardware.

### Batch gates

- contiguous assigned ordinals;
- all elements selectable;
- system-specific animation semantics;
- no invalid installed variant;
- deterministic manifest/hash;
- bounded load/unload behavior;
- Piezas and Repuestos navigation;
- focused tests green.

### Acceptance

- Exactly 420 individual experiences.
- Every element has knowledge, authority and a 3D binding.
- All sellable elements support the commercial showroom.
- Every integrated feature routes to its parent or service.
- No placeholder geometry label or unfinished UI path remains.

## Phase 7 — Final skill enrichment and release verification

### Skill v2

- Re-open the skill after all 420 elements.
- Add lessons, metrics, optimizations and examples from all twenty systems.
- Add migration guidance for another engine.
- Run the expanded evaluation suite and maintain backward compatibility with
  the first 30 recipes.

### Full verification

```bash
python3 tools/knowledge/build_g4ed_engine_atlas.py --verify
cd tools/engine-asset-generator
npm run verify:g4ed -- --all
cd ../..
bash tests/parity/ci-verify.sh
cd android
./gradlew testDebugUnitTest assembleDebug
```

On the connected Android device:

1. install with `adb install -r -d`;
2. cold launch and confirm foreground/process;
3. inspect representative items from all twenty systems;
4. exercise Piezas → 360 → Repuestos → return;
5. repeat 30 asset openings while observing memory;
6. verify map/ride/camera flows did not regress;
7. inspect logcat for fatal exceptions and ANR.

### Final acceptance

- APK contains and opens all 420 experiences.
- Existing Motor 3D 360 remains functional.
- Piezas and Repuestos share canonical IDs and authority.
- Tests and cross-runtime parity pass.
- No fatal crash or ANR in the ADB walkthrough.
- Skill v2 is validated and documents the final production method.

## Rollback

- Generated G4ED assets live in their own directory and can be disabled through
  the atlas feature contract without deleting the existing generic L4 asset.
- New UI entry points degrade to the current proprietary browser when the pack
  fails validation.
- Canonical listing fields are additive; existing listings remain readable.
- No Room migration is introduced until a separate migration and rollback plan
  is justified.

