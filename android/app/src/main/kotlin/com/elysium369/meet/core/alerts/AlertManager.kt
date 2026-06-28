package com.elysium369.meet.core.alerts

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AlertSeverity { INFO, WARNING, CRITICAL }

data class ObdAlert(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val severity: AlertSeverity,
    val timestamp: Long = System.currentTimeMillis()
)

class AlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceFeedbackManager: com.elysium369.meet.core.audio.VoiceFeedbackManager
) {
    
    private val _alerts = MutableSharedFlow<ObdAlert>(extraBufferCapacity = 10)
    val alerts = _alerts.asSharedFlow()

    // Thresholds configurables
    var maxTempThreshold = 105f
    var minVoltEncendidoThreshold = 13.2f
    var minVoltApagadoThreshold = 11.8f
    var maxRpmThreshold = 5500f

    fun processLiveData(data: Map<String, Float>, isEngineRunning: Boolean) {
        // Temperatura
        data["0105"]?.let { temp ->
            if (temp >= maxTempThreshold) {
                triggerAlert("Sobrecalentamiento", "Temp Motor: ${temp}°C", AlertSeverity.CRITICAL)
            }
        }
        
        // Voltaje (AT RV / custom)
        data["AT RV"]?.let { volt ->
            val minVolt = if (isEngineRunning) minVoltEncendidoThreshold else minVoltApagadoThreshold
            if (volt < minVolt) {
                triggerAlert("Voltaje Bajo", "Batería: ${volt}V", AlertSeverity.WARNING)
            }
        }

        // RPM
        data["010C"]?.let { rpm ->
            if (rpm >= maxRpmThreshold) {
                triggerAlert("RPM Elevado", "Motor a ${rpm.toInt()} RPM", AlertSeverity.WARNING)
            }
        }
    }

    fun triggerNewDtcAlert(dtc: String) {
        triggerAlert("Falla Detectada", "Nuevo código: $dtc", AlertSeverity.CRITICAL)
    }

    private fun triggerAlert(title: String, msg: String, severity: AlertSeverity) {
        if (severity == AlertSeverity.CRITICAL) {
            try {
                val vibrator = context.getSystemService(Vibrator::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 200, 100, 200), -1)
                }
            } catch (e: SecurityException) {
                android.util.Log.w("ElysiumVanguard", "Vibrate permission not granted, skipping haptic feedback", e)
            }
            voiceFeedbackManager.speak("Atención. Alerta crítica de $title. $msg", "Warning. Critical alert: $title. $msg")
        } else if (severity == AlertSeverity.WARNING) {
            voiceFeedbackManager.speak("Advertencia. $title. $msg", "Caution. $title. $msg")
        }
        _alerts.tryEmit(ObdAlert(title = title, message = msg, severity = severity))
    }
    
    fun startMonitoring(liveDataFlow: kotlinx.coroutines.flow.StateFlow<Map<String, Float>>, scope: kotlinx.coroutines.CoroutineScope) {
        val alertCooldown = mutableMapOf<String, Long>()
        scope.launch {
            liveDataFlow.collect { data ->
                val now = System.currentTimeMillis()
                data["0105"]?.let { temp ->
                    val lastTempAlert = alertCooldown["TEMP_ENGINE"] ?: 0L
                    if (temp > maxTempThreshold && (now - lastTempAlert > 60000)) {
                        triggerAlert("Sobrecalentamiento", "Temp Motor: ${temp}°C", AlertSeverity.CRITICAL)
                        alertCooldown["TEMP_ENGINE"] = now
                    }
                }
                
                data["010C"]?.let { rpm ->
                    val lastRpmAlert = alertCooldown["RPM_ENGINE"] ?: 0L
                    if (rpm > maxRpmThreshold && (now - lastRpmAlert > 10000)) {
                        triggerAlert("RPM Elevado", "Motor a ${rpm.toInt()} RPM", AlertSeverity.WARNING)
                        alertCooldown["RPM_ENGINE"] = now
                    }
                }

                // Battery Voltage Alert Check
                val volt = data["0142"] ?: data["AT RV"]
                volt?.let { v ->
                    val rpm = data["010C"] ?: 0f
                    val isEngineRunning = rpm > 400f
                    val minVolt = if (isEngineRunning) minVoltEncendidoThreshold else minVoltApagadoThreshold
                    val lastVoltAlert = alertCooldown["VOLT_ALERT"] ?: 0L
                    if (v < minVolt && (now - lastVoltAlert > 60000)) {
                        triggerAlert("Voltaje Bajo", "Batería: ${"%.1f".format(v)}V", AlertSeverity.WARNING)
                        alertCooldown["VOLT_ALERT"] = now
                    }
                }
            }
        }
    }
}
