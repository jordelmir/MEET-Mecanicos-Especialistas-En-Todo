package com.elysium369.meet.core.copilot

import com.elysium369.meet.core.alerts.AlertSeverity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class RuleEngine @Inject constructor(
    private val eventBus: EventBus
) {
    // Keep track of timestamps for sustained state conditions
    private var engineRunningStartTime: Long = 0L
    private var highLoadStartTime: Long = 0L
    private var isHighLoadActive = false
    private var isColdEngineAlertSent = false

    // Cooldown trackers to avoid flooding the EventBus (in ms)
    private val eventCooldowns = mutableMapOf<CopilotEventType, Long>()
    private val COOLDOWN_DURATION = 60_000L // 60 seconds standard event cooldown

    fun evaluate(liveData: Map<String, Float>, activeDtcs: List<String>) {
        val now = System.currentTimeMillis()

        val rpm = liveData["010C"] ?: 0f
        val isEngineRunning = rpm > 450f
        val speed = liveData["010D"] ?: 0f
        val isDriving = speed > 5f

        // Track engine running start time
        if (isEngineRunning) {
            if (engineRunningStartTime == 0L) {
                engineRunningStartTime = now
            }
        } else {
            engineRunningStartTime = 0L
            isColdEngineAlertSent = false
            isHighLoadActive = false
            highLoadStartTime = 0L
        }

        // Rule 1: Voltage
        val voltage = liveData["0142"] ?: liveData["VOLTAGE"] ?: liveData["AT RV"]
        val minVoltLimit = if (isEngineRunning) 13.0f else 12.0f
        if (voltage != null && voltage < minVoltLimit) {
            triggerEvent(
                CopilotEventType.VOLTAJE_BAJO,
                AlertSeverity.WARNING,
                "Voltaje bajo de batería. Voltaje actual de ${String.format("%.1f", voltage)} voltios.",
                "Low battery voltage. Current voltage is ${String.format("%.1f", voltage)} volts."
            )
        }

        // Rule 2: Coolant Temp (Sobrecalentamiento vs Temperatura Anormal)
        val ect = liveData["0105"] ?: liveData["COOLANT"]
        if (ect != null && ect > 105f) {
            triggerEvent(
                CopilotEventType.SOBRECALENTAMIENTO,
                AlertSeverity.CRITICAL,
                "Alerta: Sobrecalentamiento severo del motor detectado. Temperatura a ${ect.toInt()} grados centígrados.",
                "Warning: Severe engine overheating detected. Temperature is ${ect.toInt()} degrees celsius."
            )
        } else if (ect != null && ect > 100f) {
            triggerEvent(
                CopilotEventType.TEMPERATURA_ANORMAL,
                AlertSeverity.WARNING,
                "Temperatura del refrigerante anormalmente elevada a ${ect.toInt()} grados centígrados.",
                "Engine coolant temperature is abnormally high at ${ect.toInt()} degrees celsius."
            )
        }

        // Rule 3: Fuel Trim Excesivo
        val ltft = liveData["0107"] ?: liveData["LTFT_B1"] ?: 0f
        if (abs(ltft) > 15f) {
            val direction = if (ltft > 0) "mezcla pobre" else "mezcla rica"
            val directionEn = if (ltft > 0) "lean mixture" else "rich mixture"
            triggerEvent(
                CopilotEventType.FUEL_TRIM_EXCESIVO,
                AlertSeverity.WARNING,
                "Fuel trim excesivo de ${String.format("%.1f", ltft)} por ciento, indicando probable $direction.",
                "Excessive fuel trim at ${String.format("%.1f", ltft)} percent, indicating probable $directionEn."
            )
        }

        // Rule 4: Misfire (DTC check)
        val hasMisfireDtc = activeDtcs.any { it.startsWith("P030") }
        if (hasMisfireDtc) {
            triggerEvent(
                CopilotEventType.MISFIRE,
                AlertSeverity.CRITICAL,
                "Falla de encendido detectada. Múltiples fallos de combustión registrados en cilindros.",
                "Engine misfire detected. Cylinder combustion failures recorded."
            )
        }

        // Rule 5: Carga Excesiva (Sustained > 90% for 5 seconds)
        val engineLoad = liveData["0104"] ?: liveData["LOAD"] ?: 0f
        if (isEngineRunning && engineLoad > 90f) {
            if (!isHighLoadActive) {
                isHighLoadActive = true
                highLoadStartTime = now
            } else if (now - highLoadStartTime > 5000L) {
                triggerEvent(
                    CopilotEventType.CARGA_EXCESIVA,
                    AlertSeverity.WARNING,
                    "Motor sometido a carga excesiva de más del noventa por ciento de forma sostenida.",
                    "Engine under heavy load exceeding ninety percent for a sustained duration."
                )
            }
        } else {
            isHighLoadActive = false
            highLoadStartTime = 0L
        }

        // Rule 6: Motor Frío (Coolant < 70°C for > 3 minutes while driving)
        if (isEngineRunning && isDriving && ect != null && ect < 70f) {
            val runDuration = now - engineRunningStartTime
            if (runDuration > 180_000L && !isColdEngineAlertSent) {
                isColdEngineAlertSent = true
                triggerEvent(
                    CopilotEventType.MOTOR_FRIO,
                    AlertSeverity.INFO,
                    "El motor lleva más de tres minutos funcionando por debajo de su temperatura óptima de operación.",
                    "The engine has been running below its optimal operating temperature for over three minutes."
                )
            }
        }
    }

    private fun triggerEvent(type: CopilotEventType, severity: AlertSeverity, es: String, en: String) {
        val now = System.currentTimeMillis()
        val lastSent = eventCooldowns[type] ?: 0L
        
        if (now - lastSent > COOLDOWN_DURATION) {
            eventCooldowns[type] = now
            val event = CopilotEvent(
                type = type,
                severity = severity,
                messageEs = es,
                messageEn = en,
                timestamp = now
            )
            eventBus.publish(event)
        }
    }
}
