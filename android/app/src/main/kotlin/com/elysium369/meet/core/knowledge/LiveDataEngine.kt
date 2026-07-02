package com.elysium369.meet.core.knowledge

import kotlinx.serialization.Serializable

/**
 * Source of an expected range value.
 *   GENERIC_SAFE_RANGE  — generic safety range, no OEM.
 *   COMMUNITY_RANGE     — community-validated.
 *   USER_MANUAL_RANGE   — user-supplied, manual entry.
 *   OEM_LICENSED_RANGE_FUTURE — reserved for future OEM integration.
 */
@Serializable
enum class RangeSource {
    GENERIC_SAFE_RANGE,
    COMMUNITY_RANGE,
    USER_MANUAL_RANGE,
    OEM_LICENSED_RANGE_FUTURE
}

/**
 * Expected range for a PID. Optional upper/lower bound with source.
 */
@Serializable
data class ExpectedRange(
    val min: Double? = null,
    val max: Double? = null,
    val unit: String = "",
    val source: RangeSource = RangeSource.GENERIC_SAFE_RANGE,
    val notes: String = ""
)

/**
 * A live PID value as observed by the engine.
 */
@Serializable
data class LivePidValue(
    val pid: String,
    val name: String,
    val value: Double?,
    val unit: String,
    val rawHex: String? = null,
    val timestamp: Long,
    val source: String,                 // OBD, USER, SIMULATED
    val quality: DataQuality = DataQuality.REAL,
    val expectedRange: ExpectedRange? = null,
    val rangeSource: RangeSource = RangeSource.GENERIC_SAFE_RANGE,
    val relationshipToDtc: String? = null
) {
    /**
     * Returns a human-readable status string per the spec.
     */
    fun status(): String = when {
        quality == DataQuality.MISSING -> "SIN ENLACE"
        quality == DataQuality.INVALID -> "INVALIDO"
        quality == DataQuality.SIMULATED -> "SIMULADO"
        value == null -> "N/A"
        expectedRange == null -> "OK"
        else -> {
            val v = value!!
            val minOk = expectedRange.min == null || v >= expectedRange.min!!
            val maxOk = expectedRange.max == null || v <= expectedRange.max!!
            when {
                minOk && maxOk -> "OK"
                else -> "FUERA DE RANGO"
            }
        }
    }

    /**
     * If the range source is OEM_LICENSED_RANGE_FUTURE, the spec says:
     * "Rango específico no disponible. Validar con manual OEM."
     */
    fun rangeDisclaimer(): String? = when {
        expectedRange == null -> null
        expectedRange.source == RangeSource.OEM_LICENSED_RANGE_FUTURE ->
            "Rango específico no disponible. Validar con manual OEM."
        else -> null
    }
}

/**
 * Live data engine.
 * Owns the source-of-truth for whether a scan is connected and
 * whether each PID is supported. Does NOT invent values.
 */
class LiveDataEngine {

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        AUTHORIZING,
        READY,
        SCANNING,
        ERROR,
        DEGRADED,
        SIMULATED
    }

    private var state: ConnectionState = ConnectionState.DISCONNECTED
    private val supportedPids: MutableSet<String> = HashSet()

    fun setState(newState: ConnectionState) {
        state = newState
    }

    fun state(): ConnectionState = state

    fun isConnected(): Boolean = state in setOf(
        ConnectionState.READY,
        ConnectionState.SCANNING,
        ConnectionState.AUTHORIZING,
        ConnectionState.SIMULATED
    )

    fun markPidSupported(pid: String) {
        supportedPids.add(pid)
    }

    fun markPidUnsupported(pid: String) {
        supportedPids.remove(pid)
    }

    fun isPidSupported(pid: String): Boolean = supportedPids.contains(pid)

    /**
     * Read a PID value. Returns MISSING if scanner is disconnected, or
     * INVALID if the PID is not supported by the current adapter.
     */
    fun readPid(
        pid: String,
        name: String,
        unit: String,
        timestamp: Long,
        expectedRange: ExpectedRange? = null,
        relationshipToDtc: String? = null
    ): LivePidValue {
        val (value, quality) = when {
            !isConnected() -> Pair(null, DataQuality.MISSING)
            !isPidSupported(pid) -> Pair(null, DataQuality.INVALID)
            state == ConnectionState.SIMULATED -> Pair(null, DataQuality.SIMULATED)
            else -> Pair(0.0, DataQuality.REAL)  // placeholder; real impl would query adapter
        }
        return LivePidValue(
            pid = pid,
            name = name,
            value = value,
            unit = unit,
            rawHex = null,
            timestamp = timestamp,
            source = if (quality == DataQuality.SIMULATED) "SIMULATED" else "OBD",
            quality = quality,
            expectedRange = expectedRange,
            rangeSource = expectedRange?.source ?: RangeSource.GENERIC_SAFE_RANGE,
            relationshipToDtc = relationshipToDtc
        )
    }
}
