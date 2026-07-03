package com.elysium369.meet.core.twin

import com.elysium369.meet.diagnostic.DiagnosticProvenance
import com.elysium369.meet.diagnostic.DiagnosticValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * A single timestamped frame of OBD-II PID values that the Digital Twin can evaluate.
 *
 * Map key is the OBD PID hex (e.g. "0105" for coolant temp). Values are the raw engineering
 * unit reported by the sensor. We do NOT enforce unit conversion here — the engine knows
 * what each PID means.
 */
data class TwinLiveFrame(
    val vehicleId: String,
    val pidValues: Map<String, Float>,
    val capturedAtMs: Long = System.currentTimeMillis()
)

/**
 * Source of live frames for the Digital Twin.
 *
 * Implementations:
 * - [SimulatedTwinFrameSource] — deterministic sine-wave demo data, SIMULATED provenance.
 * - A future real source wraps the OBD session and emits REAL frames as they arrive.
 *
 * The repository composes a [TwinFrameSource] + the engine so the UI never has to
 * ask "is this frame real?" — every frame already carries its [DiagnosticProvenance].
 */
interface TwinFrameSource {

    /** What kind of frames this source emits. */
    val provenance: DiagnosticProvenance

    /** Hot stream of frames for the given vehicle. */
    fun frames(vehicleId: String): Flow<DiagnosticValue<TwinLiveFrame>>
}

/**
 * Simulated frame source for demos, tutorials, and offline runs.
 *
 * Produces a deterministic-ish signal per PID (sine around a baseline with small
 * Gaussian jitter) so the twin has something realistic to evaluate against.
 * All frames are emitted with [DiagnosticProvenance.Simulated] — the UI MUST
 * render the "SIMULATED" badge.
 */
@Singleton
class SimulatedTwinFrameSource @Inject constructor() : TwinFrameSource {

    override val provenance: DiagnosticProvenance = DiagnosticProvenance.Simulated

    private val shared = MutableSharedFlow<DiagnosticValue<TwinLiveFrame>>(replay = 1, extraBufferCapacity = 64)

    override fun frames(vehicleId: String): Flow<DiagnosticValue<TwinLiveFrame>> =
        shared.asSharedFlow()

    /**
     * Pump one frame into the stream. Test/demo code calls this directly.
     */
    suspend fun emit(vehicleId: String, pidValues: Map<String, Float>) {
        val frame = TwinLiveFrame(vehicleId = vehicleId, pidValues = pidValues)
        shared.emit(DiagnosticValue.simulated(frame))
    }
}

/**
 * Real frame source wired to the OBD adapter. Currently a placeholder; once we
 * re-establish the wireless device we will wire [com.elysium369.meet.core.obd.ObdSession]
 * to call [push] from its PID-sampling loop.
 *
 * For now, this class exists so the DI graph has a single source-of-truth for "real"
 * frames and the UI can already render the REAL/OFFLINE badge correctly.
 */
@Singleton
class ObdTwinFrameSource @Inject constructor() : TwinFrameSource {

    override val provenance: DiagnosticProvenance = DiagnosticProvenance.Real

    private val shared = MutableSharedFlow<DiagnosticValue<TwinLiveFrame>>(replay = 1, extraBufferCapacity = 64)

    override fun frames(vehicleId: String): Flow<DiagnosticValue<TwinLiveFrame>> =
        shared.asSharedFlow()

    /**
     * Called by ObdSession (or its PID sampler) for each fresh frame.
     */
    suspend fun push(vehicleId: String, pidValues: Map<String, Float>, timestampMs: Long = System.currentTimeMillis()) {
        val frame = TwinLiveFrame(vehicleId = vehicleId, pidValues = pidValues, capturedAtMs = timestampMs)
        shared.emit(DiagnosticValue.real(frame, timestampMs))
    }
}

/**
 * Helper for synthetic baseline generation. Used by:
 * - Demo seeders
 * - Tests
 * - The SimulatedTwinFrameSource pump
 */
internal object TwinDemoProfiles {

    /**
     * Build a healthy baseline map keyed by PID. Values are mid-range engineering
     * numbers typical for a warmed-up gasoline engine at idle.
     */
    fun healthyBaseline(): Map<String, Float> = mapOf(
        "0105" to 90f,    // Coolant °C
        "0142" to 14.0f,  // Battery V
        "010C" to 800f,   // Engine RPM
        "0104" to 22f,    // Engine Load %
        "0107" to 0.5f,   // Long-term fuel trim %
        "010B" to 35f,    // MAP kPa
        "0110" to 4.0f,   // MAF g/s
        "010F" to 28f,    // Intake Air Temp °C
        "010D" to 0f,     // Speed km/h
        "0111" to 3f      // Throttle Position %
    )

    /**
     * Build a frame that deviates from the baseline by adding a small sine drift
     * plus Gaussian jitter. Optional [anomalySeed] will push one PID way off baseline
     * so the twin has something to flag.
     */
    fun perturbedFrame(
        baseline: Map<String, Float>,
        t: Int,
        anomalySeed: Pair<String, Float>? = null,
        rng: Random = Random(seed = 42L)
    ): Map<String, Float> {
        val out = mutableMapOf<String, Float>()
        for ((pid, base) in baseline) {
            val drift = (sin(2.0 * PI * t / 30.0).toFloat()) * 0.02f * base
            val jitter = rng.nextFloat() * 0.01f * base
            out[pid] = base + drift + jitter
        }
        anomalySeed?.let { (pid, value) -> out[pid] = value }
        return out
    }
}