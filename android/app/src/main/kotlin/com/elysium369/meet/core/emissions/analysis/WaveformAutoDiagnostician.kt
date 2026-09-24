package com.elysium369.meet.core.emissions.analysis

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Clinical operational states for oxygen sensor waveform diagnostics.
 */
enum class WaveformClinicalState {
    HEALTHY_CLOSED_LOOP,        // Active Closed Loop: fast sinusoidal switching (f >= 0.5Hz, Vpp >= 0.55V)
    SLUGGISH_AGED_SENSOR,       // Aged / slow response: 0.15 <= f < 0.5Hz or low slew rate
    BIASED_RICH,                // Pegado en mezcla rica (Vmean > 0.65V, Dwell Rico > 75%, f < 0.2Hz)
    BIASED_LEAN,                // Pegado en mezcla pobre (Vmean < 0.25V, Dwell Pobre > 75%, f < 0.2Hz)
    DEAD_OR_POISONED,           // Sensor plano / envenenado por silicio o refrigerante (Vpp < 0.20V)
    CATALYST_DEPLETED,          // Catalizador agotado: B1S2 copia a B1S1 (isolation ratio > 0.65)
    EXHAUST_LEAK_FALSE_LEAN,    // Fuga de escape: Lambda > 1.10 con Fuel Trims negativos (< -8%)
    SAMPLING_RATE_LIMITED,      // Tasa OBD baja (< 2.0 Hz) en bus ISO/K-Line
    DISCONNECTED                // Sin comunicación física OBD
}

/**
 * Full observability telemetry metrics extracted from the oxygen waveform.
 */
data class WaveformObservabilityMetrics(
    val peakToPeakVolts: Double,
    val centerBiasVolts: Double,
    val switchingFrequencyHz: Double?,
    val richDwellPct: Double,
    val leanDwellPct: Double,
    val slewRateVPerSec: Double?,
    val healthIndexPct: Int,
    val catalyticIsolationRatio: Double?,
    val combinedTrimPct: Double?,
    val lambda: Double?
)

/**
 * Authoritative mathematical auto-diagnosis of waveform behavior.
 */
data class WaveformClinicalDiagnosis(
    val state: WaveformClinicalState,
    val title: String,
    val badgeColorHex: Long,
    val summary: String,
    val technicalExplanation: String,
    val impactOnItv: String,
    val recommendedAction: String,
    val metrics: WaveformObservabilityMetrics
)

/**
 * Mathematical Auto-Diagnostician for Oscilloscope and Exhaust Waveforms.
 * Converts raw voltage-temporal plots into clear, actionable clinical verdicts.
 */
class WaveformAutoDiagnostician {

    fun diagnose(
        upstreamFeatures: OxygenSignalFeatures?,
        downstreamFeatures: OxygenSignalFeatures? = null,
        catalystAssessment: CatalystAssessment? = null,
        stftPct: Double? = null,
        ltftPct: Double? = null,
        lambda: Double? = null,
        isConnected: Boolean = true
    ): WaveformClinicalDiagnosis {
        val combinedTrim = if (stftPct != null || ltftPct != null) {
            (stftPct ?: 0.0) + (ltftPct ?: 0.0)
        } else null

        // 1. Disconnection Guard
        if (!isConnected || upstreamFeatures == null || upstreamFeatures.sampleCount == 0) {
            return WaveformClinicalDiagnosis(
                state = WaveformClinicalState.DISCONNECTED,
                title = "DESCONECTADO / SIN SEÑAL OBD",
                badgeColorHex = 0xFF757575, // Gray
                summary = "Conecta el adaptador OBD2 con el motor en marcha para iniciar la captura de onda.",
                technicalExplanation = "No hay paquetes de telemetría OBD llegando al buffer del osciloscopio.",
                impactOnItv = "No concluyente. Requiere enlace físico con la ECU.",
                recommendedAction = "Verifica la conexión Bluetooth/Wi-Fi del adaptador OBD2.",
                metrics = WaveformObservabilityMetrics(
                    peakToPeakVolts = 0.0,
                    centerBiasVolts = 0.0,
                    switchingFrequencyHz = null,
                    richDwellPct = 0.0,
                    leanDwellPct = 0.0,
                    slewRateVPerSec = null,
                    healthIndexPct = 0,
                    catalyticIsolationRatio = null,
                    combinedTrimPct = null,
                    lambda = null
                )
            )
        }

        val vpp = upstreamFeatures.amplitude ?: 0.0
        val vMean = upstreamFeatures.mean ?: 0.45
        val switchHz = upstreamFeatures.switchHz
        val richDwell = upstreamFeatures.richDwellPct ?: 50.0
        val leanDwell = upstreamFeatures.leanDwellPct ?: 50.0

        // Slew rate estimation: (V90 - V10) / transition time
        val avgTransitionMs = listOfNotNull(upstreamFeatures.richToLeanMs, upstreamFeatures.leanToRichMs).average()
        val slewRate = if (!avgTransitionMs.isNaN() && avgTransitionMs > 0.0) {
            (vpp * 0.8) / (avgTransitionMs / 1000.0)
        } else null

        val isolationRatio = catalystAssessment?.isolationRatio

        // Health Score (0 - 100%)
        val amplitudeScore = min(1.0, vpp / 0.70)
        val frequencyScore = if (switchHz != null) min(1.0, switchHz / 0.75) else 0.40
        val symmetryScore = 1.0 - (abs(richDwell - 50.0) / 50.0).coerceIn(0.0, 1.0)
        val slewScore = if (slewRate != null) min(1.0, slewRate / 2.0) else 0.50

        val rawHealth = (amplitudeScore * 0.35 + frequencyScore * 0.30 + symmetryScore * 0.20 + slewScore * 0.15) * 100.0
        val healthIndex = rawHealth.toInt().coerceIn(5, 100)

        val metrics = WaveformObservabilityMetrics(
            peakToPeakVolts = vpp,
            centerBiasVolts = vMean,
            switchingFrequencyHz = switchHz,
            richDwellPct = richDwell,
            leanDwellPct = leanDwell,
            slewRateVPerSec = slewRate,
            healthIndexPct = healthIndex,
            catalyticIsolationRatio = isolationRatio,
            combinedTrimPct = combinedTrim,
            lambda = lambda
        )

        // 2. Fuga en tubo de escape (Dilución atmosférica)
        if (lambda != null && lambda >= 1.10 && combinedTrim != null && combinedTrim <= -8.0) {
            return WaveformClinicalDiagnosis(
                state = WaveformClinicalState.EXHAUST_LEAK_FALSE_LEAN,
                title = "EVIDENCIA DE FUGA EN ESCAPE",
                badgeColorHex = 0xFFFF9100, // Warning Amber
                summary = "Paradoja de mezcla: El analizador mide mezcla pobre (Lambda ${String.format("%.3f", lambda)}), pero la ECU quita gasolina (Trims ${String.format("%.1f", combinedTrim)}%). Esto indica entrada de aire ambiental al tubo de escape antes del sensor.",
                technicalExplanation = "Dilución atmosférica por succión de aire (efecto Venturi) en fisuras del escape, empaques quemados o unión del flexible.",
                impactOnItv = "Rechazo seguro por Lambda superior al límite legal (1.00 ± 0.07).",
                recommendedAction = "Inspeccionar fuga de gases en uniones del múltiple, tubo flexible y empaque del silenciador.",
                metrics = metrics
            )
        }

        // 3. Bloqueado en rico (Biased Rich)
        if (vMean > 0.65 && richDwell > 70.0 && (switchHz == null || switchHz < 0.25) && upstreamFeatures.sampleCount >= 8) {
            return WaveformClinicalDiagnosis(
                state = WaveformClinicalState.BIASED_RICH,
                title = "SEÑAL BLOQUEADA EN MEZCLA RICA",
                badgeColorHex = 0xFFFF5252, // Coral Red
                summary = "La onda se mantiene anclada en zona alta (> 0.65V) el ${richDwell.toInt()}% del tiempo. El motor inyecta combustible en exceso continuo.",
                technicalExplanation = "Tensión media = ${String.format("%.2f", vMean)}V. Falta de cruces a zona pobre (< 0.45V).",
                impactOnItv = "Disparo severo de CO (> 0.50%) e hidrocarburos (HC). Humo visible.",
                recommendedAction = "Verificar inyector goteando, presión de gasolina excesiva o sensor MAF/MAP sucio.",
                metrics = metrics.copy(healthIndexPct = 25)
            )
        }

        // 4. Bloqueado en pobre (Biased Lean)
        if (vMean < 0.25 && leanDwell > 70.0 && (switchHz == null || switchHz < 0.25) && upstreamFeatures.sampleCount >= 8) {
            return WaveformClinicalDiagnosis(
                state = WaveformClinicalState.BIASED_LEAN,
                title = "SEÑAL BLOQUEADA EN MEZCLA POBRE",
                badgeColorHex = 0xFFFFAB00, // Amber
                summary = "La onda se mantiene anclada en zona baja (< 0.25V) el ${leanDwell.toInt()}% del tiempo. Falta de combustible o entrada de aire no medido.",
                technicalExplanation = "Tensión media = ${String.format("%.2f", vMean)}V con predominio pobre del ${leanDwell.toInt()}%.",
                impactOnItv = "Falta de encendido (misfires) que dispara HC no quemado y eleva Lambda.",
                recommendedAction = "Buscar fugas de vacío en mangueras de admisión; medir presión de la bomba de combustible.",
                metrics = metrics.copy(healthIndexPct = 28)
            )
        }

        // 5. Sensor plano / muerto o envenenado por silicio / refrigerante
        if (vpp < 0.20 && upstreamFeatures.sampleCount >= 8) {
            return WaveformClinicalDiagnosis(
                state = WaveformClinicalState.DEAD_OR_POISONED,
                title = "SENSOR O₂ INACTIVO / ENVENENADO",
                badgeColorHex = 0xFFFF1744, // Bright Red
                summary = "La señal apenas oscila (amplitud = ${String.format("%.2f", vpp)}V). El sensor no responde a las variaciones estequiométricas.",
                technicalExplanation = "Amplitud pico a pico (Vpp < 0.20V). Indica envenenamiento por silicio (silicón RTV inadecuado), plomo o fuga interna de refrigerante.",
                impactOnItv = "Rechazo por emisiones fuera de control: la ECU opera en Open Loop sustitutivo.",
                recommendedAction = "Revisar alimentación y masa del sensor; sustituir sonda lambda upstream B1S1.",
                metrics = metrics.copy(healthIndexPct = 12)
            )
        }

        // 6. Catalizador agotado (B1S2 copiando a B1S1)
        if (isolationRatio != null && isolationRatio > 0.65 && upstreamFeatures.crossCount >= 6) {
            return WaveformClinicalDiagnosis(
                state = WaveformClinicalState.CATALYST_DEPLETED,
                title = "CATALIZADOR DEGRADADO / SIN ALMACENAMIENTO",
                badgeColorHex = 0xFFFF1744, // Red
                summary = "El sensor posterior B1S2 oscila al ritmo del frontal (ratio = ${String.format("%.2f", isolationRatio)}). El monolito perdió su capacidad de convertir gases.",
                technicalExplanation = "Capacidad de retención de oxígeno (OSC) agotada. En buen estado, B1S2 debe ser una línea horizontal estable (~0.65V).",
                impactOnItv = "Fallo garantizado en prueba acelerada (CO > 0.30% y HC > 100 ppm).",
                recommendedAction = "Sustituir catalizador cerámico y verificar que no existan fallos de chispa previos.",
                metrics = metrics
            )
        }

        // 7. Sensor perezoso / envejecido (Sluggish O2)
        if (((switchHz != null && switchHz in 0.15..0.45) || (vpp in 0.25..0.52)) && upstreamFeatures.sampleCount >= 8) {
            return WaveformClinicalDiagnosis(
                state = WaveformClinicalState.SLUGGISH_AGED_SENSOR,
                title = "SENSOR PEREZOSO / RESPUESTA LENTA",
                badgeColorHex = 0xFFFFD600, // Yellow
                summary = "El sensor oscila pero con lentitud (${String.format("%.2f", switchHz ?: 0.0)} Hz). Tarda demasiado en reaccionar a cambios de carga.",
                technicalExplanation = "Tiempo de respuesta entre rico y pobre degradado (> 250 ms) o amplitud reducida (${String.format("%.2f", vpp)}V).",
                impactOnItv = "Puede pasar al ralentí estático pero falla con facilidad al acelerar a 2500 RPM.",
                recommendedAction = "Limpieza de carbonilla o reemplazo preventivo de la sonda lambda B1S1.",
                metrics = metrics.copy(healthIndexPct = max(35, healthIndex))
            )
        }

        // 8. Tasa de muestreo limitada por protocolo (requiere ráfaga)
        if (upstreamFeatures.insufficientSampleRate && upstreamFeatures.sampleCount < 8) {
            return WaveformClinicalDiagnosis(
                state = WaveformClinicalState.SAMPLING_RATE_LIMITED,
                title = "TASA DE MUESTREO LIMITADA (K-LINE)",
                badgeColorHex = 0xFF00E5FF, // Cyan
                summary = "El bus OBD transmite a menos de 2.0 Hz. Usa el botón [RÁFAGA O2 (15s)] para concentrar el bus en alta velocidad.",
                technicalExplanation = "Protocolo ISO 9141-2 / K-Line con sondeo multi-PID lento. No refleja necesariamente fallo del sensor.",
                impactOnItv = "Diagnóstico preliminar pendiente de captura en ráfaga.",
                recommendedAction = "Presiona [RÁFAGA O2 (15s)] para capturar 15 segundos en modo de alta prioridad.",
                metrics = metrics.copy(healthIndexPct = 60)
            )
        }

        // 9. Conmutación Óptima (Healthy Closed Loop)
        return WaveformClinicalDiagnosis(
            state = WaveformClinicalState.HEALTHY_CLOSED_LOOP,
            title = "CONMUTACIÓN ÓPTIMA (LAZO CERRADO ACTIVO)",
            badgeColorHex = 0xFF00FF7F, // Neon Green
            summary = "Oscilación sinusoidal saludable (${String.format("%.2f", switchHz ?: 0.0)} Hz, ${String.format("%.2f", vpp)}V). La ECU ajusta la mezcla con agilidad.",
            technicalExplanation = "Amplitud pico a pico > 0.55V, cruces simétricos de 0.45V y tiempos de dwell balanceados (40-60%).",
            impactOnItv = "Condición ideal para aprobar límites CO, HC y factor Lambda en DEKRA.",
            recommendedAction = "Sistema en óptimo estado de regulación estequiométrica.",
            metrics = metrics.copy(healthIndexPct = max(85, healthIndex))
        )
    }
}
