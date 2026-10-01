package com.elysium.nexus.fabric.adapter

import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.DeviceType
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.UniversalAction
import kotlinx.coroutines.flow.StateFlow

/**
 * The §4.1 device adapter interface.
 *
 * A [DeviceAdapter] bridges a **protocol-specific**
 * API (Matter, Zigbee, Z-Wave, MQTT, ONVIF, BLE,
 * Home Assistant REST, IR, HDMI-CEC, vendor REST/WS)
 * to the §4.2 canonical [DeviceTwin] model.
 *
 * Every adapter:
 *  1. **Discovers** devices on its protocol.
 *  2. **Populates** [DeviceTwin]s from the device's
 *     actual state.
 *  3. **Commands** the device by translating a
 *     canonical [DeviceState] into a protocol call.
 *  4. **Streams** state changes back as [DeviceTwin]s.
 *
 * The adapter does **not** own automation logic;
 * the §28 automation engine reads twins and issues
 * commands through the adapter.
 *
 * ## Why an interface, not a class hierarchy
 *
 * The interface is the testable seam. A unit test
 * uses a `FakeAdapter` that returns canned twins;
 * the production implementation talks to a real
 * protocol stack. The adapter memory rule applies.
 *
 * ## Lifecycle
 *
 * ```
 * Created → start() → [Active]
 *                        ↕  scan / read / write
 *                     stop() → [Released]
 * ```
 *
 * After [stop], the adapter is unusable. Create a
 * new instance to reuse.
 */
interface DeviceAdapter {

    /**
     * The protocol this adapter speaks.
     * Used by the knowledge graph to route
     * commands to the right adapter.
     */
    val protocol: Protocol

    /**
     * A human-readable label (e.g. "Home Assistant",
     * "Matter", "Zigbee 3.0").
     */
    val label: String

    /**
     * The set of capabilities this adapter can
     * translate. Used by the knowledge graph to
     * decide which adapter to use for a given
     * device capability.
     */
    val supportedCapabilities: Set<Capability>

    /**
     * The adapter's current state. Observers
     * (the Hub UI, the Android app) react to
     * state changes.
     */
    val state: StateFlow<AdapterState>

    /**
     * Initialize the adapter (open connections,
     * start discovery, claim hardware resources).
     *
     * @return [AdapterResult.Ok] on success.
     */
    suspend fun start(): AdapterResult

    /**
     * Scan for devices on this protocol. The
     * adapter emits discovered devices via
     * [devices] flow. This method returns
     * after the scan completes or times out.
     *
     * @param timeoutMs maximum scan duration.
     * @return the number of devices found.
     */
    suspend fun scan(timeoutMs: Long = 10_000L): ScanResult

    /**
     * The set of device twins discovered (or
     * paired) by this adapter. The flow emits
     * a new set every time a device state
     * changes.
     */
    val devices: StateFlow<List<DeviceTwin>>

    /**
     * Read the current state of a specific device.
     *
     * @param deviceId the device to read.
     * @return the fresh [DeviceTwin] or an error.
     */
    suspend fun read(deviceId: DeviceId): ReadResult

    /**
     * Write a desired state to a device. The
     * adapter translates the canonical
     * [DeviceState] into a protocol-specific
     * command.
     *
     * @param deviceId the target device.
     * @param state the desired state.
     * @return [WriteResult.Ok] on success.
     */
    suspend fun write(deviceId: DeviceId, state: DeviceState): WriteResult

    /**
     * Subscribe to real-time state changes for a
     * device. The adapter pushes new [DeviceTwin]s
     * into [devices] whenever the device reports
     * a change.
     *
     * @param deviceId the device to watch.
     * @return [AdapterResult.Ok] on success.
     */
    suspend fun subscribe(deviceId: DeviceId): AdapterResult

    /**
     * Unsubscribe from a device's state changes.
     */
    suspend fun unsubscribe(deviceId: DeviceId): AdapterResult

    /**
     * Release the adapter. After [stop], the
     * adapter is unusable.
     */
    suspend fun stop(): AdapterResult

    /**
     * Translate a [UniversalAction] into a protocol-specific
     * [DeviceState] that can be passed to [write].
     *
     * Default returns null (unsupported). Adapters override
     * to provide protocol-aware mapping.
     *
     * @param action the canonical action to translate.
     * @return the protocol-specific state, or null if
     *   this adapter cannot handle the action.
     */
    fun translateAction(action: UniversalAction): DeviceState? = null
}

/**
 * The adapter's lifecycle state.
 */
enum class AdapterState {
    /** Created but not started. */
    Idle,
    /** Initializing (opening connections). */
    Starting,
    /** Active: scanning, reading, writing. */
    Active,
    /** Scanning for devices. */
    Scanning,
    /** Stopping (releasing resources). */
    Stopping,
    /** Released. Cannot be reused. */
    Released,
    /** An unrecoverable error occurred. */
    Error
}

/** Generic adapter result. */
sealed class AdapterResult {
    object Ok : AdapterResult()
    data class Error(val code: ErrorCode, val message: String) : AdapterResult()
}

/** Scan result: devices found. */
sealed class ScanResult {
    data class Ok(val deviceCount: Int) : ScanResult()
    data class Error(val code: ErrorCode, val message: String) : ScanResult()
}

/** Read result: a fresh device twin. */
sealed class ReadResult {
    data class Ok(val twin: DeviceTwin) : ReadResult()
    data class Error(val code: ErrorCode, val message: String) : ReadResult()
}

/** Write result: command delivered. */
sealed class WriteResult {
    data class Ok(val reportedState: DeviceState?) : WriteResult()
    data class Error(val code: ErrorCode, val message: String) : WriteResult()
}

/**
 * Error codes. The adapter translates protocol-
 * specific errors into these codes; the UI
 * and automation engine react to the code,
 * not the message.
 */
enum class ErrorCode {
    /** The adapter is not started. */
    NotStarted,
    /** The adapter is already started. */
    AlreadyStarted,
    /** The device was not found. */
    DeviceNotFound,
    /** The device is offline / unreachable. */
    DeviceOffline,
    /** The protocol returned an authentication error. */
    AuthFailed,
    /** The protocol returned a permission error. */
    PermissionDenied,
    /** The command is not supported for this device. */
    UnsupportedOperation,
    /** A network / transport error occurred. */
    NetworkError,
    /** The protocol returned an unknown error. */
    Unknown,
    /** A timeout occurred. */
    Timeout,
    /** The adapter's hardware is unavailable. */
    HardwareUnavailable
}
