package com.elysium369.meet.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elysium369.meet.data.local.dao.DiagnosticSessionDao
import com.elysium369.meet.data.local.dao.TripDao
import com.elysium369.meet.data.supabase.DiagnosticSession
import com.elysium369.meet.data.supabase.SupabaseManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import android.util.Log

/**
 * SyncWorker — Professional background synchronization engine.
 * Ensures local data is safely uploaded to Supabase when network is available.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionDao: DiagnosticSessionDao,
    private val tripDao: TripDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("SyncWorker", "Starting background sync...")
        
        val userId = SupabaseManager.client.auth.currentUserOrNull()?.id ?: return Result.failure()

        return try {
            syncSessions(userId)
            syncTrips(userId)
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed", e)
            Result.retry()
        }
    }

    private suspend fun syncSessions(userId: String) {
        val pendingSessions = sessionDao.getPendingSync()
        if (pendingSessions.isEmpty()) return

        Log.i("SyncWorker", "Found ${pendingSessions.size} pending sessions to sync")

        val syncedIds = mutableListOf<String>()

        pendingSessions.forEach { entity ->
            val domainSession = DiagnosticSession(
                id = entity.id,
                user_id = userId,
                vehicle_vin = entity.vehicleId,
                adapter_type = entity.adapterFingerprint,
                dtcs_found = entity.dtcSnapshot,
                live_data_snapshot = entity.liveDataSummary
            )

            try {
                SupabaseManager.client.postgrest["scan_sessions"].upsert(domainSession)
                syncedIds.add(entity.id)
                Log.d("SyncWorker", "Synced session ${entity.id}")
            } catch (e: Exception) {
                Log.e("SyncWorker", "Failed to sync session ${entity.id}, will retry later", e)
            }
        }

        if (syncedIds.isNotEmpty()) {
            sessionDao.markAsSynced(syncedIds)
        }
    }

    private suspend fun syncTrips(userId: String) {
        val pendingTrips = tripDao.getPendingSync()
        if (pendingTrips.isEmpty()) return

        Log.i("SyncWorker", "Found ${pendingTrips.size} pending trips to sync")

        val syncedIds = mutableListOf<String>()

        pendingTrips.forEach { entity ->
            val domainTrip = com.elysium369.meet.data.supabase.Trip(
                id = entity.id,
                user_id = userId,
                vehicle_id = entity.vehicleId,
                session_id = entity.sessionId,
                started_at = entity.startedAt,
                ended_at = entity.endedAt,
                distance_km = entity.distanceKm,
                duration_seconds = entity.durationSeconds,
                avg_speed_kmh = entity.avgSpeedKmh,
                max_speed_kmh = entity.maxSpeedKmh,
                max_rpm = entity.maxRpm,
                avg_rpm = entity.avgRpm,
                max_temp_c = entity.maxTempC,
                fuel_efficiency = entity.fuelEfficiency,
                eco_score = entity.ecoScore,
                gps_track_json = entity.gpsTrackJson
            )

            try {
                SupabaseManager.client.postgrest["trips"].upsert(domainTrip)
                syncedIds.add(entity.id)
                Log.d("SyncWorker", "Synced trip ${entity.id}")
            } catch (e: Exception) {
                Log.e("SyncWorker", "Failed to sync trip ${entity.id}, will retry later", e)
            }
        }

        if (syncedIds.isNotEmpty()) {
            tripDao.markAsSynced(syncedIds)
        }
    }
}
