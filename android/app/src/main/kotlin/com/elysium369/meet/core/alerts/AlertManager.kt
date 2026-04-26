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

class AlertManager @Inject constructor(@ApplicationContext private val context: Context) {
    
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
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 200, 100, 200), -1)
            }
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
            }
        }
    }
}
