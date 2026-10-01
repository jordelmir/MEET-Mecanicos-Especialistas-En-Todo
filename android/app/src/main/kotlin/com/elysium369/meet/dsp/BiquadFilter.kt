package com.elysium369.meet.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Biquad filter coefficients — shared across channels.
 * Based on Audio EQ Cookbook (Robert Bristow-Johnson).
 *
 * Transfer function: H(z) = (b0 + b1*z^-1 + b2*z^-2) / (a0 + a1*z^-1 + a2*z^-2)
 */
data class BiquadCoefficients(
    val b0: Double = 1.0,
    val b1: Double = 0.0,
    val b2: Double = 0.0,
    val a1: Double = 0.0,
    val a2: Double = 0.0
) {
    companion object {
        fun compute(
            type: BiquadFilter.Type,
            sampleRate: Int,
            frequencyHz: Double,
            Q: Double = 0.707,
            gainDb: Double = 0.0
        ): BiquadCoefficients {
            val A = if (gainDb != 0.0) pow10(gainDb / 40.0) else 1.0
            val w0 = 2.0 * PI * frequencyHz / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * Q)

            var b0 = 1.0; var b1 = 0.0; var b2 = 0.0
            var a1 = 0.0; var a2 = 0.0

            when (type) {
                BiquadFilter.Type.LOW_PASS -> {
                    val norm = 1.0 / (1.0 + alpha)
                    b0 = (1.0 - cosW0) / 2.0 * norm
                    b1 = (1.0 - cosW0) * norm
                    b2 = b0
                    a1 = -2.0 * cosW0 * norm
                    a2 = (1.0 - alpha) * norm
                }
                BiquadFilter.Type.HIGH_PASS -> {
                    val norm = 1.0 / (1.0 + alpha)
                    b0 = (1.0 + cosW0) / 2.0 * norm
                    b1 = -(1.0 + cosW0) * norm
                    b2 = b0
                    a1 = -2.0 * cosW0 * norm
                    a2 = (1.0 - alpha) * norm
                }
                BiquadFilter.Type.BAND_PASS -> {
                    val norm = 1.0 / (1.0 + alpha)
                    b0 = alpha * norm
                    b1 = 0.0
                    b2 = -alpha * norm
                    a1 = -2.0 * cosW0 * norm
                    a2 = (1.0 - alpha) * norm
                }
                BiquadFilter.Type.NOTCH -> {
                    val norm = 1.0 / (1.0 + alpha)
                    b0 = 1.0 * norm
                    b1 = -2.0 * cosW0 * norm
                    b2 = 1.0 * norm
                    a1 = -2.0 * cosW0 * norm
                    a2 = (1.0 - alpha) * norm
                }
                BiquadFilter.Type.ALL_PASS -> {
                    val norm = 1.0 / (1.0 + alpha)
                    b0 = (1.0 - alpha) * norm
                    b1 = -2.0 * cosW0 * norm
                    b2 = (1.0 + alpha) * norm
                    a1 = -2.0 * cosW0 * norm
                    a2 = (1.0 - alpha) * norm
                }
                BiquadFilter.Type.PEAKING -> {
                    val norm = 1.0 / (1.0 + alpha / A)
                    b0 = (1.0 + alpha * A) * norm
                    b1 = -2.0 * cosW0 * norm
                    b2 = (1.0 - alpha * A) * norm
                    a1 = -2.0 * cosW0 * norm
                    a2 = (1.0 - alpha / A) * norm
                }
                BiquadFilter.Type.LOW_SHELF -> {
                    val sqrtA = sqrt(A)
                    val twoSqrtAAlpha = 2.0 * sqrtA * alpha
                    val norm = 1.0 / (1.0 + twoSqrtAAlpha + A)
                    b0 = A * ((A + 1.0) - (A - 1.0) * cosW0 + twoSqrtAAlpha) * norm
                    b1 = 2.0 * A * ((A - 1.0) - (A + 1.0) * cosW0) * norm
                    b2 = A * ((A + 1.0) - (A - 1.0) * cosW0 - twoSqrtAAlpha) * norm
                    a1 = -2.0 * ((A - 1.0) + (A + 1.0) * cosW0) * norm
                    a2 = ((A + 1.0) + (A - 1.0) * cosW0 - twoSqrtAAlpha) * norm
                }
                BiquadFilter.Type.HIGH_SHELF -> {
                    val sqrtA = sqrt(A)
                    val twoSqrtAAlpha = 2.0 * sqrtA * alpha
                    val norm = 1.0 / (1.0 + twoSqrtAAlpha + A)
                    b0 = A * ((A + 1.0) + (A - 1.0) * cosW0 + twoSqrtAAlpha) * norm
                    b1 = -2.0 * A * ((A - 1.0) + (A + 1.0) * cosW0) * norm
                    b2 = A * ((A + 1.0) + (A - 1.0) * cosW0 - twoSqrtAAlpha) * norm
                    a1 = 2.0 * ((A - 1.0) - (A + 1.0) * cosW0) * norm
                    a2 = ((A + 1.0) - (A - 1.0) * cosW0 - twoSqrtAAlpha) * norm
                }
            }

            return BiquadCoefficients(b0, b1, b2, a1, a2)
        }

        private fun pow10(x: Double): Double = kotlin.math.exp(x * 2.302585093)
    }
}

/**
 * Per-channel filter state — Direct Form II Transposed.
 */
data class BiquadState(
    var z1: Double = 0.0,
    var z2: Double = 0.0
) {
    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }
}

/**
 * BiquadFilter — supports multi-channel with independent state per channel.
 *
 * Architecture:
 *   Coefficients (shared) + State per channel (independent)
 *
 * This prevents cross-channel contamination in stereo/multi-channel processing.
 */
class BiquadFilter {

    enum class Type {
        LOW_PASS, HIGH_PASS, BAND_PASS, NOTCH, ALL_PASS, PEAKING, LOW_SHELF, HIGH_SHELF
    }

    var type: Type = Type.PEAKING
        private set
    var frequencyHz: Double = 1000.0
        private set
    var Q: Double = 0.707
        private set
    var gainDb: Double = 0.0
        private set
    var sampleRate: Int = 48_000
        private set

    // Shared coefficients
    var coefficients: BiquadCoefficients = BiquadCoefficients()
        private set

    // Per-channel state
    private val channelStates = mutableMapOf<Int, BiquadState>()

    fun getState(channel: Int): BiquadState {
        return channelStates.getOrPut(channel) { BiquadState() }
    }

    fun configure(
        type: Type,
        sampleRate: Int,
        frequencyHz: Double,
        Q: Double = 0.707,
        gainDb: Double = 0.0
    ) {
        this.type = type
        this.sampleRate = sampleRate
        this.frequencyHz = frequencyHz
        this.Q = Q
        this.gainDb = gainDb
        recomputeCoefficients()
    }

    fun setGainDb(gainDb: Double) {
        if (this.gainDb != gainDb) {
            this.gainDb = gainDb
            recomputeCoefficients()
        }
    }

    fun setFrequency(frequencyHz: Double) {
        if (this.frequencyHz != frequencyHz) {
            this.frequencyHz = frequencyHz
            recomputeCoefficients()
        }
    }

    fun reset() {
        channelStates.values.forEach { it.reset() }
    }

    fun resetChannel(channel: Int) {
        getState(channel).reset()
    }

    /**
     * Process a single sample through the filter for a specific channel.
     */
    fun process(input: Double, channel: Int = 0): Double {
        val state = getState(channel)
        val c = coefficients
        val output = c.b0 * input + state.z1
        state.z1 = c.b1 * input - c.a1 * output + state.z2
        state.z2 = c.b2 * input - c.a2 * output
        return output
    }

    /**
     * Process a mono buffer (in-place).
     */
    fun process(buffer: FloatArray, frameCount: Int, channel: Int = 0) {
        for (i in 0 until frameCount) {
            buffer[i] = process(buffer[i].toDouble(), channel).toFloat()
        }
    }

    /**
     * Process interleaved stereo buffer with independent L/R state.
     */
    fun processStereo(buffer: FloatArray, frameCount: Int) {
        for (i in 0 until frameCount) {
            val left = process(buffer[i * 2].toDouble(), 0).toFloat()
            val right = process(buffer[i * 2 + 1].toDouble(), 1).toFloat()
            buffer[i * 2] = left
            buffer[i * 2 + 1] = right
        }
    }

    /**
     * Process multi-channel interleaved buffer.
     */
    fun processMultiChannel(buffer: FloatArray, frameCount: Int, channelCount: Int) {
        for (i in 0 until frameCount) {
            for (ch in 0 until channelCount) {
                val idx = i * channelCount + ch
                buffer[idx] = process(buffer[idx].toDouble(), ch).toFloat()
            }
        }
    }

    private fun recomputeCoefficients() {
        coefficients = BiquadCoefficients.compute(type, sampleRate, frequencyHz, Q, gainDb)
    }
}