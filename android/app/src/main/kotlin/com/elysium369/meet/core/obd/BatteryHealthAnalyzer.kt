package com.elysium369.meet.core.obd

/**
 * BatteryHealthAnalyzer — Análisis del sistema eléctrico en tiempo real.
 * Detecta: batería débil, alternador defectuoso, caída bajo carga, parasitic draw.
 */
class BatteryHealthAnalyzer {

    enum class BatteryVerdict { EXCELLENT, GOOD, DEGRADED, REPLACE, CHARGING_ISSUE }

    data class BatteryReport(
        val currentVoltage: Float,
        val verdict: BatteryVerdict,
        val verdictText: String,
        val verdictColor: String, // hex
        val stateOfCharge: Int, // 0-100%
        val alternatorStatus: String,
        val findings: List<String>,
        val recommendations: List<String>
    )

    private val voltageHistory = mutableListOf<Float>()
    private var engineRunning = false

    fun analyze(data: Map<String, Float>): BatteryReport {
        val voltage = data["0142"] ?: data["42"] ?: data["VOLTAGE"] ?: 0f
        val rpm = data["010C"] ?: data["RPM"] ?: 0f
        engineRunning = rpm > 400

        if (voltage > 5f) voltageHistory.add(voltage)
        if (voltageHistory.size > 200) voltageHistory.removeAt(0)

        val findings = mutableListOf<String>()
        val recs = mutableListOf<String>()

        // State of Charge estimation (12V lead-acid)
        val soc = when {
            voltage >= 12.7f -> 100
            voltage >= 12.5f -> 75
            voltage >= 12.3f -> 50
            voltage >= 12.1f -> 25
            else -> 10
        }

        // Alternator analysis (engine running)
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
                voltage >= 12.6f -> { findings.add("✅ Batería en buen estado estático: ${format(voltage)}V"); "MOTOR APAGADO" }
                voltage >= 12.3f -> { findings.add("⚠️ Batería parcialmente descargada: ${format(voltage)}V"); "MOTOR APAGADO" }
                else -> { findings.add("❌ Batería descargada: ${format(voltage)}V"); "MOTOR APAGADO" }
            }
        }

        // Voltage stability (detect drops under load)
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

        // Verdict
        val (verdict, text, color) = when {
            !engineRunning && voltage < 12.0f -> Triple(BatteryVerdict.REPLACE, "Batería agotada — Reemplazar", "#FF1744")
            engineRunning && voltage < 12.8f -> Triple(BatteryVerdict.CHARGING_ISSUE, "Problema de carga — Alternador", "#FF6D00")
            engineRunning && voltage > 15.0f -> Triple(BatteryVerdict.CHARGING_ISSUE, "Sobrecarga — Regulador", "#FF6D00")
            voltage < 12.3f -> Triple(BatteryVerdict.DEGRADED, "Batería degradada", "#FFD600")
            voltage < 12.5f -> Triple(BatteryVerdict.GOOD, "Batería aceptable", "#00E676")
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
