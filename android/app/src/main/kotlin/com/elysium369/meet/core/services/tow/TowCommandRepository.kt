package com.elysium369.meet.core.services.tow

import com.elysium369.meet.core.geo.GeoPoint
import com.elysium369.meet.core.services.kernel.Money
import com.elysium369.meet.core.services.kernel.ServiceRole
import com.elysium369.meet.data.local.dao.TowJobDao
import com.elysium369.meet.data.local.dao.TowTruckDao
import com.elysium369.meet.data.local.entities.TowJobEntity
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

private val SHA256_HEX_REGEX = Regex("^[a-fA-F0-9]{64}$")

@Singleton
class TowCommandRepository(
    private val towJobDao: TowJobDao?,
    private val towTruckDao: TowTruckDao?,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(towJobDao: TowJobDao, towTruckDao: TowTruckDao) : this(
        towJobDao = towJobDao,
        towTruckDao = towTruckDao,
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    )
    constructor(towTruckDao: TowTruckDao?, scope: CoroutineScope) : this(
        towJobDao = null,
        towTruckDao = towTruckDao,
        scope = scope
    )

    constructor(towJobDao: TowJobDao) : this(
        towJobDao = towJobDao,
        towTruckDao = null,
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    )

    constructor() : this(
        towJobDao = null,
        towTruckDao = null,
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    )

    private val _activeTowJob = MutableStateFlow<TowJob?>(null)
    val activeTowJob: StateFlow<TowJob?> = _activeTowJob.asStateFlow()

    private val jobHistory = ConcurrentHashMap<UUID, TowJob>()
    private val jobLocks = ConcurrentHashMap<UUID, Mutex>()

    private fun getLockForJob(jobId: UUID): Mutex =
        jobLocks.computeIfAbsent(jobId) { Mutex() }

    init {
        // Hydrate from durable Room TowJobDao (primary) or legacy TowTruckDao
        towJobDao?.let { dao ->
            scope.launch {
                dao.getAllJobsFlow().collect { entities ->
                    entities.forEach { entity ->
                        val job = entity.toTowJob()
                        jobHistory[job.jobId] = job
                        if (job.state.isActive && _activeTowJob.value == null) {
                            _activeTowJob.value = job
                        }
                    }
                }
            }
        } ?: towTruckDao?.let { dao ->
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
        return towJobDao?.getAllJobsFlow()?.map { list ->
            list.map { it.toTowJob() }
        } ?: towTruckDao?.getRequestsFlow()?.map { list ->
            list.map { entity ->
                jobHistory[runCatching { UUID.fromString(entity.requestId) }.getOrNull()] ?: entity.toTowJob()
            }
        } ?: flowOf(jobHistory.values.sortedByDescending { it.createdAtEpochMs })
    }

    fun requestTow(
        customerId: UUID,
        customerName: String,
        customerPhone: String,
        vehicleVin: String? = null,
        vehicleSummary: String,
        pickupLocation: GeoPoint,
        pickupAddress: String,
        destinationLocation: GeoPoint? = null,
        destinationAddress: String? = null,
        requiredCapabilities: Set<TowCapabilities> = setOf(TowCapabilities.FLATBED),
        estimatedPrice: Money? = null,
        correlationId: String = UUID.randomUUID().toString(),
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
            correlationId = correlationId,
            createdAtEpochMs = System.currentTimeMillis(),
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        _activeTowJob.value = job
        jobHistory[job.jobId] = job

        val entity = job.toTowJobEntity()
        towJobDao?.let { dao ->
            scope.launch {
                dao.insertJob(entity)
            }
        }
        towTruckDao?.let { dao ->
            scope.launch {
                dao.insertRequest(job.toLegacyEntity())
            }
        }

        return job
    }

    fun executeAction(
        jobId: UUID,
        action: TowAction,
        actorRole: ServiceRole,
        actorId: UUID = UUID.randomUUID(),
        expectedVersion: Long, // Mandatory optimistic version check
    ): TowCommandResult = runBlocking {
        val lock = getLockForJob(jobId)
        lock.withLock {
            val currentJob = getJobById(jobId) ?: return@withLock TowCommandResult.JobNotFound(jobId)

            // Concurrency guard: version must match exactly
            if (currentJob.serverVersion != expectedVersion) {
                return@withLock TowCommandResult.ConcurrencyConflict(
                    expectedVersion = expectedVersion,
                    actualVersion = currentJob.serverVersion
                )
            }

            val nextState = TowStateEngine.getNextState(currentJob.state, action, actorRole)
                ?: return@withLock TowCommandResult.InvalidTransition(currentJob.state, action.javaClass.simpleName)

            val updatedCustody = currentJob.custodyRecords.toMutableList()
            var updatedUnit = currentJob.assignedUnit
            var updatedOperatorName = currentJob.assignedOperatorName
            var updatedOperatorPhone = currentJob.assignedOperatorPhone

            when (action) {
                is TowAction.AssignOperator -> {
                    // Do not forge verification or invent capabilities; preserve known unit or mark unverified
                    updatedUnit = TowUnit(
                        towUnitId = action.towUnitId,
                        operatorId = action.operatorId,
                        brandModel = "Unidad Asignada",
                        licensePlate = "GRUA-${action.towUnitId.take(4).uppercase()}",
                        capabilities = setOf(TowCapabilities.FLATBED),
                        isVerified = false,
                        isAvailable = false
                    )
                }
                is TowAction.ConfirmLoaded -> {
                    if (!SHA256_HEX_REGEX.matches(action.secureEvidenceHash)) {
                        return@withLock TowCommandResult.InvalidEvidence(
                            "Hash de evidencia de carga inválido: se requiere un hash SHA-256 hexadecimal de 64 caracteres. Recibido: ${action.secureEvidenceHash}"
                        )
                    }
                    updatedCustody.add(
                        TowCustodyRecord(
                            checkpoint = TowCustodyCheckpoint.LOADED_SECURED,
                            evidenceHash = action.secureEvidenceHash,
                            recordedAtEpochMs = System.currentTimeMillis(),
                            recordedByActorId = actorId,
                            canonicalEvidenceId = null,
                            notes = "Vehículo cargado y anclado conforme a inspección técnica."
                        )
                    )
                }
                is TowAction.ConfirmDelivered -> {
                    if (!SHA256_HEX_REGEX.matches(action.deliveryEvidenceHash)) {
                        return@withLock TowCommandResult.InvalidEvidence(
                            "Hash de evidencia de entrega inválido: se requiere un hash SHA-256 hexadecimal de 64 caracteres. Recibido: ${action.deliveryEvidenceHash}"
                        )
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

            // Atomic CAS in SQLite
            if (towJobDao != null) {
                val rows = towJobDao.compareAndSwapState(
                    jobId = jobId.toString(),
                    expectedVersion = expectedVersion,
                    newState = nextState.name,
                    updatedAtEpochMs = updatedJob.updatedAtEpochMs,
                    operatorName = updatedJob.assignedOperatorName,
                    operatorPhone = updatedJob.assignedOperatorPhone,
                    custodyRecordsJson = serializeCustodyRecords(updatedJob.custodyRecords),
                    assignedUnitJson = serializeUnit(updatedJob.assignedUnit)
                )
                if (rows == 0) {
                    return@withLock TowCommandResult.ConcurrencyConflict(
                        expectedVersion = expectedVersion,
                        actualVersion = currentJob.serverVersion
                    )
                }
            }

            towTruckDao?.let { dao ->
                scope.launch {
                    dao.insertRequest(updatedJob.toLegacyEntity())
                }
            }

            _activeTowJob.value = if (nextState in setOf(TowState.COMPLETED, TowState.CANCELLED)) null else updatedJob
            jobHistory[jobId] = updatedJob

            TowCommandResult.Success(updatedJob)
        }
    }

    fun clearActiveJob() {
        _activeTowJob.value = null
    }

    companion object {
        fun serializeCapabilities(caps: Set<TowCapabilities>): String =
            caps.joinToString(",") { it.name }

        fun deserializeCapabilities(raw: String): Set<TowCapabilities> =
            if (raw.isBlank()) setOf(TowCapabilities.FLATBED)
            else raw.split(",").mapNotNull { runCatching { TowCapabilities.valueOf(it.trim()) }.getOrNull() }.toSet()

        fun serializeUnit(unit: TowUnit?): String? = unit?.let {
            "${it.towUnitId}::${it.operatorId}::${it.brandModel}::${it.licensePlate}::${serializeCapabilities(it.capabilities)}::${it.maxWeightKg}::${it.isVerified}::${it.isAvailable}"
        }

        fun deserializeUnit(raw: String?): TowUnit? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split("::")
            if (parts.size < 8) return null
            return TowUnit(
                towUnitId = parts[0],
                operatorId = runCatching { UUID.fromString(parts[1]) }.getOrElse { return null },
                brandModel = parts[2],
                licensePlate = parts[3],
                capabilities = deserializeCapabilities(parts[4]),
                maxWeightKg = parts[5].toIntOrNull() ?: 3500,
                isVerified = parts[6].toBoolean(),
                isAvailable = parts[7].toBoolean(),
            )
        }

        fun serializeCustodyRecords(records: List<TowCustodyRecord>): String {
            if (records.isEmpty()) return "[]"
            return records.joinToString(";;") { rec ->
                "${rec.checkpoint.name}::${rec.evidenceHash}::${rec.recordedAtEpochMs}::${rec.recordedByActorId}::${rec.canonicalEvidenceId ?: ""}::${rec.notes ?: ""}"
            }
        }

        fun deserializeCustodyRecords(raw: String): List<TowCustodyRecord> {
            if (raw.isBlank() || raw == "[]") return emptyList()
            return raw.split(";;").mapNotNull { entry ->
                val parts = entry.split("::")
                if (parts.size >= 4) {
                    val cp = runCatching { TowCustodyCheckpoint.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
                    val hash = parts[1]
                    val ms = parts[2].toLongOrNull() ?: return@mapNotNull null
                    val actorId = runCatching { UUID.fromString(parts[3]) }.getOrNull() ?: return@mapNotNull null
                    val canonicalId = parts.getOrNull(4)?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    val notes = parts.getOrNull(5)?.takeIf { it.isNotBlank() }
                    TowCustodyRecord(
                        checkpoint = cp,
                        evidenceHash = hash,
                        recordedAtEpochMs = ms,
                        recordedByActorId = actorId,
                        canonicalEvidenceId = canonicalId,
                        notes = notes
                    )
                } else null
            }
        }

        fun TowJobEntity.toTowJob(): TowJob {
            val jId = runCatching { UUID.fromString(jobId) }.getOrElse { UUID.nameUUIDFromBytes(jobId.toByteArray()) }
            val cId = runCatching { UUID.fromString(customerId) }.getOrElse { UUID.nameUUIDFromBytes(customerId.toByteArray()) }
            val towState = runCatching { TowState.valueOf(state) }.getOrDefault(TowState.REQUESTED)

            return TowJob(
                jobId = jId,
                customerId = cId,
                customerName = customerName,
                customerPhone = customerPhone,
                vehicleVin = vehicleVin,
                vehicleSummary = vehicleSummary,
                pickupLocation = GeoPoint(pickupLatitude, pickupLongitude),
                pickupAddress = pickupAddress,
                destinationLocation = if (destinationLatitude != null && destinationLongitude != null) GeoPoint(destinationLatitude, destinationLongitude) else null,
                destinationAddress = destinationAddress,
                requiredCapabilities = deserializeCapabilities(requiredCapabilities),
                assignedUnit = deserializeUnit(assignedUnitJson),
                assignedOperatorName = assignedOperatorName,
                assignedOperatorPhone = assignedOperatorPhone,
                assignedOperatorRating = assignedOperatorRating,
                assignedOperatorCompletedJobs = assignedOperatorCompletedJobs,
                operatorLocation = if (operatorLatitude != null && operatorLongitude != null) GeoPoint(operatorLatitude, operatorLongitude) else null,
                operatorLocationFreshnessEpochMs = operatorFreshnessEpochMs,
                state = towState,
                estimatedPrice = estimatedPriceMinor?.let { Money.ofCrc(it) },
                quotedPrice = quotedPriceMinor?.let { Money.ofCrc(it) },
                authorizedPrice = authorizedPriceMinor?.let { Money.ofCrc(it) },
                finalSettlement = finalSettlementMinor?.let { Money.ofCrc(it) },
                custodyRecords = deserializeCustodyRecords(custodyRecordsJson),
                serverVersion = serverVersion,
                correlationId = correlationId,
                createdAtEpochMs = createdAtEpochMs,
                updatedAtEpochMs = updatedAtEpochMs,
            )
        }

        fun TowJob.toTowJobEntity(): TowJobEntity {
            return TowJobEntity(
                jobId = jobId.toString(),
                customerId = customerId.toString(),
                customerName = customerName,
                customerPhone = customerPhone,
                vehicleVin = vehicleVin,
                vehicleSummary = vehicleSummary,
                pickupLatitude = pickupLocation.latitude,
                pickupLongitude = pickupLocation.longitude,
                pickupAddress = pickupAddress,
                destinationLatitude = destinationLocation?.latitude,
                destinationLongitude = destinationLocation?.longitude,
                destinationAddress = destinationAddress,
                state = state.name,
                serverVersion = serverVersion,
                createdAtEpochMs = createdAtEpochMs,
                updatedAtEpochMs = updatedAtEpochMs,
                assignedProviderId = assignedUnit?.operatorId?.toString(),
                assignedOperatorId = assignedUnit?.operatorId?.toString(),
                assignedTowUnitId = assignedUnit?.towUnitId,
                assignedOperatorName = assignedOperatorName,
                assignedOperatorPhone = assignedOperatorPhone,
                assignedOperatorRating = assignedOperatorRating,
                assignedOperatorCompletedJobs = assignedOperatorCompletedJobs,
                operatorLatitude = operatorLocation?.latitude,
                operatorLongitude = operatorLocation?.longitude,
                operatorFreshnessEpochMs = operatorLocationFreshnessEpochMs,
                requiredCapabilities = serializeCapabilities(requiredCapabilities),
                assignedUnitJson = serializeUnit(assignedUnit),
                estimatedPriceMinor = estimatedPrice?.amountMinor,
                quotedPriceMinor = quotedPrice?.amountMinor,
                authorizedPriceMinor = authorizedPrice?.amountMinor,
                finalSettlementMinor = finalSettlement?.amountMinor,
                currency = estimatedPrice?.currency?.name ?: "CRC",
                correlationId = correlationId,
                custodyRecordsJson = serializeCustodyRecords(custodyRecords),
            )
        }

        fun TowJob.toLegacyEntity(): TowTruckRequestEntity {
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
                customerName = "Cliente",
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
                        isVerified = false,
                        isAvailable = false
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
    }
}
