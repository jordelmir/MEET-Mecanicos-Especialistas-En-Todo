package com.elysium.nexus.fabric.infrared

import com.elysium.nexus.core.device.IrSignal

/**
 * V0.7 Phase 5 — Runtime execution policy.
 *
 * Defines which signal loaders may return which signals.
 *
 *  - [COMMERCIAL]: production default. EXPERIMENTAL codecs (RC5, RC6, Kaseikyo)
 *    and unknown codecs are LAB_ONLY and blocked from every runtime path:
 *    probe, saved profiles, brand lookup, direct lookup, and automation.
 *  - [LAB_ONLY]: unrestricted. Only used by developer/lab tooling.
 */
enum class RuntimePolicy {
    COMMERCIAL,
    LAB_ONLY
}

/**
 * V0.7 Phase 5 — Single commercial codec policy entry point.
 *
 * The audit requirement: "No candidate loader may bypass this policy."
 * Every signal that leaves the catalog for execution MUST pass through
 * [isExecutable] with the default [RuntimePolicy.COMMERCIAL].
 *
 * Fail closed: an [IrSignal.Encoded] with a null or unknown codecId is
 * NOT executable under COMMERCIAL (never silently assumed transmittable).
 */
object RuntimeSignalPolicy {

    fun isExecutable(
        signal: IrSignal,
        policy: RuntimePolicy = RuntimePolicy.COMMERCIAL
    ): Boolean = when (policy) {
        RuntimePolicy.LAB_ONLY -> true
        RuntimePolicy.COMMERCIAL -> when (signal) {
            is IrSignal.Raw -> true
            is IrSignal.Encoded -> signal.codecId
                ?.let { ProtocolCodecRegistry.isCodecTransmittable(it) }
                ?: false
        }
    }
}