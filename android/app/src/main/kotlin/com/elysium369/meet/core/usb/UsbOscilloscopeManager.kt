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
import kotlin.math.sin

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

    private val _isSimulationMode = MutableStateFlow(true) // Simulation active by default
    val isSimulationMode = _isSimulationMode.asStateFlow()

    private val _selectedWaveform = MutableStateFlow("INJECTOR_PWM") // Default simulation waveform
    val selectedWaveform = _selectedWaveform.asStateFlow()

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

    fun setSimulationMode(enabled: Boolean) {
        _isSimulationMode.value = enabled
        if (_isStreaming.value) {
            restartStream()
        }
    }

    fun setSelectedWaveform(type: String) {
        _selectedWaveform.value = type
    }

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

        // Try physical USB first if simulation is disabled
        if (!_isSimulationMode.value) {
            val list = usbManager.deviceList
            val hantekDevice = list.values.firstOrNull { driver.isHantekDevice(it) }
            if (hantekDevice != null) {
                if (driver.connect(hantekDevice)) {
                    _deviceConnected.value = true
                    driver.configureDso(_samplingRate.value, _ch1Gain.value, _ch2Gain.value)
                    driver.startCapture()
                    Log.i(TAG, "Successfully started physical DSO capture")
                } else {
                    Log.w(TAG, "Hantek device found but failed to claim. Falling back to simulation.")
                    _isSimulationMode.value = true
                }
            } else {
                Log.i(TAG, "No physical Hantek DSO detected. Running simulation engine.")
                _isSimulationMode.value = true
            }
        }

        restartStream()
    }

    private fun restartStream() {
        captureJob?.cancel()
        captureJob = managerScope.launch {
            if (_isSimulationMode.value) {
                runSimulationLoop()
            } else {
                runPhysicalCaptureLoop()
            }
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

    private suspend fun runSimulationLoop() {
        var phase = 0f
        while (coroutineContext.isActive && _isStreaming.value) {
            val ch1 = FloatArray(RENDER_POINTS)
            val ch2 = FloatArray(RENDER_POINTS)
            val type = _selectedWaveform.value

            val triggerV = _triggerLevel.value
            val atten1 = _ch1Attenuation.value
            val atten2 = _ch2Attenuation.value

            // Generate beautiful, physically accurate automotive diagnostic traces
            for (i in 0 until RENDER_POINTS) {
                val t = i / RENDER_POINTS.toFloat()
                when (type) {
                    "INJECTOR_PWM" -> {
                        // High resolution representation of a PWM common-rail injector pulse:
                        // 12V supply baseline, dips to 0V (driving gate), then flyback inductive kick (60-80V Vpp)
                        val period = 0.3f
                        val localT = (t + phase) % period
                        ch1[i] = when {
                            localT < 0.05f -> 12f * atten1 // Off state
                            localT < 0.12f -> 0f           // Injecting state
                            localT < 0.13f -> 75f * atten1 // Inductive peak
                            localT < 0.18f -> (12f + 15f * sin((localT - 0.13f) * 100f)) * atten1 // Ramped decay
                            else -> 12f * atten1
                        }
                        // CH2: CKP reference for synch
                        ch2[i] = (2.5f + 2.5f * sin((t + phase) * 80f)) * atten2
                    }
                    "ALTERNATOR_RIPPLE" -> {
                        // Alternator DC Charging baseline with a high-fidelity alternator ripple noise (burn diodes check)
                        val baseline = 14.2f
                        // 120Hz-300Hz AC Ripple on top
                        val ripple = 0.25f * sin((t + phase) * 180f) + 0.05f * sin((t + phase) * 450f)
                        ch1[i] = (baseline + ripple) * atten1
                        // CH2: Battery Logical 13.8V
                        ch2[i] = 13.8f * atten2
                    }
                    "CKP_SENSOR" -> {
                        // Crankshaft Hall / Inductive sensor 60-2 missing teeth standard automotive wheel
                        val angle = (t + phase) * 6f * Math.PI
                        val teethCount = 60
                        val toothIndex = (angle * teethCount / (2 * Math.PI)).toInt() % teethCount
                        val isMissingTooth = toothIndex == 0 || toothIndex == 1
                        val sineVal = if (isMissingTooth) 0f else sin(angle * teethCount).toFloat()
                        ch1[i] = sineVal * 3.5f * atten1 // +/- 3.5V standard Vpp
                        ch2[i] = (if (toothIndex % 2 == 0) 5f else 0f) * atten2
                    }
                    "CMP_SENSOR" -> {
                        // Camshaft Hall sensor (square wave reference, 1 pulse per 720 degrees)
                        val period = 0.5f
                        val localT = (t + phase) % period
                        ch1[i] = (if (localT < 0.25f) 5f else 0f) * atten1
                        ch2[i] = (if (localT > 0.1f && localT < 0.2f) 5f else 0f) * atten2
                    }
                    "LAMBDA_O2" -> {
                        // Lambda Oxygen Sensor: Sinusoidal rich/lean cycling (0.1V - 0.9V)
                        val baseline = 0.5f
                        val lambdaSin = 0.4f * sin((t + phase) * 8f)
                        ch1[i] = (baseline + lambdaSin) * atten1
                        // CH2: Catalytic downstream sensor (steady at 0.7V)
                        ch2[i] = 0.7f * atten2
                    }
                    else -> {
                        // Sinusoidal calibration wave
                        ch1[i] = (2.5f * sin((t + phase) * 40f)) * atten1
                        ch2[i] = (2.5f * sin((t + phase) * 40f + 1.5f)) * atten2
                    }
                }
            }

            // Slide phase forward
            phase += 0.008f
            
            _ch1Data.value = ch1
            _ch2Data.value = ch2

            delay(33) // Fluid 30 FPS update
        }
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
