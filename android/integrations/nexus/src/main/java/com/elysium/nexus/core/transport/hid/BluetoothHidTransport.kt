package com.elysium.nexus.core.transport.hid

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.elysium.nexus.core.model.CanonicalButton
import com.elysium.nexus.core.model.DpadState
import com.elysium.nexus.core.model.UniversalControllerState
import com.elysium.nexus.core.transport.ConnectionResult
import com.elysium.nexus.core.transport.ControllerTransport
import com.elysium.nexus.core.transport.DisconnectResult
import com.elysium.nexus.core.transport.PairingResult
import com.elysium.nexus.core.transport.ReliableInputEvent
import com.elysium.nexus.core.transport.SendResult
import com.elysium.nexus.core.transport.TransportCapabilities
import com.elysium.nexus.core.transport.TransportResult
import com.elysium.nexus.core.transport.TransportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Elysium Nexus — Universal Bluetooth HID transport.
 *
 * This class turns the Android phone into a
 * Bluetooth keyboard + mouse + consumer control
 * for **any host** that accepts Bluetooth HID
 * input:
 *
 *  - macOS (10.4+) — pairs as a generic
 *    keyboard + mouse + media keys
 *  - Windows 10/11 — same, no driver needed
 *  - Linux (BlueZ 5+) — same
 *  - Android TV, Google TV, Fire TV — same
 *  - Raspberry Pi (with `bluetoothd` + HID
 *    plugin) — same
 *  - iOS / iPadOS (limited — Apple hides
 *    pointer devices, but keyboards + media
 *    keys work)
 *  - Smart TVs with BT HID support
 *
 * The host sees the phone as a generic input
 * device; no software is required on the host.
 *
 * ## How it works
 *
 *  1. `start()` registers the app as a HID
 *     device via the platform
 *     `BluetoothHidDevice.registerApp` API
 *     (Android 9+ / API 28+).
 *  2. The user pairs the phone with the host
 *     from the **host** side (the phone is
 *     not discoverable — the host has to
 *     "Add Bluetooth device").
 *  3. `pairedHosts()` lists the
 *     already-paired devices. The user picks
 *     one and taps Connect.
 *  4. `connect(device)` opens the HID channel.
 *     The host's cursor / focus now follows
 *     the phone.
 *  5. Every trackpad / keyboard gesture is
 *     translated to a HID report and sent via
 *     `sendReport`.
 *
 * ## Why BT HID and not a custom protocol
 *
 * Every modern OS already knows what to do
 * with a Bluetooth keyboard + mouse. By
 * speaking the standard USB HID boot profile
 * we get universal support for free — no
 * driver, no daemon, no special sauce.
 *
 * ## Limitations
 *
 *  - **iOS / iPadOS** — Apple limits what BT
 *    pointers can do. Keyboard + media keys
 *    work; the mouse pointer is hidden.
 *  - **PS4 / PS5 / Xbox** — Sony and Microsoft
 *    only accept their own peripherals.
 */
class BluetoothHidTransport(
    private val context: Context
) : ControllerTransport {

    private val _hidState = MutableStateFlow<HidConnectionState>(HidConnectionState.Idle)
    val hidState: StateFlow<HidConnectionState> = _hidState.asStateFlow()

    private val _ctrlState = MutableStateFlow(TransportState.IDLE)
    override val state: TransportState
        get() = _ctrlState.value

    override val capabilities: TransportCapabilities = TransportCapabilities(
        maxRealtimeFps = 120,
        supportsReliable = false,
        latencyMs = 15,
        label = "Bluetooth HID"
    )

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                Log.d(TAG, "HID device profile connected")
                registerHidApp()
            }
        }
        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                _hidState.value = HidConnectionState.Idle
                Log.d(TAG, "HID device profile disconnected")
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "HID app status: registered=$registered")
            if (registered) {
                _hidState.value = HidConnectionState.Registered
            } else {
                _hidState.value = HidConnectionState.Idle
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(TAG, "HID connection state: $state for ${device.address}")
            when (state) {
                BluetoothProfile.STATE_CONNECTING -> {
                    _hidState.value = HidConnectionState.Connecting(device)
                }
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHost = device
                    _hidState.value = HidConnectionState.Connected(device)
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    _hidState.value = HidConnectionState.Disconnecting(device)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val wasOurs = connectedHost?.address == device.address
                    if (wasOurs) connectedHost = null
                    _hidState.value = if (wasOurs) {
                        HidConnectionState.Registered
                    } else {
                        _hidState.value
                    }
                }
            }
        }
    }

    /**
     * Kick off the transport. The actual
     * registration is asynchronous
     * (callback-based); the state flow
     * reflects progress.
     */
    fun startHid(): TransportResult {
        if (!hasBluetoothPermissions()) {
            return TransportResult.Error("Missing BLUETOOTH_CONNECT permission")
        }
        val adapter = bluetoothAdapter
            ?: return TransportResult.Error("BluetoothAdapter unavailable")
        if (!adapter.isEnabled) {
            return TransportResult.Error("Bluetooth is off — enable it in Settings")
        }
        if (hidDevice != null) return TransportResult.Ok
        _hidState.value = HidConnectionState.Registering
        return try {
            adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
            _ctrlState.value = TransportState.INITIALISING
            TransportResult.Ok
        } catch (e: Throwable) {
            Log.e(TAG, "getProfileProxy failed", e)
            _hidState.value = HidConnectionState.Error("getProfileProxy: ${e.message}")
            TransportResult.Error(e.message ?: "getProfileProxy failed")
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerHidApp() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Elysium Nexus Universal Remote",
            "Elysium Nexus keyboard + mouse + media keys",
            "Elysium Nexus",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidDescriptors.COMBO
        )
        try {
            hidDevice?.registerApp(sdp, null, null, { it.run() }, hidCallback)
        } catch (e: Throwable) {
            Log.e(TAG, "registerApp failed", e)
            _hidState.value = HidConnectionState.Error("registerApp: ${e.message}")
        }
    }

    /**
     * The list of Bluetooth devices already
     * paired with the phone.
     */
    @SuppressLint("MissingPermission")
    fun pairedHosts(): List<BluetoothDevice> {
        if (!hasBluetoothPermissions()) return emptyList()
        return try {
            bluetoothAdapter?.bondedDevices.orEmpty()
                .filter { it.address != bluetoothAdapter?.address }
                .sortedBy { it.name ?: it.address }
        } catch (e: SecurityException) {
            Log.w(TAG, "pairedHosts: SecurityException ${e.message}")
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun connectTo(device: BluetoothDevice): Boolean {
        if (!hasBluetoothPermissions()) return false
        val hid = hidDevice ?: run {
            Log.w(TAG, "connectTo: hidDevice is null (not registered?)")
            return false
        }
        return try {
            hid.connect(device)
        } catch (e: Throwable) {
            Log.e(TAG, "connectTo failed", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectHid() {
        if (!hasBluetoothPermissions()) return
        val hid = hidDevice ?: return
        try {
            connectedHost?.let { hid.disconnect(it) }
        } catch (e: Throwable) {
            Log.w(TAG, "disconnectHid: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun sendKeyboardReport(report: ByteArray): Boolean {
        if (!hasBluetoothPermissions()) return false
        val hid = hidDevice ?: return false
        val host = connectedHost ?: return false
        return try {
            hid.sendReport(host, HidDescriptors.REPORT_ID_KEYBOARD, report)
        } catch (e: Throwable) {
            Log.w(TAG, "sendKeyboardReport: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun sendMouseReport(report: ByteArray): Boolean {
        if (!hasBluetoothPermissions()) return false
        val hid = hidDevice ?: return false
        val host = connectedHost ?: return false
        return try {
            hid.sendReport(host, HidDescriptors.REPORT_ID_MOUSE, report)
        } catch (e: Throwable) {
            Log.w(TAG, "sendMouseReport: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun sendConsumerReport(usageId: Int): Boolean {
        if (!hasBluetoothPermissions()) return false
        val hid = hidDevice ?: return false
        val host = connectedHost ?: return false
        return try {
            hid.sendReport(host, HidDescriptors.REPORT_ID_CONSUMER, HidReports.consumer(usageId))
        } catch (e: Throwable) {
            Log.w(TAG, "sendConsumerReport: ${e.message}")
            false
        }
    }

    /**
     * Release every keyboard + mouse + consumer
     * key. Send this when the user leaves the
     * control surface or on disconnect.
     */
    fun releaseAllKeys() {
        sendKeyboardReport(HidReports.keyboardReleaseAll())
        sendMouseReport(HidReports.mouse(0, 0, 0, 0))
        sendConsumerReport(0)
    }

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        }
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH
        ) == PackageManager.PERMISSION_GRANTED
    }

    // === ControllerTransport implementation ===

    override suspend fun start(): TransportResult = startHid()

    override suspend fun pair(): PairingResult = PairingResult.Ok

    override suspend fun connect(): ConnectionResult {
        return ConnectionResult.Error("Use connectTo(BluetoothDevice) for HID")
    }

    override suspend fun sendReliable(event: ReliableInputEvent): SendResult {
        return when (event) {
            is ReliableInputEvent.ReleaseAll -> {
                releaseAllKeys()
                SendResult.Ok
            }
            is ReliableInputEvent.ButtonDown -> {
                val hidKey = canonicalButtonToHidKey(event.button) ?: return SendResult.Ok
                val report = HidReports.keyboard(modifier = 0, keycodes = intArrayOf(hidKey))
                if (sendKeyboardReport(report)) SendResult.Ok
                else SendResult.Error("BT HID send failed")
            }
            is ReliableInputEvent.ButtonUp -> {
                if (sendKeyboardReport(HidReports.keyboardReleaseAll())) SendResult.Ok
                else SendResult.Error("BT HID send failed")
            }
            is ReliableInputEvent.ProfileChanged -> SendResult.Ok
            else -> SendResult.Ok
        }
    }

    override suspend fun sendRealtime(state: UniversalControllerState): SendResult {
        if (hidDevice == null || connectedHost == null) return SendResult.Error("Not connected")

        // D-pad → arrow keys (HID usage codes)
        val dpadKey = when (state.dpad) {
            DpadState.North -> 0x52
            DpadState.South -> 0x51
            DpadState.West -> 0x50
            DpadState.East -> 0x4F
            DpadState.NorthWest -> 0x04    // 'a' for diagonal
            DpadState.NorthEast -> 0x07    // 'd'
            DpadState.SouthWest -> 0x16    // 's'
            DpadState.SouthEast -> 0x15    // 'r'
            DpadState.Center -> 0
        }
        if (dpadKey != 0) {
            sendKeyboardReport(HidReports.keyboard(modifier = 0, keycodes = intArrayOf(dpadKey)))
        } else {
            sendKeyboardReport(HidReports.keyboardReleaseAll())
        }

        // Left stick → mouse movement
        val dx = (state.leftStick.x * 8f).toInt().coerceIn(-127, 127)
        val dy = (state.leftStick.y * 8f).toInt().coerceIn(-127, 127)
        if (dx != 0 || dy != 0) {
            sendMouseReport(HidReports.mouse(buttons = 0, dx = dx, dy = dy, wheel = 0))
        }

        // Face buttons → keyboard keys
        val keys = mutableListOf<Int>()
        if (state.buttons.isPressed(CanonicalButton.South)) keys.add(0x2C)  // Space
        if (state.buttons.isPressed(CanonicalButton.East)) keys.add(0x29)   // Escape
        if (state.buttons.isPressed(CanonicalButton.West)) keys.add(0x06)   // 'c'
        if (state.buttons.isPressed(CanonicalButton.North)) keys.add(0x19)  // 'v'
        if (state.buttons.isPressed(CanonicalButton.MenuPrimary)) keys.add(0x28)  // Enter
        if (keys.isNotEmpty()) {
            val arr = keys.toIntArray()
            sendKeyboardReport(HidReports.keyboard(modifier = 0, keycodes = arr))
        }

        // Triggers → scroll wheel
        val scrollDelta = ((state.rightTrigger.value - state.leftTrigger.value) * 3f).toInt()
            .coerceIn(-127, 127)
        if (scrollDelta != 0) {
            sendMouseReport(HidReports.mouse(buttons = 0, dx = 0, dy = 0, wheel = scrollDelta))
        }

        return SendResult.Ok
    }

    override suspend fun releaseAll(): SendResult {
        releaseAllKeys()
        return SendResult.Ok
    }

    override suspend fun disconnect(): DisconnectResult {
        disconnectHid()
        return DisconnectResult.Ok
    }

    @SuppressLint("MissingPermission")
    override suspend fun stop(): TransportResult {
        try {
            hidDevice?.unregisterApp()
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        } catch (e: Throwable) {
            Log.w(TAG, "stop: ${e.message}")
        }
        hidDevice = null
        connectedHost = null
        _hidState.value = HidConnectionState.Idle
        return TransportResult.Ok
    }

    /**
     * Map a [CanonicalButton] to an HID keyboard usage code.
     * Returns null for buttons that don't have a keyboard mapping.
     */
    private fun canonicalButtonToHidKey(button: CanonicalButton): Int? = when (button) {
        CanonicalButton.South -> 0x2C       // Space
        CanonicalButton.East -> 0x29        // Escape
        CanonicalButton.West -> 0x06        // 'c'
        CanonicalButton.North -> 0x19       // 'v'
        CanonicalButton.LeftBumper -> 0x2F  // '['
        CanonicalButton.RightBumper -> 0x30 // ']'
        CanonicalButton.MenuPrimary -> 0x28 // Enter
        CanonicalButton.MenuSecondary -> 0x2A // Backspace
        CanonicalButton.System -> 0x08      // 'e' (home)
        CanonicalButton.LeftStickClick -> 0x16 // 's'
        CanonicalButton.RightStickClick -> 0x07 // 'd'
        else -> null
    }

    companion object {
        private const val TAG = "BluetoothHidTransport"
        const val MIN_SDK: Int = Build.VERSION_CODES.P // API 28
    }
}

/**
 * The transport's UI state.
 */
sealed class HidConnectionState {
    object Idle : HidConnectionState()
    object Registering : HidConnectionState()
    object Registered : HidConnectionState()
    data class Connecting(val device: BluetoothDevice) : HidConnectionState()
    data class Connected(val device: BluetoothDevice) : HidConnectionState()
    data class Disconnecting(val device: BluetoothDevice) : HidConnectionState()
    data class Error(val reason: String) : HidConnectionState()
}
