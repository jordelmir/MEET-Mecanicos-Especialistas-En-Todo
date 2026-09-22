package com.elysium369.meet.safety.di

import com.elysium369.meet.data.local.MeetDatabase
import com.elysium369.meet.safety.data.local.SafetyCommandOutboxDao
import com.elysium369.meet.safety.data.local.SafetyPayloadDao
import com.elysium369.meet.safety.data.local.SafetyPublicDao
import com.elysium369.meet.safety.data.local.SafetyReportDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SafetyDatabaseModule {

    @Provides
    @Singleton
    fun provideSafetyReportDao(database: MeetDatabase): SafetyReportDao =
        database.safetyReportDao()

    @Provides
    @Singleton
    fun provideSafetyOutboxDao(database: MeetDatabase): SafetyCommandOutboxDao =
        database.safetyCommandOutboxDao()

    @Provides
    @Singleton
    fun provideSafetyPayloadDao(database: MeetDatabase): SafetyPayloadDao =
        database.safetyPayloadDao()

    @Provides
    @Singleton
    fun provideSafetyPublicDao(database: MeetDatabase): SafetyPublicDao =
        database.safetyPublicDao()
    @Provides
    fun provideSafetyEvidenceDao(database: MeetDatabase): com.elysium369.meet.safety.evidence.SafetyEvidenceDao = database.safetyEvidenceDao()
}
