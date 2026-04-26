package com.elysium369.meet.core.obd

import com.elysium369.meet.core.transport.TransportInterface
import com.elysium369.meet.core.keepalive.KeepAliveManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class ObdState {
    DISCONNECTED,
    CONNECTING,
    NEGOTIATING,
    CONNECTED,
    ERROR
}

class ObdSession(
    private var transport: TransportInterface,
    private val scope: CoroutineScope,
    private val bluetoothAdapter: android.bluetooth.BluetoothAdapter?
) {
    private val _state = MutableStateFlow(ObdState.DISCONNECTED)
    val state: StateFlow<ObdState> = _state.asStateFlow()

    private val commandQueue = ObdCommandQueue()
    private val keepAliveManager = KeepAliveManager(this)
    
    private val _liveData = MutableStateFlow<Map<String, Float>>(emptyMap())
    val liveData: StateFlow<Map<String, Float>> = _liveData.asStateFlow()
    
    private var isRunning = false
    private var currentJob: Job? = null
    
    private val _isAdapterPro = MutableStateFlow(false)
    val isAdapterPro: StateFlow<Boolean> = _isAdapterPro.asStateFlow()

    fun setTargetAddress(address: String) {
        // Simple heuristic: if it contains a dot or port colon, it's WiFi
        if (address.contains(".") || address.contains(":35000")) {
            val parts = address.split(":")
            val ip = parts.getOrNull(0) ?: "192.168.0.10"
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 35000
            transport = com.elysium369.meet.core.transport.WifiTransport(ip, port)
        } else {
            // Otherwise assume BT Classic MAC Address
            if (transport is com.elysium369.meet.core.transport.BtClassicTransport) {
                (transport as com.elysium369.meet.core.transport.BtClassicTransport).macAddress = address
            } else if (bluetoothAdapter != null) {
                // If current transport is WiFi, switch back to BT
                transport = com.elysium369.meet.core.transport.BtClassicTransport(address, bluetoothAdapter)
            }
        }
    }

    suspend fun connect() {
        if (_state.value != ObdState.DISCONNECTED) return
        
        _state.value = ObdState.CONNECTING
        try {
            transport.connect()
            _state.value = ObdState.NEGOTIATING
            
            // 1. Adapter initialization sequence (clone-safe)
            initializeAdapter()
            
            _state.value = ObdState.CONNECTED
            isRunning = true
            
            // Start workers
            startQueueProcessor()
            keepAliveManager.start()
            
        } catch (e: Exception) {
            _state.value = ObdState.ERROR
            disconnect()
        }
    }

    private suspend fun initializeAdapter() {
        // Send initial commands sequentially without relying on queue yet
        // 38400 baud is safe for most clones. We disable echo, headers, spaces.
        sendCommandDirectly("ATZ") // Reset
        delay(500)
        sendCommandDirectly("ATE0") // Echo off
        sendCommandDirectly("ATL0") // Linefeeds off
        sendCommandDirectly("ATS0") // Spaces off
        sendCommandDirectly("ATH0") // Headers off
        sendCommandDirectly("ATSP0") // Auto protocol
        
        // Detect adapter type
        val atI = sendCommandDirectly("ATI").uppercase()
        val isClone = atI.contains("V1.5") || atI.contains("V2.1") || atI.contains("ELM327") || atI.contains("CH340") || atI.contains("PIC18")
        val isPro = atI.contains("VLINKER") || atI.contains("OBDLINK") || atI.contains("STN") || atI.contains("ELM327 V2.2")
        // Prefer PRO features if explicit PRO string found, otherwise default to clone limitations to be safe
        _isAdapterPro.value = isPro || !isClone
        
        // Check if it's connected to ECU
        val response = sendCommandDirectly("0100")
        if (response.contains("UNABLE TO CONNECT") || response.contains("ERROR")) {
            // Fallback strategy could be implemented here
            throw Exception("Failed to connect to ECU: $response")
        }
    }

    private suspend fun sendCommandDirectly(command: String): String {
        return withContext(Dispatchers.IO) {
            transport.write("$command\r".toByteArray())
            readResponse()
        }
    }

    private suspend fun readResponse(timeoutMs: Long = 3000L): String = 
        withContext(Dispatchers.IO) {
        
        val buffer = StringBuilder()
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val chunk = transport.read(64)
            if (chunk != null) {
                val str = String(chunk, Charsets.ISO_8859_1)
                buffer.append(str)
                keepAliveManager.notifyBytesReceived() // resetear timer
                
                // EL CONTRATO ELM327: cuando llega ">" la respuesta está completa
                if (buffer.contains('>')) {
                    break
                }
                
                // Detectar errores del clon — respuesta inmediata sin esperar timeout
                val current = buffer.toString().uppercase()
                if (current.contains("NO DATA") ||
                    current.contains("UNABLE TO CONNECT") ||
                    current.contains("BUS INIT") ||
                    current.contains("CAN ERROR") ||
                    current.contains("FB ERROR") ||
                    current.contains("DATA ERROR") ||
                    current.contains("BUFFER FULL") ||   // clon saturado
                    current.contains("RX ERROR")) {      // clon corrupto
                    break
                }
            } else {
                delay(10) // NO busy-wait, ceder CPU entre lecturas
            }
        }
        
        // Limpiar la respuesta antes de retornarla
        return@withContext cleanResponse(buffer.toString())
    }

    private fun cleanResponse(raw: String): String {
        return raw
            .replace("\r", "")
            .replace("\n", "")
            .replace(">", "")      // quitar el prompt
            .replace("SEARCHING...", "") // algunos clones lo insertan en la respuesta
            .trim()
    }
    
    suspend fun sendKeepAliveDirectly(command: String) {
        withContext(Dispatchers.IO) {
            transport.write(command.toByteArray())
        }
    }
    
    fun notifyChannelDead() {
        // Here we could trigger ReconnectPolicy
        _state.value = ObdState.ERROR
        disconnect()
    }

    private fun startQueueProcessor() {
        currentJob = scope.launch(Dispatchers.IO) {
            while (isRunning && isActive) {
                val command = commandQueue.dequeue()
                if (command != null) {
                    try {
                        transport.write("${command.query}\r".toByteArray())
                        val response = readResponse()
                        
                        if (response.contains("NO DATA") || response.contains("?")) {
                            // Ignorar, no desconectar por esto
                            command.onError(Exception("Invalid response: $response"))
                        } else {
                            command.onSuccess(response)
                        }
                    } catch (e: Exception) {
                        command.onError(e)
                    }
                } else {
                    delay(50) // Prevent tight loop if queue is empty
                }
            }
        }
    }

    fun enqueueCommand(
        query: String,
        priority: Int = 1,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (!isRunning) return
        commandQueue.enqueue(ObdCommand(query, priority, onSuccess, onError))
    }
    
    // Terminal mode functionality: bypasses queue if strictly needed, or just enqueues with highest priority
    suspend fun sendRawCommand(command: String): String {
        if (_state.value != ObdState.CONNECTED) {
            throw IllegalStateException("OBD is not connected")
        }
        // Suspend until we get a result
        val deferredResult = CompletableDeferred<String>()
        commandQueue.enqueue(ObdCommand(
            query = command,
            priority = 999, // Max priority for user manual commands
            onSuccess = { deferredResult.complete(it) },
            onError = { deferredResult.completeExceptionally(it) }
        ))
        return deferredResult.await()
    }

    fun disconnect() {
        isRunning = false
        currentJob?.cancel()
        keepAliveManager.stop()
        scope.launch {
            try { transport.disconnect() } catch (e: Exception) { }
        }
        _state.value = ObdState.DISCONNECTED
    }
}

data class ObdCommand(
    val query: String,
    val priority: Int,
    val onSuccess: (String) -> Unit,
    val onError: (Exception) -> Unit
)

class ObdCommandQueue {
    private val queue = mutableListOf<ObdCommand>()
    
    @Synchronized
    fun enqueue(command: ObdCommand) {
        queue.add(command)
        // Sort by priority (higher number = higher priority)
        queue.sortByDescending { it.priority }
    }
    
    @Synchronized
    fun dequeue(): ObdCommand? {
        if (queue.isEmpty()) return null
        return queue.removeAt(0)
    }
    
    @Synchronized
    fun clear() {
        queue.clear()
    }
}
