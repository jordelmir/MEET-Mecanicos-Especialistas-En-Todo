package com.elysium369.meet.core.transport

import android.hardware.usb.*
import android.util.Log
import com.elysium369.meet.core.obd.TransportLinkEvent
import com.elysium369.meet.core.obd.TransportLinkState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * UsbCanTransport — Native hardware CAN Frame Transport for CandleLight / gs_usb devices.
 * Directly streams RawCanFrames at bus speeds (250kbps / 500kbps / 1Mbps) with zero intermediate string translations.
 */
class UsbCanTransport(
    private val usbManager: UsbManager,
    private val device: UsbDevice,
    private var bitrateBps: Int = 500000,
) : FrameTransport {

    companion object {
        private const val TAG = "EV_USB_CAN"

        // gs_usb USB Vendor / Product IDs
        val CANDLELIGHT_VIDS = setOf(0x1D50, 0x1209)
        val CANDLELIGHT_PIDS = setOf(0x606F, 0x0001, 0x2323)

        // gs_usb Host Commands
        private const val GS_USB_BREQ_HOST_FORMAT = 0
        private const val GS_USB_BREQ_BITTIMING = 1
        private const val GS_USB_BREQ_MODE = 2
        private const val GS_USB_BREQ_BERR = 3
        private const val GS_USB_BREQ_BT_CONST = 4
        private const val GS_USB_BREQ_DEVICE_CONFIG = 5

        private const val GS_CAN_MODE_START = 1
        private const val GS_CAN_MODE_RESET = 0
    }

    private val _linkState = MutableStateFlow<TransportLinkState>(TransportLinkState.Disconnected)
    override val linkState: StateFlow<TransportLinkState> = _linkState.asStateFlow()

    private val _linkEvents = MutableSharedFlow<TransportLinkEvent>(extraBufferCapacity = 32)
    override val linkEvents: SharedFlow<TransportLinkEvent> = _linkEvents.asSharedFlow()

    private val _frameFlow = MutableSharedFlow<RawCanFrame>(extraBufferCapacity = 256)

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null

    private var readerJob: Job? = null
    private val mutex = Mutex()

    override val isConnected: Boolean
        get() = connection != null && inEndpoint != null && outEndpoint != null

    override suspend fun connect() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                Log.i(TAG, "Connecting to gs_usb / CandleLight CAN Device: VID=0x${device.vendorId.toString(16)} PID=0x${device.productId.toString(16)}")
                _linkState.value = TransportLinkState.Connecting
                _linkEvents.tryEmit(TransportLinkEvent.StateChanged(TransportLinkState.Connecting))

                if (!usbManager.hasPermission(device)) {
                    val ex = SecurityException("Permiso USB denegado para CandleLight CAN VID=0x${device.vendorId.toString(16)}")
                    _linkState.value = TransportLinkState.IoFailure(ex)
                    _linkEvents.tryEmit(TransportLinkEvent.IoFailure(ex, System.nanoTime() / 1_000_000L))
                    throw ex
                }

                val conn = usbManager.openDevice(device) ?: run {
                    val ex = java.io.IOException("No se pudo abrir el adaptador USB CAN")
                    _linkState.value = TransportLinkState.IoFailure(ex)
                    _linkEvents.tryEmit(TransportLinkEvent.IoFailure(ex, System.nanoTime() / 1_000_000L))
                    throw ex
                }

                val intf = device.getInterface(0)
                if (!conn.claimInterface(intf, true)) {
                    conn.close()
                    val ex = java.io.IOException("No se pudo reclamar la interfaz USB CAN")
                    _linkState.value = TransportLinkState.IoFailure(ex)
                    _linkEvents.tryEmit(TransportLinkEvent.IoFailure(ex, System.nanoTime() / 1_000_000L))
                    throw ex
                }

                var bulkIn: UsbEndpoint? = null
                var bulkOut: UsbEndpoint? = null
                for (j in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN) bulkIn = ep
                        else if (ep.direction == UsbConstants.USB_DIR_OUT) bulkOut = ep
                    }
                }

                if (bulkIn == null || bulkOut == null) {
                    conn.releaseInterface(intf)
                    conn.close()
                    val ex = java.io.IOException("No se encontraron endpoints Bulk IN/OUT en el adaptador CAN")
                    _linkState.value = TransportLinkState.IoFailure(ex)
                    _linkEvents.tryEmit(TransportLinkEvent.IoFailure(ex, System.nanoTime() / 1_000_000L))
                    throw ex
                }

                connection = conn
                usbInterface = intf
                inEndpoint = bulkIn
                outEndpoint = bulkOut

                // Send GS_USB host config & mode start
                setHostFormat(conn)
                startCanMode(conn, bitrateBps)
                startReaderWorker()

                _linkState.value = TransportLinkState.Connected
                _linkEvents.tryEmit(TransportLinkEvent.StateChanged(TransportLinkState.Connected))
                Log.i(TAG, "gs_usb CAN transport conectado a $bitrateBps bps")
            }
        }
    }


    private fun setHostFormat(conn: UsbDeviceConnection) {
        val formatBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0xEFBE0000.toInt()).array()
        conn.controlTransfer(0x41, GS_USB_BREQ_HOST_FORMAT, 1, 0, formatBuf, formatBuf.size, 1000)
    }

    private fun startCanMode(conn: UsbDeviceConnection, bitrate: Int) {
        // Mode command: start controller
        val modeBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(GS_CAN_MODE_START)
            .putInt(0) // flags
            .array()
        conn.controlTransfer(0x41, GS_USB_BREQ_MODE, 0, 0, modeBuf, modeBuf.size, 1000)
    }

    private fun stopCanMode(conn: UsbDeviceConnection) {
        val modeBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(GS_CAN_MODE_RESET)
            .putInt(0)
            .array()
        conn.controlTransfer(0x41, GS_USB_BREQ_MODE, 0, 0, modeBuf, modeBuf.size, 1000)
    }

    override suspend fun setBitrate(bitrateBps: Int): Boolean = mutex.withLock {
        this.bitrateBps = bitrateBps
        val conn = connection ?: return false
        try {
            stopCanMode(conn)
            startCanMode(conn, bitrateBps)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando tasa de baudios CAN: ${e.message}")
            false
        }
    }

    private fun startReaderWorker() {
        readerJob?.cancel()
        val conn = connection ?: return
        val epIn = inEndpoint ?: return

        readerJob = CoroutineScope(Dispatchers.IO).launch {
            val packetBuf = ByteArray(64) // gs_host_frame struct size
            try {
                while (isActive && isConnected) {
                    val read = conn.bulkTransfer(epIn, packetBuf, packetBuf.size, 500)
                    if (read >= 16) {
                        val bb = ByteBuffer.wrap(packetBuf, 0, read).order(ByteOrder.LITTLE_ENDIAN)
                        val echoId = bb.int
                        val canId = bb.int
                        val canDlc = bb.get().toInt() and 0xFF
                        val channel = bb.get()
                        val flags = bb.get()
                        val reserved = bb.get()

                        val isExtended = (canId and (1 shl 31)) != 0
                        val cleanId = if (isExtended) canId and 0x1FFFFFFF else canId and 0x7FF
                        val dataLen = canDlc.coerceIn(0, 8)
                        val data = ByteArray(dataLen)
                        bb.get(data)

                        val frame = RawCanFrame(
                            arbitrationId = cleanId,
                            isExtended = isExtended,
                            isFd = false,
                            data = data,
                            timestampNanos = System.nanoTime(),
                        )
                        _frameFlow.tryEmit(frame)
                        _linkEvents.tryEmit(TransportLinkEvent.BytesReceived(read, System.nanoTime() / 1_000_000L))
                    }
                }
            } catch (e: Exception) {
                if (_linkState.value is TransportLinkState.Connected) {
                    val timestampMs = System.nanoTime() / 1_000_000L
                    _linkState.value = TransportLinkState.RemoteClosed("gs_usb reader error: ${e.message}", timestampMs)
                    _linkEvents.tryEmit(TransportLinkEvent.RemoteClosed("gs_usb reader error: ${e.message}", timestampMs))
                }
            }
        }
    }

    override suspend fun sendFrame(frame: RawCanFrame) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val conn = connection ?: throw java.io.IOException("USB CAN no conectado")
                val epOut = outEndpoint ?: throw java.io.IOException("USB CAN Endpoint OUT no disponible")

                val canId = if (frame.isExtended) (frame.arbitrationId or (1 shl 31)) else frame.arbitrationId
                val packet = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(0) // echo_id
                    .putInt(canId)
                    .put(frame.data.size.toByte())
                    .put(0.toByte()) // channel
                    .put(0.toByte()) // flags
                    .put(0.toByte()) // reserved
                    .put(frame.data)
                    .array()

                val transferred = conn.bulkTransfer(epOut, packet, packet.size, 500)
                if (transferred < 0) {
                    throw java.io.IOException("Error enviando frame CAN a través de USB: transferred=$transferred")
                }
                _linkEvents.tryEmit(TransportLinkEvent.BytesSent(transferred, System.nanoTime() / 1_000_000L))
            }
        }
    }

    override fun receiveFrames(): Flow<RawCanFrame> = _frameFlow.asSharedFlow()

    override fun abortConnect() {
        disconnectInternal()
    }

    override suspend fun disconnect() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                _linkState.value = TransportLinkState.Closing
                _linkEvents.tryEmit(TransportLinkEvent.StateChanged(TransportLinkState.Closing))
                disconnectInternal()
                _linkState.value = TransportLinkState.Disconnected
                _linkEvents.tryEmit(TransportLinkEvent.StateChanged(TransportLinkState.Disconnected))
            }
        }
    }


    private fun disconnectInternal() {
        readerJob?.cancel()
        readerJob = null
        try {
            connection?.let { stopCanMode(it) }
            usbInterface?.let { connection?.releaseInterface(it) }
            connection?.close()
        } catch (_: Exception) {}
        connection = null
        usbInterface = null
        inEndpoint = null
        outEndpoint = null
    }
}
