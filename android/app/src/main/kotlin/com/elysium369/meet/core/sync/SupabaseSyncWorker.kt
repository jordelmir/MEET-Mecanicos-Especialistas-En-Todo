package com.elysium369.meet.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elysium369.meet.data.supabase.SupabaseManager
import com.elysium369.meet.data.supabase.DiagnosticSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.gotrue.auth

class SupabaseSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch pending offline sessions from Room Database
            val db = androidx.room.Room.databaseBuilder(
                applicationContext,
                com.elysium369.meet.data.local.MeetDatabase::class.java,
                "meet_database"
            ).build()
            
            val pendingEntities = db.sessionDao().getPendingSync()

            if (pendingEntities.isEmpty()) {
                return@withContext Result.success()
            }

            // Convertir a domain model para Supabase
            val pendingSessions = pendingEntities.map { 
                DiagnosticSession(
                    id = it.id,
                    user_id = SupabaseManager.client.auth.currentUserOrNull()?.id ?: "unauthenticated",
                    vehicle_vin = it.vehicleId,
                    adapter_type = it.adapterFingerprint,
                    dtcs_found = it.dtcSnapshot,
                    live_data_snapshot = it.liveDataSummary
                )
            }

            // 2. Upload to Supabase Web App Backend
            val supabase = SupabaseManager.client
            supabase.postgrest["diagnostic_sessions"].insert(pendingSessions)

            // 3. Mark as synced in local DB
            db.sessionDao().markAsSynced(pendingEntities.map { it.id })

            Result.success()
        } catch (e: Exception) {
            // Exponential backoff will be handled by WorkManager if we return retry
            Result.retry()
        }
    }
}
