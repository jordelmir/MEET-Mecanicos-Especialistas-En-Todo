package com.elysium369.meet.core.services.tow

import com.elysium369.meet.core.geo.GeoPoint
import com.elysium369.meet.core.services.kernel.Money
import com.elysium369.meet.core.services.kernel.ServiceRole
import com.elysium369.meet.data.local.dao.TowTruckDao
import com.elysium369.meet.data.local.entities.TowTruckRequestEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed interface TowCommandResult {
    data class Success(val job: TowJob) : TowCommandResult
    data class InvalidTransition(val currentState: TowState, val action: String) : TowCommandResult
    data class Unauthorized(val actorRole: ServiceRole, val requiredAction: String) : TowCommandResult
    data class JobNotFound(val jobId: UUID) : TowCommandResult
    data class ConcurrencyConflict(val expectedVersion: Long, val actualVersion: Long) : TowCommandResult
    data class InvalidEvidence(val reason: String) : TowCommandResult
}

@Singleton
class TowCommandRepository(
    private val towTruckDao: TowTruckDao?,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(towTruckDao: TowTruckDao) : this(
        towTruckDao = towTruckDao,
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    )

    constructor() : this(
        towTruckDao = null,
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    )
    private val _activeTowJob = MutableStateFlow<TowJob?>(null)
    val activeTowJob: StateFlow<TowJob?> = _activeTowJob.asStateFlow()

    private val jobHistory = ConcurrentHashMap<UUID, TowJob>()

    init {
        // Hydrate from durable Room persistence if available
        towTruckDao?.let { dao ->
            scope.launch {
                dao.getRequestsFlow().collect { entities ->
                    entities.forEach { entity ->
                        val job = entity.toTowJob()
                        jobHistory[job.jobId] = job
                        if (job.state.isActive && _activeTowJob.value == null) {
                            _activeTowJob.value = job
                        }
                    }
                }
            }
        }
    }

    fun getJobById(jobId: UUID): TowJob? = _activeTowJob.value?.takeIf { it.jobId == jobId } ?: jobHistory[jobId]

    fun observeAllJobs(): Flow<List<TowJob>> {
        return towTruckDao?.getRequestsFlow()?.map { list ->
            list.map { entity ->
                jobHistory[runCatching { UUID.fromString(entity.requestId) }.getOrNull()] ?: entity.toTowJob()
            }
        } ?: flowOf(jobHistory.values.sortedByDescending { it.createdAtEpochMs })
    }

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

        towTruckDao?.let { dao ->
            scope.launch {
                dao.insertRequest(job.toEntity())
            }
        }

        return job
    }

    fun executeAction(
        jobId: UUID,
        action: TowAction,
        actorRole: ServiceRole,
        actorId: UUID = UUID.randomUUID(),
        expectedServerVersion: Long? = null,
    ): TowCommandResult {
        val currentJob = getJobById(jobId) ?: return TowCommandResult.JobNotFound(jobId)

        // Optimistic concurrency control (CAS)
        if (expectedServerVersion != null && currentJob.serverVersion != expectedServerVersion) {
            return TowCommandResult.ConcurrencyConflict(
                expectedVersion = expectedServerVersion,
                actualVersion = currentJob.serverVersion
            )
        }

        val nextState = TowStateEngine.getNextState(currentJob.state, action, actorRole)
            ?: return TowCommandResult.InvalidTransition(currentJob.state, action.javaClass.simpleName)

        val updatedCustody = currentJob.custodyRecords.toMutableList()
        var updatedUnit = currentJob.assignedUnit
        var updatedOperatorName = currentJob.assignedOperatorName
        var updatedOperatorPhone = currentJob.assignedOperatorPhone

        when (action) {
            is TowAction.AssignOperator -> {
                updatedUnit = TowUnit(
                    towUnitId = action.towUnitId,
                    operatorId = action.operatorId,
                    brandModel = "Unidad de Auxilio Vial",
                    licensePlate = "GRUA-${action.towUnitId.take(4).uppercase()}",
                    capabilities = currentJob.requiredCapabilities,
                    isVerified = true,
                    isAvailable = true
                )
            }
            is TowAction.ConfirmLoaded -> {
                if (action.secureEvidenceHash.isBlank()) {
                    return TowCommandResult.InvalidEvidence("Hash de evidencia de carga requerido y no puede ser vacío")
                }
                updatedCustody.add(
                    TowCustodyRecord(
                        checkpoint = TowCustodyCheckpoint.LOADED_SECURED,
                        evidenceHash = action.secureEvidenceHash,
                        recordedAtEpochMs = System.currentTimeMillis(),
                        recordedByActorId = actorId,
                        canonicalEvidenceId = null,
                        notes = "Vehículo cargado y anclado conforme a norma técnica."
                    )
                )
            }
            is TowAction.ConfirmDelivered -> {
                if (action.deliveryEvidenceHash.isBlank()) {
                    return TowCommandResult.InvalidEvidence("Hash de evidencia de entrega requerido y no puede ser vacío")
                }
                updatedCustody.add(
                    TowCustodyRecord(
                        checkpoint = TowCustodyCheckpoint.DELIVERED,
                        evidenceHash = action.deliveryEvidenceHash,
                        recordedAtEpochMs = System.currentTimeMillis(),
                        recordedByActorId = actorId,
                        canonicalEvidenceId = null,
                        notes = "Vehículo entregado en destino acordado."
                    )
                )
            }
            else -> Unit
        }

        val updatedJob = currentJob.copy(
            state = nextState,
            assignedUnit = updatedUnit,
            assignedOperatorName = updatedOperatorName,
            assignedOperatorPhone = updatedOperatorPhone,
            custodyRecords = updatedCustody,
            serverVersion = currentJob.serverVersion + 1,
            updatedAtEpochMs = System.currentTimeMillis(),
        )

        _activeTowJob.value = if (nextState in setOf(TowState.COMPLETED, TowState.CANCELLED)) null else updatedJob
        jobHistory[jobId] = updatedJob

        towTruckDao?.let { dao ->
            scope.launch {
                dao.insertRequest(updatedJob.toEntity())
            }
        }

        return TowCommandResult.Success(updatedJob)
    }

    fun clearActiveJob() {
        _activeTowJob.value = null
    }

    companion object {
        fun TowTruckRequestEntity.toTowJob(): TowJob {
            val jId = runCatching { UUID.fromString(requestId) }.getOrElse { UUID.nameUUIDFromBytes(requestId.toByteArray()) }
            val cId = runCatching { UUID.fromString(userId) }.getOrElse { UUID.nameUUIDFromBytes(userId.toByteArray()) }
            val towState = when (status) {
                "OPEN" -> TowState.REQUESTED
                "TAKEN" -> TowState.ASSIGNED
                "COMPLETED" -> TowState.COMPLETED
                "CANCELLED" -> TowState.CANCELLED
                "DISPUTED" -> TowState.DISPUTED
                else -> runCatching { TowState.valueOf(status) }.getOrDefault(TowState.REQUESTED)
            }
            return TowJob(
                jobId = jId,
                customerId = cId,
                customerName = "Cliente MEET",
                customerPhone = phone,
                vehicleVin = null,
                vehicleSummary = vehicleInfo.ifBlank { "Vehículo" },
                pickupLocation = GeoPoint(latitude, longitude),
                pickupAddress = locationName.ifBlank { "Punto de recogida" },
                destinationLocation = if (destinationLatitude != null && destinationLongitude != null) GeoPoint(destinationLatitude, destinationLongitude) else null,
                destinationAddress = destinationName,
                requiredCapabilities = setOf(TowCapabilities.FLATBED),
                assignedUnit = assignedDriverId?.let { drvId ->
                    TowUnit(
                        towUnitId = drvId,
                        operatorId = runCatching { UUID.fromString(drvId) }.getOrElse { UUID.nameUUIDFromBytes(drvId.toByteArray()) },
                        brandModel = "Unidad Asignada",
                        licensePlate = "---",
                        capabilities = setOf(TowCapabilities.FLATBED),
                        isVerified = true,
                        isAvailable = true
                    )
                },
                assignedOperatorName = assignedDriverName,
                assignedOperatorPhone = assignedDriverPhone,
                state = towState,
                estimatedPrice = if (priceOffer > 0.0) Money.ofCrc(priceOffer.toLong()) else null,
                serverVersion = 1L,
                createdAtEpochMs = createdAt,
                updatedAtEpochMs = completedAt ?: createdAt,
            )
        }

        fun TowJob.toEntity(): TowTruckRequestEntity {
            val statusStr = when (state) {
                TowState.REQUESTED, TowState.MATCHING -> "OPEN"
                TowState.ASSIGNED, TowState.EN_ROUTE, TowState.ARRIVED, TowState.LOADING,
                TowState.LOADED, TowState.IN_TRANSIT, TowState.ARRIVED_DESTINATION,
                TowState.UNLOADING, TowState.DELIVERED -> "TAKEN"
                TowState.COMPLETED -> "COMPLETED"
                TowState.CANCELLED -> "CANCELLED"
                TowState.DISPUTED -> "DISPUTED"
            }
            return TowTruckRequestEntity(
                requestId = jobId.toString(),
                userId = customerId.toString(),
                vehicleInfo = vehicleSummary,
                latitude = pickupLocation.latitude,
                longitude = pickupLocation.longitude,
                locationName = pickupAddress,
                destinationLatitude = destinationLocation?.latitude,
                destinationLongitude = destinationLocation?.longitude,
                destinationName = destinationAddress,
                phone = customerPhone,
                status = statusStr,
                assignedDriverId = assignedUnit?.operatorId?.toString(),
                assignedDriverName = assignedOperatorName,
                assignedDriverPhone = assignedOperatorPhone,
                priceOffer = estimatedPrice?.amountMinor?.toDouble() ?: 0.0,
                createdAt = createdAtEpochMs,
                completedAt = if (state in setOf(TowState.COMPLETED, TowState.CANCELLED)) updatedAtEpochMs else null
            )
        }
    }
}
