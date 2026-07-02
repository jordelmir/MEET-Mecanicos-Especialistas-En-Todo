package com.elysium369.meet.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.elysium369.meet.data.local.dao.*
import com.elysium369.meet.data.local.entities.*

@Database(
    entities = [
        VehicleEntity::class,
        DiagnosticSessionEntity::class,
        DtcEventEntity::class,
        TripEntity::class,
        AdapterProfileEntity::class,
        DtcDefinitionEntity::class,
        MaintenanceAlertEntity::class,
        AiConsultEntity::class,
        CustomPidEntity::class,
        DashboardEntity::class,
        DashboardWidgetEntity::class,
        SensorHistoryEntity::class,
        HealthSnapshotEntity::class,
        MaintenanceLogEntity::class,
        RepairHistoryEntity::class,
        BusinessProfileEntity::class,
        FleetEntity::class,
        FleetMemberEntity::class,
        ChatMessageEntity::class,
        ChatBlocklistEntity::class,
        DvirReportEntity::class,
        VehicleDnaProfileEntity::class,
        RepairCaseEntity::class,
        PredictionEventEntity::class,

        // FEATURE ENTITIES
        LiveSessionEntity::class,
        LiveSnapshotEntity::class,
        MechanicNoteEntity::class,
        RepairPhotoEntity::class,
        RepairPartEntity::class,
        RepairVoteEntity::class,
        RepairCommentEntity::class,
        RepairVerificationEntity::class,
        ServiceRequestEntity::class,
        ServiceBidEntity::class,
        PartsStoreEntity::class,
        PartRequestEntity::class,
        PartOfferEntity::class,
        EvidencePackageEntity::class,
        VehicleTwinProfileEntity::class,
        TwinAnomalyEntity::class,
        TowTruckRequestEntity::class,
        RatingEntity::class,
        ProviderProfileEntity::class,

        // KNOWLEDGE GRAPH ENTITIES
        DtcSymptomEntity::class,
        DtcCauseEntity::class,
        DtcProcedureEntity::class,
        DtcRelatedPidEntity::class,
        DtcCoOccurrenceEntity::class,
        DtcVehicleCompatEntity::class,
        DtcVerifiedFixEntity::class,
        DtcRepairCostEntity::class,
        DtcSearchIndexEntity::class,

        // ELYSIUM VANGUARD KNOWLEDGE ENGINE v4.0
        MeetKnowledgeMatrixEntity::class,
        MechanicalProcedureEntity::class,
        ComponentRebuildGuideEntity::class,
        SymptomGuideEntity::class,
        TrenchKnowledgeEntity::class,
        AutomotiveChemicalEntity::class,
        ToolUsageGuideEntity::class,
        SafetyProtocolEntity::class,

        // GAUGE MARKETPLACE ENTITIES
        SavedGaugeEntity::class,
        GaugeListingCacheEntity::class,

        // MEET RIDES
        RideRequestEntity::class,
        RideOfferEntity::class,
        RideChatMessageEntity::class,

        // IDENTITY VERIFICATION
        DriverVerificationEntity::class,
        PassengerVerificationEntity::class,

        // ELYSIUM VANGUARD TELEMETRY INTELLIGENCE
        VanguardObdSessionEntity::class,
        ObdPidSampleEntity::class,
        ObdCommandLogEntity::class,
        EcuFailureEventEntity::class,
        CompatibilityRuleEntity::class,
        VehicleProfileSnapshotEntity::class,
        Mode06ResultEntity::class,
        FreezeFrameEntity::class,
        DerivedMetricEntity::class,
        HealthScoreEntity::class,
        RepairRecommendationEntity::class,
        AiDiagnosticResultEntity::class,
        VehicleHistoryEntity::class,
        PdfReportEntity::class,
        AuditLogEntity::class,
        FixRolloutEntity::class,

        // ELYSIUM VANGUARD COMMERCE TRUST CORE
        VanguardEventEntity::class,
        MarketplaceLedgerEntryEntity::class,
        VanguardOutboxEntity::class
    ],
    version = 39,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MeetDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun sessionDao(): DiagnosticSessionDao
    abstract fun dtcDao(): DtcDao
    abstract fun tripDao(): TripDao
    abstract fun adapterDao(): AdapterProfileDao
    abstract fun dtcDefinitionDao(): DtcDefinitionDao
    abstract fun maintenanceDao(): MaintenanceAlertDao
    abstract fun aiConsultDao(): AiConsultDao
    abstract fun customPidDao(): CustomPidDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun sensorHistoryDao(): SensorHistoryDao
    abstract fun healthSnapshotDao(): HealthSnapshotDao
    abstract fun maintenanceLogDao(): MaintenanceLogDao
    abstract fun repairHistoryDao(): RepairHistoryDao
    abstract fun fleetDao(): FleetDao
    abstract fun chatDao(): ChatDao
    abstract fun dvirReportDao(): DvirReportDao
    abstract fun vehicleDnaDao(): VehicleDnaDao
    abstract fun repairCaseDao(): RepairCaseDao
    abstract fun predictionEventDao(): PredictionEventDao

    // NEW FEATURE DAOS
    abstract fun liveSessionDao(): LiveSessionDao
    abstract fun repairNetworkAddonsDao(): RepairNetworkAddonsDao
    abstract fun marketplaceDao(): MarketplaceDao
    abstract fun blackBoxDao(): BlackBoxDao
    abstract fun vehicleTwinDao(): VehicleTwinDao
    abstract fun towTruckDao(): TowTruckDao
    abstract fun ratingDao(): RatingDao
    abstract fun providerProfileDao(): ProviderProfileDao

    // KNOWLEDGE GRAPH DAO
    abstract fun dtcKnowledgeGraphDao(): DtcKnowledgeGraphDao
    abstract fun mechanicalKnowledgeDao(): MechanicalKnowledgeDao

    // GAUGE MARKETPLACE DAO
    abstract fun savedGaugeDao(): SavedGaugeDao

    // MEET RIDES DAO
    abstract fun rideDao(): RideDao

    // ELYSIUM VANGUARD TELEMETRY DAO
    abstract fun vanguardTelemetryDao(): VanguardTelemetryDao

    // ELYSIUM VANGUARD COMMERCE DAO
    abstract fun vanguardCommerceDao(): VanguardCommerceDao
}
