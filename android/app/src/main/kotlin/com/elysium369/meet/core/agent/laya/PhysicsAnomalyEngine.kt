package com.elysium369.meet.core.agent.laya

/**
 * Real-time physical sensor stream snapshot from OBD-II and mobile device sensors.
 */
data class VehiclePhysicsStream(
    val rpm: Float,
    val speedKmh: Float,
    val coolantTempC: Float,
    val stftPercent: Float = 0.0f, // Short term fuel trim (-25% to +25%)
    val ltftPercent: Float = 0.0f, // Long term fuel trim (-25% to +25%)
    val batteryVoltage: Float = 13.8f,
    val accelGForce: Float = 1.0f, // 1G = earth gravity
    val engineRunDurationSeconds: Long = 60L,
)

/**
 * Early warning predictive anomaly detected before DTC/MIL trigger.
 */
data class PreDtcPrediction(
    val prospectiveDtc: String,
    val component: String,
    val description: String,
    val severity: Int, // 1 to 5
    val estimatedKmUntilMil: Int, // Estimated km until Check Engine illuminates
)

/**
 * Comprehensive physics and mechanical stress assessment result.
 */
data class PhysicsAnomalyAssessment(
    val isSevereCollisionDetected: Boolean,
    val engineStressIndex: Int, // 0 (ideal) to 100 (extreme mechanical abuse)
    val stressCategory: String, // "NORMAL", "MODERATE", "SEVERE_ABUSE"
    val activeWarnings: List<String>,
    val preDtcPredictions: List<PreDtcPrediction>,
    val ecoDrivingScore: Int,   // 0 to 100
    val latencyMs: Long,
)

/**
 * PhysicsAnomalyEngine — Evaluates thermodynamic, electrical, and kinematic sensor data
 * to detect vehicular crashes, mechanical engine abuse, and subtle pre-DTC sensor drifts
 * BEFORE catastrophic failure or Check Engine illumination occurs.
 *
 * Runs 100% local-first on device with sub-millisecond execution time and zero battery impact.
 */
class PhysicsAnomalyEngine(
    private val decisionEngine: LayaDecisionEngine = LayaDecisionEngine(),
) {

    fun evaluate(stream: VehiclePhysicsStream): PhysicsAnomalyAssessment {
        val startEpoch = System.currentTimeMillis()
        val warnings = mutableListOf<String>()
        val predictions = mutableListOf<PreDtcPrediction>()

        var stressPoints = 0

        // 1. Crash & High-G Collision Detection
        // Real crash requires high deceleration force (> 3.8G) AND vehicle motion drop/engine stall
        val isCollision = stream.accelGForce > 3.8f && (stream.speedKmh > 20f || stream.rpm == 0f)
        if (isCollision) {
            warnings.add("¡IMPACTO CINÉTICO SEVERO DETECTADO! (${String.format("%.1f", stream.accelGForce)}G)")
            stressPoints += 100
        }

        // 2. Engine Thermodynamic Stress
        if (stream.coolantTempC < 60f && stream.rpm > 3200f) {
            // Cold engine revving - severe cylinder bore & piston ring wear
            warnings.add("Sobrerrevolución con motor frío (${stream.coolantTempC.toInt()}°C a ${stream.rpm.toInt()} RPM)")
            stressPoints += 40
        } else if (stream.coolantTempC > 108f) {
            // Overheating hazard
            warnings.add("Temperatura crítica de refrigerante: ${stream.coolantTempC.toInt()}°C")
            stressPoints += 60
        } else if (stream.engineRunDurationSeconds > 900L && stream.coolantTempC < 70f) {
            // Thermostat stuck open (takes too long to reach operating temp)
            predictions.add(
                PreDtcPrediction(
                    prospectiveDtc = "P0128",
                    component = "Termostato de Refrigerante",
                    description = "Motor no alcanza temperatura óptima tras 15 min de marcha. Termostato trabado abierto.",
                    severity = 2,
                    estimatedKmUntilMil = 150
                )
            )
            warnings.add("Termostato operando por debajo de temperatura nominal.")
        }

        // 3. Fuel Trim Drift (Pre-P0171 / Pre-P0172 Detection)
        val totalFuelTrim = stream.stftPercent + stream.ltftPercent
        if (totalFuelTrim > 18.0f) {
            // Lean drift: air leak or low fuel pressure
            predictions.add(
                PreDtcPrediction(
                    prospectiveDtc = "P0171",
                    component = "Sistema de Admisión / Bomba Gasolina",
                    description = "Corrección de combustible en +${totalFuelTrim.toInt()}%. Fuga de vacío en admisión detectada antes de encender Check Engine.",
                    severity = 3,
                    estimatedKmUntilMil = 80
                )
            )
            warnings.add("Deriva de mezcla pobre detectada (+${totalFuelTrim.toInt()}% STFT+LTFT).")
            stressPoints += 25
        } else if (totalFuelTrim < -18.0f) {
            // Rich drift: leaking injector or high fuel pressure
            predictions.add(
                PreDtcPrediction(
                    prospectiveDtc = "P0172",
                    component = "Inyectores / MAF",
                    description = "Corrección de combustible en ${totalFuelTrim.toInt()}%. Mezcla rica persistente.",
                    severity = 3,
                    estimatedKmUntilMil = 90
                )
            )
            warnings.add("Deriva de mezcla rica detectada (${totalFuelTrim.toInt()}% STFT+LTFT).")
            stressPoints += 25
        }

        // 4. Electrical Alternator / Battery Health
        if (stream.rpm > 600f && stream.batteryVoltage < 12.8f) {
            // Alternator failing to charge while engine running
            predictions.add(
                PreDtcPrediction(
                    prospectiveDtc = "P0562",
                    component = "Alternador / Regulador de Voltaje",
                    description = "Voltaje del sistema en ${String.format("%.1f", stream.batteryVoltage)}V con motor en marcha. El alternador no está cargando la batería.",
                    severity = 4,
                    estimatedKmUntilMil = 30
                )
            )
            warnings.add("Alternador con bajo voltaje de carga.")
            stressPoints += 30
        } else if (stream.batteryVoltage > 15.2f) {
            warnings.add("Sobretensión en sistema eléctrico (${String.format("%.1f", stream.batteryVoltage)}V). Riesgo para las ECUs.")
            stressPoints += 50
        }

        val finalEsi = stressPoints.coerceIn(0, 100)
        val stressCategory = when {
            finalEsi >= 70 -> "SEVERE_ABUSE"
            finalEsi >= 30 -> "MODERATE"
            else -> "NORMAL"
        }

        // Eco-driving score calculation (penalizes high RPM at low speed and extreme fuel trims)
        val ecoScore = (100 - (finalEsi * 0.7f).toInt() - if (stream.rpm > 3500f && stream.speedKmh < 40f) 20 else 0).coerceIn(10, 100)
        val elapsed = System.currentTimeMillis() - startEpoch

        return PhysicsAnomalyAssessment(
            isSevereCollisionDetected = isCollision,
            engineStressIndex = finalEsi,
            stressCategory = stressCategory,
            activeWarnings = warnings,
            preDtcPredictions = predictions,
            ecoDrivingScore = ecoScore,
            latencyMs = elapsed,
        )
    }
}
