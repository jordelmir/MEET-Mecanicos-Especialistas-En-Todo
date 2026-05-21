package com.elysium369.meet.core.obd

import javax.inject.Inject
import javax.inject.Singleton

/**
 * AlertThresholdEngine — Motor de alertas en tiempo real con umbrales configurables.
 * Monitorea PIDs críticos y dispara alertas cuando salen de rango.
 */
@Singleton
class AlertThresholdEngine @Inject constructor() {

    enum class AlertLevel { WARNING, CRITICAL }

    data class ThresholdAlert(
        val pidKey: String,
        val label: String,
        val currentValue: Float,
        val threshold: Float,
        val unit: String,
        val message: String,
        val level: AlertLevel,
        val icon: String,
        val timestampMs: Long = System.currentTimeMillis()
    )

    data class ThresholdRule(
        val pidKey: String,
        val label: String,
        val unit: String,
        val minWarning: Float?,
        val maxWarning: Float?,
        val minCritical: Float?,
        val maxCritical: Float?,
        val icon: String,
        val messageTemplate: String
    )

    private val alertHistory = mutableListOf<ThresholdAlert>()
    private val cooldownMap = mutableMapOf<String, Long>()
    private val COOLDOWN_MS = 10_000L // No repetir la misma alerta en 10s

    // Reglas por defecto (el usuario puede personalizar)
    val defaultRules = listOf(
        ThresholdRule("COOLANT", "Temperatura Motor", "°C",
            null, 100f, null, 110f, "🌡️",
            "Temperatura del motor a %.0f°C"),
        ThresholdRule("RPM", "RPM Motor", "rpm",
            400f, 6000f, null, 7000f, "🔄",
            "RPM a %.0f"),
        ThresholdRule("VOLTAGE", "Voltaje Batería", "V",
            12.0f, 15.0f, 11.0f, 16.0f, "🔋",
            "Voltaje batería a %.1fV"),
        ThresholdRule("SPEED", "Velocidad", "km/h",
            null, 160f, null, 200f, "🚗",
            "Velocidad a %.0f km/h"),
        ThresholdRule("LTFT_B1", "Fuel Trim Largo", "%",
            -15f, 15f, -25f, 25f, "⛽",
            "LTFT B1 a %.1f%%"),
        ThresholdRule("STFT_B1", "Fuel Trim Corto", "%",
            -20f, 20f, -30f, 30f, "⛽",
            "STFT B1 a %.1f%%"),
        ThresholdRule("MAF", "Flujo MAF", "g/s",
            null, null, null, 250f, "💨",
            "Flujo MAF a %.1f g/s"),
        ThresholdRule("LOAD", "Carga Motor", "%",
            null, 85f, null, 95f, "📊",
            "Carga motor a %.0f%%"),
        ThresholdRule("IAT", "Temp Admisión", "°C",
            null, 50f, null, 65f, "🌬️",
            "Temp admisión a %.0f°C"),
        ThresholdRule("FUEL_LEVEL", "Nivel Combustible", "%",
            10f, null, 5f, null, "⛽",
            "Nivel combustible a %.0f%%")
    )

    private var activeRules: List<ThresholdRule> = defaultRules

    fun setRules(rules: List<ThresholdRule>) { activeRules = rules }

    /**
     * Evalúa datos en vivo contra los umbrales.
     * Retorna lista de alertas nuevas (respeta cooldown).
     */
    fun evaluate(liveData: Map<String, Float>): List<ThresholdAlert> {
        val newAlerts = mutableListOf<ThresholdAlert>()
        val now = System.currentTimeMillis()

        for (rule in activeRules) {
            val value = liveData[rule.pidKey] ?: continue
            val lastAlert = cooldownMap[rule.pidKey] ?: 0L
            if (now - lastAlert < COOLDOWN_MS) continue

            val alert = checkRule(rule, value)
            if (alert != null) {
                newAlerts.add(alert)
                alertHistory.add(alert)
                cooldownMap[rule.pidKey] = now
            }
        }
        return newAlerts
    }

    private fun checkRule(rule: ThresholdRule, value: Float): ThresholdAlert? {
        // Check critical first
        rule.minCritical?.let { min ->
            if (value < min) return ThresholdAlert(
                rule.pidKey, rule.label, value, min, rule.unit,
                "🚨 CRÍTICO: ${String.format(rule.messageTemplate, value)} — Bajo mínimo crítico ($min${rule.unit})",
                AlertLevel.CRITICAL, rule.icon
            )
        }
        rule.maxCritical?.let { max ->
            if (value > max) return ThresholdAlert(
                rule.pidKey, rule.label, value, max, rule.unit,
                "🚨 CRÍTICO: ${String.format(rule.messageTemplate, value)} — Sobre máximo crítico ($max${rule.unit})",
                AlertLevel.CRITICAL, rule.icon
            )
        }
        // Check warning
        rule.minWarning?.let { min ->
            if (value < min) return ThresholdAlert(
                rule.pidKey, rule.label, value, min, rule.unit,
                "⚠️ ${String.format(rule.messageTemplate, value)} — Bajo mínimo ($min${rule.unit})",
                AlertLevel.WARNING, rule.icon
            )
        }
        rule.maxWarning?.let { max ->
            if (value > max) return ThresholdAlert(
                rule.pidKey, rule.label, value, max, rule.unit,
                "⚠️ ${String.format(rule.messageTemplate, value)} — Sobre máximo ($max${rule.unit})",
                AlertLevel.WARNING, rule.icon
            )
        }
        return null
    }

    fun getAlertHistory(): List<ThresholdAlert> = alertHistory.toList()
    fun clearHistory() { alertHistory.clear(); cooldownMap.clear() }
}
