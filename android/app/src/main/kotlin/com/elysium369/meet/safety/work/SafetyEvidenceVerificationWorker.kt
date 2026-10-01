package com.elysium369.meet.safety.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.safety.evidence.SafetyEvidenceDao
import com.elysium369.meet.safety.evidence.SafetyEvidenceVerificationGateway
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.CancellationException

/** Resumes received evidence after process death; transport failure never invents verification. */
@HiltWorker
class SafetyEvidenceVerificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val dao: SafetyEvidenceDao,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val owner = SupabaseModule.client.auth.currentUserOrNull()?.id ?: return Result.retry()
        val pending = dao.verificationPending(owner)
        var retry = pending.size == 20
        for (evidence in pending) {
            if (SupabaseModule.client.auth.currentUserOrNull()?.id != owner) return Result.retry()
            try {
                val (state, receipt) = SafetyEvidenceVerificationGateway.verify(evidence.evidenceId, owner)
                dao.update(evidence.evidenceId, owner, state, evidence.attemptCount,
                    if (state == "QUARANTINED") "SERVER_BYTE_VERIFICATION_FAILED" else null, receipt)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                dao.update(evidence.evidenceId, owner, "RECEIVED", evidence.attemptCount,
                    "SERVER_VERIFICATION_PENDING")
                retry = true
            }
        }
        return if (retry) Result.retry() else Result.success()
    }
}
