# MEET Complete 3D Vehicle Repair Twin

**Status:** Proposed for implementation
**Date:** 2026-07-16
**Owner:** MEET / Elysium Vanguard
**Primary vehicle profile:** Hyundai Accent/Verna 2005, automatic transmission, 1600 cc engine
**Source authority:** `Document (16).docx` and `Document (17).docx`

## 1. Decision

Replace the current seed-driven cloud of boxes and cylinders with a complete,
serviceable 3D vehicle twin. The twin will let the user move progressively from
the complete car to a system, assembly, component, service subpart, fastener,
seal, or connector. Every selectable 3D node will link back to the proprietary
catalog and, when available, to a repair procedure, diagnostic evidence,
required tools, warnings, and completion gates.

The production renderer will be SceneView 4.22.0 over Google Filament. The
existing Compose Canvas renderer remains as a compatibility fallback and is no
longer the visual authority for the primary experience.

This is a hybrid asset strategy:

- a complete vehicle shell and major assemblies establish understandable
  physical context;
- an automotive archetype atlas supplies recognizable mechanical geometry;
- the 4,753 proprietary component entities bind to stable nodes and archetypes;
- a component may share an archetype, but never an ID, provenance record, or
  repair context;
- exact dimensional or OEM claims remain blocked until evidence supports them.

## 2. Problem Confirmed On Device

The current APK successfully exposes the complete proprietary corpus, but the
3D representation is not a vehicle twin:

- universal nodes are generated as a box or cylinder from a random-looking
  deterministic seed;
- system pages can place dozens of unrelated volumes in concentric rings;
- selecting a 368-entity system creates severe visual density and broken
  framing;
- all components in a system use nearly the same material and color;
- the user cannot understand installed location, access order, parent assembly,
  or removal direction;
- rendering measured on the connected Android device at 8.65% janky frames,
  with a 40 ms 90th-percentile frame time during the isolated 3D audit.

The source information is valuable and complete. The visual model must now
make that information spatially and mechanically understandable.

## 3. Product Goals

1. Show a complete 3D car as the first visual context.
2. Divide the car into every system represented by the proprietary catalog.
3. Drill down without losing context: vehicle, system, assembly, component,
   service subpart, hardware.
4. Link every proprietary component to a stable 3D node.
5. Link repair procedures and steps to the nodes they inspect, disconnect,
   loosen, remove, install, torque, reconnect, fill, calibrate, or verify.
6. Preserve all literal source text, hashes, source order, and real cases.
7. Provide useful automotive materials, lighting, shadows, depth, and motion.
8. Keep the experience offline-first and performant on the connected device.
9. Preserve honest visual authority and compatibility language.
10. Retain all existing diagnostic, marketplace, report, and evidence flows.

## 4. Non-Goals And Truth Boundaries

- The initial full-car shell is not advertised as an OEM CAD model.
- No dimension, torque, OEM number, pinout, routing, or connector geometry is
  inferred merely because a 3D node exists.
- A DTC never proves that the highlighted part is defective.
- A shared visual archetype does not prove interchangeability.
- The 297 real cases are evidence and learning records, not geometry nodes.
- The viewer does not render all 4,753 meshes simultaneously.
- The design does not remove the current procedural diagnostic scenes; they
  remain available as fallback and specialized views.

Visual authority remains explicit:

| Authority | Meaning |
|---|---|
| `GENERIC_SCHEMATIC` | Recognizable automotive family, not vehicle-specific |
| `SYSTEM_PROBABLE` | Probable system and location, physical confirmation required |
| `VEHICLE_PROFILED` | Bound to the Hyundai profile, not dimensional OEM proof |
| `VIN_OEM_VALIDATED` | VIN and OEM evidence support the binding |
| `VISUAL_CONFIRMED` | User or technician evidence confirms the installed item |

The application must display the authority state in the selected-part sheet.

## 5. Vehicle Decomposition Model

The twin uses six service levels. A deeper level never replaces the parent
context; it isolates or ghosts it.

| Level | Name | Example |
|---|---|---|
| L0 | Complete vehicle | Compact sedan reference shell |
| L1 | System | Engine, brakes, electrical, body, HVAC |
| L2 | Assembly | Engine long block, front axle, fuse box |
| L3 | Component | Crankshaft, CKP sensor, control arm |
| L4 | Service subpart | Bearing, bushing, connector, boot |
| L5 | Hardware and consumable | Bolt, clip, seal, fluid, O-ring |

The complete vehicle exposes all current proprietary systems:

1. Core structure
2. Combustion engine
3. Air intake
4. Forced induction
5. Transmission and drivetrain
6. Suspension
7. Steering
8. Brakes
9. Wheels and tires
10. Electrical system
11. ECUs, modules, and controllers
12. Sensors
13. Actuators
14. Lighting
15. HVAC
16. Passive safety
17. ADAS
18. Exterior body
19. Wipers and washing
20. Interior
21. Infotainment and communication
22. Access and immobilizer
23. Hybrid and electric systems
24. Fluids, consumables, and wear
25. Fasteners, seals, and hardware
26. Functional index and rules

Systems that are not installed on the primary Hyundai profile remain available
as source-backed reference cases. They must not appear as installed equipment
without explicit applicability evidence.

`Functional index and rules` is a non-physical knowledge layer. Its entities
remain searchable and may point to relevant physical nodes, but they do not
create floating parts or claim an installed location.

## 6. Stable 3D Identity Contract

Every proprietary component keeps its existing `entity.id`. A new visual
binding schema extends the current binding without changing source identity.

```text
VehicleTwinBinding
  entityId
  nodeId
  nodeKind
  parentNodeId
  assemblyId
  systemId
  archetypeId
  locationAnchorId
  installedTransform
  explodedTransform
  materialClass
  visualAuthority
  dimensionalState
  visibilityPolicy
  accessDependencyIds[]
  sourceDocumentId
  sourceBlockId
  sourceTextHash
```

Required invariants:

- `entityId` remains the proprietary catalog ID.
- `nodeId` is stable across application releases.
- every component entity has exactly one primary physical or informational
  node binding;
- one node may reference one archetype instance, never another entity's
  provenance;
- parent references form an acyclic graph;
- every node resolves to an existing system and assembly;
- every source hash remains unchanged;
- all generic bindings remain non-dimensional;
- applicability is independent from visual availability.

Allowed `nodeKind` values are `PHYSICAL_COMPONENT`, `INFORMATIONAL_REFERENCE`,
and `SYSTEM_ANCHOR`. Only `PHYSICAL_COMPONENT` requires renderable geometry.

## 7. Automotive Archetype Atlas

The atlas replaces seed-based geometry with recognizable visual families. It
must cover at least these families before the old universal scene can cease to
be the default:

- vehicle shell, unibody, firewall, subframes, crossmembers;
- inline engine block, cylinder head, crankshaft, camshaft, piston, connecting
  rod, bearings, covers, pulleys, belts, chains, manifolds, exhaust;
- automatic transmission case, torque converter, planetary gear family,
  valve body, differential, driveshaft, axle, CV joint;
- airbox, filter, throttle body, ducts, hoses, turbo family, intercooler;
- strut, spring, control arm, bushing, ball joint, hub, bearing, stabilizer;
- steering rack, column, tie rod, knuckle, pump, motor;
- disc, drum, caliper, pad, shoe, master cylinder, booster, ABS block, line;
- battery, alternator, starter, fuse, relay, connector, harness, ground point;
- ECU, sensor probe, pressure sensor, position sensor, speed sensor, actuator,
  solenoid, motor, pump, valve;
- lamp, lens, bulb, switch, wiper motor, linkage, washer pump;
- HVAC compressor, condenser, evaporator, blower, heater core, flap actuator;
- airbag, pretensioner, impact sensor, seat structure, restraint anchor;
- body panel, door, hinge, latch, glass, mirror, trim, weather seal;
- interior control, cluster, display, speaker, antenna, infotainment module;
- bolt, nut, washer, clip, bracket, gasket, seal, boot, O-ring, fluid volume.

The atlas may use parameterized geometry to vary proportions and connectors,
but each family must remain visually recognizable. Random boxes are not an
acceptable final fallback for a known component family.

## 8. Rendering Architecture

### Primary Renderer

- SceneView 4.22.0 integrated as a Jetpack Compose `SceneView`.
- Google Filament for physically based rendering.
- glTF/GLB 2.0 assets with stable node names.
- KTX2/Basis textures and MeshOpt or Draco compression where supported.
- image-based lighting plus directional key and rim lights;
- contact shadows under the vehicle and focused assembly;
- neutral automotive materials with neon diagnostic overlays;
- level-of-detail variants for complete vehicle, system, and isolated part;
- instancing for repeated hardware, clips, bolts, fuses, and connectors.

### Compatibility Fallback

The existing Canvas renderer remains behind a renderer capability interface.
It is activated only when:

- Filament initialization fails;
- the device lacks the required graphics capability;
- an asset fails validation or cannot be loaded;
- a test explicitly requests deterministic schematic rendering.

Fallback state must be visible as `Vista esquemática`; it must never silently
pretend to be the full 3D twin.

### Material Language

- steel and iron: neutral dark metal;
- aluminum: lighter rough metal;
- rubber: near-black, high roughness;
- plastic: system-neutral dark polymer;
- copper and terminals: restrained copper material;
- glass and lenses: controlled transparency;
- PCB and electronics: dark substrate with limited emissive traces;
- selected part: lime edge or halo, not solid lime material;
- active DTC hypothesis: pulsing red outline with evidence disclaimer;
- live electrical path: cyan animated trace;
- unavailable or unconfirmed equipment: desaturated ghost material.

## 9. Interaction And Camera Model

The primary screen opens at L0 with the complete car centered and a visible
hint of the active system panel.

Supported interactions:

- drag to orbit;
- pinch to zoom;
- two-finger drag to pan;
- tap to select;
- double tap to isolate and frame;
- reset camera icon;
- exploded-view slider;
- x-ray toggle;
- body/interior visibility toggle;
- `Ubicación`, `Aislar`, `Desmontar`, and `Volver al vehículo` commands;
- step forward and backward during a repair procedure.

Camera presets are data, not hard-coded screen coordinates:

```text
EXTERIOR_FRONT_3Q
EXTERIOR_REAR_3Q
ENGINE_BAY
UNDERBODY_FRONT
UNDERBODY_REAR
CABIN_FRONT
TRUNK
SELECTED_COMPONENT
REPAIR_STEP
```

The camera always recomputes framing from the selected node bounds. Switching
from 30 to 368 entities must not change the header layout or clip the scene.

## 10. Progressive Disclosure

The viewer never communicates completeness by drawing every component at once.
It communicates completeness through navigation and coverage counts.

1. L0 shows the full vehicle and system hotspots.
2. Selecting a system ghosts unrelated systems.
3. Selecting an assembly hides exterior occluders and frames the assembly.
4. Selecting a component isolates it while preserving a translucent parent.
5. Entering repair mode shows only the current step's target, dependencies,
   tools, safety zone, and removal path.

The UI displays both:

- total source-backed entities in the current branch;
- currently rendered node count.

This prevents `368 piezas` from being misread as 368 simultaneous meshes.

## 11. Repair Knowledge Binding

The existing `RepairProcedure` and `RepairStep` contracts become the execution
source for visual repair mode. Each step gains a separate visual binding so the
literal instruction remains unchanged.

```text
RepairStepVisualBinding
  procedureId
  stepId
  focusNodeIds[]
  contextNodeIds[]
  ghostNodeIds[]
  hideNodeIds[]
  removeNodeIds[]
  supportNodeIds[]
  cameraPreset
  animationAction
  motionAxis
  motionDistanceState
  toolOverlayIds[]
  hazardOverlayIds[]
  evidencePromptIds[]
```

Allowed animation actions:

```text
INSPECT
DISCONNECT
DRAIN
SUPPORT
LOOSEN
REMOVE
MOVE_ASIDE
CLEAN
MEASURE
INSTALL
TIGHTEN
TORQUE
RECONNECT
FILL
BLEED
CALIBRATE
VERIFY
```

`motionDistanceState` defaults to `ILLUSTRATIVE`. A real distance may only be
used when a verified source provides it.

The visual player must obey the existing procedure gate:

- the next step may be previewed;
- completion remains blocked when evidence is missing;
- torque completion remains blocked without a verified technical claim;
- high-voltage actions require the existing safety policy;
- the player cannot convert a training-only procedure into an approved repair;
- each completed step persists through `RepairProgressStore`;
- repair completion can feed the certified report and post-scan flow.

## 12. Proprietary Source Integration

The complete corpus remains the textual source of truth:

- 74,648 literal blocks;
- 4,753 component entities;
- 297 real cases;
- 347 source sections;
- both original document SHA-256 hashes.

Selecting a node must provide:

1. exact component name from the source;
2. vehicle or real-case scope;
3. source file and source order;
4. literal context blocks;
5. visual authority;
6. applicability and evidence warning;
7. available repair procedures;
8. related diagnostic and marketplace actions.

The 3D generation pipeline may add visual metadata, but it may not edit source
text or source hashes.

## 13. Data Flow

```text
DOCX literal corpus
  -> ProprietaryPartsCatalogRepository
  -> VehicleTwinManifest + VehicleTwinBinding index
  -> VehicleTwinRepository
  -> CompleteVehicleSceneState
  -> SceneView / Filament nodes

RepairProcedure + RepairStep
  -> RepairStepVisualBinding
  -> RepairTwinController
  -> camera + visibility + animation state
  -> evidence and safety gate
  -> RepairProgressStore
  -> certified report / post-scan flow
```

Core ownership boundaries:

- catalog repository owns source truth and search;
- twin repository owns hierarchy, geometry bindings, and asset validation;
- renderer owns pixels, transforms, camera, lighting, and hit testing;
- repair controller owns step visualization and progress orchestration;
- safety engine owns whether a step may be completed;
- reports own signed evidence and history.

## 14. Failure Handling

- Missing GLB: show the recognized archetype or schematic fallback.
- Missing binding: keep the entity searchable and display `Modelo 3D pendiente de validación`.
- Broken parent reference: reject the twin manifest during validation.
- Asset hash mismatch: do not load the asset; log a diagnostic event.
- Filament failure: switch to visible schematic fallback without crashing.
- Procedure references unknown node: reject that visual binding.
- Missing evidence: allow inspection but block completion.
- Unknown applicability: ghost the item and require physical confirmation.
- Memory pressure: unload inactive assemblies and lower the LOD.

## 15. Performance Budget

Measured on the connected `VER_N49` device:

- stable 60 FPS during orbit, zoom, explode, and repair-step transitions;
- janky frames below 5% for the isolated viewer session;
- 90th-percentile frame time at or below 20 ms;
- first complete-vehicle frame within 1.5 seconds after entering the viewer;
- selected-system transition within 350 ms after assets are warm;
- no blank canvas or off-screen scene during loading;
- active scene below 350,000 rendered triangles at the default LOD;
- repeated hardware uses instancing;
- the bundled core 3D asset pack adds no more than 25 MB compressed;
- inactive system assets are unloaded from GPU memory.

If a budget fails, reduce LOD, texture resolution, overdraw, and simultaneous
labels before reducing data coverage.

## 16. Accessibility And Device Layout

- The complete-car viewport gets the majority of the available height.
- On foldables and tablets, the detail sheet becomes a right-side inspector.
- On phones, the detail sheet is a bottom sheet that never covers selection.
- Three labels maximum are visible in the scene unless the user requests more.
- Labels are screen-space constrained and cannot overlap the system controls.
- Every visual command has a content description.
- Reduced-motion mode disables idle rotation, pulses, and animated traces.
- Color is never the only indicator of selection, DTC, or authority.

## 17. Migration Sequence

### Phase 1: Contracts And Renderer Shell

- introduce `VehicleTwinManifest`, node binding, hierarchy, and validators;
- introduce the renderer interface and SceneView implementation;
- load a complete generic compact-sedan shell with named major zones;
- preserve the current Canvas fallback.

### Phase 2: Full Vehicle Systems

- define all 26 system zones and major assemblies;
- implement L0-L2 navigation, x-ray, isolate, and exploded view;
- add stable camera presets and responsive inspector layouts.

### Phase 3: Component Atlas And Complete Binding

- implement the automotive archetype atlas;
- classify and bind all 4,753 component entities;
- validate zero orphan entities and zero duplicate primary bindings;
- expose source authority and literal context from every node.

### Phase 4: Repair Twin

- bind existing procedures and steps to visual actions;
- add repair-step playback, tools, hazards, evidence prompts, and gates;
- preserve training and review-required policies;
- connect progress to report and post-scan flows.

### Phase 5: Device Hardening

- run screenshot, pixel, interaction, accessibility, and performance checks;
- optimize LOD, textures, instancing, loading, and memory;
- build, reinstall, launch, navigate, and inspect through ADB;
- compare before and after screenshots on the connected Android device.

All phases ship together in the final APK. Partial phases may be tested locally
but must not replace the current experience until their acceptance gates pass.

## 18. Verification Strategy

### Unit And Contract Tests

- parse and validate every twin manifest;
- assert all 4,753 component IDs have one primary binding;
- assert every node has a valid parent, system, and assembly;
- assert the hierarchy is acyclic;
- assert source hashes and literal block counts remain unchanged;
- assert generic nodes cannot claim dimensional authority;
- assert repair bindings reference known procedures, steps, and nodes;
- assert completion gates cannot be bypassed by animation state.

### Asset Tests

- run glTF validation on every GLB;
- verify stable node names and asset hashes;
- enforce triangle, texture, and file-size budgets;
- reject missing materials, invalid transforms, or unbounded geometry;
- render deterministic golden views for each archetype family.

### UI And Device Tests

- complete vehicle visible and framed on first render;
- system selection, assembly isolation, x-ray, and explode controls work;
- component tap opens the exact proprietary record;
- repair steps focus and move only declared nodes;
- mobile, foldable, and tablet layouts do not overlap;
- no new `FATAL EXCEPTION` appears in logcat;
- process remains alive after repeated system changes;
- frame and startup budgets pass on the connected device.

## 19. Acceptance Criteria

The implementation is complete only when all statements are true:

1. The first 3D view is a complete car, not a cloud of primitives.
2. All 26 proprietary systems are reachable from the car.
3. Every one of the 4,753 component entities has a stable primary physical or
   informational node binding.
4. Every selected component exposes its exact literal source context.
5. Every physical part with a recognized family uses a recognizable archetype,
   not random seed geometry; informational references do not masquerade as
   physical parts.
6. The user can isolate and explode systems and assemblies without broken
   framing.
7. Repair procedures drive node focus, visibility, and removal order.
8. Evidence and safety gates remain authoritative.
9. The Hyundai profile label is exact and no unsupported equipment is shown as
   installed.
10. Visual authority is visible and no generic model is called OEM-exact.
11. The connected Android device passes launch, interaction, screenshot, and
    performance checks.
12. Android tests, web tests, and cross-runtime parity remain green.

## 20. References

- SceneView Android Compose and Filament documentation:
  <https://github.com/sceneview/sceneview>
- Google Filament PBR renderer documentation:
  <https://google.github.io/filament/>
- Khronos glTF Asset Creation Guidelines 2.0:
  <https://www.khronos.org/blog/introducing-asset-creation-guidelines-2.0-siggraph-2025>
- Khronos KTX texture container:
  <https://www.khronos.org/ktx/>
- `docs/PRODUCT_VISION.md`
- `docs/PRODUCT_OS_ROADMAP.md`
- `docs/VISUAL_DIAGNOSTICS_3D.md`
- `docs/architecture/proprietary-literal-parts-and-universal-3d.md`
