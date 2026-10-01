package com.elysium.vanguard.recordshield.di

import android.content.Context
import androidx.room.Room
import com.elysium.vanguard.recordshield.BuildConfig
import com.elysium.vanguard.recordshield.data.cloud.CloudStorageManager
import com.elysium.vanguard.recordshield.data.cloud.GoogleDriveClient
import com.elysium.vanguard.recordshield.data.cloud.GoogleDriveStorageProvider
import com.elysium.vanguard.recordshield.data.cloud.SupabaseStorageProvider
import com.elysium.vanguard.recordshield.data.local.ChunkDao
import com.elysium.vanguard.recordshield.data.local.RecordShieldDatabase
import com.elysium.vanguard.recordshield.data.local.RecordingDao
import com.elysium.vanguard.recordshield.data.local.SecureStorage
import com.elysium.vanguard.recordshield.data.remote.EvidenceApiClient
import com.elysium.vanguard.recordshield.data.repository.ChunkRepositoryImpl
import com.elysium.vanguard.recordshield.data.repository.EvidenceUploadRepositoryImpl
import com.elysium.vanguard.recordshield.data.repository.RecordingRepositoryImpl
import com.elysium.vanguard.recordshield.domain.repository.ChunkRepository
import com.elysium.vanguard.recordshield.domain.repository.EvidenceUploadRepository
import com.elysium.vanguard.recordshield.domain.repository.RecordingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ========================================================================
    // DATABASE
    // ========================================================================

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RecordShieldDatabase {
        return Room.databaseBuilder(
            context,
            RecordShieldDatabase::class.java,
            "record_shield_${com.elysium.vanguard.recordshield.RecordShieldIdentity.storageScope()}.db"
        )
        .build()
    }

    @Provides
    @Singleton
    fun provideRecordingDao(db: RecordShieldDatabase): RecordingDao = db.recordingDao()

    @Provides
    @Singleton
    fun provideChunkDao(db: RecordShieldDatabase): ChunkDao = db.chunkDao()

    // ========================================================================
    // NETWORK
    // ========================================================================

    @Provides
    fun provideEvidenceApiClient(): EvidenceApiClient {
        return EvidenceApiClient(baseUrl = BuildConfig.VERCEL_API_URL)
    }

    // ========================================================================
    // CLOUD STORAGE
    // ========================================================================

    @Provides
    fun provideGoogleDriveClient(
        @ApplicationContext context: Context,
        secureStorage: SecureStorage
    ): GoogleDriveClient {
        return GoogleDriveClient(context, secureStorage)
    }

    @Provides
    fun provideGoogleDriveStorageProvider(
        driveClient: GoogleDriveClient,
        secureStorage: SecureStorage
    ): GoogleDriveStorageProvider {
        return GoogleDriveStorageProvider(driveClient, secureStorage)
    }

    @Provides
    fun provideSupabaseStorageProvider(
        apiClient: EvidenceApiClient,
        secureStorage: SecureStorage
    ): SupabaseStorageProvider {
        return SupabaseStorageProvider(apiClient, secureStorage)
    }

    @Provides
    fun provideCloudStorageManager(
        googleDrive: GoogleDriveStorageProvider,
        supabase: SupabaseStorageProvider,
        secureStorage: SecureStorage
    ): CloudStorageManager {
        return CloudStorageManager(googleDrive, supabase, secureStorage)
    }

    // ========================================================================
    // REPOSITORIES
    // ========================================================================

    @Provides
    fun provideRecordingRepository(recordingDao: RecordingDao): RecordingRepository {
        return RecordingRepositoryImpl(recordingDao)
    }

    @Provides
    fun provideChunkRepository(
        chunkDao: ChunkDao,
        recordingDao: RecordingDao
    ): ChunkRepository {
        return ChunkRepositoryImpl(chunkDao, recordingDao)
    }

    @Provides
    fun provideEvidenceUploadRepository(apiClient: EvidenceApiClient): EvidenceUploadRepository {
        return EvidenceUploadRepositoryImpl(apiClient)
    }
}
