package com.elysium369.meet.safety.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SafetyCommandScheduler {

    private const val UNIQUE_WORK = "safety-command-sync"

    fun enqueueNow(context: Context) = schedule(context, 0)

    fun schedule(context: Context, delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<SafetyCommandSyncWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
    }
}
