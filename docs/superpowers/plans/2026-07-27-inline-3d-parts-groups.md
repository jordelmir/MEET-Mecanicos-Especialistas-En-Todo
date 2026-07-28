# MEET Inline 3D Parts Groups — Implementation Plan

Date: 2026-07-27  
Branch: `codex/inline-3d-parts-groups`  
Owner constraint: no subagents

## Outcome

Ship an organized parts catalog with conservative cross-catalog identity
resolution and an interactive 3D/360 viewer embedded in the proprietary part
detail screen.

## Tasks

1. Add a pure system-family taxonomy and tests for complete, non-overlapping
   coverage of the proprietary manifest systems.
2. Add a conservative name/alias matcher and Android repository that resolves a
   proprietary entity to one unique G4ED or technical-atlas element.
3. Add tests for exact, plural, alias, ambiguity and no-match behavior.
4. Add a reusable loader for canonical manifest and binding pairs.
5. Replace the detail glyph with the inline 3D viewer, authority label, canonical
   identity and full-view navigation.
6. Replace the flat top-level filter with family blocks plus subsystem chips.
7. Insert system headers and counts into the result list.
8. Preserve literal search, roles, source hashes and existing atlas entry points.
9. Bump Android version and document the release.
10. Run targeted tests, full Android unit tests, TS/Kotlin parity and debug build.
11. Install, launch, inspect the target screen and review crash logs via ADB.
12. Commit, push, merge through a pull request and publish the verified APK.

## Verification gates

```bash
cd android
./gradlew :app:testDebugUnitTest --tests '*ProprietaryCanonical3dResolverTest'
./gradlew :app:testDebugUnitTest --tests '*CatalogSystemFamilyTest'
./gradlew :app:testDebugUnitTest
cd ..
bash tests/parity/ci-verify.sh
cd android
./gradlew :app:assembleDebug
```

ADB acceptance requires successful install, `am start -W`, resumed activity,
live PID, a screenshot of the inline viewer and no new app `FATAL EXCEPTION`.
