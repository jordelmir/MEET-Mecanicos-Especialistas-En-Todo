# Complete 3D Vehicle Repair Twin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the proprietary catalog's abstract box cloud with a complete, interactive, source-backed 3D vehicle twin that preserves all 4,753 component identities and drives repair context on the connected Android device.

**Architecture:** A SceneView/Filament renderer owns real-time vehicle geometry while a pure Kotlin vehicle-twin manifest maps every proprietary entity to one of 26 system anchors and a stable semantic node. Compose remains responsible for navigation, evidence text, system controls, truthful authority labels, and a visible schematic fallback. The reference GLB supplies vehicle context without claiming Hyundai OEM geometry.

**Tech Stack:** Kotlin 2.4.0, Kotlin Compose compiler plugin 2.4.0, Android Gradle Plugin 9.2.1, Gradle 9.4.1, Compose BOM 2026.05.01, SceneView 4.22.0, Google Filament 1.71.5, glTF/GLB 2.0, kotlinx.serialization, JUnit 4, ADB.

---

## Task 1: Migrate The Android Toolchain As One Compatible Set

**Files:**
- Modify: `android/build.gradle.kts`
- Modify: `android/app/build.gradle.kts`
- Modify: `android/gradle/wrapper/gradle-wrapper.properties`
- Modify: `android/gradle.properties`

- [ ] Record the current dependency graph and baseline unit-test/build status.
- [ ] Upgrade Kotlin to 2.4.0 and apply `org.jetbrains.kotlin.plugin.compose` 2.4.0.
- [ ] Upgrade Compose BOM to 2026.05.01 in production and Android tests.
- [ ] Compile against API 37 as required by the 2026.05 AndroidX artifacts while retaining targetSdk 35 for this feature release.
- [ ] Upgrade AGP to 9.2.1 and Gradle to 9.4.1, use built-in Kotlin, and migrate annotation processing to the documented `com.android.legacy-kapt` bridge.
- [ ] Upgrade the smallest coherent Hilt/processor set needed by the new compiler.
- [ ] Upgrade Hilt to 2.60.1, AndroidX Hilt to 1.4.0, and Room to 2.8.4 so annotation processing consumes Kotlin 2.4 metadata natively.
- [ ] Add `io.github.sceneview:sceneview:4.22.0` without mass-upgrading unrelated libraries.
- [ ] Run `cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace` and resolve migration failures from official guidance.

## Task 2: Add And Validate The Reference Vehicle Asset

**Files:**
- Create: `android/app/src/main/assets/models/vehicle_twin/reference_vehicle.glb`
- Create: `android/app/src/main/assets/models/vehicle_twin/ATTRIBUTION.md`
- Create: `android/app/src/test/kotlin/com/elysium369/meet/visual3d/ReferenceVehicleAssetTest.kt`

- [ ] Write a failing test requiring a non-empty GLB 2.0 header and the exact asset path.
- [x] Download the Khronos CarConcept CC BY 4.0 GLB from the official glTF Sample Assets repository.
- [ ] Record upstream URL, license, SHA-256, and the explicit non-OEM truth boundary.
- [ ] Run the focused asset test and preserve the failing/passing evidence.

## Task 3: Define The Vehicle-Twin Contract Test First

**Files:**
- Create: `android/app/src/main/kotlin/com/elysium369/meet/visual3d/domain/VehicleTwinContract.kt`
- Create: `android/app/src/test/kotlin/com/elysium369/meet/visual3d/VehicleTwinContractTest.kt`

- [ ] Write failing tests for stable node IDs, six service levels, 26 systems, valid parent references, physical versus informational nodes, and conservative visual authority.
- [ ] Implement immutable twin contracts and validation errors with no Android dependency.
- [ ] Run the focused unit test red, then green.

## Task 4: Bind Every Proprietary Component To The Twin

**Files:**
- Create: `android/app/src/main/kotlin/com/elysium369/meet/visual3d/data/ProprietaryVehicleTwinMapper.kt`
- Create: `android/app/src/test/kotlin/com/elysium369/meet/visual3d/ProprietaryVehicleTwinMapperTest.kt`

- [ ] Write a failing coverage test against `entity_index.json` requiring exactly one physical or informational primary binding for every component entity.
- [ ] Map all 26 source systems to stable installed-location anchors, camera presets, material classes, and applicable/not-installed states.
- [ ] Preserve `entity.id`, source document, source block, source hash, source order, and dimensional truth for all 4,753 entries.
- [ ] Validate that overview/rules remain informational and non-installed Hyundai-inapplicable systems are never represented as confirmed equipment.
- [ ] Run the focused mapper test red, then green.

## Task 5: Implement The SceneView/Filament Renderer

**Files:**
- Create: `android/app/src/main/kotlin/com/elysium369/meet/visual3d/ui/CompleteVehicleTwinView.kt`
- Create: `android/app/src/main/kotlin/com/elysium369/meet/visual3d/ui/VehicleTwinViewportState.kt`

- [ ] Load one cached GLB model instance with physically based rendering and controlled environment lighting.
- [ ] Implement orbit, zoom, pan, reset, auto-rotation, system focus, x-ray/ghost mode, exploded-progress state, and selected-system emissive overlay.
- [ ] Render only bounded system hotspots and the selected semantic branch, never 4,753 simultaneous meshes.
- [ ] Surface a visible `Vista esquemática` fallback if Filament or the asset fails.
- [ ] Keep interactive state stable across recomposition and dispose engine resources through SceneView lifecycle APIs.

## Task 6: Integrate Complete-Car Navigation And Repair Context

**Files:**
- Modify: `android/app/src/main/kotlin/com/elysium369/meet/ui/screens/ComponentLocatorScreen.kt`
- Modify: `android/app/src/main/kotlin/com/elysium369/meet/ui/components/Interactive3DDiagView.kt`
- Create: `android/app/src/main/kotlin/com/elysium369/meet/visual3d/domain/RepairStepVisualBinding.kt`
- Create: `android/app/src/test/kotlin/com/elysium369/meet/visual3d/RepairStepVisualBindingTest.kt`

- [ ] Make the complete vehicle the default proprietary-catalog scene.
- [ ] Connect system chips, list selection, `Ubicación`, `Aislar`, `Desmontar`, reset, x-ray, and exploded controls to the viewport state.
- [ ] Keep source-backed literal details and the Hyundai Accent/Verna 2005 automatic 1600 cc profile visible outside the 3D asset authority label.
- [ ] Bind repair actions to focus/context/ghost/remove node sets without changing literal source instructions.
- [ ] Display total source entities and current rendered-node count separately.

## Task 7: Full Regression And Source-Integrity Gates

**Files:**
- Verify only; repair the narrowest affected files if a gate fails.

- [ ] Run `cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace`.
- [ ] Run `bash tests/parity/ci-verify.sh`.
- [ ] Verify the manifest still reports 74,648 blocks, 4,753 entities, 297 real cases, and 347 sections.
- [ ] Verify both source-document SHA-256 values and every existing `sourceTextHash` remain unchanged.
- [ ] Inspect `git diff --check` and ensure unrelated user/Mavis changes were not reverted.

## Task 8: Install And Audit On The Connected Android Device

**Files:**
- Output evidence: `build/device-audit/complete-vehicle-twin-*.png`

- [ ] Run the conservative Codex/Mavis sync audit before the final assemble because the shared worktree is dirty.
- [ ] Install with `adb install -r -d`, launch with `am start -W`, confirm the foreground activity, and check `pidof` plus crash-focused logcat.
- [ ] Navigate to Motor 3D, exercise complete-car/system/component/exploded flows, and capture the active physical display.
- [ ] Inspect captures for real vehicle geometry, legible labels, non-overlap, correct asset authority, and complete data counts.
- [ ] Reset `gfxinfo`, run an interaction sample, and report total/janky frames plus frame-time percentiles.
- [ ] Compare the new capture and measurements with the prior abstract-renderer evidence without overstating the result.
