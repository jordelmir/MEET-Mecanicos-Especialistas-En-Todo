package com.elysium.nexus.fabric.infrared

/**
 * V0.7 Phase 9 — Physical carrier policy.
 *
 * The audit requirement: the blanket global ±2000 Hz carrier fallback is a
 * physical alteration of the emitted signal and must NOT be a global rule.
 *
 *  - [CarrierPolicyMode.STRICT] (commercial default): the requested carrier
 *    is used as-is. If the hardware does not support it, the transmission
 *    fails closed with [CarrierSelection.Unsupported]. Zero silent shifts.
 *  - [CarrierPolicyMode.LAB_TOLERANCE] (lab only): nearest supported carrier
 *    within ±[LAB_TOLERANCE_HZ] may be used. Only developer/lab tooling may
 *    opt in; a production dispatch path must never pass this mode.
 *
 * Promotion to commercial fallback requires recorded physical evidence
 * (`PhysicalTestEvidence` with the exact `usedCarrierHz`), per the audit:
 * "Fallback only when protocol/variant allows it and physical tests prove
 * operation."
 */
enum class CarrierPolicyMode {
    STRICT,
    LAB_TOLERANCE
}

sealed interface CarrierSelection {
    data class Use(val carrierHz: Int) : CarrierSelection
    data class Unsupported(
        val requestedHz: Int,
        val supportedRanges: List<IntRange>
    ) : CarrierSelection
}

object CarrierPolicy {

    /** Maximum deviation tolerated ONLY under [CarrierPolicyMode.LAB_TOLERANCE]. */
    const val LAB_TOLERANCE_HZ = 2_000

    /**
     * Pure decision function — JVM-testable without Android mocks.
     *
     * @param requestedHz canonical carrier of the waveform.
     * @param supportedRanges hardware-supported carrier ranges (empty = unknown → allow request).
     * @param mode STRICT (default, fail closed) or LAB_TOLERANCE (nearest within ±2000 Hz).
     */
    fun selectCarrier(
        requestedHz: Int,
        supportedRanges: List<IntRange>,
        mode: CarrierPolicyMode = CarrierPolicyMode.STRICT
    ): CarrierSelection {
        if (supportedRanges.isEmpty()) {
            return CarrierSelection.Use(requestedHz)
        }
        val isSupported = supportedRanges.any { range -> requestedHz in range }
        if (isSupported) return CarrierSelection.Use(requestedHz)

        if (mode == CarrierPolicyMode.LAB_TOLERANCE) {
            val nearestHz = supportedRanges
                .flatMap { range -> listOf(range.first, range.last) }
                .minBy { range -> kotlin.math.abs(range - requestedHz) }
            if (kotlin.math.abs(nearestHz - requestedHz) <= LAB_TOLERANCE_HZ) {
                return CarrierSelection.Use(nearestHz)
            }
        }
        return CarrierSelection.Unsupported(requestedHz, supportedRanges)
    }
}