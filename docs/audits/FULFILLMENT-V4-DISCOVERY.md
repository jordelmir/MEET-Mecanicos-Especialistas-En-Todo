# Fulfillment V4 Discovery Audit

## 1. Baseline Confirmation
- **Target Repository**: `jordelmir/MEET-Mecanicos-Especialistas-En-Todo`
- **Active Branch**: `main`
- **Audit Baseline Commit**: `87db2c5bad66d2fe9264a6d487d6955716b8fbd3`
- **Commit Title**: `feat(fulfillment): complete Tow Authority V2, Room v71 migration, atomic CAS and operational truth hardening`
- **Working Tree**: Clean, in-sync with `origin/main`.

## 2. Component & Architecture Inventory

### Tow Domain & Persistence
- **Room Entities**:
  - `TowJobEntity`: Storing all 37 domain columns, full `TowState` persistence without collapsing, `serverVersion`, `requiredCapabilities`, `custodyRecordsJson`.
  - `TowTruckRequestEntity` (legacy): Table `tow_truck_requests`, status enum (OPEN, TAKEN, COMPLETED, CANCELLED).
- **DAOs**:
  - `TowJobDao`: Primary V2 DAO with atomic SQLite `compareAndSwapState` updating `state` and incrementing `server_version = server_version + 1` with `WHERE job_id = :jobId AND server_version = :expectedVersion`.
  - `TowTruckDao`: Legacy DAO.
- **Repositories**:
  - `TowCommandRepository`: Enforces versioned optimistic locking with per-job `Mutex`, dual hydration from `TowJobDao` and `TowTruckDao`. Dual-writes to `TowTruckDao` slated for retirement.
- **State Machine**:
  - `TowStateEngine`: 14 exhaustive states (`REQUESTED`, `MATCHING`, `ASSIGNED`, `EN_ROUTE`, `ARRIVED`, `LOADING`, `LOADED`, `IN_TRANSIT`, `ARRIVED_DESTINATION`, `UNLOADING`, `DELIVERED`, `COMPLETED`, `CANCELLED`, `DISPUTED`).

### Room Database & Migrations
- **Current Version**: 71
- **Migration in AppModule**: `MIGRATION_70_71`
  - *Identified Discrepancy (P0)*: Index names generated were `idx_tow_jobs_*`, whereas Room schema `71.json` mandates `index_tow_jobs_*`.
  - *Identified Backfill Gap (P0)*: No data migration from legacy `tow_truck_requests` to `tow_jobs`.

### Backend / Supabase
- 76 existing migrations in `supabase/migrations`.
- Legacy `tow_requests` table exists from June 2026 (`20260629170000_vanguard_p0_foundation.sql`).
- Server authority for `tow_jobs` requires dedicated schema with PostGIS geography, idempotency receipts, outbox, and atomic assignment procedure (`tow_claim_job`).

### Truth Boundaries & Identified Semantic Residue
1. **Passenger Ride GPS Accuracy**: Hardcoded `pickupAcc = 5.0f` in `PassengerRideRequestScreen.kt`. Needs actual location accuracy or null.
2. **Driver Profile Defaults**: `driverRating = 0.0` and `driverTotalTrips = 0` in `DriverAppScreen.kt` when rating/trips are unknown. Must be nullable.
3. **Driver Navigation**: Hardcoded speed/maneuvers in navigation overlay when route authority is absent. Must reflect `FeatureAvailability.NotIntegrated`.
4. **Tow Creation Durability**: In `TowCommandRepository.requestTow`, job must be durably persisted before publishing state.
