# MEET Universal Parts 3D + Collaborative Rides

**Status:** Approved by product owner on 2026-07-28  
**Pilot:** Costa Rica, globally extensible  
**Principle:** additive closed loop, source-grounded automotive guidance, server-authoritative ride safety

## Outcomes

1. Every proprietary component record opens an inline interactive 3D/360 scene.
2. Every record exposes source-grounded diagnostic, repair, replacement, validation, and evidence steps.
3. Ride passengers can search real places, add ordered stops, understand the price, and start only after a secure four-digit boarding PIN.
4. Drivers see a glanceable request summary and only one driver can claim a request.
5. Drivers can report road conditions; corroborated, fresh reports influence route ETAs without allowing one untrusted report to dominate.

## Non-negotiable truth boundaries

- A procedural reconstruction is not OEM CAD.
- No torque, measurement, compatibility, route, traffic, or ETA may be presented as verified when its source is absent.
- New `base de datos principal.docx` material is ingested as versioned provenance; historical sources are not silently overwritten.
- Raw PINs are not persisted. Full phone, plate, VIN, and exact private trip history are not exposed to unrelated drivers.
- Map search and routing failures remain visible as unavailable data, never synthetic results.

## Universal 3D resolution

The resolver uses the first trustworthy level:

1. `DEDICATED_REFERENCE`: unique canonical atlas binding.
2. `SYSTEM_CUTAWAY`: literal component localized in a recognizable system asset.
3. `SEMANTIC_PROCEDURAL`: deterministic per-record scene produced from normalized identity, system, archetype, and stable seed.

Every result carries `authority`, `fidelity`, `source`, and `limitations`. The detail screen renders the selected result inline and no longer treats the general atlas as the missing-model fallback.

Normalization separates the physical name from tabular procedure text. Archetypes cover, at minimum, structure/panels, fasteners, seals, hoses/lines, harnesses/connectors, relays/fuses/modules, sensors, actuators/solenoids, valves, pumps, shafts/gears/bearings, pistons, belts/chains, brakes, springs/dampers, wheels/tires, lamps/glass, seats/restraints, and generic assemblies.

## Per-part repair intelligence

Each component receives a structured bundle assembled only from attributable nearby source blocks:

- role and failure symptoms;
- discovery questions;
- visual/non-invasive tests;
- electrical, mechanical, hydraulic, or pneumatic checks;
- preparation and safety;
- removal and inspection;
- repair-versus-replace decision;
- installation/calibration;
- post-repair validation;
- related parts and evidence checklist.

Missing values use explicit unavailable/pending language. Applicability remains gated by vehicle/VIN/OEM evidence.

## Ride request and stops

A request owns ordered immutable stop snapshots. Editing before dispatch produces a new quote version. A post-start stop/deviation is a proposed amendment requiring both parties' acceptance and an auditable price effect.

Place search uses a provider-neutral adapter with debounce, cancellation, geographic bias, attribution, and cache. Selecting a result stores provider ID, label, coordinates, and resolution timestamp. Routing accepts pickup + stops + destination.

Money is stored in integer minor units. Passenger UI shows estimate components before request and final components after completion. Initial payment methods are cash and SINPE; the app records the declared method but does not falsely claim settlement confirmation.

## Boarding and voice

The server generates a four-digit PIN, stores only a slow hash plus expiry, and limits attempts. The assigned driver verifies it through an idempotent RPC. Successful verification transitions the trip to `PASSENGER_ONBOARD`.

Voice prompts are event-driven, localized, optional, interruptible, and never require continuous microphone access:

- arrival;
- PIN accepted;
- greeting using the passenger's chosen display name;
- service start;
- route-preference question;
- stop arrival;
- completion and amount due.

## Atomic driver claim

The default dispatch mode is first confirmed server claim:

- request row locked;
- request must still be claimable;
- driver and vehicle eligibility checked;
- available commission balance reserved;
- assignment and event written in one transaction;
- stable idempotency key returns the prior result on retries;
- losing callers receive `ALREADY_CLAIMED` plus a safe summary.

Success and loss feedback have distinct animation, haptic, and sound semantics. Audio obeys driver settings and accessibility.

## Collaborative road intelligence

Supported report types:

- slow traffic;
- very slow/stopped traffic;
- stalled vehicle (left, center, right);
- pothole;
- obstacle (left, center, right);
- road closed;
- wrong-way/contra-flow hazard;
- police presence;
- traffic officer/control.

Every report includes snapped road segment, direction/bearing, location accuracy, severity, lane/side when relevant, creation/expiry, reporter pseudonymous ID, and moderation state.

Reports are ephemeral and confidence-weighted. Confidence combines:

- freshness decay;
- reporter reliability;
- location accuracy;
- direction match;
- independent confirmations/denials;
- spatial clustering;
- contradiction with observed fleet speeds.

One reporter cannot create a hard closure. Closures and wrong-way hazards require corroboration or trusted authority. Police reports are short-lived, do not identify individuals, and are presented as road context rather than evasion guidance.

ETA uses a bounded segment multiplier:

`effective_speed = baseline_speed × live_speed_factor × incident_factor`

The factors use robust aggregates (median/trimmed observations), minimum sample sizes, age decay, and floor/ceiling limits. The UI distinguishes map/provider traffic, MEET community evidence, and insufficient evidence. Completed trip segment times update rolling time-of-day baselines after privacy aggregation.

## Security and privacy

- Row-level security and RPC authorization for all trip mutations.
- Coarse public incident cells; exact reporter trails are private.
- Rate limits, duplicate suppression, impossible-speed checks, device/account reputation, and moderation.
- Telemetry sharing remains opt-in and category-specific.
- Safety reports and cancellation reasons cannot silently alter fare.

## Acceptance

- Coverage manifest proves inline binding for every proprietary component.
- Repair bundle tests prove traceable source IDs and prohibited-value handling.
- Stops preserve order and quote version.
- PIN cannot be replayed, brute-forced, or used by another driver.
- A concurrency test proves exactly one winning claim.
- Road reports expire, deduplicate, and require corroboration for hard closures.
- ETA tests prove stale/untrusted reports have bounded or zero effect.
- Passenger and driver Compose flows expose price, stops, payment method, PIN, and assignment feedback.
- Unit, database, parity, assemble, install, launch, foreground, and crash-log gates pass.

