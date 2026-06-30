package com.elysium369.meet.core.obd

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * PrePurchaseInspection — Motor de inspección pre-compra automática.
 * Evalúa 8 dimensiones y genera un "Score de Confianza" 0-100.
 */
@Singleton
class PrePurchaseInspection @Inject constructor() {

    enum class Verdict { APPROVED, CAUTION, REJECT }

    data class InspectionCategory(
        val name: String,
        val icon: String,
        val score: Int,
        val maxScore: Int,
        val findings: List<String>,
        val severity: DiagnosticSeverity
    )

    data class InspectionResult(
        val overallScore: Int,
        val verdict: Verdict,
        val verdictText: String,
        val verdictExplanation: String,
        val categories: List<InspectionCategory>,
        val redFlags: List<String>,
        val recommendations: List<String>
    )

    fun runInspection(
        activeDtcs: List<String>,
        pendingDtcs: List<String>,
        permanentDtcs: List<String>,
        readinessMonitors: Map<String, Boolean>,
        liveData: Map<String, Float>,
        freezeFrame: Map<String, String>?,
        mode06Results: List<Mode06TestResult>?
    ): InspectionResult {
        val categories = mutableListOf<InspectionCategory>()
        val redFlags = mutableListOf<String>()

        // ── 1. DTCs Activos (25 pts) ──
        val dtcScore = when {
            activeDtcs.isEmpty() -> 25
            activeDtcs.size <= 2 -> 15
            activeDtcs.size <= 5 -> 8
            else -> 0
        }
        val dtcFindings = mutableListOf<String>()
        if (activeDtcs.isNotEmpty()) {
            dtcFindings.add("⚠️ ${activeDtcs.size} DTC(s) activos: ${activeDtcs.joinToString(", ")}")
            if (activeDtcs.any { it.startsWith("P0") && it.substring(1, 3).toIntOrNull()?.let { n -> n in 300..312 } == true })
                redFlags.add("🔴 MISFIRES DETECTADOS — Problema potencial de motor grave")
            if (activeDtcs.any { it.startsWith("P0420") || it.startsWith("P0430") })
                redFlags.add("🔴 CATALIZADOR DAÑADO — Reparación costosa ($500-$2000+)")
        } else dtcFindings.add("✅ Sin códigos de falla activos")
        categories.add(InspectionCategory("DTCs Activos", "🔍", dtcScore, 25, dtcFindings,
            if (dtcScore >= 20) DiagnosticSeverity.INFO else DiagnosticSeverity.HIGH))

        // ── 2. DTCs Permanentes (15 pts) ──
        val permScore = if (permanentDtcs.isEmpty()) 15 else 0
        val permFindings = if (permanentDtcs.isNotEmpty()) {
            redFlags.add("🔴 DTCs PERMANENTES — No se pueden borrar, indican falla no reparada")
            listOf("❌ ${permanentDtcs.size} DTC(s) permanentes: ${permanentDtcs.joinToString(", ")}")
        } else listOf("✅ Sin códigos permanentes")
        categories.add(InspectionCategory("DTCs Permanentes", "🛡️", permScore, 15, permFindings,
            if (permScore > 0) DiagnosticSeverity.INFO else DiagnosticSeverity.CRITICAL))

        // ── 3. Monitores de Emisiones (15 pts) ──
        val totalMon = readinessMonitors.size.coerceAtLeast(1)
        val completeMon = readinessMonitors.count { it.value }
        val incompleteMon = totalMon - completeMon
        val monScore = when {
            incompleteMon == 0 -> 15
            incompleteMon <= 2 -> 10
            incompleteMon <= 4 -> 5
            else -> 0
        }
        val monFindings = mutableListOf<String>()
        if (incompleteMon > 2 && activeDtcs.isEmpty()) {
            redFlags.add("🟡 SOSPECHOSO: DTCs borrados recientemente — ${incompleteMon} monitores incompletos sin fallas activas")
            monFindings.add("⚠️ Posible borrado reciente de DTCs para ocultar fallas")
        }
        monFindings.add("$completeMon/$totalMon monitores completos")
        categories.add(InspectionCategory("Monitores Emisiones", "📡", monScore, 15, monFindings,
            if (monScore >= 10) DiagnosticSeverity.INFO else DiagnosticSeverity.MODERATE))

        // ── 4. Fuel Trims (15 pts) ──
        val stft = liveData["STFT_B1"] ?: liveData["stft_b1"] ?: liveData["0106"]
        val ltft = liveData["LTFT_B1"] ?: liveData["ltft_b1"] ?: liveData["0107"]
        val ftScore = when {
            stft == null && ltft == null -> 10
            ltft != null && abs(ltft) > 20 -> 0
            ltft != null && abs(ltft) > 10 -> 5
            stft != null && abs(stft) > 25 -> 5
            else -> 15
        }
        val ftFindings = mutableListOf<String>()
        if (ltft != null && abs(ltft) > 15) {
            redFlags.add("🟡 FUEL TRIM EXCESIVO (${ltft.roundToInt()}%) — Posible fuga de vacío, inyectores sucios, o sensor MAF contaminado")
            ftFindings.add("⚠️ LTFT: ${String.format(Locale.US, "%.1f", ltft)}% (normal: ±10%)")
        } else if (ltft != null) ftFindings.add("✅ LTFT: ${String.format(Locale.US, "%.1f", ltft)}%")
        if (stft != null) ftFindings.add("STFT: ${String.format(Locale.US, "%.1f", stft)}%")
        if (ftFindings.isEmpty()) ftFindings.add("ℹ️ Datos de Fuel Trim no disponibles")
        categories.add(InspectionCategory("Mezcla Combustible", "⛽", ftScore, 15, ftFindings,
            if (ftScore >= 10) DiagnosticSeverity.INFO else DiagnosticSeverity.HIGH))

        // ── 5. Sistema Térmico (10 pts) ──
        val coolant = liveData["COOLANT"] ?: liveData["coolant"] ?: liveData["0105"]
        val thermalScore = when {
            coolant == null -> 7
            coolant > 110 -> 0
            coolant > 105 -> 3
            coolant < 60 -> 5
            else -> 10
        }
        val thermalFindings = mutableListOf<String>()
        if (coolant != null) {
            if (coolant > 105) {
                redFlags.add("🔴 SOBRECALENTAMIENTO (${coolant.roundToInt()}°C) — Posible empaque de cabeza dañado")
                thermalFindings.add("❌ Temp refrigerante: ${coolant.roundToInt()}°C (ALTO)")
            } else thermalFindings.add("✅ Temp refrigerante: ${coolant.roundToInt()}°C")
        } else thermalFindings.add("ℹ️ Sensor de temperatura no leído")
        categories.add(InspectionCategory("Sistema Térmico", "🌡️", thermalScore, 10, thermalFindings,
            if (thermalScore >= 7) DiagnosticSeverity.INFO else DiagnosticSeverity.CRITICAL))

        // ── 6. Sistema Eléctrico (10 pts) ──
        val voltage = liveData["VOLTAGE"] ?: liveData["voltage"] ?: liveData["CTRL_VOLTAGE"] ?: liveData["CALC_VOLTAGE"] ?: liveData["ELM_VOLTAGE"] ?: liveData["0142"]
        val elecScore = when {
            voltage == null -> 7
            voltage < 11.5f -> 2
            voltage < 12.2f -> 5
            voltage > 15.5f -> 3
            else -> 10
        }
        val elecFindings = mutableListOf<String>()
        if (voltage != null) {
            when {
                voltage < 11.5f -> {
                    redFlags.add("🔴 VOLTAJE MUY BAJO (${String.format(Locale.US, "%.1f", voltage)}V) — Batería agotada o alternador fallando")
                    elecFindings.add("❌ Voltaje: ${String.format(Locale.US, "%.1f", voltage)}V")
                }
                voltage > 15.5f -> elecFindings.add("⚠️ Voltaje alto: ${String.format(Locale.US, "%.1f", voltage)}V (regulador?)")
                else -> elecFindings.add("✅ Voltaje: ${String.format(Locale.US, "%.1f", voltage)}V")
            }
        } else elecFindings.add("ℹ️ Voltaje no leído")
        categories.add(InspectionCategory("Sistema Eléctrico", "🔋", elecScore, 10, elecFindings,
            if (elecScore >= 7) DiagnosticSeverity.INFO else DiagnosticSeverity.HIGH))

        // ── 7. DTCs Pendientes (5 pts) ──
        val pendScore = if (pendingDtcs.isEmpty()) 5 else 2
        val pendFindings = if (pendingDtcs.isNotEmpty())
            listOf("⚠️ ${pendingDtcs.size} DTC(s) pendientes — fallas intermitentes detectadas")
        else listOf("✅ Sin códigos pendientes")
        categories.add(InspectionCategory("DTCs Pendientes", "⏳", pendScore, 5, pendFindings,
            if (pendScore >= 4) DiagnosticSeverity.INFO else DiagnosticSeverity.MODERATE))

        // ── 8. Mode 06 Tests (5 pts) ──
        val m06Score = if (mode06Results != null) {
            val failed = mode06Results.count { !it.passed }
            when {
                failed == 0 -> 5
                failed <= 3 -> 3
                else -> 0
            }
        } else 3
        val m06Findings = if (mode06Results != null) {
            val failed = mode06Results.count { !it.passed }
            if (failed > 0) listOf("⚠️ $failed pruebas internas fallidas de ${mode06Results.size}")
            else listOf("✅ Todas las pruebas internas pasaron")
        } else listOf("ℹ️ Mode 06 no disponible")
        categories.add(InspectionCategory("Pruebas Internas ECU", "🧪", m06Score, 5, m06Findings,
            if (m06Score >= 3) DiagnosticSeverity.INFO else DiagnosticSeverity.MODERATE))

        // ── RESULTADO FINAL ──
        val total = categories.sumOf { it.score }
        val verdict = when {
            total >= 80 && redFlags.isEmpty() -> Verdict.APPROVED
            total >= 55 && redFlags.count { it.startsWith("🔴") } == 0 -> Verdict.CAUTION
            else -> Verdict.REJECT
        }
        val verdictText = when (verdict) {
            Verdict.APPROVED -> "✅ APROBADO PARA COMPRA"
            Verdict.CAUTION -> "⚠️ COMPRA CON RESERVAS"
            Verdict.REJECT -> "❌ NO RECOMENDADO"
        }
        val verdictExpl = when (verdict) {
            Verdict.APPROVED -> "El vehículo se encuentra en condiciones aceptables. No se detectaron problemas críticos."
            Verdict.CAUTION -> "Se detectaron algunas anomalías que requieren atención. Negocie el precio o solicite reparación antes de comprar."
            Verdict.REJECT -> "Se detectaron problemas graves. Las reparaciones podrían costar más que el valor del vehículo. Se recomienda NO comprar."
        }
        val recommendations = mutableListOf<String>()
        if (incompleteMon > 2) recommendations.add("Solicite al vendedor completar un ciclo de manejo antes de la compra")
        if (activeDtcs.isNotEmpty()) recommendations.add("Solicite cotización de reparación antes de negociar")
        if (voltage != null && voltage < 12.2f) recommendations.add("Verificar estado de batería y alternador")
        if (redFlags.isEmpty() && total >= 80) recommendations.add("Vehículo en buen estado — proceda con confianza")

        return InspectionResult(total, verdict, verdictText, verdictExpl, categories, redFlags, recommendations)
    }
}
