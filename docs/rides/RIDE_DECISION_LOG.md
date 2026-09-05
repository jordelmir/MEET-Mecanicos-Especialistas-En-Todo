# ELYSIUM MOBILITY OS — ARCHITECTURE DECISION LOG (ADR)
**Status**: AUTHORITATIVE RECORD V1  
**Baseline SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`  
**Governing Rule**: *Record every material architectural decision with context, alternatives, trade-offs, evidence, and revisit conditions.*

---

## ADR-001: PostgreSQL Row-Level Locking (`FOR UPDATE`) for Exclusive Ride Assignment

- **Date**: 2026-09-05
- **SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`
- **Context**: An exclusive ride can have at most one winning driver. Under simultaneous taps or network retries, concurrent transactions must be evaluated deterministically.
- **Alternatives Considered**:
  1. Redis distributed locks (Redlock).
  2. Optimistic concurrency control (OCC) only via CAS `version = version + 1`.
  3. PostgreSQL pessimistic row lock (`SELECT ... FOR UPDATE`) + version CAS.
- **Trade-offs**: Redis is fast but ephemeral; Redis loss or network partitions can lead to split-brain double assignment. OCC alone can result in high retry thrashing under intense contention.
- **Chosen Option**: **PostgreSQL `FOR UPDATE` + version CAS**. Guarantees ACID serialization at the single source of durable truth.
- **Evidence**: Verified in `RideAcceptanceConcurrencyRaceTest` across 25 iterations with 10 concurrent racing threads (100% win uniqueness, zero double assignment).
- **Revisit Condition**: If matching write throughput on a single shard exceeds 10,000 rides/second, partition via spatial geographic shards (PostGIS clusters).

---

## ADR-002: Double-Entry Balanced Ledger for Monetary Accounting & 5% Commission

- **Date**: 2026-09-05
- **SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`
- **Context**: Financial movements (fares, commissions, driver earnings, tips, refunds) cannot use mutable floating-point balances.
- **Alternatives Considered**:
  1. Single mutable `balance` column in user profile table.
  2. Append-only transaction log without balance constraints.
  3. Double-entry accounting ledger with immutable journal entries enforcing $\sum \text{Debits} == \sum \text{Credits}$.
- **Trade-offs**: Double-entry requires writing two postings per transaction and more storage, but completely eliminates unaccounted balance drift and fraud.
- **Chosen Option**: **Double-entry balanced ledger (`RideDoubleEntryLedger` / `ride_wallet_ledger`)**.
- **Evidence**: Verified in `RideDoubleEntryLedgerTest`. Captures 5% platform commission to sovereign account with zero discrepancy.
- **Revisit Condition**: None. Financial integrity standard is permanent.

---

## ADR-003: Transactional Outbox Pattern via WorkManager for Android Offline Reliability

- **Date**: 2026-09-05
- **SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`
- **Context**: Mobile devices experience sudden network dropouts, airplane mode transitions, and operating system process terminations.
- **Alternatives Considered**:
  1. Direct Ktor / Retrofit network calls inside ViewModels.
  2. In-memory queue with coroutine channels.
  3. SQLite Room transactional outbox drained by Android WorkManager.
- **Trade-offs**: Outbox adds local storage write before network transmission, but guarantees zero dropped user commands across process death.
- **Chosen Option**: **Room `ride_command_outbox` + `RideCommandSyncWorker`**.
- **Evidence**: Verified in `RideDeliveryTruthContractTest` and `RideCommandSyncWorker`. Survives activity recreation and app backgrounding.
- **Revisit Condition**: Revisit only if mobile OS deprecates WorkManager.

---

## ADR-004: MapLibre Native Vector Maps & OpenFreeMap over Proprietary Google Maps SDK

- **Date**: 2026-09-05
- **SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`
- **Context**: High operational cost and proprietary lock-in of commercial map APIs (Google Maps Platform).
- **Alternatives Considered**:
  1. Google Maps SDK with billing per tile/route request.
  2. MapLibre Native Android SDK with OpenFreeMap / PMTiles vector tiles.
- **Trade-offs**: MapLibre requires custom styling and local tile caching, but eliminates recurring per-MAU costs and enables complete offline vector maps.
- **Chosen Option**: **MapLibre Native + OpenFreeMap / Photon / OSRM**.
- **Evidence**: Documented in `docs/rides/FREE_MAPS_OPERATIONS.md`. Verified smooth rendering in `RideMapAvatarRenderer`.
- **Revisit Condition**: Revisit if open tile servers fail Costa Rica benchmark requirements for address precision.

---

## ADR-005: Decoupled Domain Entities: Identity vs Driver vs Vehicle vs Availability

- **Date**: 2026-09-05
- **SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`
- **Context**: Conflating "driver is registered" with "vehicle is inspected" or "driver is available" creates safety and compliance hazards.
- **Alternatives Considered**:
  1. Single boolean flag `isDriverOnline = true`.
  2. Multi-attribute state model with separate authorities: `ActivePrincipalKernel` (Identity), `PlatformTrustCenter` (Driver verification), `VehicleRepository` (Vehicle inspection), and `RidePresencePolicy` (Online availability).
- **Trade-offs**: More tables and validation checks, but impossible for an uninspected vehicle or suspended driver to be matched.
- **Chosen Option**: **Separate verification truths with fail-closed enforcement**.
- **Evidence**: Verified in `RideEligibilityPolicyTest` and `RideActorOwnershipContractTest`.
- **Revisit Condition**: Permanent safety invariant.
