package com.elysium369.meet.core.obd

/**
 * Common data models for OBD2 diagnostics.
 */

data class ReadinessResult(
    val milOn: Boolean,
    val dtcCount: Int,
    val monitors: List<MonitorStatus>
)

data class MonitorStatus(
    val name: String,
    val available: Boolean,
    val complete: Boolean
)

data class DataLogEntry(
    val timestamp: Long,
    val sensorData: Map<String, Float>
)

data class QosMetrics(
    val cmdsPerSecond: Float = 0f,
    val latencyMs: Int = 0,
    val isStable: Boolean = true,
    // Compatibility with old UI if needed
    val avgLatencyMs: Float = 0f,
    val reliability: Float = 100f,
    val totalRequests: Int = 0
)

data class ObdCommandDef(
    val command: String,
    val expectedResponse: String,
    val description: String,
    val isSafetyCritical: Boolean = false
)

data class ActiveTest(
    val id: String,
    val name: String,
    val description: String,
    val startCommand: String,
    val stopCommand: String,
    val manufacturer: String? = null,
    val durationMs: Long = 10000L, // Default 10s if not manual
    val monitoredPids: List<String> = emptyList(), // PIDs to watch during test
    val safetyConditions: List<SafetyCondition> = emptyList()
)

enum class SafetyCondition {
    ENGINE_OFF,
    ENGINE_RUNNING,
    VEHICLE_STATIONARY,
    BATTERY_ABOVE_12V,
    TRANS_IN_PARK
}

data class ActiveTestStatus(
    val isActive: Boolean = false,
    val progress: Float = 0f,
    val message: String = "",
    val currentValues: Map<String, Float> = emptyMap()
)

class ObdConnectionException(message: String) : Exception(message)
