package com.elysium369.meet.core.operations

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ActiveOperationScope — Determines when an operation survives.
 */
enum class ActiveOperationScope {
    /** Lives only while its Compose screen/navigation destination is visible. */
    SCREEN_SCOPED,
    /** Lives across navigation within the same session (ViewModel lifecycle). */
    SESSION_SCOPED,
    /** Tied to the currently active vehicle — dies when vehicle changes. */
    VEHICLE_SCOPED,
    /** Lives as long as the app process lives; survives navigation & vehicle changes. */
    APPLICATION_SCOPED,
    /** Tied to a foreground Service lifecycle; survives background, screen off. */
    FOREGROUND_SERVICE_SCOPED,
    /** Tied to a WorkManager periodic/one-time worker; survives process death & reboot. */
    PERSISTENT_WORK_SCOPED,
}

/**
 * ActiveOperationType — Canonical operation types across domains.
 */
enum class ActiveOperationType {
    // OBD / Vehicle
    OBD_SESSION,
    OBD_SCAN,
    VIN_READ,
    ECU_DIAGNOSTIC,
    VEHICLE_TRUTH_SYNC,
    // Ride / Mobility
    RIDE_ACTIVE,
    RIDE_LOCATION_TRACKING,
    RIDE_MATCHING,
    DRIVER_ENROLLMENT,
    // Fuel / Energy
    FUEL_TRANSACTION,
    FUEL_OCR,
    FUEL_STATION_SYNC,
    // Communications
    MESSAGE_SENDING,
    CALL_ACTIVE,
    PTT_CHANNEL_ACTIVE,
    // Legal / Evidence
    LEGAL_EVENT_CAPTURE,
    EVIDENCE_UPLOAD,
    CUSTODY_CHAIN_SYNC,
    // Market / Commerce
    MARKET_SYNC,
    PARTS_REQUEST,
    SERVICE_BIDDING,
    // Sync / Background
    CLOUD_SYNC,
    BACKUP,
    DATABASE_MAINTENANCE,
    // Terminal / Dev
    TERMINAL_DAEMON,
    LOCAL_COMPILATION,
    // Generic
    UNKNOWN,
}

/**
 * ActiveOperationState — Lifecycle of an operation.
 */
enum class ActiveOperationState {
    CREATED,
    STARTING,
    RUNNING,
    PAUSED,
    STOPPING,
    STOPPED,
    FAILED,
    COMPLETED,
}

/**
 * ActiveOperationRecoverability — What happens on process death / reboot.
 */
enum class ActiveOperationRecoverability {
    /** Fully restorable from durable state (Room + intent). */
    FULL,
    /** Partially restorable — needs user confirmation. */
    PARTIAL,
    /** Not restorable — must be explicitly restarted by user. */
    NONE,
}

/**
 * ActiveOperation — Domain-neutral representation of an in-flight operation.
 */
data class ActiveOperation(
    val operationId: String = UUID.randomUUID().toString(),
    val operationType: ActiveOperationType,
    val scope: ActiveOperationScope,
    val ownerPrincipalId: String,
    val vehicleId: String? = null,
    val rideId: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    var state: ActiveOperationState = ActiveOperationState.CREATED,
    var progress: Double? = null, // 0.0 - 1.0
    val recoverability: ActiveOperationRecoverability,
    var lastHeartbeat: Long = System.currentTimeMillis(),
    var error: String? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    fun copyWithState(newState: ActiveOperationState, newError: String? = null): ActiveOperation {
        return copy(
            state = newState,
            error = newError,
            lastHeartbeat = System.currentTimeMillis(),
        )
    }
}

/**
 * Room entity for durable operation tracking.
 */
@Entity(
    tableName = "active_operations",
    primaryKeys = ["operationId"],
    indices = [
        Index(value = ["ownerPrincipalId"], name = "idx_active_operations_owner"),
        Index(value = ["vehicleId"], name = "idx_active_operations_vehicle"),
        Index(value = ["rideId"], name = "idx_active_operations_ride"),
        Index(value = ["scope"], name = "idx_active_operations_scope"),
        Index(value = ["state"], name = "idx_active_operations_state"),
    ],
)
data class ActiveOperationEntity(
    val operationId: String,
    val operationType: String,
    val scope: String,
    val ownerPrincipalId: String,
    val vehicleId: String? = null,
    val rideId: String? = null,
    val startedAt: Long,
    val state: String,
    val progress: Double? = null,
    val recoverability: String,
    val lastHeartbeat: Long,
    val error: String? = null,
    val metadataJson: String = "{}",
)