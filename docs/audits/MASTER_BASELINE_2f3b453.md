# MASTER BASELINE — MEET / Elysium Vanguard

## CURRENT SHA

`2f3b453d8fc9c82d4d718217afe73ab28b65a2c` on `main`

## Build

- `versionCode = 56`, `versionName = "4.23.6"`
- Room DB `version = 69`
- 811 Kotlin source files, 255 test files
- BUILD SUCCESSFUL (1m 22s)

---

## Authority Matrix

| Domain | Authority | Local Persistence | Cloud Authority | Realtime | Status | Duplicates |
|---|---|---|---|---|---|---|
| Identity | `ActivePrincipalKernel` | SharedPreferences + Supabase Auth | Supabase Auth | Auth state | AUTHORITATIVE | None |
| Vehicle | `VehicleRepository` + `VehicleEntity` | Room | Supabase `vehicles` | Realtime channel | AUTHORITATIVE | None |
| ActiveVehicle | `ActiveVehicleKernel` | `ActiveVehicleSelectionEntity` (Room) | None (local-only) | None | AUTHORITATIVE | None |
| OBD Session | `ObdSession` (state machine) | None (in-memory) | None | None | SIMULATION | None |
| Vehicle Truth | `VehicleTruthEngine` (in ObdViewModel) | Room (`DiagnosticSnapshotEntity`, `HealthScoreEntity`) | None | None | PROJECTION | None |
| Ride | `RideViewModel` + `RideDao` | Room (`RideRequestEntity`, `RideOfferEntity`, outbox) | Supabase rides tables | Realtime | AUTHORITATIVE | None |
| Presence | `CommunicationPresenceLeaseEntity` | Room | Supabase | Realtime | PROJECTION | None |
| Communications | `CommunicationDao` + `CommunicationViewModel` | Room (conversations, events, receipts) | Supabase | Realtime | AUTHORITATIVE | None |
| PTT | Not implemented | None | None | None | NOT_IMPLEMENTED | None |
| Fuel | `FuelLedger` + `FuelLedgerDao` | Room (`fuel_transactions_local`, `fuel_reward_ledger_local`) | Supabase (via outbox) | None | AUTHORITATIVE | None |
| Rewards | `FuelLedger.confirmedRewards` | Room (same ledger) | Supabase (server authority) | None | PROJECTION | None |
| Legal | `LegalEvidenceLedger` + `LegalEvidenceDao` | Room (cases, events, evidence, custody) | None | None | AUTHORITATIVE | None |
| Evidence | `LegalEvidenceItemEntity` + `EvidencePackageEntity` | Room | None | None | AUTHORITATIVE | None |
| Properties | `PropertyListingProjectionEntity` | Room (projection) | Supabase (read-only) | None | PROJECTION | None |
| Money | Not implemented as ledger | None | None | None | NOT_IMPLEMENTED | None |
| Market | `MarketOsRepository` + `MarketOsViewModel` | Room projections | Supabase | Realtime | PROJECTION | None |
| Repair | `RepairCaseEntity` + `RepairCaseDao` | Room | None | None | AUTHORITATIVE | None |
| Service OS | `ServiceRequestEntity` + `ServiceBidEntity` | Room | Supabase | None | AUTHORITATIVE | None |

---

## Room Database: 177 Entity Classes, 57+ DAOs

### Entity Groups (by domain)

| Group | Entities | Tables |
|---|---|---|
| Vehicle | `VehicleEntity`, `ActiveVehicleSelectionEntity`, `VehicleDnaProfileEntity`, `VehicleTwinProfileEntity`, `TwinAnomalyEntity` | 5 |
| OBD/Diagnostic | `DiagnosticSessionEntity`, `DtcEventEntity`, `DtcDefinitionEntity`, `SensorHistoryEntity`, `HealthSnapshotEntity`, `Mode06ResultEntity`, `FreezeFrameEntity`, `DerivedMetricEntity`, `ObdPidSampleEntity`, `ObdCommandLogEntity`, `EcuFailureEventEntity`, `CompatibilityRuleEntity`, `VehicleProfileSnapshotEntity`, `DiagnosticSnapshotEntity` (cert V2) | 14 |
| Fuel | `FuelTransactionEntity`, `FuelRewardLedgerEntryEntity`, `FuelStationPriceObservationEntity` | 3 |
| Ride | `RideRequestEntity`, `ActiveRideSelectionEntity`, `RideOfferEntity`, `RideChatMessageEntity`, `RideCommandOutboxEntity` | 5 |
| Communications | `CommunicationConversationEntity`, `CommunicationParticipantEntity`, `CommunicationEventEntity`, `CommunicationReceiptEntity`, `CommunicationCallEntity`, `CommunicationIdentityProfileEntity`, `CommunicationPrivacySettingsEntity`, `CommunicationRelationshipEntity`, `CommunicationLocalBlockEntity`, `CommunicationPresenceLeaseEntity`, `CommunicationMeshPeerEntity`, `CommunicationMeshOutboxEntity` | 12 |
| Legal | `LegalCaseEntity`, `LegalJournalEventEntity`, `LegalEvidenceItemEntity`, `LegalCustodyEventEntity`, `LegalReminderEntity`, `LegalExpenseEntity` | 6 |
| Market OS | `MarketOrganizationProjectionEntity`, `LegalMatterProjectionEntity`, `PropertyListingProjectionEntity`, `FuelCouponProjectionEntity`, `MarketCommandOutboxEntity` | 5 |
| Repair/Service | `RepairCaseEntity`, `RepairActionEntity`, `ServiceRequestEntity`, `ServiceBidEntity`, `PartRequestEntity`, `PartOfferEntity`, `PartsStoreEntity` | 7 |
| Certified Reports V2 | `CertifiedReportEntity`, `ReportEvidenceEntity`, `ReportSignatureEntity`, `DiagnosticFindingEntity`, `DiagnosticExchangeEntity`, `EncryptedEvidenceBlobEntity`, `DiagnosticObservationEntity`, `FindingDiagnosticSnapshotEntity`, `FindingSnapshotExchangeRefEntity`, `DiagnosticSessionIntegrityEntity` | 10 |
| Knowledge Graph | 9 DTC knowledge entities + 8 Vanguard knowledge entities | 17 |
| Identity | `DriverVerificationEntity`, `PassengerVerificationEntity`, `ProviderProfileEntity` | 3 |
| Vehicle Access | `VehicleAccessCredentialEntity`, `AccessGrantEntity`, `AccessAuditEventEntity` | 3 |
| Humanity | `HumanityProgressEntity`, `HumanityEvidenceEntity`, `HumanityCapabilityEntity` | 3 |
| Fleet | `FleetEntity`, `FleetMemberEntity` | 2 |
| Dashboard | `DashboardEntity`, `DashboardWidgetEntity` | 2 |
| Telemetry/Vanguard | `VanguardObdSessionEntity`, `VanguardEventEntity`, `VanguardOutboxEntity`, `MarketplaceLedgerEntryEntity`, `VehicleIdentityObservationEntity`, `DiagnosticScanManifestEntity`, `TelemetryUploadQueueEntity` | 7 |
| Other | `TripEntity`, `AdapterProfileEntity`, `MaintenanceAlertEntity`, `AiConsultEntity`, `CustomPidEntity`, `MaintenanceLogEntity`, `RepairHistoryEntity`, `BusinessProfileEntity`, `ChatMessageEntity`, `ChatBlocklistEntity`, `DvirReportEntity`, `PredictionEventEntity`, `LiveSessionEntity`, `LiveSnapshotEntity`, `MechanicNoteEntity`, `RepairPhotoEntity`, `RepairPartEntity`, `RepairVoteEntity`, `RepairCommentEntity`, `RepairVerificationEntity`, `SavedGaugeEntity`, `GaugeListingCacheEntity`, `RatingEntity`, `TowTruckRequestEntity`, `EvidencePackageEntity` | 26 |

---

## ViewModels

| ViewModel | Injects | Responsibilities |
|---|---|---|
| `ObdViewModel` | ~20 deps (ActiveVehicleKernel, FuelLedger, RideDao, CommunicationDao, etc.) | **God ViewModel** — OBD, vehicles, garage, fuel, rides, comms, trust center, driver mode, analytics |
| `MarketOsViewModel` | MarketOsRepository, ActivePrincipalKernel, LegalEvidenceLedger, FuelLedger, ActiveVehicleKernel | Fuel Rewards hub, marketplace, legal projections |
| `RideViewModel` | Ride-related deps | Ride lifecycle, matching, driver/passenger flows |
| `CommunicationViewModel` | Communication deps | Messages, conversations, presence |
| `FleetChatViewModel` | Fleet deps | Fleet messaging |
| `HomeExperienceViewModel` | Home deps | Home screen modules |
| `DashboardViewModel` | Dashboard deps | Dashboard widgets |
| `ElysiumAiViewModel` | AI deps | AI diagnostics, chat |
| `GaugeMarketplaceViewModel` | Gauge deps | Gauge marketplace |
| `RepairNetworkViewModel` | Repair deps | Repair network |
| `SupportChatViewModel` | Support deps | Support chat |
| `TheoryExamViewModel` | Education deps | Theory exams |
| `VehicleDetailViewModel` | Vehicle deps | Vehicle detail screen |
| `ComponentLocatorViewModel` | Component deps | Component locator |

**Critical observation**: `ObdViewModel` is a God ViewModel with 9919 lines. It handles OBD, vehicles, fuel, rides, communications, trust center, driver verification, and more. This is a primary architectural risk.

---

## Workers

| Worker | Purpose |
|---|---|
| `SyncWorker` | General cloud sync |
| `BackupWorker` | Data backup |
| `DatabaseMaintenanceWorker` | DB maintenance |
| `MarketOsSyncWorker` | Marketplace sync |
| `RideCommandSyncWorker` | Ride command outbox |
| `RideDriverEnrollmentWorker` | Driver enrollment |
| `RideLocationBreadcrumbWorker` | Ride location breadcrumbs |

---

## Foreground Services

| Service | Purpose |
|---|---|
| `ObdForegroundService` | OBD Bluetooth connection |
| `ElysiumTerminalService` | Terminal |
| `RideLocationTrackingService` | Ride GPS tracking |

---

## Navigation

- Root: `NavHost` in `MainActivity.kt`
- Routes defined in `MeetDestinations.kt`
- Forge sub-graph: `ForgeNavGraph.kt` (separate `NavHost`)
- Home: adaptive/classic modes
- Key routes: `fuel_rewards`, `garage`, `rides`, `communications`, `platform_trust_center`, `vehicle_detail`, etc.

---

## Supabase Migrations (latest 5)

1. `20260903120000_relax_v3_evidence_check.sql`
2. `20260903010000_ride_tip_system.sql`
3. `20260902090000_ride_location_safety_observability.sql`
4. `20260901090000_rides_evidence_and_parts_rls.sql`
5. `20260829020000_ride_legacy_schema_drift_hardening.sql`

---

## LiveKit

- `ElysiumCallTransport.kt` — only file referencing LiveKit
- Used for voice/video calls in communications
- NOT used for PTT (PTT not implemented)

---

## Known Risks (from prior work)

1. **Fuel Rewards crash** — `FuelRewardsHub` composable crashes on open. Likely Hilt injection failure in `MarketOsViewModel` (depends on `MarketOsRepository`, `LegalEvidenceLedger`, `FuelLedger`, `ActiveVehicleKernel`). Room tables exist. Needs device reproduction.
2. **ObdViewModel God Object** — 9919 lines, ~20 injected deps. Fragile, hard to test, single point of failure.
3. **ActiveVehicle deselection** — `ActiveVehicleKernel` looks correct (uses durable selection, never `firstOrNull()`), but `ObdViewModel` may override via other paths. Needs audit of all write sites.
4. **OBD Session** — purely in-memory, lost on process death. No persistence layer.
5. **PTT** — not implemented at all.
6. **Legal domain** — exists locally but no cloud sync.
7. **Properties** — projection-only from Supabase, no local authority.
8. **Money/Ledger** — not implemented.

---

## Prior Fixes Already Applied (this session)

1. **Auto-submit removed from `refreshProviderRoles()`** — registration is now user-initiated only. `PlatformTrustCenterGateway.submit()` only called from explicit `registerProviderProfile()` and `submitPassengerTrustApplication()`.
