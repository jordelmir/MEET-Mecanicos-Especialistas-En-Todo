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
     * Migration from v5 → v6: No schema changes — this is a version bump to escape
     * the poisoned createFromAsset + fallbackToDestructiveMigration combination
     * that was wiping vehicle data on every version change.
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // No schema changes — this migration exists solely to preserve user data
            // by replacing fallbackToDestructiveMigration with an explicit no-op migration.
            android.util.Log.i("MeetDB", "Migration 5→6: Preserving all user data (no schema changes)")
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

        .addMigrations(MIGRATION_5_6)
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
