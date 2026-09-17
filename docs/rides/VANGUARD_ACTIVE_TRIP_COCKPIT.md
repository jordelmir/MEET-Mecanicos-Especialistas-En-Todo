# MEET Vanguard Active Trip Cockpit — V8 Specification & Architecture

## 1. Baseline Freeze & Metadata

- **Frozen Git Baseline SHA**: `8e552dec52635a36cf8d9474a8bd7006046b4137`
- **Branch**: `main`
- **Release Target**: 4.24.0 (versionCode 57)
- **Status**: Implemented, Integrated, and Verified

---

## 2. Executive Summary & Philosophy

> *"Todo en uno. Siempre a más, nunca a menos. Al máximo nivel de la humanidad."*

The **MEET Vanguard Active Trip Cockpit** transforms the active driver trip into a world-class, production-grade operational cockpit. Navigation, turn-by-turn guidance, route matching, passenger interaction, safety controls, and mission-critical lifecycle transitions live in a single unified experience.

Critically, this is built **strictly on top** of MEET's distributed authority architecture without introducing any parallel state machines or optimistic state counterfeits:
1. **PostgreSQL / Supabase** is the sole authoritative state machine, assignment authority, and settlement ledger.
2. **Room** serves as the authoritative local projection (read model) and durable outbox for offline-first transactional mutations.
3. **Actor-bound RPCs** enforce strict Compare-And-Swap (CAS) with expected version numbers and UUID-backed idempotency keys.
4. **Vanguard Neon Navigation** renders the map visual presentation while decoupling business logic from underlying map and routing SDK providers.

---

## 3. High-Level Architecture Diagram

```
PostgreSQL (Authority)
   ├── RLS Policies & Actor Integrity
   ├── State Machine (ASSIGNED → DRIVER_EN_ROUTE → ARRIVED → PASSENGER_ONBOARD → IN_PROGRESS → COMPLETED)
   ├── CAS Version Validation (`expected_version`) & Idempotency Storage
   └── Authoritative Timestamps (`driver_arrived_at`, `completed_at`)
          │
          ▼
Supabase Realtime + RLS Catch-up Poll
          │
          ▼
Room Projection (`meet.db`)
   ├── `ride_requests` (Local Read Model Projection)
   └── `ride_command_outbox` (Durable Idempotent Outbox)
          │
          ▼
DriverActiveTripViewModel (UI State Coordinator)
   ├── Injected Repositories: RideDao, RideCommandBus, RideRoutingProvider
   ├── Concurrency Mutex: Double-tap/double-swipe physical protection
   ├── Routing Engine: OSRM `steps=true`, RideRouteMatcher (O(k)), RerouteDetector (Hysteresis)
   ├── Preflight Engine: Monotonic DriverArrivalPreflight (<15s age, <=50m acc, <=120m dist)
   └── Presence Isolation: `acceptingFutureOffers` decoupled from canonical active trip phase
          │
          ▼
StateFlow<DriverTripUiState> (Unidirectional Presentation Snapshot)
   ┌──────────────────────┴──────────────────────┐
   ▼                                             ▼
DriverActiveTripCockpit                       DriverTripDetailsSheet
(HUD Header, Map, Sliding Action, HUD Panel)   (Future Offers, Safety Share, Support, Cancel)
```

---

## 4. Key Subsystems & Implementations

### 4.1. Turn-by-Turn Guidance & Routing Engine
- **Maneuver Extraction (`steps=true`)**:
  - `RideRouting.kt` requests OSRM with `?overview=full&geometries=geojson&steps=true`.
  - Parses wire models `OsrmRouteWire`, `OsrmLegWire`, `OsrmStepWire`, and `OsrmManeuverWire` into domain `RideRouteManeuver`.
- **Localized Presentation (`RideManeuverFormatter`)**:
  - Maps semantic maneuver types and modifiers to clean visual glyphs (`↰`, `↱`, `↑`, `↺`, `🏁`) and distance strings (`34 m`, `2.4 km`).
- **Efficient Map Matching (`RideRouteMatcher`)**:
  - Employs a local window cursor (`localWindow = 80` segments) with equirectangular projection to achieve $O(k)$ average complexity per GPS tick, falling back to full geometry search only when off-track.
- **Reroute Hysteresis (`RerouteDetector`)**:
  - Triggers reroute only after 3 consecutive samples exceeding 35m off-route with <= 25m accuracy and respects a 10s cooldown.

### 4.2. Arrival Preflight & Outbox Command Durability
- **Dual-Clock Freshness Evaluation (`DriverLocationSample`)**:
  - Captures civil time (`capturedAt: Instant`) for server-side audit logs and monotonic time (`capturedAtElapsedRealtimeNanos: Long`) for local freshness checks immune to wall-clock manipulation.
- **Preflight UX Gate (`DriverArrivalPreflight`)**:
  - Rejects arrival attempts if location age > 15s, GPS accuracy > 50m, or Haversine distance to pickup > 120m.
  - Preflight is an instantaneous client-side UX guard; the server RPC `ride_driver_arrived_v3` executes authoritative spatial and version validation.
- **Durable Outbox Bus (`DefaultRideCommandBus`)**:
  - Commands (`RideQueuedCommand`) use stable ULID/UUID idempotency keys and decimal canonical string coordinates (`BigDecimal.toPlainString()`).
  - Strict adherence to rule: **`OUTBOX PENDING != SERVER SUCCESS`**. The cockpit displays `"Confirmando..."` until Room's `serverState` reflects server ACK.

### 4.3. Boarding PIN & Active Trip Progression
- **Two-Step Boarding**:
  - Arrival transitions trip to `ARRIVED` (visually presented as "Esperando al pasajero" with wait timer derived from authoritative `driver_arrived_at`).
  - Driver cannot advance directly from `ARRIVED` to `IN_PROGRESS`.
  - Boarding PIN is verified via `VERIFY_BOARDING_PIN` (`ride_verify_boarding_pin_v2`).
  - Trip start is enabled only when server projection reports `PASSENGER_ONBOARD`.
- **Dynamic Waypoint Switching**:
  - Once onboard and trip starts, navigation switches route from `driverLocation -> pickup` to `driverLocation -> intermediateStops -> destination`.

### 4.4. Driver Presence & Availability Separation
- The active trip status (`DriverTripPhase`) is completely isolated from driver offer availability (`acceptingFutureOffers`).
- Drivers can toggle "Dejar de aceptar viajes" at any time during an active trip without modifying, cancelling, or conflicting with the current ride.

### 4.5. Privacy-Safe Safety Share Sessions
- "Compartir seguimiento" generates a `ride_share_sessions` record.
- Generates a 256-bit CSPRNG token; stores only the SHA-256 digest in the database.
- Public link structure: `/trip-share/<opaque_token>` with TTL, avoiding trip ID enumeration or PII disclosure. Zero raw phone number exposure (`tel:` links strictly forbidden).

### 4.6. Process Death Recovery
- The cockpit does not rely on transient Compose state.
- Upon process death and relaunch, the cockpit initializes from Room's `RideRequestEntity`, joins the authoritative request flow, recovers route guidance, and restores UI without state divergences.

---

## 5. Verification Matrix & Test Evidence

| Test Class | Scope / Invariant Verified | Status |
|---|---|---|
| `DriverArrivalPreflightTest` | Stale GPS rejection, poor accuracy rejection, distance threshold enforcement | **PASS** |
| `DriverTripPhaseTest` | Canonical server status mapping to Presentation Phase without fictitious states | **PASS** |
| `RideRoutingManeuverParserTest` | Turn-by-turn step parsing from OSRM JSON, coordinate validity, glyph resolution | **PASS** |
| `RideRouteMatcherTest` | Cursor window segment projection, along-track distance, reroute hysteresis & cooldown | **PASS** |
| `DriverActiveTripViewModelTest` | Intent dispatching, command generation, preflight gates, presence isolation, PIN gate | **PASS** |
| `RideCommandIdempotencyTest` | Canonical decimal coordinates, idempotency key stability, CAS version monotonicity | **PASS** |
| `DriverTripProcessRecoveryTest` | Full recovery from Room projection across process death, intermediate stops preservation | **PASS** |
| `verify-ride-android-authority.sh` | Outbox PK, actor identity, OSRM `steps=true`, cockpit isolation from direct RPC imports | **PASS** |

### Execution Commands
```bash
# Static Authority Gate
bash tests/ride/verify-ride-android-authority.sh

# Cockpit & Routing Unit Tests
cd android && ./gradlew testDebugUnitTest --tests "com.elysium369.meet.ride.driver.*"

# Comprehensive Ride Test Suite
cd android && ./gradlew testDebugUnitTest --tests "com.elysium369.meet.ride.*"
```
