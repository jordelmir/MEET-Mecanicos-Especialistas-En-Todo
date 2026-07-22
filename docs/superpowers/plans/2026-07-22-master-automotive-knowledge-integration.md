# Master Automotive Knowledge Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a deterministic, evidence-gated knowledge graph from MEET's complete proprietary corpus and make DTC, diagnosis, AI, parts, procedures, and 3D consume one shared repair-intelligence contract.

**Architecture:** Preserve the 74,648-block literal corpus as immutable evidence, compile a compact semantic graph with source references, then resolve it through an Accent-aware applicability layer and one Kotlin orchestrator. Existing engines remain fallback contributors and may not override reviewed exclusions or replacement gates.

**Tech Stack:** Python 3 standard library, JSON/JSON Schema, SHA-256, Kotlin/JVM 17, kotlinx.serialization, Android assets, Jetpack Compose, TypeScript, Vitest, JUnit 4, Gradle, ADB.

---

## File structure

### Build-time knowledge files

- Create `tools/knowledge/schema/automotive-knowledge-graph.schema.json`: graph artifact contract.
- Create `tools/knowledge/curated/accent_verna_2005_knowledge.json`: reviewed reference-vehicle applicability and DTC rules.
- Create `tools/knowledge/build_automotive_knowledge_graph.py`: deterministic compiler.
- Create `tools/knowledge/tests/test_automotive_knowledge_graph.py`: compiler, safety, and determinism tests.
- Create `public/knowledge/graph/automotive_knowledge_graph.json`: generated web artifact.
- Create `android/app/src/main/assets/knowledge/graph/automotive_knowledge_graph.json`: identical Android artifact.

### Android knowledge fabric

- Create `android/app/src/main/kotlin/com/elysium369/meet/core/knowledge/graph/KnowledgeGraphModels.kt`: serialization and domain vocabulary.
- Create `android/app/src/main/kotlin/com/elysium369/meet/core/knowledge/graph/AutomotiveKnowledgeGraphRepository.kt`: validated read-only asset access.
- Create `android/app/src/main/kotlin/com/elysium369/meet/core/knowledge/graph/VehicleApplicabilityResolver.kt`: evidence-gated vehicle applicability.
- Create `android/app/src/main/kotlin/com/elysium369/meet/core/knowledge/graph/RepairKnowledgeOrchestrator.kt`: unified product-facing bundle.
- Create `android/app/src/main/kotlin/com/elysium369/meet/core/knowledge/graph/KnowledgeDtc3dResolver.kt`: graph-to-visual target resolver.
- Create `android/app/src/main/kotlin/com/elysium369/meet/ui/components/RepairIntelligencePanel.kt`: shared evidence UI.

### Android integration and tests

- Modify `android/app/src/main/kotlin/com/elysium369/meet/ai/ProprietaryGroundedContextBuilder.kt`.
- Modify `android/app/src/main/kotlin/com/elysium369/meet/core/parts/PartSuggestionEngine.kt`.
- Modify `android/app/src/main/kotlin/com/elysium369/meet/ui/screens/ProprietaryPartsBrowser.kt`.
- Modify `android/app/src/main/kotlin/com/elysium369/meet/ui/screens/ComponentLocatorScreen.kt`.
- Modify `android/app/src/main/kotlin/com/elysium369/meet/ui/screens/DtcRepairGuideScreen.kt`.
- Modify `android/app/src/main/kotlin/com/elysium369/meet/ui/screens/PartRequestScreen.kt`.
- Create focused tests under `android/app/src/test/kotlin/com/elysium369/meet/core/knowledge/graph/`.
- Extend existing AI, parts, and 3D tests only where their public contracts change.

### TypeScript and documentation

- Create `lib/knowledge/automotive-knowledge-graph.ts`.
- Create `lib/knowledge/__tests__/automotive-knowledge-graph.test.ts`.
- Modify `tools/knowledge/README.md`.
- Create `docs/knowledge/AUTOMOTIVE_KNOWLEDGE_FABRIC.md`.

### Task 1: Define the graph and reference-vehicle source contracts

**Files:**
- Create: `tools/knowledge/schema/automotive-knowledge-graph.schema.json`
- Create: `tools/knowledge/curated/accent_verna_2005_knowledge.json`
- Test: `tools/knowledge/tests/test_automotive_knowledge_graph.py`

- [ ] **Step 1: Write failing contract tests**

Add tests that require schema version `1`, a 64-character source-corpus hash,
unique nodes and edges, allowed applicability states, and a reference profile
whose exclusions include MAF, electronic APP, EPS, EPB, turbo, diesel, and ADAS.

```python
def test_reference_vehicle_keeps_generic_hardware_out():
    curated = load_json(CURATED_PATH)
    excluded = {rule["canonicalKey"] for rule in curated["applicabilityRules"]
                if rule["state"] in {"NOT_APPLICABLE", "NOT_DOCUMENTED"}}
    assert {"maf_sensor", "app_sensor", "eps", "epb", "turbocharger", "dpf", "adas"} <= excluded

def test_curated_edges_require_source_or_observed_evidence():
    curated = load_json(CURATED_PATH)
    for edge in curated["edges"]:
        assert edge["sourceBlockIds"] or edge["evidenceRequired"]
```

- [ ] **Step 2: Run the tests and verify failure**

Run:

```bash
python3 -m unittest tools.knowledge.tests.test_automotive_knowledge_graph -v
```

Expected: failure because the schema, curated source, and compiler module do not exist.

- [ ] **Step 3: Add exact graph vocabulary**

The schema permits these node types:

```json
["SYSTEM", "SECTION", "COMPONENT", "DTC", "SYMPTOM", "DIAGNOSTIC_TEST", "PROCEDURE", "TOOL", "SAFETY_WARNING", "PART_CANDIDATE", "VEHICLE_PROFILE", "VISUAL_TARGET"]
```

It permits these applicability states:

```json
["CONFIRMED", "PROBABLE", "CONDITIONAL", "GENERIC", "NOT_DOCUMENTED", "NOT_APPLICABLE", "CONFLICTED"]
```

Every edge carries `sourceBlockIds`, `evidenceRequired`, `reviewState`,
`applicability`, and `confidence`. `CONFIRMED` edges require a curated source or
observed-evidence requirement.

- [ ] **Step 4: Add the Accent reference overlay**

Use profile ID `hyundai_accent_verna_2005_1_6_at`. Encode positive reviewed
architecture from the existing pack, explicit negative assertions, and a P0230
circuit-first sequence. Use `NOT_DOCUMENTED` when absence is not proven.

- [ ] **Step 5: Run contract tests**

Expected: all contract-source tests pass while compiler tests remain pending.

- [ ] **Step 6: Commit**

```bash
git add tools/knowledge/schema/automotive-knowledge-graph.schema.json tools/knowledge/curated/accent_verna_2005_knowledge.json tools/knowledge/tests/test_automotive_knowledge_graph.py
git commit -m "feat(knowledge): define evidence-gated automotive graph contract"
```

### Task 2: Compile the immutable corpus into a deterministic graph

**Files:**
- Create: `tools/knowledge/build_automotive_knowledge_graph.py`
- Modify: `tools/knowledge/tests/test_automotive_knowledge_graph.py`
- Create: `public/knowledge/graph/automotive_knowledge_graph.json`
- Create: `android/app/src/main/assets/knowledge/graph/automotive_knowledge_graph.json`

- [ ] **Step 1: Add failing compiler tests**

Tests must compile twice into temporary directories and assert identical bytes,
`5,050` entity-backed nodes, `26` system nodes, `347` section nodes, no orphan
edges, preserved corpus hash, and byte equality between public and Android
outputs.

```python
first = compile_graph(repo_root, temp_a)
second = compile_graph(repo_root, temp_b)
self.assertEqual(first.read_bytes(), second.read_bytes())
self.assertEqual(5050, graph["statistics"]["entityNodeCount"])
self.assertFalse(find_orphan_edges(graph))
```

- [ ] **Step 2: Verify tests fail because the compiler is absent**

Run the Task 1 test command and confirm the missing import/function failure.

- [ ] **Step 3: Implement deterministic compilation**

The compiler must:

```python
def build_graph(repo_root: Path) -> dict[str, Any]:
    manifest = load_json(repo_root / "public/knowledge/proprietary/manifest.json")
    index = load_json(repo_root / "public/knowledge/proprietary/entity_index.json")
    curated = load_json(repo_root / "tools/knowledge/curated/accent_verna_2005_knowledge.json")
    nodes = build_system_nodes(manifest) + build_section_nodes(manifest) + build_entity_nodes(index)
    edges = build_structural_edges(manifest, index) + curated["edges"]
    validate_references(nodes, edges)
    return finalize_with_content_hash(sorted_graph(nodes, edges, curated, manifest))
```

Use stable IDs and sorted arrays. Copy the same encoded bytes to public and
Android paths with atomic replacement. Do not duplicate literal source text;
store block IDs and hashes.

- [ ] **Step 4: Generate committed artifacts**

Run:

```bash
python3 tools/knowledge/build_automotive_knowledge_graph.py --repo-root .
```

Expected: one graph hash, two byte-identical artifacts, and printed node/edge statistics.

- [ ] **Step 5: Run all Python knowledge tests**

```bash
python3 -m unittest discover -s tools/knowledge/tests -p 'test_*.py'
```

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add tools/knowledge/build_automotive_knowledge_graph.py tools/knowledge/tests/test_automotive_knowledge_graph.py public/knowledge/graph android/app/src/main/assets/knowledge/graph
git commit -m "feat(knowledge): compile deterministic automotive knowledge graph"
```

### Task 3: Add Kotlin graph contracts and validated repository

**Files:**
- Create: `android/app/src/main/kotlin/com/elysium369/meet/core/knowledge/graph/KnowledgeGraphModels.kt`
- Create: `android/app/src/main/kotlin/com/elysium369/meet/core/knowledge/graph/AutomotiveKnowledgeGraphRepository.kt`
- Create: `android/app/src/test/kotlin/com/elysium369/meet/core/knowledge/graph/AutomotiveKnowledgeGraphRepositoryTest.kt`

- [ ] **Step 1: Write repository tests**

Require decoding of the real asset, corpus/hash validation, stable lookup by ID,
system filtering, source-ref preservation, and rejection of a modified content hash.

```kotlin
@Test fun `real graph validates and exposes every catalog entity`() {
    val graph = AutomotiveKnowledgeGraphParser.decode(asset().readText())
    assertEquals(5_050, graph.statistics.entityNodeCount)
    assertEquals(74_648, graph.statistics.sourceBlockCount)
    assertTrue(graph.nodes.all { it.sourceRefs.all(SourceRef::isComplete) })
}
```

- [ ] **Step 2: Run the focused test and confirm compilation failure**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*AutomotiveKnowledgeGraphRepositoryTest'
```

- [ ] **Step 3: Implement serialization contracts and parser**

Define `KnowledgeNode`, `KnowledgeEdge`, `SourceRef`, `VehicleGraphProfile`,
`GraphStatistics`, and `AutomotiveKnowledgeGraph`. The parser recomputes the
canonical content hash from the payload without `contentSha256`, validates unique
IDs and edge endpoints, then constructs maps for lookup.

- [ ] **Step 4: Implement repository lookups**

Provide `node(id)`, `neighbors(id, edgeTypes)`, `components(systemId)`,
`dtc(code)`, `profile(id)`, and `integrityStatus()`.

- [ ] **Step 5: Run focused and catalog tests**

Expected: repository and existing proprietary catalog tests pass.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/elysium369/meet/core/knowledge/graph android/app/src/test/kotlin/com/elysium369/meet/core/knowledge/graph
git commit -m "feat(android): add validated automotive graph repository"
```

### Task 4: Resolve vehicle applicability without false exactness

**Files:**
- Create: `android/app/src/main/kotlin/com/elysium369/meet/core/knowledge/graph/VehicleApplicabilityResolver.kt`
- Create: `android/app/src/test/kotlin/com/elysium369/meet/core/knowledge/graph/VehicleApplicabilityResolverTest.kt`

- [ ] **Step 1: Write applicability tests**

Cover the exact reference tuple, missing market/VIN, universal educational mode,
reviewed exclusions, conflicting evidence, and the rule that `CONFIRMED` purchase
compatibility needs VIN+OEM or approved physical evidence.

```kotlin
@Test fun `generic MAF never becomes an Accent fact`() {
    val result = resolver.resolve(accent, component("maf_sensor"), emptyList())
    assertTrue(result.state in setOf(NOT_DOCUMENTED, NOT_APPLICABLE))
    assertFalse(result.replacementAllowed)
    assertTrue(result.missingEvidence.contains(EvidenceKind.OEM))
}
```

- [ ] **Step 2: Verify focused tests fail**

Run the test class and confirm missing types.

- [ ] **Step 3: Implement the pure resolver**

Return `ApplicabilityDecision(state, confidence, reason, evidenceUsed,
missingEvidence, warnings, diagnosisAllowed, replacementAllowed,
purchaseCompatibility)`. Resolve reviewed exclusions before aliases or generic
matches. Never return `EXACT` from a name match.

- [ ] **Step 4: Run focused tests**

Expected: all applicability and negative-architecture tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/elysium369/meet/core/knowledge/graph/VehicleApplicabilityResolver.kt android/app/src/test/kotlin/com/elysium369/meet/core/knowledge/graph/VehicleApplicabilityResolverTest.kt
git commit -m "feat(diagnostics): enforce vehicle applicability evidence gates"
```

### Task 5: Build the unified repair orchestrator

**Files:**
- Create: `android/app/src/main/kotlin/com/elysium369/meet/core/knowledge/graph/RepairKnowledgeOrchestrator.kt`
- Create: `android/app/src/test/kotlin/com/elysium369/meet/core/knowledge/graph/RepairKnowledgeOrchestratorTest.kt`

- [ ] **Step 1: Write end-to-end bundle tests**

Test P0230, an unknown DTC, component-only navigation, and a generic non-reference
vehicle. P0230 must rank fuse/relay/wiring tests before the pump and PCM, expose
citations, set `replacementAllowed=false`, and produce a 3D target.

```kotlin
val bundle = orchestrator.resolve(RepairKnowledgeRequest(accent, dtcs = listOf("P0230")))
assertEquals("P0230", bundle.dtcs.single().code)
assertTrue(bundle.nextTests.first().label.contains("fusible", ignoreCase = true))
assertTrue(bundle.doNotReplaceYet.isNotEmpty())
assertFalse(bundle.partGate.replacementAllowed)
assertTrue(bundle.citations.isNotEmpty())
```

- [ ] **Step 2: Verify focused test failure**

Run the class and confirm the orchestrator is missing.

- [ ] **Step 3: Implement request and bundle contracts**

Use explicit `Observation`, `DiagnosticProvenance`, `RepairCandidate`,
`ConfirmationTest`, `SafetyNotice`, `PartEvidenceGate`, `VisualFocusTarget`, and
`KnowledgeCitation` types. Do not use raw confidence percentages unless marked
`CURATED_STATISTICAL`.

- [ ] **Step 4: Implement layered resolution**

Resolve reviewed graph edges first, source-backed component neighborhoods second,
and existing engine suggestions last. Deduplicate by canonical component ID.
Reviewed exclusions always win.

- [ ] **Step 5: Run focused and existing diagnosis/parts tests**

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/elysium369/meet/core/knowledge/graph/RepairKnowledgeOrchestrator.kt android/app/src/test/kotlin/com/elysium369/meet/core/knowledge/graph/RepairKnowledgeOrchestratorTest.kt
git commit -m "feat(diagnostics): orchestrate cited repair intelligence"
```

### Task 6: Ground AI and replacement suggestions in the bundle

**Files:**
- Modify: `android/app/src/main/kotlin/com/elysium369/meet/ai/ProprietaryGroundedContextBuilder.kt`
- Modify: `android/app/src/main/kotlin/com/elysium369/meet/core/parts/PartSuggestionEngine.kt`
- Modify: corresponding existing tests.

- [ ] **Step 1: Add failing AI and part-gate tests**

Require AI JSON to contain observation/source/inference separation, citations,
applicability state, missing evidence, and `doNotReplaceYet`. Require part
suggestions to be informational until the bundle's evidence gate allows a request.

- [ ] **Step 2: Run both test classes and verify failure**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*ProprietaryGroundedContextBuilderTest' --tests '*PartSuggestionEngineTest'
```

- [ ] **Step 3: Add `build(bundle)` to the AI context builder**

Keep the existing entity/block overload. The new overload serializes only bounded
cited evidence and includes the policy:

```text
OBSERVATIONS_ARE_NOT_SOURCE_CLAIMS; INFERENCES_REQUIRE_CITATIONS; EXACT_VALUES_REQUIRE_REVIEWED_EVIDENCE
```

- [ ] **Step 4: Add evidence-aware part suggestion overload**

Return existing `PartSuggestion` values with explicit `evidenceState`,
`requestAllowed`, and `missingEvidence`. Preserve old callers through defaults.

- [ ] **Step 5: Run focused tests**

Expected: pass with old behavior preserved and new safety fields enforced.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/elysium369/meet/ai/ProprietaryGroundedContextBuilder.kt android/app/src/main/kotlin/com/elysium369/meet/core/parts/PartSuggestionEngine.kt android/app/src/test/kotlin/com/elysium369/meet/ai/ProprietaryGroundedContextBuilderTest.kt android/app/src/test/kotlin/com/elysium369/meet/core/parts/PartSuggestionEngineTest.kt
git commit -m "feat(ai): ground diagnosis and parts in repair evidence"
```

### Task 7: Resolve graph components to honest 3D targets

**Files:**
- Create: `android/app/src/main/kotlin/com/elysium369/meet/core/knowledge/graph/KnowledgeDtc3dResolver.kt`
- Create: `android/app/src/test/kotlin/com/elysium369/meet/core/knowledge/graph/KnowledgeDtc3dResolverTest.kt`
- Modify: `android/app/src/main/kotlin/com/elysium369/meet/visual3d/data/ProprietaryVehicleTwinMapper.kt`

- [ ] **Step 1: Write visual authority tests**

Assert that `map_sensor` resolves to the inline-four named part key with
`L2_GENERIC_ASSEMBLY`, while an unmapped body component resolves to its system
schematic with `PROCEDURAL_SCHEMATIC`. Neither may claim OEM dimensions.

- [ ] **Step 2: Run focused tests and verify failure**

- [ ] **Step 3: Implement alias normalization against committed asset contracts**

Use only `GenericInlineFourAssetContract.partKeys` and generated system bindings.
Do not inspect GLB node names heuristically at runtime.

- [ ] **Step 4: Add DTC focus resolution**

Map graph DTC candidates to stable catalog entity IDs and then to the best visual
target. Fall back to the existing static `DtcTo3dComponentMap` only when the graph
has no reviewed candidate.

- [ ] **Step 5: Run 3D contract suites**

Expected: all existing and new visual tests pass.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/elysium369/meet/core/knowledge/graph/KnowledgeDtc3dResolver.kt android/app/src/main/kotlin/com/elysium369/meet/visual3d/data/ProprietaryVehicleTwinMapper.kt android/app/src/test/kotlin/com/elysium369/meet/core/knowledge/graph/KnowledgeDtc3dResolverTest.kt
git commit -m "feat(visual3d): bind repair knowledge to honest 3D targets"
```

### Task 8: Add Repair Intelligence UI and connect all entry points

**Files:**
- Create: `android/app/src/main/kotlin/com/elysium369/meet/ui/components/RepairIntelligencePanel.kt`
- Modify: `android/app/src/main/kotlin/com/elysium369/meet/ui/screens/ProprietaryPartsBrowser.kt`
- Modify: `android/app/src/main/kotlin/com/elysium369/meet/ui/screens/ComponentLocatorScreen.kt`
- Modify: `android/app/src/main/kotlin/com/elysium369/meet/ui/screens/DtcRepairGuideScreen.kt`
- Modify: `android/app/src/main/kotlin/com/elysium369/meet/ui/screens/PartRequestScreen.kt`
- Create: `android/app/src/test/kotlin/com/elysium369/meet/ui/RepairIntelligencePresentationTest.kt`

- [ ] **Step 1: Write presentation mapping tests**

Map a bundle into deterministic sections: `Observado`, `Fuente`, `Inferencia`,
`Próxima prueba`, `No reemplazar todavía`, `Compatibilidad`, `3D`, and `Citas`.
Verify guided mode exposes one next action and mechanic mode exposes all sections.

- [ ] **Step 2: Verify focused tests fail**

- [ ] **Step 3: Implement the shared Compose panel**

The component accepts a bundle and callbacks `onRunTest`, `onOpen3d`,
`onRequestPart`, and `onOpenCitation`. Disable the part action when the graph gate
is closed and render the missing-evidence reason.

- [ ] **Step 4: Integrate catalog and 3D component locator**

Resolve the selected proprietary entity through the orchestrator. Keep literal
blocks and current 360 view intact; insert the shared panel above source details.

- [ ] **Step 5: Integrate DTC repair guide and part request**

Create a DTC bundle from the active code/provenance. Pre-fill part candidates but
block request submission until required evidence is present. Existing quote and
request persistence remain unchanged.

- [ ] **Step 6: Run presentation, screen compilation, and unit tests**

```bash
cd android && ./gradlew :app:testDebugUnitTest
```

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/elysium369/meet/ui/components/RepairIntelligencePanel.kt android/app/src/main/kotlin/com/elysium369/meet/ui/screens/ProprietaryPartsBrowser.kt android/app/src/main/kotlin/com/elysium369/meet/ui/screens/ComponentLocatorScreen.kt android/app/src/main/kotlin/com/elysium369/meet/ui/screens/DtcRepairGuideScreen.kt android/app/src/main/kotlin/com/elysium369/meet/ui/screens/PartRequestScreen.kt android/app/src/test/kotlin/com/elysium369/meet/ui/RepairIntelligencePresentationTest.kt
git commit -m "feat(android): connect repair intelligence across product surfaces"
```

### Task 9: Add TypeScript graph contract and web parity

**Files:**
- Create: `lib/knowledge/automotive-knowledge-graph.ts`
- Create: `lib/knowledge/__tests__/automotive-knowledge-graph.test.ts`

- [ ] **Step 1: Write failing TypeScript artifact tests**

Read the public artifact and assert schema/hash shape, unique IDs, no orphan edges,
reference profile presence, exclusion behavior, and equality with the Android file.

- [ ] **Step 2: Run the focused test and verify failure**

```bash
npx vitest run lib/knowledge/__tests__/automotive-knowledge-graph.test.ts
```

- [ ] **Step 3: Implement shared TypeScript types and validators**

Export the same applicability values and a `validateAutomotiveKnowledgeGraph`
function. Return structured validation issues instead of throwing on the first
problem.

- [ ] **Step 4: Run focused and full web tests**

```bash
npm test
npm run build
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add lib/knowledge public/knowledge/graph android/app/src/main/assets/knowledge/graph
git commit -m "feat(web): validate shared automotive knowledge graph"
```

### Task 10: Documentation, full verification, APK, and device proof

**Files:**
- Modify: `tools/knowledge/README.md`
- Create: `docs/knowledge/AUTOMOTIVE_KNOWLEDGE_FABRIC.md`
- Modify: design/plan checkboxes to reflect completed tasks.

- [ ] **Step 1: Document generation and authority boundaries**

Include exact compiler command, graph hash, statistics, runtime fallback order,
reference-vehicle limitations, and rules for adding reviewed vehicle overlays.

- [ ] **Step 2: Run source and generated-artifact checks**

```bash
python3 -m unittest discover -s tools/knowledge/tests -p 'test_*.py'
python3 tools/knowledge/build_automotive_knowledge_graph.py --repo-root . --check
git diff --exit-code -- public/knowledge/graph android/app/src/main/assets/knowledge/graph
```

- [ ] **Step 3: Run web, Android, and parity suites**

```bash
npm test
npm run build
bash tests/parity/ci-verify.sh
cd android && ./gradlew clean :app:testDebugUnitTest :app:assembleDebug
```

Expected: every command succeeds.

- [ ] **Step 4: Verify APK identity and install by ADB**

Record APK SHA-256, install with `adb install -r -d` or the safe push + `pm install`
fallback, launch `.MainActivity`, verify foreground activity and stable PID, then
inspect logcat for `FATAL EXCEPTION`, `AndroidRuntime`, and ANR.

- [ ] **Step 5: Exercise the reference flow on device**

Open Piezas y Reparaciones, select the Accent reference profile, navigate to a
P0230-related component, confirm the evidence panel, open the 360 view, and verify
that the part request remains gated until evidence is supplied.

- [ ] **Step 6: Commit final documentation and verification evidence**

```bash
git add tools/knowledge/README.md docs/knowledge docs/superpowers/plans/2026-07-22-master-automotive-knowledge-integration.md
git commit -m "docs(knowledge): document automotive knowledge fabric verification"
```

## Plan self-review

- Every design requirement maps to Tasks 1-10.
- The corpus remains immutable and nonduplicated.
- The reference vehicle has positive and negative applicability gates.
- DTC, diagnosis, AI, parts, procedures, and 3D converge on one bundle.
- Web/Android artifact equality and report parity are explicit gates.
- No task requires guessed measurements, OEM part numbers, or silent exact compatibility.
- No implementation placeholder remains in this plan.

