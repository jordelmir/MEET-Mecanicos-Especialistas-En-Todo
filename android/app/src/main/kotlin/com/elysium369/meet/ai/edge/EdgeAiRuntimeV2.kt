package com.elysium369.meet.ai.edge

enum class EdgeModelTier {
    TINY_QUANTIZED_LOCAL,
    BALANCED_SLM_LOCAL,
    CLOUD_REASONING_REMOTE,
}

data class DeviceCapabilitySnapshot(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val isNpuSupported: Boolean,
    val isGpuAccelerated: Boolean,
    val batteryPercent: Int,
    val isThermalThrottling: Boolean,
)

/**
 * EdgeAiRuntimeV2 — Evaluates real-time device hardware capabilities to select the optimal model tier.
 * Never allows local SLM outputs to override physical sensor observations.
 */
object EdgeAiRuntimeV2 {

    fun selectModelTier(snapshot: DeviceCapabilitySnapshot, requiresDeepReasoning: Boolean): EdgeModelTier {
        if (requiresDeepReasoning) {
            return EdgeModelTier.CLOUD_REASONING_REMOTE
        }

        // Under severe thermal throttle or critical battery, use lightweight tiny model
        if (snapshot.isThermalThrottling || snapshot.batteryPercent < 15 || snapshot.availableRamMb < 512) {
            return EdgeModelTier.TINY_QUANTIZED_LOCAL
        }

        // Flagship / balanced devices with sufficient RAM and accelerator support
        if (snapshot.availableRamMb >= 1500 && (snapshot.isNpuSupported || snapshot.isGpuAccelerated)) {
            return EdgeModelTier.BALANCED_SLM_LOCAL
        }

        return EdgeModelTier.TINY_QUANTIZED_LOCAL
    }
}
