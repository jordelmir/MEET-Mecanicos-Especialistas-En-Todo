package com.elysium369.meet.core.obd

import android.util.Log
import com.elysium369.meet.core.transport.TransportInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ElmNegotiator(private val transport: TransportInterface) {

    private val TAG = "MEET_NEGOTIATOR"

    data class AdapterProfile(
        val chipVersion: String,
        val isClone: Boolean,
        val isSTN: Boolean,
        val detectedProtocol: ObdProtocol,
        val baseDelayMs: Long,
        val maxLineLength: Int
    )

    /**
     * Executes the full ELM327/STN negotiation sequence.
     * @param hintProtocol Optional protocol to try first to speed up connection
     * @param onProgress Callback for UI status updates
     */
    suspend fun negotiate(
        hintProtocol: ObdProtocol = ObdProtocol.AUTO,
        onProgress: (String) -> Unit
    ): AdapterProfile {
        Log.i(TAG, "═══ NEGOTIATION START ═══ (hint=${hintProtocol.displayName})")
        transport.drain()
        
        // 1. Physical Warm-up
        onProgress("Sincronizando enlace físico...")
        for (i in 1..2) {
            transport.write("\r".toByteArray())
            delay(50)
        }
        transport.drain()

        // 2. Identification (ATZ / AT WS)
        onProgress("Identificando adaptador...")
        var idResponse = ""
        for (attempt in 1..3) {
            idResponse = sendWithTimeout("ATZ\r", 2500)
            if (isValidIdResponse(idResponse)) break
            
            if (attempt == 2) {
                idResponse = sendWithTimeout("AT WS\r", 2000)
                if (isValidIdResponse(idResponse)) break
            }
            delay(300)
            transport.drain()
        }

        // Blind fallback for silent clones
        if (!isValidIdResponse(idResponse)) {
            val blindTest = sendWithTimeout("0100\r", 3000)
            if (blindTest.contains("4100") || blindTest.contains("41 00")) {
                idResponse = "ELM327 v1.5 (Silent Clone)"
            } else {
                throw ObdConnectionException("Adaptador no responde. Verifica el encendido.")
            }
        }

        val isClone = detectClone(idResponse)
        val chipVersion = parseChipVersion(idResponse)
        
        // Test for STN specific support
        val stiResponse = sendWithTimeout("STI\r", 800)
        val isSTN = stiResponse.contains("STN", true) || idResponse.contains("STN", true) || idResponse.contains("vLinker", true)

        val baseDelay = if (isClone) 30L else 15L
        
        Log.i(TAG, "Adapter Identified: $chipVersion | Clone=$isClone | STN=$isSTN")
        onProgress("Adaptador: $chipVersion")

        // 3. Stabilization
        delay(if (isClone) 800 else 100)
        transport.drain()

        // 4. Initial AT Configuration
        val initSequence = buildInitSequence(isClone, isSTN)
        for (cmd in initSequence) {
            sendWithTimeout("$cmd\r", 800)
            delay(baseDelay)
        }

        // 5. Protocol Negotiation
        onProgress("Buscando protocolo del vehículo...")
        val protocol = sweepProtocols(hintProtocol, isClone, baseDelay, onProgress)

        Log.i(TAG, "═══ NEGOTIATION SUCCESS ═══ Protocol: ${protocol.displayName}")
        
        return AdapterProfile(
            chipVersion = chipVersion,
            isClone = isClone,
            isSTN = isSTN,
            detectedProtocol = protocol,
            baseDelayMs = baseDelay,
            maxLineLength = if (isClone) 64 else 512
        )
    }

    private suspend fun sweepProtocols(
        hint: ObdProtocol,
        isClone: Boolean,
        baseDelay: Long,
        onProgress: (String) -> Unit
    ): ObdProtocol {
        // Step 5a: Try hint first if specific
        if (hint != ObdProtocol.AUTO) {
            onProgress("Probando protocolo guardado: ${hint.displayName}...")
            sendWithTimeout("ATSP${hint.atspCode}\r", 1000)
            delay(baseDelay)
            val resp = sendWithTimeout("0100\r", 4000)
            if (resp.contains("4100") || resp.contains("41 00")) {
                return hint
            }
            Log.w(TAG, "Hint protocol ${hint.name} failed, falling back to auto-search")
            sendWithTimeout("ATSP0\r", 800) // Reset to auto
            delay(baseDelay)
        }

        // Step 5b: Try ATSP0 (Auto)
        sendWithTimeout("ATSP0\r", 1000)
        delay(baseDelay)
        
        for (attempt in 1..2) {
            onProgress("Auto-detectando protocolo ($attempt/2)...")
            val resp = sendWithTimeout("0100\r", 5000)
            if (resp.contains("4100") || resp.contains("41 00")) {
                return detectActiveProtocol()
            }
            if (resp.contains("UNABLE") || resp.contains("ERROR")) break
            delay(400)
        }

        // Step 5c: Manual Sweep if Auto fails
        val manualList = listOf("6", "7", "8", "9", "D", "E", "3", "5", "4", "1", "2", "A") // CAN 11/29, CAN FD, ISO, KWP, J1850, J1939
        for (pCode in manualList) {
            if (pCode == hint.atspCode) continue // Already tried
            
            onProgress("Escaneando protocolo $pCode...")
            sendWithTimeout("ATSP$pCode\r", 1000)
            delay(baseDelay)
            val resp = sendWithTimeout("0100\r", 3000)
            if (resp.contains("4100") || resp.contains("41 00")) {
                return detectActiveProtocol()
            }
        }

        throw ObdConnectionException("No se pudo enlazar con la ECU del vehículo. Verifica el contacto (IGN ON).")
    }

    private suspend fun detectActiveProtocol(): ObdProtocol {
        val raw = sendWithTimeout("ATDPN\r", 1000)
        val cleanLines = raw.split(Regex("[\\r\\n]+"))
            .map { it.trim().replace(">", "").trim() }
            .filter { it.isNotBlank() && !it.startsWith("AT", ignoreCase = true) }
        
        val activeLine = cleanLines.firstOrNull { line ->
            val cleanLine = line.removePrefix("A").removePrefix("a").trim()
            cleanLine.length == 1 && (cleanLine[0].isDigit() || cleanLine[0].uppercaseChar() in 'A'..'C')
        } ?: "0"
        
        val protocolCode = activeLine.removePrefix("A").removePrefix("a").trim()
        return ObdProtocol.values().find { it.atspCode == protocolCode } ?: ObdProtocol.AUTO
    }

    private fun buildInitSequence(isClone: Boolean, isSTN: Boolean): List<String> = buildList {
        add("ATE0") // Echo off
        add("ATL0") // Linefeeds off
        add("ATS0") // Spaces off
        add("ATH0") // Headers off
        add("ATCAF1") // CAN Auto Formatting on
        
        if (isClone) {
            add("ATAT2") // Adaptive Timing 2 (Aggressive) — faster polling even on clones
            add("ATST32") // Set Timeout 128ms (was 256ms) — CAN responds in 50-200ms
        } else {
            add("ATAT2") // Adaptive Timing 2 (Aggressive)
            add("ATST19") // Set Timeout ~100ms for pro adapters
        }

        if (isSTN) {
            add("ST AT 1") // STN Advanced Timing
            add("STP31")   // Optimization
            add("STPBR 1") // Baud rate optimization
        }
    }

    private fun isValidIdResponse(resp: String): Boolean {
        return resp.contains("ELM", true) || resp.contains("STN", true) || 
               resp.contains("OBD", true) || resp.contains(">")
    }

    private fun detectClone(resp: String): Boolean {
        if (resp.contains("STN", true) || resp.contains("OBDLink", true) || resp.contains("vLinker", true)) return false
        if (resp.contains("v2.2", true)) return false
        return true // Default to clone safety for anything else
    }

    private fun parseChipVersion(raw: String): String {
        return raw.replace(">", "").replace("\r", "").replace("\n", "").trim()
    }

    private suspend fun sendWithTimeout(cmd: String, timeoutMs: Long): String {
        return withContext(Dispatchers.IO) {
            transport.write(cmd.toByteArray())
            val buffer = StringBuilder()
            val startTime = System.currentTimeMillis()
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val chunk = transport.read(1024, timeoutMs = 200)
                if (chunk != null) {
                    buffer.append(String(chunk, Charsets.ISO_8859_1))
                    if (buffer.contains(">")) break
                }
            }
            buffer.toString()
        }
    }
}
