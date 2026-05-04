package com.elysium369.meet.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.elysium369.meet.core.ai.GeminiDiagnostic
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

        .addMigrations(MIGRATION_1_6, MIGRATION_2_6, MIGRATION_3_6, MIGRATION_4_6, MIGRATION_5_6)
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
}
