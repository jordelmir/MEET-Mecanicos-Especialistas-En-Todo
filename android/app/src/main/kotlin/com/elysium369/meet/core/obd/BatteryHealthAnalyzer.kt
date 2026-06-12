package com.elysium369.meet.core.obd

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * BatteryHealthAnalyzer — Análisis del sistema eléctrico en tiempo real.
 * Detecta: batería débil, alternador defectuoso, caída bajo carga, parasitic draw.
 *
 * Features:
 * - Thermal compensation (adjusts thresholds based on Coolant Temp / Intake Air Temp)
 * - Linear SoC interpolation instead of discrete bands
 * - Parasitic-draw detection when engine is off
 * - Thread-safe voltage history via CopyOnWriteArrayList
 * - Multi-PID engine-running detection (RPM + Engine Load fallback)
 */
class BatteryHealthAnalyzer {

    companion object {
        private const val TAG = "BatteryHealthAnalyzer"

        // --- Lead-acid reference at 25 °C ---
        private const val REF_TEMP_C = 25f
        // Voltage offset per °C deviation from reference (≈ −0.004 V/°C for Pb-acid)
        private const val TEMP_COEFF_V_PER_C = -0.004f

        // 100% = 12.73V, 0% = 11.80V at 25 °C (resting, no-load)
        private const val SOC_100_V = 12.73f
        private const val SOC_0_V   = 11.80f
    }

    enum class BatteryVerdict { EXCELLENT, GOOD, DEGRADED, REPLACE, CHARGING_ISSUE }

    data class BatteryReport(
        val currentVoltage: Float,
        val verdict: BatteryVerdict,
        val verdictText: String,
        val verdictColor: String,
        val stateOfCharge: Int,
        val alternatorStatus: String,
        val findings: List<String>,
        val recommendations: List<String>
    )

    // Thread-safe history
    private val voltageHistory = CopyOnWriteArrayList<Float>()
    private var engineRunning = false

    /**
     * [data] map should contain OBD PID keys (hex) → Float values.
     * Recognised keys: 0142/42/VOLTAGE (control module voltage),
     * 010C/RPM, 0104/04/LOAD, 0105/05/COOLANT_TEMP, 010F/0F/INTAKE_AIR_TEMP
     */
    fun analyze(data: Map<String, Float>): BatteryReport {
        val voltage = data["0142"] ?: data["42"] ?: data["VOLTAGE"] ?: 0f
        val rpm = data["010C"] ?: data["RPM"] ?: 0f
        val load = data["0104"] ?: data["04"] ?: data["LOAD"] ?: 0f

        // Robust engine-running detection: RPM primary, Load secondary
        engineRunning = rpm > 400 || (load > 5f && voltage > 13.0f)

        if (voltage > 5f) {
            voltageHistory.add(voltage)
            // Trim to last 200 samples
            while (voltageHistory.size > 200) voltageHistory.removeAt(0)
        }

        val findings = mutableListOf<String>()
        val recs = mutableListOf<String>()

        // ── Thermal Compensation ──────────────────────────────────
        // Use coolant temp (PID 05) or intake air temp (PID 0F) as proxy.
        // If unavailable, assume 25 °C (no compensation).
        val tempC = data["0105"] ?: data["05"] ?: data["COOLANT_TEMP"]
            ?: data["010F"] ?: data["0F"] ?: data["INTAKE_AIR_TEMP"]
            ?: REF_TEMP_C
        val thermalOffset = (tempC - REF_TEMP_C) * TEMP_COEFF_V_PER_C
        // Compensated voltage = what the battery *would* read at 25 °C
        val compensated = voltage - thermalOffset

        if (tempC != REF_TEMP_C && (tempC < 10f || tempC > 40f)) {
            findings.add("🌡️ Compensación térmica aplicada: ${format(tempC)}°C → ajuste ${if (thermalOffset >= 0) "+" else ""}${format(thermalOffset)}V")
        }

        // ── SoC: Linear Interpolation at 25 °C basis ─────────────
        val soc = when {
            !engineRunning -> {
                val pct = ((compensated - SOC_0_V) / (SOC_100_V - SOC_0_V) * 100).toInt()
                pct.coerceIn(0, 100)
            }
            else -> {
                // While charging, SoC estimation is less meaningful; use raw voltage band
                val pct = ((voltage - 12.0f) / (14.4f - 12.0f) * 100).toInt()
                pct.coerceIn(0, 100)
            }
        }

        // ── Alternator analysis (engine running) ─────────────────
        val altStatus = if (engineRunning) {
            when {
                voltage > 15.0f -> { findings.add("⚠️ Sobrecarga: ${format(voltage)}V — Regulador defectuoso"); "SOBRECARGA" }
                voltage > 14.7f -> { findings.add("💡 Voltaje alto: ${format(voltage)}V — Monitorear regulador"); "ALTO" }
                voltage in 13.5f..14.7f -> { findings.add("✅ Alternador cargando correctamente: ${format(voltage)}V"); "NORMAL" }
                voltage in 12.8f..13.5f -> { findings.add("⚠️ Carga baja: ${format(voltage)}V — Alternador débil o banda suelta"); "DÉBIL" }
                else -> { findings.add("❌ Sin carga: ${format(voltage)}V — Alternador no funciona"); "FALLA" }
            }
        } else {
            when {
                compensated >= 12.6f -> { findings.add("✅ Batería en buen estado estático: ${format(voltage)}V"); "MOTOR APAGADO" }
                compensated >= 12.3f -> { findings.add("⚠️ Batería parcialmente descargada: ${format(voltage)}V"); "MOTOR APAGADO" }
                else -> { findings.add("❌ Batería descargada: ${format(voltage)}V"); "MOTOR APAGADO" }
            }
        }

        // ── Voltage stability (detect drops under load) ──────────
        if (voltageHistory.size > 20) {
            val recent = voltageHistory.takeLast(20)
            val min = recent.min()
            val max = recent.max()
            val delta = max - min
            if (delta > 1.5f && engineRunning) {
                findings.add("⚠️ Fluctuación de ${format(delta)}V detectada — Posible conexión suelta o alternador intermitente")
                recs.add("Revisar bornes de batería y conexiones del alternador.")
            }
            if (min < 11.5f && engineRunning) {
                findings.add("❌ Caída a ${format(min)}V bajo carga — Batería o alternador crítico")
                recs.add("Realizar prueba de carga profesional inmediatamente.")
            }
        }

        // ── Parasitic Draw Detection (engine off, voltage trending down) ──
        if (!engineRunning && voltageHistory.size >= 10) {
            val tail = voltageHistory.takeLast(10)
            val head = tail.first()
            val last = tail.last()
            val drift = head - last
            if (drift > 0.3f) {
                // Voltage dropping steadily with engine off — likely parasitic draw
                findings.add("🔋 Posible consumo parásito detectado: −${format(drift)}V en las últimas 10 lecturas con motor apagado")
                recs.add("Desconectar accesorios y realizar prueba de consumo en reposo (< 50mA ideal).")
            }
        }

        // ── Verdict (uses compensated voltage for accuracy) ──────
        val (verdict, text, color) = when {
            !engineRunning && compensated < 12.0f -> Triple(BatteryVerdict.REPLACE, "Batería agotada — Reemplazar", "#FF1744")
            engineRunning && voltage < 12.8f -> Triple(BatteryVerdict.CHARGING_ISSUE, "Problema de carga — Alternador", "#FF6D00")
            engineRunning && voltage > 15.0f -> Triple(BatteryVerdict.CHARGING_ISSUE, "Sobrecarga — Regulador", "#FF6D00")
            compensated < 12.3f -> Triple(BatteryVerdict.DEGRADED, "Batería degradada", "#FFD600")
            compensated < 12.5f -> Triple(BatteryVerdict.GOOD, "Batería aceptable", "#00E676")
            else -> Triple(BatteryVerdict.EXCELLENT, "Sistema eléctrico excelente", "#00E676")
        }

        if (verdict == BatteryVerdict.REPLACE) recs.add("Reemplazar batería lo antes posible.")
        if (verdict == BatteryVerdict.CHARGING_ISSUE) recs.add("Revisar alternador, regulador y bandas.")
        if (recs.isEmpty()) recs.add("✅ Sistema eléctrico funcionando correctamente.")

        return BatteryReport(voltage, verdict, text, color, soc, altStatus, findings, recs)
    }

    private fun format(v: Float) = "%.2f".format(v)
    fun reset() { voltageHistory.clear() }
}
