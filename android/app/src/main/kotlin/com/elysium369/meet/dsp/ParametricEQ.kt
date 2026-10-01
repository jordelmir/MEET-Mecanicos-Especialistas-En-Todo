package com.elysium369.meet.dsp

import kotlin.math.pow

/**
 * 10-band parametric equalizer.
 * Standard ISO frequency bands: 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz.
 * Each band is a peaking (bell) filter with configurable frequency, Q, and gain.
 */
class ParametricEQ {

    data class EQBand(
        val index: Int,
        val frequencyHz: Double,
        val Q: Double = 1.0,
        var gainDb: Double = 0.0
    ) {
        init {
            require(gainDb in -12.0..12.0) { "gainDb must be in [-12, 12], was $gainDb" }
        }
    }

    private val bands = mutableListOf<EQBand>()
    private val filters = mutableListOf<BiquadFilter>()
    private var sampleRate: Int = 48_000

    // Output gain to prevent clipping after EQ boost
    private var outputGain: Float = 1.0f

    init {
        // Standard 10-band ISO frequencies
        val frequencies = doubleArrayOf(
            31.0, 62.0, 125.0, 250.0, 500.0,
            1000.0, 2000.0, 4000.0, 8000.0, 16000.0
        )
        frequencies.forEachIndexed { index, freq ->
            bands.add(EQBand(index, freq))
            filters.add(BiquadFilter())
        }
    }

    fun configure(sampleRate: Int) {
        this.sampleRate = sampleRate
        filters.forEachIndexed { index, filter ->
            filter.configure(
                type = BiquadFilter.Type.PEAKING,
                sampleRate = sampleRate,
                frequencyHz = bands[index].frequencyHz,
                Q = bands[index].Q,
                gainDb = bands[index].gainDb
            )
        }
        updateOutputGain()
    }

    fun setBandGain(bandIndex: Int, gainDb: Double) {
        require(bandIndex in bands.indices) { "Band index $bandIndex out of range [0, ${bands.size - 1}]" }
        val clampedGain = gainDb.coerceIn(-12.0, 12.0)
        bands[bandIndex] = bands[bandIndex].copy(gainDb = clampedGain)
        filters[bandIndex].setGainDb(clampedGain)
        updateOutputGain()
    }

    fun getBandGain(bandIndex: Int): Double {
        require(bandIndex in bands.indices)
        return bands[bandIndex].gainDb
    }

    fun setBandFrequency(bandIndex: Int, frequencyHz: Double) {
        require(bandIndex in bands.indices)
        bands[bandIndex] = bands[bandIndex].copy(frequencyHz = frequencyHz)
        filters[bandIndex].setFrequency(frequencyHz)
    }

    fun getBands(): List<EQBand> = bands.toList()

    fun reset() {
        bands.forEachIndexed { index, band ->
            bands[index] = band.copy(gainDb = 0.0)
            filters[index].setGainDb(0.0)
            filters[index].reset()
        }
        outputGain = 1.0f
    }

    /**
     * Get the output gain factor to prevent clipping.
     * Call this after setting all band gains to get the correct compensation.
     */
    fun getOutputGain(): Float = outputGain

    /**
     * Process a mono buffer through all EQ bands (in-place).
     */
    fun process(buffer: FloatArray, frameCount: Int) {
        for (i in 0 until frameCount) {
            var sample = buffer[i].toDouble()
            for (filter in filters) {
                sample = filter.process(sample)
            }
            buffer[i] = (sample * outputGain).toFloat()
        }
    }

    /**
     * Process interleaved stereo buffer through all EQ bands (in-place).
     * Each channel has independent filter state — no cross-channel contamination.
     */
    fun processStereo(buffer: FloatArray, frameCount: Int) {
        for (i in 0 until frameCount) {
            var left = buffer[i * 2].toDouble()
            var right = buffer[i * 2 + 1].toDouble()

            for (filter in filters) {
                left = filter.process(left, 0)  // Channel 0 = Left
                right = filter.process(right, 1) // Channel 1 = Right
            }

            buffer[i * 2] = (left * outputGain).toFloat()
            buffer[i * 2 + 1] = (right * outputGain).toFloat()
        }
    }

    /**
     * Calculate output gain to prevent clipping when bands are boosted.
     * Estimates worst-case gain from all bands.
     */
    private fun updateOutputGain() {
        var maxBoost = 0.0
        for (band in bands) {
            if (band.gainDb > maxBoost) {
                maxBoost = band.gainDb
            }
        }

        // Convert dB to linear, add headroom
        // For a 10-band EQ with max +12dB boost, we need to reduce output
        outputGain = if (maxBoost > 0.0) {
            val linearGain = 10.0.pow(-maxBoost / 20.0)
            (linearGain * 0.9f).toFloat() // 10% headroom
        } else {
            1.0f
        }
    }

    /**
     * Apply a preset EQ curve.
     */
    fun applyPreset(preset: EQPreset) {
        reset()
        preset.bands.forEach { (index, gainDb) ->
            if (index in bands.indices) {
                setBandGain(index, gainDb)
            }
        }
    }

    enum class EQPreset(val bands: Map<Int, Double>) {
        FLAT(emptyMap()),
        BASS_BOOST(mapOf(0 to 6.0, 1 to 4.0, 2 to 2.0)),
        TREBLE_BOOST(mapOf(7 to 4.0, 8 to 6.0, 9 to 4.0)),
        VOCAL(mapOf(2 to 2.0, 3 to 4.0, 4 to 4.0, 5 to 2.0)),
        ROCK(mapOf(0 to 4.0, 1 to 2.0, 4 to -2.0, 7 to 2.0, 8 to 4.0)),
        JAZZ(mapOf(2 to 2.0, 3 to 4.0, 5 to 2.0, 7 to 2.0)),
        ELECTRONIC(mapOf(0 to 6.0, 1 to 4.0, 4 to -2.0, 7 to 2.0, 8 to 4.0, 9 to 2.0)),
        CLASSICAL(mapOf(3 to 2.0, 4 to 2.0, 5 to 2.0, 7 to 2.0)),
        CUSTOM(emptyMap())
    }
}