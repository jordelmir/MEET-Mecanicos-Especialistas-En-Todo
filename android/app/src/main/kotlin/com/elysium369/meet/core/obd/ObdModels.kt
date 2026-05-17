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


data class QosMetrics(
    val cmdsPerSecond: Float = 0f,
    val latencyMs: Int = 0,
    val isStable: Boolean = true,
    // Compatibility and advanced metrics
    val avgLatencyMs: Float = 0f,
    val reliability: Float = 100f,
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0
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

/**
 * Professional Mode 06 Test Result.
 * Includes parsed metrics, unit conversion and expert insights.
 */
data class Mode06TestResult(
    val mid: String, // Monitor ID (e.g. "$01")
    val tid: String, // Test ID (e.g. "$01")
    val value: Float,
    val minLimit: Float?,
    val maxLimit: Float?,
    val unit: String,
    val passed: Boolean,
    val testName: String,
    val componentName: String,
    val proTip: String? = null,
    val severity: DiagnosticSeverity = DiagnosticSeverity.INFO
)

enum class DiagnosticSeverity {
    INFO, MODERATE, HIGH, CRITICAL
}

data class TopFix(
    val code: String,
    val title: String,
    val fix: String,
    val severity: DiagnosticSeverity,
    val probability: Int,
    val parts: List<String>,
    val laborHours: Float
)

data class DtcAnalysis(
    val code: String,
    val description: String,
    val topFix: TopFix? = null,
    val severity: DiagnosticSeverity = DiagnosticSeverity.INFO
)

class ObdConnectionException(message: String) : Exception(message)
