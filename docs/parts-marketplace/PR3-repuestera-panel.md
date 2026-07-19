# PR 3 — Repuestera Panel + Quote Form + Sales History

**Branch:** `feature/parts-marketplace-pr3-repuestera-panel`
**Depends on:** PR 1 (`feature/parts-marketplace-pr1-foundation`) only. Ships **in parallel** to PR 2 — independent of `part-suggestion.ts` / `ranking.ts` to avoid stacking churn.

---

## Why this PR third

The customer wizard (PR 2) and the supplier panel (PR 3) are two faces of the
same marketplace. Splitting them into independent PRs lets the team review
each surface on its own merits, and lets us ship value without forcing an
all-or-nothing review.

This PR lands everything the **repuestera** needs to do her job:

1. See incoming requests, with the same anti-fraud warnings the customer
   saw.
2. Submit a fully-validated quote (12 fields, photo evidence, condition
   declared, etc.).
3. Track her own sales history with a trust badge that summarizes her
   rating and dispute rate.

---

## What lands

### 1. `lib/parts/quote.ts` — pure quote utilities

* `buildQuoteFromForm(form): DraftSupplierQuote` — trims, normalizes photo
  URLs, computes `expiresAt`.
* `validateQuote(quote): ValidationResult` — anti-fraud rules live here
  (USEd requires photos, EXACT requires OEM+notes, IMPORT_REQUIRED with
  too-short ETA is suspect, etc.). 24 unit tests pin all of these.
* `tagQuote({ratingAvg, totalSales, claimRate}): TRUSTED | WARM | COLD` —
  trust signal classifier.
* `isRiskPartForQuote(partName): boolean` — gates the form's submit button
  for critical-safety parts (brakes, fuel, airbag, high-voltage).
* `expiresAtFromNow(hours)` — defensive 24h default if a bad value comes
  in.

### 2. `components/SupplierQuoteForm.tsx`

* All 12 fields from the spec, in the same order the spec lists them.
* Live `validateQuote()` panel (red blockers / amber warnings) that
  updates on every keystroke.
* Safety-part gate: if the requested part is a fuel pump / brake / airbag
  / high-voltage component, a mandatory "Instalación por técnico
  calificado" checkbox is shown and gates the submit button.
* Submit is BLOCKED while any validation error is present.
* `onSubmit` callback receives a fully-validated `DraftSupplierQuote` —
  no Supabase dependency inside the form.

### 3. `components/SalesHistoryBadge.tsx`

* Trust ribbon used in the repuestera panel header and in any future
  quote-row that needs to surface the repuestera's reputation.
* Three states: `TRUSTED` (≥4.6★, ≥50 sales, <5% disputes), `WARM`
  (≥4.0★, ≥5 sales), `COLD` (everything else).
* Tooltip exposes the raw numbers for full transparency.

### 4. `components/RepuesteraPanel.tsx`

The 6-tab repuestera dashboard per the spec:

| Tab | What it shows | Status |
|---|---|---|
| **Solicitudes** | Open PartRequests, with a "Cotizar" button. The same `evaluateCompatibility()` engine runs server-side-effects-free to surface BLOCK warnings the repuestera should see before quoting. | LIVE |
| **Mis cotizaciones** | Quotes the repuestera has sent, with status badges (SENT / ACCEPTED / REJECTED / EXPIRED / CANCELLED). | LIVE |
| **Inventario** | Placeholder. Local inventory lands in PR-4 once we decide whether to back it with `supplier_inventory_items` or a separate per-supplier table. | PLACEHOLDER |
| **Ventas** | ACCEPTED quotes, summary card (count + total gross), and a re-ranked list. | LIVE (re-ranks locally) |
| **Reputación** | Rating, total sales, dispute rate, verification status. | LIVE |
| **Configuración** | Profile data (business name, contact, address, delivery options). | LIVE |

The panel is **drop-in**: takes `profile`, `openRequests`, `myQuotes` as
props; emits an `onSubmitQuote({requestId, quote})` callback when the
repuestera submits a new quote. No Supabase inside.

### 5. New tests
`lib/parts/__tests__/quote.test.ts` — 24 tests covering all anti-fraud
rules, the trust tag classifier, and the risk-part detector. Total stack:
**44/44 tests pass**.

### 6. TypeScript clean-up (incidental fix)
`lib/parts/__tests__/compatibility.test.ts` had a brittle import path
(`'../compatibility'` instead of `'../'`). Fixed to use the barrel so
the test no longer depends on a single-file re-export.

---

## What does NOT land in this PR

* No backend writes. The panel emits callbacks; persistence is a separate
  PR.
* No real-time subscriptions. Polling-friendly data shape, the next PR
  can wire `supabase.channel(...).on('postgres_changes', ...)`.
* No Android changes. APK keeps using its existing `PartRequestScreen.kt`.
* No App.tsx wiring. Drop-in component for the next integrator.

---

## Independence from PR 2

PR 3 does **not** import from `lib/parts/part-suggestion.ts` or
`lib/parts/ranking.ts` (those live in PR 2). The Ventas tab does its
ranking with a local `acceptedScore()` helper instead. This lets the
two PRs land in either order without merge conflicts.

After both PRs are merged, a follow-up PR can swap the local helper for
`rankQuotes()` from the engine and remove the duplicated logic.

---

## Acceptance criteria (the spec, restated)

> la cotización incluye precio, condición, garantía y compatibilidad

All four are first-class form inputs, declared with types matching
`PartCondition` and `CompatibilityConfidence`.

> no se permite proveedor editar precio después de aceptación

Not enforced in this PR (server-side). The DB-side guard lives in PR 1's
trigger `trg_prevent_post_acceptance_quote_mutation`. The form will
need a "Quote accepted — readonly" state in a later integration PR.

> exigir evidencia fotográfica para piezas usadas

Enforced in `validateQuote()` — USED / REFURBISHED without photos
returns `BLOCK` (test `BLOCKS USED part without photos`).

> exigir garantía mínima visible cuando aplique

The form always shows the warranty input and surfaces a warning when
USED parts have 0 warranty days. Hard floor to be set in a follow-up
ADR after we collect real supplier behavior.

> mostrar advertencia para piezas críticas

Mandatory `installByQualifiedTech` checkbox shown for any part in the
safety taxonomy. Submit disabled until checked.

---

## Risk register

| Risk | Mitigation |
|---|---|
| Repuestera panel too dense for first launch | The 6 tabs match the spec exactly. PR-3 integrator can collapse tabs not yet backed by data. |
| Local `acceptedScore()` duplicates PR-2 `rankQuotes()` | Documented; follow-up PR will consolidate after both PRs merge. |
| No persistence layer yet | The form + panel are data-only by design. The integrator wires the callback to `supabase.from('part_offers').insert(...)`. |
| `compatibility.test.ts` import fix was incidental | Tests still pass; this is a code-hygiene improvement, not a behavior change. |
