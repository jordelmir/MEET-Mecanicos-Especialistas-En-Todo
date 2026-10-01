package com.elysium.nexus.core.transport

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import com.elysium.nexus.core.model.UniversalControllerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * The §17 USB Accessory transport.
 *
 * `MASTER_ORDER.md` §17 lists `UsbAccessoryTransport`
 * as one of the transport implementations. The
 * Android `UsbManager` + `UsbAccessory` API is the
 * platform-agnostic way to talk to a USB device
 * that speaks the Android Open Accessory protocol
 * (AOAv2). The Nexus Receiver (Phase 4) is one
 * such device.
 *
 * Phase 1.10 ships the **skeleton**: the
 * [UsbAccessoryTransport] is a real
 * [ControllerTransport] implementation that opens
 * a USB accessory, reads / writes the input
 * stream, and forwards the events. The actual
 * protocol (the §18 Elysium Nexus Gamepad
 * descriptor, the §19 Elysium Link protocol) is
 * the same as the Bluetooth HID transport; the
 * only difference is the wire layer.
 *
 * ## Why USB Accessory, not USB Host
 *
 * The Nexus Receiver is an *accessory* (it has
 * a host-mode USB port and the Android device
 * acts as the device). The Android `UsbManager`
 * accessory API is the right one. The host-mode
 * API (`UsbDevice`) is for the inverse case
 * (the Android device is the host, the
 * peripheral is a device) and is a different
 * module.
 */
class UsbAccessoryTransport(
    private val context: Context,
    private val scope: CoroutineScope
) : ControllerTransport {

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var accessory: UsbAccessory? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null
    private var readJob: kotlinx.coroutines.Job? = null

    private val _state = MutableStateFlow(TransportState.IDLE)
    override val state: TransportState
        get() = _state.value

    override val capabilities: TransportCapabilities = TransportCapabilities(
        maxRealtimeFps = 1000, // USB bulk transfer is fast
        supportsReliable = true,
        latencyMs = 1, // direct wire
        label = "USB Accessory"
    )

    private val incomingFrames: MutableSharedFlow<UniversalControllerState> =
        MutableSharedFlow(extraBufferCapacity = 8)

    /**
     * The host-side flow of input frames received
     * from the USB accessory. The activity wires
     * this to the engine's `submitTouchPoint` /
     * `submitMotion` paths (for inverse direction;
     * the activity is the device sending input TO
     * the host).
     */
    fun incoming(): SharedFlow<UniversalControllerState> = incomingFrames.asSharedFlow()

    override suspend fun start(): TransportResult {
        // The actual accessory discovery is
        // event-driven (BroadcastReceiver on
        // `ACTION_USB_ACCESSORY_ATTACHED`). For
        // Phase 1.10 the skeleton just transitions
        // to `INITIALISING` and waits. A real
        // accessory listing + selection UI lands
        // in Phase 2 with the first real device.
        _state.value = TransportState.INITIALISING
        return TransportResult.Ok
    }

    override suspend fun pair(): PairingResult {
        // USB Accessory is "paired" by physical
        // connection: the user plugs the accessory
        // into the device. The Paired state is
        // reached when `fileDescriptor` is non-null.
        if (fileDescriptor == null) {
            return PairingResult.Error("No USB accessory connected")
        }
        _state.value = TransportState.PAIRED
        return PairingResult.Ok
    }

    override suspend fun connect(): ConnectionResult {
        if (fileDescriptor == null) {
            return ConnectionResult.Error("No USB accessory connected")
        }
        _state.value = TransportState.CONNECTED
        // Start the read loop on a worker
        // dispatcher. The loop is a coroutine that
        // reads bytes from the input stream and
        // forwards them to the consumer.
        readJob = scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val stream = inputStream ?: return@withContext
                    val buffer = ByteArray(64)
                    while (true) {
                        val n = stream.read(buffer)
                        if (n <= 0) break
                        // The skeleton's protocol is
                        // raw bytes. The real protocol
                        // (Phase 2 with the §18
                        // descriptor) frames the
                        // bytes. For now, we just
                        // surface the bytes via a
                        // log line; the consumer
                        // ignores them.
                        // (Phase 1.10: the wire format
                        // is a placeholder.)
                    }
                } catch (e: Throwable) {
                    // The read loop terminates when
                    // the accessory is disconnected
                    // (the input stream throws).
                    _state.value = TransportState.DISCONNECTED
                }
            }
        }
        return ConnectionResult.Ok
    }

    override suspend fun sendReliable(event: ReliableInputEvent): SendResult = withContext(Dispatchers.IO) {
        try {
            val stream = outputStream ?: return@withContext SendResult.Error("Not connected")
            val frame = encodeFrame(event)
            stream.write(frame)
            stream.flush()
            SendResult.Ok
        } catch (e: Throwable) {
            SendResult.Error("sendReliable failed: ${e.message}")
        }
    }

    override suspend fun sendRealtime(state: UniversalControllerState): SendResult = withContext(Dispatchers.IO) {
        try {
            val stream = outputStream ?: return@withContext SendResult.Error("Not connected")
            val frame = encodeFrame(state)
            stream.write(frame)
            // USB bulk transfer is fast enough that
            // we do not need to flush on every
            // realtime frame. The host's kernel
            // buffers the writes.
            SendResult.Ok
        } catch (e: Throwable) {
            SendResult.Error("sendRealtime failed: ${e.message}")
        }
    }

    override suspend fun releaseAll(): SendResult = sendReliable(ReliableInputEvent.ReleaseAll)

    override suspend fun disconnect(): DisconnectResult {
        readJob?.cancel()
        fileDescriptor?.close()
        fileDescriptor = null
        inputStream = null
        outputStream = null
        accessory = null
        _state.value = TransportState.DISCONNECTED
        return DisconnectResult.Ok
    }

    override suspend fun stop(): TransportResult {
        disconnect()
        _state.value = TransportState.IDLE
        return TransportResult.Ok
    }

    /**
     * Open a [UsbAccessory] for reading / writing.
     * Called by the activity when the
     * `ACTION_USB_ACCESSORY_ATTACHED` broadcast
     * fires. The skeleton's protocol is a
     * placeholder; the real protocol is the
     * §18 / §19 Elysium Link format (Phase 2+).
     */
    fun openAccessory(acc: UsbAccessory) {
        accessory = acc
        fileDescriptor = usbManager.openAccessory(acc)
        if (fileDescriptor == null) {
            _state.value = TransportState.ERROR
            return
        }
        val fd = fileDescriptor!!
        inputStream = FileInputStream(fd.fileDescriptor)
        outputStream = FileOutputStream(fd.fileDescriptor)
    }

    /**
     * Encode a [ReliableInputEvent] as a wire
     * frame. The skeleton's encoding is a 1-byte
     * tag + 0-or-more bytes of payload. The
     * §18 / §19 encoding (Phase 2+) is more
     * elaborate.
     */
    private fun encodeFrame(event: ReliableInputEvent): ByteArray = when (event) {
        is ReliableInputEvent.ReleaseAll -> byteArrayOf(0x01)
        is ReliableInputEvent.ButtonDown -> byteArrayOf(0x02, event.button.ordinal.toByte())
        is ReliableInputEvent.ButtonUp -> byteArrayOf(0x03, event.button.ordinal.toByte())
        is ReliableInputEvent.ProfileChanged -> byteArrayOf(0x04, event.profileId.toByte())
        is ReliableInputEvent.PairingRequest -> byteArrayOf(0x05) + event.hostName.toByteArray()
        is ReliableInputEvent.Revocation -> byteArrayOf(0x06) + event.hostName.toByteArray()
    }

    /**
     * Encode a [UniversalControllerState] as a
     * wire frame. The skeleton's encoding is a
     * placeholder (1-byte tag + raw bytes); the
     * §18 HID encoding (Phase 2+) is the real
     * one.
     */
    private fun encodeFrame(state: UniversalControllerState): ByteArray = byteArrayOf(0x10) +
        state.buttons.encode() + state.leftStick.encode()

    private fun com.elysium.nexus.core.model.ButtonSet.encode(): ByteArray {
        // The skeleton's encoding is the raw
        // 64-bit mask as 8 bytes. The §18 HID
        // encoding maps the canonical buttons to
        // specific bit positions in the report.
        val mask = this.bits
        return byteArrayOf(
            (mask ushr 56 and 0xff).toByte(),
            (mask ushr 48 and 0xff).toByte(),
            (mask ushr 40 and 0xff).toByte(),
            (mask ushr 32 and 0xff).toByte(),
            (mask ushr 24 and 0xff).toByte(),
            (mask ushr 16 and 0xff).toByte(),
            (mask ushr 8 and 0xff).toByte(),
            (mask and 0xff).toByte()
        )
    }

    private fun com.elysium.nexus.core.model.StickState.encode(): ByteArray {
        // 2 bytes: x in [0, 255], y in [0, 255].
        // The skeleton's encoding is a placeholder;
        // the §18 HID encoding uses signed 8-bit
        // values (-127..127).
        return byteArrayOf(
            ((this.x + 1f) * 127.5f).toInt().coerceIn(0, 255).toByte(),
            ((this.y + 1f) * 127.5f).toInt().coerceIn(0, 255).toByte()
        )
    }
}
