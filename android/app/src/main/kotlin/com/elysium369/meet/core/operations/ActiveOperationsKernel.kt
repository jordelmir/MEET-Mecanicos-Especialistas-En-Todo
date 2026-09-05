package com.elysium369.meet.core.operations

import android.content.Context
import android.util.Log
import com.elysium369.meet.data.local.dao.ActiveOperationDao
import com.elysium369.meet.identity.ActivePrincipalKernel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ActiveOperationsKernel — Application-scoped authority for all in-flight operations.
 *
 * Every domain (OBD, Ride, Fuel, Comms, Legal, Market, Sync, Terminal) registers
 * its operations here. The kernel enforces:
 * - Single source of truth for operation lifecycle
 * - Scope-based survival semantics
 * - Heartbeat monitoring for stalled operations
 * - Cross-domain visibility (Home dashboard, debugging, recovery)
 */
@Singleton
class ActiveOperationsKernel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val principalKernel: ActivePrincipalKernel,
    private val operationDao: ActiveOperationDao,
    private val applicationScope: CoroutineScope,
) {
    private val _operations = MutableStateFlow<Map<String, ActiveOperation>>(emptyMap())
    val operations: StateFlow<Map<String, ActiveOperation>> = _operations.asStateFlow()

    // Active operations filtered by scope for dashboard visibility
    val foregroundServiceOps: StateFlow<List<ActiveOperation>> = _operations.map { ops ->
        ops.values.filter { it.scope == ActiveOperationScope.FOREGROUND_SERVICE_SCOPED && it.state.isActive }.toList()
    }.stateIn(applicationScope, SharingStarted.WhileSubscribed(), emptyList())

    val persistentWorkOps: StateFlow<List<ActiveOperation>> = _operations.map { ops ->
        ops.values.filter { it.scope == ActiveOperationScope.PERSISTENT_WORK_SCOPED && it.state.isActive }.toList()
    }.stateIn(applicationScope, SharingStarted.WhileSubscribed(), emptyList())

    val vehicleScopedOps: StateFlow<List<ActiveOperation>> = _operations.map { ops ->
        ops.values.filter { it.scope == ActiveOperationScope.VEHICLE_SCOPED && it.state.isActive }.toList()
    }.stateIn(applicationScope, SharingStarted.WhileSubscribed(), emptyList())

    // All currently active (non-terminal) operations
    val allActiveOps: StateFlow<List<ActiveOperation>> = _operations.map { ops ->
        ops.values.filter { it.state.isActive }.toList()
    }.stateIn(applicationScope, SharingStarted.WhileSubscribed(), emptyList())

    init {
        // Heartbeat monitor — marks operations stale if no heartbeat for 5 min
        applicationScope.launch {
            while (true) {
                delay(60_000) // check every minute
                val now = System.currentTimeMillis()
                _operations.update { current ->
                    current.mapValues { (id, op) ->
                        if (op.state.isActive && (now - op.lastHeartbeat > 300_000)) {
                            op.copyWithState(ActiveOperationState.FAILED, "Heartbeat timeout (5m)")
                        } else {
                            op
                        }
                    }
                }
                persistActiveOperations()
            }
        }

        // Load durable operations on principal change
        applicationScope.launch {
            principalKernel.activePrincipal
                .map { it.id }
                .distinctUntilChanged()
                .collect { ownerId ->
                    loadDurableOperations(ownerId)
                }
        }
    }

    /** Register a new operation — returns the created operation. */
    fun register(
        operationType: ActiveOperationType,
        scope: ActiveOperationScope,
        vehicleId: String? = null,
        rideId: String? = null,
        recoverability: ActiveOperationRecoverability = ActiveOperationRecoverability.FULL,
        metadata: Map<String, String> = emptyMap(),
    ): ActiveOperation {
        val ownerId = principalKernel.current().id
        val operation = ActiveOperation(
            operationType = operationType,
            scope = scope,
            ownerPrincipalId = ownerId,
            vehicleId = vehicleId,
            rideId = rideId,
            recoverability = recoverability,
            metadata = metadata,
        )
        _operations.update { it + (operation.operationId to operation.copyWithState(ActiveOperationState.STARTING)) }
        persistOperation(operation.copyWithState(ActiveOperationState.STARTING))
        Log.i("ActiveOps", "Registered: ${operation.operationType} (${operation.operationId}) scope=$scope vehicle=$vehicleId ride=$rideId")
        return operation
    }

    /** Update operation state with heartbeat. */
    fun heartbeat(operationId: String, state: ActiveOperationState? = null, progress: Double? = null, error: String? = null) {
        _operations.update { current ->
            current[operationId]?.let { op ->
                val newState = state ?: op.state
                val newProgress = progress ?: op.progress
                val newError = error ?: op.error
                val updated = op.copy(
                    state = newState,
                    error = newError,
                    lastHeartbeat = System.currentTimeMillis(),
                    progress = newProgress,
                )
                current + (operationId to updated)
            } ?: current
        }
        // Persist asynchronously
        applicationScope.launch {
            operationDao.getById(operationId)?.let { entity ->
                val domainOp = entity.toDomain()
                persistOperation(domainOp.copy(
                    state = state ?: domainOp.state,
                    error = error ?: domainOp.error,
                    lastHeartbeat = System.currentTimeMillis(),
                    progress = progress ?: domainOp.progress,
                ))
            }
        }
    }

    /** Mark operation as completed successfully. */
    fun complete(operationId: String) {
        heartbeat(operationId, ActiveOperationState.COMPLETED)
        // Clean up from memory after 30s, keep in DB for audit
        applicationScope.launch {
            delay(30_000)
            _operations.update { it - operationId }
        }
    }

    /** Mark operation as failed. */
    fun fail(operationId: String, error: String) {
        heartbeat(operationId, ActiveOperationState.FAILED, error = error)
        // Keep failed ops in memory for 5 min for debugging
        applicationScope.launch {
            delay(300_000)
            _operations.update { it - operationId }
        }
    }

    /** Stop operation gracefully (user cancellation). */
    fun stop(operationId: String) {
        heartbeat(operationId, ActiveOperationState.STOPPING)
        applicationScope.launch {
            delay(5_000)
            _operations.update { it - operationId }
            withContext(Dispatchers.IO) {
                operationDao.markStopped(operationId, "User stopped", System.currentTimeMillis())
            }
        }
    }

    /** Get operation by ID. */
    fun get(operationId: String): ActiveOperation? = _operations.value[operationId]

    /** Get all active operations for a vehicle. */
    fun getActiveByVehicle(vehicleId: String): List<ActiveOperation> =
        _operations.value.values.filter { it.vehicleId == vehicleId && it.state.isActive }.toList()

    /** Get all active operations for a ride. */
    fun getActiveByRide(rideId: String): List<ActiveOperation> =
        _operations.value.values.filter { it.rideId == rideId && it.state.isActive }.toList()

    /** Get all active operations of a type. */
    fun getActiveByType(type: ActiveOperationType): List<ActiveOperation> =
        _operations.value.values.filter { it.operationType == type && it.state.isActive }.toList()

    /** Check if any operation of a type is running. */
    fun isTypeActive(type: ActiveOperationType): Boolean =
        _operations.value.values.any { it.operationType == type && it.state.isActive }

    private fun persistOperation(operation: ActiveOperation) {
        applicationScope.launch(Dispatchers.IO) {
            try {
                operationDao.upsert(operation.toEntity())
            } catch (e: Exception) {
                Log.e("ActiveOps", "Failed to persist operation ${operation.operationId}", e)
            }
        }
    }

    private suspend fun loadDurableOperations(ownerId: String) {
        try {
            val entities = withContext(Dispatchers.IO) { operationDao.getActiveByOwner(ownerId) }
            val loaded = entities.map { it.toDomain() }.associateBy { it.operationId }
            _operations.update { current ->
                // Merge: durable operations take precedence for RUNNING/STARTING/PAUSED
                current + loaded.filter { (_, op) -> op.state.isActive }
            }
            Log.i("ActiveOps", "Loaded ${loaded.size} durable operations for $ownerId")
        } catch (e: Exception) {
            Log.e("ActiveOps", "Failed to load durable operations for $ownerId", e)
        }
    }

    private suspend fun persistActiveOperations() {
        val active = _operations.value.values.filter { it.state.isActive }.map { it.toEntity() }
        applicationScope.launch(Dispatchers.IO) {
            active.forEach { operationDao.upsert(it) }
        }
    }

    companion object {
        private const val TAG = "ActiveOperationsKernel"
    }
}

private val ActiveOperationState.isActive: Boolean
    get() = this in setOf(
        ActiveOperationState.CREATED,
        ActiveOperationState.STARTING,
        ActiveOperationState.RUNNING,
        ActiveOperationState.PAUSED,
        ActiveOperationState.STOPPING,
    )

private fun ActiveOperation.toEntity(): ActiveOperationEntity {
    return ActiveOperationEntity(
        operationId = operationId,
        operationType = operationType.name,
        scope = scope.name,
        ownerPrincipalId = ownerPrincipalId,
        vehicleId = vehicleId,
        rideId = rideId,
        startedAt = startedAt,
        state = state.name,
        progress = progress,
        recoverability = recoverability.name,
        lastHeartbeat = lastHeartbeat,
        error = error,
        metadataJson = kotlinx.serialization.json.Json.encodeToString(metadata),
    )
}

private fun ActiveOperationEntity.toDomain(): ActiveOperation {
    val metadata = try {
        kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(metadataJson)
    } catch (e: Exception) {
        emptyMap()
    }
    return ActiveOperation(
        operationId = operationId,
        operationType = ActiveOperationType.valueOf(operationType),
        scope = ActiveOperationScope.valueOf(scope),
        ownerPrincipalId = ownerPrincipalId,
        vehicleId = vehicleId,
        rideId = rideId,
        startedAt = startedAt,
        state = ActiveOperationState.valueOf(state),
        progress = progress,
        recoverability = ActiveOperationRecoverability.valueOf(recoverability),
        lastHeartbeat = lastHeartbeat,
        error = error,
        metadata = metadata,
    )
}