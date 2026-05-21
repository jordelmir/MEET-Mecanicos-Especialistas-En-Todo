package com.elysium369.meet.core.obd

/**
 * ReferenceSignalDatabase — Formas de onda de referencia "normal" para comparación visual.
 * Permite superponer la señal de referencia sobre la señal real en el osciloscopio.
 */
object ReferenceSignalDatabase {

    data class ReferenceSignal(
        val pidKey: String,
        val name: String,
        val description: String,
        val normalMin: Float,
        val normalMax: Float,
        val unit: String,
        val waveformPattern: WaveformPattern,
        val frequency: String,
        val expectedBehavior: String,
        val abnormalIndicators: List<String>,
        val samplePoints: List<Float> // Normalized 0-1 reference waveform
    )

    enum class WaveformPattern {
        SINE,        // O2 sensors
        SQUARE,      // Injectors, ignition
        RAMP,        // TPS smooth
        SAWTOOTH,    // CKP/CMP
        FLAT,        // Stable sensors (coolant, baro)
        PULSED,      // MAF during acceleration
        OSCILLATING  // Normal variation (fuel trims)
    }

    val signals = mapOf(
        "O2_B1S1" to ReferenceSignal(
            "O2_B1S1", "Sensor O2 B1S1 (Pre-Cat)", 
            "El sensor O2 pre-catalizador debe oscilar entre 0.1V y 0.9V a ~1-2Hz en lazo cerrado.",
            0.1f, 0.9f, "V", WaveformPattern.SINE, "1-2 Hz",
            "Oscilación constante entre rico y pobre cada 0.5-1 segundo",
            listOf("Señal plana en 0.45V = sensor perezoso/muerto",
                "Siempre > 0.7V = mezcla rica constante",
                "Siempre < 0.2V = mezcla pobre constante",
                "Oscilación lenta (>3s) = sensor degradado"),
            // 20 puntos de referencia de una onda sinusoidal normalizada
            listOf(0.5f, 0.65f, 0.8f, 0.9f, 0.85f, 0.7f, 0.5f, 0.3f, 0.15f, 0.1f,
                0.15f, 0.3f, 0.5f, 0.65f, 0.8f, 0.9f, 0.85f, 0.7f, 0.5f, 0.3f)
        ),
        "O2_B1S2" to ReferenceSignal(
            "O2_B1S2", "Sensor O2 B1S2 (Post-Cat)",
            "Post-catalizador debe ser estable ~0.45V. Oscilación = catalizador agotado.",
            0.3f, 0.6f, "V", WaveformPattern.FLAT, "~0 Hz (estable)",
            "Línea casi plana entre 0.4-0.6V",
            listOf("Oscila como B1S1 = catalizador ineficiente (P0420)",
                "< 0.1V constante = sensor en corto o circuito abierto"),
            listOf(0.45f, 0.46f, 0.44f, 0.47f, 0.45f, 0.43f, 0.46f, 0.45f, 0.44f, 0.46f,
                0.45f, 0.47f, 0.44f, 0.45f, 0.46f, 0.44f, 0.45f, 0.47f, 0.45f, 0.44f)
        ),
        "MAF" to ReferenceSignal(
            "MAF", "Sensor MAF (Flujo de Aire)",
            "Responde proporcionalmente al acelerador. Ralentí: 2-7 g/s. WOT: 80-250 g/s.",
            2f, 250f, "g/s", WaveformPattern.PULSED, "Variable",
            "Sube suavemente con aceleración, baja con desaceleración",
            listOf("Valor fijo = sensor contaminado o desconectado",
                "Picos erráticos = fuga de aire post-MAF",
                "Valor bajo constante = filtro de aire tapado"),
            listOf(0.1f, 0.1f, 0.12f, 0.15f, 0.3f, 0.5f, 0.7f, 0.85f, 0.9f, 0.88f,
                0.7f, 0.5f, 0.3f, 0.15f, 0.12f, 0.1f, 0.1f, 0.1f, 0.12f, 0.1f)
        ),
        "TPS" to ReferenceSignal(
            "TPS", "Sensor TPS (Posición Acelerador)",
            "Debe subir linealmente de 0% a 100% sin saltos ni zonas muertas.",
            0f, 100f, "%", WaveformPattern.RAMP, "Proporcional al pie",
            "Rampa suave sin escalones ni caídas abruptas",
            listOf("Saltos abruptos = pista resistiva desgastada",
                "Zona muerta = punto ciego del sensor",
                "No regresa a ~0% = cable pegado o resorte roto"),
            listOf(0f, 0.05f, 0.1f, 0.15f, 0.25f, 0.35f, 0.5f, 0.65f, 0.8f, 0.9f,
                1.0f, 0.95f, 0.85f, 0.7f, 0.55f, 0.4f, 0.25f, 0.15f, 0.05f, 0f)
        ),
        "MAP" to ReferenceSignal(
            "MAP", "Sensor MAP (Presión Múltiple)",
            "Ralentí: 25-35 kPa. WOT: ~95-101 kPa. Turbo: >101 kPa.",
            20f, 250f, "kPa", WaveformPattern.PULSED, "Variable con carga",
            "Baja en ralentí, sube con aceleración",
            listOf("Igual a presión barométrica en ralentí = fuga de vacío grande",
                "Valor errático = sensor o manguera dañada",
                "No cambia con aceleración = sensor desconectado"),
            listOf(0.3f, 0.3f, 0.28f, 0.32f, 0.5f, 0.7f, 0.85f, 0.95f, 0.98f, 0.95f,
                0.8f, 0.6f, 0.4f, 0.32f, 0.3f, 0.3f, 0.28f, 0.3f, 0.3f, 0.3f)
        ),
        "RPM" to ReferenceSignal(
            "RPM", "RPM del Motor",
            "Ralentí: 600-900 RPM (gasolina). Estable ±50 RPM.",
            0f, 8000f, "rpm", WaveformPattern.FLAT, "Estable en ralentí",
            "Línea estable en ralentí, sube suavemente con aceleración",
            listOf("Fluctuación >100 RPM en ralentí = falla de vacío o inyector",
                "Hunting (sube y baja rítmicamente) = problema IAC/MAF",
                "RPM no baja al soltar acelerador = cable pegado"),
            listOf(0.1f, 0.1f, 0.1f, 0.11f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f,
                0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f)
        ),
        "COOLANT" to ReferenceSignal(
            "COOLANT", "Temperatura Refrigerante",
            "Debe subir de ambiente a 85-95°C y estabilizarse. Termostato abre a ~82°C.",
            -40f, 130f, "°C", WaveformPattern.RAMP, "Gradual",
            "Subida gradual hasta 85-95°C, luego estable",
            listOf("No llega a 80°C = termostato pegado abierto",
                "> 105°C = sobrecalentamiento (bomba, ventilador, termostato)",
                "Fluctúa 10+°C = aire en el sistema de enfriamiento"),
            listOf(0.2f, 0.25f, 0.3f, 0.35f, 0.4f, 0.45f, 0.5f, 0.55f, 0.6f, 0.65f,
                0.68f, 0.7f, 0.7f, 0.7f, 0.7f, 0.7f, 0.7f, 0.7f, 0.7f, 0.7f)
        ),
        "FUEL_TRIM" to ReferenceSignal(
            "FUEL_TRIM", "Fuel Trims (STFT/LTFT)",
            "STFT oscila ±5%. LTFT debe estar entre ±10%. >±25% = falla.",
            -25f, 25f, "%", WaveformPattern.OSCILLATING, "Variable",
            "Pequeñas oscilaciones alrededor de 0%",
            listOf("> +15% = fuga de vacío, inyector sucio, o sensor MAF sucio",
                "< -15% = inyector con fuga, regulador de presión fallando",
                "Ambos bancos positivos = problema compartido (MAF, filtro aire)",
                "Solo un banco = problema específico de ese banco"),
            listOf(0.5f, 0.52f, 0.48f, 0.53f, 0.47f, 0.51f, 0.49f, 0.52f, 0.48f, 0.5f,
                0.51f, 0.49f, 0.52f, 0.48f, 0.5f, 0.53f, 0.47f, 0.51f, 0.49f, 0.5f)
        )
    )

    fun getReference(pidKey: String): ReferenceSignal? = signals[pidKey]

    fun getAllKeys(): List<String> = signals.keys.toList()

    /**
     * Calcula el % de coincidencia entre la señal real y la referencia.
     * Retorna 0-100 donde 100 = señal perfectamente normal.
     */
    fun calculateMatchPercent(pidKey: String, realValues: List<Float>): Float? {
        val ref = signals[pidKey] ?: return null
        if (realValues.isEmpty()) return null

        val normalizedReal = realValues.map { v ->
            ((v - ref.normalMin) / (ref.normalMax - ref.normalMin)).coerceIn(0f, 1f)
        }
        // Resample reference to match real data size
        val resampledRef = resample(ref.samplePoints, normalizedReal.size)

        var totalError = 0f
        for (i in normalizedReal.indices) {
            val diff = kotlin.math.abs(normalizedReal[i] - resampledRef[i])
            totalError += diff
        }
        val avgError = totalError / normalizedReal.size
        return ((1f - avgError) * 100f).coerceIn(0f, 100f)
    }

    private fun resample(source: List<Float>, targetSize: Int): List<Float> {
        if (targetSize <= 0) return emptyList()
        if (source.isEmpty()) return List(targetSize) { 0f }
        return List(targetSize) { i ->
            val srcIdx = i.toFloat() / targetSize * source.size
            val lo = srcIdx.toInt().coerceIn(0, source.size - 1)
            val hi = (lo + 1).coerceIn(0, source.size - 1)
            val frac = srcIdx - lo
            source[lo] * (1 - frac) + source[hi] * frac
        }
    }
}
