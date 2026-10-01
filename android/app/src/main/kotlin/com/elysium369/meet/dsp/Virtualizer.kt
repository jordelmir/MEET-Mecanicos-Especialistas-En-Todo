package com.elysium369.meet.dsp

/**
 * Stereo virtualizer using mid-side processing and all-pass filters.
 * Provides stereo widening and spatial enhancement.
 *
 * NOT a full HRTF implementation (that requires head-related transfer functions),
 * but provides meaningful stereo enhancement through:
 * - Mid-side decomposition
 * - All-pass phase manipulation
 * - Crossfeed for headphone optimization
 */
class Virtualizer {

    private var enabled: Boolean = true
    private var width: Float = 0.5f // 0.0 = mono, 1.0 = full wide
    private var crossfeed: Float = 0.0f // 0.0 = none, 1.0 = full crossfeed

    // All-pass filters for phase decorrelation
    private val allpassL1 = BiquadFilter()
    private val allpassL2 = BiquadFilter()
    private val allpassR1 = BiquadFilter()
    private val allpassR2 = BiquadFilter()

    private var sampleRate: Int = 48_000

    fun configure(sampleRate: Int) {
        this.sampleRate = sampleRate

        // Configure all-pass filters at different frequencies for phase decorrelation
        allpassL1.configure(BiquadFilter.Type.ALL_PASS, sampleRate, 800.0, 0.5)
        allpassL2.configure(BiquadFilter.Type.ALL_PASS, sampleRate, 2500.0, 0.5)
        allpassR1.configure(BiquadFilter.Type.ALL_PASS, sampleRate, 1200.0, 0.5)
        allpassR2.configure(BiquadFilter.Type.ALL_PASS, sampleRate, 3500.0, 0.5)
    }

    fun setWidth(width: Float) {
        this.width = width.coerceIn(0.0f, 1.0f)
    }

    fun getWidth(): Float = width

    fun setCrossfeed(crossfeed: Float) {
        this.crossfeed = crossfeed.coerceIn(0.0f, 1.0f)
    }

    fun getCrossfeed(): Float = crossfeed

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) {
            resetFilters()
        }
    }

    fun isEnabled(): Boolean = enabled

    fun reset() {
        resetFilters()
        width = 0.5f
        crossfeed = 0.0f
    }

    private fun resetFilters() {
        allpassL1.reset()
        allpassL2.reset()
        allpassR1.reset()
        allpassR2.reset()
    }

    /**
     * Process interleaved stereo buffer (in-place).
     * Expects buffer layout: [L0, R0, L1, R1, ...]
     */
    fun process(buffer: FloatArray, frameCount: Int) {
        if (!enabled) return

        for (i in 0 until frameCount) {
            val idx = i * 2
            if (idx + 1 >= buffer.size) break

            var left = buffer[idx].toDouble()
            var right = buffer[idx + 1].toDouble()

            // Step 1: Mid-Side decomposition
            // M = (L + R) / 2  (mono sum)
            // S = (L - R) / 2  (stereo difference)
            val mid = (left + right) / 2.0
            val side = (left - right) / 2.0

            // Step 2: Apply width adjustment
            // width = 0.0 → mono (only mid)
            // width = 1.0 → full side
            val adjustedSide = side * width * 2.0

            // Step 3: Recompose L and R
            left = mid + adjustedSide
            right = mid - adjustedSide

            // Step 4: Apply all-pass filters for phase decorrelation
            // This creates the perception of space without actual HRTF
            left = allpassL1.process(left)
            left = allpassL2.process(left)
            right = allpassR1.process(right)
            right = allpassR2.process(right)

            // Step 5: Apply crossfeed (blend a bit of L into R and vice versa)
            if (crossfeed > 0.0f) {
                val cf = crossfeed.toDouble()
                val crossL = left * (1.0 - cf) + right * cf
                val crossR = right * (1.0 - cf) + left * cf
                left = crossL
                right = crossR
            }

            buffer[idx] = left.toFloat()
            buffer[idx + 1] = right.toFloat()
        }

        // Prevent clipping
        preventClipping(buffer, frameCount)
    }

    private fun preventClipping(buffer: FloatArray, frameCount: Int) {
        var maxAbs = 0.0f
        for (i in 0 until frameCount * 2) {
            val abs = kotlin.math.abs(buffer[i])
            if (abs > maxAbs) maxAbs = abs
        }

        if (maxAbs > 1.0f) {
            val scale = 1.0f / maxAbs
            for (i in 0 until frameCount * 2) {
                buffer[i] *= scale
            }
        }
    }
}