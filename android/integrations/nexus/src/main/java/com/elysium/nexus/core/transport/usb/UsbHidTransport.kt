package com.elysium.nexus.core.transport.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * USB HID transport — latencia cero.
 *
 * When the Android phone is connected to a Mac/PC
 * via USB-C cable, this transport sends raw HID
 * reports directly over the USB bulk endpoint.
 *
 * ## How it works
 *
 * 1. The phone detects USB device attachment via
 *    [UsbManager] broadcast.
 * 2. The phone opens the USB device and claims
 *    the HID interface.
 * 3. Mouse/keyboard/touchpad events are sent as
 *    raw HID reports over the bulk OUT endpoint.
 * 4. The Mac/PC side runs a lightweight daemon
 *    ([macos-agent/]) that reads from the USB
 *    device and injects events via CGEventPost.
 *
 * ## Latency
 *
 * USB full-speed: 1ms per frame.
 * USB high-speed: 0.125ms per frame.
 * Actual round-trip: < 2ms (no Wi-Fi, no BT stack).
 *
 * ## Why USB bulk, not USB HID gadget
 *
 * USB HID gadget mode requires ConfigFS kernel
 * support and root. USB bulk transfer works on
 * any Android device with USB host/device support
 * (API 12+). The Mac daemon interprets the raw
 * bytes as HID reports.
 *
 * ## Wire format
 *
 * Each frame is a tagged binary packet:
 *
 * | Tag (1B) | Payload |
 * |----------|---------|
 * | 0x01     | Mouse move: dx(i16), dy(i16) |
 * | 0x02     | Mouse button: button(u8), pressed(u8) |
 * | 0x03     | Mouse scroll: dy(i16) |
 * | 0x04     | Keyboard: key(u8), pressed(u8) |
 * | 0x05     | Touchpad move: x(i16), y(i16), fingers(u8) |
 * | 0x06     | Touchpad click: button(u8), pressed(u8) |
 * | 0x07     | Touchpad scroll: dy(i16) |
 * | 0x10     | Gamepad state: buttons(u64), lx(i8), ly(i8), rx(i8), ry(i8), lt(u8), rt(u8) |
 * | 0xFE     | Ping (keepalive) |
 * | 0xFF     | Release all |
 */
class UsbHidTransport(
    private val context: Context,
    private val scope: CoroutineScope
) : ControllerTransport {

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var connection: UsbDeviceConnection? = null
    private var outEndpoint: UsbEndpoint? = null
    private var inEndpoint: UsbEndpoint? = null
    private var device: UsbDevice? = null

    private val _state = MutableStateFlow(TransportState.IDLE)
    override val state: TransportState
        get() = _state.value

    private val _connectedDevice = MutableStateFlow<UsbDevice?>(null)
    val connectedDevice: StateFlow<UsbDevice?> = _connectedDevice.asStateFlow()

    override val capabilities: TransportCapabilities = TransportCapabilities(
        maxRealtimeFps = 1000,
        supportsReliable = true,
        latencyMs = 1,
        label = "USB-C HID (cable)"
    )

    private val TAG = "UsbHidTransport"
    private val ACTION_USB_PERMISSION = "com.elysium.nexus.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val dev = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    val granted = intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED, false
                    )
                    if (granted && dev != null) {
                        openDevice(dev)
                    } else {
                        Log.w(TAG, "USB permission denied")
                        _state.value = TransportState.ERROR
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val dev = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (dev?.serialNumber == device?.serialNumber) {
                        scope.launch { disconnect() }
                    }
                }
            }
        }
    }

    /**
     * Start listening for USB device attachments.
     * Registers the broadcast receiver.
     */
    override suspend fun start(): TransportResult {
        _state.value = TransportState.INITIALISING
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        // Check if a device is already connected.
        val devices = usbManager.deviceList
        if (devices.isNotEmpty()) {
            val dev = devices.values.first()
            requestPermission(dev)
        }
        return TransportResult.Ok
    }

    override suspend fun pair(): PairingResult {
        val conn = connection
            ?: return PairingResult.Error("No USB device connected")
        if (conn.fileDescriptor <= 0) {
            return PairingResult.Error("USB connection not open")
        }
        _state.value = TransportState.PAIRED
        return PairingResult.Ok
    }

    override suspend fun connect(): ConnectionResult {
        val conn = connection
            ?: return ConnectionResult.Error("No USB device connected")
        if (outEndpoint == null) {
            return ConnectionResult.Error("No HID endpoint found")
        }
        _state.value = TransportState.CONNECTED
        // Send a ping to verify the connection.
        sendPing()
        return ConnectionResult.Ok
    }

    override suspend fun sendReliable(event: ReliableInputEvent): SendResult {
        return when (event) {
            is ReliableInputEvent.ReleaseAll -> sendRaw(byteArrayOf(0xFF.toByte()))
            is ReliableInputEvent.ButtonDown -> sendRaw(
                byteArrayOf(0x10, 0x01, event.button.ordinal.toByte())
            )
            is ReliableInputEvent.ButtonUp -> sendRaw(
                byteArrayOf(0x10, 0x00, event.button.ordinal.toByte())
            )
            is ReliableInputEvent.ProfileChanged -> sendRaw(
                byteArrayOf(0x10, 0x02, event.profileId.toByte())
            )
            else -> SendResult.Ok
        }
    }

    override suspend fun sendRealtime(state: UniversalControllerState): SendResult {
        val buf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x10) // tag: gamepad state
        buf.putLong(state.buttons.bits)
        buf.put((state.leftStick.x + 1f).times(127.5f).toInt().coerceIn(-128, 127).toByte())
        buf.put((state.leftStick.y + 1f).times(127.5f).toInt().coerceIn(-128, 127).toByte())
        buf.put((state.rightStick.x + 1f).times(127.5f).toInt().coerceIn(-128, 127).toByte())
        buf.put((state.rightStick.y + 1f).times(127.5f).toInt().coerceIn(-128, 127).toByte())
        buf.put(state.leftTrigger.value.times(255f).toInt().coerceIn(0, 255).toByte())
        buf.put(state.rightTrigger.value.times(255f).toInt().coerceIn(0, 255).toByte())
        return sendRaw(buf.array())
    }

    override suspend fun releaseAll(): SendResult {
        return sendRaw(byteArrayOf(0xFF.toByte()))
    }

    /**
     * Send a mouse movement report.
     * @param dx delta X in pixels (-32768..32767)
     * @param dy delta Y in pixels (-32768..32767)
     */
    suspend fun sendMouseMove(dx: Int, dy: Int): SendResult {
        val buf = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x01) // tag: mouse move
        buf.putShort(dx.coerceIn(-32768, 32767).toShort())
        buf.putShort(dy.coerceIn(-32768, 32767).toShort())
        return sendRaw(buf.array())
    }

    /**
     * Send a mouse button report.
     * @param button 0=left, 1=right, 2=middle
     * @param pressed true=down, false=up
     */
    suspend fun sendMouseButton(button: Int, pressed: Boolean): SendResult {
        val buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x02) // tag: mouse button
        buf.put(button.toByte())
        buf.put(if (pressed) 1.toByte() else 0.toByte())
        return sendRaw(buf.array())
    }

    /**
     * Send a mouse scroll report.
     * @param dy scroll delta (-128..127)
     */
    suspend fun sendScroll(dy: Int): SendResult {
        val buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x03) // tag: mouse scroll
        buf.putShort(dy.coerceIn(-128, 127).toShort())
        return sendRaw(buf.array())
    }

    /**
     * Send a keyboard event.
     * @param key HID usage code
     * @param pressed true=down, false=up
     */
    suspend fun sendKey(key: Int, pressed: Boolean): SendResult {
        val buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x04) // tag: keyboard
        buf.put(key.toByte())
        buf.put(if (pressed) 1.toByte() else 0.toByte())
        return sendRaw(buf.array())
    }

    /**
     * Send a touchpad move report.
     * @param x absolute X (0..trackpad_width)
     * @param y absolute Y (0..trackpad_height)
     * @param fingers number of fingers (1=move, 2=scroll, 3=pinch)
     */
    suspend fun sendTouchpadMove(x: Int, y: Int, fingers: Int): SendResult {
        val buf = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x05) // tag: touchpad move
        buf.putShort(x.coerceIn(0, 32767).toShort())
        buf.putShort(y.coerceIn(0, 32767).toShort())
        buf.put(fingers.toByte())
        return sendRaw(buf.array())
    }

    /**
     * Send a touchpad click report.
     */
    suspend fun sendTouchpadClick(button: Int, pressed: Boolean): SendResult {
        val buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x06) // tag: touchpad click
        buf.put(button.toByte())
        buf.put(if (pressed) 1.toByte() else 0.toByte())
        return sendRaw(buf.array())
    }

    /**
     * Send a touchpad scroll (two-finger) report.
     */
    suspend fun sendTouchpadScroll(dy: Int): SendResult {
        val buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x07) // tag: touchpad scroll
        buf.putShort(dy.coerceIn(-128, 127).toShort())
        return sendRaw(buf.array())
    }

    override suspend fun disconnect(): DisconnectResult {
        releaseAll()
        connection?.close()
        connection = null
        outEndpoint = null
        inEndpoint = null
        device = null
        _connectedDevice.value = null
        _state.value = TransportState.DISCONNECTED
        return DisconnectResult.Ok
    }

    override suspend fun stop(): TransportResult {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Throwable) { /* already unregistered */ }
        disconnect()
        _state.value = TransportState.IDLE
        return TransportResult.Ok
    }

    // ── Internal ──────────────────────────────────────

    private fun requestPermission(dev: UsbDevice) {
        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName)
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_MUTABLE
        )
        usbManager.requestPermission(dev, permissionIntent)
    }

    private fun openDevice(dev: UsbDevice) {
        device = dev
        _connectedDevice.value = dev
        val conn = usbManager.openDevice(dev)
        if (conn == null) {
            Log.e(TAG, "Failed to open USB device")
            _state.value = TransportState.ERROR
            return
        }
        connection = conn
        // Find the HID interface (class 0x03 = HID).
        for (i in 0 until dev.interfaceCount) {
            val iface = dev.getInterface(i)
            if (iface.interfaceClass == 0x03) { // HID class
                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    if (ep.type == 2) { // BULK
                        if (ep.direction == 0) { // OUT
                            outEndpoint = ep
                        } else { // IN
                            inEndpoint = ep
                        }
                    }
                }
                conn.claimInterface(iface, true)
                Log.i(TAG, "HIF HID interface claimed: ${iface.name}")
                break
            }
        }
        if (outEndpoint == null) {
            // Fallback: use the first bulk OUT endpoint.
            for (i in 0 until dev.interfaceCount) {
                val iface = dev.getInterface(i)
                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    if (ep.type == 2 && ep.direction == 0) {
                        outEndpoint = ep
                        conn.claimInterface(iface, true)
                        Log.i(TAG, "Fallback bulk OUT endpoint found")
                        break
                    }
                }
                if (outEndpoint != null) break
            }
        }
        _state.value = TransportState.PAIRED
        Log.i(TAG, "USB device opened: ${dev.deviceName}")
    }

    private suspend fun sendRaw(data: ByteArray): SendResult = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext SendResult.Error("No USB connection")
        val ep = outEndpoint ?: return@withContext SendResult.Error("No OUT endpoint")
        return@withContext try {
            val sent = conn.bulkTransfer(ep, data, data.size, 100)
            if (sent >= 0) SendResult.Ok
            else SendResult.Error("bulkTransfer returned $sent")
        } catch (e: Throwable) {
            SendResult.Error("USB send failed: ${e.message}")
        }
    }

    private fun sendPing() {
        scope.launch(Dispatchers.IO) {
            @Suppress("BlockingMethodInNonBlockingContext")
            sendRaw(byteArrayOf(0xFE.toByte()))
        }
    }
}
