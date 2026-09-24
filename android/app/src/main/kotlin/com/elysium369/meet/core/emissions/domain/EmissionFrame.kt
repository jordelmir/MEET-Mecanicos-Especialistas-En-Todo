package com.elysium369.meet.core.emissions.domain

/**
 * Synchronized emissions and combustion telemetry frame.
 * Single source of truth across OBD, physics models, ML estimation, UI, and storage.
 */
data class EmissionFrame(
    val timestampMs: Long = System.currentTimeMillis(),
    val monotonicNs: Long = System.nanoTime(),
    val rpm: EmissionMetric? = null,
    val coolantC: EmissionMetric? = null,
    val mafGps: EmissionMetric? = null,
    val mapKpa: EmissionMetric? = null,
    val iatC: EmissionMetric? = null,
    val stftB1Pct: EmissionMetric? = null,
    val ltftB1Pct: EmissionMetric? = null,
    val o2B1S1: EmissionMetric? = null,
    val o2B1S2: EmissionMetric? = null,
    val lambda: EmissionMetric? = null,
    val fuelRate: EmissionMetric? = null,
    val catalystTempC: EmissionMetric? = null,
    val co: EmissionMetric? = null,
    val hc: EmissionMetric? = null,
    val co2: EmissionMetric? = null,
    val nox: EmissionMetric? = null
)
