package com.elysium369.meet.core.maintenance

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elysium369.meet.core.health.PredictiveHealthEngine
import com.elysium369.meet.data.local.MeetDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DatabaseMaintenanceWorker — Automated background database hygiene.
 *
 * Scheduled as a daily PeriodicWorkRequest, this Worker:
 *   1. Purges telemetry older than 90 days via PredictiveHealthEngine.performMaintenance()
 *   2. Runs SQLite VACUUM to reclaim disk space from deleted pages
 *
 * Constraints: Only runs when device is NOT low on battery.
 * The Worker is idempotent and safe to re-run — duplicate executions are harmless.
 *
 * Why VACUUM matters:
 *   SQLite never shrinks its file on DELETE; it marks freed pages for reuse.
 *   After a large maintenance purge (potentially 100K+ rows), the DB file stays
 *   at its peak size forever unless VACUUM is run explicitly.
 *   VACUUM rewrites the entire DB into a compact file, returning storage to the OS.
 *
 * Performance notes:
 *   - VACUUM is O(N) on total DB size (reads + rewrites every page)
 *   - On a typical 50-100 MB meet_database, this takes 2-8 seconds
 *   - We run on Dispatchers.IO to avoid blocking any UI thread
 *   - The daily schedule ensures this never becomes a bottleneck
 */
@HiltWorker
class DatabaseMaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val predictiveHealthEngine: PredictiveHealthEngine,
    private val database: MeetDatabase
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DBMaintenance"
        const val WORK_NAME = "database_maintenance_periodic"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🔧 Starting database maintenance cycle...")
            val startTime = System.currentTimeMillis()

            // ── Step 1: Purge expired telemetry (>90 days) ──
            Log.i(TAG, "  Step 1/2: Purging expired sensor history & health snapshots...")
            predictiveHealthEngine.performMaintenance()
            val afterPurge = System.currentTimeMillis()
            Log.i(TAG, "  Step 1/2 complete in ${afterPurge - startTime}ms")

            // ── Step 2: VACUUM to reclaim disk space ──
            Log.i(TAG, "  Step 2/2: Running VACUUM to reclaim disk space...")
            database.openHelper.writableDatabase.execSQL("VACUUM")
            val afterVacuum = System.currentTimeMillis()
            Log.i(TAG, "  Step 2/2 complete in ${afterVacuum - afterPurge}ms")

            val totalMs = afterVacuum - startTime
            Log.i(TAG, "✅ Database maintenance complete in ${totalMs}ms")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Database maintenance failed", e)
            // Retry on next scheduled run; don't spam retries
            Result.failure()
        }
    }
}
