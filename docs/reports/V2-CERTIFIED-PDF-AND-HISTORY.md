# Reports PDF Certificados V2 + Vehicle Technical History

**Branch:** TBD (start from `v0.6.0-report-hashing` tag)
**Owner:** Codex / ChatGPT agent
**Status:** Draft (waiting for Codex green-light)
**Supersedes:** `docs/reports/PR4-foundations.md`, `docs/reports/PR8-sync-verifier.md` (extend, don't replace)
**Related:** ADR 0004 (Hilt wrapper + Compose EntryPoint pattern), `docs/parts-marketplace/V2-TECHNICAL-MARKETPLACE.md` (next-in-line after this round)

---

## Why this section before any other

Jor's words, not mine:

> "Un scanner bonito impresiona; un reporte certificado con evidencia,
> antes/después, DTCs, fotos, firma, hash, QR y trazabilidad cobra
> dinero."

This is the section that converts everything else in MEET into a
sellable product. Without a certified report, the scanner is a toy.
With it, MEET is a technical-evidence platform: pre-purchase peritaje,
post-repair validation, fleet DVIR, supplier-buyer dispute resolution,
insurance claim support — all the same artifact type, all
hash-verifiable.

The high-level flow we want:

```
Inspection Session → Evidence Capture → Diagnostic Snapshot → Repair Actions
   → Post-Validation → Certified PDF → Vehicle History → Share/Verify
```

---

## What's already in the repo (do NOT re-implement)

| Surface | Path | Status |
|---|---|---|
| `HashEngine` (pure) | `android/.../core/reports/HashEngine.kt` | Shipped (PR-7, PR-8). Provides `hashReport(DraftReport)`, `verifyChain(...)`, `sha256Hex(...)`, `hashDeviceId(...)`. The byte-exact parity with TS lives here. |
| `ReportHashingService` (Hilt @Singleton) | `android/.../core/reports/ReportHashingService.kt` | Shipped 2026-07-04 (`e1076723`). `p0230ParityDemo()`, `signDraftReport(...)`, `demoReportChainOk`, `demoReportChainBroken`. **Use this as the signing core** — it already pins the golden hash `71b393aeb4ddbb23dc4fdeb3720450a91734ebf567a0698620b273f4b545072e`. |
| `ReportIntegrityCard` | `android/.../core/reports/ReportIntegrityCard.kt` | Shipped 2026-07-04 (`e1076723`). Compose leaf composable showing MATCH/MISMATCH + hashes + canonical preview + chain demo. **Drop this into the new Report detail screen** as the "verifiable" panel. |
| `ReportsModule` + `ReportsEntryPoint` | `android/.../di/ReportsModule.kt` | Shipped 2026-07-04 (`e1076723`). Hilt @Provides + Compose EntryPoint. Mirror for any new reports-side services. |
| `HashEngineParityTest` | `android/.../test/.../HashEngineParityTest.kt` | Shipped 2026-07-04 (`91662aa2`). Writes `app/build/reports/parity/snapshot-p0230.txt` so `ci-verify.sh` is green. If V2 adds byte-exact contracts (e.g. signed PDF hash), copy this style. |
| `DiagnosticSnapshot` | `android/.../diagnostic/DiagnosticSnapshot.kt` | Shipped. **Read-only** in the existing form. V2 should NOT mutate this; instead, extend it with the `report_id` foreign key OR wrap it in a new `ReportLinkedSnapshot` (see Phase 1 below). |
| `EvidenceCompiler` | `core/blackbox/EvidenceCompiler.kt` | Exists, used elsewhere in the codebase. Reuse for evidence hashing + chain-of-custody, don't roll a parallel one. |
| `ReportScreen.kt` (4-line ReportIntegrityCard integration) | `android/.../ui/screens/ReportScreen.kt` | Shipped 2026-07-04. **The existing UI is the Wizard shell; V2 turns it into the full "Inspection Session" surface.** Don't revert the 4-line addition. |
| `ReportGenerator` (PDF generation engine) | `android/.../core/export/ReportGenerator.kt` | Shipped. Reuse for the PDF layer. V2 adds the multi-page layout on top. |
| Cross-runtime parity harness | `tests/parity/ci-verify.sh` + `lib/reports/hash.ts` | Shipped. GREEN end-to-end. Any new byte-exact contract MUST go through this harness. |
| Existing PDF theme infra | `core/print/BluetoothPrinterManager`, `core/print/PrintReportData` | Reuse for thermal printers + the BT receipt variant of the report. |

Total: **~1,200 lines of reports-side code already ship-ready.** Mirror, don't duplicate.

---

## What to build (priority order)

### Phase 1 — Data model

The user-supplied spec lists 6 entities. Map each to the existing layer:

| Spec entity | Where it should land | Notes |
|---|---|---|
| `CertifiedReport` | New Room table + Supabase mirror | Aggregate root. Status enum: DRAFT, READY, SIGNED, EXPORTED, SHARED, VOIDED. |
| `ReportEvidence` | New Room table | One report has many evidences. Type: PHOTO, VIDEO, OBD_SNAPSHOT, FREEZE_FRAME, SENSOR_GRAPH, SIGNATURE, MEASUREMENT, PART_INVOICE, REPAIR_NOTE. |
| `DiagnosticSnapshot` | **Reuse** `diagnostic/DiagnosticSnapshot.kt` | Add nullable `reportId` field OR wrap with a `ReportLinkedSnapshot` data class that carries the reportId + provenance metadata. Do NOT mutate the existing class's hash contract. |
| `RepairAction` | New Room table | action_type, component, dtc_related, part_used, supplier, mechanic, cost, currency, warranty_days. |
| `ReportSignature` | New Room table | signer_name, signer_role, signature_image_uri, signed_at, device_id_hash, integrity_hash. |
| `ReportType` | New enum | PRE_SCAN_REPORT, POST_SCAN_REPORT, REPAIR_EVIDENCE_REPORT, PRE_PURCHASE_INSPECTION_REPORT, DVIR_REPORT. |

Migration filename: `supabase/migrations/2026XXXXXX_certified_reports_v2.sql`

**RLS:**
- Customers see their own reports + reports shared with them.
- Mechanics see reports assigned to them.
- Repuesteras see only the `SupplierQuote` link inside a report, never the full report.
- Admins see everything, including VOIDED.
- A report in `VOIDED` status must remain queryable for audit (no hard delete).

### Phase 2 — Hash chain per vehicle

Reuse `HashEngine.hashReport(DraftReport)` as the signing primitive. The
chain rule:

```
integrityHash(N) = SHA256(canonicalString(report_N) || previousHash(N-1))
```

Where `previousHash(N-1) = integrityHash(N-1)` for the same `vehicleId`.

`ReportHashingService.signDraftReport(...)` already implements this for
the `DraftReport` shape. V2 should:

1. Extend `DraftReport` (or create a `CertifiedReportDraft` carrier) so
   it carries the 6 entity types above.
2. Add a `reportChain()` helper that walks a vehicle's reports in
   `generated_at` order, asserts `previousHash` linkage, returns
   `HashEngine.ChainResult`.
3. Call `verifyChain(...)` on every "open report" pull so tampering
   from any device (local or remote) breaks the chain visibly.

UI rendering: drop the existing `ReportIntegrityCard` into a new
`CertifiedReportDetailScreen` and pass it the actual report's hash +
previous hash. The card already supports the demo chain; point it at
real data.

### Phase 3 — QR + verifier

Each exported PDF carries a QR with minimum payload:

```json
{
  "report_id": "...",
  "integrity_hash": "...",
  "vehicle_id": "...",
  "generated_at": 1700000000,
  "report_type": "PRE_SCAN_REPORT",
  "verifier_url": "https://.../verify/<id>"
}
```

**Privacy rule:** never put full VIN / plate / phone in the QR. The
payload above is enough for verification without leaking PII.

If online: QR points to a Supabase endpoint that returns
`{ valid: true, report_type, generated_at }` based on `integrity_hash`.
If offline: the QR is a self-contained signed payload and the
in-app verifier uses `HashEngine` to recompute and compare.

Suggested new module:
- `core/reports/QrPayload.kt` — encode/decode.
- `core/reports/ReportVerifier.kt` — verify against local DB or
  remote endpoint.

### Phase 4 — Inspection Session UI (replace current `ReportScreen.kt` body)

The 4-line addition from `e1076723` (the `ReportIntegrityCard` after
the success card) MUST stay. Layer the new surface around it.

Header:
- Active vehicle, OBD status, VIN (with redact toggle), odometer, last
  report date + type.

Type selector (chip row):
- Pre-Scan
- Post-Scan
- Reparación
- Peritaje
- DVIR

Each type has its own sub-flow:

#### Pre-Scan
1. Capture OBD snapshot (auto) — `DiagnosticSnapshot.computeHash()`.
2. Manual overrides: add DTC, add freeze frame, add photo, add notes.
3. Sign.
4. Generate PDF.
5. Save to history.
6. Share.

#### Post-Scan
1. Pull the last Pre-Scan for the same vehicle.
2. Run live snapshot.
3. `BeforeAfterComparator.compare(before, after)` (already exists at
   `diagnostic/BeforeAfterComparator.kt` — reuse it).
4. Show cleared vs persistent DTCs.
5. Add repair evidence (RepairAction rows + photos + supplier quotes).
6. Sign, PDF, history.

#### Reparación (Repair Evidence)
1. Pick an existing open Post-Scan OR start fresh.
2. Add RepairActions (component, description, part, cost, warranty).
3. Required: at least one photo before, one photo after, one signature.
4. Sign, PDF, history.

#### Peritaje (Pre-Purchase)
Reuse the existing `MeetPerito` checklist engine
(`core/perito/MeetPerito.kt` if it exists, otherwise search for the
peritaje infrastructure). Score 0–100. Verdict:
APROBADO / APROBADO_CON_OBSERVACIONES / NEGOCIAR / NO_RECOMENDADO /
RIESGO_ALTO. PDF template renders the peritaje layout with score gauge.

#### DVIR (Fleet)
1. Operator selects vehicle + enters shift.
2. Tick checklist (brakes, lights, tires, fluids, battery).
3. Optional photo of damage.
4. Sign.
5. Save + sync to fleet supervisor.

### Phase 5 — Vehicle History

`Garage → Vehicle → Historial de Servicio` becomes the canonical
timeline. Render as a `LazyColumn` of events:

```
2026-07-04   POST_SCAN_REPORT    [score 87]  [hash a4f2…]  [open PDF]
2026-06-21   PRE_SCAN_REPORT     [score 72]  [hash 71b3…]  [open PDF]
2026-06-21   REPAIR_ACTION       [P0230 → relay replaced] [warranty 90d]
2026-06-15   DVIR_REPORT         [OK]      [hash 32c1…]
2026-06-01   MAINTENANCE         [oil change] [vendor Shell]
…
```

Each row is clickable → opens the underlying `CertifiedReport`,
`RepairAction`, or `MaintenanceLog`.

Add a "Verify this report" action on every row that re-runs
`HashEngine.verifyChain(...)` and shows ✓ / ✗ with the broken-at id
if the chain has been tampered.

### Phase 6 — PDF generation

Don't redo from scratch. Extend `ReportGenerator.generatePdfReport(...)`:

- 6-page layout as specified by Jor:
  - p1 cover (logo, type, vehicle, VIN/plate/odo, datetime, score, QR)
  - p2 executive summary (DTCs, severity, recommendation)
  - p3 per-DTC detail (description, causes, tests, components)
  - p4 telemetry (PID tables, freeze frame, sensor graphs)
  - p5 repair actions (parts, costs, warranty, mechanic)
  - p6 evidence (photos, signatures, disclaimer, hash footer)
- Theme selection already exists (ELYSIUM_CYAN, CARBON_RED, etc.) —
  don't break it. Add a `LEGAL_FORENSIC` theme for fleet/insurance
  reports if needed.
- QR rendered via ZXing (or any local lib) — embedded as PNG.
- Footer on every page: `Verified by Elysium Vanguard · <integrity_hash>`
  in 8pt mono.

### Phase 7 — Integrations

- **Mecánicos**: when a `MechanicService` order transitions to
  `COMPLETED`, the system must auto-prompt "Create Post-Scan report".
  Reject completion without `RepairAction` rows.
- **Repuestos**: when a `SupplierQuote` is accepted (`quote_status =
  ACCEPTED`), it must be linked into the relevant `CertifiedReport`.
  Show on the report PDF: "Pieza usada en reparación · <brand> ·
  <condition> · <warranty_days>d garantía · No implica compatibilidad
  exacta salvo confirmación por VIN/OEM/prueba física."
- **Historial**: every `MaintenanceLog` / `RepairAction` shows in the
  timeline with hash-link to its source report.

### Phase 8 — Privacy + offline

- Redact toggles per report: hide VIN partially, hide plate, hide
  location. Apply at PDF render time, NOT at DB write time (so the
  hash always covers the full payload; only the rendered PDF respects
  the toggle).
- Offline-first: report creation, signing, PDF generation, hashing,
  history insertion all work without network. Network is only required
  for:
  - Syncing the report to Supabase.
  - Generating a `verifier_url`.
  - Backing up the PDF to Google Drive.
- Sync queue: reuse the `SupabaseVanguardOutboxDispatcher` pattern
  (see `core/vanguard/SupabaseVanguardOutboxDispatcher.kt`).
- Hard rule: **never lose a signed report**. If sync fails, the report
  stays in the outbox and retries on next connect.

---

## Anti-goals (do NOT do these)

- **Do not re-implement `HashEngine`** — extend it via
  `ReportHashingService`.
- **Do not break the existing 4-line `ReportIntegrityCard` integration**
  on `ReportScreen.kt` (commit `e1076723`). It's the only UI surface
  today that demonstrates cross-runtime parity end-to-end.
- **Do not allow silent edits on a signed report.** Either create a
  new version with a chained hash, or transition to `VOIDED` and
  start fresh.
- **Do not invent data.** "OBD no disponible" must appear verbatim
  when there's no live snapshot. "Dato no capturado" / "Pendiente
  de validación" / "Confianza limitada" / "Requiere prueba física"
  are the four allowed honest phrases.
- **Do not mark compatibility `EXACT`** without VIN + OEM evidence
  (this rule is shared with the Parts Marketplace V2 spec — keep
  them consistent).
- **Do not skip the QR** even if Supabase is offline — generate the
  payload locally with the integrity hash, the verifier can still
  run in-app.
- **Do not put full VIN / plate / phone in the QR.** Only the 6-field
  minimal payload.

---

## Acceptance criterion (Jor's hard test)

```
Setup: Hyundai Accent Verna 2005, OBD disconnected, DTC P0230 in DB.

Steps:
1. Open Reportes → Pre-Scan.
2. App must show "Snapshot OBD no disponible. Reporte basado en
   datos manuales/offline." — NOT pretend to capture live data.
3. DTC P0230 appears in the report because it lives in the local DB.
4. PDF is generated, includes the DTC + the disclaimer.
5. PDF footer shows integrity_hash (SHA-256).
6. QR is generated and embedded.
7. Verifier (in-app, offline) confirms ✓ by recomputing the hash.
8. Report is saved in the vehicle's history timeline.
9. No sentence like "bomba dañada confirmada" appears anywhere.

If any of these fail, the build is not acceptable.
```

---

## Coordination with the other agent's WIP + my tonight's work

In-progress on `main` (uncommitted):

Other agent:
- `ui/ObdViewModel.kt`, `ui/screens/PartRequestScreen.kt`,
  `ui/screens/RepairNetworkScreen.kt`, `ui/screens/RideServiceScreen.kt`,
  `ui/screens/TowTruckServiceScreen.kt`,
  `ui/screens/MechanicServiceScreen.kt`,
  `test/.../SanitizeGpsAddressTest.kt`
- `core/parts/PartQuoteRanker.kt`, `core/parts/PartsMarketplaceContract.kt`
  and their tests.
- `core/services/` (new directory).

Me (Mavis, 2026-07-04 night):
- `core/reports/HashEngine.kt`, `core/reports/ReportHashingService.kt`,
  `core/reports/ReportIntegrityCard.kt`,
  `core/reports/HashEngineParityTest.kt`,
  `core/reports/ReportHashingServiceTest.kt`,
  `di/ReportsModule.kt`,
  `ui/screens/ReportScreen.kt` (4-line addition).

The skill `~/.mavis/skills/codex-mavis-sync/scripts/sync.sh --auto`
will merge both sides before the APK ships.

---

## Refs

- `docs/reports/PR4-foundations.md` (existing schemas)
- `docs/reports/PR8-sync-verifier.md` (existing offline queue + QR infra)
- `docs/parts-marketplace/V2-TECHNICAL-MARKETPLACE.md` (next round)
- `docs/adr/0004-report-hashing-service.md` (Hilt wrapper pattern)
- `docs/architecture/CROSS-RUNTIME-PARITY.md`
- `android/.../core/reports/*.kt` (existing engines — DO NOT duplicate)
- `android/.../di/ReportsModule.kt` (reference Hilt module)
- `android/.../diagnostic/BeforeAfterComparator.kt` (reuse for Post-Scan)
- `android/.../core/export/ReportGenerator.kt` (extend, don't redo)
- `android/.../core/vanguard/SupabaseVanguardOutboxDispatcher.kt` (outbox pattern)
- `tests/parity/ci-verify.sh` (any new byte-exact contract goes through here)
