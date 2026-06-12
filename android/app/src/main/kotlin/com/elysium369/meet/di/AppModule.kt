package com.elysium369.meet.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.elysium369.meet.core.ai.GeminiDiagnostic
import com.elysium369.meet.core.health.PredictiveHealthEngine
import com.elysium369.meet.data.local.MeetDatabase
import com.elysium369.meet.data.local.dao.*

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Complete schema migration to v6. Handles ALL differences from any prior version:
     * 1. Adds 5 new vehicle columns (displacementCc, engineTech, transmissionType, etc.)
     * 2. Creates any missing tables that were added after the source version
     *
     * Uses CREATE TABLE IF NOT EXISTS + ALTER TABLE pattern to be
     * idempotent and safe regardless of what intermediate schema the device has.
     * Rule: ALWAYS provide migrations from ALL possible source versions.
     */
    private fun migrateToV6(db: SupportSQLiteDatabase, from: Int) {
        android.util.Log.i("MeetDB", "Migration $from→6: Starting comprehensive schema migration")

        // --- 1. Add missing vehicle columns ---
        val existingColumns = mutableSetOf<String>()
        db.query("PRAGMA table_info(vehicles)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                existingColumns.add(cursor.getString(nameIndex))
            }
        }
        fun addColIfMissing(col: String, type: String) {
            if (!existingColumns.contains(col)) {
                db.execSQL("ALTER TABLE vehicles ADD COLUMN $col $type")
                android.util.Log.i("MeetDB", "Migration $from→6: Added vehicle column '$col'")
            }
        }
        addColIfMissing("displacementCc", "INTEGER NOT NULL DEFAULT 0")
        addColIfMissing("engineTech", "TEXT NOT NULL DEFAULT ''")
        addColIfMissing("transmissionType", "TEXT NOT NULL DEFAULT ''")
        addColIfMissing("transmissionSubtype", "TEXT NOT NULL DEFAULT ''")
        addColIfMissing("fuelType", "TEXT NOT NULL DEFAULT ''")

        // --- 2. Create missing tables ---
        db.execSQL("""CREATE TABLE IF NOT EXISTS `custom_pids` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `userId` TEXT NOT NULL,
            `mode` TEXT NOT NULL,
            `pid` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `unit` TEXT NOT NULL,
            `formula` TEXT NOT NULL,
            `minVal` REAL NOT NULL,
            `maxVal` REAL NOT NULL,
            `warningThreshold` REAL,
            `color` TEXT NOT NULL
        )""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS `dashboards` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `name` TEXT NOT NULL,
            `isDefault` INTEGER NOT NULL DEFAULT 0,
            `createdAt` INTEGER NOT NULL DEFAULT 0
        )""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS `dashboard_widgets` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `dashboardId` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `pid` TEXT NOT NULL,
            `type` TEXT NOT NULL,
            `gridX` INTEGER NOT NULL,
            `gridY` INTEGER NOT NULL,
            `gridW` INTEGER NOT NULL,
            `gridH` INTEGER NOT NULL,
            `color` TEXT NOT NULL,
            `minVal` REAL NOT NULL,
            `maxVal` REAL NOT NULL,
            `unit` TEXT NOT NULL
        )""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS `maintenance_alerts` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `vehicleId` TEXT NOT NULL,
            `type` TEXT NOT NULL,
            `intervalKm` INTEGER NOT NULL,
            `lastDoneKm` INTEGER NOT NULL,
            `nextDueKm` INTEGER NOT NULL,
            `notes` TEXT
        )""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS `ai_consults` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `sessionId` TEXT NOT NULL,
            `dtcCodes` TEXT NOT NULL,
            `prompt` TEXT NOT NULL,
            `response` TEXT NOT NULL,
            `model` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `exportedAsPdf` INTEGER NOT NULL DEFAULT 0
        )""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS `diagnostic_sessions` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `vehicleId` TEXT NOT NULL,
            `adapterFingerprint` TEXT NOT NULL,
            `protocolUsed` TEXT NOT NULL,
            `startedAt` INTEGER NOT NULL,
            `endedAt` INTEGER,
            `dtcSnapshot` TEXT NOT NULL,
            `liveDataSummary` TEXT NOT NULL,
            `synced` INTEGER NOT NULL DEFAULT 0
        )""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS `dtc_events` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `sessionId` TEXT NOT NULL,
            `vehicleId` TEXT NOT NULL,
            `code` TEXT NOT NULL,
            `description` TEXT NOT NULL,
            `severity` TEXT NOT NULL,
            `status` TEXT NOT NULL,
            `firstSeenAt` INTEGER NOT NULL,
            `resolvedAt` INTEGER,
            `occurrenceCount` INTEGER NOT NULL,
            `freezeFrameJson` TEXT
        )""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS `trips` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `vehicleId` TEXT NOT NULL,
            `sessionId` TEXT NOT NULL,
            `startedAt` INTEGER NOT NULL,
            `endedAt` INTEGER,
            `distanceKm` REAL NOT NULL,
            `durationSeconds` INTEGER NOT NULL,
            `avgSpeedKmh` REAL NOT NULL,
            `maxSpeedKmh` REAL NOT NULL,
            `maxRpm` REAL NOT NULL,
            `avgRpm` REAL NOT NULL,
            `maxTempC` REAL NOT NULL,
            `fuelEfficiency` REAL,
            `ecoScore` INTEGER NOT NULL,
            `gpsTrackJson` TEXT,
            `synced` INTEGER NOT NULL DEFAULT 0
        )""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS `adapter_profiles` (
            `deviceAddress` TEXT NOT NULL PRIMARY KEY,
            `deviceName` TEXT NOT NULL,
            `chipVersion` TEXT NOT NULL,
            `isClone` INTEGER NOT NULL DEFAULT 0,
            `optimalBaudRate` INTEGER NOT NULL,
            `commandDelayMs` INTEGER NOT NULL,
            `supportsSTN` INTEGER NOT NULL DEFAULT 0,
            `lastUsedAt` INTEGER NOT NULL,
            `successfulConnections` INTEGER NOT NULL,
            `failedConnections` INTEGER NOT NULL
        )""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS `dtc_definitions` (
            `code` TEXT NOT NULL,
            `descriptionEs` TEXT NOT NULL,
            `descriptionEn` TEXT NOT NULL,
            `system` TEXT NOT NULL,
            `severity` TEXT NOT NULL,
            `possibleCauses` TEXT NOT NULL,
            `urgency` TEXT NOT NULL,
            PRIMARY KEY(`code`)
        )""")

        android.util.Log.i("MeetDB", "Migration $from→6: Complete — all tables & columns verified")
    }

    private val MIGRATION_1_6 = object : Migration(1, 6) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateToV6(db, 1)
    }
    private val MIGRATION_2_6 = object : Migration(2, 6) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateToV6(db, 2)
    }
    private val MIGRATION_3_6 = object : Migration(3, 6) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateToV6(db, 3)
    }
    private val MIGRATION_4_6 = object : Migration(4, 6) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateToV6(db, 4)
    }
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateToV6(db, 5)
    }

    // ── v6/v7 → v8: Predictive Health Engine tables ──
    private fun migrateToV8(db: SupportSQLiteDatabase, from: Int) {
        android.util.Log.i("MeetDB", "Migration $from→8: Adding predictive health tables")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `sensor_history` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `vehicleId` TEXT NOT NULL,
            `sessionId` TEXT NOT NULL,
            `pid` TEXT NOT NULL,
            `pidLabel` TEXT NOT NULL,
            `value` REAL NOT NULL,
            `unit` TEXT NOT NULL,
            `timestamp` INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sensor_history_vehicleId_pid_timestamp` ON `sensor_history` (`vehicleId`, `pid`, `timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sensor_history_sessionId` ON `sensor_history` (`sessionId`)")

        db.execSQL("""CREATE TABLE IF NOT EXISTS `health_snapshots` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `vehicleId` TEXT NOT NULL,
            `sessionId` TEXT NOT NULL,
            `overallScore` INTEGER NOT NULL,
            `engineScore` INTEGER NOT NULL,
            `fuelScore` INTEGER NOT NULL,
            `coolingScore` INTEGER NOT NULL,
            `electricalScore` INTEGER NOT NULL,
            `emissionsScore` INTEGER NOT NULL,
            `activeDtcCount` INTEGER NOT NULL,
            `pendingDtcCount` INTEGER NOT NULL,
            `anomalyCount` INTEGER NOT NULL,
            `sensorSummaryJson` TEXT NOT NULL,
            `timestamp` INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_snapshots_vehicleId_timestamp` ON `health_snapshots` (`vehicleId`, `timestamp`)")
        android.util.Log.i("MeetDB", "Migration $from→8: Complete")
    }

    private fun migrateToV9(db: SupportSQLiteDatabase, from: Int) {
        android.util.Log.i("MeetDB", "Migration $from→9: Patching dtc_events missing columns")
        try { db.execSQL("ALTER TABLE `dtc_events` ADD COLUMN `lastSeenAt` INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE `dtc_events` ADD COLUMN `synced` INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
        android.util.Log.i("MeetDB", "Migration $from→9: Complete")
    }

    private val MIGRATION_6_9 = object : Migration(6, 9) {
        override fun migrate(db: SupportSQLiteDatabase) { migrateToV8(db, 6); migrateToV9(db, 6) }
    }
    private val MIGRATION_7_9 = object : Migration(7, 9) {
        override fun migrate(db: SupportSQLiteDatabase) { migrateToV8(db, 7); migrateToV9(db, 7) }
    }
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateToV9(db, 8)
    }

    // ── v9 → v10: Maintenance Logs & Repair History ──
    private fun migrateToV10(db: SupportSQLiteDatabase, from: Int) {
        android.util.Log.i("MeetDB", "Migration $from→10: Adding maintenance & repair tables")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `maintenance_logs` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `vehicleId` TEXT NOT NULL,
            `category` TEXT NOT NULL,
            `description` TEXT NOT NULL,
            `brand` TEXT NOT NULL,
            `specification` TEXT NOT NULL,
            `datePerformed` INTEGER NOT NULL,
            `odometerAtService` INTEGER NOT NULL,
            `intervalKm` INTEGER NOT NULL,
            `intervalMonths` INTEGER NOT NULL,
            `nextDueKm` INTEGER NOT NULL,
            `nextDueDate` INTEGER NOT NULL,
            `cost` REAL NOT NULL,
            `currency` TEXT NOT NULL,
            `workshopName` TEXT NOT NULL,
            `notes` TEXT NOT NULL,
            `receiptPhotoPath` TEXT,
            `createdAt` INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `repair_history` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `vehicleId` TEXT NOT NULL,
            `partCategory` TEXT NOT NULL,
            `partName` TEXT NOT NULL,
            `partNumber` TEXT NOT NULL,
            `brand` TEXT NOT NULL,
            `isOem` INTEGER NOT NULL DEFAULT 0,
            `reason` TEXT NOT NULL,
            `relatedDtc` TEXT,
            `datePerformed` INTEGER NOT NULL,
            `odometerAtRepair` INTEGER NOT NULL,
            `expectedLifeKm` INTEGER,
            `expectedLifeMonths` INTEGER,
            `nextReplacementKm` INTEGER,
            `isPeriodic` INTEGER NOT NULL DEFAULT 0,
            `laborCost` REAL NOT NULL,
            `partCost` REAL NOT NULL,
            `totalCost` REAL NOT NULL,
            `currency` TEXT NOT NULL,
            `workshopName` TEXT NOT NULL,
            `warrantyMonths` INTEGER NOT NULL,
            `warrantyKm` INTEGER NOT NULL,
            `notes` TEXT NOT NULL,
            `photoPath` TEXT,
            `createdAt` INTEGER NOT NULL
        )""")
        android.util.Log.i("MeetDB", "Migration $from→10: Complete")
    }

    private fun migrateToV11(db: SupportSQLiteDatabase, from: Int) {
        android.util.Log.i("MeetDB", "Migration $from→11: Migrating dtc_definitions table to support OEM manufacturer primary key")
        try {
            db.execSQL("ALTER TABLE `dtc_definitions` RENAME TO `dtc_definitions_old`")
            db.execSQL("""
                CREATE TABLE `dtc_definitions` (
                    `code` TEXT NOT NULL,
                    `manufacturer` TEXT NOT NULL DEFAULT 'GENERIC',
                    `descriptionEs` TEXT NOT NULL,
                    `descriptionEn` TEXT NOT NULL,
                    `system` TEXT NOT NULL,
                    `severity` TEXT NOT NULL,
                    `possibleCauses` TEXT NOT NULL,
                    `urgency` TEXT NOT NULL,
                    PRIMARY KEY(`code`, `manufacturer`)
                )
            """)
            db.execSQL("""
                INSERT INTO `dtc_definitions` (`code`, `manufacturer`, `descriptionEs`, `descriptionEn`, `system`, `severity`, `possibleCauses`, `urgency`)
                SELECT `code`, 'GENERIC', `descriptionEs`, `descriptionEn`, `system`, `severity`, `possibleCauses`, `urgency`
                FROM `dtc_definitions_old`
            """)
            db.execSQL("DROP TABLE `dtc_definitions_old`")
            android.util.Log.i("MeetDB", "Migration $from→11: Complete")
        } catch (e: Exception) {
            android.util.Log.e("MeetDB", "Migration $from→11 failed, recreating definitions table", e)
            db.execSQL("DROP TABLE IF EXISTS `dtc_definitions_old`")
            db.execSQL("DROP TABLE IF EXISTS `dtc_definitions`")
            db.execSQL("""
                CREATE TABLE `dtc_definitions` (
                    `code` TEXT NOT NULL,
                    `manufacturer` TEXT NOT NULL DEFAULT 'GENERIC',
                    `descriptionEs` TEXT NOT NULL,
                    `descriptionEn` TEXT NOT NULL,
                    `system` TEXT NOT NULL,
                    `severity` TEXT NOT NULL,
                    `possibleCauses` TEXT NOT NULL,
                    `urgency` TEXT NOT NULL,
                    PRIMARY KEY(`code`, `manufacturer`)
                )
            """)
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateToV10(db, 9)
    }
    private val MIGRATION_6_10 = object : Migration(6, 10) {
        override fun migrate(db: SupportSQLiteDatabase) { migrateToV8(db, 6); migrateToV9(db, 6); migrateToV10(db, 6) }
    }
    private val MIGRATION_7_10 = object : Migration(7, 10) {
        override fun migrate(db: SupportSQLiteDatabase) { migrateToV8(db, 7); migrateToV9(db, 7); migrateToV10(db, 7) }
    }
    private val MIGRATION_8_10 = object : Migration(8, 10) {
        override fun migrate(db: SupportSQLiteDatabase) { migrateToV9(db, 8); migrateToV10(db, 8) }
    }
    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateToV11(db, 10)
    }

    private fun migrateToV12(db: SupportSQLiteDatabase, from: Int) {
        android.util.Log.i("MeetDB", "Migration $from→12: Adding fleet management schema elements")
        
        try { db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `businessId` TEXT") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `fleetId` TEXT") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `assignedDriverId` TEXT") } catch (_: Exception) {}

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `business_profiles` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `taxId` TEXT,
                `planType` TEXT NOT NULL,
                `maxVehicles` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `ownerUserId` TEXT NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `fleets` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `businessId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT,
                `createdAt` INTEGER NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `fleet_members` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `businessId` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `role` TEXT NOT NULL,
                `email` TEXT NOT NULL,
                `inviteStatus` TEXT NOT NULL,
                `joinedAt` INTEGER
            )
        """)
        
        android.util.Log.i("MeetDB", "Migration $from→12: Complete")
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateToV12(db, 11)
    }

    private fun migrateToV13(db: SupportSQLiteDatabase, from: Int) {
        android.util.Log.i("MeetDB", "Migration $from→13: Adding chat messaging schema elements")
        
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `chat_messages` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `businessId` TEXT NOT NULL,
                `senderId` TEXT NOT NULL,
                `receiverId` TEXT NOT NULL,
                `messageText` TEXT,
                `messageType` TEXT NOT NULL,
                `fileLocalPath` TEXT,
                `fileRemoteUrl` TEXT,
                `durationSeconds` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `status` TEXT NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `chat_blocklist` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `businessId` TEXT NOT NULL,
                `blockerUserId` TEXT NOT NULL,
                `blockedUserId` TEXT NOT NULL,
                `blockedAt` INTEGER NOT NULL
            )
        """)
        
        android.util.Log.i("MeetDB", "Migration $from→13: Complete")
    }

    private fun migrateToV14(db: SupportSQLiteDatabase, from: Int) {
        android.util.Log.i("MeetDB", "Migration $from→14: Adding inviteCode to fleets and fleetId to fleet_members")
        try { db.execSQL("ALTER TABLE `fleets` ADD COLUMN `inviteCode` TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE `fleet_members` ADD COLUMN `fleetId` TEXT") } catch (_: Exception) {}
        android.util.Log.i("MeetDB", "Migration $from→14: Complete")
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateToV13(db, 12)
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateToV14(db, 13)
    }

    private fun migrateToV15(db: SupportSQLiteDatabase, from: Int) {
        android.util.Log.i("MeetDB", "Migration $from→15: Adding dvir_reports table")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `dvir_reports` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `vehicleId` TEXT NOT NULL,
                `driverId` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `brakesOk` INTEGER NOT NULL,
                `lightsOk` INTEGER NOT NULL,
                `tiresOk` INTEGER NOT NULL,
                `fluidsOk` INTEGER NOT NULL,
                `batteryOk` INTEGER NOT NULL,
                `remarks` TEXT,
                `signaturePath` TEXT
            )
        """)
        android.util.Log.i("MeetDB", "Migration $from→15: Complete")
    }

    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateToV15(db, 14)
    }

    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("MeetDB", "Migration 15→16: Adding standalone indices on timestamp column")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sensor_history_timestamp` ON `sensor_history` (`timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_snapshots_timestamp` ON `health_snapshots` (`timestamp`)")
            android.util.Log.i("MeetDB", "Migration 15→16: Complete")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeetDatabase {
        return Room.databaseBuilder(
            context,
            MeetDatabase::class.java,
            "meet_database"
        )
        // ⛔ REMOVED: createFromAsset("databases/meet_dtc.db")
        // ROOT CAUSE of vehicle data loss — this combined with fallbackToDestructiveMigration
        // caused Room to wipe ALL tables (including vehicles) on any schema version mismatch,
        // then recreate from the asset file which contains zero vehicle records.
        // DTC definitions are now loaded programmatically via DtcDatabaseLoader on first launch.

        // ⛔ REMOVED: fallbackToDestructiveMigration()
        // This silently destroyed user data. We now use explicit migrations.

        .addMigrations(MIGRATION_1_6, MIGRATION_2_6, MIGRATION_3_6, MIGRATION_4_6, MIGRATION_5_6, MIGRATION_6_9, MIGRATION_7_9, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_6_10, MIGRATION_7_10, MIGRATION_8_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                android.util.Log.i("MeetDB", "Database created fresh — DtcDatabaseLoader will populate DTCs on first use")
            }
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                android.util.Log.d("MeetDB", "Database opened successfully — user data intact")
            }
        })
        .build()
    }

    @Provides
    fun provideVehicleDao(db: MeetDatabase): VehicleDao = db.vehicleDao()

    @Provides
    fun provideDiagnosticSessionDao(db: MeetDatabase): DiagnosticSessionDao = db.sessionDao()

    @Provides
    fun provideDtcDao(db: MeetDatabase): DtcDao = db.dtcDao()

    @Provides
    fun provideTripDao(db: MeetDatabase): TripDao = db.tripDao()

    @Provides
    fun provideAdapterProfileDao(db: MeetDatabase): AdapterProfileDao = db.adapterDao()

    @Provides
    fun provideDtcDefinitionDao(db: MeetDatabase): DtcDefinitionDao = db.dtcDefinitionDao()

    @Provides
    fun provideMaintenanceAlertDao(db: MeetDatabase): MaintenanceAlertDao = db.maintenanceDao()

    @Provides
    fun provideAiConsultDao(db: MeetDatabase): AiConsultDao = db.aiConsultDao()

    @Provides
    fun provideCustomPidDao(db: MeetDatabase): CustomPidDao = db.customPidDao()

    @Provides
    fun provideDashboardDao(db: MeetDatabase): DashboardDao = db.dashboardDao()

    @Provides
    fun provideSensorHistoryDao(db: MeetDatabase): SensorHistoryDao = db.sensorHistoryDao()

    @Provides
    fun provideHealthSnapshotDao(db: MeetDatabase): HealthSnapshotDao = db.healthSnapshotDao()

    @Provides
    fun provideMaintenanceLogDao(db: MeetDatabase): MaintenanceLogDao = db.maintenanceLogDao()

    @Provides
    fun provideRepairHistoryDao(db: MeetDatabase): RepairHistoryDao = db.repairHistoryDao()

    @Provides
    fun provideFleetDao(db: MeetDatabase): FleetDao = db.fleetDao()

    @Provides
    fun provideChatDao(db: MeetDatabase): ChatDao = db.chatDao()

    @Provides
    @Singleton
    fun providePredictiveHealthEngine(
        sensorHistoryDao: SensorHistoryDao,
        healthSnapshotDao: HealthSnapshotDao
    ): PredictiveHealthEngine = PredictiveHealthEngine(sensorHistoryDao, healthSnapshotDao)

    @Provides
    @Singleton
    fun provideSupabaseClient(): io.github.jan.supabase.SupabaseClient {
        return com.elysium369.meet.data.supabase.SupabaseManager.client
    }

    @Provides
    @Singleton
    fun provideGeminiDiagnostic(): GeminiDiagnostic {
        return GeminiDiagnostic()
    }

    @Provides
    @Singleton
    fun provideReportGenerator(@ApplicationContext context: Context): com.elysium369.meet.core.export.ReportGenerator {
        return com.elysium369.meet.core.export.ReportGenerator(context)
    }

    @Provides
    fun provideDvirReportDao(db: MeetDatabase): DvirReportDao = db.dvirReportDao()
}
