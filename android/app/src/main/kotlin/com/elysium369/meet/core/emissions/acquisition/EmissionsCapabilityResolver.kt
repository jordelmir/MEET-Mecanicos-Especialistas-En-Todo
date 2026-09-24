package com.elysium369.meet.core.emissions.acquisition

enum class CapabilityState {
    SUPPORTED,
    NOT_SUPPORTED,
    UNKNOWN
}

data class EmissionsCapabilities(
    val fuelSystemStatus: CapabilityState = CapabilityState.UNKNOWN,
    val engineLoad: CapabilityState = CapabilityState.UNKNOWN,
    val coolantTemp: CapabilityState = CapabilityState.UNKNOWN,
    val stftB1: CapabilityState = CapabilityState.UNKNOWN,
    val ltftB1: CapabilityState = CapabilityState.UNKNOWN,
    val map: CapabilityState = CapabilityState.UNKNOWN,
    val rpm: CapabilityState = CapabilityState.UNKNOWN,
    val speed: CapabilityState = CapabilityState.UNKNOWN,
    val iat: CapabilityState = CapabilityState.UNKNOWN,
    val maf: CapabilityState = CapabilityState.UNKNOWN,
    val o2Narrowband: CapabilityState = CapabilityState.UNKNOWN,
    val o2WidebandLambda: CapabilityState = CapabilityState.UNKNOWN,
    val commandedEquivalenceRatio: CapabilityState = CapabilityState.UNKNOWN,
    val catalystTemp: CapabilityState = CapabilityState.UNKNOWN,
    val fuelRate: CapabilityState = CapabilityState.UNKNOWN,
    val mode06: CapabilityState = CapabilityState.UNKNOWN
) {
    val canComputeEmissionsConfidence: Double
        get() {
            var score = 0.0
            if (rpm == CapabilityState.SUPPORTED) score += 0.2
            if (coolantTemp == CapabilityState.SUPPORTED) score += 0.15
            if (stftB1 == CapabilityState.SUPPORTED) score += 0.15
            if (o2Narrowband == CapabilityState.SUPPORTED || o2WidebandLambda == CapabilityState.SUPPORTED) score += 0.2
            if (maf == CapabilityState.SUPPORTED || map == CapabilityState.SUPPORTED) score += 0.15
            if (mode06 == CapabilityState.SUPPORTED) score += 0.15
            return score.coerceIn(0.0, 1.0)
        }
}

/**
 * Resolves vehicle emissions telemetry capabilities from supported PIDs and protocol interrogation.
 */
class EmissionsCapabilityResolver {

    fun resolve(
        supportedPids: Set<String>,
        mode06Available: Boolean? = null
    ): EmissionsCapabilities {
        fun check(pidHex: String): CapabilityState {
            val upper = pidHex.uppercase().removePrefix("01")
            val fullUpper = pidHex.uppercase()
            return if (supportedPids.contains(fullUpper) || supportedPids.contains(upper)) {
                CapabilityState.SUPPORTED
            } else {
                CapabilityState.NOT_SUPPORTED
            }
        }

        val hasNarrowband = check("0114") == CapabilityState.SUPPORTED ||
            check("0115") == CapabilityState.SUPPORTED ||
            supportedPids.any { it.startsWith("011") }

        val hasWideband = check("0124") == CapabilityState.SUPPORTED ||
            check("0134") == CapabilityState.SUPPORTED ||
            supportedPids.any { it.startsWith("012") || it.startsWith("013") }

        val hasCatTemp = check("013C") == CapabilityState.SUPPORTED ||
            check("013D") == CapabilityState.SUPPORTED ||
            check("013E") == CapabilityState.SUPPORTED ||
            check("013F") == CapabilityState.SUPPORTED

        val m06State = when (mode06Available) {
            true -> CapabilityState.SUPPORTED
            false -> CapabilityState.NOT_SUPPORTED
            null -> CapabilityState.UNKNOWN
        }

        return EmissionsCapabilities(
            fuelSystemStatus = check("0103"),
            engineLoad = check("0104"),
            coolantTemp = check("0105"),
            stftB1 = check("0106"),
            ltftB1 = check("0107"),
            map = check("010B"),
            rpm = check("010C"),
            speed = check("010D"),
            iat = check("010F"),
            maf = check("0110"),
            o2Narrowband = if (hasNarrowband) CapabilityState.SUPPORTED else CapabilityState.NOT_SUPPORTED,
            o2WidebandLambda = if (hasWideband) CapabilityState.SUPPORTED else CapabilityState.NOT_SUPPORTED,
            commandedEquivalenceRatio = check("0144"),
            catalystTemp = if (hasCatTemp) CapabilityState.SUPPORTED else CapabilityState.NOT_SUPPORTED,
            fuelRate = check("015E"),
            mode06 = m06State
        )
    }
}
