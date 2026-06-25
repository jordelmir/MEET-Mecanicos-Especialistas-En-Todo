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
        EvidencePackageEntity::class,
        VehicleTwinProfileEntity::class,
        TwinAnomalyEntity::class,

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

        // MEET KNOWLEDGE ENGINE v4.0
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
        GaugeListingCacheEntity::class
    ],
    version = 28,
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

    // KNOWLEDGE GRAPH DAO
    abstract fun dtcKnowledgeGraphDao(): DtcKnowledgeGraphDao
    abstract fun mechanicalKnowledgeDao(): MechanicalKnowledgeDao

    // GAUGE MARKETPLACE DAO
    abstract fun savedGaugeDao(): SavedGaugeDao
}
