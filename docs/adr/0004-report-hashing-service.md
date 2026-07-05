# ADR 0004 — Report Hashing Service: Hilt wrapper + Compose EntryPoint

**Status:** Accepted
**Date:** 2026-07-04
**Deciders:** Jor, Mavis
**Supersedes:** —

---

## Context

`core/reports/HashEngine.kt` and `diagnostic/DiagnosticSnapshot.kt` are
the two parity-pure hashing primitives of MEET:

| Primitive | Purpose | Parity with |
| --- | --- | --- |
| `HashEngine.hashReport(DraftReport)` | Per-vehicle signed-report chain | `lib/reports/hash.ts#hashReport` |
| `DiagnosticSnapshot.hashSha256` (computed in init) | Per-session snapshot chain | `lib/reports/hash.ts#canonicalSnapshotString` |

Both are tested and green against the P0230 golden fixture. Both are
**plain Kotlin `object`s** — pure, stateless, and not bound to any DI
graph. That was deliberate: it keeps them deterministic and easy to
fuzz. But it left a gap:

1. **No way for the UI to consume them.** Compose screens can't inject
   `object HashEngine` and `DiagnosticSnapshot.computeHash` is private.
2. **Cross-runtime parity was only visible to test runs**, not to a
   human reviewer holding the device.
3. **No demo path for a non-developer to see "TS ≡ Kotlin".**

The other side of the constraint: the build is large and already
touches a lot of files. Any new wiring has to be **additive and
local** — no rewrites of `ObdViewModel`, `ReportGenerator`, the
navgraph, or any file the parallel Codex agent is currently working on.

## Decision

Introduce a thin injectable wrapper plus a leaf Compose composable:

1. **`core/reports/ReportHashingService.kt`** — `@Singleton @Inject
   class` that exposes three methods:
   - `p0230ParityDemo(): ParityResult` — builds the reference snapshot
     and compares against the TS golden hash.
   - `signDraftReport(...)` — wraps `HashEngine.hashReport` with a
     structured `ReportSignResult` that also returns the canonical
     string for inspection.
   - `demoReportChainOk / demoReportChainBroken` — exercise
     `HashEngine.verifyChain` so the UI can render the chain demo.

   It also holds the immutable `p0230ExpectedHash` so the value is
   defined in exactly one Kotlin file (and matches
   `tests/parity/fixtures/snapshot-p0230.json#expectedHash`).

2. **`di/ReportsModule.kt`** — explicit `@Module` with two
   `@Provides @Singleton` bindings (HashEngine + ReportHashingService)
   and a `@EntryPoint` for Compose-side lookup. The explicit module is
   intentional: it keeps the binding grep-able and easy to swap for a
   fake during Espresso UI tests.

3. **`core/reports/ReportIntegrityCard.kt`** — a leaf composable that
   drops into any screen. Uses `EntryPointAccessors.fromApplication`
   through `rememberReportHashingService()`, so callers don't need to
   thread a `@HiltViewModel` for this single concern. The card
   renders:
   - MATCH / MISMATCH badge.
   - Expected (TS) vs Computed (APK) hashes, side by side.
   - Canonical preview (the pipe-delimited string before SHA-256).
   - Chain integrity demo (OK vs BROKEN).

4. **`ui/screens/ReportScreen.kt`** — 4-line addition: one import
   pair + one `ReportIntegrityCard` call after the success card.

5. **`core/reports/HashEngineParityTest.kt`** — the missing piece
   `tests/parity/ci-verify.sh` was always looking for. Reads the same
   fixture, builds the same snapshot, writes
   `app/build/reports/parity/snapshot-p0230.txt` in the exact format
   the bash wrapper expects (including trailing newline). This closes
   the loop the architecture doc at `docs/architecture/CROSS-RUNTIME-PARITY.md`
   described but never finished.

## Alternatives considered

- **Inject `HashEngine` directly into `ObdViewModel`** and surface the
  hash through a `StateFlow`. Rejected: would require touching the
  ViewModel that the parallel Codex agent is currently editing, and
  it leaks a hashing concern into a domain ViewModel that has no
  business with it. The card is a self-contained UI feature, not a
  data-flow feature.
- **Put the card on `AiDiagnosticScreen.kt`** instead of
  `ReportScreen.kt`. Rejected: `AiDiagnosticScreen` requires a live
  OBD link for the parity check to feel meaningful, while
  `ReportScreen` is exactly where the user just generated a report
  and is in the right headspace to learn "this hash is byte-exact
  with the web side".
- **Compose-only service via `CompositionLocalProvider`**. Rejected:
  adds a global provider pass-through for one card. EntryPoint lookup
  is one line per consumer and the service is `@Singleton` so the cost
  is paid once.

## Consequences

Positive:
- A reviewer holding the device can verify cross-runtime parity in
  under 30 seconds: Home → Reportes → GENERAR → scroll to the
  integrity card → read MATCH.
- `tests/parity/ci-verify.sh` now exits 0 (it was broken before this
  ADR because `HashEngineParityTest` didn't exist).
- Pattern is reusable: any future pure `object` can be lifted into
  the Hilt graph with a 6-line module + a 5-line wrapper. Three more
  services queued behind this pattern (`CompatibilityEngine`,
  `PartSuggestionEngine`, `QuoteValidator`).

Negative / accepted:
- One more file (`ReportsModule.kt`) to keep in sync with the
  Service. Mitigated: the @Provides are explicit so a `git diff` on
  the module tells the reviewer exactly what is bound.
- The integrity card runs the hash on `Dispatchers.Default` to keep
  the UI thread snappy. On a slow device the very first recompose
  can show a one-frame spinner. Acceptable trade-off for not blocking
  the main thread.

## Verification

- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (2 min incremental).
- `./gradlew :app:testDebugUnitTest` → 290 tests, 0 failures.
- `npx tsx tests/parity/hash-parity.ts tests/parity/fixtures/snapshot-p0230.json` → [OK].
- `bash tests/parity/ci-verify.sh` → "TS and Kotlin produced identical output. Cross-runtime parity OK."
- Device flow on VER-N49 (Android 16 API 36):
  Home → Reportes → GENERAR REPORTE → success card shows
  "INTEGRIDAD DE REPORTES — TS ≡ Kotlin" with MATCH badge and the
  two hashes equal byte-for-byte.

## Refs

- `tests/parity/fixtures/snapshot-p0230.json`
- `android/app/src/main/kotlin/com/elysium369/meet/core/reports/HashEngine.kt`
- `android/app/src/main/kotlin/com/elysium369/meet/diagnostic/DiagnosticSnapshot.kt`
- `android/app/src/main/kotlin/com/elysium369/meet/di/ReportsModule.kt`
- `android/app/src/main/kotlin/com/elysium369/meet/core/reports/ReportHashingService.kt`
- `android/app/src/main/kotlin/com/elysium369/meet/core/reports/ReportIntegrityCard.kt`
- `android/app/src/test/kotlin/com/elysium369/meet/core/reports/HashEngineParityTest.kt`
- `docs/architecture/CROSS-RUNTIME-PARITY.md`
