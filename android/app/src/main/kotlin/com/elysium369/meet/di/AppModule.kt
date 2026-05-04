package com.elysium369.meet.di

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeetDatabase {
        return Room.databaseBuilder(
            context,
            MeetDatabase::class.java,
            "meet_database"
        )
        .createFromAsset("databases/meet_dtc.db")
        .fallbackToDestructiveMigration()
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
