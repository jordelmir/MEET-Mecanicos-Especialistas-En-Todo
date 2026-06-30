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
import java.util.Locale
import kotlin.math.roundToInt

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

    // Thread-safe central cooldown tracker for warning and critical alerts
    private val alertCooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // Configurable thresholds
    var maxTempThreshold = 105f
    var minVoltEncendidoThreshold = 13.2f
    var minVoltApagadoThreshold = 11.8f
    var maxRpmThreshold = 5500f

    fun processLiveData(data: Map<String, Float>, isEngineRunning: Boolean) {
        val now = System.currentTimeMillis()

        // Temperature Alert
        data["0105"]?.let { temp ->
            val lastAlert = alertCooldowns["TEMP_ENGINE"] ?: 0L
            if (temp >= maxTempThreshold && (now - lastAlert > 60000L)) {
                triggerAlert("Sobrecalentamiento", "Temp Motor: ${temp.roundToInt()}°C", AlertSeverity.CRITICAL)
                alertCooldowns["TEMP_ENGINE"] = now
            }
        }
        
        // Voltage Alert
        data["AT RV"]?.let { volt ->
            val minVolt = if (isEngineRunning) minVoltEncendidoThreshold else minVoltApagadoThreshold
            val lastAlert = alertCooldowns["VOLT_ALERT"] ?: 0L
            if (volt < minVolt && (now - lastAlert > 60000L)) {
                triggerAlert("Voltaje Bajo", "Batería: ${String.format(Locale.US, "%.1f", volt)}V", AlertSeverity.WARNING)
                alertCooldowns["VOLT_ALERT"] = now
            }
        }

        // RPM Alert
        data["010C"]?.let { rpm ->
            val lastAlert = alertCooldowns["RPM_ENGINE"] ?: 0L
            if (rpm >= maxRpmThreshold && (now - lastAlert > 10000L)) {
                triggerAlert("RPM Elevado", "Motor a ${rpm.toInt()} RPM", AlertSeverity.WARNING)
                alertCooldowns["RPM_ENGINE"] = now
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
        scope.launch {
            liveDataFlow.collect { data ->
                val now = System.currentTimeMillis()
                
                // Temperature Alert
                data["0105"]?.let { temp ->
                    val lastAlert = alertCooldowns["TEMP_ENGINE"] ?: 0L
                    if (temp > maxTempThreshold && (now - lastAlert > 60000L)) {
                        triggerAlert("Sobrecalentamiento", "Temp Motor: ${temp.roundToInt()}°C", AlertSeverity.CRITICAL)
                        alertCooldowns["TEMP_ENGINE"] = now
                    }
                }
                
                // RPM Alert
                data["010C"]?.let { rpm ->
                    val lastAlert = alertCooldowns["RPM_ENGINE"] ?: 0L
                    if (rpm > maxRpmThreshold && (now - lastAlert > 10000L)) {
                        triggerAlert("RPM Elevado", "Motor a ${rpm.toInt()} RPM", AlertSeverity.WARNING)
                        alertCooldowns["RPM_ENGINE"] = now
                    }
                }

                // Battery Voltage Alert
                val volt = data["0142"] ?: data["AT RV"]
                volt?.let { v ->
                    val rpm = data["010C"] ?: 0f
                    val isEngineRunning = rpm > 400f
                    val minVolt = if (isEngineRunning) minVoltEncendidoThreshold else minVoltApagadoThreshold
                    val lastAlert = alertCooldowns["VOLT_ALERT"] ?: 0L
                    if (v < minVolt && (now - lastAlert > 60000L)) {
                        triggerAlert("Voltaje Bajo", "Batería: ${String.format(Locale.US, "%.1f", v)}V", AlertSeverity.WARNING)
                        alertCooldowns["VOLT_ALERT"] = now
                    }
                }
            }
        }
    }
}
