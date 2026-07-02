package com.elysium369.meet.core.knowledge

import kotlinx.serialization.Serializable

/**
 * Snapshot of vehicle PIDs captured at the moment a DTC was set.
 * Includes the DTC code and the OBD-II standardized Service $02 fields.
 */
@Serializable
data class FreezeFrameSnapshot(
    val dtcCode: String,
    val capturedAt: Long,
    val pids: Map<String, Double> = emptyMap(),
    val pidRanges: Map<String, ExpectedRange> = emptyMap(),
    val dataQuality: DataQuality = DataQuality.REAL
) {
    /**
     * Returns the comparison string per the spec:
     *   "Actual: 11.2V / Esperado KOEO generico: 12.4-12.7V / Estado: Bajo / Impacto: ..."
     */
    fun compareTo(
        pid: String,
        label: String,
        range: ExpectedRange
    ): PidComparison {
        val value = pids[pid]
        val inRange = value != null &&
            (range.min == null || value >= range.min!!) &&
            (range.max == null || value <= range.max!!)
        val status = when {
            dataQuality != DataQuality.REAL -> "SIN ENLACE REAL"
            value == null -> "MISSING"
            range.source == RangeSource.OEM_LICENSED_RANGE_FUTURE -> "VALIDAR CON OEM"
            inRange -> "OK"
            value < (range.min ?: value) -> "BAJO"
            else -> "ALTO"
        }
        val impact = impactOnDtc(pid, status, value, range)
        return PidComparison(
            pid = pid,
            label = label,
            actual = value,
            actualUnit = range.unit,
            expected = "${range.min ?: "-"}-${range.max ?: "-"} ${range.unit}",
            rangeSource = range.source,
            status = status,
            impact = impact
        )
    }

    private fun impactOnDtc(pid: String, status: String, value: Double?, range: ExpectedRange): String {
        if (value == null) return "Sin lectura real; no se puede juzgar este parametro."
        return when (pid.uppercase()) {
            "BATTERY_VOLTAGE" -> when {
                value < 11.5 -> "Puede causar falsos DTC electricos, no-start y fallas de relay."
                value < 12.4 -> "Bordeline; revisar sistema de carga."
                else -> "Voltaje dentro de rango operativo."
            }
            "ECT" -> when {
                value > 110.0 -> "Sobrecalentamiento; riesgo de dano al motor."
                value < 70.0 -> "Motor frio; lecturas pueden no ser representativas."
                else -> "Temperatura operativa normal."
            }
            else -> "Sin impacto especifico predefinido para este PID."
        }
    }
}

@Serializable
data class PidComparison(
    val pid: String,
    val label: String,
    val actual: Double?,
    val actualUnit: String,
    val expected: String,
    val rangeSource: RangeSource,
    val status: String,
    val impact: String
)

/**
 * Freeze-frame engine: stores and compares PIDs against expected ranges.
 * Knows about the P0230 use case (battery low → low-voltage warning).
 */
class FreezeFrameEngine {

    fun buildComparison(
        snapshot: FreezeFrameSnapshot,
        pid: String,
        label: String,
        range: ExpectedRange
    ): PidComparison = snapshot.compareTo(pid, label, range)

    /**
     * Build a P0230-style comparison for a battery voltage reading.
     * If voltage is < 12.4V with data quality REAL, returns BAJO.
     */
    fun batteryComparison(
        snapshot: FreezeFrameSnapshot
    ): PidComparison {
        val genericKoeo = ExpectedRange(
            min = 12.4, max = 12.7, unit = "V",
            source = RangeSource.GENERIC_SAFE_RANGE,
            notes = "Generic KOEO range; not OEM-specific."
        )
        return snapshot.compareTo("BATTERY_VOLTAGE", "Battery Voltage", genericKoeo)
    }
}
