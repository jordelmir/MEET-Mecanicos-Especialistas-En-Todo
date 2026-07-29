# Viajes quality contract

## Non-negotiable truth

- Never fabricate drivers, passengers, trips, GPS, ETA, traffic, ratings,
  income, spend, DTC history, telemetry, balances, or successful payments.
- Mark unavailable, stale, pending, locally estimated, and provider-derived
  values explicitly.
- Share mechanical history or telemetry only with specific, voluntary,
  revocable consent and a visible freshness/source label.

## Lifecycle and money

- One request can have at most one winning driver; acceptance must be atomic
  and idempotent server-side before production claims.
- A ride starts only after the correct passenger PIN and authorized lifecycle
  transition.
- Added stops remain ordered and visible to both actors before acceptance.
- Fare, adjustments, payment method, commission, and final breakdown use
  integer minor units and immutable ledger entries.
- Capture the 5% platform commission only after a completed ride. Release any
  reservation on cancellation or failure.
- Promotional credit and purchased credit remain distinguishable.

## Passenger and driver parity

- Present actor-specific cancellation reasons and consequences.
- Keep destination, stops, payment method, fare, safety actions, and trip state
  consistent for both actors.
- Never block access to captured history, support, or evidence merely because a
  verification signal is missing.

## Maps and traffic

- Place suggestions must expose provider/loading/empty/error states and use
  real coordinates.
- Traffic reports require location, direction when applicable, timestamp,
  confidence, expiry, and abuse controls.
- ETA adjustments may use only fresh, geographically relevant observations;
  show when routing is estimated or unavailable.

## Required evidence

- Domain tests for lifecycle, money, privacy, cancellation, PIN, dispatch, and
  route/stops behavior affected by the change.
- Full Android unit tests, lint, assembly, and TS/Kotlin parity before merge.
- Install, launch, foreground/process, and crash-log proof when a real Android
  device is available.

## Resource budget

- This development Mac has 8 GB RAM. Keep Gradle at one worker, no parallel
  tasks, no persistent daemon, and a maximum 2304 MB JVM heap.
- Run one heavyweight Gradle gate at a time and stop compiler/Gradle daemons
  after verification.
- Reuse incremental outputs and the compact audit script before rescanning the
  source tree.
