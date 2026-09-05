# ELYSIUM FULFILLMENT OS — Master Architecture Specification

**Principle:** *"Rides = gramática de interacción; Cada Servicio = contrato de dominio especializado"* ("Same shell, different contract").  
**Law:** *"MORE CAPABILITY, FEWER COMPETING AUTHORITIES."*

---

## 1. System Overview & Core Philosophy

MEET / Elysium Vanguard unifies on-demand mobility, vehicle recovery, roadside assistance, mobile diagnostic repair, workshop maintenance, and parts delivery under a single automotive operating system.

Rather than fragmenting the user experience into disconnected mini-apps or forcing heterogeneous services into a single generic "Ride" or "Service" aggregate, MEET decouples **interaction grammar** from **domain authority**:

```text
┌─────────────────────────────────────────────────────────────┐
│                     ELYSIUM FULFILLMENT OS                  │
│                     (Presentation Shell)                    │
│                                                             │
│   Request → Configure → Match → Track → Execute → Settle    │
└──────────────────────────────┬──────────────────────────────┘
                               │
               Vertical Presentation Adapters
                               │
       ┌───────────┬───────────┼───────────┬───────────┐
       ▼           ▼           ▼           ▼           ▼
     RIDE         TOW        REPAIR     ROADSIDE     PARTS
   Authority   Authority   Authority   Authority   Authority
       │           │           │           │           │
       └───────────┴───────────┼───────────┴───────────┘
                               ▼
            SHARED ENTERPRISE FOUNDATION AUTHORITIES
       Identity · Vehicle · Evidence · Payments · Geo
```

### Key Rules
1. **SAME EXPERIENCE DOES NOT REQUIRE SAME DOMAIN**: Towing a damaged EV onto a flatbed with locked wheels is fundamentally distinct from transporting a passenger in a sedan. The domain models remain separate; the UX shell remains unified.
2. **ONE FACT, ONE AUTHORITY**:
   - `Ride`: Canonical authority for trips, vehicle passenger capacity, and ride fares.
   - `Tow`: Canonical authority for `TowJob`, `TowCapabilities`, `TowUnit`, and vehicle custody state machines.
   - `Evidence`: Canonical authority for cryptographic SHA-256 asset attestations.
   - `Vehicle`: Canonical authority for vehicle identity, specs, and telemetry.
   - `Identity`: Canonical authority for active principals and security scopes.
   - `Payments`: Canonical authority for financial ledgers and settlement.
   - `Fulfillment`: Read-only projection adapter and UI coordination shell. **Never an independent transactional authority.**

---

## 2. P0 Truth Repair Audit & Eradication of Synthetic Data

The initial audit revealed simulated logic inside production paths. All synthetic elements have been completely eradicated from production routes:

| Production Component | Previous State (Synthetic) | Current State (Authoritative) |
|---|---|---|
| `PassengerRideRequestScreen.kt` | `delay(3000)` simulating driver matching; hardcoded `Rodrigo Alvarado`, `BGH-409`. | Bound directly to `viewModel.activeRideRequest`, `passengerVerification`, and reactive repository state. Fail-honest empty states. |
| `DriverAppScreen.kt` | `delay(2000)` generating fake request for `Mariela Quesada`. | Bound to `viewModel.openRideRequests` and `viewModel.makeRideOffer(...)`. Uses verified driver credentials. |
| `ActiveRideTrackingScreen.kt` | Simulated Canvas gradient box and hardcoded route lines. | Driven by `CommonMapPanel` and reactive `CommonMapState`. If geo coordinates are unavailable, displays honest *"Mapa no disponible"*. |
| `MainActivity.kt:ride_active_tracking` | Inline hardcoded `ActiveRideViewState` instantiated in navigation routes. | Bound to `obdViewModel.activeRideRequest`. Displays honest empty state with action to request a ride if no trip is active. |

---

## 3. Architecture & Presentation Contracts

Located in package `com.elysium369.meet.fulfillment.domain`:

- **`FulfillmentReference`**: Immutable typed reference `(vertical: ServiceVertical, aggregateId: UUID, correlationId: UUID)`.
- **`FulfillmentMode`**: Operational modality:
  - `ON_DEMAND_MOBILE`: Rides, mobile mechanics, battery jumps.
  - `SCHEDULED_MOBILE`: Scheduled pre-purchase inspections.
  - `PICKUP_AND_DELIVERY`: Towing to workshop, parts courier.
  - `DROP_OFF`: Workshop visits.
  - `REMOTE`: Remote diagnostic consultations.
- **`FulfillmentPhase`**: Sealed UI presentation state:
  `Configuring` → `Searching` → `Offered` → `Matched` → `ProviderEnRoute` → `ProviderArrived` → `InProgress` → `Completing` → `Completed` | `Cancelled` | `Disputed` | `Failed`.
- **`FulfillmentPricing`**: Four-tier pricing model (`EstimatedRange`, `Quote`, `AuthorizedAmount`, `FinalSettlement`) using `Money` and `CurrencyCode`.
- **`FulfillmentPresentationAdapter<T>`**: Contract mapping domain aggregates to `FulfillmentProjection`.

---

## 4. Pilot Vertical Migration: Towing & Roadside

Located in package `com.elysium369.meet.core.services.tow`:

- **`TowCapabilities`**: Strongly typed physical equipment capabilities:
  - `FLATBED`, `WHEEL_LIFT`, `HEAVY_DUTY`, `MOTORCYCLE`, `EV_COMPATIBLE`, `LOW_CLEARANCE`, `LOCKED_WHEELS`, `NON_ROLLING_VEHICLE`, `ACCIDENT_RECOVERY`, `WINCH`, `OFFROAD_RECOVERY`, `UNDERGROUND_PARKING`.
- **`TowUnit`**: Physical towing truck specification (VIN, plate, equipment capabilities, max weight/length/clearance).
- **`TowCustodyCheckpoint`**: Pre-load and post-unload inspection record referencing canonical `EvidenceAttestation` SHA-256 hashes.
- **`TowJob`**: Authoritative domain aggregate tracking lifecycle through `TowStateEngine`.
- **`TowCommandRepository`**: Authoritative gateway validating transitions and actor permissions.

---

## 5. Unified Activity Hub

Located in package `com.elysium369.meet.fulfillment.ui`:

- **`UnifiedActivityScreen`**: Aggregates multi-service history (Rides, Tows, Repairs, Roadside, Parts) into a single chronological feed.
- Eliminates fragmented screens ("Mis viajes", "Mis grúas", "Mis reparaciones").
- Displays real-time status chips, provider initials, monetary settlement, and deep navigation to active jobs.

---

## 6. Verification Ladder & Automated Tests

All changes are strictly validated against automated verification gates:

1. **`ElysiumFulfillmentOsTest` (8/8 tests passed)**:
   - `towValidLifecycleProgressionTest`: Confirms valid `TowJob` transition sequence.
   - `towInvalidTransitionsFailClosedTest`: Confirms invalid transitions are rejected.
   - `towActorAuthorizationGuardsTest`: Confirms driver/customer role boundaries.
   - `towEvidenceRequiredForCustodyGuardsTest`: Confirms loading requires cryptographic evidence.
   - `towCapabilitiesEnumIntegrityTest`: Validates required tow capabilities.
   - `towFulfillmentAdapterProjectsCorrectPhasesTest`: Validates projection adapter mapping.
   - `rideFulfillmentAdapterProjectsActiveRideTest`: Validates ride projection mapping.
   - `productionRideRoutesHaveNoSyntheticRodrigoOrFakeDelaysTest`: Prohibits hardcoded drivers or fake delays across all ride screen files.
2. **`ProductionHardcodedActorGuardTest` (PASSED)**:
   - Verifies zero hardcoded actors across entire codebase.
3. **Cross-Runtime Parity Verification (`bash tests/parity/ci-verify.sh`)**:
   - TypeScript ≡ Kotlin SHA-256 byte-exact parity: `Cross-runtime parity OK`.
