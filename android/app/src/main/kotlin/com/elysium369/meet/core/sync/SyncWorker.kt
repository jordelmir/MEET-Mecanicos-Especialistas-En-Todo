package com.elysium369.meet.core.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elysium369.meet.data.local.dao.DiagnosticSessionDao
import com.elysium369.meet.data.local.dao.TripDao
import com.elysium369.meet.data.supabase.DiagnosticSession
import com.elysium369.meet.data.supabase.SupabaseManager
import com.elysium369.meet.data.supabase.Trip
import com.elysium369.meet.data.supabase.toDomain
import com.elysium369.meet.identity.ActivePrincipal
import com.elysium369.meet.identity.ActivePrincipalKernel
import com.elysium369.meet.identity.OfflineOwnership
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.postgrest.postgrest

enum class SyncItemResult {
    SUCCESS,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
    CONFLICT,
    AUTH_REQUIRED,
}

data class SyncBatchResult(
    val successCount: Int = 0,
    val retryableCount: Int = 0,
    val permanentCount: Int = 0,
) {
    val hasRetryableFailure: Boolean get() = retryableCount > 0
}

/**
 * SyncWorker — Professional background synchronization engine.
 * Ensures local data is safely uploaded to Supabase when network is available.
 * Never reports false success when individual items experience retryable failures.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionDao: DiagnosticSessionDao,
    private val tripDao: TripDao,
    private val dtcDao: com.elysium369.meet.data.local.dao.DtcDao,
    private val activePrincipalKernel: ActivePrincipalKernel,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("SyncWorker", "Starting background sync...")

        val activePrincipal = activePrincipalKernel.current()
        if (!activePrincipal.canSyncToCloud) return Result.success()

        return try {
            val sessions = syncSessions(activePrincipal)
            val trips = syncTrips(activePrincipal)
            val dtcs = syncDtcs(activePrincipal)

            if (sessions.hasRetryableFailure || trips.hasRetryableFailure || dtcs.hasRetryableFailure) {
                Log.w("SyncWorker", "Partial batch failure (sessions=${sessions.retryableCount}, trips=${trips.retryableCount}, dtcs=${dtcs.retryableCount}); requesting WorkManager retry.")
                Result.retry()
            } else {
                Log.i("SyncWorker", "Sync complete: sessions=${sessions.successCount}, trips=${trips.successCount}, dtcs=${dtcs.successCount}")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync worker caught fatal exception", e)
            Result.retry()
        }
    }

    suspend fun syncSessions(activePrincipal: ActivePrincipal): SyncBatchResult {
        val pendingSessions = sessionDao.getPendingSync(activePrincipal.id)
        if (pendingSessions.isEmpty()) return SyncBatchResult()

        Log.i("SyncWorker", "Found ${pendingSessions.size} pending sessions to sync")

        val syncedIds = mutableListOf<String>()
        var retryableFailures = 0
        var permanentFailures = 0

        pendingSessions.forEach { entity ->
            if (!OfflineOwnership.canSync(entity.ownerPrincipalId, activePrincipal)) return@forEach
            val domainSession = DiagnosticSession(
                id = entity.id,
                user_id = entity.ownerPrincipalId,
                vehicle_vin = entity.observedVin.takeUnless { it == "LEGACY_NOT_CAPTURED" },
                adapter_type = entity.adapterFingerprint,
                dtcs_found = entity.dtcSnapshot,
                live_data_snapshot = entity.liveDataSummary,
            )

            try {
                SupabaseManager.client.postgrest["scan_sessions"].upsert(domainSession)
                syncedIds.add(entity.id)
                Log.d("SyncWorker", "Synced session ${entity.id}")
            } catch (e: Exception) {
                Log.e("SyncWorker", "Failed to sync session ${entity.id}, marking retryable", e)
                retryableFailures++
            }
        }

        if (syncedIds.isNotEmpty()) {
            sessionDao.markAsSynced(syncedIds, activePrincipal.id)
        }

        return SyncBatchResult(
            successCount = syncedIds.size,
            retryableCount = retryableFailures,
            permanentCount = permanentFailures,
        )
    }

    suspend fun syncTrips(activePrincipal: ActivePrincipal): SyncBatchResult {
        val pendingTrips = tripDao.getPendingSync(activePrincipal.id)
        if (pendingTrips.isEmpty()) return SyncBatchResult()

        Log.i("SyncWorker", "Found ${pendingTrips.size} pending trips to sync")

        val syncedIds = mutableListOf<String>()
        var retryableFailures = 0
        var permanentFailures = 0

        pendingTrips.forEach { entity ->
            if (!OfflineOwnership.canSync(entity.ownerPrincipalId, activePrincipal)) return@forEach
            val domainTrip = Trip(
                id = entity.id,
                user_id = entity.ownerPrincipalId,
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
                gps_track_json = entity.gpsTrackJson,
            )

            try {
                SupabaseManager.client.postgrest["trips"].upsert(domainTrip)
                syncedIds.add(entity.id)
                Log.d("SyncWorker", "Synced trip ${entity.id}")
            } catch (e: Exception) {
                Log.e("SyncWorker", "Failed to sync trip ${entity.id}, marking retryable", e)
                retryableFailures++
            }
        }

        if (syncedIds.isNotEmpty()) {
            tripDao.markAsSynced(syncedIds, activePrincipal.id)
        }

        return SyncBatchResult(
            successCount = syncedIds.size,
            retryableCount = retryableFailures,
            permanentCount = permanentFailures,
        )
    }

    suspend fun syncDtcs(activePrincipal: ActivePrincipal): SyncBatchResult {
        val pendingDtcs = dtcDao.getPendingSyncDtcs(activePrincipal.id)
        if (pendingDtcs.isEmpty()) return SyncBatchResult()

        Log.i("SyncWorker", "Found ${pendingDtcs.size} pending DTCs to sync")

        val syncedIds = mutableListOf<String>()
        var retryableFailures = 0
        var permanentFailures = 0
        val supabase = SupabaseManager.client

        pendingDtcs.forEach { entity ->
            if (!OfflineOwnership.canSync(entity.ownerPrincipalId, activePrincipal)) return@forEach
            val domainDtc = entity.toDomain()
            try {
                supabase.postgrest["dtc_events"].upsert(domainDtc)
                syncedIds.add(entity.id)
                Log.d("SyncWorker", "Synced DTC ${entity.code} for vehicle ${entity.vehicleId}")
            } catch (e: Exception) {
                Log.e("SyncWorker", "Failed to sync DTC ${entity.id}, marking retryable", e)
                retryableFailures++
            }
        }

        if (syncedIds.isNotEmpty()) {
            dtcDao.markDtcsAsSynced(syncedIds, activePrincipal.id)
        }

        return SyncBatchResult(
            successCount = syncedIds.size,
            retryableCount = retryableFailures,
            permanentCount = permanentFailures,
        )
    }
}

