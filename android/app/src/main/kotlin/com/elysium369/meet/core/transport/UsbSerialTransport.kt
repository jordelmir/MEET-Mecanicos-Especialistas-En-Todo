package com.elysium369.meet.core.transport

import android.hardware.usb.*
import android.util.Log
import com.elysium369.meet.core.obd.TransportLinkEvent
import com.elysium369.meet.core.obd.TransportLinkState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * UsbSerialTransport — High-performance USB OTG UART transport for OBD2 adapters.
 * Supports USB CDC ACM, FTDI, CH340, CP210x, and PL2303 via Android USB Host API.
 */
class UsbSerialTransport(
    private val usbManager: UsbManager,
    private val device: UsbDevice,
    private val baudRate: Int = 115200,
) : ByteStreamTransport {

    companion object {
        private const val TAG = "EV_USB_SERIAL"
        private const val MAX_RX_BUFFER_SIZE = 65536

        // Recognized USB UART Chipsets
        val FTDI_VIDS = setOf(0x0403)
        val CH340_VIDS = setOf(0x1A86)
        val CP210X_VIDS = setOf(0x10C4)
        val PL2303_VIDS = setOf(0x067B)
    }

    private val _linkState = MutableStateFlow<TransportLinkState>(TransportLinkState.Disconnected)
    override val linkState: StateFlow<TransportLinkState> = _linkState.asStateFlow()

    private val _linkEvents = MutableSharedFlow<TransportLinkEvent>(extraBufferCapacity = 32)
    override val linkEvents: SharedFlow<TransportLinkEvent> = _linkEvents.asSharedFlow()

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null

    private val rxRingBuffer = CircularByteRingBuffer(MAX_RX_BUFFER_SIZE)
    private var readerJob: Job? = null
    private val mutex = Mutex()

    override val isConnected: Boolean
        get() = connection != null && inEndpoint != null && outEndpoint != null

    override suspend fun connect() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                Log.i(TAG, "Connecting to USB Serial device: VID=0x${device.vendorId.toString(16)} PID=0x${device.productId.toString(16)}")
                _linkState.value = TransportLinkState.Connecting
                _linkEvents.tryEmit(TransportLinkEvent.StateChanged(TransportLinkState.Connecting))

                if (!usbManager.hasPermission(device)) {
                    val ex = SecurityException("Falta permiso USB para el dispositivo VID=0x${device.vendorId.toString(16)}")
                    _linkState.value = TransportLinkState.IoFailure(ex)
                    _linkEvents.tryEmit(TransportLinkEvent.IoFailure(ex, System.nanoTime() / 1_000_000L))
                    throw ex
                }

                val conn = usbManager.openDevice(device) ?: run {
                    val ex = java.io.IOException("No se pudo abrir la conexión USB con el dispositivo")
                    _linkState.value = TransportLinkState.IoFailure(ex)
                    _linkEvents.tryEmit(TransportLinkEvent.IoFailure(ex, System.nanoTime() / 1_000_000L))
                    throw ex
                }

                // Find bulk IN and OUT endpoints
                var selectedInterface: UsbInterface? = null
                var bulkIn: UsbEndpoint? = null
                var bulkOut: UsbEndpoint? = null

                for (i in 0 until device.interfaceCount) {
                    val intf = device.getInterface(i)
                    var foundIn: UsbEndpoint? = null
                    var foundOut: UsbEndpoint? = null

                    for (j in 0 until intf.endpointCount) {
                        val ep = intf.getEndpoint(j)
                        if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            if (ep.direction == UsbConstants.USB_DIR_IN) foundIn = ep
                            else if (ep.direction == UsbConstants.USB_DIR_OUT) foundOut = ep
                        }
                    }

                    if (foundIn != null && foundOut != null) {
                        selectedInterface = intf
                        bulkIn = foundIn
                        bulkOut = foundOut
                        break
                    }
                }

                if (selectedInterface == null || bulkIn == null || bulkOut == null) {
                    conn.close()
                    val ex = java.io.IOException("No se encontraron endpoints Bulk IN/OUT válidos en el dispositivo USB")
                    _linkState.value = TransportLinkState.IoFailure(ex)
                    _linkEvents.tryEmit(TransportLinkEvent.IoFailure(ex, System.nanoTime() / 1_000_000L))
                    throw ex
                }

                if (!conn.claimInterface(selectedInterface, true)) {
                    conn.close()
                    val ex = java.io.IOException("No se pudo reclamar la interfaz USB")
                    _linkState.value = TransportLinkState.IoFailure(ex)
                    _linkEvents.tryEmit(TransportLinkEvent.IoFailure(ex, System.nanoTime() / 1_000_000L))
                    throw ex
                }

                connection = conn
                usbInterface = selectedInterface
                inEndpoint = bulkIn
                outEndpoint = bulkOut

                configureBaudRate(conn, baudRate)
                startReaderWorker()

                _linkState.value = TransportLinkState.Connected
                _linkEvents.tryEmit(TransportLinkEvent.StateChanged(TransportLinkState.Connected))
                Log.i(TAG, "USB Serial conectado exitosamente a $baudRate baudios")
            }
        }
    }


    private fun configureBaudRate(conn: UsbDeviceConnection, baudRate: Int) {
        try {
            // Standard CDC ACM Set Line Coding: [BaudRate(4B LE), StopBits(1B), Parity(1B), DataBits(1B)]
            val lineCoding = ByteArray(7).apply {
                this[0] = (baudRate and 0xFF).toByte()
                this[1] = ((baudRate shr 8) and 0xFF).toByte()
                this[2] = ((baudRate shr 16) and 0xFF).toByte()
                this[3] = ((baudRate shr 24) and 0xFF).toByte()
                this[4] = 0 // 1 Stop Bit
                this[5] = 0 // No Parity
                this[6] = 8 // 8 Data Bits
            }
            conn.controlTransfer(0x21, 0x20, 0, 0, lineCoding, lineCoding.size, 1000)
            // Set DTR + RTS
            conn.controlTransfer(0x21, 0x22, 0x03, 0, null, 0, 1000)
        } catch (e: Exception) {
            Log.w(TAG, "Configuración de velocidad USB CDC no soportada o genérica: ${e.message}")
        }
    }

    override fun abortConnect() {
        disconnectInternal()
    }

    private fun startReaderWorker() {
        readerJob?.cancel()
        rxRingBuffer.reset()
        val conn = connection ?: return
        val epIn = inEndpoint ?: return

        readerJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(2048)
            try {
                while (isActive && isConnected) {
                    val bytesRead = conn.bulkTransfer(epIn, buffer, buffer.size, 500)
                    if (bytesRead > 0) {
                        val timestampMs = System.nanoTime() / 1_000_000L
                        _linkEvents.tryEmit(TransportLinkEvent.BytesReceived(bytesRead, timestampMs))
                        val dropped = rxRingBuffer.write(buffer, 0, bytesRead)
                        if (dropped > 0) {
                            Log.w(TAG, "USB Serial rx ring buffer overflow: dropped $dropped bytes")
                            _linkEvents.tryEmit(TransportLinkEvent.BufferOverflow(dropped, timestampMs))
                        }
                    }
                }
            } catch (e: Exception) {
                if (_linkState.value is TransportLinkState.Connected) {
                    val timestampMs = System.nanoTime() / 1_000_000L
                    _linkState.value = TransportLinkState.RemoteClosed("USB IO Error: ${e.message}", timestampMs)
                    _linkEvents.tryEmit(TransportLinkEvent.RemoteClosed("USB IO Error: ${e.message}", timestampMs))
                }
            }
        }
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
            usbInterface?.let { connection?.releaseInterface(it) }
            connection?.close()
        } catch (_: Exception) {}
        connection = null
        usbInterface = null
        inEndpoint = null
        outEndpoint = null
        rxRingBuffer.reset()
    }

    override suspend fun write(data: ByteArray) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val conn = connection ?: throw java.io.IOException("USB Serial no conectado")
                val epOut = outEndpoint ?: throw java.io.IOException("USB Endpoint OUT no disponible")

                val transferred = conn.bulkTransfer(epOut, data, data.size, 1000)
                if (transferred < 0) {
                    throw java.io.IOException("Error en transferencia bulk USB: transferred=$transferred")
                }
                _linkEvents.tryEmit(TransportLinkEvent.BytesSent(transferred, System.nanoTime() / 1_000_000L))
            }
        }
    }


    override suspend fun read(maxBytes: Int, timeoutMs: Long): ByteArray? {
        val deadlineNanos = System.nanoTime() + timeoutMs * 1_000_000L
        while (System.nanoTime() < deadlineNanos) {
            if (rxRingBuffer.hasPromptOrCapacity(maxBytes)) {
                return rxRingBuffer.readAvailable()
            }
            delay(2)
        }
        return rxRingBuffer.readAvailable()
    }

    override suspend fun drain() {
        rxRingBuffer.reset()
    }
}
