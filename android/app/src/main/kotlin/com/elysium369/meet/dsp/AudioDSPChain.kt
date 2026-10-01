package com.elysium369.meet.dsp

import com.elysium369.meet.observability.MeetTelemetry

/**
 * Complete DSP processing chain.
 * Replaces LegacyEffectsEngine with real audio processing.
 *
 * Signal path:
 * Input → BassBoost → ParametricEQ → Virtualizer → Limiter → Output
 *
 * All processing is sample-accurate and handles stereo interleaving.
 */
class AudioDSPChain {

    companion object {
        private const val TAG = "AudioDSPChain"
    }

    val bassBoost = BassBoost()
    val eq = ParametricEQ()
    val virtualizer = Virtualizer()
    val limiter = Limiter(ceiling = 0.95f)

    private var sampleRate: Int = 48_000
    private var configured: Boolean = false

    // Performance metrics
    var processTimeUs: Long = 0
        private set
    var bufferCount: Long = 0
        private set

    fun configure(sampleRate: Int) {
        this.sampleRate = sampleRate
        bassBoost.configure(sampleRate)
        eq.configure(sampleRate)
        virtualizer.configure(sampleRate)
        configured = true
        MeetTelemetry.event("audio_dsp_configured", mapOf("sampleRateHz" to sampleRate))
    }

    /**
     * Process a mono buffer through the complete DSP chain (in-place).
     */
    fun processMono(buffer: FloatArray, frameCount: Int) {
        if (!configured) return

        val startTime = System.nanoTime()

        // Signal path: Bass → EQ → Limiter
        bassBoost.process(buffer, frameCount)
        eq.process(buffer, frameCount)
        limiter.process(buffer, frameCount)

        bufferCount++
        processTimeUs = (System.nanoTime() - startTime) / 1000
    }

    /**
     * Process interleaved stereo buffer through the complete DSP chain (in-place).
     * Buffer layout: [L0, R0, L1, R1, ...]
     */
    fun processStereo(buffer: FloatArray, frameCount: Int) {
        if (!configured) return

        val startTime = System.nanoTime()

        // Signal path: Bass → EQ → Virtualizer → Limiter
        bassBoost.processStereo(buffer, frameCount)
        eq.processStereo(buffer, frameCount)
        virtualizer.process(buffer, frameCount)
        limiter.processStereo(buffer, frameCount)

        bufferCount++
        processTimeUs = (System.nanoTime() - startTime) / 1000
    }

    /**
     * Get the combined output gain from all stages.
     * Use this to adjust the final output level to prevent clipping.
     */
    fun getCombinedOutputGain(): Float {
        val bassGain = bassBoost.getOutputGain()
        val eqGain = eq.getOutputGain()
        return bassGain * eqGain
    }

    fun reset() {
        bassBoost.reset()
        eq.reset()
        virtualizer.reset()
        limiter.reset()
        processTimeUs = 0
        bufferCount = 0
    }

    fun getStats(): DSPStats {
        return DSPStats(
            bassBoostDb = bassBoost.getBoost(),
            eqBands = eq.getBands().map { it.gainDb },
            virtualizerWidth = virtualizer.getWidth(),
            peak = limiter.peak,
            rms = limiter.rms,
            clippedSamples = limiter.clippedSamples,
            processTimeUs = processTimeUs,
            bufferCount = bufferCount
        )
    }
}

data class DSPStats(
    val bassBoostDb: Float,
    val eqBands: List<Double>,
    val virtualizerWidth: Float,
    val peak: Float,
    val rms: Float,
    val clippedSamples: Int,
    val processTimeUs: Long,
    val bufferCount: Long
)