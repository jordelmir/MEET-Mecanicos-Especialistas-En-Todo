package com.elysium.nexus.fabric.canonical

import java.util.UUID

/**
 * The §4.4 canonical universal action.
 *
 * A [UniversalAction] is the single verb the system
 * uses to express user intent. Every adapter translates
 * a [UniversalAction] into its protocol-specific command;
 * the rest of the system speaks only [UniversalAction].
 *
 * ## Why a sealed hierarchy
 *
 * A sealed hierarchy gives the compiler exhaustive-when
 * checking: if a new action is added, every adapter that
 * handles actions is forced to handle it (or declare
 * it unsupported). An open class would let adapters
 * silently ignore new actions.
 *
 * ## Tracing
 *
 * Every action carries a [correlationId] (UUID) for
 * end-to-end tracing through the dispatch pipeline:
 * UI → ActionDispatcher → RouteNegotiator → Adapter →
 * EvidenceStore. The correlation id is the join key
 * for diagnostic queries.
 */
sealed class UniversalAction {

    /** Stable target device. */
    abstract val targetDeviceId: DeviceId
    /** Wall-clock nanos when the action was created. */
    abstract val timestampNs: Long
    /** UUID for end-to-end tracing. */
    abstract val correlationId: String

    // ── Power ──────────────────────────────────────────

    data class PowerOn(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    data class PowerOff(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    data class PowerToggle(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    // ── Volume ─────────────────────────────────────────

    data class VolumeUp(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    data class VolumeDown(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    data class Mute(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    data class SetVolume(
        override val targetDeviceId: DeviceId,
        /** Volume level 0.0..1.0 */
        val level: Float,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction() {
        init {
            require(level in 0f..1f) { "Volume level must be in [0, 1] (got $level)." }
        }
    }

    // ── Channel ────────────────────────────────────────

    data class ChannelUp(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    data class ChannelDown(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    // ── Input ──────────────────────────────────────────

    data class InputSelect(
        override val targetDeviceId: DeviceId,
        /** Input index or name (adapter-specific). */
        val inputId: String,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction() {
        init {
            require(inputId.isNotBlank()) { "inputId must be non-blank." }
        }
    }

    // ── Media transport ────────────────────────────────

    data class MediaPlay(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    data class MediaPause(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    data class MediaStop(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    data class MediaNext(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    data class MediaPrevious(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    // ── Navigation ─────────────────────────────────────

    data class Navigate(
        override val targetDeviceId: DeviceId,
        val direction: Direction,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    data class Ok(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    data class Back(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    data class Home(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    data class Menu(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    // ── Climate ────────────────────────────────────────

    data class SetTemperature(
        override val targetDeviceId: DeviceId,
        val targetCelsius: Float,
        val mode: ClimateMode = ClimateMode.Auto,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction() {
        init {
            require(targetCelsius in -50f..150f) {
                "Temperature must be in [-50, 150] °C (got $targetCelsius)."
            }
        }
    }

    data class SetFanSpeed(
        override val targetDeviceId: DeviceId,
        /** Fan speed level 0.0..1.0 */
        val level: Float,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction() {
        init {
            require(level in 0f..1f) { "Fan speed level must be in [0, 1] (got $level)." }
        }
    }

    data class SetMode(
        override val targetDeviceId: DeviceId,
        val mode: ClimateMode,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction()

    // ── Escape hatch ───────────────────────────────────

    data class Custom(
        override val targetDeviceId: DeviceId,
        val key: String,
        val payload: Map<String, String> = emptyMap(),
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = UUID.randomUUID().toString()
    ) : UniversalAction() {
        init {
            require(key.isNotBlank()) { "Custom action key must be non-blank." }
        }
    }

    /**
     * The required [Capability] to execute this action.
     * The [PermissionGate] and policy engine use this
     * to check that the target device exposes the
     * capability and the user has permission.
     */
    fun requiredCapability(): Capability = when (this) {
        is PowerOn, is PowerOff, is PowerToggle -> Capability.OnOff
        is VolumeUp, is VolumeDown, is Mute, is SetVolume -> Capability.Volume
        is ChannelUp, is ChannelDown -> Capability.Channel
        is InputSelect -> Capability.InputSource
        is MediaPlay, is MediaPause, is MediaStop,
        is MediaNext, is MediaPrevious -> Capability.MediaTransport
        is Navigate, is Ok, is Back, is Home, is Menu -> Capability.MediaTransport
        is SetTemperature -> Capability.TargetTemperature
        is SetFanSpeed -> Capability.FanSpeed
        is SetMode -> Capability.Mode
        is Custom -> Capability.Custom
    }
}

/** Navigation direction. */
enum class Direction {
    Up, Down, Left, Right
}
