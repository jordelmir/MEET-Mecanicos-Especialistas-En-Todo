package com.elysium369.meet.operations

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class OperationOwner {
    SCREEN_SCOPED,
    SESSION_SCOPED,
    VEHICLE_SCOPED,
    APPLICATION_SCOPED,
    FOREGROUND_SERVICE_SCOPED,
    PERSISTENT_WORK_SCOPED,
}

enum class OperationState { STARTING, RUNNING, PAUSED, RETRYING, FAILED }

enum class OperationRecoverability {
    NONE,
    ACTIVITY_RECREATION,
    PROCESS_RESTART,
    DEVICE_REBOOT,
}

data class ActiveOperation(
    val operationId: String,
    val type: String,
    val vehicleId: String?,
    val startedAtEpochMs: Long,
    val state: OperationState,
    val progress: Float?,
    val owner: OperationOwner,
    val recoverability: OperationRecoverability,
    val lastHeartbeatEpochMs: Long,
    val errorCode: String? = null,
)

/** Application projection of operations which outlive a destination. */
@Singleton
class ActiveOperationsRegistry @Inject constructor() {
    private val mutableOperations = MutableStateFlow<Map<String, ActiveOperation>>(emptyMap())
    val operations: StateFlow<Map<String, ActiveOperation>> = mutableOperations.asStateFlow()

    fun upsert(operation: ActiveOperation) {
        require(operation.operationId.isNotBlank())
        require(operation.type.isNotBlank())
        require(operation.progress == null || operation.progress in 0f..1f)
        mutableOperations.update { it + (operation.operationId to operation) }
    }

    fun heartbeat(
        operationId: String,
        state: OperationState,
        progress: Float? = null,
        errorCode: String? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        mutableOperations.update { current ->
            val operation = current[operationId] ?: return@update current
            current + (operationId to operation.copy(
                state = state,
                progress = progress,
                lastHeartbeatEpochMs = nowEpochMs,
                errorCode = errorCode,
            ))
        }
    }

    /** Completion comes from the operation authority, never screen disposal. */
    fun complete(operationId: String) {
        mutableOperations.update { it - operationId }
    }
}
