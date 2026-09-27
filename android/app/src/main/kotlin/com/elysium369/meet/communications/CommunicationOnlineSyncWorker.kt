package com.elysium369.meet.communications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Durable retries never migrate one account's messages into another account. */
@HiltWorker
class CommunicationOnlineSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val repository: ElysiumCommunicationRepository,
    private val gateway: CommunicationRemoteGateway,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (gateway.authenticatedPrincipalId() != inputData.getString("owner")) return Result.success()
        return if (repository.synchronizeOnline()) Result.success() else Result.retry()
    }
}
