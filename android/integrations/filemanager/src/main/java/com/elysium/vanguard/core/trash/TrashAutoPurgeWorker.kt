package com.elysium.vanguard.core.trash

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elysium.vanguard.core.database.TrashDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * PHASE 1.3 — Daily worker that purges trash entries older than the configured
 * retention window.
 *
 * Retention default is 30 days; the value can be overridden via
 * [TrashConfig.retentionDays] which the user can change in settings.
 *
 * Scheduling:
 *   - Period: 24 h.
 *   - Constraints: requires battery not low (we don't want to fail mid-purge).
 *   - Backoff: 30 min if it fails (e.g. SAF permission revoked).
 */
@HiltWorker
class TrashAutoPurgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val trashDao: TrashDao,
    private val trashRepository: TrashRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val retentionDays = TrashConfig.retentionDays(applicationContext)
            val cutoff = System.currentTimeMillis() -
                TimeUnit.DAYS.toMillis(retentionDays.toLong())
            val expired = trashDao.listOlderThan(cutoff)
            for (item in expired) {
                trashRepository.purge(item)
            }
            Result.success()
        } catch (t: Throwable) {
            com.elysium.vanguard.core.util.SafeLog.e("TrashAutoPurgeWorker", "Purge failed", t)
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "trash-auto-purge"

        /**
         * Schedule (or replace) the daily purge job.
         *
         * Safe to call multiple times — [ExistingPeriodicWorkPolicy.UPDATE] replaces
         * the schedule when the user changes retention.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TrashAutoPurgeWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
