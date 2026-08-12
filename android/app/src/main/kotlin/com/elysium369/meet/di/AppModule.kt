package com.elysium369.meet.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.elysium369.meet.core.ai.GeminiDiagnostic
import com.elysium369.meet.core.health.PredictiveHealthEngine
import com.elysium369.meet.core.vanguard.SupabaseVanguardOutboxDispatcher
import com.elysium369.meet.core.vanguard.VanguardOutboxDispatcher
import com.elysium369.meet.data.local.MeetDatabase
import com.elysium369.meet.data.local.dao.*
import com.elysium369.meet.core.reports.ReportVerifier
import com.elysium369.meet.data.local.CertifiedReportRepository
import io.github.jan.supabase.postgrest.postgrest

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
        android.util.Log.i("ElysiumDB", "Migration $from→6: Starting comprehensive schema migration")

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
                android.util.Log.i("ElysiumDB", "Migration $from→6: Added vehicle column '$col'")
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

        android.util.Log.i("ElysiumDB", "Migration $from→6: Complete — all tables & columns verified")
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
        android.util.Log.i("ElysiumDB", "Migration $from→8: Adding predictive health tables")
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
        android.util.Log.i("ElysiumDB", "Migration $from→8: Complete")
    }

    private fun migrateToV9(db: SupportSQLiteDatabase, from: Int) {
        android.util.Log.i("ElysiumDB", "Migration $from→9: Patching dtc_events missing columns")
        try { db.execSQL("ALTER TABLE `dtc_events` ADD COLUMN `lastSeenAt` INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE `dtc_events` ADD COLUMN `synced` INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
        android.util.Log.i("ElysiumDB", "Migration $from→9: Complete")
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
        android.util.Log.i("ElysiumDB", "Migration $from→10: Adding maintenance & repair tables")
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
        android.util.Log.i("ElysiumDB", "Migration $from→10: Complete")
    }

    private fun migrateToV11(db: SupportSQLiteDatabase, from: Int) {
        android.util.Log.i("ElysiumDB", "Migration $from→11: Migrating dtc_definitions table to support OEM manufacturer primary key")
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
            android.util.Log.i("ElysiumDB", "Migration $from→11: Complete")
        } catch (e: Exception) {
            android.util.Log.e("ElysiumDB", "Migration $from→11 failed, recreating definitions table", e)
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
        android.util.Log.i("ElysiumDB", "Migration $from→12: Adding fleet management schema elements")
        
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
        
        android.util.Log.i("ElysiumDB", "Migration $from→12: Complete")
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateToV12(db, 11)
    }

    private fun migrateToV13(db: SupportSQLiteDatabase, from: Int) {
        android.util.Log.i("ElysiumDB", "Migration $from→13: Adding chat messaging schema elements")
        
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
        
        android.util.Log.i("ElysiumDB", "Migration $from→13: Complete")
    }

    private fun migrateToV14(db: SupportSQLiteDatabase, from: Int) {
        android.util.Log.i("ElysiumDB", "Migration $from→14: Adding inviteCode to fleets and fleetId to fleet_members")
        try { db.execSQL("ALTER TABLE `fleets` ADD COLUMN `inviteCode` TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE `fleet_members` ADD COLUMN `fleetId` TEXT") } catch (_: Exception) {}
        android.util.Log.i("ElysiumDB", "Migration $from→14: Complete")
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateToV13(db, 12)
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateToV14(db, 13)
    }

    private fun migrateToV15(db: SupportSQLiteDatabase, from: Int) {
        android.util.Log.i("ElysiumDB", "Migration $from→15: Adding dvir_reports table")
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
        android.util.Log.i("ElysiumDB", "Migration $from→15: Complete")
    }

    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateToV15(db, 14)
    }

    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 15→16: Adding standalone indices on timestamp column")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sensor_history_timestamp` ON `sensor_history` (`timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_snapshots_timestamp` ON `health_snapshots` (`timestamp`)")
            android.util.Log.i("ElysiumDB", "Migration 15→16: Complete")
        }
    }

    private val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 16→17: Recreating dtc_definitions table to match Room schema exactly")
            db.execSQL("DROP TABLE IF EXISTS `dtc_definitions`")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `dtc_definitions` (
                    `code` TEXT NOT NULL,
                    `manufacturer` TEXT NOT NULL,
                    `isGeneric` TEXT NOT NULL,
                    `descriptionEs` TEXT NOT NULL,
                    `descriptionEn` TEXT NOT NULL,
                    `obd2StandardNameEn` TEXT,
                    `system` TEXT NOT NULL,
                    `subSystem` TEXT,
                    `severity` TEXT NOT NULL,
                    `urgency` TEXT NOT NULL,
                    `dtcCategory` TEXT,
                    `faultType` TEXT,
                    `monitorType` TEXT,
                    `readinessMonitor` TEXT,
                    `faultPersistence` TEXT,
                    `possibleCauses` TEXT,
                    `symptoms` TEXT,
                    `affectedComponents` TEXT,
                    `diagnosticSteps` TEXT,
                    `relatedCodes` TEXT,
                    `freezeFramePIDs` TEXT,
                    `liveDataThresholds` TEXT,
                    `repairComplexity` TEXT,
                    `drivabilityImpact` TEXT,
                    `repairCostUSD` TEXT,
                    `laborHoursEstimate` TEXT,
                    `diyFriendly` TEXT,
                    `specialToolsRequired` TEXT,
                    `repairVerification` TEXT,
                    `preventiveMaintenance` TEXT,
                    `milBehavior` TEXT,
                    `emissionsImpact` TEXT,
                    `warrantyNote` TEXT,
                    `cascadeRisk` TEXT,
                    `frequencyRank` TEXT,
                    `safeToResetWithoutRepair` TEXT,
                    `vehicleYearRange` TEXT,
                    `obd2Protocol` TEXT,
                    `countryRegulation` TEXT,
                    `obd2DiagnosticMode` TEXT,
                    `tsbBulletins` TEXT,
                    PRIMARY KEY(`code`, `manufacturer`)
                )
            """)
        }
    }

    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 17→18: Creating vehicle_dna_profiles table")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `vehicle_dna_profiles` (
                    `vehicleId` TEXT NOT NULL,
                    `baselineJson` TEXT NOT NULL,
                    `varianceJson` TEXT NOT NULL,
                    `forestJson` TEXT NOT NULL,
                    `confidence` REAL NOT NULL,
                    `lastTrainingDate` INTEGER NOT NULL,
                    PRIMARY KEY(`vehicleId`)
                )
            """)
        }
    }

    private val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 18→19: Creating repair_cases table")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `repair_cases` (
                    `id` TEXT NOT NULL,
                    `vehicleMake` TEXT NOT NULL,
                    `vehicleModel` TEXT NOT NULL,
                    `year` INTEGER NOT NULL,
                    `engine` TEXT NOT NULL,
                    `country` TEXT NOT NULL,
                    `dtcCode` TEXT NOT NULL,
                    `symptoms` TEXT NOT NULL,
                    `solution` TEXT NOT NULL,
                    `cost` REAL NOT NULL,
                    `timeSpent` INTEGER NOT NULL,
                    `partsUsed` TEXT NOT NULL,
                    `verified` INTEGER NOT NULL DEFAULT 0,
                    `votes` INTEGER NOT NULL DEFAULT 0,
                    `successRate` REAL NOT NULL DEFAULT 100.0,
                    `isBookmarked` INTEGER NOT NULL DEFAULT 0,
                    `isMyContribution` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
            """)
        }
    }

    private val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `prediction_events` (
                    `eventId` TEXT NOT NULL PRIMARY KEY,
                    `vehicleId` TEXT NOT NULL,
                    `severity` TEXT NOT NULL,
                    `confidence` REAL NOT NULL,
                    `message` TEXT NOT NULL,
                    `estimatedDays` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_prediction_events_vehicleId_createdAt` ON `prediction_events` (`vehicleId`, `createdAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_prediction_events_createdAt` ON `prediction_events` (`createdAt`)")
        }
    }

    private val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 20→21: Creating new feature tables")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `live_sessions` (
                    `sessionId` TEXT NOT NULL,
                    `vehicleId` TEXT NOT NULL,
                    `ownerId` TEXT NOT NULL,
                    `mechanicId` TEXT,
                    `status` TEXT NOT NULL,
                    `startedAt` INTEGER NOT NULL,
                    `endedAt` INTEGER,
                    `permissions` TEXT NOT NULL,
                    `sessionCode` TEXT NOT NULL,
                    `shareUrl` TEXT NOT NULL,
                    `durationMinutes` INTEGER NOT NULL,
                    `videoCallUrl` TEXT,
                    PRIMARY KEY(`sessionId`)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `live_snapshots` (
                    `snapshotId` TEXT NOT NULL,
                    `sessionId` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `pidValues` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    PRIMARY KEY(`snapshotId`)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `mechanic_notes` (
                    `noteId` TEXT NOT NULL,
                    `sessionId` TEXT NOT NULL,
                    `authorId` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`noteId`)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `repair_photos` (
                    `photoId` TEXT NOT NULL,
                    `caseId` TEXT NOT NULL,
                    `photoPath` TEXT NOT NULL,
                    `caption` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`photoId`)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `repair_parts` (
                    `partId` TEXT NOT NULL,
                    `caseId` TEXT NOT NULL,
                    `partNumber` TEXT NOT NULL,
                    `partName` TEXT NOT NULL,
                    `price` REAL NOT NULL,
                    `brand` TEXT NOT NULL,
                    PRIMARY KEY(`partId`)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `repair_votes` (
                    `id` TEXT NOT NULL,
                    `caseId` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `voteType` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `repair_comments` (
                    `commentId` TEXT NOT NULL,
                    `caseId` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `userName` TEXT NOT NULL,
                    `userReputation` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`commentId`)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `repair_verifications` (
                    `verificationId` TEXT NOT NULL,
                    `caseId` TEXT NOT NULL,
                    `verifierId` TEXT NOT NULL,
                    `verifierName` TEXT NOT NULL,
                    `verifierCredential` TEXT NOT NULL,
                    `verifiedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`verificationId`)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `service_requests` (
                    `requestId` TEXT NOT NULL,
                    `vehicleId` TEXT NOT NULL,
                    `problem` TEXT NOT NULL,
                    `priority` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `location` TEXT NOT NULL,
                    `radiusKm` REAL NOT NULL,
                    `status` TEXT NOT NULL,
                    `autoDtcCode` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`requestId`)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `service_bids` (
                    `bidId` TEXT NOT NULL,
                    `requestId` TEXT NOT NULL,
                    `shopId` TEXT NOT NULL,
                    `shopName` TEXT NOT NULL,
                    `shopRating` REAL NOT NULL,
                    `price` REAL NOT NULL,
                    `estimatedHours` REAL NOT NULL,
                    `warrantyDays` INTEGER NOT NULL,
                    `message` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`bidId`)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `evidence_packages` (
                    `packageId` TEXT NOT NULL,
                    `vehicleId` TEXT NOT NULL,
                    `eventType` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `gpsLocation` TEXT NOT NULL,
                    `videoPath` TEXT NOT NULL,
                    `audioPath` TEXT NOT NULL,
                    `pidSnapshot` TEXT NOT NULL,
                    `dtcs` TEXT NOT NULL,
                    `hashSha256` TEXT NOT NULL,
                    `signatureVersion` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`packageId`)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `vehicle_twin_profiles` (
                    `profileId` TEXT NOT NULL,
                    `vehicleId` TEXT NOT NULL,
                    `baselineJson` TEXT NOT NULL,
                    `varianceJson` TEXT NOT NULL,
                    `confidence` REAL NOT NULL,
                    `lastTrainingDate` INTEGER NOT NULL,
                    `anomalyCount` INTEGER NOT NULL,
                    `healthScore` INTEGER NOT NULL,
                    PRIMARY KEY(`profileId`)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `twin_anomalies` (
                    `anomalyId` TEXT NOT NULL,
                    `vehicleId` TEXT NOT NULL,
                    `parameter` TEXT NOT NULL,
                    `expectedValue` REAL NOT NULL,
                    `actualValue` REAL NOT NULL,
                    `deviation` REAL NOT NULL,
                    `severity` TEXT NOT NULL,
                    `confidence` REAL NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    PRIMARY KEY(`anomalyId`)
                )
            """)
            
            // Speed up local mechanical SO database searches
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_repair_cases_vehicleMake_vehicleModel` ON `repair_cases` (`vehicleMake`, `vehicleModel`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_repair_cases_dtcCode` ON `repair_cases` (`dtcCode`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_repair_cases_createdAt` ON `repair_cases` (`createdAt`)")
        }
    }

    private val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 21→22: Creating DTC Knowledge Graph tables")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `dtc_symptoms` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dtcCode` TEXT NOT NULL,
                    `manufacturer` TEXT NOT NULL DEFAULT 'GENERIC',
                    `symptomEs` TEXT NOT NULL,
                    `symptomEn` TEXT,
                    `probability` TEXT NOT NULL,
                    `isDriverNoticeable` INTEGER NOT NULL DEFAULT 1
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dtc_symptoms_dtcCode` ON `dtc_symptoms` (`dtcCode`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `dtc_causes` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dtcCode` TEXT NOT NULL,
                    `manufacturer` TEXT NOT NULL DEFAULT 'GENERIC',
                    `causeEs` TEXT NOT NULL,
                    `causeEn` TEXT,
                    `probability` TEXT NOT NULL,
                    `componentAffected` TEXT,
                    `isElectronic` INTEGER NOT NULL DEFAULT 0,
                    `isMechanical` INTEGER NOT NULL DEFAULT 0
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dtc_causes_dtcCode` ON `dtc_causes` (`dtcCode`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `dtc_procedures` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dtcCode` TEXT NOT NULL,
                    `manufacturer` TEXT NOT NULL DEFAULT 'GENERIC',
                    `stepNumber` INTEGER NOT NULL,
                    `titleEs` TEXT NOT NULL,
                    `descriptionEs` TEXT NOT NULL,
                    `toolRequired` TEXT,
                    `expectedValue` TEXT,
                    `estimatedMinutes` INTEGER NOT NULL DEFAULT 15,
                    `difficulty` TEXT NOT NULL DEFAULT 'medio',
                    `icon` TEXT NOT NULL DEFAULT '🔧'
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dtc_procedures_dtcCode` ON `dtc_procedures` (`dtcCode`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `dtc_related_pids` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dtcCode` TEXT NOT NULL,
                    `manufacturer` TEXT NOT NULL DEFAULT 'GENERIC',
                    `pidCommand` TEXT NOT NULL,
                    `pidNameEs` TEXT NOT NULL,
                    `pidNameEn` TEXT,
                    `normalRange` TEXT,
                    `unit` TEXT,
                    `priority` INTEGER NOT NULL DEFAULT 0
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dtc_related_pids_dtcCode` ON `dtc_related_pids` (`dtcCode`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `dtc_co_occurrences` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dtcCode` TEXT NOT NULL,
                    `relatedDtcCode` TEXT NOT NULL,
                    `correlationStrength` REAL NOT NULL DEFAULT 0.5,
                    `combinedDiagnosisEs` TEXT,
                    `combinedDiagnosisEn` TEXT
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dtc_co_occurrences_dtcCode` ON `dtc_co_occurrences` (`dtcCode`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dtc_co_occurrences_relatedDtcCode` ON `dtc_co_occurrences` (`relatedDtcCode`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `dtc_vehicle_compat` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dtcCode` TEXT NOT NULL,
                    `manufacturer` TEXT NOT NULL,
                    `make` TEXT NOT NULL,
                    `model` TEXT,
                    `yearFrom` INTEGER,
                    `yearTo` INTEGER,
                    `engineType` TEXT,
                    `specialNotesEs` TEXT
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dtc_vehicle_compat_dtcCode` ON `dtc_vehicle_compat` (`dtcCode`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dtc_vehicle_compat_manufacturer` ON `dtc_vehicle_compat` (`manufacturer`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `dtc_verified_fixes` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dtcCode` TEXT NOT NULL,
                    `manufacturer` TEXT NOT NULL DEFAULT 'GENERIC',
                    `fixDescriptionEs` TEXT NOT NULL,
                    `fixDescriptionEn` TEXT,
                    `successRate` REAL NOT NULL DEFAULT 0.0,
                    `voteCount` INTEGER NOT NULL DEFAULT 0,
                    `partRequired` TEXT,
                    `estimatedCostUsd` REAL,
                    `difficultyLevel` TEXT NOT NULL DEFAULT 'medio',
                    `source` TEXT,
                    `addedAt` INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dtc_verified_fixes_dtcCode` ON `dtc_verified_fixes` (`dtcCode`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `dtc_repair_costs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dtcCode` TEXT NOT NULL,
                    `manufacturer` TEXT NOT NULL DEFAULT 'GENERIC',
                    `region` TEXT NOT NULL DEFAULT 'LATAM',
                    `minCostUsd` REAL NOT NULL,
                    `maxCostUsd` REAL NOT NULL,
                    `laborHours` REAL,
                    `partsDescription` TEXT,
                    `currency` TEXT NOT NULL DEFAULT 'USD',
                    `source` TEXT,
                    `updatedAt` INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dtc_repair_costs_dtcCode` ON `dtc_repair_costs` (`dtcCode`)")
        }
    }

    private val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 22→23: Creating FTS Search Index table")
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `dtc_search_index` USING fts4(`code`, `descriptionEs`, `symptoms`, `causes`)")
        }
    }

    private val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 23→24: Creating Gauge Marketplace tables")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `saved_gauges` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `bgType` INTEGER NOT NULL,
                `bgPresetIndex` INTEGER NOT NULL,
                `bgImageUri` TEXT NOT NULL,
                `bezelStyle` INTEGER NOT NULL,
                `needleStyle` INTEGER NOT NULL,
                `ticksStyle` INTEGER NOT NULL,
                `accentColor` INTEGER NOT NULL,
                `accentColor2` INTEGER NOT NULL,
                `glowIntensity` REAL NOT NULL,
                `imageOpacity` REAL NOT NULL,
                `animationIndex` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `isPublished` INTEGER NOT NULL DEFAULT 0,
                `marketplaceId` TEXT,
                `thumbnailPath` TEXT
            )""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `gauge_listing_cache` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `creatorId` TEXT NOT NULL,
                `creatorName` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT,
                `configJson` TEXT NOT NULL,
                `thumbnailUrl` TEXT,
                `priceTier` INTEGER NOT NULL,
                `totalSales` INTEGER NOT NULL,
                `avgRating` REAL NOT NULL,
                `reviewCount` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                `cachedAt` INTEGER NOT NULL
            )""")
        }
    }

    private val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 24→25: Adding individual styling fields to dashboard_widgets")
            db.execSQL("ALTER TABLE `dashboard_widgets` ADD COLUMN `widgetStyle` TEXT")
            db.execSQL("ALTER TABLE `dashboard_widgets` ADD COLUMN `savedStyleId` TEXT")
        }
    }

    private val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 25→26: Adding typographyIndex and escrow columns")
            db.execSQL("ALTER TABLE `dashboard_widgets` ADD COLUMN `typographyIndex` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `saved_gauges` ADD COLUMN `typographyIndex` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `service_requests` ADD COLUMN `escrowStatus` TEXT DEFAULT 'NONE'")
            db.execSQL("ALTER TABLE `service_requests` ADD COLUMN `paymentId` TEXT")
        }
    }

    private fun createMechanicalKnowledgeTables(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `meet_knowledge_matrix` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `dtcCode` TEXT,
                `componentName` TEXT,
                `systemCategory` TEXT,
                `urgencyLevel` TEXT,
                `layerDiagnosticsJson` TEXT NOT NULL,
                `layerRebuildSpecsJson` TEXT NOT NULL,
                `layerTrenchKnowledgeJson` TEXT NOT NULL,
                `layerAdvancedEngJson` TEXT NOT NULL,
                `lastUpdated` INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_meet_knowledge_matrix_dtcCode_componentName_unique` ON `meet_knowledge_matrix` (`dtcCode`, `componentName`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meet_knowledge_matrix_dtcCode` ON `meet_knowledge_matrix` (`dtcCode`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meet_knowledge_matrix_componentName` ON `meet_knowledge_matrix` (`componentName`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meet_knowledge_matrix_systemCategory` ON `meet_knowledge_matrix` (`systemCategory`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meet_knowledge_matrix_urgencyLevel` ON `meet_knowledge_matrix` (`urgencyLevel`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `mechanical_procedures` (
                `componentId` TEXT NOT NULL PRIMARY KEY,
                `system` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `difficulty` INTEGER NOT NULL,
                `estimatedTimeHours` REAL NOT NULL,
                `searchKeywords` TEXT NOT NULL,
                `payloadJson` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mechanical_procedures_componentId` ON `mechanical_procedures` (`componentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mechanical_procedures_system` ON `mechanical_procedures` (`system`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mechanical_procedures_difficulty` ON `mechanical_procedures` (`difficulty`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `component_rebuild_guides` (
                `componentId` TEXT NOT NULL PRIMARY KEY,
                `rebuildPossible` INTEGER NOT NULL,
                `searchKeywords` TEXT NOT NULL,
                `payloadJson` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_component_rebuild_guides_componentId` ON `component_rebuild_guides` (`componentId`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `symptom_guides` (
                `symptomId` TEXT NOT NULL PRIMARY KEY,
                `title` TEXT NOT NULL,
                `dangerLevel` TEXT NOT NULL,
                `searchKeywords` TEXT NOT NULL,
                `relatedDtcs` TEXT NOT NULL,
                `payloadJson` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_symptom_guides_symptomId` ON `symptom_guides` (`symptomId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_symptom_guides_dangerLevel` ON `symptom_guides` (`dangerLevel`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `trench_knowledge` (
                `scenarioId` TEXT NOT NULL PRIMARY KEY,
                `title` TEXT NOT NULL,
                `riskLevel` TEXT NOT NULL,
                `searchKeywords` TEXT NOT NULL,
                `payloadJson` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trench_knowledge_scenarioId` ON `trench_knowledge` (`scenarioId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trench_knowledge_riskLevel` ON `trench_knowledge` (`riskLevel`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `automotive_chemistry` (
                `chemicalId` TEXT NOT NULL PRIMARY KEY,
                `category` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `searchKeywords` TEXT NOT NULL,
                `payloadJson` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automotive_chemistry_chemicalId` ON `automotive_chemistry` (`chemicalId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automotive_chemistry_category` ON `automotive_chemistry` (`category`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `tool_usage_guides` (
                `toolId` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `searchKeywords` TEXT NOT NULL,
                `payloadJson` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tool_usage_guides_toolId` ON `tool_usage_guides` (`toolId`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `safety_protocols` (
                `protocolId` TEXT NOT NULL PRIMARY KEY,
                `system` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `mandatoryBefore` TEXT NOT NULL,
                `searchKeywords` TEXT NOT NULL,
                `payloadJson` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_safety_protocols_protocolId` ON `safety_protocols` (`protocolId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_safety_protocols_system` ON `safety_protocols` (`system`)")
    }

    private fun seedMechanicalKnowledge(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.execSQL("""
            INSERT OR REPLACE INTO `safety_protocols`
            (`protocolId`, `system`, `title`, `mandatoryBefore`, `searchKeywords`, `payloadJson`, `updatedAt`)
            VALUES (
                'hot_engine_fluids',
                'engine',
                'Motor caliente y fluidos inflamables',
                'oil_leak;coolant_leak;fuel_smell;engine_bay_work',
                'seguridad motor caliente aceite fuga fluido inflamable gato elevacion',
                '{"ppe":["guantes de nitrilo","gafas","luz de inspeccion fria"],"steps":["Apagar motor y esperar hasta que el escape y el aceite bajen de temperatura","No rociar desengrasante sobre colector o catalizador caliente","Si hay que levantar el vehiculo, usar torres certificadas y calzar ruedas"],"fatal_risks":["quemaduras","incendio por solventes","aplastamiento"],"common_mistakes":["meter manos cerca del ventilador electrico","usar solo el gato hidraulico como soporte"]}',
                $now
            )
        """)
        db.execSQL("""
            INSERT OR REPLACE INTO `symptom_guides`
            (`symptomId`, `title`, `dangerLevel`, `searchKeywords`, `relatedDtcs`, `payloadJson`, `updatedAt`)
            VALUES (
                'oil_leak',
                'Como diagnosticar una fuga de aceite',
                'medium',
                'fuga aceite oil leak mancha goteo carter tapa valvulas reten motor',
                'P0520,P0521,P0522,P0523,P0010,P0011,P0014',
                '{"safety_protocols":["hot_engine_fluids"],"first_checks":["Confirmar si el fluido es aceite: color ambar/cafe/negro y tacto lubricante","Revisar nivel de aceite antes de arrancar","Ubicar el punto mas alto humedo, no solo la gota en el piso"],"diagnostic_tree":["Lavar zona con desengrasante seguro y secar","Agregar UV dye si la fuga no es evidente","Inspeccionar de arriba hacia abajo: tapa de valvulas, VVT, filtro, enfriador, carter, retenes","Arrancar 5-10 minutos y observar con lampara UV","Prueba de carretera corta y reinspeccion","Si aparece aceite entre motor y caja, sospechar reten trasero o fuga superior que escurre"],"most_common_causes_ranked":["Tapa de valvulas o junta superior","Filtro de aceite flojo o doble empaque","Tapon/arandela de carter","Sensor de presion de aceite","Carter golpeado o junta RTV fallida","Reten delantero/trasero"],"tools_required":["linterna","espejo telescopico","limpiador de frenos/desengrasante","UV dye","lampara UV","torquimetro"],"fluids_or_dyes":["UV dye compatible con aceite","desengrasante no inflamable en frio"],"confirmation_tests":["Nivel estable despues de reparar","Sin rastro UV nuevo despues de prueba de manejo","Sin olor a aceite quemado en escape"],"false_diagnosis_risks":["Cambiar carter cuando la fuga baja desde tapa de valvulas","Confundir aceite de direccion/transmision con aceite de motor"],"related_components":["valve_cover_gasket","oil_pan_gasket","oil_pressure_sensor","front_crank_seal","rear_main_seal"],"related_procedures":["valve_cover_gasket","oil_pan_gasket"],"common_mistakes":["apretar de mas tornillos pequeños en aluminio","usar RTV donde va junta seca","no limpiar respiradero PCV"],"when_not_to_diy":["goteo sobre escape caliente","perdida rapida de nivel","fuga entre motor y transmision","vehiculo hibrido/EV con zona HV cercana"]}',
                $now
            )
        """)
        db.execSQL("""
            INSERT OR REPLACE INTO `mechanical_procedures`
            (`componentId`, `system`, `title`, `difficulty`, `estimatedTimeHours`, `searchKeywords`, `payloadJson`, `updatedAt`)
            VALUES (
                'valve_cover_gasket',
                'engine_lubrication',
                'Cambio de empaque de tapa de valvulas',
                2,
                1.5,
                'tapa valvulas empaque fuga aceite valve cover gasket',
                '{"required_tools":["dado 10mm","torquimetro bajo rango","raspador plastico"],"required_consumables":["empaque nuevo","RTV solo en esquinas indicadas","limpiador de frenos"],"safety_warnings":["motor frio","desconectar bateria si se retiran bobinas"],"removal_steps":["Retirar cubierta decorativa y bobinas si aplica","Desconectar PCV y arneses sin jalar cables","Aflojar tornillos en cruz","Levantar tapa sin deformarla"],"installation_steps":["Limpiar superficies sin rayar aluminio","Aplicar RTV solo en medias lunas/esquinas segun diseno","Instalar empaque y apretar en secuencia"],"torque_specs":["tornillos tapa valvulas tipico 7-10 Nm; verificar manual"],"post_install_tests":["arranque 10 min","inspeccion con luz","prueba de manejo y reinspeccion"],"common_mistakes":["exceso de RTV","sobretorque","manguera PCV agrietada no reemplazada"],"when_not_to_diy":["tornillos barridos","tapa plastica deformada","fuga cerca de arnes principal"]}',
                $now
            )
        """)
        db.execSQL("""
            INSERT OR REPLACE INTO `mechanical_procedures`
            (`componentId`, `system`, `title`, `difficulty`, `estimatedTimeHours`, `searchKeywords`, `payloadJson`, `updatedAt`)
            VALUES (
                'oil_pan_gasket',
                'engine_lubrication',
                'Cambio de junta o sellado de carter',
                4,
                3.0,
                'carter aceite fuga oil pan gasket rtv tapon drenaje',
                '{"required_tools":["torquimetro","raspador plastico","charola de drenaje"],"required_consumables":["aceite","filtro","RTV especificado o junta"],"safety_warnings":["usar torres","no trabajar bajo vehiculo sostenido solo por gato"],"removal_steps":["Drenar aceite","Retirar protectores","Soltar carter siguiendo secuencia","Evitar palancas que doblen aluminio"],"installation_steps":["Limpiar pestañas","Aplicar cordon RTV continuo si aplica","Presentar carter antes de que cure","Torque en secuencia"],"post_install_tests":["llenar aceite","verificar presion/luz de aceite","inspeccion en frio y caliente"],"common_mistakes":["demasiado RTV dentro del motor","contaminar superficie con aceite antes de sellar"],"when_not_to_diy":["subchasis bloquea carter","escape debe desmontarse","roscas dañadas en bloque"]}',
                $now
            )
        """)
        db.execSQL("""
            INSERT OR REPLACE INTO `automotive_chemistry`
            (`chemicalId`, `category`, `name`, `searchKeywords`, `payloadJson`, `updatedAt`)
            VALUES (
                'uv_oil_dye',
                'diagnostic_dye',
                'Tinte UV para aceite',
                'uv dye tinte aceite fuga lampara ultravioleta',
                '{"use_cases":["fugas pequeñas o intermitentes de aceite","confirmar reparacion despues de limpieza"],"how_it_works":"Circula con el aceite y deja rastro fluorescente en el punto real de fuga.","safe_materials":["aceite de motor compatible segun etiqueta"],"unsafe_materials":["no usar en sistemas donde el fabricante prohiba aditivos"],"application_time_minutes":[10,30],"temporary_fix":false,"can_cause_damage":false,"do_not_use_when":["nivel de aceite criticamente bajo","motor con daño interno evidente"]}',
                $now
            )
        """)
        db.execSQL("""
            INSERT OR REPLACE INTO `tool_usage_guides`
            (`toolId`, `name`, `searchKeywords`, `payloadJson`, `updatedAt`)
            VALUES (
                'uv_lamp',
                'Lampara UV de inspeccion',
                'lampara uv ultravioleta tinte aceite fuga inspeccion',
                '{"allowed_use_cases":["rastrear tinte UV en aceite, refrigerante o A/C segun producto"],"forbidden_use_cases":["apuntar a ojos","usar como unica prueba sin limpiar primero"],"ppe_required":["gafas"],"vehicle_risk_zones":["correas","ventilador electrico","escape caliente"],"fire_risk":false,"precision_risk":"low","safer_alternatives":["talco tecnico para fugas externas evidentes"]}',
                $now
            )
        """)
        db.execSQL("""
            INSERT OR REPLACE INTO `trench_knowledge`
            (`scenarioId`, `title`, `riskLevel`, `searchKeywords`, `payloadJson`, `updatedAt`)
            VALUES (
                'seized_oil_pan_bolt',
                'Tornillo de carter o tapa trabado',
                'high',
                'tornillo carter barrido pegado oxidado extractor helicoil aceite',
                '{"escalation_ladder":["limpiar cabeza","dado de 6 puntas correcto","golpe seco controlado","penetrante 20-60 min","calor muy controlado lejos de aceite/solventes","extractor","soldar tuerca solo si no hay riesgo de incendio","reparacion de rosca helicoil/timesert"],"heat_allowed":true,"heat_forbidden_zones":["cerca de combustible","despues de rociar solvente","cerca de retenes"],"cutting_allowed":true,"fire_risk_warnings":["aceite y desengrasante pueden encender sobre escape"],"thread_repair_options":["helicoil","timesert","tapon sobredimensionado como solucion temporal"]}',
                $now
            )
        """)
        db.execSQL("""
            INSERT OR REPLACE INTO `meet_knowledge_matrix`
            (`dtcCode`, `componentName`, `systemCategory`, `urgencyLevel`, `layerDiagnosticsJson`, `layerRebuildSpecsJson`, `layerTrenchKnowledgeJson`, `layerAdvancedEngJson`, `lastUpdated`)
            VALUES (
                'P0520',
                'Sensor de presion de aceite',
                'Powertrain - Lubrication',
                'pronto',
                '{"related_symptom_guides":["oil_leak"],"diagnostic_steps":["verificar nivel","inspeccionar fuga en sensor","medir presion mecanica si hay luz de aceite"],"confirmation_tests":["presion real dentro de especificacion","sin fuga en rosca/conector"]}',
                '{"electrical_specs":["5V referencia segun diseño","señal variable o switch segun motor"],"torque_specs":["ver manual; sensores en aluminio requieren bajo torque y sellador correcto"]}',
                '{"related_trench_knowledge":["seized_oil_pan_bolt"],"common_shop_notes":["no confundir sensor mojado por fuga superior con sensor defectuoso"]}',
                '{"scope_patterns":["verificar estabilidad de señal si aplica"],"ev_hvac_notes":[]}',
                $now
            )
        """)

        db.execSQL("""
            INSERT OR REPLACE INTO `safety_protocols`
            (`protocolId`, `system`, `title`, `mandatoryBefore`, `searchKeywords`, `payloadJson`, `updatedAt`)
            VALUES
            (
                'srs_airbag',
                'restraint_system',
                'SRS / Airbag / Pretensores',
                'steering_wheel_dash_seatbelt_module_work',
                'airbag srs pretensor volante tablero seguridad bateria capacitores',
                '{"ppe":["gafas","guantes"],"steps":["Desconectar terminal negativo y esperar minimo 10 minutos antes de tocar conectores SRS","No medir resistencia directa sobre infladores o pretensores","Transportar airbags con cubierta hacia arriba"],"fatal_risks":["despliegue accidental","lesiones faciales","daño de modulo"],"common_mistakes":["usar multimetro comun en circuito de detonacion","dejar bateria conectada","poner modulo boca abajo"]}',
                $now
            ),
            (
                'hybrid_high_voltage',
                'hev_ev',
                'Alto voltaje HEV / EV',
                'battery_pack_inverter_ac_compressor_orange_cables',
                'hibrido electrico alto voltaje guantes clase 0 loto desconexion servicio',
                '{"ppe":["guantes clase 0 certificados","proteccion facial","candado LOTO"],"steps":["Verificar fabricante y procedimiento OEM","Retirar service plug y asegurar bloqueo/etiquetado","Confirmar ausencia de voltaje con equipo aprobado CAT III/CAT IV"],"fatal_risks":["electrocucion","arco electrico","arranque automatico de compresor electrico"],"common_mistakes":["confiar solo en que el vehiculo esta apagado","trabajar sin verificar ausencia de voltaje","tocar cables naranja sin aislamiento"]}',
                $now
            ),
            (
                'fuel_pressure_release',
                'fuel_system',
                'Despresurizacion de combustible',
                'injector_rail_fuel_line_pump_filter_work',
                'combustible presion inyeccion gasolina incendio riel inyector',
                '{"ppe":["gafas","guantes resistentes a hidrocarburos"],"steps":["Desactivar bomba segun OEM o retirar fusible/relay y agotar motor si aplica","Capturar derrames con trapo absorbente y charola","No usar calor ni chispas en zona de trabajo"],"fatal_risks":["incendio","lesion ocular","salpicadura a escape caliente"],"common_mistakes":["abrir linea con motor caliente","trabajar cerca de baterias cargando","no ventilar area"]}',
                $now
            )
        """)

        db.execSQL("""
            INSERT OR REPLACE INTO `symptom_guides`
            (`symptomId`, `title`, `dangerLevel`, `searchKeywords`, `relatedDtcs`, `payloadJson`, `updatedAt`)
            VALUES
            (
                'coolant_leak',
                'Como diagnosticar una fuga de refrigerante',
                'high',
                'fuga agua refrigerante coolant leak radiador manguera deposito bomba agua',
                'P0117,P0118,P0125,P0128,P0217',
                '{"safety_protocols":["hot_engine_fluids"],"first_checks":["Nunca abrir tapon con el motor a temperatura","Confirmar color y olor del fluido","Revisar nivel en deposito y signos de sobrecalentamiento"],"diagnostic_tree":["Presurizar sistema en frio con bomba de prueba","Inspeccionar tanque, radiador, mangueras, bomba de agua, housing de termostato y calefaccion","Usar tinte UV si la fuga aparece solo en caliente","Verificar interior por olor dulce o vidrio empañado para descartar heater core","Si no hay fuga externa, realizar prueba de CO2 o leak-down por posible empaque de culata"],"most_common_causes_ranked":["Manguera envejecida o abrazadera floja","Depósito fisurado","Bomba de agua con sello vencido","Radiador con microfisura","Housing/termostato deformado","Empaque de culata"],"tools_required":["bomba presurizadora","linterna","lampara UV","detector de CO2"],"fluids_or_dyes":["tinte UV compatible con refrigerante"],"confirmation_tests":["sistema mantiene presion especificada","nivel estable varios ciclos termicos","ventiladores operan normal"],"false_diagnosis_risks":["confundir agua de A/C con refrigerante","reemplazar radiador cuando la fuga viene del deposito"],"related_components":["radiator","water_pump","thermostat_housing","heater_core"],"related_procedures":["water_pump_replacement"],"common_mistakes":["mezclar refrigerantes incompatibles","apretar abrazaderas en cuello plastico fragil"],"when_not_to_diy":["sobrecalentamiento severo","mezcla aceite-refrigerante","vehiculo HEV/EV con circuito HV de enfriamiento"]}',
                $now
            ),
            (
                'alternator_not_charging',
                'Como diagnosticar alternador que no carga',
                'medium',
                'alternador no carga bateria descarga luz bateria system voltage low',
                'P0560,P0562,P0563,P0620',
                '{"safety_protocols":["fuel_pressure_release"],"first_checks":["Verificar tension de banda y estado del tensor","Medir voltaje KOEO y con motor en marcha","Confirmar que la luz de bateria o mensaje de carga este presente"],"diagnostic_tree":["Prueba de caida de voltaje B+ y tierra bajo carga maxima","Medir ripple AC en bateria","Verificar señal LIN/COM/FR si el sistema es inteligente","Comprobar fusible megafuse y continuidad del cable B+","Si el alternador responde pero no estabiliza, revisar IBS/BMS y demanda del modulo"],"most_common_causes_ranked":["Regulador interno defectuoso","Diodo rectificador dañado","Cable B+ sulfatado o megafuse abierto","Tierra deficiente entre motor y bateria","Tensor o polea overrunning fallando"],"tools_required":["multimetro","pinza amperimetrica","osciloscopio opcional"],"confirmation_tests":["13.5-14.8V tipico segun estrategia OEM","ripple AC menor a 0.5V","caida de voltaje dentro de especificacion"],"false_diagnosis_risks":["cambiar bateria cuando el problema es cableado","culpar alternador cuando el BMS ordena baja carga"],"related_components":["alternator","battery","serpentine_belt","battery_sensor"],"related_procedures":["alternator_replacement"],"when_not_to_diy":["vehiculos con alternador refrigerado por liquido","sistemas inteligentes LIN/BSS sin diagrama"]}',
                $now
            ),
            (
                'hard_start',
                'Como diagnosticar arranque lento o dificil',
                'medium',
                'arranque lento hard start no arranca bateria motor arranque combustible chispa',
                'P0335,P0340,P0562,P0563,P0685',
                '{"safety_protocols":["fuel_pressure_release"],"first_checks":["Definir si el motor no gira, gira lento o gira normal pero no enciende","Verificar voltaje de bateria en reposo y durante cranking","Escuchar clic de solenoide o caida de voltaje severa"],"diagnostic_tree":["Prueba de caida de voltaje en circuito de arranque","Prueba de consumo amperimetrico del motor de arranque","Confirmar chispa, combustible y señal CKP/CMP si gira pero no enciende","Verificar presion residual de combustible en caliente","Comprobar inmovilizador si hay arranque y corte inmediato"],"most_common_causes_ranked":["Bateria sulfatada o baja","Terminales/tierra con alta resistencia","Motor de arranque desgastado","Bomba de combustible debil","Sensor CKP intermitente"],"tools_required":["multimetro","pinza amperimetrica","manometro de combustible"],"confirmation_tests":["caida de voltaje dentro de rango","rpm de cranking adecuadas","presion/encendido presentes"],"related_components":["battery","starter_motor","fuel_pump","crankshaft_sensor"],"related_procedures":["starter_replacement"],"when_not_to_diy":["vehiculos con inmovilizador activo","motor trabado mecanicamente"]}',
                $now
            ),
            (
                'spongy_brake_pedal',
                'Como diagnosticar pedal de freno esponjoso',
                'critical',
                'freno esponjoso pedal largo abs fuga liquido aire lineas',
                'C1201,C1234,C0020',
                '{"safety_protocols":["hot_engine_fluids"],"first_checks":["No conducir si el pedal se va al piso","Revisar nivel y color del liquido","Inspeccionar fugas en ruedas, flexible, cilindro maestro y ABS"],"diagnostic_tree":["Purgar en secuencia OEM","Verificar expansion de mangueras flexibles bajo presion","Pinzar circuitos para aislar si el maestro colapsa internamente","Activar purga ABS con escaner si se abrio el modulo o el deposito se vacio","Comprobar ajuste de zapatas traseras en tambores si aplica"],"most_common_causes_ranked":["Aire en sistema","fuga externa","cilindro maestro by-pass interno","mangueras abombadas","ABS con aire atrapado"],"tools_required":["purga asistida o vacio","llave de purga","escaner con ABS bleed"],"confirmation_tests":["pedal firme motor apagado y encendido","sin fuga residual","frenada recta"],"related_components":["master_cylinder","brake_hose","abs_hcu"],"related_procedures":["brake_service_full"],"when_not_to_diy":["sistema ABS/ESC con purga por escaner obligatoria","lineas severamente corroídas"]}',
                $now
            )
        """)

        db.execSQL("""
            INSERT OR REPLACE INTO `mechanical_procedures`
            (`componentId`, `system`, `title`, `difficulty`, `estimatedTimeHours`, `searchKeywords`, `payloadJson`, `updatedAt`)
            VALUES
            (
                'alternator_replacement',
                'charging_system',
                'Cambio de alternador',
                3,
                1.8,
                'alternador reemplazo charging system bateria luz carga',
                '{"required_tools":["dados y llaves segun acceso","torquimetro","multimetro"],"required_consumables":["banda nueva si esta agrietada","limpiador de terminales"],"safety_warnings":["desconectar bateria negativa","no tocar B+ con herramienta a masa"],"removal_steps":["Registrar radio/codigos si aplica","Quitar tension de banda","Desconectar conector de control y terminal B+","Retirar pernos soporte sin forzar carcasa"],"installation_steps":["Comparar polea y offset antes de montar","Limpiar superficies de masa","Apretar soportes y terminal B+ a torque OEM","Reinstalar banda y verificar alineacion"],"torque_specs":["soporte alternador tipico 35-55 Nm","terminal B+ tipico 9-15 Nm; verificar OEM"],"electrical_specs":["carga tipica 13.5-14.8V","ripple AC <0.5V"],"post_install_tests":["medir caida de voltaje bajo carga maxima","verificar testigo de bateria apagado","revisar tension de banda"],"common_mistakes":["arrancar sin conectar arnes de control","contaminar polea con aceite"],"when_not_to_diy":["alternador refrigerado por agua","acceso requiere desmontar frente completo"]}',
                $now
            ),
            (
                'starter_replacement',
                'starting_system',
                'Cambio de motor de arranque',
                3,
                2.0,
                'motor arranque starter solenoide no gira clic',
                '{"required_tools":["dados profundos","extension","torquimetro"],"required_consumables":["limpiador de terminales","grasa dielectrica ligera para conector de mando"],"safety_warnings":["desconectar bateria","soportar vehiculo con torres si acceso inferior"],"removal_steps":["Confirmar que el fallo no sea bateria/cableado","Desconectar cable B+ y terminal de mando","Retirar pernos de montaje y bajar el conjunto"],"installation_steps":["Inspeccionar dientes de corona visibles","Montar arranque sin pellizcar arnes","Apretar pernos y cables al torque correcto"],"torque_specs":["pernos de arranque tipico 40-60 Nm","terminal solenoide pequeño bajo torque"],"electrical_specs":["caida de voltaje positiva <0.2V por tramo","tierra <0.1V por tramo"],"post_install_tests":["cranking estable","sin ruido de engrane","caida de voltaje dentro de rango"],"common_mistakes":["condenar el arranque sin prueba de voltaje","dejar flojo cable B+"],"when_not_to_diy":["arranque integrado con soportes complejos","acceso junto a catalizador extremadamente caliente"]}',
                $now
            ),
            (
                'water_pump_replacement',
                'cooling_system',
                'Cambio de bomba de agua',
                4,
                3.5,
                'bomba agua water pump fuga coolant sobrecalentamiento',
                '{"required_tools":["juego de dados","torquimetro","charola","herramienta de tensado segun motor"],"required_consumables":["refrigerante correcto","junta o RTV OEM"],"safety_warnings":["motor completamente frio","despresurizar sistema"],"removal_steps":["Drenar refrigerante","Retirar banda/accesorios o cubierta de distribucion segun diseño","Desmontar bomba sin golpear superficies"],"installation_steps":["Limpiar superficie","instalar junta o sellador segun OEM","torque en cruz","llenado y purga de aire"],"torque_specs":["pernos bomba tipico 10-25 Nm; verificar OEM"],"post_install_tests":["prueba de presion en frio","ciclos termicos con calefaccion","verificar ventiladores"],"common_mistakes":["mezclar refrigerantes","no purgar aire"],"when_not_to_diy":["bomba impulsada por cadena/correa interna","motores con timing critico"]}',
                $now
            ),
            (
                'brake_service_full',
                'braking_system',
                'Servicio completo de frenos',
                3,
                2.5,
                'frenos pastillas discos caliper purga pedal',
                '{"required_tools":["gato y torres","torquimetro","herramienta retraer piston","purga"],"required_consumables":["pastillas","grasa de frenos","liquido DOT especificado"],"safety_warnings":["no inhalar polvo","no contaminar friccion con grasa"],"removal_steps":["inspeccionar espesor y desgaste disparejo","retirar caliper y soporte","medir disco y runout si aplica"],"installation_steps":["limpiar cubo","lubricar puntos correctos","retraer piston con procedimiento adecuado","torque ruedas y soportes"],"torque_specs":["tuercas rueda segun OEM","pernos soporte caliper tipico 70-120 Nm"],"post_install_tests":["pedal firme antes de mover","asentamiento de pastillas","verificar fugas y nivel"],"common_mistakes":["dejar colgar caliper por manguera","usar grasa donde toca friccion"],"when_not_to_diy":["EPB electronico sin modo servicio","ABS requiere bleed por escaner"]}',
                $now
            ),
            (
                'windshield_replacement',
                'body_glass',
                'Cambio de parabrisas',
                5,
                4.0,
                'parabrisas windshield vidrio uretano adas calibracion',
                '{"required_tools":["cuerda o cuchilla fria","ventosas","pistola para uretano"],"required_consumables":["primer","uretano automotriz","clips nuevos si aplica"],"safety_warnings":["usar guantes anticorte","proteger tablero y pintura"],"removal_steps":["retirar molduras y sensores","cortar uretano sin dañar pinchweld","extraer vidrio con ventosas"],"installation_steps":["tratar corrosion del marco si existe","aplicar primer segun fabricante","cordon continuo de uretano","colocar vidrio y respetar tiempo safe-drive-away"],"post_install_tests":["prueba de fugas de agua","verificar ADAS/camara y calibrar si aplica"],"common_mistakes":["usar uretano sin tiempo de curado correcto","no calibrar ADAS"],"when_not_to_diy":["vehiculos con camaras/radar en parabrisas","pinchweld oxidado o deformado"]}',
                $now
            )
        """)

        db.execSQL("""
            INSERT OR REPLACE INTO `component_rebuild_guides`
            (`componentId`, `rebuildPossible`, `searchKeywords`, `payloadJson`, `updatedAt`)
            VALUES
            (
                'alternator',
                1,
                'alternador reconstruccion diodos rotor estator regulador escobillas ripple',
                '{"internal_parts":["puente rectificador","regulador","rotor","estator","anillos rozantes","rodamientos","escobillas"],"bench_tests":["ripple AC en bateria <0.5V","prueba de diodos en modo diode","resistencia rotor tipica 2-6 ohm segun diseño","aislamiento rotor/masa infinito"],"failure_signatures":["luces que pulsan","ripple alto","rodamiento ruidoso","sobrecarga >15V"],"minimum_service_limits":["anillos rozantes sin surcos profundos","longitud minima de escobillas segun kit","rodamiento sin juego axial"],"rebuild_steps":["desarmar y marcar carcasa","prueba individual de diodos","rectificar o reemplazar anillos","instalar rodamientos/escobillas/regulador","prueba de banco antes de reinstalar"],"replace_vs_rebuild_decision":["reconstruir si carcasa/estator son recuperables y partes de calidad disponibles","reemplazar si hay daño severo por calor o carcasa fracturada"],"quality_control_tests":["voltaje estable bajo carga","ripple bajo","sin ruido mecanico"]}',
                $now
            ),
            (
                'starter_motor',
                1,
                'arranque reconstruccion solenoide bendix inducido escobillas bujes',
                '{"internal_parts":["solenoide","bendix/overrunning clutch","inducido","escobillas","portaescobillas","bujes/rodamientos"],"bench_tests":["consumo de corriente comparado con especificacion","caida de voltaje interna","desplazamiento correcto del bendix"],"failure_signatures":["clic sin giro","giro lento con bateria sana","ruido de engrane"],"minimum_service_limits":["conmutador sin segmentos quemados","bujes con juego dentro de tolerancia","escobillas sobre longitud minima"],"rebuild_steps":["desarmar y limpiar sin contaminar embrague unidireccional","medir continuidad y cortos a masa del inducido","cambiar bujes/escobillas/solenoide segun daño","probar en banco"],"replace_vs_rebuild_decision":["reconstruir si inducido y carcasa son recuperables","reemplazar si el inducido esta carbonizado o el nose cone roto"],"quality_control_tests":["enganche consistente","velocidad de giro correcta","sin sobreconsumo"]}',
                $now
            )
        """)

        db.execSQL("""
            INSERT OR REPLACE INTO `automotive_chemistry`
            (`chemicalId`, `category`, `name`, `searchKeywords`, `payloadJson`, `updatedAt`)
            VALUES
            (
                'penetrating_oil_pb',
                'penetrant',
                'Penetrante tipo PB Blaster / Kroil',
                'penetrante pb blaster kroil tornillo oxidado trabado',
                '{"use_cases":["tornilleria oxidada","sensores roscados","uniones expuestas a sal"],"how_it_works":"Disuelve corrosion y reduce friccion capilarmente si se le da tiempo de saturacion.","safe_materials":["acero","hierro"],"unsafe_materials":["gomas sensibles segun producto"],"application_time_minutes":[20,60],"temporary_fix":false,"can_cause_damage":false,"do_not_use_when":["superficie extremadamente caliente","cerca de llama abierta"]}',
                $now
            ),
            (
                'atf_acetone_mix',
                'penetrant',
                'Mezcla ATF + acetona',
                'atf acetona penetrante casero extractor perno',
                '{"use_cases":["extraccion de pernos muy trabados cuando se prepara la mezcla justo antes de aplicar"],"how_it_works":"La acetona reduce viscosidad y ayuda a transportar el ATF a la rosca; es volatil e inflamable.","safe_materials":["acero"],"unsafe_materials":["plasticos y pinturas sensibles"],"application_time_minutes":[10,30],"temporary_fix":false,"can_cause_damage":true,"do_not_use_when":["cerca de chispa/llama","sobre componentes pintados delicados","en interiores sin ventilacion"]}',
                $now
            ),
            (
                'maf_cleaner',
                'sensor_cleaner',
                'Limpiador MAF',
                'maf cleaner sensor flujo aire contaminacion',
                '{"use_cases":["sensor MAF contaminado por polvo/aceite"],"how_it_works":"Evapora sin residuo y remueve contaminantes sin atacar la pelicula caliente.","safe_materials":["elemento MAF"],"unsafe_materials":["cuerpo de aceleracion en lugar de MAF"],"application_time_minutes":[5,10],"temporary_fix":false,"can_cause_damage":false,"do_not_use_when":["sensor roto fisicamente","usar cepillo o aire a alta presion"]}',
                $now
            ),
            (
                'dielectric_grease',
                'electrical_protection',
                'Grasa dielectrica',
                'grasa dielectrica bobina conector humedad',
                '{"use_cases":["botas de bobina","sellado ambiental en conectores no de alta presion de contacto"],"how_it_works":"Desplaza humedad y previene corrosion superficial.","safe_materials":["gomas y conectores apropiados"],"unsafe_materials":["superficies donde se requiere friccion seca especifica"],"application_time_minutes":[1,1],"temporary_fix":false,"can_cause_damage":false,"do_not_use_when":["rellenar terminales hembra completamente","pretender reparar caida de voltaje con grasa"]}',
                $now
            )
        """)

        db.execSQL("""
            INSERT OR REPLACE INTO `tool_usage_guides`
            (`toolId`, `name`, `searchKeywords`, `payloadJson`, `updatedAt`)
            VALUES
            (
                'induction_heater',
                'Calentador por induccion',
                'induccion calor extractor perno tuerca sin llama',
                '{"allowed_use_cases":["tuercas/pernos ferrosos cercanos a lineas donde llama es riesgosa"],"forbidden_use_cases":["material no ferroso que no responde","componentes con electronica sensible muy cercana"],"ppe_required":["guantes","gafas"],"vehicle_risk_zones":["arneses cercanos","sensores plastico"],"fire_risk":false,"precision_risk":"medium","safer_alternatives":["penetrante y impacto controlado"]}',
                $now
            ),
            (
                'angle_grinder_metabo',
                'Esmeril angular / Metabo',
                'metabo esmeril angular corte destructivo perno trabado',
                '{"allowed_use_cases":["extraccion destructiva de tornilleria o abrazaderas cuando otros escalones fallaron"],"forbidden_use_cases":["cerca de tanque/lineas de combustible","cerca de vidrio o modulos sin proteccion"],"ppe_required":["careta","guantes","proteccion auditiva"],"vehicle_risk_zones":["mangueras de freno","arneses","sellos","vidrio"],"fire_risk":true,"precision_risk":"high","safer_alternatives":["induccion","soldar tuerca","extractor"]}',
                $now
            ),
            (
                'smoke_machine',
                'Maquina de humo',
                'smoke machine evap admision fuga vacio',
                '{"allowed_use_cases":["EVAP","fugas de vacio/admision"],"forbidden_use_cases":["presurizar escape caliente","exceder presion especificada"],"ppe_required":["gafas"],"vehicle_risk_zones":["tanque EVAP si se excede presion"],"fire_risk":false,"precision_risk":"low","safer_alternatives":["spray limpio para cambios de rpm","inspeccion visual"]}',
                $now
            )
        """)

        db.execSQL("""
            INSERT OR REPLACE INTO `trench_knowledge`
            (`scenarioId`, `title`, `riskLevel`, `searchKeywords`, `payloadJson`, `updatedAt`)
            VALUES
            (
                'broken_stud_extraction',
                'Extraccion de esparrago roto',
                'critical',
                'esparrago roto tuerca soldada extractor perforar centrado',
                '{"escalation_ladder":["centrar con punzon","penetrante","broca zurda","extractor solo si queda suficiente material","soldar tuerca para choque termico","taladrado progresivo y reparacion de rosca"],"heat_allowed":true,"heat_forbidden_zones":["alrededor de combustible","aluminio delgado sin control"],"cutting_allowed":false,"fire_risk_warnings":["soldadura cerca de grasa/combustible"],"thread_repair_options":["helicoil","timesert","rosca sobremedida"],"common_failures":["romper extractor dentro del perno empeora radicalmente el trabajo"]}',
                $now
            ),
            (
                'aluminum_thread_repair',
                'Reparacion de rosca en aluminio',
                'high',
                'aluminio rosca barrida helicoil timesert torque',
                '{"escalation_ladder":["confirmar largo y paso original","taladrar perpendicular","machuelear limpio","instalar inserto adecuado","verificar protrusion y torque"],"heat_allowed":false,"heat_forbidden_zones":["aluminio tratado termicamente"],"cutting_allowed":true,"fire_risk_warnings":[],"thread_repair_options":["helicoil para servicio general","timesert para zonas criticas y reapriete frecuente"],"common_failures":["usar inserto demasiado corto","no retirar viruta"]}',
                $now
            )
        """)

        db.execSQL("""
            INSERT OR REPLACE INTO `meet_knowledge_matrix`
            (`dtcCode`, `componentName`, `systemCategory`, `urgencyLevel`, `layerDiagnosticsJson`, `layerRebuildSpecsJson`, `layerTrenchKnowledgeJson`, `layerAdvancedEngJson`, `lastUpdated`)
            VALUES
            (
                'P0562',
                'Alternador / sistema de carga',
                'Powertrain - Charging',
                'pronto',
                '{"related_symptom_guides":["alternator_not_charging","hard_start"],"diagnostic_steps":["medir voltaje KOEO y en marcha","hacer prueba de caida de voltaje positiva y de tierra","medir ripple AC","verificar señal de control inteligente"],"confirmation_tests":["13.5-14.8V segun estrategia OEM","ripple bajo","sin caida excesiva en B+"]}',
                '{"electrical_specs":["carga tipica 13.5-14.8V","ripple AC <0.5V","caida B+ <0.3V, tierra <0.1V"],"rebuild_paths":["alternator"]}',
                '{"related_trench_knowledge":["aluminum_thread_repair"],"common_shop_notes":["no condenar alternador sin revisar megafuse, tierra motor y sensor IBS/BMS"]}',
                '{"scope_patterns":["ripple de seis pulsos identifica diodos/fases"],"ev_hvac_notes":["en hibridos revisar estrategia DC-DC antes de culpar alternador"]}',
                $now
            ),
            (
                'P0420',
                'Catalizador / eficiencia',
                'Powertrain - Emissions',
                'monitor',
                '{"related_symptom_guides":["oil_leak"],"diagnostic_steps":["confirmar que no existan misfires ni fugas de escape","comparar O2 upstream/downstream","medir contrapresion si hay perdida de potencia"],"confirmation_tests":["contrapresion <1 psi a 2500 rpm","downstream mas estable que upstream","sin fugas antes del sensor downstream"]}',
                '{"electrical_specs":["sensor O2 upstream debe cruzar rapido","sensor downstream mas estable"],"service_limits":["contrapresion alta implica restriccion fisica"]}',
                '{"related_trench_knowledge":["broken_stud_extraction"],"common_shop_notes":["no cambiar catalizador antes de resolver consumo de aceite o misfire que lo destruye"]}',
                '{"scope_patterns":["upstream oscilando y downstream copiando = catalizador agotado"],"ev_hvac_notes":[]}',
                $now
            )
        """)
    }

    private fun seedCommunityCases(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        android.util.Log.i("ElysiumDB", "Seeding community cases for standard DTCs...")
        
        fun insertCase(id: String, make: String, model: String, year: Int, engine: String, country: String, dtc: String, symptoms: String, solution: String, cost: Double, time: Int, parts: String, verified: Int, votes: Int, rate: Double) {
            db.execSQL("""
                INSERT OR IGNORE INTO `repair_cases`
                (`id`, `vehicleMake`, `vehicleModel`, `year`, `engine`, `country`, `dtcCode`, `symptoms`, `solution`, `cost`, `timeSpent`, `partsUsed`, `verified`, `votes`, `successRate`, `isBookmarked`, `isMyContribution`, `createdAt`)
                VALUES ('$id', '$make', '$model', $year, '$engine', '$country', '$dtc', '$symptoms', '$solution', $cost, $time, '$parts', $verified, $votes, $rate, 0, 0, $now)
            """)
        }

        insertCase("case_1", "Chevrolet", "Spark", 2018, "1.2L", "México", "P0300", "Motor tiembla mucho en ralentí, pérdida de potencia y check engine parpadea bajo carga.", "Reemplazo de cables de encendido agrietados y juego de bujías de iridio nuevas.", 75.0, 45, "Bujías NGK, Cables ACDelco", 1, 28, 96.0)
        insertCase("case_2", "Toyota", "Corolla", 2012, "1.8L Dual VVT-i", "Costa Rica", "P0420", "Luz check engine encendida permanente, olor inusual en el escape tipo huevo podrido.", "Cambio de convertidor catalítico obstruido por uno homologado de flujo directo y sensor O2 secundario.", 380.0, 120, "Catalizador Magnaflow, Sensor Denso", 1, 45, 92.5)
        insertCase("case_3", "Nissan", "Versa", 2017, "1.6L HR16DE", "Colombia", "P0171", "Ralentí inestable que oscila, tironeos leves al acelerar a bajas revoluciones.", "Limpieza del cuerpo de aceleración y cambio de junta del múltiple de admisión rota que causaba entrada de aire no medido.", 95.0, 90, "Empaque admisión original", 1, 19, 100.0)
        insertCase("case_4", "Ford", "Focus", 2014, "2.0L GDI Duratec", "Argentina", "P0700", "Caja patina al pasar de segunda a tercera, golpe fuerte al colocar reversa.", "Actualización de software del módulo de control de transmisión (TCM) y limpieza de contactos a tierra.", 180.0, 150, "Ninguno (Mano de obra)", 1, 33, 85.0)
        insertCase("case_5", "Hyundai", "Accent", 2016, "1.4L MPI", "Chile", "P0115", "El ventilador del radiador se queda encendido todo el tiempo, aguja de temperatura no sube.", "Reemplazo del sensor de temperatura de refrigerante del motor (ECT) dañado y purga de burbujas de aire.", 55.0, 60, "Sensor ECT original Hyundai", 1, 14, 98.0)
        insertCase("case_6", "Kia", "Sportage", 2015, "2.0L Nu", "Perú", "P0442", "Check engine encendida, no hay fallas de rendimiento perceptibles en el manejo.", "Reemplazo de la junta de goma agrietada en el tapón de llenado de combustible por uno nuevo original.", 25.0, 15, "Tapón de gasolina Kia", 1, 9, 100.0)
        insertCase("case_7", "Volkswagen", "Jetta", 2013, "2.5L 5 cil", "México", "P0122", "Pedal de aceleración no responde intermitentemente, motor entra en modo de seguridad (limp mode).", "Cambio del sensor de posición de mariposa (TPS) integrado y recalibración del cuerpo de aceleración electrónico.", 110.0, 80, "Sensor TPS Bosch", 1, 22, 94.0)
        insertCase("case_8", "Honda", "Civic", 2011, "1.8L i-VTEC", "Panamá", "P0302", "Pérdida de potencia notable bajo aceleración, el motor trabaja en 3 cilindros.", "Bobina de encendido del cilindro 2 quemada por cortocircuito interno. Reemplazo por bobina nueva.", 85.0, 30, "Bobina Hitachi original", 1, 37, 100.0)
        insertCase("case_9", "Renault", "Duster", 2016, "2.0L F4R", "Colombia", "P0562", "Luces de tablero parpadean de noche, dirección electro-asistida se pone dura ocasionalmente.", "Fallo del alternador por desgaste en las escobillas del regulador de voltaje. Cambio de regulador.", 140.0, 100, "Regulador Valeo, Batería nueva", 1, 15, 90.0)
        insertCase("case_10", "Peugeot", "207", 2012, "1.4L VTi", "Uruguay", "P0011", "Cascabeleo del motor caliente al ralentí, luz de check engine encendida.", "Solenoide de control de válvula variable (VVT) del lado de admisión atascado por lodos. Limpieza de conductos y reemplazo del solenoide.", 125.0, 90, "Solenoide VVT PSA", 1, 18, 95.0)
    }

    private val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 26→27: Creating Elysium Vanguard Knowledge Engine v4.0 tables")
            createMechanicalKnowledgeTables(db)
            seedMechanicalKnowledge(db)
        }
    }

    private val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 27→28: Hardening hybrid knowledge matrix and expanding offline knowledge seeds")
            createMechanicalKnowledgeTables(db)
            db.execSQL("""
                DELETE FROM meet_knowledge_matrix
                WHERE id NOT IN (
                    SELECT MIN(id)
                    FROM meet_knowledge_matrix
                    GROUP BY COALESCE(dtcCode, ''), COALESCE(componentName, '')
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_meet_knowledge_matrix_dtcCode_componentName_unique` ON `meet_knowledge_matrix` (`dtcCode`, `componentName`)")
            seedMechanicalKnowledge(db)
        }
    }

    private val MIGRATION_28_29 = object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 28→29: Creating real parts-store auction tables")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `parts_stores` (
                    `storeId` TEXT NOT NULL,
                    `storeName` TEXT NOT NULL,
                    `rating` REAL NOT NULL,
                    `phone` TEXT NOT NULL,
                    `location` TEXT NOT NULL,
                    `deliveryRadiusKm` REAL NOT NULL,
                    `averageEtaMinutes` INTEGER NOT NULL,
                    `verified` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`storeId`)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `part_requests` (
                    `requestId` TEXT NOT NULL,
                    `serviceRequestId` TEXT,
                    `vehicleId` TEXT NOT NULL,
                    `dtcCode` TEXT,
                    `partName` TEXT NOT NULL,
                    `partNumber` TEXT,
                    `quantity` INTEGER NOT NULL,
                    `oemPreference` TEXT NOT NULL,
                    `deliveryLocation` TEXT NOT NULL,
                    `urgencyMinutes` INTEGER NOT NULL,
                    `customerNotes` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `acceptedOfferId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`requestId`)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `part_offers` (
                    `offerId` TEXT NOT NULL,
                    `partRequestId` TEXT NOT NULL,
                    `storeId` TEXT NOT NULL,
                    `storeName` TEXT NOT NULL,
                    `brand` TEXT NOT NULL,
                    `partNumber` TEXT NOT NULL,
                    `condition` TEXT NOT NULL,
                    `price` REAL NOT NULL,
                    `deliveryFee` REAL NOT NULL,
                    `etaMinutes` INTEGER NOT NULL,
                    `warrantyDays` INTEGER NOT NULL,
                    `message` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`offerId`)
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_part_requests_status_createdAt` ON `part_requests` (`status`, `createdAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_part_requests_vehicleId` ON `part_requests` (`vehicleId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_part_requests_serviceRequestId` ON `part_requests` (`serviceRequestId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_part_offers_partRequestId` ON `part_offers` (`partRequestId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_part_offers_storeId` ON `part_offers` (`storeId`)")
        }
    }

    private val MIGRATION_29_30 = object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 29→30: Creating tow-truck requests and ratings tables")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `tow_truck_requests` (
                    `requestId` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `vehicleInfo` TEXT NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `locationName` TEXT NOT NULL,
                    `destinationLatitude` REAL,
                    `destinationLongitude` REAL,
                    `destinationName` TEXT,
                    `phone` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `assignedDriverId` TEXT,
                    `assignedDriverName` TEXT,
                    `assignedDriverPhone` TEXT,
                    `priceOffer` REAL NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `completedAt` INTEGER,
                    PRIMARY KEY(`requestId`)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `ratings` (
                    `ratingId` TEXT NOT NULL,
                    `targetType` TEXT NOT NULL,
                    `targetId` TEXT NOT NULL,
                    `sourceId` TEXT NOT NULL,
                    `sourceName` TEXT NOT NULL,
                    `stars` REAL NOT NULL,
                    `comment` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`ratingId`)
                )
            """)
            seedCommunityCases(db)
        }
    }

    private val MIGRATION_30_31 = object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 30→31: Adding mechanic assignment, GPS, and part position fields")
            // ServiceRequestEntity new columns
            db.execSQL("ALTER TABLE service_requests ADD COLUMN latitude REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE service_requests ADD COLUMN longitude REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE service_requests ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE service_requests ADD COLUMN priceOffer REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE service_requests ADD COLUMN assignedMechanicId TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE service_requests ADD COLUMN assignedMechanicName TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE service_requests ADD COLUMN assignedMechanicPhone TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE service_requests ADD COLUMN completedAt INTEGER DEFAULT NULL")
            // PartRequestEntity new columns
            db.execSQL("ALTER TABLE part_requests ADD COLUMN partPosition TEXT NOT NULL DEFAULT 'N/A'")
            db.execSQL("ALTER TABLE part_requests ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE part_requests ADD COLUMN latitude REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE part_requests ADD COLUMN longitude REAL NOT NULL DEFAULT 0.0")
        }
    }

    private val MIGRATION_31_32 = object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 31→32: Creating provider_profiles table")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `provider_profiles` (
                    `profileId` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `providerType` TEXT NOT NULL,
                    `businessName` TEXT NOT NULL,
                    `ownerName` TEXT NOT NULL,
                    `phone` TEXT NOT NULL,
                    `location` TEXT NOT NULL,
                    `latitude` REAL NOT NULL DEFAULT 0.0,
                    `longitude` REAL NOT NULL DEFAULT 0.0,
                    `specialties` TEXT NOT NULL DEFAULT '',
                    `radiusKm` REAL NOT NULL DEFAULT 25.0,
                    `licenseNumber` TEXT NOT NULL DEFAULT '',
                    `isActive` INTEGER NOT NULL DEFAULT 1,
                    `verified` INTEGER NOT NULL DEFAULT 0,
                    `rating` REAL NOT NULL DEFAULT 0.0,
                    `totalJobs` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`profileId`)
                )
            """)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_provider_profiles_userId_providerType` ON `provider_profiles` (`userId`, `providerType`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_profiles_providerType_isActive` ON `provider_profiles` (`providerType`, `isActive`)")
        }
    }

    private val MIGRATION_32_33 = object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 32→33: Creating ride tables")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `ride_requests` (
                    `requestId` TEXT NOT NULL,
                    `passengerId` TEXT NOT NULL,
                    `passengerName` TEXT NOT NULL,
                    `passengerPhone` TEXT NOT NULL,
                    `pickupLatitude` REAL NOT NULL,
                    `pickupLongitude` REAL NOT NULL,
                    `pickupAddress` TEXT NOT NULL,
                    `pickupAccuracy` REAL NOT NULL,
                    `destLatitude` REAL NOT NULL,
                    `destLongitude` REAL NOT NULL,
                    `destAddress` TEXT NOT NULL,
                    `priceOffer` REAL NOT NULL,
                    `currency` TEXT NOT NULL,
                    `estimatedDistanceKm` REAL NOT NULL,
                    `estimatedDurationMin` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `acceptedOfferId` TEXT,
                    `assignedDriverId` TEXT,
                    `assignedDriverName` TEXT,
                    `assignedDriverPhone` TEXT,
                    `assignedDriverVehicle` TEXT,
                    `finalPrice` REAL,
                    `passengerRating` REAL,
                    `driverRating` REAL,
                    `createdAt` INTEGER NOT NULL,
                    `completedAt` INTEGER,
                    PRIMARY KEY(`requestId`)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `ride_offers` (
                    `offerId` TEXT NOT NULL,
                    `requestId` TEXT NOT NULL,
                    `driverId` TEXT NOT NULL,
                    `driverName` TEXT NOT NULL,
                    `driverPhone` TEXT NOT NULL,
                    `driverRating` REAL NOT NULL,
                    `driverTotalTrips` INTEGER NOT NULL,
                    `vehicleDescription` TEXT NOT NULL,
                    `counterPrice` REAL NOT NULL,
                    `currency` TEXT NOT NULL,
                    `estimatedArrivalMin` INTEGER NOT NULL,
                    `driverLatitude` REAL NOT NULL,
                    `driverLongitude` REAL NOT NULL,
                    `message` TEXT,
                    `status` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`offerId`)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `ride_chat_messages` (
                    `messageId` TEXT NOT NULL,
                    `rideRequestId` TEXT NOT NULL,
                    `senderId` TEXT NOT NULL,
                    `senderName` TEXT NOT NULL,
                    `senderRole` TEXT NOT NULL,
                    `messageType` TEXT NOT NULL,
                    `textContent` TEXT,
                    `audioFilePath` TEXT,
                    `audioDurationMs` INTEGER,
                    `isRead` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`messageId`)
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ride_requests_passengerId` ON `ride_requests` (`passengerId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ride_requests_assignedDriverId` ON `ride_requests` (`assignedDriverId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ride_requests_status` ON `ride_requests` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ride_offers_requestId` ON `ride_offers` (`requestId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ride_offers_driverId` ON `ride_offers` (`driverId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ride_chat_messages_rideRequestId` ON `ride_chat_messages` (`rideRequestId`)")
        }
    }

    private val MIGRATION_33_34 = object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 33→34: Creating identity verification tables")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `driver_verifications` (
                    `driverId` TEXT NOT NULL,
                    `fullName` TEXT NOT NULL,
                    `phone` TEXT NOT NULL,
                    `email` TEXT NOT NULL,
                    `dateOfBirth` TEXT NOT NULL,
                    `vehicleMake` TEXT NOT NULL,
                    `vehicleModel` TEXT NOT NULL,
                    `vehicleYear` INTEGER NOT NULL,
                    `vehicleColor` TEXT NOT NULL,
                    `vehiclePlate` TEXT NOT NULL,
                    `pathLicenciaFront` TEXT NOT NULL,
                    `pathLicenciaBack` TEXT NOT NULL,
                    `pathCedulaFront` TEXT NOT NULL,
                    `pathCedulaBack` TEXT NOT NULL,
                    `pathHojaDelincuencia` TEXT NOT NULL,
                    `pathMarchamo` TEXT NOT NULL,
                    `pathDekra` TEXT NOT NULL,
                    `pathSeguro` TEXT NOT NULL,
                    `pathSelfieProfile` TEXT NOT NULL,
                    `pathSelfieWithCedula` TEXT NOT NULL,
                    `pathSelfieWithLicencia` TEXT NOT NULL,
                    `pathVehicleFront` TEXT NOT NULL,
                    `pathVehicleBack` TEXT NOT NULL,
                    `pathVehicleInterior` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `rejectionReason` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL DEFAULT 0,
                    `approvedAt` INTEGER,
                    PRIMARY KEY(`driverId`)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `passenger_verifications` (
                    `passengerId` TEXT NOT NULL,
                    `fullName` TEXT NOT NULL,
                    `phone` TEXT NOT NULL,
                    `pathProfilePhoto` TEXT NOT NULL,
                    `pathCedulaFront` TEXT NOT NULL,
                    `pathSelfieWithCedula` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `rejectionReason` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `approvedAt` INTEGER,
                    PRIMARY KEY(`passengerId`)
                )
            """)
        }
    }

    private data class RoomColumnShape(
        val type: String,
        val notNull: Boolean,
        val primaryKeyPosition: Int = 0
    )

    private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'").use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun readColumnShapes(db: SupportSQLiteDatabase, tableName: String): Map<String, RoomColumnShape> {
        val columns = linkedMapOf<String, RoomColumnShape>()
        db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val typeIndex = cursor.getColumnIndex("type")
            val notNullIndex = cursor.getColumnIndex("notnull")
            val pkIndex = cursor.getColumnIndex("pk")
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)
                val type = cursor.getString(typeIndex).uppercase()
                columns[name] = RoomColumnShape(
                    type = type,
                    notNull = cursor.getInt(notNullIndex) == 1,
                    primaryKeyPosition = cursor.getInt(pkIndex)
                )
            }
        }
        return columns
    }

    private fun ensureRoomTableShape(
        db: SupportSQLiteDatabase,
        tableName: String,
        expectedColumns: Map<String, RoomColumnShape>,
        managedIndices: List<String> = emptyList()
    ) {
        managedIndices.forEach { indexName ->
            db.execSQL("DROP INDEX IF EXISTS `$indexName`")
        }

        if (!tableExists(db, tableName)) return

        val currentColumns = readColumnShapes(db, tableName)
        val matchesExpectedShape =
            currentColumns.keys == expectedColumns.keys &&
                expectedColumns.all { (name, expected) ->
                    currentColumns[name]?.let { current ->
                        current.type == expected.type &&
                            current.notNull == expected.notNull &&
                            current.primaryKeyPosition == expected.primaryKeyPosition
                    } == true
                }

        if (matchesExpectedShape) return

        var backupName = "${tableName}_legacy_before_v40"
        var suffix = 2
        while (tableExists(db, backupName)) {
            backupName = "${tableName}_legacy_before_v40_$suffix"
            suffix += 1
        }
        android.util.Log.w(
            "ElysiumDB",
            "Backing up incompatible Vanguard table '$tableName' as '$backupName' before Room schema repair"
        )
        db.execSQL("ALTER TABLE `$tableName` RENAME TO `$backupName`")
    }

    private fun createVanguardTelemetryTables(db: SupportSQLiteDatabase) {
        ensureRoomTableShape(
            db,
            "vanguard_obd_sessions",
            linkedMapOf(
                "sessionId" to RoomColumnShape("TEXT", true, 1),
                "vehicleId" to RoomColumnShape("TEXT", true),
                "adapterId" to RoomColumnShape("TEXT", false),
                "protocol" to RoomColumnShape("TEXT", true),
                "startedAt" to RoomColumnShape("INTEGER", true),
                "endedAt" to RoomColumnShape("INTEGER", false),
                "status" to RoomColumnShape("TEXT", true),
                "totalPidsRead" to RoomColumnShape("INTEGER", true),
                "errorCount" to RoomColumnShape("INTEGER", true),
                "lastError" to RoomColumnShape("TEXT", false)
            )
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `vanguard_obd_sessions` (
            `sessionId` TEXT NOT NULL,
            `vehicleId` TEXT NOT NULL,
            `adapterId` TEXT,
            `protocol` TEXT NOT NULL,
            `startedAt` INTEGER NOT NULL,
            `endedAt` INTEGER,
            `status` TEXT NOT NULL,
            `totalPidsRead` INTEGER NOT NULL,
            `errorCount` INTEGER NOT NULL,
            `lastError` TEXT,
            PRIMARY KEY(`sessionId`)
        )""")

        ensureRoomTableShape(
            db,
            "obd_pid_samples",
            linkedMapOf(
                "id" to RoomColumnShape("INTEGER", true, 1),
                "sessionId" to RoomColumnShape("TEXT", true),
                "pid" to RoomColumnShape("TEXT", true),
                "value" to RoomColumnShape("REAL", true),
                "unit" to RoomColumnShape("TEXT", true),
                "capturedAt" to RoomColumnShape("INTEGER", true)
            ),
            listOf(
                "index_obd_pid_samples_sessionId",
                "index_obd_pid_samples_capturedAt",
                "index_obd_pid_samples_sessionId_pid_timestampMs",
                "index_obd_pid_samples_vehicleId_pid_timestampMs"
            )
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `obd_pid_samples` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `sessionId` TEXT NOT NULL,
            `pid` TEXT NOT NULL,
            `value` REAL NOT NULL,
            `unit` TEXT NOT NULL,
            `capturedAt` INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_obd_pid_samples_sessionId` ON `obd_pid_samples` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_obd_pid_samples_capturedAt` ON `obd_pid_samples` (`capturedAt`)")

        ensureRoomTableShape(
            db,
            "obd_command_log",
            linkedMapOf(
                "id" to RoomColumnShape("INTEGER", true, 1),
                "sessionId" to RoomColumnShape("TEXT", true),
                "command" to RoomColumnShape("TEXT", true),
                "response" to RoomColumnShape("TEXT", true),
                "latencyMs" to RoomColumnShape("INTEGER", true),
                "success" to RoomColumnShape("INTEGER", true),
                "sentAt" to RoomColumnShape("INTEGER", true)
            ),
            listOf("index_obd_command_log_sessionId", "index_obd_command_log_sentAt")
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `obd_command_log` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `sessionId` TEXT NOT NULL,
            `command` TEXT NOT NULL,
            `response` TEXT NOT NULL,
            `latencyMs` INTEGER NOT NULL,
            `success` INTEGER NOT NULL,
            `sentAt` INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_obd_command_log_sessionId` ON `obd_command_log` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_obd_command_log_sentAt` ON `obd_command_log` (`sentAt`)")

        ensureRoomTableShape(
            db,
            "ecu_failure_events",
            linkedMapOf(
                "eventId" to RoomColumnShape("TEXT", true, 1),
                "vehicleId" to RoomColumnShape("TEXT", true),
                "dtcCode" to RoomColumnShape("TEXT", true),
                "source" to RoomColumnShape("TEXT", true),
                "severity" to RoomColumnShape("TEXT", true),
                "description" to RoomColumnShape("TEXT", true),
                "detectedAt" to RoomColumnShape("INTEGER", true),
                "resolvedAt" to RoomColumnShape("INTEGER", false)
            ),
            listOf(
                "index_ecu_failure_events_vehicleId",
                "index_ecu_failure_events_detectedAt",
                "index_ecu_failure_events_sessionId_timestampMs",
                "index_ecu_failure_events_failureType_vehicleMake_vehicleModel_vehicleYear",
                "index_ecu_failure_events_adapterType_protocolSelected"
            )
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `ecu_failure_events` (
            `eventId` TEXT NOT NULL,
            `vehicleId` TEXT NOT NULL,
            `dtcCode` TEXT NOT NULL,
            `source` TEXT NOT NULL,
            `severity` TEXT NOT NULL,
            `description` TEXT NOT NULL,
            `detectedAt` INTEGER NOT NULL,
            `resolvedAt` INTEGER,
            PRIMARY KEY(`eventId`)
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ecu_failure_events_vehicleId` ON `ecu_failure_events` (`vehicleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ecu_failure_events_detectedAt` ON `ecu_failure_events` (`detectedAt`)")

        ensureRoomTableShape(
            db,
            "compatibility_rules",
            linkedMapOf(
                "id" to RoomColumnShape("INTEGER", true, 1),
                "vehicleMake" to RoomColumnShape("TEXT", true),
                "vehicleModel" to RoomColumnShape("TEXT", true),
                "vehicleYear" to RoomColumnShape("INTEGER", false),
                "featureId" to RoomColumnShape("TEXT", true),
                "compatible" to RoomColumnShape("INTEGER", true),
                "notes" to RoomColumnShape("TEXT", false)
            ),
            listOf(
                "index_compatibility_rules_vehicleMake",
                "index_compatibility_rules_vehicleModel",
                "index_compatibility_rules_priority_enabled",
                "index_compatibility_rules_expiresAtMs"
            )
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `compatibility_rules` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `vehicleMake` TEXT NOT NULL,
            `vehicleModel` TEXT NOT NULL,
            `vehicleYear` INTEGER,
            `featureId` TEXT NOT NULL,
            `compatible` INTEGER NOT NULL,
            `notes` TEXT
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_compatibility_rules_vehicleMake` ON `compatibility_rules` (`vehicleMake`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_compatibility_rules_vehicleModel` ON `compatibility_rules` (`vehicleModel`)")

        ensureRoomTableShape(
            db,
            "vehicle_profile_snapshots",
            linkedMapOf(
                "id" to RoomColumnShape("INTEGER", true, 1),
                "vehicleId" to RoomColumnShape("TEXT", true),
                "capturedAt" to RoomColumnShape("INTEGER", true),
                "odometerKm" to RoomColumnShape("REAL", false),
                "batteryVoltage" to RoomColumnShape("REAL", false),
                "coolantTempC" to RoomColumnShape("REAL", false),
                "oilLifePercent" to RoomColumnShape("REAL", false),
                "payloadJson" to RoomColumnShape("TEXT", true)
            ),
            listOf("index_vehicle_profile_snapshots_vehicleId", "index_vehicle_profile_snapshots_capturedAt")
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `vehicle_profile_snapshots` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `vehicleId` TEXT NOT NULL,
            `capturedAt` INTEGER NOT NULL,
            `odometerKm` REAL,
            `batteryVoltage` REAL,
            `coolantTempC` REAL,
            `oilLifePercent` REAL,
            `payloadJson` TEXT NOT NULL
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_vehicle_profile_snapshots_vehicleId` ON `vehicle_profile_snapshots` (`vehicleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_vehicle_profile_snapshots_capturedAt` ON `vehicle_profile_snapshots` (`capturedAt`)")

        ensureRoomTableShape(
            db,
            "mode06_results",
            linkedMapOf(
                "id" to RoomColumnShape("INTEGER", true, 1),
                "sessionId" to RoomColumnShape("TEXT", true),
                "testId" to RoomColumnShape("TEXT", true),
                "componentId" to RoomColumnShape("TEXT", true),
                "value" to RoomColumnShape("REAL", true),
                "minValue" to RoomColumnShape("REAL", true),
                "maxValue" to RoomColumnShape("REAL", true),
                "status" to RoomColumnShape("TEXT", true),
                "capturedAt" to RoomColumnShape("INTEGER", true)
            ),
            listOf("index_mode06_results_sessionId", "index_mode06_results_sessionId_mid_tid")
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `mode06_results` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `sessionId` TEXT NOT NULL,
            `testId` TEXT NOT NULL,
            `componentId` TEXT NOT NULL,
            `value` REAL NOT NULL,
            `minValue` REAL NOT NULL,
            `maxValue` REAL NOT NULL,
            `status` TEXT NOT NULL,
            `capturedAt` INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mode06_results_sessionId` ON `mode06_results` (`sessionId`)")

        ensureRoomTableShape(
            db,
            "freeze_frames",
            linkedMapOf(
                "id" to RoomColumnShape("INTEGER", true, 1),
                "vehicleId" to RoomColumnShape("TEXT", true),
                "dtcCode" to RoomColumnShape("TEXT", true),
                "capturedAt" to RoomColumnShape("INTEGER", true),
                "payloadJson" to RoomColumnShape("TEXT", true)
            ),
            listOf("index_freeze_frames_dtcCode", "index_freeze_frames_vehicleId", "index_freeze_frames_sessionId_dtcCode")
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `freeze_frames` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `vehicleId` TEXT NOT NULL,
            `dtcCode` TEXT NOT NULL,
            `capturedAt` INTEGER NOT NULL,
            `payloadJson` TEXT NOT NULL
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_freeze_frames_dtcCode` ON `freeze_frames` (`dtcCode`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_freeze_frames_vehicleId` ON `freeze_frames` (`vehicleId`)")

        ensureRoomTableShape(
            db,
            "derived_metrics",
            linkedMapOf(
                "id" to RoomColumnShape("INTEGER", true, 1),
                "vehicleId" to RoomColumnShape("TEXT", true),
                "metricName" to RoomColumnShape("TEXT", true),
                "value" to RoomColumnShape("REAL", true),
                "unit" to RoomColumnShape("TEXT", true),
                "computedAt" to RoomColumnShape("INTEGER", true)
            ),
            listOf("index_derived_metrics_vehicleId", "index_derived_metrics_computedAt", "index_derived_metrics_sessionId_metricId_timestampMs")
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `derived_metrics` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `vehicleId` TEXT NOT NULL,
            `metricName` TEXT NOT NULL,
            `value` REAL NOT NULL,
            `unit` TEXT NOT NULL,
            `computedAt` INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_derived_metrics_vehicleId` ON `derived_metrics` (`vehicleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_derived_metrics_computedAt` ON `derived_metrics` (`computedAt`)")

        ensureRoomTableShape(
            db,
            "health_scores",
            linkedMapOf(
                "id" to RoomColumnShape("INTEGER", true, 1),
                "vehicleId" to RoomColumnShape("TEXT", true),
                "overallScore" to RoomColumnShape("INTEGER", true),
                "engineScore" to RoomColumnShape("INTEGER", false),
                "batteryScore" to RoomColumnShape("INTEGER", false),
                "brakesScore" to RoomColumnShape("INTEGER", false),
                "tiresScore" to RoomColumnShape("INTEGER", false),
                "payloadJson" to RoomColumnShape("TEXT", false),
                "computedAt" to RoomColumnShape("INTEGER", true)
            ),
            listOf("index_health_scores_vehicleId", "index_health_scores_computedAt", "index_health_scores_vehicleId_timestampMs")
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `health_scores` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `vehicleId` TEXT NOT NULL,
            `overallScore` INTEGER NOT NULL,
            `engineScore` INTEGER,
            `batteryScore` INTEGER,
            `brakesScore` INTEGER,
            `tiresScore` INTEGER,
            `payloadJson` TEXT,
            `computedAt` INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_scores_vehicleId` ON `health_scores` (`vehicleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_scores_computedAt` ON `health_scores` (`computedAt`)")

        ensureRoomTableShape(
            db,
            "repair_recommendations",
            linkedMapOf(
                "recommendationId" to RoomColumnShape("TEXT", true, 1),
                "vehicleId" to RoomColumnShape("TEXT", true),
                "dtcCode" to RoomColumnShape("TEXT", false),
                "priority" to RoomColumnShape("TEXT", true),
                "title" to RoomColumnShape("TEXT", true),
                "description" to RoomColumnShape("TEXT", true),
                "estimatedCostCents" to RoomColumnShape("INTEGER", false),
                "estimatedTimeMinutes" to RoomColumnShape("INTEGER", false),
                "createdAt" to RoomColumnShape("INTEGER", true)
            ),
            listOf("index_repair_recommendations_vehicleId", "index_repair_recommendations_dtcCode", "index_repair_recommendations_vehicleId_sessionId")
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `repair_recommendations` (
            `recommendationId` TEXT NOT NULL,
            `vehicleId` TEXT NOT NULL,
            `dtcCode` TEXT,
            `priority` TEXT NOT NULL,
            `title` TEXT NOT NULL,
            `description` TEXT NOT NULL,
            `estimatedCostCents` INTEGER,
            `estimatedTimeMinutes` INTEGER,
            `createdAt` INTEGER NOT NULL,
            PRIMARY KEY(`recommendationId`)
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_repair_recommendations_vehicleId` ON `repair_recommendations` (`vehicleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_repair_recommendations_dtcCode` ON `repair_recommendations` (`dtcCode`)")

        ensureRoomTableShape(
            db,
            "ai_diagnostic_results",
            linkedMapOf(
                "resultId" to RoomColumnShape("TEXT", true, 1),
                "vehicleId" to RoomColumnShape("TEXT", true),
                "inputContextJson" to RoomColumnShape("TEXT", true),
                "outputDiagnosisJson" to RoomColumnShape("TEXT", true),
                "modelVersion" to RoomColumnShape("TEXT", true),
                "confidence" to RoomColumnShape("REAL", true),
                "generatedAt" to RoomColumnShape("INTEGER", true)
            ),
            listOf("index_ai_diagnostic_results_vehicleId", "index_ai_diagnostic_results_generatedAt", "index_ai_diagnostic_results_sessionId_createdAt")
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `ai_diagnostic_results` (
            `resultId` TEXT NOT NULL,
            `vehicleId` TEXT NOT NULL,
            `inputContextJson` TEXT NOT NULL,
            `outputDiagnosisJson` TEXT NOT NULL,
            `modelVersion` TEXT NOT NULL,
            `confidence` REAL NOT NULL,
            `generatedAt` INTEGER NOT NULL,
            PRIMARY KEY(`resultId`)
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_diagnostic_results_vehicleId` ON `ai_diagnostic_results` (`vehicleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_diagnostic_results_generatedAt` ON `ai_diagnostic_results` (`generatedAt`)")

        ensureRoomTableShape(
            db,
            "vehicle_history",
            linkedMapOf(
                "id" to RoomColumnShape("INTEGER", true, 1),
                "vehicleId" to RoomColumnShape("TEXT", true),
                "eventType" to RoomColumnShape("TEXT", true),
                "eventAt" to RoomColumnShape("INTEGER", true),
                "summary" to RoomColumnShape("TEXT", true),
                "payloadJson" to RoomColumnShape("TEXT", false)
            ),
            listOf("index_vehicle_history_vehicleId", "index_vehicle_history_eventAt", "index_vehicle_history_vehicleId_metric_bucketStartMs")
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `vehicle_history` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `vehicleId` TEXT NOT NULL,
            `eventType` TEXT NOT NULL,
            `eventAt` INTEGER NOT NULL,
            `summary` TEXT NOT NULL,
            `payloadJson` TEXT
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_vehicle_history_vehicleId` ON `vehicle_history` (`vehicleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_vehicle_history_eventAt` ON `vehicle_history` (`eventAt`)")

        ensureRoomTableShape(
            db,
            "pdf_reports",
            linkedMapOf(
                "reportId" to RoomColumnShape("TEXT", true, 1),
                "vehicleId" to RoomColumnShape("TEXT", true),
                "reportType" to RoomColumnShape("TEXT", true),
                "title" to RoomColumnShape("TEXT", true),
                "filePath" to RoomColumnShape("TEXT", true),
                "generatedAt" to RoomColumnShape("INTEGER", true),
                "signatureHash" to RoomColumnShape("TEXT", false)
            ),
            listOf("index_pdf_reports_vehicleId", "index_pdf_reports_generatedAt", "index_pdf_reports_vehicleId_createdAt")
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `pdf_reports` (
            `reportId` TEXT NOT NULL,
            `vehicleId` TEXT NOT NULL,
            `reportType` TEXT NOT NULL,
            `title` TEXT NOT NULL,
            `filePath` TEXT NOT NULL,
            `generatedAt` INTEGER NOT NULL,
            `signatureHash` TEXT,
            PRIMARY KEY(`reportId`)
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pdf_reports_vehicleId` ON `pdf_reports` (`vehicleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pdf_reports_generatedAt` ON `pdf_reports` (`generatedAt`)")

        ensureRoomTableShape(
            db,
            "audit_logs",
            linkedMapOf(
                "id" to RoomColumnShape("INTEGER", true, 1),
                "actorId" to RoomColumnShape("TEXT", false),
                "actorRole" to RoomColumnShape("TEXT", false),
                "action" to RoomColumnShape("TEXT", true),
                "resourceType" to RoomColumnShape("TEXT", true),
                "resourceId" to RoomColumnShape("TEXT", true),
                "payloadJson" to RoomColumnShape("TEXT", false),
                "occurredAt" to RoomColumnShape("INTEGER", true)
            ),
            listOf("index_audit_logs_actorId", "index_audit_logs_occurredAt", "index_audit_logs_sessionId_timestampMs")
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `audit_logs` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `actorId` TEXT,
            `actorRole` TEXT,
            `action` TEXT NOT NULL,
            `resourceType` TEXT NOT NULL,
            `resourceId` TEXT NOT NULL,
            `payloadJson` TEXT,
            `occurredAt` INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_actorId` ON `audit_logs` (`actorId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_occurredAt` ON `audit_logs` (`occurredAt`)")

        ensureRoomTableShape(
            db,
            "fix_rollouts",
            linkedMapOf(
                "id" to RoomColumnShape("INTEGER", true, 1),
                "fixId" to RoomColumnShape("TEXT", true),
                "rolloutType" to RoomColumnShape("TEXT", true),
                "affectedVersions" to RoomColumnShape("TEXT", true),
                "targetVersion" to RoomColumnShape("TEXT", true),
                "status" to RoomColumnShape("TEXT", true),
                "rolledOutAt" to RoomColumnShape("INTEGER", false)
            ),
            listOf("index_fix_rollouts_fixId", "index_fix_rollouts_rolledOutAt", "index_fix_rollouts_ruleId_appliedAt")
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `fix_rollouts` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `fixId` TEXT NOT NULL,
            `rolloutType` TEXT NOT NULL,
            `affectedVersions` TEXT NOT NULL,
            `targetVersion` TEXT NOT NULL,
            `status` TEXT NOT NULL,
            `rolledOutAt` INTEGER
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fix_rollouts_fixId` ON `fix_rollouts` (`fixId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fix_rollouts_rolledOutAt` ON `fix_rollouts` (`rolledOutAt`)")
    }

    private val MIGRATION_34_35 = object : Migration(34, 35) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 34→35: Creating Elysium Vanguard telemetry tables")
            createVanguardTelemetryTables(db)
        }
    }

    private fun createVanguardCommerceTables(db: SupportSQLiteDatabase) {
        ensureRoomTableShape(
            db,
            "vanguard_events",
            linkedMapOf(
                "eventId" to RoomColumnShape("TEXT", true, 1),
                "aggregateType" to RoomColumnShape("TEXT", true),
                "aggregateId" to RoomColumnShape("TEXT", true),
                "eventType" to RoomColumnShape("TEXT", true),
                "actorId" to RoomColumnShape("TEXT", false),
                "actorRole" to RoomColumnShape("TEXT", false),
                "source" to RoomColumnShape("TEXT", true),
                "correlationId" to RoomColumnShape("TEXT", false),
                "causationId" to RoomColumnShape("TEXT", false),
                "idempotencyKey" to RoomColumnShape("TEXT", true),
                "payloadJson" to RoomColumnShape("TEXT", true),
                "schemaVersion" to RoomColumnShape("INTEGER", true),
                "occurredAt" to RoomColumnShape("INTEGER", true),
                "synced" to RoomColumnShape("INTEGER", true)
            ),
            listOf(
                "index_vanguard_events_aggregateId",
                "index_vanguard_events_occurredAt",
                "index_vanguard_events_idempotencyKey",
                "index_vanguard_events_aggregateType_aggregateId_occurredAt",
                "index_vanguard_events_eventType_occurredAt",
                "index_vanguard_events_synced_occurredAt"
            )
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `vanguard_events` (
            `eventId` TEXT NOT NULL,
            `aggregateType` TEXT NOT NULL,
            `aggregateId` TEXT NOT NULL,
            `eventType` TEXT NOT NULL,
            `actorId` TEXT,
            `actorRole` TEXT,
            `source` TEXT NOT NULL,
            `correlationId` TEXT,
            `causationId` TEXT,
            `idempotencyKey` TEXT NOT NULL,
            `payloadJson` TEXT NOT NULL,
            `schemaVersion` INTEGER NOT NULL,
            `occurredAt` INTEGER NOT NULL,
            `synced` INTEGER NOT NULL,
            PRIMARY KEY(`eventId`)
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_vanguard_events_aggregateId` ON `vanguard_events` (`aggregateId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_vanguard_events_occurredAt` ON `vanguard_events` (`occurredAt`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vanguard_events_idempotencyKey` ON `vanguard_events` (`idempotencyKey`)")

        ensureRoomTableShape(
            db,
            "marketplace_ledger_entries",
            linkedMapOf(
                "ledgerEntryId" to RoomColumnShape("TEXT", true, 1),
                "transactionId" to RoomColumnShape("TEXT", true),
                "relatedEventId" to RoomColumnShape("TEXT", false),
                "orderType" to RoomColumnShape("TEXT", true),
                "orderId" to RoomColumnShape("TEXT", true),
                "participantId" to RoomColumnShape("TEXT", false),
                "participantRole" to RoomColumnShape("TEXT", true),
                "entryType" to RoomColumnShape("TEXT", true),
                "direction" to RoomColumnShape("TEXT", true),
                "amountCents" to RoomColumnShape("INTEGER", true),
                "currency" to RoomColumnShape("TEXT", true),
                "status" to RoomColumnShape("TEXT", true),
                "metadataJson" to RoomColumnShape("TEXT", false),
                "createdAt" to RoomColumnShape("INTEGER", true),
                "settledAt" to RoomColumnShape("INTEGER", false),
                "idempotencyKey" to RoomColumnShape("TEXT", true),
                "synced" to RoomColumnShape("INTEGER", true)
            ),
            listOf(
                "index_marketplace_ledger_entries_transactionId",
                "index_marketplace_ledger_entries_orderId",
                "index_marketplace_ledger_entries_idempotencyKey",
                "index_marketplace_ledger_entries_orderType_orderId",
                "index_marketplace_ledger_entries_status_createdAt",
                "index_marketplace_ledger_entries_synced_createdAt"
            )
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `marketplace_ledger_entries` (
            `ledgerEntryId` TEXT NOT NULL,
            `transactionId` TEXT NOT NULL,
            `relatedEventId` TEXT,
            `orderType` TEXT NOT NULL,
            `orderId` TEXT NOT NULL,
            `participantId` TEXT,
            `participantRole` TEXT NOT NULL,
            `entryType` TEXT NOT NULL,
            `direction` TEXT NOT NULL,
            `amountCents` INTEGER NOT NULL,
            `currency` TEXT NOT NULL,
            `status` TEXT NOT NULL,
            `metadataJson` TEXT,
            `createdAt` INTEGER NOT NULL,
            `settledAt` INTEGER,
            `idempotencyKey` TEXT NOT NULL,
            `synced` INTEGER NOT NULL,
            PRIMARY KEY(`ledgerEntryId`)
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_marketplace_ledger_entries_transactionId` ON `marketplace_ledger_entries` (`transactionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_marketplace_ledger_entries_orderId` ON `marketplace_ledger_entries` (`orderId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_marketplace_ledger_entries_idempotencyKey` ON `marketplace_ledger_entries` (`idempotencyKey`)")

        ensureRoomTableShape(
            db,
            "vanguard_outbox",
            linkedMapOf(
                "outboxId" to RoomColumnShape("TEXT", true, 1),
                "eventId" to RoomColumnShape("TEXT", true),
                "destination" to RoomColumnShape("TEXT", true),
                "operation" to RoomColumnShape("TEXT", true),
                "payloadJson" to RoomColumnShape("TEXT", true),
                "status" to RoomColumnShape("TEXT", true),
                "attemptCount" to RoomColumnShape("INTEGER", true),
                "nextAttemptAt" to RoomColumnShape("INTEGER", true),
                "lastError" to RoomColumnShape("TEXT", false),
                "createdAt" to RoomColumnShape("INTEGER", true),
                "updatedAt" to RoomColumnShape("INTEGER", true),
                "idempotencyKey" to RoomColumnShape("TEXT", true)
            ),
            listOf(
                "index_vanguard_outbox_eventId",
                "index_vanguard_outbox_status",
                "index_vanguard_outbox_idempotencyKey",
                "index_vanguard_outbox_status_nextAttemptAt",
                "index_vanguard_outbox_destination_status"
            )
        )
        db.execSQL("""CREATE TABLE IF NOT EXISTS `vanguard_outbox` (
            `outboxId` TEXT NOT NULL,
            `eventId` TEXT NOT NULL,
            `destination` TEXT NOT NULL,
            `operation` TEXT NOT NULL,
            `payloadJson` TEXT NOT NULL,
            `status` TEXT NOT NULL,
            `attemptCount` INTEGER NOT NULL,
            `nextAttemptAt` INTEGER NOT NULL,
            `lastError` TEXT,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `idempotencyKey` TEXT NOT NULL,
            PRIMARY KEY(`outboxId`)
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_vanguard_outbox_eventId` ON `vanguard_outbox` (`eventId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_vanguard_outbox_status` ON `vanguard_outbox` (`status`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vanguard_outbox_idempotencyKey` ON `vanguard_outbox` (`idempotencyKey`)")
    }

    private val MIGRATION_35_36 = object : Migration(35, 36) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 35→36: Creating Vanguard commerce events, ledger, and outbox tables")
            createVanguardTelemetryTables(db)
            createVanguardCommerceTables(db)
        }
    }

    private val MIGRATION_36_37 = object : Migration(36, 37) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 36→37: Guarding Vanguard telemetry and commerce schema")
            createVanguardTelemetryTables(db)
            createVanguardCommerceTables(db)
        }
    }

    private val MIGRATION_37_38 = object : Migration(37, 38) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 37→38: Guarding Vanguard telemetry and commerce schema")
            createVanguardTelemetryTables(db)
            createVanguardCommerceTables(db)
        }
    }

    private val MIGRATION_38_39 = object : Migration(38, 39) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 38→39: Advancing existing installs without data loss")
            createVanguardTelemetryTables(db)
            createVanguardCommerceTables(db)
        }
    }

    private val MIGRATION_39_40 = object : Migration(39, 40) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 39→40: Repairing Vanguard Room schema after legacy debug builds")
            createVanguardTelemetryTables(db)
            createVanguardCommerceTables(db)
        }
    }

    private fun fallbackVehicleIdSql(): String =
        "COALESCE((SELECT id FROM vehicles ORDER BY createdAt DESC LIMIT 1), 'legacy-vanguard-vehicle')"

    private fun migrateLegacyVanguardTelemetry(db: SupportSQLiteDatabase) {
        createVanguardTelemetryTables(db)
        createVanguardCommerceTables(db)

        if (tableExists(db, "obd_pid_samples_legacy_before_v40")) {
            db.execSQL("""
                INSERT OR IGNORE INTO `vanguard_obd_sessions`
                    (`sessionId`, `vehicleId`, `adapterId`, `protocol`, `startedAt`, `endedAt`, `status`, `totalPidsRead`, `errorCount`, `lastError`)
                SELECT
                    `sessionId`,
                    COALESCE(NULLIF(TRIM(`vehicleId`), ''), ${fallbackVehicleIdSql()}),
                    NULL,
                    'LEGACY_PID_STREAM',
                    MIN(`timestampMs`),
                    MAX(`timestampMs`),
                    'RECOVERED_LEGACY',
                    SUM(CASE WHEN `value` IS NOT NULL THEN 1 ELSE 0 END),
                    SUM(CASE WHEN `errorReason` IS NOT NULL AND TRIM(`errorReason`) <> '' THEN 1 ELSE 0 END),
                    MAX(NULLIF(TRIM(`errorReason`), ''))
                FROM `obd_pid_samples_legacy_before_v40`
                WHERE `sessionId` IS NOT NULL AND TRIM(`sessionId`) <> ''
                GROUP BY `sessionId`
            """)
            db.execSQL("""
                INSERT INTO `obd_pid_samples` (`sessionId`, `pid`, `value`, `unit`, `capturedAt`)
                SELECT
                    legacy.`sessionId`,
                    legacy.`pid`,
                    legacy.`value`,
                    COALESCE(NULLIF(TRIM(legacy.`unit`), ''), ''),
                    legacy.`timestampMs`
                FROM `obd_pid_samples_legacy_before_v40` legacy
                WHERE legacy.`value` IS NOT NULL
                  AND legacy.`sessionId` IS NOT NULL
                  AND TRIM(legacy.`sessionId`) <> ''
                  AND legacy.`pid` IS NOT NULL
                  AND TRIM(legacy.`pid`) <> ''
                  AND NOT EXISTS (
                      SELECT 1 FROM `obd_pid_samples` live
                      WHERE live.`sessionId` = legacy.`sessionId`
                        AND live.`pid` = legacy.`pid`
                        AND live.`capturedAt` = legacy.`timestampMs`
                  )
            """)
            db.execSQL("""
                INSERT INTO `audit_logs` (`actorId`, `actorRole`, `action`, `resourceType`, `resourceId`, `payloadJson`, `occurredAt`)
                SELECT
                    NULL,
                    'SYSTEM',
                    'IMPORT_LEGACY_VANGUARD_SKIPPED',
                    'obd_pid_samples',
                    'null_value_samples',
                    '{"skippedNullValueRows":' || c || ',"reason":"new_schema_requires_real_numeric_value","backupTable":"obd_pid_samples_legacy_before_v40"}',
                    CAST(strftime('%s','now') AS INTEGER) * 1000
                FROM (SELECT COUNT(*) AS c FROM `obd_pid_samples_legacy_before_v40` WHERE `value` IS NULL)
                WHERE c > 0
            """)
        }

        if (tableExists(db, "derived_metrics_legacy_before_v40")) {
            db.execSQL("""
                INSERT OR IGNORE INTO `vanguard_obd_sessions`
                    (`sessionId`, `vehicleId`, `adapterId`, `protocol`, `startedAt`, `endedAt`, `status`, `totalPidsRead`, `errorCount`, `lastError`)
                SELECT
                    `sessionId`,
                    COALESCE(NULLIF(TRIM(`vehicleId`), ''), ${fallbackVehicleIdSql()}),
                    NULL,
                    'LEGACY_DERIVED_METRICS',
                    MIN(`timestampMs`),
                    MAX(`timestampMs`),
                    'RECOVERED_LEGACY',
                    0,
                    0,
                    NULL
                FROM `derived_metrics_legacy_before_v40`
                WHERE `sessionId` IS NOT NULL AND TRIM(`sessionId`) <> ''
                GROUP BY `sessionId`
            """)
            db.execSQL("""
                INSERT INTO `derived_metrics` (`vehicleId`, `metricName`, `value`, `unit`, `computedAt`)
                SELECT
                    COALESCE(NULLIF(TRIM(legacy.`vehicleId`), ''), ${fallbackVehicleIdSql()}),
                    legacy.`metricId`,
                    legacy.`value`,
                    COALESCE(NULLIF(TRIM(legacy.`unit`), ''), ''),
                    legacy.`timestampMs`
                FROM `derived_metrics_legacy_before_v40` legacy
                WHERE legacy.`value` IS NOT NULL
                  AND legacy.`metricId` IS NOT NULL
                  AND TRIM(legacy.`metricId`) <> ''
                  AND NOT EXISTS (
                      SELECT 1 FROM `derived_metrics` live
                      WHERE live.`vehicleId` = COALESCE(NULLIF(TRIM(legacy.`vehicleId`), ''), ${fallbackVehicleIdSql()})
                        AND live.`metricName` = legacy.`metricId`
                        AND live.`computedAt` = legacy.`timestampMs`
                  )
            """)
            db.execSQL("""
                INSERT INTO `audit_logs` (`actorId`, `actorRole`, `action`, `resourceType`, `resourceId`, `payloadJson`, `occurredAt`)
                SELECT
                    NULL,
                    'SYSTEM',
                    'IMPORT_LEGACY_VANGUARD_SKIPPED',
                    'derived_metrics',
                    'null_value_metrics',
                    '{"skippedNullValueRows":' || c || ',"reason":"new_schema_requires_real_numeric_value","backupTable":"derived_metrics_legacy_before_v40"}',
                    CAST(strftime('%s','now') AS INTEGER) * 1000
                FROM (SELECT COUNT(*) AS c FROM `derived_metrics_legacy_before_v40` WHERE `value` IS NULL)
                WHERE c > 0
            """)
        }

        if (tableExists(db, "ecu_failure_events_legacy_before_v40")) {
            db.execSQL("""
                INSERT OR IGNORE INTO `vanguard_obd_sessions`
                    (`sessionId`, `vehicleId`, `adapterId`, `protocol`, `startedAt`, `endedAt`, `status`, `totalPidsRead`, `errorCount`, `lastError`)
                SELECT
                    `sessionId`,
                    ${fallbackVehicleIdSql()},
                    MAX(NULLIF(TRIM(`adapterType`), '')),
                    COALESCE(MAX(NULLIF(TRIM(`protocolSelected`), '')), MAX(NULLIF(TRIM(`protocolAttempted`), '')), 'LEGACY_ECU_FAILURES'),
                    MIN(`timestampMs`),
                    MAX(`timestampMs`),
                    'RECOVERED_LEGACY',
                    0,
                    COUNT(*),
                    MAX(NULLIF(TRIM(`failureType` || ': ' || `reason`), ''))
                FROM `ecu_failure_events_legacy_before_v40`
                WHERE `sessionId` IS NOT NULL AND TRIM(`sessionId`) <> ''
                GROUP BY `sessionId`
            """)
            db.execSQL("""
                INSERT INTO `obd_command_log` (`sessionId`, `command`, `response`, `latencyMs`, `success`, `sentAt`)
                SELECT
                    legacy.`sessionId`,
                    legacy.`commandSent`,
                    COALESCE(NULLIF(TRIM(legacy.`normalizedResponse`), ''), NULLIF(TRIM(legacy.`rawResponse`), ''), NULLIF(TRIM(legacy.`negativeResponseCode`), ''), ''),
                    COALESCE(legacy.`latencyMs`, legacy.`timeoutMs`, 0),
                    CASE WHEN UPPER(COALESCE(legacy.`failureType`, '')) IN ('', 'NONE', 'SUCCESS') THEN 1 ELSE 0 END,
                    legacy.`timestampMs`
                FROM `ecu_failure_events_legacy_before_v40` legacy
                WHERE legacy.`sessionId` IS NOT NULL
                  AND TRIM(legacy.`sessionId`) <> ''
                  AND legacy.`commandSent` IS NOT NULL
                  AND TRIM(legacy.`commandSent`) <> ''
                  AND NOT EXISTS (
                      SELECT 1 FROM `obd_command_log` live
                      WHERE live.`sessionId` = legacy.`sessionId`
                        AND live.`command` = legacy.`commandSent`
                        AND live.`sentAt` = legacy.`timestampMs`
                  )
            """)
            db.execSQL("""
                INSERT OR IGNORE INTO `ecu_failure_events`
                    (`eventId`, `vehicleId`, `dtcCode`, `source`, `severity`, `description`, `detectedAt`, `resolvedAt`)
                SELECT
                    legacy.`id`,
                    ${fallbackVehicleIdSql()},
                    CASE
                        WHEN legacy.`pid` IS NOT NULL AND TRIM(legacy.`pid`) <> '' THEN 'PID_' || legacy.`pid`
                        WHEN legacy.`commandSent` IS NOT NULL AND TRIM(legacy.`commandSent`) <> '' THEN 'OBD_' || legacy.`commandSent`
                        ELSE 'LEGACY_' || COALESCE(NULLIF(TRIM(legacy.`failureType`), ''), 'ECU_FAILURE')
                    END,
                    'TELEMETRY',
                    CASE
                        WHEN legacy.`confidence` >= 0.85 THEN 'HIGH'
                        WHEN legacy.`confidence` >= 0.50 THEN 'MEDIUM'
                        ELSE 'LOW'
                    END,
                    'Legacy Vanguard ECU failure recovered. Type=' || COALESCE(legacy.`failureType`, 'UNKNOWN') ||
                        '; Reason=' || COALESCE(legacy.`reason`, '') ||
                        '; Command=' || COALESCE(legacy.`commandSent`, '') ||
                        '; Response=' || COALESCE(NULLIF(TRIM(legacy.`normalizedResponse`), ''), NULLIF(TRIM(legacy.`rawResponse`), ''), '') ||
                        '; Session=' || COALESCE(legacy.`sessionId`, ''),
                    legacy.`timestampMs`,
                    NULL
                FROM `ecu_failure_events_legacy_before_v40` legacy
                WHERE legacy.`id` IS NOT NULL AND TRIM(legacy.`id`) <> ''
            """)
        }

        if (tableExists(db, "mode06_results_legacy_before_v40")) {
            db.execSQL("""
                INSERT OR IGNORE INTO `vanguard_obd_sessions`
                    (`sessionId`, `vehicleId`, `adapterId`, `protocol`, `startedAt`, `endedAt`, `status`, `totalPidsRead`, `errorCount`, `lastError`)
                SELECT
                    `sessionId`,
                    COALESCE(NULLIF(TRIM(`vehicleId`), ''), ${fallbackVehicleIdSql()}),
                    NULL,
                    'LEGACY_MODE_06',
                    MIN(`timestampMs`),
                    MAX(`timestampMs`),
                    'RECOVERED_LEGACY',
                    0,
                    SUM(CASE WHEN `passed` = 0 THEN 1 ELSE 0 END),
                    MAX(CASE WHEN `passed` = 0 THEN NULLIF(TRIM(`testName` || ': ' || `explanation`), '') ELSE NULL END)
                FROM `mode06_results_legacy_before_v40`
                WHERE `sessionId` IS NOT NULL AND TRIM(`sessionId`) <> ''
                GROUP BY `sessionId`
            """)
            db.execSQL("""
                INSERT INTO `mode06_results` (`sessionId`, `testId`, `componentId`, `value`, `minValue`, `maxValue`, `status`, `capturedAt`)
                SELECT
                    legacy.`sessionId`,
                    COALESCE(NULLIF(TRIM(legacy.`mid` || ':' || legacy.`tid`), ':'), legacy.`id`),
                    COALESCE(NULLIF(TRIM(legacy.`componentName`), ''), NULLIF(TRIM(legacy.`testName`), ''), 'Legacy Mode 06'),
                    legacy.`value`,
                    COALESCE(legacy.`minLimit`, legacy.`value`),
                    COALESCE(legacy.`maxLimit`, legacy.`value`),
                    CASE WHEN legacy.`passed` = 1 THEN 'PASS' ELSE 'FAIL' END,
                    legacy.`timestampMs`
                FROM `mode06_results_legacy_before_v40` legacy
                WHERE legacy.`value` IS NOT NULL
                  AND legacy.`sessionId` IS NOT NULL
                  AND TRIM(legacy.`sessionId`) <> ''
                  AND NOT EXISTS (
                      SELECT 1 FROM `mode06_results` live
                      WHERE live.`sessionId` = legacy.`sessionId`
                        AND live.`testId` = COALESCE(NULLIF(TRIM(legacy.`mid` || ':' || legacy.`tid`), ':'), legacy.`id`)
                        AND live.`capturedAt` = legacy.`timestampMs`
                  )
            """)
        }

        db.execSQL("""
            UPDATE `vanguard_obd_sessions`
            SET `totalPidsRead` = (
                SELECT COUNT(*) FROM `obd_pid_samples` samples
                WHERE samples.`sessionId` = `vanguard_obd_sessions`.`sessionId`
            )
            WHERE `status` = 'RECOVERED_LEGACY'
        """)

        db.execSQL("""
            INSERT INTO `vehicle_history` (`vehicleId`, `eventType`, `eventAt`, `summary`, `payloadJson`)
            SELECT
                session.`vehicleId`,
                'LEGACY_VANGUARD_IMPORT',
                session.`startedAt`,
                'Telemetría Vanguard legacy recuperada para sesión ' || session.`sessionId`,
                '{"sessionId":"' || session.`sessionId` || '","totalPidsRead":' || session.`totalPidsRead` || ',"errorCount":' || session.`errorCount` || '}'
            FROM `vanguard_obd_sessions` session
            WHERE session.`status` = 'RECOVERED_LEGACY'
              AND NOT EXISTS (
                  SELECT 1 FROM `vehicle_history` history
                  WHERE history.`eventType` = 'LEGACY_VANGUARD_IMPORT'
                    AND history.`payloadJson` LIKE '%"sessionId":"' || session.`sessionId` || '"%'
              )
        """)

        db.execSQL("""
            INSERT INTO `audit_logs` (`actorId`, `actorRole`, `action`, `resourceType`, `resourceId`, `payloadJson`, `occurredAt`)
            VALUES (
                NULL,
                'SYSTEM',
                'IMPORT_LEGACY_VANGUARD',
                'ROOM_MIGRATION',
                '40_to_41',
                '{"obdPidSamples":' || (SELECT COUNT(*) FROM `obd_pid_samples`) ||
                    ',"derivedMetrics":' || (SELECT COUNT(*) FROM `derived_metrics`) ||
                    ',"ecuFailures":' || (SELECT COUNT(*) FROM `ecu_failure_events`) ||
                    ',"mode06Results":' || (SELECT COUNT(*) FROM `mode06_results`) || '}',
                CAST(strftime('%s','now') AS INTEGER) * 1000
            )
        """)
    }

    private fun ensureRepairCasesAndEventsForDtcEvents(db: SupportSQLiteDatabase) {
        db.execSQL("""
            INSERT OR IGNORE INTO `repair_cases`
                (`id`, `vehicleMake`, `vehicleModel`, `year`, `engine`, `country`, `dtcCode`, `symptoms`, `solution`, `cost`, `timeSpent`, `partsUsed`, `verified`, `votes`, `successRate`, `isBookmarked`, `isMyContribution`, `createdAt`)
            SELECT
                'dtc_event_case_' || event.`id`,
                COALESCE(NULLIF(TRIM(vehicle.`make`), ''), 'Desconocido'),
                COALESCE(NULLIF(TRIM(vehicle.`model`), ''), 'Desconocido'),
                COALESCE(vehicle.`year`, 0),
                COALESCE(NULLIF(TRIM(vehicle.`engine`), ''), 'Motor no especificado'),
                'Local',
                event.`code`,
                'Evento DTC ' || event.`status` || ' detectado por scanner. ' || COALESCE(event.`description`, ''),
                'Caso generado automáticamente desde DTC real. Abrir la guía de reparación de ' || event.`code` ||
                    ' y validar freeze frame, causa raíz, prueba después de reparación y borrado controlado del código.',
                0.0,
                0,
                COALESCE(
                    (SELECT NULLIF(TRIM(definition.`affectedComponents`), '') FROM `dtc_definitions` definition WHERE definition.`code` = event.`code` ORDER BY CASE WHEN definition.`manufacturer` = 'GENERIC' THEN 0 ELSE 1 END LIMIT 1),
                    (SELECT NULLIF(TRIM(definition.`possibleCauses`), '') FROM `dtc_definitions` definition WHERE definition.`code` = event.`code` ORDER BY CASE WHEN definition.`manufacturer` = 'GENERIC' THEN 0 ELSE 1 END LIMIT 1),
                    'Por diagnosticar'
                ),
                0,
                0,
                0.0,
                0,
                1,
                COALESCE(NULLIF(event.`firstSeenAt`, 0), CAST(strftime('%s','now') AS INTEGER) * 1000)
            FROM `dtc_events` event
            LEFT JOIN `vehicles` vehicle ON vehicle.`id` = event.`vehicleId`
            WHERE event.`id` IS NOT NULL AND TRIM(event.`id`) <> ''
              AND event.`code` IS NOT NULL AND TRIM(event.`code`) <> ''
        """)

        db.execSQL("""
            INSERT OR IGNORE INTO `vanguard_events`
                (`eventId`, `aggregateType`, `aggregateId`, `eventType`, `actorId`, `actorRole`, `source`, `correlationId`, `causationId`, `idempotencyKey`, `payloadJson`, `schemaVersion`, `occurredAt`, `synced`)
            SELECT
                'v41_dtc_' || event.`id`,
                'DTC',
                event.`id`,
                'DTC_EVENT_RESTORED',
                NULL,
                'SYSTEM',
                'LOCAL_ROOM',
                event.`sessionId`,
                NULL,
                'v41_dtc_' || event.`id`,
                '{"dtcCode":"' || event.`code` || '","vehicleId":"' || event.`vehicleId` || '","status":"' || event.`status` || '","repairCaseId":"dtc_event_case_' || event.`id` || '"}',
                1,
                COALESCE(NULLIF(event.`lastSeenAt`, 0), event.`firstSeenAt`, CAST(strftime('%s','now') AS INTEGER) * 1000),
                0
            FROM `dtc_events` event
            WHERE event.`id` IS NOT NULL AND TRIM(event.`id`) <> ''
        """)

        db.execSQL("""
            INSERT OR IGNORE INTO `vanguard_events`
                (`eventId`, `aggregateType`, `aggregateId`, `eventType`, `actorId`, `actorRole`, `source`, `correlationId`, `causationId`, `idempotencyKey`, `payloadJson`, `schemaVersion`, `occurredAt`, `synced`)
            SELECT
                'v41_repair_case_' || event.`id`,
                'REPAIR',
                'dtc_event_case_' || event.`id`,
                'REPAIR_CASE_ENSURED_FOR_DTC',
                NULL,
                'SYSTEM',
                'LOCAL_ROOM',
                event.`sessionId`,
                'v41_dtc_' || event.`id`,
                'v41_repair_case_' || event.`id`,
                '{"dtcCode":"' || event.`code` || '","dtcEventId":"' || event.`id` || '","source":"room_migration_40_41"}',
                1,
                COALESCE(NULLIF(event.`lastSeenAt`, 0), event.`firstSeenAt`, CAST(strftime('%s','now') AS INTEGER) * 1000),
                0
            FROM `dtc_events` event
            WHERE event.`id` IS NOT NULL AND TRIM(event.`id`) <> ''
        """)

        db.execSQL("""
            INSERT INTO `vehicle_history` (`vehicleId`, `eventType`, `eventAt`, `summary`, `payloadJson`)
            SELECT
                event.`vehicleId`,
                'DTC_REPAIR_CASE_ENSURED',
                COALESCE(NULLIF(event.`lastSeenAt`, 0), event.`firstSeenAt`, CAST(strftime('%s','now') AS INTEGER) * 1000),
                'Caso de reparación asegurado para DTC ' || event.`code`,
                '{"dtcEventId":"' || event.`id` || '","repairCaseId":"dtc_event_case_' || event.`id` || '"}'
            FROM `dtc_events` event
            WHERE NOT EXISTS (
                SELECT 1 FROM `vehicle_history` history
                WHERE history.`eventType` = 'DTC_REPAIR_CASE_ENSURED'
                  AND history.`payloadJson` LIKE '%"dtcEventId":"' || event.`id` || '"%'
            )
        """)
    }

    private val MIGRATION_40_41 = object : Migration(40, 41) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 40→41: Restoring legacy Vanguard data and ensuring DTC repair cases/events")
            migrateLegacyVanguardTelemetry(db)
            ensureRepairCasesAndEventsForDtcEvents(db)
        }
    }

    /**
     * V2 Certified Reports — adds the 5 tables introduced for the
     * Reports PDF Certificados + Vehicle Technical History round.
     *
     * Mirrors the Postgres schema in
     *   supabase/migrations/20260704000000_reports_foundations.sql
     *   supabase/migrations/20260705000000_reports_sync_and_evidence_extend.sql
     *
     * Indexes + FKs are created with `IF NOT EXISTS` so re-running the
     * migration on a partially-upgraded device is safe.
     */
    private val MIGRATION_41_42 = object : Migration(41, 42) {
        override fun migrate(db: SupportSQLiteDatabase) {
            android.util.Log.i("ElysiumDB", "Migration 41→42: Certified Reports V2 (certified_reports, report_evidence, repair_actions, report_signatures, diagnostic_snapshots)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `certified_reports` (
                  `reportId` TEXT NOT NULL,
                  `vehicleId` TEXT NOT NULL,
                  `userId` TEXT NOT NULL,
                  `reportType` TEXT NOT NULL,
                  `title` TEXT NOT NULL,
                  `status` TEXT NOT NULL,
                  `odometerKm` INTEGER,
                  `vin` TEXT,
                  `plate` TEXT,
                  `generatedAt` INTEGER NOT NULL,
                  `signedAt` INTEGER,
                  `pdfUri` TEXT,
                  `qrVerificationUrl` TEXT,
                  `integrityHash` TEXT NOT NULL,
                  `previousHash` TEXT,
                  `createdAt` INTEGER NOT NULL,
                  `updatedAt` INTEGER NOT NULL,
                  PRIMARY KEY(`reportId`)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_certified_reports_vehicleId_generatedAt` ON `certified_reports` (`vehicleId`, `generatedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_certified_reports_userId_generatedAt` ON `certified_reports` (`userId`, `generatedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_certified_reports_vehicleId_integrityHash` ON `certified_reports` (`vehicleId`, `integrityHash`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_certified_reports_status` ON `certified_reports` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_certified_reports_reportType` ON `certified_reports` (`reportType`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `report_evidence` (
                  `evidenceId` TEXT NOT NULL,
                  `reportId` TEXT NOT NULL,
                  `evidenceType` TEXT NOT NULL,
                  `label` TEXT NOT NULL,
                  `description` TEXT NOT NULL,
                  `uri` TEXT NOT NULL,
                  `hash` TEXT,
                  `capturedAt` INTEGER NOT NULL,
                  `lat` REAL,
                  `lng` REAL,
                  PRIMARY KEY(`evidenceId`),
                  FOREIGN KEY(`reportId`) REFERENCES `certified_reports`(`reportId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_report_evidence_reportId_capturedAt` ON `report_evidence` (`reportId`, `capturedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_report_evidence_evidenceType` ON `report_evidence` (`evidenceType`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `repair_actions` (
                  `actionId` TEXT NOT NULL,
                  `reportId` TEXT NOT NULL,
                  `actionType` TEXT NOT NULL,
                  `component` TEXT NOT NULL,
                  `dtcRelated` TEXT,
                  `description` TEXT NOT NULL,
                  `partUsed` TEXT,
                  `supplier` TEXT,
                  `mechanic` TEXT,
                  `cost` REAL,
                  `currency` TEXT NOT NULL,
                  `warrantyDays` INTEGER,
                  `createdAt` INTEGER NOT NULL,
                  PRIMARY KEY(`actionId`),
                  FOREIGN KEY(`reportId`) REFERENCES `certified_reports`(`reportId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_repair_actions_reportId` ON `repair_actions` (`reportId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_repair_actions_dtcRelated` ON `repair_actions` (`dtcRelated`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_repair_actions_supplier` ON `repair_actions` (`supplier`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `report_signatures` (
                  `signatureId` TEXT NOT NULL,
                  `reportId` TEXT NOT NULL,
                  `signerName` TEXT NOT NULL,
                  `signerRole` TEXT NOT NULL,
                  `signatureImageUri` TEXT NOT NULL,
                  `signedAt` INTEGER NOT NULL,
                  `deviceIdHash` TEXT NOT NULL,
                  `integrityHash` TEXT NOT NULL,
                  PRIMARY KEY(`signatureId`),
                  FOREIGN KEY(`reportId`) REFERENCES `certified_reports`(`reportId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_report_signatures_reportId` ON `report_signatures` (`reportId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_report_signatures_signerName` ON `report_signatures` (`signerName`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `diagnostic_snapshots` (
                  `snapshotId` TEXT NOT NULL,
                  `vehicleId` TEXT NOT NULL,
                  `sessionId` TEXT,
                  `createdAtMs` INTEGER NOT NULL,
                  `dtcsActiveJson` TEXT NOT NULL,
                  `dtcsPendingJson` TEXT NOT NULL,
                  `dtcsPermanentJson` TEXT NOT NULL,
                  `freezeFramePidValuesJson` TEXT NOT NULL,
                  `livePidsJson` TEXT NOT NULL,
                  `readinessJson` TEXT NOT NULL,
                  `ecuVoltage` REAL,
                  `rpm` REAL,
                  `coolantTempC` REAL,
                  `speedKph` REAL,
                  `engineLoadPct` REAL,
                  `fuelTrimStft` REAL,
                  `fuelTrimLtft` REAL,
                  `rawFramesJson` TEXT NOT NULL,
                  `notes` TEXT NOT NULL,
                  `liveFromAdapter` INTEGER NOT NULL,
                  `provenanceLabel` TEXT NOT NULL,
                  `hashSha256` TEXT NOT NULL,
                  `reportId` TEXT,
                  PRIMARY KEY(`snapshotId`),
                  FOREIGN KEY(`reportId`) REFERENCES `certified_reports`(`reportId`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_diagnostic_snapshots_vehicleId_createdAtMs` ON `diagnostic_snapshots` (`vehicleId`, `createdAtMs`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_diagnostic_snapshots_reportId` ON `diagnostic_snapshots` (`reportId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_diagnostic_snapshots_hashSha256` ON `diagnostic_snapshots` (`hashSha256`)")
        }
    }

    private val MIGRATION_42_43 = object : Migration(42, 43) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `ride_requests` ADD COLUMN `stopsJson` TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE `ride_requests` ADD COLUMN `paymentMethod` TEXT NOT NULL DEFAULT 'CASH'")
            db.execSQL("ALTER TABLE `ride_requests` ADD COLUMN `quoteVersion` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `ride_requests` ADD COLUMN `fareBreakdownJson` TEXT NOT NULL DEFAULT '{}'")
        }
    }

    private val MIGRATION_43_44 = object : Migration(43, 44) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `ride_requests` ADD COLUMN `priceOfferMinor` INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                """
                UPDATE `ride_requests`
                SET `priceOfferMinor` = CAST(
                    ROUND(
                        CASE
                            WHEN UPPER(`currency`) = 'CRC' THEN `priceOffer`
                            ELSE `priceOffer` * 100.0
                        END
                    ) AS INTEGER
                )
                """.trimIndent()
            )
            db.execSQL(
                "ALTER TABLE `ride_requests` ADD COLUMN `finalPriceMinor` INTEGER"
            )
            db.execSQL(
                """
                UPDATE `ride_requests`
                SET `finalPriceMinor` = CASE
                    WHEN `finalPrice` IS NULL THEN NULL
                    ELSE CAST(
                        ROUND(
                            CASE
                                WHEN UPPER(`currency`) = 'CRC' THEN `finalPrice`
                                ELSE `finalPrice` * 100.0
                            END
                        ) AS INTEGER
                    )
                END
                """.trimIndent()
            )
            db.execSQL(
                "ALTER TABLE `ride_requests` ADD COLUMN `serverState` TEXT"
            )
            db.execSQL(
                "ALTER TABLE `ride_requests` ADD COLUMN `serverVersion` INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE `ride_requests` ADD COLUMN `serverAssignedVehicleId` TEXT"
            )
            db.execSQL(
                "ALTER TABLE `ride_requests` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'"
            )
            db.execSQL(
                "ALTER TABLE `ride_requests` ADD COLUMN `lastSyncedAt` INTEGER"
            )
            db.execSQL(
                "ALTER TABLE `ride_requests` ADD COLUMN `lastCorrelationId` TEXT"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ride_command_outbox` (
                    `idempotencyKey` TEXT NOT NULL,
                    `rideId` TEXT NOT NULL,
                    `actorSessionUserId` TEXT NOT NULL,
                    `commandType` TEXT NOT NULL,
                    `expectedVersion` INTEGER NOT NULL,
                    `payloadVersion` INTEGER NOT NULL,
                    `payloadJson` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `attemptCount` INTEGER NOT NULL,
                    `nextAttemptAt` INTEGER NOT NULL,
                    `leaseStartedAt` INTEGER,
                    `lastErrorCode` TEXT,
                    `lastErrorMessage` TEXT,
                    `correlationId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`idempotencyKey`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                `index_ride_command_outbox_rideId_createdAt`
                ON `ride_command_outbox` (`rideId`, `createdAt`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                `index_ride_command_outbox_status_nextAttemptAt`
                ON `ride_command_outbox` (`status`, `nextAttemptAt`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                `index_ride_command_outbox_actorSessionUserId_status`
                ON `ride_command_outbox` (`actorSessionUserId`, `status`)
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_44_45 = object : Migration(44, 45) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `ride_requests` ADD COLUMN `boardingPin` TEXT"
            )
            db.execSQL(
                "ALTER TABLE `ride_requests` ADD COLUMN `boardingPinExpiresAt` INTEGER"
            )
        }
    }

    private val MIGRATION_45_46 = object : Migration(45, 46) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Existing records have no trustworthy capacity source. One seat
            // is the conservative fallback until the driver edits onboarding.
            db.execSQL(
                "ALTER TABLE driver_verifications " +
                    "ADD COLUMN vehicleSeats INTEGER NOT NULL DEFAULT 1"
            )
        }
    }

    private val MIGRATION_46_47 = object : Migration(46, 47) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE ride_chat_messages ADD COLUMN imageFilePath TEXT")
            db.execSQL("ALTER TABLE ride_chat_messages ADD COLUMN remoteMediaPath TEXT")
            db.execSQL("ALTER TABLE ride_chat_messages ADD COLUMN mediaMimeType TEXT")
            db.execSQL("ALTER TABLE ride_chat_messages ADD COLUMN syncState TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
            db.execSQL("ALTER TABLE service_bids ADD COLUMN providerPhone TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_47_48 = object : Migration(47, 48) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE ride_requests ADD COLUMN fareMode TEXT NOT NULL DEFAULT 'OPEN_BID'")
            db.execSQL("ALTER TABLE ride_requests ADD COLUMN distanceRateMinorPerKm INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE ride_requests ADD COLUMN timeRateMinorPerMinute INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE ride_requests ADD COLUMN estimatedFareMinor INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE ride_requests ADD COLUMN fareRateCardVersion INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE ride_requests ADD COLUMN allowsInTripStops INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_48_49 = object : Migration(48, 49) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE dtc_events ADD COLUMN observationState TEXT NOT NULL DEFAULT 'OBSERVED'"
            )
        }
    }

    internal val MIGRATION_49_50 = object : Migration(49, 50) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE dtc_events ADD COLUMN diagnosticNamespace TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE dtc_events ADD COLUMN moduleIdentity TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE dtc_events ADD COLUMN moduleName TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE dtc_events ADD COLUMN targetAddress TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE dtc_events ADD COLUMN responseAddress TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE dtc_events ADD COLUMN sourceService TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE dtc_events ADD COLUMN statusByte INTEGER")
            db.execSQL("ALTER TABLE dtc_events ADD COLUMN observationSemantic TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                """CREATE INDEX IF NOT EXISTS index_dtc_events_finding_identity
                   ON dtc_events(vehicleId, diagnosticNamespace, moduleIdentity, code, observationSemantic)"""
            )
        }
    }

    internal val MIGRATION_50_51 = object : Migration(50, 51) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE dtc_events ADD COLUMN rawDtcIdentity TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE dtc_events ADD COLUMN rawDtc24 INTEGER")
            db.execSQL("ALTER TABLE dtc_events ADD COLUMN failureType INTEGER")
            db.execSQL("ALTER TABLE dtc_events ADD COLUMN dtcFormat TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("UPDATE dtc_events SET rawDtcIdentity = UPPER(code) WHERE rawDtcIdentity = ''")
            db.execSQL("DROP INDEX IF EXISTS index_dtc_events_finding_identity")
            db.execSQL(
                """CREATE INDEX IF NOT EXISTS index_dtc_events_finding_identity
                   ON dtc_events(vehicleId, diagnosticNamespace, moduleIdentity, rawDtcIdentity)"""
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS diagnostic_exchanges (
                    id TEXT NOT NULL PRIMARY KEY,
                    sessionId TEXT NOT NULL,
                    timestampMs INTEGER NOT NULL,
                    transport TEXT NOT NULL,
                    applicationProtocol TEXT NOT NULL,
                    requestScope TEXT NOT NULL,
                    requestAddress TEXT,
                    responseAddress TEXT,
                    service TEXT NOT NULL,
                    rawRequest TEXT NOT NULL,
                    rawResponse TEXT NOT NULL,
                    decodedOutcome TEXT NOT NULL,
                    latencyMs INTEGER,
                    retryCount INTEGER NOT NULL,
                    negativeResponseCode INTEGER,
                    adapterConfiguration TEXT NOT NULL,
                    parserVersion TEXT NOT NULL
                )"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_exchanges_sessionId_timestampMs ON diagnostic_exchanges(sessionId, timestampMs)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_exchanges_responseAddress_service ON diagnostic_exchanges(responseAddress, service)")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS diagnostic_findings (
                    id TEXT NOT NULL PRIMARY KEY,
                    vehicleId TEXT NOT NULL,
                    ecuEndpointId TEXT NOT NULL,
                    diagnosticNamespace TEXT NOT NULL,
                    rawDtcIdentity TEXT NOT NULL,
                    displayCode TEXT NOT NULL,
                    createdAtMs INTEGER NOT NULL,
                    resolutionState TEXT NOT NULL DEFAULT 'OPEN',
                    resolvedAtMs INTEGER
                )"""
            )
            db.execSQL(
                """CREATE UNIQUE INDEX IF NOT EXISTS index_diagnostic_findings_stable_identity
                   ON diagnostic_findings(vehicleId, ecuEndpointId, diagnosticNamespace, rawDtcIdentity)"""
            )
            db.execSQL(
                """INSERT OR IGNORE INTO diagnostic_findings(
                    id, vehicleId, ecuEndpointId, diagnosticNamespace, rawDtcIdentity,
                    displayCode, createdAtMs, resolutionState, resolvedAtMs
                )
                SELECT event.id, event.vehicleId,
                       CASE WHEN event.moduleIdentity = '' THEN 'LEGACY' ELSE event.moduleIdentity END,
                       CASE WHEN event.diagnosticNamespace = '' THEN 'SAE_OBD' ELSE event.diagnosticNamespace END,
                       event.rawDtcIdentity, event.code, event.firstSeenAt,
                       'OPEN', NULL
                FROM dtc_events event
                WHERE NOT EXISTS (
                    SELECT 1 FROM dtc_events earlier
                    WHERE earlier.vehicleId = event.vehicleId
                      AND (CASE WHEN earlier.moduleIdentity = '' THEN 'LEGACY' ELSE earlier.moduleIdentity END) =
                          (CASE WHEN event.moduleIdentity = '' THEN 'LEGACY' ELSE event.moduleIdentity END)
                      AND (CASE WHEN earlier.diagnosticNamespace = '' THEN 'SAE_OBD' ELSE earlier.diagnosticNamespace END) =
                          (CASE WHEN event.diagnosticNamespace = '' THEN 'SAE_OBD' ELSE event.diagnosticNamespace END)
                      AND UPPER(earlier.code) = UPPER(event.code)
                      AND (earlier.firstSeenAt < event.firstSeenAt OR
                           (earlier.firstSeenAt = event.firstSeenAt AND earlier.id < event.id))
                )"""
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS diagnostic_observations (
                    id TEXT NOT NULL PRIMARY KEY,
                    findingId TEXT NOT NULL,
                    sessionId TEXT NOT NULL,
                    observedAt INTEGER NOT NULL,
                    observationState TEXT NOT NULL,
                    semantics TEXT NOT NULL,
                    statusByte INTEGER,
                    sourceService TEXT NOT NULL,
                    exchangeId TEXT,
                    rawPayloadHash TEXT NOT NULL
                )"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_observations_findingId_observedAt ON diagnostic_observations(findingId, observedAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_observations_sessionId_observedAt ON diagnostic_observations(sessionId, observedAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_observations_exchangeId ON diagnostic_observations(exchangeId)")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS finding_diagnostic_snapshots (
                    id TEXT NOT NULL PRIMARY KEY,
                    findingId TEXT NOT NULL,
                    moduleIdentity TEXT NOT NULL,
                    capturedAtMs INTEGER NOT NULL,
                    source TEXT NOT NULL,
                    parametersJson TEXT NOT NULL,
                    rawExchangeIdsJson TEXT NOT NULL
                )"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_finding_diagnostic_snapshots_findingId_capturedAtMs ON finding_diagnostic_snapshots(findingId, capturedAtMs)")
            db.execSQL("ALTER TABLE derived_metrics ADD COLUMN origin TEXT NOT NULL DEFAULT 'DERIVED'")
            db.execSQL("ALTER TABLE derived_metrics ADD COLUMN confidence REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE derived_metrics ADD COLUMN inputPidsJson TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE derived_metrics ADD COLUMN formulaVersion TEXT NOT NULL DEFAULT 'UNVERSIONED'")
            db.execSQL("ALTER TABLE dtc_definitions ADD COLUMN diagnosticNamespace TEXT NOT NULL DEFAULT 'SAE_OBD'")
            db.execSQL("ALTER TABLE dtc_definitions ADD COLUMN dtcFormat TEXT NOT NULL DEFAULT 'SAE_J2012_2_BYTE'")
            db.execSQL("ALTER TABLE dtc_definitions ADD COLUMN failureType INTEGER")
            db.execSQL("ALTER TABLE dtc_definitions ADD COLUMN ecuFamily TEXT")
            db.execSQL("ALTER TABLE dtc_definitions ADD COLUMN calibration TEXT")
            db.execSQL("ALTER TABLE dtc_definitions ADD COLUMN sourceAuthority TEXT NOT NULL DEFAULT 'UNVERIFIED'")
            db.execSQL("ALTER TABLE dtc_definitions ADD COLUMN sourceVersion TEXT")
            db.execSQL("ALTER TABLE dtc_definitions ADD COLUMN vehicleApplicabilityJson TEXT NOT NULL DEFAULT '{}'")
            db.execSQL("ALTER TABLE dtc_definitions ADD COLUMN verificationStatus TEXT NOT NULL DEFAULT 'UNVERIFIED'")
        }
    }

    internal val MIGRATION_51_52 = object : Migration(51, 52) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE diagnostic_exchanges ADD COLUMN sessionSequence INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE diagnostic_exchanges ADD COLUMN elapsedRealtimeNanos INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE diagnostic_exchanges ADD COLUMN rawRequestHash TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE diagnostic_exchanges ADD COLUMN rawResponseHash TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE diagnostic_exchanges ADD COLUMN previousExchangeHash TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE diagnostic_exchanges ADD COLUMN exchangeHash TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE diagnostic_exchanges ADD COLUMN retentionClass TEXT NOT NULL DEFAULT 'RAW_FORENSIC'")
            db.execSQL("ALTER TABLE diagnostic_exchanges ADD COLUMN expiresAtMs INTEGER")

            // Preserve pre-51 lifecycle information as explicitly limited legacy evidence.
            db.execSQL(
                """INSERT OR IGNORE INTO diagnostic_observations(
                       id, findingId, sessionId, observedAt, observationState, semantics,
                       statusByte, sourceService, exchangeId, rawPayloadHash
                   )
                   SELECT 'legacy-import-' || event.id, finding.id, event.sessionId, event.lastSeenAt,
                          'LEGACY_IMPORTED',
                          CASE WHEN event.observationSemantic = '' THEN 'LEGACY_LIMITED' ELSE event.observationSemantic END,
                          event.statusByte,
                          CASE WHEN event.sourceService = '' THEN 'LEGACY_UNKNOWN' ELSE event.sourceService END,
                          NULL, ''
                   FROM dtc_events event
                   INNER JOIN diagnostic_findings finding
                     ON finding.vehicleId = event.vehicleId
                    AND finding.ecuEndpointId = CASE WHEN event.moduleIdentity = '' THEN 'LEGACY' ELSE event.moduleIdentity END
                    AND finding.diagnosticNamespace = CASE WHEN event.diagnosticNamespace = '' THEN 'SAE_OBD' ELSE event.diagnosticNamespace END
                    AND finding.rawDtcIdentity = event.rawDtcIdentity
                   WHERE NOT EXISTS (
                       SELECT 1 FROM diagnostic_observations observation
                       WHERE observation.id = 'legacy-import-' || event.id
                   )"""
            )

            db.execSQL(
                """CREATE TABLE diagnostic_observations_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    findingId TEXT NOT NULL,
                    sessionId TEXT NOT NULL,
                    observedAt INTEGER NOT NULL,
                    observationState TEXT NOT NULL,
                    semantics TEXT NOT NULL,
                    statusByte INTEGER,
                    sourceService TEXT NOT NULL,
                    exchangeId TEXT,
                    rawPayloadHash TEXT NOT NULL,
                    sessionSequence INTEGER NOT NULL DEFAULT 0,
                    elapsedRealtimeNanos INTEGER NOT NULL DEFAULT 0,
                    previousObservationHash TEXT NOT NULL DEFAULT '',
                    observationHash TEXT NOT NULL DEFAULT '',
                    FOREIGN KEY(findingId) REFERENCES diagnostic_findings(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(exchangeId) REFERENCES diagnostic_exchanges(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                )"""
            )
            db.execSQL(
                """INSERT INTO diagnostic_observations_new(
                    id, findingId, sessionId, observedAt, observationState, semantics,
                    statusByte, sourceService, exchangeId, rawPayloadHash
                ) SELECT observation.id, observation.findingId, observation.sessionId,
                         observation.observedAt, observation.observationState, observation.semantics,
                         observation.statusByte, observation.sourceService,
                         CASE WHEN observation.exchangeId IS NULL OR exchange.id IS NOT NULL
                              THEN observation.exchangeId ELSE NULL END,
                         observation.rawPayloadHash
                  FROM diagnostic_observations observation
                  INNER JOIN diagnostic_findings finding ON finding.id = observation.findingId
                  LEFT JOIN diagnostic_exchanges exchange ON exchange.id = observation.exchangeId"""
            )
            db.execSQL("DROP TABLE diagnostic_observations")
            db.execSQL("ALTER TABLE diagnostic_observations_new RENAME TO diagnostic_observations")
            db.execSQL("CREATE INDEX index_diagnostic_observations_findingId_observedAt ON diagnostic_observations(findingId, observedAt)")
            db.execSQL("CREATE INDEX index_diagnostic_observations_sessionId_observedAt ON diagnostic_observations(sessionId, observedAt)")
            db.execSQL("CREATE INDEX index_diagnostic_observations_exchangeId ON diagnostic_observations(exchangeId)")

            db.execSQL(
                """CREATE TABLE finding_diagnostic_snapshots_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    findingId TEXT NOT NULL,
                    moduleIdentity TEXT NOT NULL,
                    capturedAtMs INTEGER NOT NULL,
                    source TEXT NOT NULL,
                    parametersJson TEXT NOT NULL,
                    rawExchangeIdsJson TEXT NOT NULL,
                    FOREIGN KEY(findingId) REFERENCES diagnostic_findings(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                )"""
            )
            // Preserve orphaned legacy snapshots without inventing a DTC identity.
            db.execSQL(
                """INSERT OR IGNORE INTO diagnostic_findings(
                       id, vehicleId, ecuEndpointId, diagnosticNamespace, rawDtcIdentity,
                       displayCode, createdAtMs, resolutionState, resolvedAtMs
                   )
                   SELECT snapshot.findingId, 'LEGACY_UNKNOWN',
                          CASE WHEN snapshot.moduleIdentity = '' THEN 'LEGACY_UNKNOWN' ELSE snapshot.moduleIdentity END,
                          'LEGACY_UNSCOPED', 'SNAPSHOT:' || snapshot.id,
                          'Dato no capturado', snapshot.capturedAtMs, 'OPEN', NULL
                   FROM finding_diagnostic_snapshots snapshot
                   LEFT JOIN diagnostic_findings finding ON finding.id = snapshot.findingId
                   WHERE finding.id IS NULL"""
            )
            db.execSQL(
                """INSERT INTO finding_diagnostic_snapshots_new
                   SELECT * FROM finding_diagnostic_snapshots"""
            )
            db.execSQL("DROP TABLE finding_diagnostic_snapshots")
            db.execSQL("ALTER TABLE finding_diagnostic_snapshots_new RENAME TO finding_diagnostic_snapshots")
            db.execSQL("CREATE INDEX index_finding_diagnostic_snapshots_findingId_capturedAtMs ON finding_diagnostic_snapshots(findingId, capturedAtMs)")

            db.execSQL(
                """CREATE TABLE diagnostic_session_integrity (
                    scanId TEXT NOT NULL PRIMARY KEY,
                    sessionId TEXT NOT NULL,
                    parserVersion TEXT NOT NULL,
                    firstSequence INTEGER NOT NULL,
                    lastSequence INTEGER NOT NULL,
                    leafCount INTEGER NOT NULL,
                    merkleRoot TEXT NOT NULL,
                    finalizedAtMs INTEGER NOT NULL,
                    hashAlgorithm TEXT NOT NULL,
                    canonicalizationVersion TEXT NOT NULL
                )"""
            )
            db.execSQL("CREATE INDEX index_diagnostic_session_integrity_sessionId_finalizedAtMs ON diagnostic_session_integrity(sessionId, finalizedAtMs)")
        }
    }

    internal val MIGRATION_52_53 = object : Migration(52, 53) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS finding_snapshot_exchange_refs (
                    snapshotId TEXT NOT NULL,
                    exchangeId TEXT NOT NULL,
                    ordinal INTEGER NOT NULL,
                    role TEXT NOT NULL,
                    PRIMARY KEY(snapshotId, exchangeId),
                    FOREIGN KEY(snapshotId) REFERENCES finding_diagnostic_snapshots(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(exchangeId) REFERENCES diagnostic_exchanges(id)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                )""",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_finding_snapshot_exchange_refs_exchangeId " +
                    "ON finding_snapshot_exchange_refs(exchangeId)",
            )
            // One-time compatibility import. Runtime retention never parses JSON.
            db.execSQL(
                """INSERT OR IGNORE INTO finding_snapshot_exchange_refs(snapshotId, exchangeId, ordinal, role)
                   SELECT snapshot.id, exchange.id, exchange.sessionSequence, 'LEGACY_JSON_IMPORT'
                   FROM finding_diagnostic_snapshots snapshot
                   INNER JOIN diagnostic_exchanges exchange
                     ON instr(snapshot.rawExchangeIdsJson, '"' || exchange.id || '"') > 0""",
            )
        }
    }

    internal val MIGRATION_53_54 = object : Migration(53, 54) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE dtc_definitions ADD COLUMN rawDtcIdentity TEXT NOT NULL DEFAULT ''")
        }
    }

    internal val MIGRATION_54_55 = object : Migration(54, 55) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE diagnostic_session_integrity ADD COLUMN vehicleBindingId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE diagnostic_session_integrity ADD COLUMN appVersion TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE diagnostic_session_integrity ADD COLUMN deviceKeyId TEXT")
            db.execSQL("ALTER TABLE diagnostic_session_integrity ADD COLUMN signatureAlgorithm TEXT")
            db.execSQL("ALTER TABLE diagnostic_session_integrity ADD COLUMN signatureBase64 TEXT")
            db.execSQL("ALTER TABLE diagnostic_session_integrity ADD COLUMN signedAtMs INTEGER")
            db.execSQL("ALTER TABLE diagnostic_session_integrity ADD COLUMN trustState TEXT NOT NULL DEFAULT 'UNSIGNED_LEGACY'")
        }
    }

    internal val MIGRATION_55_56 = object : Migration(55, 56) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // v1 records remain byte-for-byte verifiable. Only new writes use v2.
            db.execSQL("ALTER TABLE diagnostic_exchanges ADD COLUMN canonicalizationVersion TEXT NOT NULL DEFAULT 'diagnostic-exchange-chain-v1'")
            db.execSQL("ALTER TABLE diagnostic_exchanges ADD COLUMN rawPayloadBlobId TEXT")
            db.execSQL("ALTER TABLE diagnostic_observations ADD COLUMN findingSequence INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE diagnostic_observations ADD COLUMN canonicalizationVersion TEXT NOT NULL DEFAULT 'diagnostic-observation-chain-v1'")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_diagnostic_observations_findingId_findingSequence " +
                    "ON diagnostic_observations(findingId, findingSequence)",
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS encrypted_evidence_blobs (
                    blobId TEXT NOT NULL PRIMARY KEY,
                    cipherSuite TEXT NOT NULL,
                    keyVersion INTEGER NOT NULL,
                    nonce BLOB NOT NULL,
                    aad BLOB NOT NULL,
                    ciphertext BLOB NOT NULL,
                    ciphertextHash TEXT NOT NULL,
                    createdAtMs INTEGER NOT NULL,
                    retentionClass TEXT NOT NULL
                )""",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_encrypted_evidence_blobs_createdAtMs ON encrypted_evidence_blobs(createdAtMs)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_encrypted_evidence_blobs_retentionClass ON encrypted_evidence_blobs(retentionClass)")
            db.execSQL("ALTER TABLE diagnostic_session_integrity ADD COLUMN signerPublicKeyBase64 TEXT")
            db.execSQL("ALTER TABLE diagnostic_session_integrity ADD COLUMN certificateChainJson TEXT")
            db.execSQL("ALTER TABLE diagnostic_session_integrity ADD COLUMN keySecurityLevel TEXT")
            db.execSQL("ALTER TABLE diagnostic_findings ADD COLUMN failureType INTEGER NOT NULL DEFAULT -1")
            db.execSQL("ALTER TABLE diagnostic_findings ADD COLUMN moduleRole TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE diagnostic_findings ADD COLUMN requestAddress TEXT")
            db.execSQL("ALTER TABLE diagnostic_findings ADD COLUMN responseAddress TEXT")
            db.execSQL("ALTER TABLE diagnostic_findings ADD COLUMN ecuFamily TEXT")
            db.execSQL("ALTER TABLE diagnostic_findings ADD COLUMN hardwareVersion TEXT")
            db.execSQL("ALTER TABLE diagnostic_findings ADD COLUMN softwareVersion TEXT")
            db.execSQL("ALTER TABLE diagnostic_findings ADD COLUMN calibrationId TEXT")
            db.execSQL("ALTER TABLE diagnostic_findings ADD COLUMN vehicleBindingId TEXT NOT NULL DEFAULT ''")
            db.execSQL("DROP INDEX IF EXISTS index_diagnostic_findings_stable_identity")
            // Recover failure-type identity from legacy events before enforcing
            // the expanded stable key. This prevents two UDS sub-failures with
            // the same raw 24-bit value from remaining collapsed.
            db.execSQL(
                """UPDATE diagnostic_findings
                   SET failureType = COALESCE((
                       SELECT MIN(COALESCE(event.failureType, -1))
                       FROM dtc_events event
                       WHERE event.vehicleId = diagnostic_findings.vehicleId
                         AND (CASE WHEN event.moduleIdentity = '' THEN 'LEGACY' ELSE event.moduleIdentity END) = diagnostic_findings.ecuEndpointId
                         AND (CASE WHEN event.diagnosticNamespace = '' THEN 'SAE_OBD' ELSE event.diagnosticNamespace END) = diagnostic_findings.diagnosticNamespace
                         AND event.rawDtcIdentity = diagnostic_findings.rawDtcIdentity
                   ), -1)""",
            )
            db.execSQL(
                """INSERT OR IGNORE INTO diagnostic_findings(
                       id, vehicleId, ecuEndpointId, diagnosticNamespace, rawDtcIdentity,
                       displayCode, createdAtMs, resolutionState, resolvedAtMs, failureType,
                       moduleRole, requestAddress, responseAddress, ecuFamily, hardwareVersion,
                       softwareVersion, calibrationId, vehicleBindingId
                   )
                   SELECT finding.id || ':ft:' || COALESCE(event.failureType, -1),
                          finding.vehicleId, finding.ecuEndpointId, finding.diagnosticNamespace,
                          finding.rawDtcIdentity, finding.displayCode,
                          MIN(event.firstSeenAt), finding.resolutionState, finding.resolvedAtMs,
                          COALESCE(event.failureType, -1),
                          MAX(event.moduleName), MAX(NULLIF(event.targetAddress, '')),
                          MAX(NULLIF(event.responseAddress, '')), NULL, NULL, NULL, NULL, ''
                   FROM dtc_events event
                   INNER JOIN diagnostic_findings finding
                     ON finding.vehicleId = event.vehicleId
                    AND finding.ecuEndpointId = CASE WHEN event.moduleIdentity = '' THEN 'LEGACY' ELSE event.moduleIdentity END
                    AND finding.diagnosticNamespace = CASE WHEN event.diagnosticNamespace = '' THEN 'SAE_OBD' ELSE event.diagnosticNamespace END
                    AND finding.rawDtcIdentity = event.rawDtcIdentity
                   WHERE COALESCE(event.failureType, -1) != finding.failureType
                   GROUP BY finding.id, COALESCE(event.failureType, -1)""",
            )
            db.execSQL(
                """UPDATE diagnostic_observations
                   SET findingId = COALESCE((
                       SELECT target.id
                       FROM dtc_events event
                       INNER JOIN diagnostic_findings target
                         ON target.vehicleId = event.vehicleId
                        AND target.ecuEndpointId = CASE WHEN event.moduleIdentity = '' THEN 'LEGACY' ELSE event.moduleIdentity END
                        AND target.diagnosticNamespace = CASE WHEN event.diagnosticNamespace = '' THEN 'SAE_OBD' ELSE event.diagnosticNamespace END
                        AND target.rawDtcIdentity = event.rawDtcIdentity
                        AND target.failureType = COALESCE(event.failureType, -1)
                       WHERE diagnostic_observations.id = 'legacy-import-' || event.id
                       LIMIT 1
                   ), findingId)
                   WHERE id LIKE 'legacy-import-%'""",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX index_diagnostic_findings_stable_identity " +
                    "ON diagnostic_findings(vehicleId, ecuEndpointId, diagnosticNamespace, rawDtcIdentity, failureType)",
            )
            db.execSQL("DROP INDEX IF EXISTS index_dtc_events_finding_identity")
            db.execSQL(
                "CREATE INDEX index_dtc_events_finding_identity " +
                    "ON dtc_events(vehicleId, diagnosticNamespace, moduleIdentity, rawDtcIdentity, failureType)",
            )
            db.execSQL("ALTER TABLE derived_metrics ADD COLUMN inputQuality REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE derived_metrics ADD COLUMN formulaAuthority TEXT NOT NULL DEFAULT 'UNREVIEWED_FORMULA'")
            db.execSQL("ALTER TABLE derived_metrics ADD COLUMN derivationCompleteness REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE derived_metrics ADD COLUMN measurementUncertainty REAL")
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
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addMigrations(
            MIGRATION_1_6, MIGRATION_2_6, MIGRATION_3_6, MIGRATION_4_6, MIGRATION_5_6,
            MIGRATION_6_9, MIGRATION_7_9, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_6_10,
            MIGRATION_7_10, MIGRATION_8_10, MIGRATION_10_11, MIGRATION_11_12,
            MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
            MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
            MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26,
            MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30,
            MIGRATION_30_31,
            MIGRATION_31_32,
            MIGRATION_32_33,
            MIGRATION_33_34,
            MIGRATION_34_35,
            MIGRATION_35_36,
            MIGRATION_36_37,
            MIGRATION_37_38,
            MIGRATION_38_39,
            MIGRATION_39_40,
            MIGRATION_40_41,
            MIGRATION_41_42,
            MIGRATION_42_43,
            MIGRATION_43_44,
            MIGRATION_44_45,
            MIGRATION_45_46,
            MIGRATION_46_47,
            MIGRATION_47_48,
            MIGRATION_48_49,
            MIGRATION_49_50,
            MIGRATION_50_51,
            MIGRATION_51_52,
            MIGRATION_52_53,
            MIGRATION_53_54,
            MIGRATION_54_55,
            MIGRATION_55_56
        )
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedMechanicalKnowledge(db)
                seedCommunityCases(db)
                android.util.Log.i("ElysiumDB", "Database created fresh — DtcDatabaseLoader will populate DTCs on first use")
            }
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                try {
                    db.execSQL("PRAGMA synchronous = NORMAL;")
                } catch (e: Exception) {
                    android.util.Log.w("ElysiumDB", "Could not set synchronous: ${e.message}")
                }
                try {
                    db.execSQL("PRAGMA temp_store = MEMORY;")
                } catch (e: Exception) {
                    android.util.Log.w("ElysiumDB", "Could not set temp_store: ${e.message}")
                }
                android.util.Log.i("ElysiumDB", "Performance PRAGMAs configured (synchronous=NORMAL, temp_store=MEMORY)")
                // Make sure community cases are seeded
                try {
                    seedCommunityCases(db)
                } catch (e: Exception) {
                    android.util.Log.e("ElysiumDB", "Failed to seed community cases on open", e)
                }
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
    fun provideDiagnosticEvidenceDao(db: MeetDatabase): DiagnosticEvidenceDao = db.diagnosticEvidenceDao()

    @Provides
    fun provideDiagnosticFindingDao(db: MeetDatabase): DiagnosticFindingDao = db.diagnosticFindingDao()

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
        healthSnapshotDao: HealthSnapshotDao,
        predictionEventDao: PredictionEventDao
    ): PredictiveHealthEngine = PredictiveHealthEngine(sensorHistoryDao, healthSnapshotDao, predictionEventDao)

    @Provides
    @Singleton
    fun provideSupabaseClient(): io.github.jan.supabase.SupabaseClient {
        return com.elysium369.meet.data.supabase.SupabaseManager.client
    }

    @Provides
    @Singleton
    fun provideGeminiDiagnostic(
        aiRepository: com.elysium369.meet.ai.data.AiRepository
    ): GeminiDiagnostic {
        return GeminiDiagnostic(aiRepository = aiRepository)
    }

    @Provides
    @Singleton
    fun provideAiSecureKeyStore(@ApplicationContext context: Context): com.elysium369.meet.ai.data.AiSecureKeyStore {
        return com.elysium369.meet.ai.data.AiSecureKeyStoreImpl(context)
    }

    @Provides
    @Singleton
    fun provideAiRepository(
        registry: com.elysium369.meet.ai.data.AiProviderRegistry,
        usageTracker: com.elysium369.meet.ai.data.AiUsageTracker,
        promptStore: com.elysium369.meet.ai.data.AiPromptStore
    ): com.elysium369.meet.ai.data.AiRepository {
        return com.elysium369.meet.ai.data.AiRepositoryImpl(registry, usageTracker, promptStore)
    }

    @Provides
    @Singleton
    fun provideReportGenerator(@ApplicationContext context: Context): com.elysium369.meet.core.export.ReportGenerator {
        return com.elysium369.meet.core.export.ReportGenerator(context)
    }

    @Provides
    fun provideDvirReportDao(db: MeetDatabase): DvirReportDao = db.dvirReportDao()

    @Provides
    fun provideVehicleDnaDao(db: MeetDatabase): VehicleDnaDao = db.vehicleDnaDao()

    @Provides
    fun provideRepairCaseDao(db: MeetDatabase): RepairCaseDao = db.repairCaseDao()

    @Provides
    fun providePredictionEventDao(db: MeetDatabase): PredictionEventDao = db.predictionEventDao()

    @Provides
    @Singleton
    fun provideGaugeStyleManager(@ApplicationContext context: Context): com.elysium369.meet.ui.components.gauges.GaugeStyleManager {
        return com.elysium369.meet.ui.components.gauges.GaugeStyleManager(context)
    }

    // NEW FEATURE DAO PROVIDERS
    @Provides
    fun provideLiveSessionDao(db: MeetDatabase): LiveSessionDao = db.liveSessionDao()

    @Provides
    fun provideRepairNetworkAddonsDao(db: MeetDatabase): RepairNetworkAddonsDao = db.repairNetworkAddonsDao()

    @Provides
    fun provideMarketplaceDao(db: MeetDatabase): MarketplaceDao = db.marketplaceDao()

    @Provides
    fun provideBlackBoxDao(db: MeetDatabase): BlackBoxDao = db.blackBoxDao()

    @Provides
    fun provideVehicleTwinDao(db: MeetDatabase): VehicleTwinDao = db.vehicleTwinDao()

    @Provides
    fun provideTowTruckDao(db: MeetDatabase): TowTruckDao = db.towTruckDao()

    @Provides
    fun provideRatingDao(db: MeetDatabase): RatingDao = db.ratingDao()

    @Provides
    fun provideProviderProfileDao(db: MeetDatabase): ProviderProfileDao = db.providerProfileDao()

    // KNOWLEDGE GRAPH DAO
    @Provides
    fun provideDtcKnowledgeGraphDao(db: MeetDatabase): DtcKnowledgeGraphDao = db.dtcKnowledgeGraphDao()

    @Provides
    fun provideMechanicalKnowledgeDao(db: MeetDatabase): MechanicalKnowledgeDao = db.mechanicalKnowledgeDao()

    // GAUGE MARKETPLACE
    @Provides
    fun provideSavedGaugeDao(db: MeetDatabase): com.elysium369.meet.data.local.dao.SavedGaugeDao = db.savedGaugeDao()

    @Provides
    fun provideRideDao(db: MeetDatabase): com.elysium369.meet.data.local.dao.RideDao = db.rideDao()

    @Provides
    fun provideRideCommandOutboxDao(
        db: MeetDatabase
    ): com.elysium369.meet.ride.data.local.RideCommandOutboxDao =
        db.rideCommandOutboxDao()

    @Provides
    @Singleton
    fun provideRideCommandGateway(
        gateway: com.elysium369.meet.ride.data.remote.SupabaseRideCommandGateway
    ): com.elysium369.meet.ride.data.remote.RideCommandGateway = gateway

    @Provides
    @Singleton
    fun provideRideDriverEnrollmentGateway(
        gateway: com.elysium369.meet.ride.data.remote.SupabaseRideDriverEnrollmentGateway
    ): com.elysium369.meet.ride.data.remote.RideDriverEnrollmentGateway = gateway

    @Provides
    fun provideVanguardTelemetryDao(db: MeetDatabase): VanguardTelemetryDao = db.vanguardTelemetryDao()

    @Provides
    fun provideVanguardCommerceDao(db: MeetDatabase): VanguardCommerceDao = db.vanguardCommerceDao()

    @Provides
    fun provideCertifiedReportDao(db: MeetDatabase): CertifiedReportDao = db.certifiedReportDao()

    @Provides
    fun provideReportEvidenceDao(db: MeetDatabase): ReportEvidenceDao = db.reportEvidenceDao()

    @Provides
    fun provideRepairActionDao(db: MeetDatabase): RepairActionDao = db.repairActionDao()

    @Provides
    fun provideReportSignatureDao(db: MeetDatabase): ReportSignatureDao = db.reportSignatureDao()

    @Provides
    fun provideDiagnosticSnapshotDao(db: MeetDatabase): DiagnosticSnapshotDao = db.diagnosticSnapshotDao()

    @Provides
    @Singleton
    fun provideReportTransactionRunner(
        db: MeetDatabase,
    ): com.elysium369.meet.data.local.ReportTransactionRunner =
        com.elysium369.meet.data.local.RoomReportTransactionRunner(db)

    @Provides
    @Singleton
    fun provideVanguardOutboxDispatcher(
        dispatcher: SupabaseVanguardOutboxDispatcher
    ): VanguardOutboxDispatcher = dispatcher

    @Provides
    @Singleton
    fun provideReportVerifier(
        repo: CertifiedReportRepository,
        supabase: io.github.jan.supabase.SupabaseClient
    ): ReportVerifier {
        return ReportVerifier(
            reportRepo = repo,
            remoteProbe = { payload ->
                runCatching {
                    supabase.postgrest["certified_reports"]
                        .select { filter { eq("reportId", payload.reportId) } }
                        .decodeSingleOrNull<RemoteReportRow>()
                        ?.integrityHash
                        ?.equals(payload.integrityHash, ignoreCase = true)
                }.getOrNull()
            }
        )
    }

    @Provides
    @Singleton
    fun provideProcedureKnowledgeBase(
        @ApplicationContext context: Context
    ): com.elysium369.meet.automotive.parts.ProcedureKnowledgeBase {
        return com.elysium369.meet.automotive.parts.ProcedureKnowledgeBase(context)
    }
}

@kotlinx.serialization.Serializable
data class RemoteReportRow(
    val reportId: String,
    val integrityHash: String
)
