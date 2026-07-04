# PR-8 — Reports sync + verifier + evidence_type extension

**Branch:** `feature/reports-sync-verifier`
**Scope:** Extend the `evidence_type` enum, ship the offline queue, add the QR verifier. No UI for the queue (PR-9).

---

## Why this PR

The certifier-report pipeline is the most sensitive surface in the
product. To make it commercially viable we need:

1. **Offline-first**: a customer can sign a report in a basement with
   no signal. The local persistence layer keeps the report alive
   until the network is back.
2. **Anti-tampering**: a printed PDF with a QR must be verifiable.
   Anyone with a phone should be able to point the camera at the
   QR and confirm "yes, the hash in the PDF matches the row in
   Supabase".
3. **Schema parity**: the Kotlin `EvidenceType` enum grew to 13
   values; the SQL side has 9. We're shipping the 10 new values
   (the 9 from the Kotlin enum minus the 3 that are already in
   the SQL) and aligning the TypeScript mirror at 19 total.

---

## What lands

### 1. SQL migration
File: `supabase/migrations/20260705000000_reports_sync_and_evidence_extend.sql`

* Extends `public.evidence_type` from 9 to 19 values:
  - BEFORE_PHOTO, AFTER_PHOTO, MULTIMETER_READING,
    FUEL_PRESSURE_READING, PART_REPLACED, RECEIPT,
    CUSTOMER_SIGNATURE, PROVIDER_NOTE, TEST_DRIVE_RESULT,
    PDF_REPORT.
* Idempotent: each `ADD VALUE` is guarded by a `pg_enum` check so
  re-applying the migration is safe.

### 2. Offline queue (pure)
File: `lib/reports/sync.ts`

* `enqueue(op)` adds a `SyncOp` to the localStorage-backed queue.
* `listQueue()` / `clearQueue()` / `markAttempt()` / `removeFromQueue()`.
* `subscribe(listener)` lets the UI react to queue changes.
* `backoffMs(attempts)` — exponential, capped at 5 minutes, with jitter.
* `dryRunFlush(items, transport, options)` — pure pipeline that
  counts success/failure without side effects. Used to test the
  retry / max-attempts logic in isolation.

### 3. Supabase transport
File: `lib/reports/api.ts`

* `sendOp(op)` writes a single queue item to Supabase, returning
  a typed result.
* `verifyReport(reportId, integrityHash)` — read-only verifier
  used by the QR scan flow. Returns whether the stored hash
  matches the one in the QR, plus the report's metadata for the UI.
* `flushQueue()` drains the queue, removing successes and bumping
  `attempts` / `lastError` on failures.

### 4. QR verifier UI
File: `components/ReportVerifier.tsx`

* Drop-in component.
* Accepts three input shapes:
  - `meet://verify?reportId=...&hash=...`
  - `https://.../?reportId=...&hash=...`
  - bare `reportId:hash` pair.
* Manual entry mode (paste the payload) is on by default.
* Renders four distinct phases: `verifying`, `ok`, `fail`, `miss`.
* Never declares "OK" unless the stored hash matches byte-for-byte.

### 5. Tests
File: `lib/reports/__tests__/sync.test.ts`

* 9 tests pinning: enqueue / remove, markAttempt, clearQueue,
  backoffMs exponential + cap, dryRunFlush success/failure counts,
  max-attempts cap.

---

## What does NOT land in this PR

* No UI for the queue itself (banner showing "3 reports pending
  sync"). Tracked for PR-9.
* No PDF generation. PR-5 of the Reports pipeline (separate).
* No Android changes. The verifier UI is web-only; the Android
  client can call `verifyReport` from its own screen when ready.

---

## Acceptance criteria (the spec, restated)

> un reporte firmado no puede editarse sin invalidarse

The trigger from PR-4 enforces this at the database. The queue in
this PR refuses to overwrite a SIGNED row — `sendOp` routes
mutations through a "VOIDED then re-inserted" path that the next
PR wires up.

> el sistema no inventa datos faltantes

The verifier reads the row that was signed, then compares the
stored hash with the one in the QR. A row that was never inserted
returns `miss` and never `ok`.

---

## Risk register

| Risk | Mitigation |
|---|---|
| Queue grows unbounded if offline for a long time | `attempts >= maxAttempts` skips the item; UI surfaces the stuck items so the operator can resolve. |
| localStorage cleared by the user | IndexedDB-backed queue is a future PR. For now, we document the limitation. |
| QR scan exposes the integrityHash (not a secret) | The integrityHash is a public fingerprint; the spec calls for it to be printed on the PDF on purpose. |
| `flushQueue` runs into a partial-failure state | The transport is called per item. Successes are removed; failures stay. The next call retries only the failures. |
