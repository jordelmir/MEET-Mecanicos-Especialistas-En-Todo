# PR 2 — Wizard + Suggestion + Ranking Engines

**Branch:** `feature/parts-marketplace-pr2-wizard`
**Depends on:** PR 1 (`feature/parts-marketplace-pr1-foundation`) — merged or stacked.
**Scope:** Suggestion engine, ranking engine, and the React wizard UI for the **customer** side. No supplier (repuestera) panel yet — that is PR 3.

---

## Why this PR second

PR 1 shipped the pure compatibility engine. Without a wizard or suggestions,
the engine is invisible — it has to be wired into the React surface for any
of the acceptance scenarios to actually run. PR 2 does the customer-side
wiring end-to-end. The repuestera-side panel is decoupled (PR 3) so we can
ship value iteratively.

---

## What lands

### 1. Pure suggestion engine
File: `lib/parts/part-suggestion.ts`

* `suggestParts({source, dtcCodes, componentSlug, workOrderHint}) -> PartSuggestion[]`
* P0230 is the headline case: the function returns relay, fuse, harness,
  fuel-pressure sensor in priority order, with the **fuel pump** last and
  tagged `riskPart: true` so the UI shows the verbatim disclaimer.
* Other DTCs covered: `P0420` (P0420 + catalytic converter risk), `P0300`
  (P0300 + spark plugs first, injector last), `P0171` (gas cap first).
* 3D component slugs covered: `fuel_pump_relay`, `fuel_pump_assembly`
  (flagged riskPart), `abs_module` (safety disclaimer).
* Work-order hints pass through verbatim.

### 2. Pure ranking engine
File: `lib/parts/ranking.ts`

* `scoreQuote(q, vehicle?)` — mirrors the SQL view weights
  (compat 0.55, reputation 0.20, delivery 0.15, warranty 0.10).
* `rankQuotes(candidates, vehicle?)` — returns sorted list with
  `BEST_COMPAT | CHEAPEST | FASTEST | TOP_RATED` tags (mutually exclusive).
* Only items with composite >= 50% of the leader AND compat MEDIUM+ are
  eligible for the alternative tags. Low/unknown confidence items never
  beat a HIGH/EXACT leader, even if they're cheaper.

### 3. Wizard UI (4 steps per the spec)
File: `components/PartRequestWizard.tsx`

* Step 1: **Identify** — name, category, OEM, position, preference, photo
  URL, notes. Pre-fills from the suggestion engine when source = `FROM_DTC`
  / `FROM_3D_COMPONENT` / `FROM_MECHANIC_WORK_ORDER`. Risk-tagged items show
  the literal disclaimer as `AlertTriangle` text under the suggestion.
* Step 2: **Compatibility** — vehicle fingerprint, live-evals
  `evaluateCompatibility()`, mounts the `CompatibilityPanel` so the user
  sees the verdict / warnings / confirmations as they type. No submit
  gate: even LOW/UNKNOWN can proceed.
* Step 3: **Delivery** — pickup vs delivery + address, urgency (NORMAL /
  HIGH / CRITICAL).
* Step 4: **Publish** — full summary, final compatibility panel, BLOCK
  warning pin (red ribbon) if any BLOCK warning is present, mandatory
  checkbox (`He leído la evaluación…`) before publish.
* The wizard is **data-only**: emits `DraftPartRequest` to the parent
  via `onPublish()`. No fetch / no Supabase — keeping the wizard
  unit-testable in isolation.

### 4. Compatibility Panel (dumb renderer)
File: `components/CompatibilityPanel.tsx`

Renders a `CompatibilityResult` with:
* Verdict pill (NEVER says "compatible guaranteed" — only EXACT / PROBABLE
  / BAJA / INSUFICIENTE + the engine's rationale string).
* BLOCK warnings pinned to top.
* "Para subir la confianza" → list of required confirmations.
* "Preguntas para la repuestera" → recommended questions list.

### 5. Quote Ranking Panel (dumb renderer)
File: `components/QuoteRankingPanel.tsx`

* List of `RankedQuote`s in ranked order.
* Per-row primary tag badge (`BEST_COMPAT`, `CHEAPEST`, `FASTEST`,
  `TOP_RATED`).
* Price, ETA, rating, warranty, compatibility confidence.
* Optional `onAccept` button. If omitted, the panel is read-only (used in
  the publish preview screen).

### 6. New tests
Files:
* `lib/parts/__tests__/part-suggestion.test.ts` (14 tests)
* `lib/parts/__tests__/ranking.test.ts` (10 tests)

Both engines now have explicit coverage including the **Jor acceptance
scenario** for P0230.

### 7. New enum value
File: `lib/parts/types.ts` — `PartPosition` enum gains `EXHAUST` so the
P0420 / O2 sensor / catalytic-converter suggestions are valid against
the type system.

### Test counts after PR 2
```
✓ lib/parts/__tests__/compatibility.test.ts   (20 tests)
✓ lib/parts/__tests__/part-suggestion.test.ts (14 tests)
✓ lib/parts/__tests__/ranking.test.ts         (10 tests)
Test Files  3 passed (3)
Tests       44 passed (44)
```

---

## What does NOT land in this PR

* No backend write of the request. PR 2 emits a payload; PR 3 will land
  the persistence path to Supabase.
* No repuestera panel UI — that's PR 3.
* No real-time subscriptions on `part_offers` — PR 3.
* No Android changes. The Android client can keep using its existing
  `PartRequestScreen.kt` until the web wizard proves out.

---

## Routing

The wizard is **not wired into `App.tsx`** in this PR. That's intentional:
App.tsx is a 1.3k-line file with many pending changes from another agent.
Adding a route here would risk merge hell. Instead, the wizard ships as a
**drop-in component**. Place it in `ClientDashboard`, or behind a new
"Repuestos" navigation entry, or in a modal — wherever the team prefers.

Recommended next step: a 6-line PR that imports `<PartRequestWizard />`
into the appropriate view (likely ClientDashboard) and supplies an
`onPublish` that calls `supabase.from('part_requests').insert({...})`.

---

## Acceptance criteria (the spec, restated)

> desde P0230, pedir "bomba de combustible" y validar que la app muestre
> advertencia: "No reemplazar bomba sin confirmar alimentación, tierra,
> relé/fusible y presión con manómetro"

Already covered by tests in `part-suggestion.test.ts`. The Wizard renders
the suggestion with the `ALTO RIESGO` chip and the verbatim disclaimer.

> puede originar la solicitud desde P0230

The wizard accepts `initialSourceContext="FROM_DTC"` and `initialDtcCodes={["P0230"]}`
and pre-fills relay / fuse / harness ahead of the pump.

> el sistema advierte si falta VIN/OEM

The compatibility panel surfaces NO_VIN and NO_OEM warnings whenever
those facts are missing.

> la cotización incluye precio, condición, garantía y compatibilidad

Inferred from `RankableQuote` and rendered in `QuoteRankingPanel`.
Condition granularity lives in `PartCondition` (PR 3 wiring).

---

## Risk register

| Risk | Mitigation |
|---|---|
| Wizard not wired in yet (routing PR outstanding) | Documented; recommended 6-line follow-up. |
| App.tsx pre-existing TS errors | Out of scope. PR 2 does not touch App.tsx; the build will still fail without a parallel fix by another agent. |
| Suggestion engine too narrow | The map is intentionally small in PR 2; the function shape is open to Knowledge-Pack backing in PR 4. |
| PR-1 not merged yet → no schema to write against | Wizard emits a typed payload; writes are deferred to PR 3. |
