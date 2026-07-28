# Universal Parts + Collaborative Rides Implementation Plan

## Phase 1 — Contracts and provenance

- Add versioned corpus source support for `base de datos principal.docx`.
- Add universal 3D authority/binding contracts and repair-intelligence contracts.
- Add ride place, stop, quote, payment, boarding, claim, incident, and ETA contracts.
- Verify with deterministic contract and corpus tests.

## Phase 2 — Universal inline 3D

- Normalize physical component identities.
- Resolve dedicated, system-cutaway, or semantic procedural scenes.
- Expand procedural archetypes and stable visual parameters.
- Replace general-atlas missing-state in the component detail screen.
- Generate and test a 4,753-record coverage manifest.

## Phase 3 — Grounded repair workflow

- Build section-aware source-neighborhood retrieval.
- Classify literal blocks into diagnostic/repair phases.
- Render phase cards, related parts, evidence, and applicability warnings.
- Verify source traceability and no invented technical values.

## Phase 4 — Ride foundation and authoritative operations

- Migrate integer money, ordered stops, payment declaration, quote versions, boarding challenges, claim receipts, and trip events.
- Add server-authoritative first-claim and PIN RPCs with RLS/rate limits.
- Wire repositories so UI mutations stop using unconditional local updates.
- Verify concurrency, replay, insufficient balance, and unauthorized actor cases.

## Phase 5 — Map, places, incidents, and ETA

- Implement provider-neutral place autocomplete and routing contracts.
- Render pickup, numbered stops, destination, driver, passenger GPS, route progress, and stale-position state.
- Add road-report composer, confirmation/denial, expiry, reputation, and moderation.
- Add robust segment-speed aggregation and bounded incident ETA factors.
- Verify no provider/no network, stale data, conflicting reports, and route recalculation.

## Phase 6 — Passenger/driver experience

- Recompose passenger request around map, real places, dynamic stops, payment, quote, and PIN.
- Recompose driver offers for glanceability and atomic claim feedback.
- Add optional event-driven TTS, haptics, sounds, glass/neon motion, accessibility, and reduced-motion mode.
- Add final fare breakdown and auditable amendments.

## Phase 7 — Release gates

- Run targeted unit and instrumentation tests.
- Run `bash tests/parity/ci-verify.sh`.
- Build debug/release candidate APK.
- Install and launch through ADB; verify foreground process and clean crash logs.
- Update product/runbook/release documentation.
- Commit, push, create/merge PR only after green gates, and upload the verified APK to the GitHub release.

