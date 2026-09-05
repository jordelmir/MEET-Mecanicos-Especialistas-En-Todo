# FULFILLMENT HARDENING V2 — BASELINE AUDIT

**Target**: `cb2ed60b797beb9674a8e3e95749aa8fe6b2a1fc`  
**Branch**: `main` (synchronized with `origin/main`)  
**Date (UTC)**: 2026-09-05T23:05:00Z  
**Author / Committer**: `Jor / Elysium Vanguard Engineering`  

---

## 1. Git State Verification

```text
git rev-parse HEAD: cb2ed60b797beb9674a8e3e95749aa8fe6b2a1fc
git rev-parse origin/main: cb2ed60b797beb9674a8e3e95749aa8fe6b2a1fc
git status --short: (clean, 0 modified, 0 untracked)
```

### Recent Commits
1. `cb2ed60b` — `feat(fulfillment): harden truth boundaries, converge tow authority to Room, and enforce invariants`
2. `7ee8617f` — `docs(fulfillment): add architectural specification and ADRs 0012-0014 for Elysium Fulfillment OS`
3. `48a4056c` — `feat(fulfillment): implement Elysium Fulfillment OS, Tow pilot vertical, and P0 truth repair`
4. `6d6e15f6` — `docs(rides): document 10/10 verified production mobility and UI capabilities`
5. `ee023329` — `feat(home-mobility): add HomeActivityStripWidget and role-adaptive ride hero cards`
6. `a2ba499c` — `feat(mobility-ui): integrate modular ride screens, PTT voice widget, and turn-by-turn navigation`
7. `ce23ebdc` — `fix(kernel): ensure coroutine timing and case matching in domain kernel test suites`
8. `4ae82099` — `feat(kernel): action safety, authority, causal evidence and vehicle signal graph`
9. `5b3d8c2b` — `refactor(identity): extract ActivePrincipalProvider interface for dependency inversion`
10. `73d45036` — `feat(presence-ptt): Elysium Circles, Vanguard PTT, dual-boundary geofencing and monotonic floor control`

---

## 2. CI Workflows Associated with `cb2ed60b`

- `Realtime ERP/1 Integration Gates`: **SUCCESS**
- `Realtime Security & RLS Conformance`: **SUCCESS**
- `Pre-production guards`: **SUCCESS**
- `cross-runtime-parity`: **SUCCESS**
- `Diagnostic v2 release gates`: **IN_PROGRESS / SUCCESS**

---

## 3. Initial Proof State Matrix (at `cb2ed60b`)

| Capability | Baseline Status | Blocker / Limitation |
|---|---|---|
| **Tow Domain & UI** | `CLIENT_IMPLEMENTED` | `TowFulfillmentScreen` has static price (`₡18.000-₡24.000`), `8.7km`, and fallback `0.0, 0.0`. |
| **Tow Persistence** | `LOCALLY_PERSISTED` | Legacy Room entity collapses 9 active states to `TAKEN` -> rehydrates to `ASSIGNED`. Hardcodes `serverVersion = 1L`. Fabricates `TowUnit(isVerified = true)`. |
| **Tow Concurrency (CAS)** | `IN_MEMORY_CHECK_ONLY` | Check performed in memory; `expectedServerVersion` is optional (`Long? = null`); DB update is async without atomic CAS query. |
| **Tow Mutation Authority** | `SPLIT_AUTHORITY` | Dual writers: `TowCommandRepository` writes to DB, but `ObdViewModel` still contains direct DAO mutators (`createTowTruckRequest`, etc.). |
| **Tow Evidence & Custody** | `NONBLANK_CHECK_ONLY` | Requires nonblank string, permits synthetic hashes (`SHA256:FAKE`), no canonical Evidence ID, generates synthetic `custody_*` IDs. |
| **Ride Truth (Passenger)** | `TRUTH_HARDENED_PARTIAL` | `(0.0, 0.0)` for missing GPS, fallback `"Usuario MEET"`, `"+506 8000-0000"`, `dropoff ?: pickup`, `ride_${System.currentTimeMillis()}`. |
| **Ride Truth (Driver)** | `TRUTH_HARDENED_PARTIAL` | `(0.0, 0.0)` GPS, `estArrivalMin = 5`, local `isOnline` state, local `COMPLETAR` without domain command dispatch. |
| **Communications (PTT)** | `NOT_INTEGRATED` | Button-down directly asserts `TRANSMITTING` without floor grant or media session. |
| **Payments** | `NOT_INTEGRATED` | Payment dialog confirms and completes without payment provider authorization/capture. |
| **Safety Actions** | `DECORATIVE_CALLBACKS` | SOS, Guardian, and Share are empty lambdas `{ /* SOS */ }`. |
| **Financial Settlement** | `UNIT_VERIFIED_PARTIAL` | Total equals sum of parts, but uses raw Long arithmetic rather than overflow-safe `Money.plus()`. |

---

## 4. Master Order Waves to Execute

1. **Wave 0**: Authority Map V2 (`docs/architecture/FULFILLMENT-AUTHORITY-MAP-V2.md`).
2. **Wave 1**: Tow Storage Model V2 (`TowJobEntity`, exact `TowState` persistence, durable `serverVersion`, `requiredCapabilities`, `custodyRecordsJson`, Room v71 migration).
3. **Wave 2**: Atomic CAS at Room/DB level (`compareAndSwapState` query returning affected rows, mandatory `expectedVersion: Long`, per-job Mutex serialization).
4. **Wave 4**: Eliminate duplicate Tow mutators in `ObdViewModel` (redirect all calls to `TowCommandRepository`).
5. **Wave 5**: TowUnit real resolution (never forge verified units with requested capabilities).
6. **Wave 7**: Authoritative Evidence validation (validate 64-char SHA-256 hex, require canonical evidence, eliminate fake IDs, separate custody hash from ledger attestation).
7. **Wave 8**: Tow pricing truth (eliminate fake authorization ID, enforce overflow-safe `(base + extras) + taxes == total` with `Money.plus()`).
8. **Wave 9**: Tow request truth repair (eliminate `0,0`, fake UUID, fake phone/user/vehicle fallbacks, eliminate static `8.7km` / `₡18.000-₡24.000`).
9. **Wave 10**: Ride truth repair II (eliminate `0,0`, fake identity, `dropoff ?: pickup`, local `rideId`, introduce `RideState.UNKNOWN`).
10. **Wave 11**: Driver app hardening (eliminate `0,0`, `estArrivalMin = 5`, local `isOnline` authority, local completion).
11. **Wave 12-14**: Honest `FeatureAvailability` for PTT, Payments, and Safety.
12. **Wave 22**: Authoritative Room in-memory test suite proving exact state persistence across process restart, CAS stale version rejection, and evidence checks.
