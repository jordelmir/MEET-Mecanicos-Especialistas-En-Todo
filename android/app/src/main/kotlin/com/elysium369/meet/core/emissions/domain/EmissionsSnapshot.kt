package com.elysium369.meet.core.emissions.domain

/**
 * Expanded vehicle state snapshot tailored for emissions telemetry analysis.
 */
data class EmissionsSnapshot(
    val o2B1S1Voltage: Double? = null,
    val o2B1S2Voltage: Double? = null,
    val equivalenceRatioB1S1: Double? = null,
    val commandedEquivalenceRatio: Double? = null,
    val catalystTempB1S1C: Double? = null,
    val catalystTempB1S2C: Double? = null,
    val fuelSystemStatus: Int? = null,
    val fuelRateLph: Double? = null,
    val stftB1Pct: Double? = null,
    val ltftB1Pct: Double? = null,
    val mafGps: Double? = null,
    val mapKpa: Double? = null,
    val supportedSignals: Set<String> = emptySet(),
    val timestampMs: Long = System.currentTimeMillis()
)
