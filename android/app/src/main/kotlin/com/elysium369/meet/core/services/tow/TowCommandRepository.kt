package com.elysium369.meet.core.services.tow

import com.elysium369.meet.core.geo.GeoPoint
import com.elysium369.meet.core.services.kernel.Money
import com.elysium369.meet.core.services.kernel.ServiceRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface TowCommandResult {
    data class Success(val job: TowJob) : TowCommandResult
    data class InvalidTransition(val currentState: TowState, val action: String) : TowCommandResult
    data class Unauthorized(val actorRole: ServiceRole, val requiredAction: String) : TowCommandResult
    data class JobNotFound(val jobId: UUID) : TowCommandResult
}

@Singleton
class TowCommandRepository @Inject constructor() {
    private val _activeTowJob = MutableStateFlow<TowJob?>(null)
    val activeTowJob: StateFlow<TowJob?> = _activeTowJob.asStateFlow()

    private val jobHistory = mutableMapOf<UUID, TowJob>()

    fun getJobById(jobId: UUID): TowJob? = _activeTowJob.value?.takeIf { it.jobId == jobId } ?: jobHistory[jobId]

    fun requestTow(
        customerId: UUID,
        customerName: String,
        customerPhone: String,
        vehicleVin: String?,
        vehicleSummary: String,
        pickupLocation: GeoPoint,
        pickupAddress: String,
        destinationLocation: GeoPoint?,
        destinationAddress: String?,
        requiredCapabilities: Set<TowCapabilities>,
        estimatedPrice: Money?,
    ): TowJob {
        val job = TowJob(
            jobId = UUID.randomUUID(),
            customerId = customerId,
            customerName = customerName,
            customerPhone = customerPhone,
            vehicleVin = vehicleVin,
            vehicleSummary = vehicleSummary,
            pickupLocation = pickupLocation,
            pickupAddress = pickupAddress,
            destinationLocation = destinationLocation,
            destinationAddress = destinationAddress,
            requiredCapabilities = requiredCapabilities,
            state = TowState.REQUESTED,
            estimatedPrice = estimatedPrice,
            serverVersion = 1L,
            createdAtEpochMs = System.currentTimeMillis(),
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        _activeTowJob.value = job
        jobHistory[job.jobId] = job
        return job
    }

    fun executeAction(
        jobId: UUID,
        action: TowAction,
        actorRole: ServiceRole,
        actorId: UUID = UUID.randomUUID(),
    ): TowCommandResult {
        val currentJob = getJobById(jobId) ?: return TowCommandResult.JobNotFound(jobId)
        val nextState = TowStateEngine.getNextState(currentJob.state, action, actorRole)
            ?: return TowCommandResult.InvalidTransition(currentJob.state, action.javaClass.simpleName)

        val updatedCustody = currentJob.custodyRecords.toMutableList()
        when (action) {
            is TowAction.ConfirmLoaded -> {
                updatedCustody.add(
                    TowCustodyRecord(
                        checkpoint = TowCustodyCheckpoint.LOADED_SECURED,
                        evidenceHash = action.secureEvidenceHash,
                        recordedAtEpochMs = System.currentTimeMillis(),
                        recordedByActorId = actorId,
                        notes = "Vehículo cargado y anclado conforme a norma técnica."
                    )
                )
            }
            is TowAction.ConfirmDelivered -> {
                updatedCustody.add(
                    TowCustodyRecord(
                        checkpoint = TowCustodyCheckpoint.DELIVERED,
                        evidenceHash = action.deliveryEvidenceHash,
                        recordedAtEpochMs = System.currentTimeMillis(),
                        recordedByActorId = actorId,
                        notes = "Vehículo entregado en destino acordado."
                    )
                )
            }
            else -> Unit
        }

        val updatedJob = currentJob.copy(
            state = nextState,
            custodyRecords = updatedCustody,
            serverVersion = currentJob.serverVersion + 1,
            updatedAtEpochMs = System.currentTimeMillis(),
        )

        _activeTowJob.value = if (nextState in setOf(TowState.COMPLETED, TowState.CANCELLED)) null else updatedJob
        jobHistory[jobId] = updatedJob
        return TowCommandResult.Success(updatedJob)
    }

    fun clearActiveJob() {
        _activeTowJob.value = null
    }
}
