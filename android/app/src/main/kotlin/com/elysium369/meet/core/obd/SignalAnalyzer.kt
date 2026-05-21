package com.elysium369.meet.core.obd

import kotlin.math.*

data class SignalMetrics(
    val frequency: Float = 0f,
    val amplitude: Float = 0f,
    val vpp: Float = 0f,
    val rms: Float = 0f,
    val thd: Float = 0f,
    val dutyCycle: Float = 50f,
    val mean: Float = 0f,
    val min: Float = 0f,
    val max: Float = 0f,
    val stability: Float = 100f,
    val noiseLevel: Float = 0f,
    val sampleCount: Int = 0,
    val durationMs: Long = 0
)

data class SignalAnomaly(
    val type: String, // spike, dropout, noise, drift, flatline, overvoltage, undervoltage
    val severity: String, // normal, warning, critical
    val description: String
)

data class SignalDiagnosis(
    val severity: String, // normal, warning, critical
    val metrics: SignalMetrics,
    val anomalies: List<SignalAnomaly>,
    val diagnosisText: String,
    val recommendationText: String,
    val confidence: Int
)

data class PidSignalInfo(
    val code: String,
    val name: String,
    val unit: String,
    val minNominal: Float,
    val maxNominal: Float,
    val category: String
)

object PidSignalRegistry {
    val SIGNALS = listOf(
        PidSignalInfo("010C", "RPM Motor", "RPM", 600f, 7000f, "Motor"),
        PidSignalInfo("0142", "Voltaje Sistema", "V", 12.0f, 14.8f, "Eléctrico"),
        PidSignalInfo("0114", "Sensor O2 B1S1", "V", 0.1f, 0.9f, "Emisiones"),
        PidSignalInfo("010B", "Sensor MAP", "kPa", 20f, 105f, "Motor"),
        PidSignalInfo("0110", "Sensor MAF", "g/s", 2f, 250f, "Combustible"),
        PidSignalInfo("0111", "Posición Acelerador", "%", 0f, 100f, "Motor"),
        PidSignalInfo("0105", "Temp. Refrigerante", "°C", 80f, 105f, "Motor"),
        PidSignalInfo("0106", "Ajuste Comb. Corto", "%", -10f, 10f, "Combustible"),
        PidSignalInfo("010D", "Velocidad", "km/h", 0f, 220f, "Motor"),
        PidSignalInfo("0104", "Carga Motor", "%", 0f, 100f, "Motor"),
    )

    fun findByCode(code: String): PidSignalInfo? = SIGNALS.find { it.code == code }
}

class SignalAnalyzer {

    fun analyze(values: List<Float>, durationMs: Long, signalInfo: PidSignalInfo): SignalDiagnosis {
        if (values.size < 10) return emptyDiagnosis()

        val metrics = calculateMetrics(values, durationMs)
        val anomalies = detectAnomalies(values, signalInfo, metrics)
        val severity = determineSeverity(anomalies, metrics, signalInfo)
        val diagText = generateDiagnosis(signalInfo, metrics, anomalies, severity)
        val recText = generateRecommendation(anomalies, severity)
        val confidence = minOf(100, maxOf(20, values.size / 5))

        return SignalDiagnosis(severity, metrics, anomalies, diagText, recText, confidence)
    }

    private fun calculateMetrics(values: List<Float>, durationMs: Long): SignalMetrics {
        val n = values.size
        val min = values.min()
        val max = values.max()
        val mean = values.average().toFloat()
        val vpp = max - min
        val rms = sqrt(values.map { it * it }.average()).toFloat()

        // Frequency via zero-crossing
        var zeroCrossings = 0
        val centered = values.map { it - mean }
        for (i in 1 until n) {
            if ((centered[i] >= 0 && centered[i - 1] < 0) || (centered[i] < 0 && centered[i - 1] >= 0))
                zeroCrossings++
        }
        val durationSec = durationMs / 1000f
        val frequency = if (durationSec > 0) zeroCrossings / (2f * durationSec) else 0f

        val dutyCycle = (values.count { it > mean }.toFloat() / n) * 100f

        // THD estimate
        val derivs = (1 until n).map { values[it] - values[it - 1] }
        val derivMean = derivs.average().toFloat()
        val derivVar = derivs.map { (it - derivMean).pow(2) }.average().toFloat()
        val normDerivVar = derivVar / (vpp * vpp + 0.001f)
        val thd = minOf(1f, normDerivVar * 10f)

        // Stability
        val windowSize = maxOf(10, n / 10)
        val windowMeans = (0..n - windowSize step windowSize).map { i ->
            values.subList(i, minOf(i + windowSize, n)).average().toFloat()
        }
        val wmAvg = windowMeans.average().toFloat()
        val wmStd = sqrt(windowMeans.map { (it - wmAvg).pow(2) }.average()).toFloat()
        val cv = wmStd / (abs(wmAvg) + 0.001f)
        val stability = maxOf(0f, minOf(100f, 100f - cv * 500f))

        val noiseLevel = if (thd > 0.3f) minOf(1f, thd) else minOf(1f, normDerivVar * 5f)

        return SignalMetrics(frequency, vpp / 2, vpp, rms, thd, dutyCycle, mean, min, max, stability, noiseLevel, n, durationMs)
    }

    private fun detectAnomalies(values: List<Float>, def: PidSignalInfo, metrics: SignalMetrics): List<SignalAnomaly> {
        val anomalies = mutableListOf<SignalAnomaly>()
        val range = def.maxNominal - def.minNominal
        val tolerance = range * 0.25f

        val overCount = values.count { it > def.maxNominal + tolerance }
        if (overCount > values.size * 0.05) anomalies.add(SignalAnomaly("overvoltage",
            if (overCount > values.size * 0.2) "critical" else "warning",
            "$overCount muestras exceden rango nominal superior"))

        val underCount = values.count { it < def.minNominal - tolerance }
        if (underCount > values.size * 0.05) anomalies.add(SignalAnomaly("undervoltage",
            if (underCount > values.size * 0.2) "critical" else "warning",
            "$underCount muestras bajo rango nominal inferior"))

        // Spikes
        var spikes = 0
        val spikeThresh = range * 0.6f
        for (i in 1 until values.size - 1) {
            if (abs(values[i] - values[i - 1]) > spikeThresh && abs(values[i + 1] - values[i]) > spikeThresh) spikes++
        }
        if (spikes > 0) anomalies.add(SignalAnomaly("spike",
            if (spikes > 5) "critical" else "warning",
            "$spikes pico(s) transitorio(s) detectado(s)"))

        // Noise
        if (metrics.noiseLevel > 0.4f) anomalies.add(SignalAnomaly("noise",
            if (metrics.noiseLevel > 0.7f) "critical" else "warning",
            "Ruido excesivo: ${(metrics.noiseLevel * 100).toInt()}%"))

        // Drift
        val q = values.size / 4
        val firstMean = values.take(q).average().toFloat()
        val lastMean = values.takeLast(q).average().toFloat()
        val drift = abs(lastMean - firstMean)
        if (drift > range * 0.15f) anomalies.add(SignalAnomaly("drift",
            if (drift > range * 0.3f) "critical" else "warning",
            "Deriva: ${String.format("%.2f", drift)} ${def.unit}"))

        return anomalies
    }

    private fun determineSeverity(anomalies: List<SignalAnomaly>, metrics: SignalMetrics, def: PidSignalInfo): String {
        if (anomalies.any { it.severity == "critical" }) return "critical"
        if (anomalies.any { it.severity == "warning" }) return "warning"
        if (metrics.stability < 40) return "warning"
        return "normal"
    }

    private fun generateDiagnosis(def: PidSignalInfo, m: SignalMetrics, anomalies: List<SignalAnomaly>, sev: String): String {
        val sb = StringBuilder()
        when (sev) {
            "normal" -> {
                sb.appendLine("✅ SEÑAL NOMINAL — ${def.name}")
                sb.appendLine("Parámetros dentro del rango operativo normal.")
            }
            "warning" -> {
                sb.appendLine("⚠️ ATENCIÓN — ${def.name}")
                anomalies.filter { it.severity == "warning" }.forEach { sb.appendLine("  → ${it.description}") }
            }
            "critical" -> {
                sb.appendLine("🔴 ALERTA CRÍTICA — ${def.name}")
                anomalies.filter { it.severity == "critical" }.forEach { sb.appendLine("  ⛔ ${it.description}") }
                anomalies.filter { it.severity == "warning" }.forEach { sb.appendLine("  → ${it.description}") }
            }
        }
        sb.appendLine()
        sb.append("📊 Freq=${String.format("%.1f", m.frequency)}Hz | RMS=${String.format("%.2f", m.rms)} | THD=${String.format("%.1f", m.thd * 100)}% | Estab=${m.stability.toInt()}%")
        return sb.toString()
    }

    private fun generateRecommendation(anomalies: List<SignalAnomaly>, severity: String): String {
        if (severity == "normal") return "Sin acción requerida. Continúe mantenimiento preventivo."
        val recs = anomalies.mapNotNull { a ->
            when (a.type) {
                "spike" -> "Inspeccionar conexiones y terminales del sensor."
                "dropout" -> "Verificar continuidad del cableado y conector ECU."
                "noise" -> "Revisar blindaje del arnés y masa del chasis."
                "drift" -> "Sensor degradado. Comparar con referencia calibrada."
                "overvoltage" -> "Verificar regulador de voltaje del alternador."
                "undervoltage" -> "Revisar estado de batería y circuito de alimentación."
                else -> null
            }
        }.distinct()
        return recs.joinToString(" ")
    }

    private fun emptyDiagnosis() = SignalDiagnosis(
        "normal", SignalMetrics(), emptyList(),
        "Captura insuficiente. Se requieren al menos 10 muestras.",
        "Inicie una captura de al menos 2 segundos.", 0
    )
}
