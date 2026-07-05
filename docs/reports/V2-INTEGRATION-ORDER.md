# V2 Reports — Integration Order

> **Audience:** an integrating AI (Codex, Mavis in a fresh session, or a
> third agent) that needs to take the 3 Mavis commits that landed on
> `main` between 2026-07-05 08:30 and 12:00 CST and turn them into a
> shipped feature. The commits compile and pass 19 unit tests, but they
> are NOT in production: they are not wired into the NavHost, not
> reached from the MechanicService completion path, and not yet
> connected to a remote verifier. This document lists exactly what to
> do, what NOT to do, and how to verify each step.

---

## TL;DR

Three commits, no production surface yet:

| Commit | Phase | What ships |
|---|---|---|
| `b676ff06` | 1 | Room entities + DAOs + `CertifiedReportRepository` + migration `41 → 42` + 8 tests |
| `711f83d4` | 3 | `QrPayload` + `ReportVerifier` (offline-first) + 11 tests |
| `e74cec0f` | 4-8 | `InspectionSessionScreen` + `VehicleHistoryScreen` + `CertifiedReportPdfRenderer` (6-page PDF with ZXing QR) + `PostScanPrompt` event channel + `ReportsOutboxBridge` |

Phase 2 (hash chain per vehicle) is implicit in Phase 1:
`ReportHashingService.signDraftReport(...)` already feeds
`previousHash` into the canonical string, and
`CertifiedReportRepository.verifyChainForVehicle(vehicleId)` walks the
chain via Room.

19 unit tests, all green:

```
./gradlew :app:testDebugUnitTest \
  --tests "com.elysium369.meet.core.reports.*" \
  --tests "com.elysium369.meet.data.local.CertifiedReportRepositoryTest"
→ 19/19 green
```

---

## What is NOT in production (the integration gap)

Three concrete hooks are dangling:

### Hook 1 — Navigation entry points

The two new Compose screens exist as top-level functions but are not
reached from any `NavHost` / `NavController`:

- `ui/screens/InspectionSessionScreen.kt` — entry signature
  `fun InspectionSessionScreen(vehicleId, vehicleLabel, vehicleVin,
  vehicleOdometerKm, obdConnected, onClose)`.
- `ui/screens/VehicleHistoryScreen.kt` — entry signature
  `fun VehicleHistoryScreen(vehicleId, vehicleLabel, onClose,
  onOpenReport)`.

**Where to wire them:**

| Caller | Target | Arguments |
|---|---|---|
| Garage → Vehicle → "Reportes" tile | `InspectionSessionScreen` | `vehicleId`, `vehicleLabel`, `vehicleVin`, `vehicleOdometerKm`, `obdConnected = obdViewModel.connectedState.value` |
| Garage → Vehicle → "Historial de Servicio" tile | `VehicleHistoryScreen` | `vehicleId`, `vehicleLabel`, `onOpenReport = { reportId -> /* push InspectionSessionScreen in read-only mode */ }` |
| `MechanicServiceScreen` on transition to COMPLETED (Hook 2 below) | `InspectionSessionScreen` with `selectedType = POST_SCAN_REPORT` | same as above |

### Hook 2 — MechanicService completion → Post-Scan auto-prompt

`MechanicServiceDao.updateMechanicStatusAndPrice(...)` is the moment
when a service flips to `COMPLETED`. At that exact line, the screen
must call:

```kotlin
PostScanPrompt.request(mechanicServiceId)
```

The host screen observes the prompt channel via
`PostScanPrompt.consume(mechanicServiceId)` and navigates to
`InspectionSessionScreen` with `selectedType = POST_SCAN_REPORT`. The
event channel is in `core/reports/PostScanPrompt.kt` and is
thread-safe (`@Synchronized`).

The integrating AI MUST add this call at the COMPLETED transition —
without it, the spec's "no permitir marcar reparación completa sin
post-scan" rule is unenforceable. Code reference: see
`PostScanPrompt.kt` for the contract and the
`Requested / Dismissed / Completed` event variants.

### Hook 3 — Remote verifier probe in `ReportVerifier`

`ReportVerifier` is currently 100% local because the constructor
defaults `remoteProbe = { null }`. To make the QR verifiable from a
device that never saw the report, rebind `remoteProbe` via Hilt to a
real HTTP client that hits the Supabase verifier endpoint. Pattern:

```kotlin
@Provides @Singleton
fun provideReportVerifier(
    repo: CertifiedReportRepository,
    supabase: SupabaseClient,
): ReportVerifier = ReportVerifier(
    reportRepo = repo,
    remoteProbe = { payload ->
        runCatching {
            supabase.postgrest
                .from("certified_reports")
                .select { filter { eq("reportId", payload.reportId) } }
                .decodeList<RemoteReportRow>()
                .firstOrNull()
                ?.integrityHash
                ?.equals(payload.integrityHash, ignoreCase = true)
        }.getOrNull()
    },
)
```

If the remote probe is not wired, the QR is still trustworthy on the
device that emitted it — but a QR scanned on a different device will
fall back to `VerifyResult.Invalid`. That is acceptable for this
shipping round but should be tracked in a follow-up issue.

---

## Step-by-step integration order

The order matters. Skipping ahead costs you merge conflicts because
each step assumes the previous one is in place.

### Step 1 — Verify the commits are reachable and tests are green

```bash
cd "/Users/jordelmirsdevhome/Downloads/Web Apps/MEET Mecanicos Especialistas En Todo"

# Confirm the three commits are present.
git log --oneline -3
# Expected:
#   e74cec0f feat(reports): Phases 4-8 — UI, timeline, PDF, post-scan prompt, outbox
#   711f83d4 feat(reports): Phase 3 — QR payload + ReportVerifier (offline-first)
#   b676ff06 feat(reports): Phase 1 — Room + repository for V2 certified reports

# Run only the Mavis-owned tests (the parallel agent's WIP is on disk
# but its test infra has filesystem permission issues that are out of
# scope for this integration).
./gradlew :app:testDebugUnitTest \
  --tests "com.elysium369.meet.core.reports.*" \
  --tests "com.elysium369.meet.data.local.CertifiedReportRepositoryTest"
# Expected: 19/19 green, BUILD SUCCESSFUL.
```

If the commits are NOT in `git log`, pull from the agent's branch
(typical pattern: `feature/mavis-reports-v2`) or replay the three
files in order:

1. `b676ff06` — 11 files (enums + entities + DAO + mapper + repository + test + MeetDatabase + AppModule migration).
2. `711f83d4` — 3 files (`QrPayload.kt`, `ReportVerifier.kt`, `ReportVerifierTest.kt`).
3. `e74cec0f` — 5 files (`InspectionSessionScreen.kt`, `VehicleHistoryScreen.kt`, `CertifiedReportPdfRenderer.kt`, `PostScanPrompt.kt`, `ReportsOutboxBridge.kt`).

### Step 2 — Run `sync.sh --auto` if the parallel agent's WIP is also in flight

```bash
bash ~/.mavis/skills/codex-mavis-sync/scripts/sync.sh --auto
```

This merges the Codex branch (with its own WIP on
`ui/screens/MechanicServiceScreen.kt`, `ui/ObdViewModel.kt`,
`core/obd/ObdSession.kt`, `core/services/*`, `core/parts/*`,
`core/diagnostics/*`) and the Mavis branch into a single
`sync/codex-mavis` integration branch. Without this step the APK
build will fail on one of the two sides. The skill is documented at
`~/.mavis/skills/codex-mavis-sync/SKILL.md`.

### Step 3 — Wire Hook 1 (Navigation)

Find the `NavHost` definition in the main app graph. Add two routes:

```kotlin
composable("inspection_session/{vehicleId}") { entry ->
    val vehicleId = entry.arguments?.getString("vehicleId").orEmpty()
    val vehicle = viewModel.activeVehicle(vehicleId) // implement lookup
    InspectionSessionScreen(
        vehicleId = vehicleId,
        vehicleLabel = "${vehicle.year} ${vehicle.make} ${vehicle.model}",
        vehicleVin = vehicle.vin,
        vehicleOdometerKm = vehicle.odometerKm,
        obdConnected = obdViewModel.isConnected(),
        onClose = { navController.popBackStack() },
    )
}

composable("vehicle_history/{vehicleId}") { entry ->
    val vehicleId = entry.arguments?.getString("vehicleId").orEmpty()
    val vehicle = viewModel.activeVehicle(vehicleId)
    VehicleHistoryScreen(
        vehicleId = vehicleId,
        vehicleLabel = "${vehicle.year} ${vehicle.make} ${vehicle.model}",
        onClose = { navController.popBackStack() },
        onOpenReport = { reportId ->
            navController.navigate("inspection_session/$vehicleId?reportId=$reportId")
        },
    )
}
```

Wire the Garage vehicle detail screen so its two existing tiles
("Reportes" and "Historial de Servicio") navigate to these routes.
Do NOT delete or replace the legacy `ReportScreen.kt` — it remains
the home of the 4-line `ReportIntegrityCard` addition from commit
`e1076723`, and Phase 1 explicitly preserves it.

### Step 4 — Wire Hook 2 (MechanicService completion)

Find the call site of
`MechanicServiceDao.updateMechanicStatusAndPrice(...)` inside
`ui/screens/MechanicServiceScreen.kt`. The status string is the last
argument. Immediately after a successful update where the new status
is `COMPLETED`, enqueue the prompt:

```kotlin
viewModelScope.launch {
    mechanicDao.updateMechanicStatusAndPrice(
        requestId = requestId,
        status = "COMPLETED",
        mechanicId = …,
        mechanicName = …,
        mechanicPhone = …,
        finalPrice = …,
    )
    // V2 spec Phase 7: completion must trigger a Post-Scan prompt.
    PostScanPrompt.request(requestId)
}
```

Then in the same screen (or its host), observe the channel:

```kotlin
LaunchedEffect(Unit) {
    val consumed = PostScanPrompt.consume(currentRequestId) ?: return@LaunchedEffect
    navController.navigate("inspection_session/${consumed.mechanicServiceId}")
}
```

Do not gate the prompt behind a confirmation dialog. The spec rule
is "no permitir marcar reparación completa sin post-scan"; the
prompt is the enforcement mechanism. The operator can dismiss it,
which fires `PostScanPrompt.Dismissed` and the report is not created.

### Step 5 — Wire Hook 3 (Remote verifier probe)

Only if Supabase verifier endpoint is reachable. Skip if shipping a
local-only round. Pattern is in the Hook 3 section above.

### Step 6 — End-to-end smoke test on device or emulator

Run the V2 acceptance criterion (Jor's hard test) manually:

```
Setup:
  - Hyundai Accent Verna 2005 selected as active vehicle.
  - OBD adapter physically disconnected (or paired to a vehicle that
    is not running).

Steps:
  1. Open Garage → Hyundai → Reportes.
     Expected: InspectionSessionScreen opens.
     Header shows orange "OBD no disponible. Reporte basado en
     datos manuales/offline."
  2. Tap the Pre-Scan chip.
  3. Enter DTCs: "P0230, P1709".
  4. Enter signerName: "Test Operador".
  5. Tap "Firmar y generar reporte".
     Expected: progress indicator, then SignedReportPanel appears
     showing the integrity hash and the ReportIntegrityCard with
     "MATCH ✓ byte-exact with TS".
  6. Open the generated PDF (use the PDF URI from the report row or
     the file at app's external files dir).
     Expected: 6 pages, page 1 has a ZXing-rendered QR, page 4 shows
     "Snapshot OBD no disponible. Reporte basado en datos
     manuales/offline.", every page footer reads
     "Verified by Elysium Vanguard · <hash>".
  7. Open Garage → Hyundai → Historial de Servicio.
     Expected: timeline shows the just-signed Pre-Scan row with its
     hash and the chain verifier banner reports OK.
  8. Scan the QR with a fresh APK install on a different device that
     has no local copy of the report.
     Expected: VerifyResult.Invalid with reason "No se encontró el
     reporte … en este dispositivo ni confirmación remota."
     If Hook 3 is wired: VerifyResult.ValidRemote.
  9. Re-install on the original device. Re-scan the same QR.
     Expected: VerifyResult.ValidLocal.
```

If any of these fail, the integration is not done. Fix and re-run.

---

## Anti-goals (do NOT do these)

The spec and the spec anti-goals apply. Reinforcing the load-bearing
ones for this integration round:

- **Do NOT delete or refactor the legacy `ReportScreen.kt`.** The
  4-line `ReportIntegrityCard` addition from commit `e1076723` is the
  only currently-shipped UI surface for the parity demo. Both screens
  coexist.
- **Do NOT re-implement `HashEngine` / `DraftReport` / `ReportHashingService`**
  / `CertifiedReportRepository` / `EvidenceCompiler`. They are the
  parity-critical surfaces. Extension only via Hilt rebind, never
  by parallel implementation.
- **Do NOT mark a `compatibility = EXACT` decision anywhere in this
  round.** It is a Parts Marketplace concern, not a Reports one. If
  the integrating AI sees `EXACT` strings while wiring Phase 5/7,
  stop and re-read `docs/parts-marketplace/V2-TECHNICAL-MARKETPLACE.md`.
- **Do NOT change `integrityHash` on a SIGNED report.** The
  `CertifiedReportDao.updateStatus` and the SQL trigger
  `trg_certified_reports_no_silent_mutation` both enforce this.
  Bypassing them is a hard break of the project principle.
- **Do NOT skip the QrPayload wire-format check.** Any future field
  addition must bump `v1|` to `v2|` and keep the verifier backward-
  compatible. The contract test
  `qr payload does not leak vin or plate` in
  `ReportVerifierTest.kt` pins this.
- **Do NOT push to `main` without running the cross-runtime parity
  harness first.**

```bash
bash tests/parity/ci-verify.sh
```

If this is green, the TS ≡ Kotlin contract is intact. If not, fix
the parity drift before merging anything.

---

## Cross-runtime parity — non-negotiable

The whole point of the V2 reports pipeline is that a report signed in
the web (TypeScript) can be verified in the APK (Kotlin), and vice
versa. The byte-exact hash chain is the foundation of that promise.

The golden value is pinned in three places that MUST stay in sync:

1. `core/reports/HashEngine.kt` — `DraftReport.canonicalReportString`
   (the canonical serializer).
2. `lib/reports/hash.ts` — TypeScript mirror.
3. `tests/parity/fixtures/snapshot-p0230.json` — `expectedHash =
   "71b393aeb4ddbb23dc4fdeb3720450a91734ebf567a0698620b273f4b545072e"`.

The reference test
`ReportHashingServiceTest.p0230 parity demo reproduces TS golden hash byte-exact`
enforces this in the Kotlin side. The CI verifier enforces it
end-to-end.

---

## Files touched by the integration

If you follow the order above, you will touch:

| File | Reason | Risk |
|---|---|---|
| `app/src/main/kotlin/.../MainActivity.kt` (or wherever the NavHost lives) | Add 2 routes | Low |
| `app/src/main/kotlin/.../ui/screens/MechanicServiceScreen.kt` | Wire `PostScanPrompt.request(...)` at COMPLETED | Low — additive |
| `app/src/main/kotlin/.../ui/screens/GarageScreen.kt` (or vehicle detail) | Wire 2 navigation tiles | Low — additive |
| `app/src/main/kotlin/.../di/AppModule.kt` (optional) | Add `provideReportVerifier` for Hook 3 | Low — additive |

You should NOT need to touch:

- `core/reports/HashEngine.kt`
- `core/reports/ReportHashingService.kt`
- `core/reports/ReportIntegrityCard.kt`
- `core/reports/QrPayload.kt`
- `core/reports/ReportVerifier.kt`
- `data/local/CertifiedReportRepository.kt`
- `data/local/MeetDatabase.kt`
- `data/local/dao/CertifiedReportsDao.kt`
- `data/local/ReportMappers.kt`
- `core/export/CertifiedReportPdfRenderer.kt`
- `core/reports/PostScanPrompt.kt`
- `core/reports/ReportsOutboxBridge.kt`
- `ui/screens/InspectionSessionScreen.kt`
- `ui/screens/VehicleHistoryScreen.kt`
- All 5 entity files in `data/local/entities/`
- All 5 DAO interfaces in `data/local/dao/CertifiedReportsDao.kt`
- All migration code in `di/AppModule.kt` (MIGRATION_41_42)

If you find yourself wanting to modify any of those, stop and re-read
the spec — almost certainly there is a hook you missed instead.

---

## Reference: complete file inventory of the 3 commits

```
android/app/src/main/kotlin/com/elysium369/meet/core/reports/EvidenceType.kt
android/app/src/main/kotlin/com/elysium369/meet/core/reports/ReportStatus.kt
android/app/src/main/kotlin/com/elysium369/meet/core/reports/ReportType.kt
android/app/src/main/kotlin/com/elysium369/meet/core/reports/QrPayload.kt
android/app/src/main/kotlin/com/elysium369/meet/core/reports/ReportVerifier.kt
android/app/src/main/kotlin/com/elysium369/meet/core/reports/PostScanPrompt.kt
android/app/src/main/kotlin/com/elysium369/meet/core/reports/ReportsOutboxBridge.kt
android/app/src/main/kotlin/com/elysium369/meet/core/export/CertifiedReportPdfRenderer.kt
android/app/src/main/kotlin/com/elysium369/meet/data/local/CertifiedReportRepository.kt
android/app/src/main/kotlin/com/elysium369/meet/data/local/ReportMappers.kt
android/app/src/main/kotlin/com/elysium369/meet/data/local/dao/CertifiedReportsDao.kt
android/app/src/main/kotlin/com/elysium369/meet/data/local/entities/CertifiedReportEntity.kt
android/app/src/main/kotlin/com/elysium369/meet/data/local/entities/DiagnosticSnapshotEntity.kt
android/app/src/main/kotlin/com/elysium369/meet/data/local/entities/RepairActionEntity.kt
android/app/src/main/kotlin/com/elysium369/meet/data/local/entities/ReportEvidenceEntity.kt
android/app/src/main/kotlin/com/elysium369/meet/data/local/entities/ReportSignatureEntity.kt
android/app/src/main/kotlin/com/elysium369/meet/ui/screens/InspectionSessionScreen.kt
android/app/src/main/kotlin/com/elysium369/meet/ui/screens/VehicleHistoryScreen.kt
android/app/src/main/kotlin/com/elysium369/meet/data/local/MeetDatabase.kt            (modified: entities + DAOs + version 41→42)
android/app/src/main/kotlin/com/elysium369/meet/di/AppModule.kt                       (modified: MIGRATION_41_42)
android/app/src/test/kotlin/com/elysium369/meet/data/local/CertifiedReportRepositoryTest.kt
android/app/src/test/kotlin/com/elysium369/meet/core/reports/ReportVerifierTest.kt
```

Total: 19 new files, 2 modified files, 19 tests green.

---

## Spec reference

The complete spec lives at `docs/reports/V2-CERTIFIED-PDF-AND-HISTORY.md`
(commit `896eea09`). Read it before any non-trivial change to the
code above. The product principle is at `docs/PRODUCT_VISION.md` and
the cross-agent rules are at `AGENTS.md`. All three are the source of
truth; this integration order is just the bridge between them and the
shipping APK.