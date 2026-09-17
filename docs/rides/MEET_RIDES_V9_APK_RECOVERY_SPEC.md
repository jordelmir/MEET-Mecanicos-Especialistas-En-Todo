# MEET RIDES V9 — ARCHITECTURE SPECIFICATION & RECOVERY CONTRACT
**Status**: PERMANENT ENGINEERING STANDARD (V9)  
**Governing Standard**: *Distributed Authority, Zero Optimistic Mutations, Zero Telemetry Fabrication, Zero Debug Bypasses.*  
**Applicability**: All AI Agents (Codex, Mavis, Google Antigravity, future automations) and Human Engineers.

---

## 1. Executive Summary & Problem Diagnosis

Prior to V9, the Android APK suffered from a critical race condition where touching "ACEPTAR VIAJE" caused the UI to temporarily display the ride as accepted for ~1 second, after which it disappeared.

### The Root Cause: Local Optimistic Synthesis
```text
[LEGACY BUGGY PATH]
User taps "ACEPTAR"
  ├── Enqueue CLAIM command in outbox
  ├── LOCAL Room Mutation: status = "ACCEPTED", selectActiveRide(trip), driverModeTab = 1
  ├── UI displays ACTIVE TRIP screen (synthetic truth)
  │     ... 1 second later ...
  ├── WorkManager executes ride_claim_request_v2
  ├── Backend rejects (e.g., VERSION_CONFLICT, ALREADY_ASSIGNED, or VEHICLE_NOT_VERIFIED)
  ├── Server snapshot projected to Room: status = "OPEN" / "CLAIMED_BY_OTHER"
  └── UI reverts: synthetic active ride disappears!
```

Additionally, 1-touch acceptance was launching both `SUBMIT_OFFER` and `CLAIM` concurrently, competing on the same `expectedVersion` and causing self-inflicted `VERSION_CONFLICT` rejections.

---

## 2. The Inviolable Core Architecture (V9 Standard)

```text
               USER INTENT (UI Tap)
                        ↓
            Validate Prerequisites Locally
      (Real GPS available? Verified vehicle active? User authenticated?)
                        ↓
             Room Transactional Outbox
            (Durable RideCommandEntity)
                        ↓
               Android WorkManager
            (RideCommandSyncWorker)
                        ↓
          Backend Authoritative RPC (PostgreSQL)
      (Row lock FOR UPDATE + Version CAS + Ledger Reservation)
                        ↓
                   Server ACK
            (v_expected_version == current_version)
                        ↓
              Room Remote Projection
          (Authoritative snapshot from server)
                        ↓
                 Reactive Room Flow
           (rideRequests StateFlow in ObdViewModel)
                        ↓
             UI Truth Promotion (Won)
      (serverVersion > 0 && assignedDriverId == actorId)
```

**Cardinal Rule**: Under NO circumstances may any composable, ViewModel, or repository modify `status = "ACCEPTED"` or select an active ride until the server has acknowledged the assignment and Room has projected it with `serverVersion > 0`.

---

## 3. The `RideClaimUiState` State Machine

To provide clear feedback to the driver without synthetic mutations, the UI observes a dedicated `StateFlow<RideClaimUiState>`:

```kotlin
sealed interface RideClaimUiState {
    data object Idle : RideClaimUiState
    data class Pending(val requestId: String) : RideClaimUiState
    data class Won(val requestId: String) : RideClaimUiState
    data class Rejected(val requestId: String, val code: String, val message: String) : RideClaimUiState
}
```

### Transition Invariants
1. **Idle → Pending(requestId)**:
   Triggered when `claimRideFirstCome(requestId)` successfully enqueues the `CLAIM` command in the Room outbox.
   - UI Action: Button displays `"CONFIRMANDO…"`, is disabled, and shows a circular loading indicator.

2. **Pending(requestId) → Won(requestId)**:
   Triggered **ONLY** by the projection collector in `ObdViewModel`:
   ```kotlin
   val isAssignedToMe = ride.assignedDriverId != null && ride.assignedDriverId in actorIds
   val isConfirmedByServer = ride.serverVersion > 0L
   val isValidActiveStatus = ride.status in listOf(
       "ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED", "PASSENGER_ONBOARD", "IN_PROGRESS"
   )
   if (isAssignedToMe && isConfirmedByServer && isValidActiveStatus) {
       _rideClaimUiState.value = RideClaimUiState.Won(ride.requestId)
       selectActiveRide(ride)
   }
   ```

3. **Pending(requestId) → Rejected(requestId, code, message)**:
   Triggered when `RideCommandSyncWorker` records a terminal failure (`RideCommandGatewayResult.Rejected`) and `recentFailures()` emits it to `ObdViewModel`.
   - Error mapping:
     - `VERSION_CONFLICT` → *"El viaje cambió mientras lo aceptabas. Actualizando."*
     - `ALREADY_ASSIGNED` → *"Otro conductor obtuvo este viaje primero."*
     - `VEHICLE_NOT_VERIFIED` → *"Tu vehículo activo aún no está VERIFIED."*
     - `INSUFFICIENT_BALANCE` → *"Saldo insuficiente para la reserva requerida."*
     - `AUTH_SESSION_MISMATCH` → *"Tu sesión cambió. Vuelve a iniciar sesión."*

---

## 4. Separation of Concerns: CLAIM vs SUBMIT_OFFER

- **Accepting Published Price**: Executes **ONLY** `CLAIM` (`RideCommandType.CLAIM`).
- **Proposing Counter-Price**: Executes **ONLY** `SUBMIT_OFFER` (`RideCommandType.SUBMIT_OFFER`).
- A single user touch must **NEVER** dispatch both `SUBMIT_OFFER` and `CLAIM`.

---

## 5. Prohibition of Fabricated Data & Telemetry

1. **Zero GPS Fabrication**:
   - Never use passenger pickup coordinates as driver location (`currentGps ?: pickupLatitude`).
   - If GPS signal is not yet fixed, the action is blocked: *"Se requiere señal GPS real para ofertar"*.
2. **Zero Vehicle Fabrication**:
   - Never invent default fallback vehicles (`"Toyota Corolla 2018 Gris"`, `"MEET-001"`).
   - Vehicle metadata must be derived strictly from `activeDriverVerification` (`activeVerifiedRemoteVehicleId()`).
3. **Zero Reputation Fabrication**:
   - Never inject hardcoded ratings (`driverRating = 5.0`) or trip counts (`driverTotalTrips = 15`).

---

## 6. Zero Debug Bypasses Policy

`BuildConfig.DEBUG` is permitted for:
- Logging diagnostic telemetry.
- Diagnostic overlays and developer tooling.
- StrictMode instrumentation.

`BuildConfig.DEBUG` is **STRICTLY PROHIBITED** from:
- Bypassing version CAS checks (`request.serverVersion <= 0L`).
- Bypassing verified vehicle requirements (`remoteVehicleId == null`).
- Bypassing boarding PIN verification (`verifyRideBoardingPin`).
- Mutating Room statuses (`ARRIVED`, `IN_PROGRESS`, `COMPLETED`) without server authority.
- Allowing self-ride claiming (`allowSelfRide = false` permanently).

---

## 7. Pure Driver Feed Eligibility

Driver open rides calculation is centralized in `com.elysium369.meet.ride.driver.RideDriverFeedPolicy.eligibleRides`:
```kotlin
fun eligibleRides(
    rides: List<RideRequestEntity>,
    actorIds: Set<String>,
    activeRideId: String?,
    hiddenRideIds: Set<String>,
    nowEpochMs: Long,
): List<RideRequestEntity>
```
Both `RideServiceScreen` (Dashboard) and `RideCenterScreen` MUST use this exact function. This guarantees that `Dashboard availability == RideCenter availability` at all times.

---

## 8. Verification & Parity Gates

Before committing any change to Rides, all agents must verify that:
1. `tests/ride/verify-ride-android-authority.sh` passes (`PASS`).
2. `tests/parity/ci-verify.sh` passes (exact byte parity between TS and Kotlin).
3. `./gradlew testDebugUnitTest` passes (2,077+ unit tests, 0 failures).
4. `./gradlew assembleDebug` succeeds.
