# ELYSIUM MOBILITY OS — MASTER AUTHORITY REGISTRY
**Status**: AUTHORITATIVE REGISTRY V1  
**Baseline SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`  
**Prime Directive**: *More capability. Fewer authorities. One business fact → one durable authority.*

---

## 1. Authority Registry Matrix

| Capability | Current authority | Storage | Mutation path | Projection/cache | Confidence | Target action |
|---|---|---|---|---|---|---|
| **Identity** | Supabase GoTrue Auth | Postgres `auth.users` | Supabase Auth API (`auth.signUp`, `auth.signIn`) | `io.github.jan.supabase.gotrue.SessionStatus` | PRODUCTION_VERIFIED | Preserve as single root identity authority. Never create parallel accounts. |
| **Principal** | `ActivePrincipalKernel` | SharedPreferences (`PrincipalProvisioningStore`) + Memory | `SessionStatus.toPrincipal()` / GoTrue auth event | `StateFlow<ActivePrincipal>` in memory | PRODUCTION_VERIFIED | Derive all client commands from `activePrincipal.id`. Fail closed on anonymous/ambiguous. |
| **Rider** | Supabase Auth + Passenger Verification | Postgres `public.ride_passenger_verifications` | `PlatformTrustCenterGateway` -> `meet_submit_service_verification_v3` | Room `passenger_verifications` | INTEGRATION_VERIFIED | Maintain passenger capability grant bound to principal ID. |
| **Driver** | Supabase Auth + Driver Verification | Postgres `public.ride_driver_verifications` + `public.provider_profiles` | `PlatformTrustCenterGateway` -> `meet_submit_service_verification_v3` | Room `driver_verifications`, `provider_profiles` | INTEGRATION_VERIFIED | Enforce reviewable document requirements and MFA admin decisions. |
| **Verification** | Platform Trust Center | Postgres `public.service_verifications` + Private Storage `trust-verification-evidence` | `PlatformTrustCenterGateway.submit` -> `meet_submit_service_verification_v3` | Room `ServiceVerificationEntity` | INTEGRATION_VERIFIED | Enforce authenticated download and evidence manifest checksums. |
| **Vehicle** | `VehicleRepository` / Elysium Vehicle Domain | Postgres `public.vehicles` + Room `vehicles` | `VehicleRepository.saveVehicle()` | Room `VehicleEntity` (local projection) | PRODUCTION_VERIFIED | Reuse existing Elysium Vehicle domain. Do not create second vehicle aggregate. |
| **Vehicle Eligibility** | Postgres Trigger / Stored Procedure | Postgres `public.ride_driver_vehicles` | `ride_upsert_driver_vehicle_v1`, `ride_guard_dispatch_vehicle()` | Room `ride_driver_vehicles` (in-memory summary) | INTEGRATION_VERIFIED | Require active inspection/SOAT/Dekra before allowing driver online. |
| **Ride Request** | Postgres `ride_requests` table | Postgres `public.ride_requests` | `RideCommandRepository` -> Outbox -> `ride_create_request_v3` | Room `ride_requests` (`RideRequestEntity`) | INTEGRATION_VERIFIED | Monotonic versioning (`serverVersion <= :serverVersion`), compare-and-set updates. |
| **Offer** | Postgres `ride_offers` table | Postgres `public.ride_offers` | `RideCommandRepository` -> Outbox -> `ride_submit_offer_v2` | Room `ride_offers` (`RideOfferEntity`) | INTEGRATION_VERIFIED | Exclusive winning offer locked via Postgres row lock (`FOR UPDATE`). |
| **Matching** | Postgres Auto-Match Engine + Pure Domain Policy | Postgres `ride_try_auto_match_v1` + `RideAutoMatchEvaluator` | Client policy evaluates -> Server CAS validates | Room / StateFlow | UNIT_VERIFIED | Client recommendation evaluated against strict server verification. |
| **Trip** | Postgres `ride_requests` (trip state machine) | Postgres `public.ride_requests` | RPCs (`ride_driver_transition_v2`, `ride_verify_boarding_pin_v2`) | Room `ride_requests` | INTEGRATION_VERIFIED | Pure server-state transitions; UI acts strictly as reactive projection. |
| **Trip Lifecycle** | `RideLifecyclePolicy` (domain) + Postgres RPCs | Postgres `public.ride_requests.state` + `public.ride_event_log` | `RideCommandGateway.executeCommand` | Room `ride_requests.status` / `serverState` | INTEGRATION_VERIFIED | Enforce actor ownership (`passengerId` or `assignedDriverId`) on every mutation. |
| **Location (Live)** | Android `RideLocationTrackingService` (Foreground) | Android FusedLocationProviderClient + Device Memory | High-accuracy GPS sensor loop (driver only) | Flow / Broadcast | INTEGRATION_VERIFIED | Foreground service type `location` with notification. Exact GPS never in logs. |
| **Location (Durable)** | Postgres PostGIS `ride_location_breadcrumbs` | Postgres `public.ride_location_breadcrumbs` (geom) | `DeviceMessageCipher` -> Outbox -> `ride_record_location_breadcrumb_v2` | PostGIS spatial queries | INTEGRATION_VERIFIED | 90-day retention, legal hold override, purpose-bound audit disclosures. |
| **Routing** | OSRM / Valhalla / Photon / OpenFreeMap | Remote routing endpoints with local cache | `RideRoutingEngine` / `FallbackEtaProvider` | In-memory route polyline cache | INTEGRATION_VERIFIED | Typed failures (`NoRoute`, `ProviderUnavailable`). Never fabricate 0 km / 0 min. |
| **ETA** | Remote Routing Engine + Historical Observation | Postgres `ride_eta_observations` | `ride_record_eta_observation_v1` / `ride_get_latest_trip_eta_v1` | StateFlow in `RideViewModel` | UNIT_VERIFIED | Expose estimation confidence; degrade gracefully to `FallbackEtaProvider`. |
| **Maps** | MapLibre Native SDK + OpenFreeMap tiles | Vector tiles, offline cache dir | MapLibre map controller render loop | Disk tile cache | INTEGRATION_VERIFIED | Open-source vector rendering. Explicit loading/error states. |
| **Payments** | Payment Gateway + Webhook Attestation | Postgres `public.ride_payment_events` | `ride_attest_payment_event_v1` | Room / StateFlow | UNIT_VERIFIED | Strict state machine: `PREAUTH_PENDING` -> `AUTHORIZED` -> `CAPTURED` -> `REFUNDED`. |
| **Wallet / Credits** | Postgres `ride_wallets` | Postgres `public.ride_wallets` | Server accounting transaction | StateFlow / UI balance display | INTEGRATION_VERIFIED | Balances are projections over ledger entries, never raw mutable floats. |
| **Ledger** | `RideDoubleEntryLedger` + Postgres Ledger | Postgres `public.ride_wallet_ledger` + `ride_commission_reservations` | Double-entry balanced journal entries | Room ledger cache | UNIT_VERIFIED | Invariant: `SUM(DEBITS) == SUM(CREDITS)`. Integer minor units (`RideMoney`). |
| **Ratings** | Postgres Feedback Registry | Postgres `public.ride_trip_feedback` | `ride_record_trip_feedback_v1` | Room `ratings` (`RatingEntity`) | INTEGRATION_VERIFIED | Unique feedback per actor per completed trip; Bayesian trust ranking. |
| **Communications** | `ElysiumCommunicationRepository` + Supabase Channels | Postgres `public.communication_*` + Room `communication_*` | `CommunicationDao` + Supabase realtime broadcast | Room entities (12 tables) | PRODUCTION_VERIFIED | Durable message outbox with end-to-end delivery receipts. |
| **PTT** | NOT IMPLEMENTED | None | None | None | DISCOVERED | Must be designed as push-to-talk capability over LiveKit/WebRTC. |
| **Safety** | `RideGuardian` + Safety Signals | Postgres `public.ride_safety_signals` + `ride_operational_holds` | `ride_signal_safety_v2` / `ride_emit_safety_signal_v1` | Room `RideSafetyPanels` | INTEGRATION_VERIFIED | One-touch emergency alert, active share links, unalterable event log. |
| **Incidents** | Support / Incident Pipeline | Postgres `public.ride_support_cases` | `ride_open_support_case_v2` | Support UI | INTEGRATION_VERIFIED | Category, summary, and cryptographic evidence manifest binding. |
| **Evidence** | `LegalEvidenceLedger` | Room `legal_evidence_items` + `evidence_packages` | `LegalEvidenceLedger.recordEvidence()` | Local forensic archive | INTEGRATION_VERIFIED | SHA-256 chained hashing, PDF certification, Dekra/court export. |
| **Legal** | `LegalRelationGraph` + Forensic Trails | Room `legal_cases`, `legal_journal_events` | `LegalEvidenceDao` | Room projections | INTEGRATION_VERIFIED | Local legal ledger for driver/passenger protection and accident claims. |
| **Fleet** | Postgres Fleet Domain | Postgres `public.fleet_*` + Room `fleets` | Fleet management RPCs | Room `FleetEntity`, `FleetMemberEntity` | DISCOVERED | Shared vehicle and driver dispatch permissions for corporate fleets. |
| **Support** | Support Case Gateway | Postgres `public.ride_support_cases` | `ride_open_support_case_v2` | Room / UI | INTEGRATION_VERIFIED | Typed support cases bound to authenticated actor and trip reference. |
| **Realtime** | Supabase Realtime Channels (Postgres CDC + Broadcast) | Postgres Replication Stream / WebSocket | Postgres publication `supabase_realtime` | Realtime state listeners | INTEGRATION_VERIFIED | Realtime is an optimization, NOT authority. Reconcile from DB on reconnect. |
| **Outbox** | `RideCommandRepository` + `RideCommandOutboxDao` | Room `ride_command_outbox` | `RideCommandRepository.enqueue()` -> `RideCommandSyncWorker` | Worker execution queue | PRODUCTION_VERIFIED | At-least-once delivery with idempotency key, expectedVersion, and backoff. |

---

## 2. Invariant Laws Enforced
1. **One Business Fact → One Durable Authority**: Client never asserts business truth; it only requests commands.
2. **Actor-Bound Storage Predicates**: `passengerId == auth.uid()` for rider commands; `assignedDriverId == auth.uid()` for driver commands.
3. **No Synthetic System Actor**: Anonymous or ambiguous sessions fail closed; no fallback to `"SYSTEM"`.
4. **Compare-and-Set Concurrency**: Every mutation requires `expectedVersion`. Mismatch triggers typed `VERSION_CONFLICT`.
5. **Integer Minor Units**: All monetary amounts represented as `Long` minor units (CRC or USD cents) in balanced double-entry ledger.
