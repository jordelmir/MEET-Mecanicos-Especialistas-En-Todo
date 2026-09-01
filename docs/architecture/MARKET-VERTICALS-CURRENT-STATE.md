# Market verticals: current authority and verification

This document describes the current implementation in the same terms used by
the product. A visible screen is not evidence that its backend, transport, or
physical integration has been verified.

## Communications

- Authority: `ElysiumCommunicationRepository`, owner-scoped Room projections,
  Android Keystore local encryption, Supabase communication contracts and the
  configured call transport.
- Working in software: identities/privacy, contact discovery, blocks, service
  conversation links, encrypted local messages, reply references, local
  in-conversation search, calls with truthful transport states, and private
  voice-note capture that survives destination navigation.
- Partial: voice notes remain `LOCAL_ONLY` until an interoperable remote E2EE
  envelope and attachment uploader are configured. Local persistence is not
  labelled delivered, and call signalling is not labelled media transport.
- Missing integration proof: two-device delivery/read receipts, remote media,
  push notifications, group administration and physical LiveKit calls.
- Competing legacy surfaces: fleet chat and ride chat still have domain-specific
  projections. New cross-domain links use Communications; migration of captured
  legacy history must preserve it rather than deleting it.

## Properties

- Authority: Market OS property tables/RPCs and `PropertyDomain`; the Android
  hub is a privacy-filtered projection, not an ownership registry.
- Working in software/backend contracts: typed operations and states, per-claim
  Property Passport truth, idempotent/versioned listing and inquiry commands,
  protected exact-address grants, due-diligence entry into Legal, and a
  Communications entry point.
- Partial UI: browsing/projection, truth claims and links are present. Listing
  creation, document capture, timeline and closing workflows are not all exposed
  in the Android hub.
- Never inferred: a listing is not verified ownership; a message is not a
  transaction; `SOLD/CLOSED` requires the authoritative workflow.

## Fuel Rewards

- Authority: server reward ledger/coupon projection. The client cannot mint or
  directly mutate a balance.
- Working in software: crash-safe empty/offline rendering, QR scanner created
  only after explicit intent, durable local purchases, integer minor money,
  millilitres, optional odometer evidence, derived consumption only from a
  positive recorded distance, and server-confirmed reward history.
- Partial: receipt OCR is explicitly not executed; station comparison and
  remote transaction reconciliation require provider/server evidence.
- Never inferred: OCR is not verification, a declared purchase does not create
  rewards, and missing odometer/distance does not become zero consumption.

## Ride

- Authority: versioned commands, Room outbox, sync worker and server projection.
  Realtime only wakes reconciliation; it does not decide business state.
- Working in software: lifecycle/role policies, atomic local offer claim,
  expected-version commands, ordered stops, boarding PIN policy, money in minor
  units, safety/evidence contracts, map provider states and durable projections.
- Continuity: leaving the Ride destination no longer terminates Realtime. The
  explicitly opened ride is stored owner-scoped and restored from Room; an
  empty refresh cannot select a replacement or erase that pointer.
- Partial integration proof: live dispatch, two actors, GPS sequence, payment,
  and physical trip execution still require a real multi-device/backend test.
- Competing code: `RideViewModel` exists but the current navigation route uses
  the root `ObdViewModel` Ride facade. It must not become a second command
  authority; future extraction should move both callers to one domain facade.

## Verification labels

- `SOFTWARE_VERIFIED`: deterministic local tests/builds passed.
- `INTEGRATION_VERIFIED`: the actual Supabase/provider path passed remotely.
- `PHYSICALLY_VERIFIED`: the required phone, adapter, ECU or multi-device path
  passed on real hardware.

No section advances to a stronger label merely because it compiles.
