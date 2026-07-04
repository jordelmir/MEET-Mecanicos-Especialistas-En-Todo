# ADR 0002 — Parts Marketplace: schema & engine first, UI later

**Status:** Accepted
**Date:** 2026-07-04
**Deciders:** Jor, Mavis
**Supersedes:** —

---

## Context

The full spec for MEET's parts/repuestera/vin-dtc marketplace covers ~13
sections: 4 entities, a compatibility engine, an inventory module, a
repuestera panel, a ranked-quotes surface, anti-fraud rules, and integration
with DTC repair guides, the 3D engine viewer and the vehicle service
history. Shipping the full spec as one PR is not viable: it would touch
Android, Web, Supabase schema, RLS, Edge Functions, and the React UI in
the same diff. That kind of PR does not get reviewed, does not get
reverted cleanly, and tends to leak bugs.

The user constraint (and what we will be evaluated against) is two specific
acceptance scenarios:

1. From DTC `P0230`, the user can request "bomba de combustible" and the
   app must NOT push that part as a first-order fix, and must show the
   verbatim warning about checking wiring, relay, fuse and fuel pressure
   first.

2. Verdict `EXACT` is never displayed unless the engine has VIN + OEM,
   or the closed tuple `(brand, model, year, engine, OEM)`.

Both scenarios can be satisfied purely with schema + a deterministic TS
engine. No UI required. **So that's the first PR.**

---

## Decision

PR 1 ships:

* Supabase migration `20260704000000_parts_marketplace_foundation.sql`
  (enums, extensions on legacy tables, new tables, view, trigger, RLS).
* Shared TS types `lib/parts/types.ts`.
* Pure compatibility engine `lib/parts/compatibility.ts`.
* Vitest config + tests for the engine.
* Documentation and this ADR.

PR 1 ships **no React UI by design.** PR 2 (wizard) and PR 3 (repuestera
panel) add React. PR 4 hooks the 3D engine viewer.

---

## Trade-offs

### Why ship the engine before the UI?

The engine is the single piece that decides whether a quote can be marked
`EXACT` or has to carry a `BLOCK` warning for `P0230`. Without it, no UI
in PR 2 or PR 3 can satisfy the acceptance scenarios — the UI would have
to invent its own logic, which would not stay synchronized across the
React Native client, the web client and any future channel.

A pure, deterministic engine with unit tests is reviewable in one pass.
It also lets the Android team start consuming the function from Kotlin in
parallel, because `evaluateCompatibility()` is `string -> object` with no
platform-specific dependencies.

### Why no UI in this PR?

* PR size. A UI PR alone is ~700 LOC even before tests. Adding schema
  work would push the PR past the threshold where reviewers lose the
  thread.
* Independent shippability. If the engine regresses in production, we
  revert **one** PR; we don't have to bisect across UI + schema changes.
* Telemetry. Once the engine is live, any consumer (current web wizard,
  Android future client, or a manual `curl` against an RPC) can write
  compatibility inputs to the view. We collect evidence before we make
  UX decisions in PR 2.

### Why named enums instead of text columns?

* IDE autocomplete on `PartCondition` vs stringly-typed `'NEW_OEM'` is
  measurably faster to write.
* Compiler catches typos before they reach staging.
* PostgreSQL native enums gain their own indexes when used in WHERE.
* Marginal cost: a `CREATE TYPE` per enum. Negligible.

### Why didn't we rename legacy tables?

* `parts_stores` is referenced by name from the Android client. Renaming
  the table would force a coordinated Android change in the same PR.
* The new columns carry the *additional* metadata the spec requires
  (verification status, opening hours, brands supported, etc.) without
  forcing a rollback if the spec changes.
* The legacy table is **logically** now `supplier_profiles`. That's a
  naming question for the docs; the DB kept `parts_stores` to minimize
  blast radius.

### Why use a SECURITY DEFINER trigger for the post-acceptance lock?

The trigger runs even if the row-level policy is bypassed by an admin
tool. The same lock works whether you go through RLS, an edge function,
or a manual `psql` session.

### Why a SQL view for ranking?

V1 ranking is a single-step weighted sum that fits in one SQL expression.
No need for a worker. We can promote to an Edge Function once we
actually need a trained model or per-region normalization (PR 4+).

---

## Consequences

* No UI in this PR. Reviewers focus on the engine + schema.
* Android client can keep working from the existing columns. New columns
  are read-only until PR 4.
* Web React surfaces need to wire the engine in PR 2.

---

## Re-evaluate when

* The engine's ranking surface starts to diverge significantly from
  what users actually accept. (Should land in PR 4 with telemetry.)
* The Supabase schema hits ~200 tables and we need a unifying registry.
* The Android client grows heavy enough to want its own copy of the
  engine. **Prefer sharing via a typed package** rather than duplicating.
