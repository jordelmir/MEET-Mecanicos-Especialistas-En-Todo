package com.elysium369.meet.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * FFT (Fast Fourier Transform) processor.
 * Radix-2 Cooley-Tukey implementation for power-of-2 sizes.
 *
 * Used for frequency response measurement and spectrum analysis.
 */
class FFT(private var size: Int = 1024) {

    // Pre-computed twiddle factors
    private var cosTable: DoubleArray = DoubleArray(size)
    private var sinTable: DoubleArray = DoubleArray(size)

    // Bit-reversal permutation table
    private var bitReverseTable: IntArray = IntArray(size)

    init {
        computeTables()
    }

    fun setSize(size: Int) {
        require(size > 0 && (size and (size - 1)) == 0) { "Size must be power of 2" }
        this.size = size
        computeTables()
    }

    fun getSize(): Int = size

    /**
     * Compute forward FFT (time domain → frequency domain).
     * Input: real samples [Real0, Real1, ..., RealN-1]
     * Output: complex [Re0, Im0, Re1, Im1, ..., ReN/2, ImN/2]
     */
    fun forward(real: DoubleArray, imag: DoubleArray) {
        require(real.size >= size && imag.size >= size) { "Arrays must be at least size $size" }

        // Bit-reversal permutation
        for (i in 0 until size) {
            val j = bitReverseTable[i]
            if (i < j) {
                val tempR = real[i]
                val tempI = imag[i]
                real[i] = real[j]
                imag[i] = imag[j]
                real[j] = tempR
                imag[j] = tempI
            }
        }

        // FFT butterfly operations
        var step = 1
        var halfStep = step shr 1

        while (step < size) {
            for (k in 0 until step) {
                val angle = -PI * k / step
                val wR = cos(angle)
                val wI = sin(angle)

                var i = k
                while (i < size) {
                    val j = i + step
                    val tR = wR * real[j] - wI * imag[j]
                    val tI = wR * imag[j] + wI * real[j]

                    real[j] = real[i] - tR
                    imag[j] = imag[i] - tI
                    real[i] = real[i] + tR
                    imag[i] = imag[i] + tI

                    i += step shl 1
                }
            }
            step = step shl 1
            halfStep = step shr 1
        }
    }

    /**
     * Compute inverse FFT (frequency domain → time domain).
     */
    fun inverse(real: DoubleArray, imag: DoubleArray) {
        require(real.size >= size && imag.size >= size) { "Arrays must be at least size $size" }

        // Conjugate and scale
        for (i in 0 until size) {
            imag[i] = -imag[i]
        }

        // Forward FFT
        forward(real, imag)

        // Conjugate and scale
        val scale = 1.0 / size
        for (i in 0 until size) {
            real[i] *= scale
            imag[i] = -imag[i] * scale
        }
    }

    /**
     * Compute magnitude spectrum from complex FFT output.
     * Returns magnitudes in dB scale.
     */
    fun magnitudeSpectrum(real: DoubleArray, imag: DoubleArray): DoubleArray {
        val magnitudes = DoubleArray(size / 2)
        for (i in 0 until size / 2) {
            val re = real[i]
            val im = imag[i]
            val mag = sqrt(re * re + im * im)
            // Convert to dB, avoid log(0)
            magnitudes[i] = if (mag > 1e-10) 20.0 * ln(mag) / ln(10.0) else -200.0
        }
        return magnitudes
    }

    /**
     * Compute power spectrum (magnitude squared).
     */
    fun powerSpectrum(real: DoubleArray, imag: DoubleArray): DoubleArray {
        val power = DoubleArray(size / 2)
        for (i in 0 until size / 2) {
            val re = real[i]
            val im = imag[i]
            power[i] = re * re + im * im
        }
        return power
    }

    /**
     * Compute frequency bin to Hz mapping.
     */
    fun binToFrequency(sampleRate: Int, bin: Int): Double {
        return bin.toDouble() * sampleRate / size
    }

    /**
     * Apply Hanning window to input samples.
     */
    fun hanningWindow(samples: DoubleArray) {
        for (i in samples.indices) {
            val window = 0.5 * (1.0 - cos(2.0 * PI * i / (samples.size - 1)))
            samples[i] *= window
        }
    }

    /**
     * Apply Hamming window to input samples.
     */
    fun hammingWindow(samples: DoubleArray) {
        for (i in samples.indices) {
            val window = 0.54 - 0.46 * cos(2.0 * PI * i / (samples.size - 1))
            samples[i] *= window
        }
    }

    private fun computeTables() {
        // Bit-reversal permutation
        val bits = log2(size)
        for (i in 0 until size) {
            var rev = 0
            var temp = i
            for (j in 0 until bits) {
                rev = (rev shl 1) or (temp and 1)
                temp = temp shr 1
            }
            bitReverseTable[i] = rev
        }
    }

    private fun log2(n: Int): Int {
        var value = n
        var result = 0
        while (value > 1) {
            value = value shr 1
            result++
        }
        return result
    }
}