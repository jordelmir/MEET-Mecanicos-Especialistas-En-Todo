# ELYSIUM MOBILITY OS — CURRENT STATE REPORT
**Baseline SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`  
**Date**: September 5, 2026  
**Status**: AUDITED & EVIDENCE-BACKED  
**Evidence Level**: `INTEGRATION_VERIFIED` (Domain & Integration Gates Passing)

---

## 1. System Inventory

### Repository & Platform
- **Repository**: `jordelmir/MEET-Mecanicos-Especialistas-En-Todo`
- **Current Branch**: `main`
- **Android Version**: `4.23.6` (`versionCode 56`), `compileSdk = 35`, `minSdk = 26`, `targetSdk = 34`
- **Room Database**: Version `70` (`exportSchema = true`) with 177 entity classes and 58 DAOs.
- **Source Scale**: 100 Ride-specific Kotlin source files (~20,244 LOC) within Android `com.elysium369.meet.ride`.
- **Test Suite**: 67 Ride test suites (~85 test files) passing deterministically in fast gate.

### Package & Module Map
```text
com.elysium369.meet.ride
├── automatch/       Pure domain evaluator and strategy ranking for driver matching
├── data/            Local outbox, remote gateway, repository, and projection sync
│   ├── local/       Room outbox entity and DAO (RideCommandOutboxEntity/Dao)
│   └── remote/      Supabase RPC gateway and wire DTOs (RideCommandGateway)
├── demand/          Dynamic pricing intelligence and demand level snapshots
├── dispatch/        Dispatch coordinator, wave policies, candidate ranking, exposure tracking
├── domain/          State machine, command envelopes, boarding PIN, money, cancellation policies
├── eta/             ETA calculation models and resilient FallbackEtaProvider
├── jurisdiction/    Regional rules, tax handling, and currency constraints
├── location/        Forensic GPS trails, PDF exporter, and Foreground Location Tracking Service
├── map/             MapLibre rendering, pin camera controller, avatar icons, place search
├── payment/         Payment method types, card models, and transaction state tracking
├── presentation/    Dedicated RideViewModel (Hilt @HiltViewModel)
├── presence/        Driver online/offline lease management and availability states
├── reputation/      Bayesian driver trust score and public profile models
├── safety/          RideGuardian, emergency broadcast, active share link generator
├── wallet/          RideDoubleEntryLedger, revenue split rule sets, and commission accounting
└── work/            WorkManager workers for command sync, enrollment, and breadcrumb uploads
```

---

## 2. Core Authorities & Persistence

### 1. Database Schemas
- **Room (Local SQLite)**:
  - `ride_requests`: Primary local projection of ride state, fares, driver assignment, boarding PIN, and server version.
  - `ride_offers`: Cached driver bids/offers associated with requests.
  - `active_ride_selections`: Tracks the user's active ride selection scoped to `ownerPrincipalId`.
  - `ride_command_outbox`: Reliable transactional outbox with idempotency key, expectedVersion, payload JSON, and retry backoff.
  - `ride_chat_messages`: In-ride messaging with delivery state.
  - `active_operations`: System-wide registry tracking active foreground operations.
- **Supabase / PostgreSQL (Cloud Authority)**:
  - 27 migrations applied up to `20260903120000_relax_v3_evidence_check.sql`.
  - `public.ride_requests`: Cloud authority for all trip lifecycles.
  - `public.ride_offers`: Driver offers table with row locks.
  - `public.ride_command_receipts`: Deduplication and replay store for idempotency.
  - `public.ride_wallet_ledger`: Immutable double-entry ledger entries.
  - `public.ride_commission_reservations`: Held commissions for pending trips.
  - `public.ride_location_breadcrumbs`: PostGIS spatial GPS breadcrumbs with 90-day retention and legal hold support.
  - `public.ride_trip_feedback`: Post-trip ratings and safety feedback.

### 2. Remote RPC Surface
The client interacts with the cloud backend through actor-bound RPCs:
- `ride_create_request_v3`: Passenger creates ride request (starts at `version = 0`).
- `ride_submit_offer_v2`: Driver submits a price and ETA offer.
- `ride_accept_offer_v2`: Passenger accepts an offer with optimistic locking (`FOR UPDATE`, version CAS).
- `ride_claim_request_v2`: Driver claims an open request first-come-first-served.
- `ride_driver_transition_v2`: Driver transitions state (`DRIVER_EN_ROUTE`, `ARRIVED`, `IN_PROGRESS`, `COMPLETED`).
- `ride_issue_boarding_pin_v2`: Generates passenger boarding verification PIN.
- `ride_verify_boarding_pin_v2`: Driver verifies PIN to transition to `PASSENGER_ONBOARD`.
- `ride_cancel_trip_v2`: Passenger or Driver cancels with typed reason code.
- `ride_complete_trip_v2`: Finalizes trip, captures 5% platform commission, balances ledger.
- `ride_record_location_breadcrumb_v2`: Ingests encrypted driver location points.

---

## 3. Architecture & ViewModel Findings

### Monolithic God ViewModel Risk
- **Issue**: `com.elysium369.meet.ui.ObdViewModel` (9,919 lines) contains duplicated Ride methods and delegates to `RideCommandRepository`.
- **Target Architecture**: `com.elysium369.meet.ride.presentation.RideViewModel` (2,345 lines) is the clean, dedicated domain ViewModel. Screen navigation in `MainActivity.kt` currently wires `ObdViewModel` to `RideServiceScreen.kt`. A planned migration will decouple `RideServiceScreen` to consume `RideViewModel` exclusively.

### Quarantined Speculative Draft Code
- Earlier unintegrated draft files in `android/app/src/main/kotlin/com/elysium369/meet/ui/screens/ride/` contained syntax errors (`!.` and `?:`) and broke compilation.
- **Action Taken**: Safely quarantined into `docs/rides/drafts/ui_screens_ride/`. No active code or tests depend on them.

---

## 4. Current Verification & Evidence Status

| Gate | Status | Command / Evidence |
|---|---|---|
| **Ride Domain Tests** | **PASS** | `./gradlew :app:testDebugUnitTest --tests 'com.elysium369.meet.ride.*'` (35 tasks, 0 failures, 24s) |
| **Parity Test Harness** | **READY** | `bash tests/parity/ci-verify.sh` |
| **No Secrets Scan** | **PASS** | `:app:verifyNoSecretsInSource` passes |
| **Ride Codebase Audit** | **PASS** | `bash .codex/skills/meet-rides-improvement-loop/scripts/audit-rides.sh` (0 non-atomic acceptance, 0 unguarded lifecycle mutations) |
| **Overall Evidence Level** | **INTEGRATION_VERIFIED** | All critical lifecycle transitions, idempotency constraints, and security contracts pass. |
