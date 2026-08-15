# PR 4 — Reports Foundations: hash chain + data model

**Branch:** `feature/reports-foundations-pr1`
**Scope:** Database schema, hash primitive, builders. **No UI yet.** PR 5 wires the PDF + wizard.

---

## Why this PR first

The certified-report pipeline's value depends on three things being
right:

1. The data model (5 tables) is shaped for the spec.
2. The hash chain is reproducible across web (TS) and Android (Kotlin).
3. The validators + trigger prevent the spec's anti-fraud rules from
   regressing.

None of this is visible to the user, but every later PR depends on it.
That's why it ships first, alone, before any UI work.

---

## What lands

### 1. Supabase migration
File: `supabase/migrations/20260704001000_reports_foundations.sql`

* 4 enums: `report_type`, `report_status`, `evidence_type`,
  `diagnostic_provenance`.
* 5 tables: `certified_reports`, `report_evidence`,
  `diagnostic_snapshots`, `repair_actions`, `report_signatures`.
* Indexes per spec: by vehicle, by user, by status, by type, plus
  the chain index on `(vehicleId, integrityHash)`.
* RLS: every table is owner-gated. A user only sees the report they
  own and the evidence/snapshot/repair/signature attached to it.
* **Trigger** `trg_certified_reports_no_silent_mutation`: blocks any
  UPDATE on a SIGNED report that doesn't change the hash. The only
  allowed transitions from SIGNED are VOIDED / SHARED / EXPORTED,
  and they all require a hash change.

### 2. Shared TypeScript types
File: `lib/reports/types.ts`

* Enumerations as `as const` arrays + derived unions.
* `CertifiedReport`, `ReportEvidence`, `DiagnosticSnapshot`,
  `RepairAction`, `ReportSignature`.
* `DraftReportInput`, `PeritajeChecklist`, `ReportPrivacyOptions`.
* Helper: `summarizeDtcs(before, after) -> ReportDtcSummary` for
  Post-Scan comparators.
* Helper: `reportConfidence(dtcs, snapshot, peritaje) -> CompatibilityConfidence`
  for the header pill.
* The `DiagnosticSnapshot` shape mirrors the Kotlin
  `DiagnosticSnapshot.kt` field-for-field. If the Android side
  evolves, the web side follows in the same PR.

### 3. Hash primitive
File: `lib/reports/hash.ts`

* `sha256Hex(input)` — async, Web Crypto based, works in browser and
  Node 20+. Returns 64-char hex.
* `canonicalize(value)` — deterministic sorted-keys, no-whitespace,
  typed-explicit JSON. Same byte output regardless of engine.
* `canonicalSnapshotString(snap)` — `|`-separated concatenation of
  the snapshot's content fields, mirroring the Kotlin
  `computeHash(...)`.
* `hashSnapshot`, `hashEvidence`, `hashRepairAction`, `hashPeritaje`,
  `hashSignature` — typed wrappers.
* `verifyChain(reports)` — sorts by `generatedAt`, returns
  `{ ok, brokenAt }`. This is the per-vehicle chain verifier.
* `hashDeviceId(deviceId)` — pre-hash helper; production uses HKDF
  with a salt, but the function shape is the same.

### 4. Builders
File: `lib/reports/generate.ts`

* `buildPreScanDraft` / `buildPostScanDraft` /
  `buildRepairEvidenceDraft` / `buildPeritajeDraft` / `buildDvirDraft`.
  Each builder emits a `DraftReportInput` with the right evidence
  shape (a pre-scan without OBD adapter still emits a `REPAIR_NOTE`
  evidence explaining the absence, per the spec's "no invented data"
  rule).
* `finalizeDraft({draft, previousHash}) -> FinalizedReport`: computes
  all hashes, assigns ids, produces a DRAFT report with a frozen
  `integrityHash`.
* `applySignature({...}) -> SignedReport`: moves the report to
  `SIGNED`, locks it, returns a `ReportSignature` whose
  `integrityHash` covers (reportHash, signerName, signerRole,
  signedAt, deviceIdHash).
* `validateDraftForSign` surfaces BLOCK / WARN issues.
* `applyPrivacy` redacts VIN/plate based on toggles.

### 5. Tests
* `lib/reports/__tests__/hash.test.ts` (24 tests).
* `lib/reports/__tests__/generate.test.ts` (17 tests).
* Total: **41 tests, all green**.

Pin:
* Determinism: same input → same hash.
* Independence: reordering DTCs / readiness / peritaje alerts / section
  scores does NOT change the hash.
* Chain: `verifyChain` accepts genesis + valid links, rejects
  mismatches, sorts by `generatedAt`.
* Spec rules: pre-scan without OBD emits a REPAIR_NOTE evidence;
  post-scan without post-snapshot surfaces a "no se puede afirmar
  reparado" note; peritaje without checklist is BLOCK.

### 6. Vitest
Already configured in PR 1's `vitest.config.ts`. The reports tests
run alongside the parts tests with `npm test`.

### 7. Docs
* `docs/adr/0003-reports-pdf-certificados.md` — this ADR.
* `docs/reports/PR4-foundations.md` — this file.

---

## What does NOT land in this PR

* No UI (no wizard, no report screen, no timeline). PR 5.
* No PDF generation. PR 5.
* No QR code. PR 5.
* No Supabase writes. PR 5.
* No offline persistence layer (IndexedDB / localStorage).
  PR 7 if needed.
* No Android changes. The Kotlin `DiagnosticSnapshot.kt` is the
  reference; the web side mirrors its contract.

---

## Acceptance criteria (the spec, restated)

> Hyundai Accent Verna 2005 with P0230 + P1709

The `DiagnosticSnapshot` shape accepts those DTCs in `dtcsActive`. Tests
exercise it.

> el reporte tiene hash SHA-256

`hashReportDraft` returns a 64-char hex. Tested.

> el reporte tiene QR

QR generation lands in PR 5; PR 4 only establishes the hash + the
chain that the QR encodes.

> el reporte queda en historial del vehículo

`certified_reports` is keyed by `vehicleId`. The timeline UI lands in
PR 6.

> un reporte firmado no puede editarse sin invalidarse

`trg_certified_reports_no_silent_mutation` raises an exception on any
silent mutation of a SIGNED row.

> el sistema no inventa datos faltantes

`buildPreScanDraft` without an OBD snapshot emits a REPAIR_NOTE
evidence describing the absence. The report never says "Snapshot OBD
no disponible" *and* "bomba dañada" simultaneously.

---

## Risk register

| Risk | Mitigation |
|---|---|
| Web/TS hash diverges from Android Kotlin hash | Both use the same `canonicalSnapshotString` shape (sorted keys, `|` separator, no whitespace). PR 5 will add a cross-language test in CI. |
| `crypto.subtle` not available in old browsers | Web Crypto is supported in every browser since 2017. A server-side fallback for legacy environments is a future PR. |
| Trigger too strict for app bugs | The exception is loud and named (`A signed report cannot be mutated without re-issuing a draft...`). Easy to find in tests. |
| Local-id collisions in `localId` | The id is a prefix + timestamp + counter. Persistence layer replaces with UUIDs at the boundary. The id is for local references only. |
