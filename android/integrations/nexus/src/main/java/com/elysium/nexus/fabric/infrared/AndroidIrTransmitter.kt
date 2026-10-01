package com.elysium.nexus.fabric.infrared

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Android hardware adapter for IR pattern transmission wrapping [ConsumerIrManager].
 *
 * Implements strict capability probing (`hasIrEmitter()`), thread safety via [Mutex],
 * asynchronous execution on [Dispatchers.IO], and typed execution outputs using [IrTransmitResult].
 */
class AndroidIrTransmitter(context: Context) {

    private val tag = "ElysiumNexus.Ir"
    private val manager: ConsumerIrManager? = try {
        context.applicationContext
            .getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    } catch (e: Throwable) {
        Log.w(tag, "ConsumerIrManager service acquisition failed; operating in no-op mode.", e)
        null
    }

    private val hasEmitter: Boolean = try {
        manager?.hasIrEmitter() == true
    } catch (e: Throwable) {
        Log.w(tag, "ConsumerIrManager.hasIrEmitter() probe failed.", e)
        false
    }

    private val transmitMutex = Mutex()

    /**
     * @return `true` if the device has an operational IR emitter hardware component.
     */
    fun hasEmitter(): Boolean = hasEmitter

    /**
     * @return List of hardware-supported carrier frequency ranges in Hz, or `emptyList()`
     * when unavailable.
     */
    fun carrierRanges(): List<IntRange> {
        val m = manager ?: return emptyList()
        val ranges = try {
            m.carrierFrequencies
        } catch (e: SecurityException) {
            Log.w(tag, "getCarrierFrequencies permission denied.", e)
            return emptyList()
        } catch (e: Throwable) {
            Log.w(tag, "getCarrierFrequencies failed.", e)
            return emptyList()
        } ?: return emptyList()

        return ranges.map { it.minFrequency..it.maxFrequency }
    }

    /**
     * Transmit an IR waveform.
     * Executed asynchronously off the main thread on [Dispatchers.IO].
     *
     * V0.7 Phase 9: carrier selection is governed by [CarrierPolicy].
     * The commercial default is [CarrierPolicyMode.STRICT] — an unsupported
     * carrier fails closed with [IrTransmitResult.UnsupportedCarrier] and is
     * NEVER silently shifted. Lab tooling may explicitly pass
     * [CarrierPolicyMode.LAB_TOLERANCE].
     *
     * @return [IrTransmitResult] detailing the typed result of the transmission attempt.
     */
    suspend fun transmit(
        waveform: IrWaveform,
        policy: CarrierPolicyMode = CarrierPolicyMode.STRICT
    ): IrTransmitResult = withContext(Dispatchers.IO) {
        val m = manager ?: return@withContext IrTransmitResult.NoEmitter
        if (!hasEmitter) return@withContext IrTransmitResult.NoEmitter

        // Carrier frequency range validation (V0.7 Phase 9 — CarrierPolicy).
        // No blanket global fallback: only LAB_TOLERANCE may shift the carrier,
        // and only within ±2000 Hz. Production (STRICT) always fails closed.
        val supportedRanges = carrierRanges()
        if (supportedRanges.isNotEmpty()) {
            val requestedHz = waveform.carrierHz
            when (val selection = CarrierPolicy.selectCarrier(requestedHz, supportedRanges, policy)) {
                is CarrierSelection.Use -> {
                    if (selection.carrierHz != requestedHz) {
                        FileLog.d("TX_CARRIER_FALLBACK policy=$policy requested=${requestedHz}Hz used=${selection.carrierHz}Hz")
                        Log.w(tag, "Carrier $requestedHz Hz unsupported; LAB_TOLERANCE fallback to ${selection.carrierHz} Hz.")
                    }
                    return@withContext transmitLocked(m, waveform, selection.carrierHz)
                }
                is CarrierSelection.Unsupported -> {
                    Log.w(tag, "Carrier $requestedHz Hz out of supported ranges $supportedRanges (policy=$policy).")
                    return@withContext IrTransmitResult.UnsupportedCarrier(
                        requestedHz = requestedHz,
                        supportedRanges = supportedRanges
                    )
                }
            }
        }

        return@withContext transmitLocked(m, waveform, waveform.carrierHz)
    }

    private suspend fun transmitLocked(
        m: ConsumerIrManager,
        waveform: IrWaveform,
        carrierHz: Int
    ): IrTransmitResult {
        // Validate pattern slices strictly > 0 and total duration < 2s
        if (waveform.pattern.isEmpty()) {
            return IrTransmitResult.InvalidPattern("Pattern is empty.")
        }
        if (waveform.pattern.any { it <= 0 }) {
            return IrTransmitResult.InvalidPattern("Pattern contains non-positive slice duration <= 0 us.")
        }
        if (waveform.totalDurationUs >= 2_000_000L) {
            return IrTransmitResult.InvalidPattern("Pattern total duration exceeds 2-second limit.")
        }

        if (!transmitMutex.tryLock()) {
            Log.w(tag, "IR transmit mutex locked; dropping concurrent request.")
            return IrTransmitResult.Busy
        }

        try {
            m.transmit(carrierHz, waveform.pattern)
            val hash = waveform.sha256Hash()
            Log.d(tag, "IR pattern successfully transmitted: $carrierHz Hz, ${waveform.totalDurationUs} us, hash=$hash")
            FileLog.d("TX_OK carrier=${carrierHz}Hz duration=${waveform.totalDurationUs}us hash=$hash slices=${waveform.pattern.size}")
            return IrTransmitResult.Success(
                carrierHz = carrierHz,
                durationUs = waveform.totalDurationUs,
                patternHash = hash
            )
        } catch (e: SecurityException) {
            FileLog.d("TX_PERMISSION_DENIED")
            Log.w(tag, "IR transmit failed: TRANSMIT_IR permission denied.", e)
            return IrTransmitResult.PermissionDenied
        } catch (e: Throwable) {
            FileLog.d("TX_EXCEPTION: ${e.message}")
            Log.e(tag, "IR transmit exception: ${e.message}", e)
            return IrTransmitResult.PlatformFailure(e)
        } finally {
            transmitMutex.unlock()
        }
    }

    companion object {
        /**
         * @return `true` when device announces system feature `FEATURE_CONSUMER_IR`.
         */
        fun deviceHasIr(context: Context): Boolean =
            context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_CONSUMER_IR
            )
    }
}
