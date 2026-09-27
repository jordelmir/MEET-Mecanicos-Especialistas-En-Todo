package com.elysium369.meet.dsp

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Audio limiter — prevents clipping by enforcing a ceiling on sample amplitude.
 *
 * Supports both mono and stereo (interleaved) processing.
 */
class Limiter(
    private val ceiling: Float = 1.0f
) {
    var clippedSamples: Int = 0
        private set
    var peak: Float = 0.0f
        private set
    var rms: Float = 0.0f
        private set

    fun reset() {
        clippedSamples = 0
        peak = 0.0f
        rms = 0.0f
    }

    /**
     * Process a mono buffer (in-place).
     */
    fun process(buffer: FloatArray, frameCount: Int): FloatArray {
        var sumSquares = 0.0f
        var maxPeak = 0.0f
        var clips = 0

        for (i in 0 until frameCount) {
            val sample = buffer[i]
            val absSample = abs(sample)

            if (absSample > ceiling) {
                buffer[i] = if (sample > 0) ceiling else -ceiling
                clips++
            }

            val absAfter = abs(buffer[i])
            if (absAfter > maxPeak) maxPeak = absAfter
            sumSquares += buffer[i] * buffer[i]
        }

        clippedSamples += clips
        peak = maxPeak
        rms = sqrt(sumSquares / frameCount)

        return buffer
    }

    /**
     * Process interleaved stereo buffer (in-place).
     * Buffer layout: [L0, R0, L1, R1, ...]
     */
    fun processStereo(buffer: FloatArray, frameCount: Int) {
        var sumSquares = 0.0f
        var maxPeak = 0.0f
        var clips = 0

        for (i in 0 until frameCount) {
            // Process left channel
            val leftIdx = i * 2
            val leftSample = buffer[leftIdx]
            val leftAbs = abs(leftSample)
            if (leftAbs > ceiling) {
                buffer[leftIdx] = if (leftSample > 0) ceiling else -ceiling
                clips++
            }

            // Process right channel
            val rightIdx = i * 2 + 1
            val rightSample = buffer[rightIdx]
            val rightAbs = abs(rightSample)
            if (rightAbs > ceiling) {
                buffer[rightIdx] = if (rightSample > 0) ceiling else -ceiling
                clips++
            }

            // Track peak across both channels
            val leftAfter = abs(buffer[leftIdx])
            val rightAfter = abs(buffer[rightIdx])
            if (leftAfter > maxPeak) maxPeak = leftAfter
            if (rightAfter > maxPeak) maxPeak = rightAfter

            sumSquares += buffer[leftIdx] * buffer[leftIdx]
            sumSquares += buffer[rightIdx] * buffer[rightIdx]
        }

        clippedSamples += clips
        peak = maxPeak
        rms = sqrt(sumSquares / (frameCount * 2))
    }

    fun getCrestFactor(): Float {
        return if (rms > 0.0f) peak / rms else 0.0f
    }
}