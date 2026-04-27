package com.elysium369.meet.core.obd

import com.elysium369.meet.core.transport.TransportInterface
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

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val commandQueue = ObdCommandQueue()
    private val keepAliveManager = KeepAliveManager(this)
    
    private val _liveData = MutableStateFlow<Map<String, Float>>(emptyMap())
    val liveData: StateFlow<Map<String, Float>> = _liveData.asStateFlow()
    
    private var isRunning = false
    private var currentJob: Job? = null
    
    private val _isAdapterPro = MutableStateFlow(false)
    val isAdapterPro: StateFlow<Boolean> = _isAdapterPro.asStateFlow()

    // Detected adapter info
    private var adapterVersion: String = ""
    private var isCloneAdapter: Boolean = true
    private var detectedProtocol: String = ""

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
        if (_state.value == ObdState.CONNECTED || _state.value == ObdState.CONNECTING) return
        
        _state.value = ObdState.CONNECTING
        _statusMessage.value = "Conectando al adaptador..."
        
        try {
            transport.connect()
            _statusMessage.value = "Adaptador conectado. Negociando protocolo..."
            _state.value = ObdState.NEGOTIATING
            
            // ═══════════════════════════════════════════════
            // SECUENCIA DE INICIALIZACIÓN PARA CLONES ELM327
            // Diseñada específicamente para adaptadores baratos
            // que no respetan timing estándar.
            // ═══════════════════════════════════════════════
            initializeAdapter()
            
            _state.value = ObdState.CONNECTED
            _statusMessage.value = "Conectado: $adapterVersion | Protocolo: $detectedProtocol"
            isRunning = true
            
            // Start workers
            startQueueProcessor()
            keepAliveManager.start()
            
        } catch (e: Exception) {
            _state.value = ObdState.ERROR
            _statusMessage.value = "Error: ${e.message}"
            try { transport.disconnect() } catch (_: Exception) {}
        }
    }

    private suspend fun initializeAdapter() {
        // ═══════════════════════════════════════════════════
        // FASE 1: RESET DEL ADAPTADOR
        // Los clones necesitan un reset limpio. Enviamos ATZ
        // y luego esperamos suficiente para que el firmware
        // del PIC/CH340 se reinicie completamente.
        // ═══════════════════════════════════════════════════
        _statusMessage.value = "Reseteando adaptador..."
        
        // Flush: enviar un CR vacío para limpiar cualquier basura en el buffer
        try {
            transport.write("\r".toByteArray())
            delay(100)
            drainInput() // Leer y descartar cualquier basura acumulada
        } catch (_: Exception) {}
        
        // ATZ — Hard reset
        var atzResponse = sendCommandDirectly("ATZ", timeoutMs = 3000)
        
        // Si ATZ no respondió, intentar AT WS (Warm Start — algunos clones no responden a ATZ)
        if (atzResponse.isBlank() || (!atzResponse.contains("ELM", ignoreCase = true) && !atzResponse.contains("OK", ignoreCase = true))) {
            delay(500)
            atzResponse = sendCommandDirectly("AT WS", timeoutMs = 2500)
        }
        
        // Si todavía no hay respuesta, probar un último intento con solo "AT"
        if (atzResponse.isBlank()) {
            delay(300)
            atzResponse = sendCommandDirectly("AT", timeoutMs = 1500)
            if (atzResponse.isBlank()) {
                throw ObdConnectionException("El adaptador no responde a comandos AT. Verifica que esté encendido y emparejado.")
            }
        }
        
        // Parsear la versión del adaptador
        adapterVersion = parseAdapterVersion(atzResponse)
        isCloneAdapter = detectClone(atzResponse)
        _isAdapterPro.value = !isCloneAdapter
        
        // Los clones necesitan un delay generoso después del reset
        delay(if (isCloneAdapter) 1000 else 300)
        
        // ═══════════════════════════════════════════════════
        // FASE 2: CONFIGURACIÓN BÁSICA
        // Cada comando con su delay propio para clones lentos.
        // Enviamos uno por uno y verificamos OK.
        // ═══════════════════════════════════════════════════
        _statusMessage.value = "Configurando adaptador..."
        
        val baseDelay = if (isCloneAdapter) 150L else 50L
        
        // Echo Off — CRÍTICO: sin esto, el clon devuelve el comando como parte de la respuesta
        sendInitCommand("ATE0", baseDelay)
        
        // Linefeeds Off
        sendInitCommand("ATL0", baseDelay)
        
        // Spaces Off — reduce el volumen de datos del clon
        sendInitCommand("ATS0", baseDelay)
        
        // Headers Off
        sendInitCommand("ATH0", baseDelay)
        
        // CAN Auto-Formatting On
        sendInitCommand("ATCAF1", baseDelay)
        
        // Adaptive Timing — para clones usar nivel más conservador
        if (isCloneAdapter) {
            // ATAT2 = Adaptive Timing agresivo (reduce tiempos de espera adaptativamente)
            sendInitCommand("ATAT2", baseDelay)
            // Timeout: 0x96 = 150 * 4ms = 600ms — conservador para clones
            sendInitCommand("ATST96", baseDelay)
        } else {
            sendInitCommand("ATAT1", baseDelay)
            sendInitCommand("ATSTFF", baseDelay)
        }
        
        // ═══════════════════════════════════════════════════
        // FASE 3: DETECCIÓN DE PROTOCOLO Y CONEXIÓN ECU
        // Esta es la parte más crítica. Los clones fallan aquí
        // frecuentemente. Usamos ATSP0 (auto-detect) y luego
        // enviamos 0100 con reintentos generosos.
        // ═══════════════════════════════════════════════════
        _statusMessage.value = "Detectando protocolo del vehículo..."
        
        // ATSP0 = Auto-detect protocol
        sendInitCommand("ATSP0", baseDelay)
        
        // El primer 0100 inicia la búsqueda de protocolo.
        // En clones, esto puede tardar entre 3 y 12 segundos.
        // Hay que ser MUY paciente aquí.
        var ecuConnected = false
        val maxEcuAttempts = 5
        val ecuTimeouts = listOf(8000L, 10000L, 12000L, 8000L, 6000L)
        
        for (attempt in 0 until maxEcuAttempts) {
            _statusMessage.value = "Buscando ECU... intento ${attempt + 1}/$maxEcuAttempts"
            
            val response = sendCommandDirectly("0100", timeoutMs = ecuTimeouts[attempt])
            val upperResponse = response.uppercase()
            
            if (upperResponse.contains("41 00") || upperResponse.contains("4100")) {
                // ¡ECU respondió exitosamente!
                ecuConnected = true
                break
            }
            
            if (upperResponse.contains("UNABLE TO CONNECT")) {
                // El clon no encontró protocolo con este intento.
                // En clones, a veces el segundo intento funciona porque
                // el CAN transceiver ya calentó.
                if (attempt < maxEcuAttempts - 1) {
                    delay(1500)
                    
                    // En el tercer intento, probar protocolo forzado CAN 11bit 500k (el más común)
                    if (attempt == 2) {
                        _statusMessage.value = "Forzando protocolo CAN 11bit 500K..."
                        sendInitCommand("ATSP6", baseDelay)
                        delay(500)
                    }
                    // En el cuarto, probar CAN 29bit 500k
                    if (attempt == 3) {
                        _statusMessage.value = "Probando CAN 29bit 500K..."
                        sendInitCommand("ATSP7", baseDelay)
                        delay(500)
                    }
                    continue
                }
            }
            
            if (upperResponse.contains("BUS INIT") || upperResponse.contains("SEARCHING")) {
                // El adaptador está intentando — darle más tiempo
                delay(2000)
                continue
            }
            
            if (upperResponse.contains("NO DATA") || upperResponse.contains("ERROR")) {
                // Error pero no terminal — reintentar
                delay(1000)
                continue
            }
            
            // Respuesta no reconocida — reintentar
            delay(1000)
        }
        
        if (!ecuConnected) {
            // Último recurso: intentar protocolos uno por uno
            _statusMessage.value = "Búsqueda exhaustiva de protocolo..."
            val protocols = listOf("6", "7", "8", "9", "3", "4", "5", "1", "2")
            for (proto in protocols) {
                sendInitCommand("ATSP$proto", baseDelay)
                delay(500)
                val response = sendCommandDirectly("0100", timeoutMs = 6000)
                val upper = response.uppercase()
                if (upper.contains("41 00") || upper.contains("4100")) {
                    ecuConnected = true
                    break
                }
                if (upper.contains("UNABLE") || upper.contains("NO DATA") || upper.contains("ERROR")) {
                    continue
                }
            }
        }
        
        if (!ecuConnected) {
            throw ObdConnectionException(
                "No se pudo conectar a la ECU del vehículo. " +
                "Verifica que: 1) El motor esté encendido (al menos contacto/ACC), " +
                "2) El adaptador OBD2 esté bien insertado en el puerto, " +
                "3) El vehículo sea compatible con OBD2."
            )
        }
        
        // ═══════════════════════════════════════════════════
        // FASE 4: IDENTIFICAR PROTOCOLO DETECTADO
        // ═══════════════════════════════════════════════════
        _statusMessage.value = "Protocolo encontrado. Verificando..."
        delay(300)
        val dpResponse = sendCommandDirectly("ATDPN", timeoutMs = 2000)
        detectedProtocol = parseProtocolName(dpResponse)
        
        _statusMessage.value = "ECU conectada via $detectedProtocol"
    }

    /**
     * Envía un comando AT de inicialización, espera OK y hace delay.
     * No lanza excepción si falla — los clones a veces no responden OK
     * a ciertos comandos pero siguen funcionando.
     */
    private suspend fun sendInitCommand(command: String, delayMs: Long) {
        try {
            val response = sendCommandDirectly(command, timeoutMs = 1500)
            // Algunos clones no responden "OK" pero sí aceptan el comando
            delay(delayMs)
        } catch (_: Exception) {
            delay(delayMs)
        }
    }

    /**
     * Drena todo el input acumulado en el buffer del stream.
     * Útil después de un reset para limpiar basura.
     */
    private suspend fun drainInput() {
        withContext(Dispatchers.IO) {
            var drained = 0
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 300) {
                val chunk = transport.read(1024)
                if (chunk != null) {
                    drained += chunk.size
                } else {
                    break
                }
            }
        }
    }

    private suspend fun sendCommandDirectly(command: String, timeoutMs: Long = 3000L): String {
        return withContext(Dispatchers.IO) {
            transport.write("$command\r".toByteArray())
            readResponse(timeoutMs)
        }
    }

    private suspend fun readResponse(timeoutMs: Long = 3000L): String = 
        withContext(Dispatchers.IO) {
        
        val buffer = StringBuilder()
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val chunk = transport.read(256)
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
                    current.contains("CAN ERROR") ||
                    current.contains("FB ERROR") ||
                    current.contains("DATA ERROR") ||
                    current.contains("BUFFER FULL") ||   // clon saturado
                    current.contains("RX ERROR") ||      // clon corrupto
                    current.contains("ACT ALERT") ||     // battery voltage warning
                    current.contains("LP ALERT") ||      // low power alert
                    current.contains("STOPPED") ||       // user interrupt
                    current.contains("?")) {             // comando no reconocido
                    break
                }
            } else {
                delay(15) // Ceder CPU entre lecturas, 15ms es buen balance para clones
            }
        }
        
        // Limpiar la respuesta antes de retornarla
        return@withContext cleanResponse(buffer.toString())
    }

    private fun cleanResponse(raw: String): String {
        return raw
            .replace("\r", " ")
            .replace("\n", " ")
            .replace(">", "")      // quitar el prompt
            .replace("SEARCHING...", "") // algunos clones lo insertan
            .replace("BUS INIT: ...OK", "") // mensaje de init
            .replace("BUS INIT: OK", "")
            .replace("  ", " ")    // normalizar espacios dobles
            .trim()
    }
    
    private fun parseAdapterVersion(response: String): String {
        // Buscar patrones como "ELM327 v1.5" o "ELM327 v2.1"
        val regex = Regex("(ELM327|STN\\d+|OBDLink|vLinker)[\\s]*v?[\\d.]+", RegexOption.IGNORE_CASE)
        return regex.find(response)?.value?.trim() ?: response.take(30).trim()
    }
    
    private fun detectClone(response: String): Boolean {
        val upper = response.uppercase()
        // Adaptadores genuinos conocidos
        if (upper.contains("STN1") || upper.contains("STN2") || 
            upper.contains("OBDLINK") || upper.contains("KIWI") ||
            upper.contains("VLINKER")) {
            return false
        }
        // Todo lo que dice ELM327 v1.5 o v2.1 es clon (los genuinos ELM327 son v2.2+)
        return upper.contains("V1.5") || upper.contains("V2.1") || 
               upper.contains("V1.3") || upper.contains("V1.4") ||
               !upper.contains("V2.2")
    }
    
    private fun parseProtocolName(response: String): String {
        val clean = response.uppercase().trim()
        return when {
            clean.contains("A6") || clean == "6" -> "CAN 11bit/500K"
            clean.contains("A7") || clean == "7" -> "CAN 29bit/500K"
            clean.contains("A8") || clean == "8" -> "CAN 11bit/250K"
            clean.contains("A9") || clean == "9" -> "CAN 29bit/250K"
            clean.contains("A3") || clean == "3" -> "ISO 9141-2"
            clean.contains("A4") || clean == "4" -> "ISO 14230 KWP"
            clean.contains("A5") || clean == "5" -> "ISO 14230 KWP Fast"
            clean.contains("A1") || clean == "1" -> "SAE J1850 PWM"
            clean.contains("A2") || clean == "2" -> "SAE J1850 VPW"
            else -> "Auto ($clean)"
        }
    }
    
    suspend fun sendKeepAliveDirectly(command: String) {
        withContext(Dispatchers.IO) {
            try {
                transport.write(command.toByteArray())
            } catch (_: Exception) {
                // Silenciar errores del keepalive
            }
        }
    }
    
    fun notifyChannelDead() {
        _state.value = ObdState.ERROR
        _statusMessage.value = "Conexión perdida con el adaptador"
        disconnect()
    }

    private fun startQueueProcessor() {
        currentJob = scope.launch(Dispatchers.IO) {
            while (isRunning && isActive) {
                val command = commandQueue.dequeue()
                if (command != null) {
                    var success = false
                    var attempts = 0
                    val maxRetries = 3
                    var lastException: Exception? = null

                    while (!success && attempts < maxRetries && isRunning) {
                        try {
                            transport.write("${command.query}\r".toByteArray())
                            val response = readResponse(timeoutMs = 2500L + (attempts * 1500L))
                            
                            val upper = response.uppercase()
                            if (upper.isEmpty()) {
                                lastException = Exception("CAN BUS TIMEOUT: No response")
                                attempts++
                                delay(150)
                            } else if (upper.contains("NO DATA") || upper.contains("?")) {
                                lastException = Exception("CAN BUS NO DATA / ERROR: $response")
                                attempts++
                                delay(150) // Cooling time para el bus CAN
                            } else if (upper.contains("UNABLE") || upper.contains("ERROR")) {
                                lastException = Exception("ECU Error: $response")
                                attempts++
                                delay(300)
                            } else {
                                success = true
                                command.onSuccess(response)
                            }
                        } catch (e: Exception) {
                            lastException = e
                            attempts++
                            delay(200)
                        }
                    }

                    if (!success) {
                        command.onError(lastException ?: Exception("Max retries exceeded on CAN BUS"))
                        if (lastException is java.io.IOException) {
                            notifyChannelDead()
                            break // Fatal transport error, detiene la cola
                        }
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
    
    // Terminal mode functionality
    suspend fun sendRawCommand(command: String): String {
        if (_state.value != ObdState.CONNECTED) {
            throw IllegalStateException("OBD is not connected")
        }
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
        _statusMessage.value = "Desconectado"
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
