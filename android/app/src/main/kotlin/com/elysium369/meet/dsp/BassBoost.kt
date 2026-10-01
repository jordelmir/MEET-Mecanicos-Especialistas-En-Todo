package com.elysium369.meet.dsp

import kotlin.math.pow

/**
 * Bass boost processor using low shelf filter.
 * Unlike Android's LoudnessEnhancer (which just increases gain),
 * this applies a frequency-dependent boost below a cutoff frequency.
 *
 * Uses a Linkwitz-Riley-style low shelf for natural bass enhancement.
 */
class BassBoost {

    private val lowShelfFilter = BiquadFilter()
    private var sampleRate: Int = 48_000
    private var boostDb: Float = 0.0f
    private var cutoffHz: Double = 150.0
    private var enabled: Boolean = true

    // Output gain to prevent clipping
    private var outputGain: Float = 1.0f

    fun configure(sampleRate: Int) {
        this.sampleRate = sampleRate
        recomputeFilter()
    }

    fun setBoost(boostDb: Float) {
        val clamped = boostDb.coerceIn(0.0f, 12.0f)
        if (this.boostDb != clamped) {
            this.boostDb = clamped
            recomputeFilter()
        }
    }

    fun getBoost(): Float = boostDb

    fun setCutoffFrequency(hz: Double) {
        val clamped = hz.coerceIn(20.0, 500.0)
        if (cutoffHz != clamped) {
            cutoffHz = clamped
            recomputeFilter()
        }
    }

    fun getCutoffFrequency(): Double = cutoffHz

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) {
            lowShelfFilter.reset()
        }
    }

    fun isEnabled(): Boolean = enabled

    fun reset() {
        lowShelfFilter.reset()
        boostDb = 0.0f
        outputGain = 1.0f
        recomputeFilter()
    }

    /**
     * Get output gain factor to prevent clipping.
     */
    fun getOutputGain(): Float = outputGain

    /**
     * Process a mono buffer through bass boost (in-place).
     */
    fun process(buffer: FloatArray, frameCount: Int) {
        if (!enabled || boostDb == 0.0f) return

        lowShelfFilter.process(buffer, frameCount)

        // Apply output gain to prevent clipping
        if (outputGain < 1.0f) {
            for (i in 0 until frameCount) {
                buffer[i] *= outputGain
            }
        }
    }

    /**
     * Process interleaved stereo buffer through bass boost (in-place).
     */
    fun processStereo(buffer: FloatArray, frameCount: Int) {
        if (!enabled || boostDb == 0.0f) return

        lowShelfFilter.processStereo(buffer, frameCount)

        // Apply output gain to prevent clipping
        if (outputGain < 1.0f) {
            for (i in 0 until frameCount * 2) {
                buffer[i] *= outputGain
            }
        }
    }

    private fun recomputeFilter() {
        lowShelfFilter.configure(
            type = BiquadFilter.Type.LOW_SHELF,
            sampleRate = sampleRate,
            frequencyHz = cutoffHz,
            Q = 0.707, // Butterworth response
            gainDb = boostDb.toDouble()
        )

        // Calculate output gain to prevent clipping
        // For low shelf, worst case is at DC (lowest frequency)
        outputGain = if (boostDb > 0.0f) {
            val linearGain = 10.0.pow(boostDb / 20.0).toFloat()
            // Reduce output to compensate for bass boost
            1.0f / (linearGain * 0.8f) // 20% headroom
        } else {
            1.0f
        }
    }
}