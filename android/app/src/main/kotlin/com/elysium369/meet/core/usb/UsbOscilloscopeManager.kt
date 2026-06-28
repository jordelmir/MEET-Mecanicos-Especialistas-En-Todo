package com.elysium369.meet.core.usb

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class UsbOscilloscopeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val TAG = "UsbOscilloscopeManager"
        const val BUFFER_SIZE = 64 * 1024 // 64KB high speed buffer
        const val RENDER_POINTS = 1000   // Downsampled size for fluid UI
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val driver = Hantek6022BEDriver(usbManager)

    // DSO Parameters
    private val _samplingRate = MutableStateFlow(1_000_000L) // 1 MSa/s default
    val samplingRate = _samplingRate.asStateFlow()

    private val _ch1Gain = MutableStateFlow(1) // Gain multiplier index
    val ch1Gain = _ch1Gain.asStateFlow()

    private val _ch2Gain = MutableStateFlow(1)
    val ch2Gain = _ch2Gain.asStateFlow()

    private val _ch1Attenuation = MutableStateFlow(1f) // 1x, 10x, 20x for automotive bobinas/injectors
    val ch1Attenuation = _ch1Attenuation.asStateFlow()

    private val _ch2Attenuation = MutableStateFlow(1f)
    val ch2Attenuation = _ch2Attenuation.asStateFlow()

    private val _triggerLevel = MutableStateFlow(0f) // Volts
    val triggerLevel = _triggerLevel.asStateFlow()

    private val _triggerEdgeRising = MutableStateFlow(true)
    val triggerEdgeRising = _triggerEdgeRising.asStateFlow()

    // Data streams
    private val _ch1Data = MutableStateFlow(FloatArray(RENDER_POINTS))
    val ch1Data = _ch1Data.asStateFlow()

    private val _ch2Data = MutableStateFlow(FloatArray(RENDER_POINTS))
    val ch2Data = _ch2Data.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming = _isStreaming.asStateFlow()

    private val _deviceConnected = MutableStateFlow(false)
    val deviceConnected = _deviceConnected.asStateFlow()

    private var captureJob: Job? = null
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun setCh1Attenuation(factor: Float) {
        _ch1Attenuation.value = factor
    }

    fun setCh2Attenuation(factor: Float) {
        _ch2Attenuation.value = factor
    }

    fun setTriggerLevel(volts: Float) {
        _triggerLevel.value = volts
    }

    fun setTriggerEdge(rising: Boolean) {
        _triggerEdgeRising.value = rising
    }

    fun setSamplingRate(hz: Long) {
        _samplingRate.value = hz
        if (driver.isConnected) {
            driver.configureDso(_samplingRate.value, _ch1Gain.value, _ch2Gain.value)
        }
    }

    fun startStreaming() {
        if (_isStreaming.value) return
        _isStreaming.value = true

        val list = usbManager.deviceList
        val hantekDevice = list.values.firstOrNull { driver.isHantekDevice(it) }
        if (hantekDevice != null && driver.connect(hantekDevice)) {
            _deviceConnected.value = true
            driver.configureDso(_samplingRate.value, _ch1Gain.value, _ch2Gain.value)
            driver.startCapture()
            Log.i(TAG, "Successfully started physical DSO capture")
        } else {
            Log.w(TAG, "No physical Hantek DSO available. Capture not started.")
            _isStreaming.value = false
            _deviceConnected.value = false
            _ch1Data.value = FloatArray(RENDER_POINTS)
            _ch2Data.value = FloatArray(RENDER_POINTS)
            return
        }

        restartStream()
    }

    private fun restartStream() {
        captureJob?.cancel()
        captureJob = managerScope.launch {
            runPhysicalCaptureLoop()
        }
    }

    fun stopStreaming() {
        _isStreaming.value = false
        captureJob?.cancel()
        captureJob = null
        if (driver.isConnected) {
            driver.stopCapture()
            driver.disconnect()
        }
        _deviceConnected.value = false
    }

    private suspend fun runPhysicalCaptureLoop() {
        val rawBuffer = ByteArray(BUFFER_SIZE)
        while (coroutineContext.isActive && _isStreaming.value && driver.isConnected) {
            val bytesRead = withContext(Dispatchers.IO) {
                driver.readBulkData(rawBuffer, 100)
            }

            if (bytesRead > 0) {
                processAndDecimateBulkData(rawBuffer, bytesRead)
            } else {
                delay(5)
            }
        }
    }

    private fun processAndDecimateBulkData(raw: ByteArray, size: Int) {
        // Hantek 6022BE interleaves bytes: [Ch1_0][Ch2_0][Ch1_1][Ch2_1]...
        val samplePairsCount = size / 2
        if (samplePairsCount == 0) return

        val atten1 = _ch1Attenuation.value
        val atten2 = _ch2Attenuation.value
        val triggerLevelVolts = _triggerLevel.value
        val isRising = _triggerEdgeRising.value

        // Helper to convert raw Ch1 index to physical volts
        fun getCh1Volts(k: Int): Float {
            val idx = k * 2
            val raw1 = (raw[idx].toInt() and 0xFF) - 128
            return (raw1 / 128f) * 5.0f * atten1
        }

        // Find stabilizing trigger crossing (rising/falling edge)
        var triggerIdx = 0
        val baseDecimation = (samplePairsCount / RENDER_POINTS).coerceAtLeast(1)
        val maxTriggerSearchLimit = (samplePairsCount - RENDER_POINTS * baseDecimation).coerceAtLeast(0)
        val searchLimit = maxTriggerSearchLimit.coerceAtMost(30000) // Scan up to 30k sample pairs

        for (k in 1 until searchLimit) {
            val prev = getCh1Volts(k - 1)
            val curr = getCh1Volts(k)
            if (isRising) {
                if (prev < triggerLevelVolts && curr >= triggerLevelVolts) {
                    triggerIdx = k
                    break
                }
            } else {
                if (prev > triggerLevelVolts && curr <= triggerLevelVolts) {
                    triggerIdx = k
                    break
                }
            }
        }

        val remainingPairs = samplePairsCount - triggerIdx
        val decimationFactor = (remainingPairs / RENDER_POINTS).coerceAtLeast(1)
        
        val ch1 = FloatArray(RENDER_POINTS)
        val ch2 = FloatArray(RENDER_POINTS)

        // High frequency decimator filter: Takes envelope peak detection (max/min)
        // to preserve ignition peaks (bobinas) in downsampled screen buffers.
        for (i in 0 until RENDER_POINTS) {
            val baseIdx = (triggerIdx + i * decimationFactor) * 2
            if (baseIdx + 1 >= size) break

            var maxValCh1 = -128
            var minValCh1 = 127
            var maxValCh2 = -128
            var minValCh2 = 127

            for (j in 0 until decimationFactor) {
                val idx = baseIdx + j * 2
                if (idx + 1 >= size) break

                // Ch1/Ch2 odd/even interleaving
                val raw1 = (raw[idx].toInt() and 0xFF) - 128
                val raw2 = (raw[idx + 1].toInt() and 0xFF) - 128

                if (raw1 > maxValCh1) maxValCh1 = raw1
                if (raw1 < minValCh1) minValCh1 = raw1

                if (raw2 > maxValCh2) maxValCh2 = raw2
                if (raw2 < minValCh2) minValCh2 = raw2
            }

            // Average of min and max for decimate representation
            val val1 = (maxValCh1 + minValCh1) / 2f
            val val2 = (maxValCh2 + minValCh2) / 2f

            // Convert to physical Volts: +/-5V range for Hantek at 1x
            ch1[i] = (val1 / 128f) * 5.0f * atten1
            ch2[i] = (val2 / 128f) * 5.0f * atten2
        }

        _ch1Data.value = ch1
        _ch2Data.value = ch2
    }
}
