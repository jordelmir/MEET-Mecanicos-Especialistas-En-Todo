# MEET Master Automotive Knowledge Integration

**Date:** 2026-07-22  
**Status:** Approved by delegated staff-engineer authority  
**Reference vehicle:** Hyundai Accent/Verna 2005, 1.6 L, automatic, with market/VIN confirmation required  
**Universal scope:** Passenger vehicles from 1990 onward, expressed as a generic taxonomy until vehicle evidence proves applicability

## 1. Decision summary

MEET will not ingest the supplied master order as another giant text blob. The
file is a plain-text rendering of the same proprietary `Document (16)` and
`Document (17)` corpus already shipped in the app. The current checkout already
contains:

- 74,648 immutable source blocks;
- 4,753 component records plus 297 real cases;
- 347 source sections across 26 automotive systems;
- an offline full-text SQLite index;
- source hashes and procedural 3D bindings;
- a generic inline-four GLB with 346 meshes and 64 named mechanical families.

The missing capability is not storage. It is a reliable semantic layer that
turns those source blocks into evidence-gated relationships consumed uniformly
by DTC, diagnosis, AI, parts, repair procedures, commerce, and 3D.

The selected design therefore adds a deterministic **Automotive Knowledge
Fabric** above the immutable corpus and below every product surface.

## 2. Approaches considered

### A. Add the supplied text as another searchable database

This is low effort but wrong. It duplicates the existing corpus, increases
ambiguity, creates competing source IDs, and still leaves diagnosis and parts
engines hardcoded. Rejected.

### B. Replace every current engine with one generated monolithic graph

This would maximize apparent coverage quickly, but an automated extractor could
promote generic examples or uncertain claims into vehicle facts. It also creates
a high-regression rewrite across mature screens. Rejected.

### C. Add an evidence-gated knowledge fabric and migrate consumers incrementally

This preserves the literal corpus as authority, compiles only deterministic
relationships, layers curated vehicle knowledge above generic knowledge, and
lets existing engines remain as safe fallbacks while consumers migrate. It is
the selected approach.

## 3. Product outcome

For a real repair session, MEET must support this traceable path:

```text
Vehicle identity + market + engine + transmission
    -> observed symptom / scan provenance / DTC / freeze frame
    -> affected systems and candidate components
    -> mandatory confirmation tests ordered by safety and cost
    -> source-backed procedure, tools, warnings, and measurements
    -> visually locate the component in the best available 3D authority
    -> request a replacement only after evidence gates pass
    -> VIN/OEM/photo/connector/dimensions compatibility evidence
    -> quote, repair evidence, post-scan, certified report and history
```

Every recommendation must explain its evidence and uncertainty. A DTC identifies
a circuit or system; it never proves that a particular part is defective.

## 4. Authority model

Knowledge is resolved in this order:

1. **Observed vehicle evidence:** VIN, scanner data, measurements, photos,
   connector shape, dimensions, and user-confirmed equipment.
2. **Reviewed vehicle pack:** exact make/model/year/engine/transmission/market
   claims with source citations.
3. **Curated DTC pack:** source-backed circuit relationships and test sequences.
4. **Derived proprietary graph:** deterministic links compiled from the owner
   corpus, always retaining source block hashes.
5. **Universal 1990+ taxonomy:** generic component and system relationships.
6. **Literal search result:** source excerpt for discovery, never silently
   promoted to an exact claim.

The resolver must never raise authority. A generic or reference-vehicle example
cannot become an exact target-vehicle fact merely because its words match.

### Applicability states

- `CONFIRMED`: direct vehicle evidence or a reviewed closed tuple supports it.
- `PROBABLE`: strong but incomplete evidence; VIN/OEM/physical confirmation is
  still required.
- `CONDITIONAL`: depends on trim, market, emissions package, transmission, or
  optional equipment.
- `GENERIC`: valid only as system-level educational knowledge.
- `NOT_DOCUMENTED`: the source set does not establish presence.
- `NOT_APPLICABLE`: reviewed evidence explicitly excludes it.
- `CONFLICTED`: sources disagree; diagnosis and purchasing remain blocked.

`EXACT` compatibility is reserved for the existing product rule: VIN + OEM
evidence, a reviewed closed vehicle tuple including OEM identifier, or explicit
visual confirmation that the workflow defines as sufficient.

## 5. Reference vehicle truth profile

The Accent/Verna 2005 1.6 automatic is the end-to-end reference profile. Its
identity must be confirmed from the active vehicle record and cannot be inferred
from the presence of the corpus.

Negative assertions are first-class regression contracts. Until reviewed
evidence says otherwise, the app must not assume this vehicle has:

- a MAF sensor when MAP + IAT is the documented architecture;
- proportional electronic APP/throttle-by-wire;
- diesel rail-pressure, DPF, NOx, or glow-plug systems;
- turbo boost hardware;
- ADAS, FlexRay, EPS, EPB, or occupant-classification hardware;
- any other optional component merely because the universal BOM lists it.

The universal catalog may display those systems as educational examples, but the
reference vehicle resolver must label them `NOT_DOCUMENTED`, `CONDITIONAL`, or
`NOT_APPLICABLE` as supported by reviewed evidence.

## 6. Compiled knowledge graph

### 6.1 Artifact

A deterministic build tool will produce a versioned graph artifact from:

- `public/knowledge/proprietary/manifest.json`;
- `public/knowledge/proprietary/entity_index.json`;
- the 347 immutable section shards;
- validated packs under `android/app/src/main/assets/knowledge/packs/`;
- the universal parts ontology;
- curated mapping rules stored as reviewable source files.

The graph is derived data. It can be rebuilt byte-for-byte from committed inputs.
The compiler writes atomically, sorts all records, rejects broken references, and
emits a SHA-256 manifest.

### 6.2 Core nodes

- `SYSTEM`
- `ASSEMBLY`
- `COMPONENT`
- `ALIAS`
- `SYMPTOM`
- `DTC`
- `DIAGNOSTIC_TEST`
- `MEASUREMENT`
- `PROCEDURE`
- `PROCEDURE_STEP`
- `TOOL`
- `SAFETY_WARNING`
- `PART_CANDIDATE`
- `VEHICLE_PROFILE`
- `SOURCE_BLOCK`
- `VISUAL_TARGET`

### 6.3 Core edges

- `PART_OF`
- `HAS_ALIAS`
- `MAY_CAUSE`
- `MAY_SET_DTC`
- `AFFECTS`
- `CONFIRMED_BY_TEST`
- `REQUIRES_TEST_BEFORE_REPLACE`
- `USES_TOOL`
- `HAS_WARNING`
- `HAS_PROCEDURE`
- `HAS_STEP`
- `NEXT_STEP`
- `SUGGESTS_PART_CANDIDATE`
- `APPLIES_TO`
- `EXCLUDED_FROM`
- `SUPPORTED_BY_SOURCE`
- `VISUALIZED_BY`

Each edge includes source block IDs, confidence, applicability, review state, and
whether physical/VIN/OEM confirmation is required.

### 6.4 Safe derivation rules

The compiler may derive hierarchy from section ownership and explicit component
parentage already present in the literal catalog. It may connect source detail to
its owning component and normalize aliases conservatively.

It must not automatically invent:

- numeric probability;
- torque, pressure, voltage, resistance, pinout, or dimensions;
- OEM part numbers;
- exact vehicle applicability;
- DTC-to-part causality from mere co-occurrence;
- a real 3D mesh binding from a similar name.

Those relationships require curated rules or reviewed packs.

## 7. Runtime architecture

### 7.1 `AutomotiveKnowledgeRepository`

This read-only repository validates and opens the compiled graph. It provides:

- component/system search;
- source-backed neighborhood traversal;
- DTC and symptom candidate lookup;
- procedures, tools, warnings, and measurements;
- applicability filtering for the active vehicle;
- 3D visual target resolution;
- integrity status and graph statistics.

The repository must fail closed if the graph hash, schema, node references, or
source corpus hash do not match.

### 7.2 `VehicleApplicabilityResolver`

This pure service combines the active vehicle tuple with reviewed claims and
observed evidence. It returns an applicability decision with:

- state and confidence;
- evidence used;
- missing evidence;
- warnings;
- explicit reason;
- whether diagnosis, replacement, or purchase is allowed.

### 7.3 `RepairKnowledgeOrchestrator`

This is the single product-facing facade. Given vehicle identity, provenance,
DTCs, symptoms, observations, and selected component, it returns a
`RepairKnowledgeBundle` containing:

- ranked affected systems/components without fake precision;
- mandatory next tests;
- do-not-replace-yet gates;
- source citations;
- safety warnings;
- procedures and tools;
- part-request eligibility and required compatibility evidence;
- 3D focus targets and visual authority;
- insufficient-data reasons.

Existing hardcoded engines become fallback contributors behind this facade.
They cannot override a reviewed exclusion or evidence gate.

## 8. Product-surface integration

### DTC and diagnosis

- Resolve each scanned code into affected systems and candidate components.
- Keep scan provenance and freeze frame distinct from manual/demo data.
- Order confirmation tests before replacement suggestions.
- Replace unsupported hardcoded confidence percentages with ranked evidence
  classes unless a validated statistical model supplies probabilities.
- Surface explicit insufficient-data states.

### AI

- Construct prompts only from the resolved bundle and literal source citations.
- Require the model to distinguish observation, source claim, inference, and
  recommendation.
- Return structured JSON that validates against the existing diagnostic response
  contract.
- Reject uncited exact values and incompatible vehicle assumptions.
- Degrade to offline deterministic guidance when an external AI provider fails.

### Parts and replacement marketplace

- Map candidates to normalized components, not free-form names alone.
- Block part requests when there is neither a DTC nor an identified component,
  matching the closed-loop product rule.
- Show `do not replace yet` tests before a request can advance.
- Collect VIN/OEM/photo/connector/dimensions evidence according to risk.
- Preserve the current quote, antifraud, report, and history contracts.

### Repair procedures

- Present source-backed steps, required tools, warnings, checkpoints, and final
  validation.
- Structural, SRS, high-voltage, fuel, brake, refrigerant, welding, and pressure
  work must display domain-specific warnings and professional-only gates.
- Unknown torque or measurement values must display `Dato no capturado` or
  `Pendiente de validación`, never a guessed number.

### 3D motor and vehicle twin

- Retain the verified 360-degree inline-four engine asset.
- Bind a component to a real named mesh only when the asset contract explicitly
  contains the relevant part key.
- Otherwise use the existing system-level procedural schematic.
- Display visual authority (`L2_GENERIC_ASSEMBLY`, `PROCEDURAL_SCHEMATIC`, or
  equivalent) and never imply OEM dimensions.
- A 3D selection opens the same `RepairKnowledgeBundle` consumed by DTC, AI, and
  parts; it must not run a separate knowledge path.
- DTC focus, exploded view, component picking, and procedure steps use stable
  semantic IDs so the camera state and source evidence remain linked.

### Web and Android parity

TypeScript and Kotlin consume the same generated semantic identifiers and
applicability vocabulary. Byte-exact report hashing remains unchanged. Any new
serialized evidence included in certified reports gets its own cross-runtime
fixture before publication.

## 9. User experience

The primary interaction is a **Repair Intelligence panel** reachable from DTC,
component locator, proprietary catalog, AI diagnosis, and part request. It shows:

1. what MEET observed;
2. what the source corpus states;
3. what MEET infers;
4. the next confirmation test;
5. the component in 3D at honest visual authority;
6. replacement and purchase gates;
7. citations and unresolved uncertainty.

Guided user mode shows one next action. Mechanic/taller/flota modes may expose the
full evidence graph, raw measurements, and alternate branches.

## 10. Error handling and offline behavior

- Graph validation failure falls back to the immutable literal corpus and current
  curated packs; it never crashes the app or silently trusts corrupt data.
- A missing external AI or Supabase connection does not disable offline
  diagnosis, search, 3D, or procedures.
- Missing vehicle identity blocks exact applicability but still permits clearly
  labeled generic education.
- Conflicting claims are visible and block replacement/purchase when material.
- Search and graph artifacts are copied atomically to `noBackupFilesDir` and are
  keyed by corpus hash, avoiding a destructive Room migration.

## 11. Verification strategy

### Compiler and contract tests

- deterministic graph hash across repeated builds;
- no duplicate IDs or orphan edges;
- every derived record traces to a source block or curated rule;
- no unreviewed exact applicability;
- no numeric measurement without units, conditions, tool, tolerance, and source;
- no safety-critical procedure without a warning;
- source corpus count and hash remain unchanged;
- Android and public artifacts match.

### Reference-vehicle regressions

- P0230 produces circuit-first tests and does not immediately condemn the pump;
- Accent/Verna does not gain MAF, APP, EPS, EPB, turbo, diesel, ADAS, or modern
  occupant-classification hardware through generic matching;
- front suspension traversal reaches control arm, ball joint, bushings,
  stabilizer bar, link, procedure, alignment requirement, symptoms, and tests;
- unknown torque remains unknown;
- part compatibility cannot become `EXACT` without the defined evidence.

### Runtime tests

- Kotlin unit tests for repository, resolver, orchestrator, AI context, part
  gates, DTC mapping, and 3D target selection;
- TypeScript contract tests for shared vocabulary and graph artifact;
- integration tests from P0230 and a non-powertrain case through the complete
  bundle;
- existing knowledge, parts, reports, and parity suites remain green;
- debug APK build, physical-device install, launch, foreground PID, and crash-log
  verification.

## 12. Delivery sequence

1. Establish graph schema, curated rule format, compiler, manifest, and tests.
2. Generate the complete generic graph plus the Accent reference overlay.
3. Add Kotlin repository, applicability resolver, and orchestrator.
4. Integrate DTC/diagnosis and grounded AI.
5. Integrate parts, compatibility evidence, and repair procedures.
6. Integrate 3D semantic focus and Repair Intelligence UI.
7. Add TypeScript contract consumption and parity fixtures where serialized
   report evidence changes.
8. Run full tests, build APK, install by ADB, validate device behavior, update
   documentation, and publish only after all gates pass.

Each step is additive and independently testable. A later phase may improve the
visual meshes, but lack of a final OEM mesh does not block truthful semantic
integration.

## 13. Acceptance criteria

The implementation is complete only when:

- all 74,648 source blocks remain addressable with their hashes;
- every compiled relationship is traceable and deterministic;
- all six target surfaces consume the same orchestration contract;
- the reference vehicle passes positive and negative applicability tests;
- no DTC directly asserts a failed part without required confirmation;
- 3D selections and DTC focus resolve to the same semantic component IDs;
- part requests carry evidence requirements and cannot claim unsupported exact
  compatibility;
- offline operation remains available;
- existing report integrity and TS/Kotlin parity remain green;
- the APK installs and runs on the physical Android device without fatal crash or
  ANR during the validated flow.

