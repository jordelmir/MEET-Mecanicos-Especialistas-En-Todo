package com.elysium369.meet.platform.marketos.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elysium369.meet.data.local.dao.MarketOsDao
import com.elysium369.meet.identity.ActivePrincipalKernel
import com.elysium369.meet.platform.marketos.data.MarketCommandRemoteResult
import com.elysium369.meet.platform.marketos.data.MarketOsRemoteGateway
import com.elysium369.meet.platform.marketos.data.MarketOsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class MarketOsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val dao: MarketOsDao,
    private val principalKernel: ActivePrincipalKernel,
    private val gateway: MarketOsRemoteGateway,
    private val repository: MarketOsRepository,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val principal = principalKernel.current()
        if (!principal.canSyncToCloud) return Result.success()
        val now = System.currentTimeMillis()
        dao.recoverStaleCommands(principal.id, now - STALE_LEASE_MS, now)
        val ready = dao.readyCommands(principal.id, now, BATCH_SIZE)
        var retryNeeded = false
        ready.forEach { candidate ->
            if (dao.acquire(candidate.ownerPrincipalId, candidate.idempotencyKey, System.currentTimeMillis()) != 1) {
                return@forEach
            }
            val leased = dao.command(candidate.ownerPrincipalId, candidate.idempotencyKey) ?: return@forEach
            if (candidate.ownerPrincipalId != principalKernel.current().id) {
                retryNeeded = true
                dao.markFailure(
                    candidate.ownerPrincipalId,
                    candidate.idempotencyKey,
                    "RETRYABLE",
                    now + retryDelay(leased.attemptCount, candidate.idempotencyKey),
                    "AUTH_SESSION_CHANGED",
                    System.currentTimeMillis(),
                )
                return@forEach
            }
            when (val result = gateway.execute(leased)) {
                is MarketCommandRemoteResult.Accepted -> {
                    dao.markDelivered(principal.id, leased.idempotencyKey, System.currentTimeMillis())
                }
                is MarketCommandRemoteResult.Rejected -> {
                    val terminal = !result.retryable || leased.attemptCount >= MAX_ATTEMPTS
                    dao.markFailure(
                        principal.id,
                        leased.idempotencyKey,
                        if (terminal) "DEAD_LETTER" else "RETRYABLE",
                        if (terminal) Long.MAX_VALUE else System.currentTimeMillis() + retryDelay(leased.attemptCount, leased.idempotencyKey),
                        result.code,
                        System.currentTimeMillis(),
                    )
                    retryNeeded = retryNeeded || !terminal
                }
                is MarketCommandRemoteResult.TransportFailure -> {
                    val terminal = leased.attemptCount >= MAX_ATTEMPTS
                    dao.markFailure(
                        principal.id,
                        leased.idempotencyKey,
                        if (terminal) "DEAD_LETTER" else "RETRYABLE",
                        if (terminal) Long.MAX_VALUE else System.currentTimeMillis() + retryDelay(leased.attemptCount, leased.idempotencyKey),
                        result.code,
                        System.currentTimeMillis(),
                    )
                    retryNeeded = retryNeeded || !terminal
                }
            }
        }
        when (val refreshed = repository.refresh()) {
            is com.elysium369.meet.platform.marketos.data.MarketRefreshResult.Failed -> {
                Log.w(TAG, "Projection refresh failed: ${refreshed.reason}")
                retryNeeded = true
            }
            else -> Unit
        }
        return if (retryNeeded) Result.retry() else Result.success()
    }

    companion object {
        const val IMMEDIATE_WORK_NAME = "market_os_command_outbox_immediate"
        const val PERIODIC_WORK_NAME = "market_os_command_outbox_periodic"
        private const val TAG = "MarketOsSync"
        private const val BATCH_SIZE = 20
        private const val MAX_ATTEMPTS = 8
        private const val STALE_LEASE_MS = 15 * 60 * 1000L

        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<MarketOsSyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        internal fun retryDelay(attempt: Int, key: String): Long {
            val base = (15_000L shl (attempt - 1).coerceIn(0, 10)).coerceAtMost(15 * 60 * 1000L)
            val jitterWindow = (base / 4).coerceAtMost(30_000L)
            val jitter = if (jitterWindow == 0L) 0L else (key.hashCode().toLong() and 0x7fffffffL) % jitterWindow
            return (base + jitter).coerceAtMost(15 * 60 * 1000L)
        }
    }
}
