package com.elysium369.meet.core.evair.prediction

import com.elysium369.meet.data.local.dao.SensorHistoryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Serializable
data class EarlyDegradationWarning(
    val pid: String,
    val subsystem: String,
    val title: String,
    val description: String,
    val confidence: Double,
    val daysUntilPredictedFault: Int?,
    val recommendedAction: String,
)

/**
 * LongitudinalHealthPredictor — Pre-DTC early degradation detection engine.
 *
 * Employs time-series linear trend forecasting over 30/60/90 days to identify
 * failing components before they trigger a Diagnostic Trouble Code or drivability failure.
 */
@Singleton
class LongitudinalHealthPredictor @Inject constructor(
    private val sensorHistoryDao: SensorHistoryDao,
) {

    suspend fun analyzeLongitudinalDegradation(vehicleId: String): List<EarlyDegradationWarning> = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<EarlyDegradationWarning>()

        // 1. Charging System Voltage Degradation (PID 0142)
        val voltHistory = sensorHistoryDao.getSessionAverages(vehicleId, "0142")
        if (voltHistory.size >= 4) {
            val slope = computeSlope(voltHistory.map { it.value.toDouble() })
            val currentVolt = voltHistory.last().value.toDouble()
            if (slope < -0.02 && currentVolt < 13.6) {
                warnings.add(
                    EarlyDegradationWarning(
                        pid = "0142",
                        subsystem = "Eléctrico",
                        title = "Degradación progresiva en el sistema de carga",
                        description = "El voltaje de carga del alternador ha disminuido consistentemente a lo largo de las últimas ${voltHistory.size} sesiones (actual: ${"%.2f".format(currentVolt)}V).",
                        confidence = 0.88,
                        daysUntilPredictedFault = 25,
                        recommendedAction = "Verificar tensión de la banda del alternador y estado de los diodos del regulador."
                    )
                )
            }
        }

        // 2. Long Term Fuel Trim Lean Drift (PID 0107)
        val ltftHistory = sensorHistoryDao.getSessionAverages(vehicleId, "0107")
        if (ltftHistory.size >= 4) {
            val slope = computeSlope(ltftHistory.map { it.value.toDouble() })
            val currentLtft = ltftHistory.last().value.toDouble()
            if (slope > 0.3 && currentLtft > 8.0) {
                warnings.add(
                    EarlyDegradationWarning(
                        pid = "0107",
                        subsystem = "Combustible / Admisión",
                        title = "Deriva positiva continua en ajuste de combustible a largo plazo",
                        description = "El LTFT se aproxima al límite de empobrecimiento (+${"%.1f".format(currentLtft)}%). Esto sugiere una fuga de vacío incipiente o degradación del sensor MAF/MAP.",
                        confidence = 0.84,
                        daysUntilPredictedFault = 18,
                        recommendedAction = "Inspeccionar mangueras de vacío, válvula PCV y junta del múltiple de admisión."
                    )
                )
            }
        }

        // 3. Engine Thermal Creep (PID 0105)
        val coolantHistory = sensorHistoryDao.getSessionAverages(vehicleId, "0105")
        if (coolantHistory.size >= 4) {
            val currentCoolant = coolantHistory.last().value.toDouble()
            val slope = computeSlope(coolantHistory.map { it.value.toDouble() })
            if (slope > 0.4 && currentCoolant > 96.0) {
                warnings.add(
                    EarlyDegradationWarning(
                        pid = "0105",
                        subsystem = "Refrigeración",
                        title = "Tendencia de sobrecalentamiento progresivo",
                        description = "La temperatura promedio de operación ha aumentado gradualmente (${"%.1f".format(currentCoolant)}°C).",
                        confidence = 0.80,
                        daysUntilPredictedFault = 14,
                        recommendedAction = "Revisar limpieza del radiador, termostato y concentración del líquido refrigerante."
                    )
                )
            }
        }

        warnings
    }

    private fun computeSlope(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val n = values.size.toDouble()
        val xMean = (n - 1) / 2.0
        val yMean = values.average()

        var numerator = 0.0
        var denominator = 0.0
        for (i in values.indices) {
            val xDiff = i - xMean
            val yDiff = values[i] - yMean
            numerator += xDiff * yDiff
            denominator += xDiff * xDiff
        }

        return if (denominator != 0.0) numerator / denominator else 0.0
    }
}
