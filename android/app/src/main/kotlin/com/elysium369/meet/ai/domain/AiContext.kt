package com.elysium369.meet.ai.domain

data class AiContext(
    val vehicle: VehicleContext?,
    val obd: ObdContext?,
    val dtcs: List<DtcContext>,
    val livePids: List<PidReading>,
    val manualAvailability: ManualAvailabilityContext?,
    val appModule: String,
    val locale: String,
    val userRole: UserRole,
    val safetyMode: Boolean = true
)

data class VehicleContext(
    val make: String,
    val model: String,
    val year: Int,
    val engine: String,
    val transmission: String = "",
    val fuel: String = "",
    val vin: String? = null,
    val odometer: Double? = null,
    val country: String? = null,
    val units: String? = null
)

data class ObdContext(
    val connected: Boolean,
    val adapterType: String = "",
    val protocol: String = "",
    val latencyMs: Long = 0L,
    val activePidsCount: Int = 0,
    val dtcActiveCount: Int = 0,
    val dtcPendingCount: Int = 0,
    val dtcHistoryCount: Int = 0,
    val readinessMonitors: List<String> = emptyList(),
    val mode06Available: Boolean = false,
    val batteryVoltage: Float = 0.0f
)

data class DtcContext(
    val code: String,
    val description: String,
    val status: String = "ACTIVE"
)

data class PidReading(
    val pid: String,
    val name: String,
    val value: String,
    val unit: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val anomalous: Boolean = false,
    val isStale: Boolean = false
)

data class ManualAvailabilityContext(
    val manualLocalAvailable: Boolean = false,
    val electricalDiagramAvailable: Boolean = false,
    val torqueSpecsAvailable: Boolean = false,
    val sourceId: String? = null
)

enum class UserRole {
    CLIENT,
    MECHANIC,
    EXPERT
}
