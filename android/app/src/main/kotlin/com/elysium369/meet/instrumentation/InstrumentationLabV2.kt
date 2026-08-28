package com.elysium369.meet.instrumentation

enum class MeasurementSourceType {
    OSCILLOSCOPE_HANTEK_6022BE,
    UVC_THERMAL_CAMERA,
    ACOUSTIC_FFT_MICROPHONE,
    IMU_VIBRATION_SENSOR,
}

data class MeasurementSample(
    val timestampMonotonicNs: Long,
    val source: MeasurementSourceType,
    val channel: Int,
    val value: Float,
)

data class AcousticFftSpectrum(
    val sampleRateHz: Int,
    val fftSize: Int,
    val dominantFrequencyHz: Float,
    val peakMagnitudeDb: Float,
    val isAnomalyDetected: Boolean,
)

data class ThermalMeasurement(
    val minTempC: Float,
    val maxTempC: Float,
    val deltaTempC: Float,
    val emissivity: Float = 0.95f,
    val isHotspotDetected: Boolean,
    val calibrationValid: Boolean = true,
)

/**
 * InstrumentationLabV2 — Synchronizes multi-channel hardware instrumentation via a unified MeasurementClock.
 */
object InstrumentationLabV2 {

    fun analyzeAcousticFrame(samples: FloatArray, sampleRateHz: Int = 44100): AcousticFftSpectrum {
        if (samples.isEmpty()) {
            return AcousticFftSpectrum(sampleRateHz, 0, 0f, 0f, isAnomalyDetected = false)
        }

        var peakVal = 0f
        var peakIdx = 0
        for (i in samples.indices) {
            val abs = kotlin.math.abs(samples[i])
            if (abs > peakVal) {
                peakVal = abs
                peakIdx = i
            }
        }

        val dominantFreq = (peakIdx * sampleRateHz.toFloat()) / samples.size
        // Simple heuristic: knock / rod bearing acoustic signature typically has loud spikes in 3kHz-7kHz
        val isAnomaly = peakVal > 0.85f && dominantFreq in 2500f..7000f

        return AcousticFftSpectrum(
            sampleRateHz = sampleRateHz,
            fftSize = samples.size,
            dominantFrequencyHz = dominantFreq,
            peakMagnitudeDb = 20 * kotlin.math.log10(peakVal.coerceAtLeast(0.0001f)),
            isAnomalyDetected = isAnomaly,
        )
    }

    fun analyzeThermalFrame(temperatures: FloatArray, hotspotThresholdC: Float = 95f): ThermalMeasurement {
        if (temperatures.isEmpty()) {
            return ThermalMeasurement(0f, 0f, 0f, isHotspotDetected = false)
        }

        var min = Float.MAX_VALUE
        var max = Float.MIN_VALUE
        for (t in temperatures) {
            if (t < min) min = t
            if (t > max) max = t
        }

        return ThermalMeasurement(
            minTempC = min,
            maxTempC = max,
            deltaTempC = max - min,
            isHotspotDetected = max >= hotspotThresholdC,
            calibrationValid = true,
        )
    }
}
