# Parts Marketplace V2 — Technical Spec

**Branch:** TBD (start from `v0.6.0-report-hashing` tag)
**Owner:** Codex / ChatGPT agent
**Status:** Draft (waiting for Code green-light)
**Supersedes:** `docs/parts-marketplace/PR{1,2,3}-*.md` (the "form-only" version)
**Related:** ADR 0004 (Hilt wrapper + Compose EntryPoint pattern)

---

## Why this spec exists

PR #2 / #3 / #4 (foundations, wizard, repuestera panel) shipped a "Pedir
Repuestos" section that still feels like a form. The goal of this round
is to convert that form into a **technical marketplace**: vehicle-aware,
DTC-aware, 3D-aware, with a real compatibility engine, a real supplier
panel, ranking that doesn't sort only by price, and anti-fraud rules
that protect the user from being sold the wrong brake pad.

The high-level flow we want:

```
DTC → diagnóstico → mecánico → repuesto compatible → cotización → compra → reporte PDF → historial
```

That last arc — purchase → vehicle history → PDF report — is what makes
the app a closed-loop product, not a scanner.

---

## What's already in the repo (do NOT re-implement)

| Surface | Path | Lines | Status |
|---|---|---|---|
| CompatibilityEngine pure object | `android/.../core/parts/CompatibilityEngine.kt` | 395 | Shipped in PR #7. Reuse as-is, expose via Hilt like the Reports service (see ADR 0004 for the pattern). |
| PartSuggestionEngine | `android/.../core/parts/PartSuggestionEngine.kt` | 227 | Shipped in PR #7. Already understands `SuggestionSource { DTC, FROM_3D_COMPONENT, WORK_ORDER, MAINTENANCE_ALERT, PREPURCHASE }`. |
| PartQuoteRanker | `android/.../core/parts/PartQuoteRanker.kt` | 121 | Shipped. Already produces `QuotePrimaryTag { BEST_COMPATIBILITY, CHEAPEST, FASTEST_DELIVERY, BEST_WARRANTY, BEST_REPUTATION }`. |
| QuoteValidator | `android/.../core/parts/QuoteValidator.kt` | 166 | Shipped. Returns `ValidationLevel { OK, WARN, BLOCK }`. |
| PartsMarketplaceContract (legacy ↔ v2 mapper) | `android/.../core/parts/PartsMarketplaceContract.kt` | 92 | Already maps legacy `status/preference/position/condition` strings to v2 enums. Reuse for DB compatibility. |
| Hilt + Compose EntryPoint pattern | `android/.../di/ReportsModule.kt` | 61 | Reference implementation. Mirror this for `PartsModule` so the wizard / repuestera panel can inject the engines without a `@HiltViewModel` ceremony. |
| Cross-runtime parity test harness | `android/.../core/reports/HashEngineParityTest.kt` | 59 | Reference for "engine + golden fixture + assertion" pattern. If V2 introduces any byte-exact contract (e.g. a ranking hash), copy this style. |
| Existing UI bones | `ui/screens/PartRequestScreen.kt`, `ui/screens/RepairNetworkScreen.kt` | modified | Already in the other agent's WIP. Continue from there — see "Coordination" below. |

Total: **1001 lines of pure engine code already ship-ready.** Do not duplicate.

---

## What to build (priority order)

### Phase 1 — Data + DB (Supabase migration)

Add the V2 entities. Suggested filenames:
`supabase/migrations/2026XXXXXX_parts_marketplace_v2.sql`

#### 1.1 Enums (extend or add)

```sql
-- Already partly created in 20260704000000_parts_marketplace_foundation.sql.
-- Verify each one matches the Kotlin enums in core/parts/*.kt before adding.
-- New / canonical definitions for V2:

create type part_request_status_v2 as enum (
  'DRAFT','OPEN','RECEIVING_QUOTES','QUOTE_ACCEPTED','WAITING_PAYMENT',
  'ORDERED','READY_FOR_PICKUP','OUT_FOR_DELIVERY','DELIVERED',
  'CANCELLED','DISPUTED'
);
create type part_preference as enum (
  'ANY','OEM','AFTERMARKET','USED','REFURBISHED','PERFORMANCE','BUDGET'
);
create type part_position as enum (
  'FRONT_RIGHT','FRONT_LEFT','REAR_RIGHT','REAR_LEFT','CENTER',
  'ENGINE','TRANSMISSION','ELECTRICAL','BODY','INTERIOR','FUSE_BOX','NOT_APPLICABLE'
);
create type part_source_context as enum (
  'MANUAL','FROM_DTC','FROM_3D_COMPONENT','FROM_MECHANIC_WORK_ORDER',
  'FROM_MAINTENANCE_ALERT','FROM_PREPURCHASE_INSPECTION'
);
create type part_condition as enum (
  'NEW_OEM','NEW_AFTERMARKET','USED','REFURBISHED','REBUILT','UNKNOWN'
);
create type part_availability as enum (
  'IN_STOCK','SAME_DAY','NEXT_DAY','IMPORT_REQUIRED','UNKNOWN'
);
create type quote_status_v2 as enum (
  'SENT','ACCEPTED','REJECTED','EXPIRED','CANCELLED'
);
create type verification_status as enum (
  'UNVERIFIED','PHONE_VERIFIED','BUSINESS_VERIFIED',
  'INVENTORY_VERIFIED','ELITE_SUPPLIER','SUSPENDED'
);
create type compatibility_confidence as enum (
  'EXACT','HIGH','MEDIUM','LOW','UNKNOWN'
);
```

**Cross-check rule:** every enum value must exist on the Kotlin side.
Use `PartsMarketplaceContract` as the legacy-string-mapper; do NOT add a
new legacy-to-enum mapping without touching that file too.

#### 1.2 Tables

- `part_requests_v2` (id, user_id, vehicle_id, source_context, dtc_codes[],
  part_name, part_number_nullable, oem_number_nullable, category, position,
  quantity, preference, notes, photos[], location_lat_nullable,
  location_lng_nullable, delivery_address_nullable, status, created_at, updated_at)
- `supplier_profiles` (id, user_id, business_name, legal_name_nullable,
  phone, whatsapp, email, province, canton, address, delivery_enabled,
  pickup_enabled, service_radius_km, opening_hours, specialties[],
  brands_supported[], part_categories[], verification_status, rating_avg,
  total_sales, claim_rate, created_at, updated_at)
- `supplier_quotes` (id, part_request_id, supplier_id, part_name, brand,
  part_number_nullable, oem_number_nullable, condition, price, currency,
  availability, estimated_delivery_hours, warranty_days, includes_delivery,
  delivery_fee, compatibility_confidence, compatibility_notes,
  photo_urls[], quote_status, created_at, expires_at)
- `supplier_inventory_items` (id, supplier_id, part_name, brand,
  part_number, oem_number_nullable, category, compatible_vehicles[],
  condition, quantity, price, currency, photos[], warranty_days,
  created_at, updated_at)

**RLS:** every table must be RLS-locked. Customers see only their own
requests + open public requests. Repuesteras see only requests matching
their `service_radius_km` + `part_categories[]` unless they're the
request author. Admins see everything.

**Anti-fraud columns to bake in from day 1** (do NOT defer to "later"):
- `supplier_quotes.quote_version` (int, increments on every update,
  immutable once status transitions out of `SENT`).
- `supplier_quotes.price_locked` (bool, true once `quote_status = ACCEPTED`).
- `supplier_quotes.evidence_photo_required` (bool, true when condition is
  USED / REFURBISHED / REBUILT).

### Phase 2 — Wire engines into Hilt

Mirror the ADR 0004 pattern:

- `android/.../di/PartsModule.kt`:
  ```kotlin
  @Module
  @InstallIn(SingletonComponent::class)
  object PartsModule {
      @Provides @Singleton fun provideCompatibilityEngine(): CompatibilityEngine = CompatibilityEngine
      @Provides @Singleton fun providePartSuggestionEngine(): PartSuggestionEngine = PartSuggestionEngine
      @Provides @Singleton fun providePartQuoteRanker(): PartQuoteRanker = PartQuoteRanker
      @Provides @Singleton fun provideQuoteValidator(): QuoteValidator = QuoteValidator
  }
  ```
- Add `@EntryPoint` interface for Compose-side lookups so screens that
  don't yet have a `@HiltViewModel` can still inject.
- Wrap the engines in a single `PartsMarketplaceService` if you want
  one-stop-injection — but only if it adds value (it likely does for the
  repuestera panel that needs all four).

### Phase 3 — UI flows

#### 3.1 Customer wizard (replace flat `PartRequestScreen.kt`)

4 steps, full-screen each, with a sticky footer for "Atrás / Siguiente":

1. **Identificar pieza** — name, category, part_number (optional), photo
   (optional), DTC chip (optional), origin (MANUAL / FROM_DTC / FROM_3D / etc.)
2. **Compatibilidad** — vehicle make/model/year/engine/transmission,
   VIN (optional), position, preference. **Always show the
   `QuoteValidator` warnings live as the user types.** If compatibility
   would resolve to EXACT without VIN/OEM, downgrade to HIGH and show a
   warning — `QuoteValidator` already does this; just surface it.
3. **Entrega** — pickup vs delivery, urgency, location.
4. **Resumen** — read-only summary + warnings + "Enviar a red de
   repuesteras" CTA. The CTA must call `PartSuggestionEngine` to fill in
   `dtc_codes[]` when origin = FROM_DTC.

Acceptance criterion (from Jor):

> Pedir "bomba de combustible" desde P0230 debe mostrar advertencia:
> "No reemplazar bomba sin confirmar alimentación, tierra, relé/fusible
> y presión con manómetro". **No empujar venta de bomba como primera
> opción.**

Implement via `QuoteValidator` + `PartSuggestionEngine.prioritize()` —
the order in which parts surface MUST NOT be "bomba" first when the DTC
is P0230. The validator should BLOCK or WARN before allowing the user to
publish a request with `category = FUEL_PUMP` if the request originates
from P0230 without explicit confirmation.

#### 3.2 Repuestera panel

New screen. Tabs:

- **Solicitudes** — list of matching `part_requests_v2` ordered by
  recency. Each row has vehicle, part, qty, position, distance, urgency,
  DTC badge, photo thumb, "Cotizar" CTA.
- **Mis cotizaciones** — `supplier_quotes` where `supplier_id = me`. Use
  `PartQuoteRanker` to tag each one (`BEST_COMPATIBILITY`, etc.) for
  the customer-side view.
- **Inventario** — `supplier_inventory_items`. MVP can skip this tab;
  allow manual quotes without inventory.
- **Ventas** — closed quotes, sorted by month.
- **Reputación** — `rating_avg`, `total_sales`, `claim_rate`. Required
  for `VerificationStatus = ELITE_SUPPLIER`.
- **Configuración** — `supplier_profiles` form. Critical fields:
  `service_radius_km`, `part_categories[]`, `brands_supported[]`,
  `delivery_enabled`, `pickup_enabled`.

### Phase 4 — Ranking

`PartQuoteRanker` is already there. V2 ranking must NOT be "cheapest
first". Output order must be selectable:

- "Mejor compatibilidad"
- "Más barato"
- "Entrega más rápida"
- "Mejor garantía"
- "Proveedor mejor calificado"

Wire each of those as a sort mode in the customer comparison screen.
The score breakdown (`SupplierQuoteScore`) should be visible per quote:
compat, price, warranty, distance, availability, reputation, evidence
photo, delivery time, condition.

### Phase 5 — Anti-fraud + safety

Hard rules (do not soften):

1. **Never mark `compatibility_confidence = EXACT`** without VIN + OEM
   OR (closed tuple `brand, model, year, engine, OEM`) OR visual
   confirmation (photo of part + connector).
2. Never display "compatible garantizado". Always say "compatibilidad
   probable, requiere confirmar por VIN/OEM/foto/conector/medidas".
3. Quote `price` and `currency` become immutable once
   `quote_status = ACCEPTED`. Use the `quote_version` column.
4. Rate-limit quotes per supplier per request (e.g. max 3 revisions).
5. **Critical parts** (brakes, steering, suspension, fuel, airbag, HV
   battery, ABS) must show a mandatory warning banner:
   > "Instalación recomendada por técnico calificado. Una pieza
   > incompatible puede causar falla mecánica, eléctrica o de seguridad."
6. USED / REFURBISHED / REBUILT parts require a `photo_urls[]` of the
   actual item. Reject the quote at submission if missing.
7. Disputes create a `DISPUTED` state on `part_request_status_v2` and
   freeze the related `supplier_quotes`. Admin-only resolution.

### Phase 6 — History integration

When `part_request_status_v2` transitions to `DELIVERED`:

- Insert a row in `repair_history` (already exists in the Room DB
  schema — see `AppModule.kt` migration `_10`).
- Snapshot the quote, supplier, vehicle, DTC link, and price.
- Make the resulting history row visible from
  `ui/screens/VehicleDetailScreen.kt` and from the `ReportScreen` PDF
  generator so the customer can export an audit-ready PDF with the
  purchase record attached.

---

## Critical parts list (V2)

Reuse / extend the existing critical-category enum. At minimum:

- `BRAKES` (pads, discs, calipers, master cylinder)
- `STEERING` (rack, tie rods, power steering pump)
- `SUSPENSION` (shocks, struts, springs, control arms)
- `FUEL_SYSTEM` (pump, injectors, lines, tank)
- `AIRBAG` (any airbag module, sensor, wiring)
- `HIGH_VOLTAGE` (hybrid / EV battery pack, inverter, orange cabling)
- `ABS_MODULE`
- `TRANSMISSION_INTERNAL` (clutch, torque converter, valve body)

Any quote touching these categories must show the mandatory safety
banner (rule #5 above) and require a `warranty_days >= 30` unless the
supplier's `verification_status = ELITE_SUPPLIER`.

---

## P0230 case study (acceptance test)

This is Jor's hard acceptance criterion:

1. Customer opens the app on Hyundai Accent Verna 2005.
2. Scanner reports DTC P0230.
3. Customer taps "Pedir repuesto" from the DTC detail card.
4. Wizard pre-fills:
   - `source_context = FROM_DTC`
   - `dtc_codes = ["P0230"]`
   - `category = FUEL_SYSTEM`
5. Customer types `part_name = "bomba de combustible"`.
6. Before the wizard allows "Enviar", the validator must:
   - Show WARN: "P0230 se resuelve en 70% de los casos con relé / fusible
     / cableado / masa. Confirmar voltaje y presión antes de pedir la
     bomba."
   - Suggest the alternative parts in priority order:
     1. Relay (P0230 most common fix)
     2. Fuse
     3. Wiring harness + ground
     4. Pump (only if voltage + pressure checks confirm)
   - Require the user to tick "He verificado alimentación, tierra, relé
     y fusible" before allowing the request to publish.
7. Only then can the user publish. Repuesteras see the request with
   `dtc_codes = ["P0230"]`, `category = FUEL_SYSTEM`, and the
   `notes` from the user's verification.
8. No quote in response can claim `compatibility_confidence = EXACT`
   without VIN or OEM. Validator must downgrade to HIGH + WARN.

The unit test for this lives at
`android/.../core/parts/QuoteValidatorP0230Test.kt` and the integration
test at `android/.../core/parts/PartRequestWizardP0230Test.kt`.

---

## Coordination with the other agent's WIP

There is an in-progress WIP on `main` (uncommitted, see `git status`):

- `ui/ObdViewModel.kt`, `ui/screens/PartRequestScreen.kt`,
  `ui/screens/RepairNetworkScreen.kt`,
  `ui/screens/RideServiceScreen.kt`,
  `ui/screens/TowTruckServiceScreen.kt`,
  `test/.../SanitizeGpsAddressTest.kt`,
- `core/parts/PartQuoteRanker.kt`, `core/parts/PartsMarketplaceContract.kt`
  and their tests.

These will land first. Do NOT clobber them. Use `git diff` before each
push to confirm you are only adding files. The skill
`~/.mavis/skills/codex-mavis-sync/scripts/sync.sh --auto` will merge
both sides into `sync/codex-mavis-<timestamp>` before the APK ships.

---

## Anti-goals (do NOT do these)

- Do not re-implement the engines already in `core/parts/`.
- Do not edit `ObdViewModel.kt` except through the `PartsMarketplaceService`
  wrapper, to keep Mavis's PR #9 RISK-3 GPS-leak fix intact.
- Do not push compatibility to `EXACT` without evidence — that's a
  hard product safety rule.
- Do not skip the ranker — sorting by price alone is a regression.
- Do not defer the antifraud columns to a follow-up migration. Bake
  them in V2.

---

## Refs

- ADR 0004 — Report Hashing Service (Hilt wrapper + EntryPoint pattern, mirror this)
- `docs/architecture/CROSS-RUNTIME-PARITY.md`
- `docs/parts-marketplace/PR1-foundations.md`
- `docs/parts-marketplace/PR2-wizard-and-engine.md`
- `docs/parts-marketplace/PR3-repuestera-panel.md`
- `android/.../core/parts/*.kt` (existing engines)
- `android/.../di/ReportsModule.kt` (reference Hilt module)
- `tests/parity/fixtures/snapshot-p0230.json` (cross-runtime reference for any byte-exact contract added)
