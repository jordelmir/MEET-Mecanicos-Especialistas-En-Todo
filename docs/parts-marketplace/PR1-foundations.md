# PR 1 — Parts Marketplace Foundation

**Branch:** `feature/parts-marketplace-pr1-foundation`
**Scope:** Schema + types + pure compatibility engine. No UI yet.
**Backwards compatible:** Yes — every new column is nullable or has a safe default, and the legacy `parts_stores` / `part_requests` / `part_offers` tables keep their existing shape and policies.

---

## Why this PR first

The full marketplace spec from Jor includes a wizard, a repuestera panel, an
inventory module, a ranking system, anti-fraud rules, and integration with the
DTC repair guides and the 3D engine viewer. None of that is shippable without
the underlying schema and a deterministic compatibility evaluator.

So PR 1 cuts the smallest possible sliver that unblocks PRs 2 (wizard) and 3
(repuestera panel). PR 2 will add UI; PR 3 will add the repuestera panel;
PR 4 will integrate with the 3D viewer. **This PR ships no UI on purpose.**

---

## What lands

### 1. Supabase migration
File: `supabase/migrations/20260704000000_parts_marketplace_foundation.sql`

* **9 new PostgreSQL enums**
  `part_request_status_v2`, `part_preference`, `part_position`,
  `part_source_context`, `part_condition`, `part_availability`,
  `quote_status_v2`, `verification_status`, `compatibility_confidence`.
* **Aliased extensions** on `parts_stores` (now a proper `SupplierProfile`),
  `part_requests` (now vehicle-aware), and `part_offers` (now compatibility-
  aware). Legacy columns remain; new columns have safe defaults.
* **2 new tables**
  * `supplier_inventory_items` — optional local inventory per repuestera.
  * `part_purchase_history` — vehicle service history (parts).
* **1 ledger table**
  * `part_disputes` — anti-fraud reports.
* **1 ranked view**
  * `part_quote_ranking_v1` — heuristic rank per offer for SQL-side scans.
* **1 trigger**
  * `trg_prevent_post_acceptance_quote_mutation` — locks price/brand/
    warranty/OEM edits after a quote is accepted; bumps `quoteVersion`
    on any other update for history recovery.
* **RLS** for the new tables. Existing policies are **not** altered.

### 2. Shared TypeScript types
File: `lib/parts/types.ts`

* Enumerations as `as const` arrays + derived unions (no stringly-typed bugs
  in the web layer).
* `VehicleFingerprint`, `CompatibilityContext`, `CompatibilityResult`,
  `CompatibilityWarning`. Warning shapes are tagged unions with severity
  (`INFO | WARN | BLOCK`) so the UI can act on them.

### 3. Compatibility Engine (pure)
File: `lib/parts/compatibility.ts`

A pure, deterministic function `evaluateCompatibility(ctx)` that:

* refuses to assert `EXACT` unless `VIN + (OEM | part number)` is present,
  **or** the closed tuple `(brand, model, year, engine, OEM)` is present.
* caps every tier at the highest confidence the evidence supports.
* emits a `BLOCK` warning for `P0230 + fuel-pump` — verbatim text from the
  spec ("No reemplazar bomba sin confirmar alimentación, tierra, relé/
  fusible y presión con manómetro").
* emits the install-by-qualified-tech warning for any critical-safety part
  (brakes, steering, suspension, airbag, fuel system, high-voltage).
* downgrades a `EXACT` verdict when a `BLOCK`-level warning is present.

### 4. Tests
File: `lib/parts/__tests__/compatibility.test.ts`

Uses Vitest. Covers the Jor acceptance scenario (P0230 + fuel pump), the
EXACT-tier rules, the safety-taxonomy classifier, and the demotion rule.

### 5. Vitest config
File: `vitest.config.ts`

Adds `npm run test` and `npm run test:watch`. No new transitive runtime
dependencies — Vitest is a `devDependency` only.

---

## What does NOT land in this PR

* No React UI. PR 2 builds the wizard.
* No RPC / Edge Function for ranking. PR 1 ships a SQL view; heavier ranking
  can come from an edge function once we collect enough telemetry.
* No payload for the repuestera panel. PR 3 adds it.
* No real-time subscriptions on `part_offers`. PR 3 opts in.
* No payments integration. The legacy `google_play_purchase_receipts`
  pipeline is unrelated and untouched.

---

## Migration rollout plan

1. Run the migration against staging first. Verify:
   * `SELECT count(*) FROM public.part_offers WHERE "statusV2" IS NULL;`
     should be **0** after the in-place backfill runs.
   * `SELECT count(*) FROM public.parts_stores WHERE "verificationStatus"
     = 'UNVERIFIED';` should equal the legacy count.
2. Replay Android release to a small cohort. The Android `PartRequestEntity`
   serializes by `camelCase`. Existing fields stay, new fields are read-only
   for now (the app ignores columns it doesn't know).
3. Promote to production. The migration is **non-destructive** — every
   column it adds is nullable or DEFAULT-valid.

---

## Acceptance criteria (the spec, restated)

> un cliente puede pedir un repuesto para Hyundai Accent Verna 2005

Already possible today (legacy). The new schema adds the metadata field
to do it without losing the context (DTC, position, OEM, photo, etc.).

> puede originar la solicitud desde P0230

`source_context` enum gains `FROM_DTC`. PR 2 wires the UI; the schema
already supports it.

> puede originarla desde el relé/bomba en 3D

`source_context` enum gains `FROM_3D_COMPONENT`. PR 4 wires it.

> una repuestera puede cotizar

`part_offers` accepts quotes, and the schema carries compatibility metadata.
The UI lives in PR 3.

> la cotización incluye precio, condición, garantía y compatibilidad

Yes. New columns on `part_offers`: `conditionDetail`, `warrantyDays`,
`compatibilityConfidence`, `compatibilityNotes`. Trigger locks them after
acceptance (see point above).

> el sistema advierte si falta VIN/OEM

`evaluateCompatibility()` emits `NO_VIN` and `NO_OEM` warnings. PR 2 surfaces
them.

> el usuario puede aceptar una cotización

Already supported by the legacy `sync_accepted_part_offer` trigger.
`part_purchase_history` row gets written in PR 3 (deferred for shippability).

> el historial del vehículo guarda la compra

`part_purchase_history` table is created here; the trigger that writes to it
lands in PR 3.

> no se marca compatibilidad exacta sin evidencia

Test `promotes to EXACT only with VIN + OEM, or closed (brand+model+year+
engine+OEM) tuple` is a hard guard.

---

## Risk register

| Risk | Mitigation |
|---|---|
| Migration breaks anonymous reads on `parts_stores` | New policies only ADD grants; legacy GRANT is untouched. |
| Android client ignores new columns silently | Forward-compatible by design. |
| Refund/dispute workflow doesn't exist yet | `part_disputes` is the ledger; status flow lands in PR 3. |
| `verify-google-play-purchase` edge function does not know about parts | Out of scope. Unrelated code path. |
