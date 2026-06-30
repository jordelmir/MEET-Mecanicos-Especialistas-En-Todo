package com.elysium369.meet.core.obd

import java.util.Locale

object SmogCheckPredictor {
    enum class SmogVerdict { WILL_PASS, LIKELY_PASS, AT_RISK, WILL_FAIL }

    data class SmogPrediction(
        val verdict: SmogVerdict, val confidencePercent: Int, val verdictText: String,
        val verdictEmoji: String, val monitorsComplete: Int, val monitorsTotal: Int,
        val monitorsIncomplete: List<String>, val blockers: List<String>,
        val warnings: List<String>, val recommendations: List<String>,
        val driveCyclesNeeded: Int?, val estimatedReadyTime: String?
    )

    fun predict(
        activeDtcs: List<String>, pendingDtcs: List<String>, permanentDtcs: List<String>,
        readinessMonitors: Map<String, Boolean>, liveData: Map<String, Float>,
        allowedIncomplete: Int = 1
    ): SmogPrediction {
        val blockers = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val recs = mutableListOf<String>()

        if (activeDtcs.isNotEmpty()) {
            blockers.add("❌ ${activeDtcs.size} DTC(s) activos: ${activeDtcs.joinToString(", ")}")
            recs.add("Reparar TODOS los DTCs activos antes de verificación.")
        }
        if (permanentDtcs.isNotEmpty()) {
            blockers.add("❌ ${permanentDtcs.size} DTC(s) permanentes (no se borran con escáner)")
            recs.add("DTCs permanentes requieren reparación real + ciclos de manejo.")
        }
        if (pendingDtcs.isNotEmpty())
            warnings.add("⚠️ ${pendingDtcs.size} DTC(s) pendientes")

        val total = readinessMonitors.size
        val complete = readinessMonitors.count { it.value }
        val incomplete = readinessMonitors.filter { !it.value }.keys.toList()
        val incCount = total - complete

        if (incCount > allowedIncomplete) {
            blockers.add("❌ $incCount monitores incompletos (máx permitido: $allowedIncomplete)")
            recs.add("Necesitas ~${incCount * 2} ciclos de manejo completos.")
        }

        listOf("STFT1" to (liveData["0106"]), "LTFT1" to (liveData["0107"])).forEach { (n, v) ->
            if (v != null && kotlin.math.abs(v) > 25)
                blockers.add("❌ $n = ${String.format(Locale.US, "%.1f", v)}% — fuera de rango")
            else if (v != null && kotlin.math.abs(v) > 15)
                warnings.add("⚠️ $n = ${String.format(Locale.US, "%.1f", v)}% — elevado")
        }

        val coolant = liveData["0105"]
        if (coolant != null && coolant < 70f)
            warnings.add("⚠️ Motor frío (${String.format(Locale.US, "%.0f", coolant)}°C), monitores no corren hasta >80°C")

        val score = (100 - blockers.size * 30 - warnings.size * 8).coerceIn(0, 100)
        val (verdict, emoji, text) = when {
            blockers.isNotEmpty() -> Triple(SmogVerdict.WILL_FAIL, "🔴", "NO PASARÁ verificación")
            warnings.size >= 3 -> Triple(SmogVerdict.AT_RISK, "🟡", "RIESGO — podría no pasar")
            warnings.isNotEmpty() -> Triple(SmogVerdict.LIKELY_PASS, "🟢", "PROBABLE que pase")
            else -> Triple(SmogVerdict.WILL_PASS, "✅", "PASARÁ sin problemas")
        }

        val cycles = if (incCount > allowedIncomplete) incCount * 2 else null
        val readyTime = cycles?.let { if (it <= 2) "~1 día" else if (it <= 5) "~2-3 días" else "~4-7 días" }

        if (recs.isEmpty() && verdict == SmogVerdict.WILL_PASS)
            recs.add("✅ Listo para verificación vehicular.")

        return SmogPrediction(verdict, score, text, emoji, complete, total, incomplete, blockers, warnings, recs, cycles, readyTime)
    }
}
