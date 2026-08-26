# Elysium Vanguard Mobility Exchange — Technical Specification & Architecture V2

## Overview

The **Elysium Vanguard Mobility Exchange** is a server-authoritative, verifiable, geospatial mobility platform built directly upon the MEET ecosystem. It transforms previous ride-hailing dynamics into a high-scale, cryptographically provable market governed by the core doctrine:

> **"NO FAKE DRIVER → NO FAKE VIEW → NO FAKE ETA → NO FAKE TRUST → NO FAKE PAYMENT → NO FAKE SAFETY"**

---

## 1. System Architecture

```
                             ELYSIUM VANGUARD MOBILITY
                        ┌─────────────────────────────────────────┐
                        │          Android Application            │
                        │       RideViewModel (Aggregate VM)      │
                        │        StateFlows + Action APIs         │
                        └───────────────────┬─────────────────────┘
                                            │ (Hilt Injected Gateways)
                        ┌───────────────────▼─────────────────────┐
                        │       PostgreSQL + PostGIS (SOT)        │
                        ├─────────────────────────────────────────┤
                        │ • ride_driver_presence (GiST)           │
                        │ • ride_dispatch_waves                   │
                        │ • ride_request_exposures                │
                        │ • ride_driver_public_profiles           │
                        │ • ride_driver_compliments               │
                        │ • ride_driver_reputation_snapshots      │
                        │ • ride_auto_match_policies              │
                        │ • ride_eta_observations                 │
                        │ • ride_location_breadcrumbs             │
                        │ • ride_next_job_reservations            │
                        │ • ride_demand_snapshots                 │
                        │ • ride_payment_intents & events         │
                        │ • ride_safety_signals (Guardian)        │
                        │ • ride_jurisdiction_policies & frozen   │
                        └─────────────────────────────────────────┘
```

---

## 2. Core Functional Pillars (Phases 0 to 15)

### A. Dispatch Engine, Presence & Seen Exposures (Fases 0–3)
- **Architectural Isolation**: Extracted ride logic into `@HiltViewModel RideViewModel`.
- **PostGIS Presence**: Table `ride_driver_presence` with `geography(point, 4326)` GiST index and 11 availability states (`OFFLINE`, `AVAILABLE`, `OFFERING`, `RESERVED`, `FINISHING_CURRENT_TRIP`, `EN_ROUTE_TO_PICKUP`, `PICKUP_WAITING`, `IN_TRIP`, `PAUSED`, `SUSPENDED`, `STALE`).
- **Adaptive Sampling**: `RideLocationSampler` scales GPS cadence dynamically (2s during trip, 8s when idle).
- **Sequence & Teleportation Guard**: `RideLocationSequenceGuard` rejects stale sequence IDs, time travel, and physically impossible speed jumps ($> 300\text{ km/h}$).
- **Exposure Truth**: Table `ride_request_exposures` and `RideExposureTracker` enforce dwell time ($\ge 50\%$ viewport card area for $\ge 500\text{ms}$) before emitting `ACK_REQUEST_SEEN`.

### B. Public Driver Profile & Reputation Truth (Fase 4)
- **Safe Projections**: `ride_driver_public_profiles` exposes only necessary verification badges (`identity`, `license`, `vehicle`, `liveness`, `insurance`, `background`).
- **Closed Compliment Taxonomy**: 8 strict compliments (`COURTEOUS`, `SAFE_DRIVING`, `FAST_PICKUP`, `CLEAN_VEHICLE`, `GOOD_COMMUNICATION`, `GOOD_NAVIGATION`, `HELPFUL`, `PROFESSIONAL`) only assignable by verified passengers of `COMPLETED` trips.
- **Bayesian Rating & Confidence**: `DriverTrustEngine` calculates smoothed ratings:
  $$\bar{R} = \frac{C \cdot m + \sum r_i}{C + n}, \quad C = 10.0, \; m = 4.80$$
  with statistical sample confidence $1 - e^{-n/50}$.
- **Institutional Trust Tiers**: `VERIFIED`, `TRUSTED`, `ELITE`, `VANGUARD DRIVER`.

### C. Transactional Auto-Match Engine (Fase 5)
- **Zero Double-Assignment**: Server-side RPC `ride_try_auto_match_v1` with row-level `FOR UPDATE` transaction locks on `ride_requests` and `ride_offers`.
- **Supported Strategies**:
  - `FASTEST_PICKUP`: Ranks primarily by lowest ETA.
  - `LOWEST_FARE`: Ranks primarily by lowest offered price.
  - `HIGHEST_TRUST`: Ranks by trust tier and Bayesian score.
  - `BALANCED`: Weighted multi-criteria optimization function.

### D. Live Location & Traffic ETA (Fases 6, 7 & 8)
- **Multi-Provider Routing**: `GoogleTrafficEtaProvider` with graceful fallback to `FallbackEtaProvider` (1.3x urban winding factor + 25 km/h urban speed).
- **Provenance Truth**: UI explicitly displays origin tags (`"~3 min (Tráfico en tiempo real)"` vs `"~4 min (Estimado geométrico)"`).
- **Visual Smoothing**: `MapLocationInterpolator` smoothly rotates and animates vehicle movement with a strict $\le 15\text{s}$ staleness cutoff (stops animating if signal is lost; never fabricates fake motion).

### E. Chained Dispatch / Next-Job Scheduler (Fase 9)
- **Two-Stage Queue**: `NEXT_JOB_RESERVED` $\to$ `NEXT_JOB_ACTIVE`.
- **Absolute Privacy Isolation**: The incoming passenger receives available arrival ETAs but **zero** information about the previous passenger's location, route, destination, or identity.
- **Delay Monitor**: `NextJobScheduler` evaluates delays against tolerance thresholds ($300\text{s}$) to trigger passenger re-selection options.

### F. Advanced Mobility Stack (Fases 10 to 15)
- **Demand Intelligence**: Table `ride_demand_snapshots` classifies H3 resolution 8 cells (`NORMAL`, `BUSY`, `HIGH`, `CRITICAL`, `UNKNOWN`) based on verifiable open requests / available drivers ratios.
- **Pricing Intelligence**: `RidePricingIntelligence` computes recent market percentile ranges (`P10` to `P90`, e.g. `₡2.900 – ₡3.400`) to guide passengers while keeping `OPEN_BID` negotiation human-controlled.
- **SINPE Payments Attestation**: `RidePaymentStatus` enforces financial truth (`PAYMENT_METHOD_SELECTED`, `PAYMENT_REQUESTED`, `USER_MARKED_SENT`, `DRIVER_MARKED_RECEIVED`, `EXTERNAL_SETTLEMENT_ATTESTED`, `BANK_CONFIRMED`, `DISPUTED`). Selecting SINPE is not proof of settlement; only `BANK_CONFIRMED` is final.
- **Guardian Mobility & Safety**: `GuardianRideMonitor` observes route deviations ($> 45^\circ$ for $> 60\text{s}$), unexpected stops ($> 180\text{s}$ stationary), extreme speeds ($> 150\text{ km/h}$), and severe crash deceleration ($> 4g$).
- **Jurisdiction Policy Engine (Costa Rica)**: Frozen regulatory rules (`ride_trip_legal_snapshots`) attach immutable legal version IDs to each trip for lifetime auditing.

---

## 3. Database Migration Sequence

| Migration File | Description |
| :--- | :--- |
| `20260825010000_ride_driver_presence.sql` | PostGIS presence table, GiST index, availability RPCs |
| `20260825020000_ride_dispatch_waves.sql` | Dispatch waves table, candidate discovery RPCs |
| `20260825030000_ride_request_exposures.sql` | Viewport seen acknowledgement RPCs and seen counter |
| `20260825040000_ride_driver_reputation.sql` | Public driver profiles, closed compliments, Bayesian rating |
| `20260825050000_ride_auto_match.sql` | Auto-match policy table and transactional matching RPC |
| `20260825060000_ride_eta_and_breadcrumbs.sql` | ETA observations table and durable location breadcrumbs |
| `20260825070000_ride_next_job_reservations.sql` | Next-job reservations with privacy projection RPC |
| `20260825080000_ride_demand_pricing_payment_guardian_jurisdiction.sql` | H3 demand snapshots, payment intents, safety signals & frozen legal policies |

---

## 4. Verification & Testing

### Automated Unit Test Suites (17 Classes, 100% Green)
```bash
./gradlew testDebugUnitTest --tests "com.elysium369.meet.ride.*"
```
- `RideDemandLevelTest`
- `RidePricingIntelligenceTest`
- `RidePaymentStatusTest`
- `GuardianRideMonitorTest`
- `JurisdictionPolicyTest`
- `NextJobSchedulerTest`
- `NextJobPrivacyProjectionTest`
- `RideEtaEstimateTest`
- `FallbackEtaProviderTest`
- `MapLocationInterpolatorTest`
- `RideAutoMatchEvaluatorTest`
- `DriverTrustEngineTest`
- `DriverTrustTierTest`
- `RideLocationSequenceGuardTest`
- `RideEligibilityPolicyTest`
- `RideLocationSamplerTest`
- `RideExposureTrackerTest`

### Cross-Runtime Parity Verification
```bash
bash tests/parity/ci-verify.sh
```
Output:
```
=== TS parity ===
[OK] P0230 fuel-pump request (Hyundai Accent Verna 2005)
=== Kotlin parity ===
=== Compare ===
TS and Kotlin produced identical output. Cross-runtime parity OK.
```
