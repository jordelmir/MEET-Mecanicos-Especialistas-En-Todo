package com.elysium369.meet.core.obd

import android.util.Log
import com.elysium369.meet.core.transport.TransportInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ElmNegotiator(private val transport: TransportInterface) {

    private val TAG = "EV_NEGOTIATOR"

    data class AdapterProfile(
        val chipVersion: String,
        val isClone: Boolean,
        val isSTN: Boolean,
        val detectedProtocol: ObdProtocol,
        val baseDelayMs: Long,
        val maxLineLength: Int
    )

    /**
     * Fast-path reconnection for known good adapter + vehicle pairing.
     * Skips full ATZ reset and manual sweeps.
     */
    suspend fun negotiateFastPath(
        knownProtocol: ObdProtocol,
        onProgress: (String) -> Unit
    ): AdapterProfile? {
        if (knownProtocol == ObdProtocol.AUTO) return null
        return runCatching {
            onProgress("Reconexión rápida (${knownProtocol.displayName})...")
            transport.drain()
            transport.write("\r".toByteArray())
            delay(30)
            transport.drain()

            // Quick sync
            val ati = sendWithTimeout("ATI\r", 600)
            if (!isValidIdResponse(ati)) return@runCatching null

            val isClone = detectClone(ati)
            val chipVersion = parseChipVersion(ati)
            val isSTN = ati.contains("STN", true) || ati.contains("vLinker", true)

            // Essential init
            sendWithTimeout("ATE0\r", 400)
            sendWithTimeout("ATH0\r", 400)
            sendWithTimeout("ATCAF1\r", 400)
            sendWithTimeout("ATSP${knownProtocol.atspCode}\r", 600)

            // Fast Probe ECU
            val resp = sendWithTimeout("0100\r", 1500)
            if (!isPositivePidSupportResponse(resp)) return@runCatching null

            applyRuntimeSettings(knownProtocol, isClone)
            AdapterProfile(
                chipVersion = chipVersion,
                isClone = isClone,
                isSTN = isSTN,
                detectedProtocol = knownProtocol,
                baseDelayMs = runtimeBaseDelay(knownProtocol, isClone),
                maxLineLength = if (isClone) 64 else 512
            )
        }.getOrNull()
    }

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
            if (isValidIdResponse(idResponse)) {
                delay(1000) // Essential post-reset stabilization for clone UARTs
                transport.drain()
                break
            }
            
            if (attempt == 2) {
                idResponse = sendWithTimeout("AT WS\r", 2000)
                if (isValidIdResponse(idResponse)) {
                    delay(800)
                    transport.drain()
                    break
                }
            }
            delay(500)
            transport.drain()
        }

        // Blind fallback for silent clones
        if (!isValidIdResponse(idResponse)) {
            val blindTest = sendWithTimeout("0100\r", 3000)
            if (isPositivePidSupportResponse(blindTest)) {
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
        val negotiationDelay = if (isClone) 60L else 30L

        Log.i(TAG, "Adapter Identified: $chipVersion | Clone=$isClone | STN=$isSTN")
        onProgress("Adaptador: $chipVersion")

        // 3. Stabilization
        delay(if (isClone) 800 else 100)
        transport.drain()

        // 4. Initial AT Configuration
        val initSequence = buildInitSequence(isSTN)
        for (cmd in initSequence) {
            sendWithTimeout("$cmd\r", 800)
            delay(negotiationDelay)
        }

        // 5. Protocol Negotiation
        onProgress("Buscando protocolo del vehículo...")
        val protocol = sweepProtocols(hintProtocol, negotiationDelay, onProgress)
        val baseDelay = runtimeBaseDelay(protocol, isClone)
        applyRuntimeSettings(protocol, isClone)

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
        baseDelay: Long,
        onProgress: (String) -> Unit
    ): ObdProtocol {
        // Step 5a: Try hint first if specific
        if (hint != ObdProtocol.AUTO) {
            onProgress("Probando protocolo guardado: ${hint.displayName}...")
            sendWithTimeout("ATSP${hint.atspCode}\r", 1000)
            if (hint in setOf(ObdProtocol.KWP2000_FAST, ObdProtocol.KWP2000, ObdProtocol.ISO9141)) {
                sendWithTimeout("ATIB10\r", 600) // Set ISO baud rate to 10400
            }
            delay(baseDelay)
            val resp = sendWithTimeout("0100\r", 4000)
            if (isPositivePidSupportResponse(resp)) {
                Log.i(TAG, "✓ Fast-probe SUCCESS on hinted protocol: ${hint.displayName}")
                return hint
            }
            Log.w(TAG, "Hint protocol ${hint.name} failed, starting targeted protocol sweep")
        }

        // Step 5b: Targeted Deterministic Protocol & Header Probe
        // Prioritize Hyundai K-Line with Physical ECU (0x10) addressing, then Functional and CAN
        data class TargetedProbe(
            val protocol: ObdProtocol,
            val header: String?,
            val initCommands: List<String>,
            val timeoutMs: Long
        )

        val probes = listOf(
            // 1. Hyundai KWP2000 Fast Init Physical ECU (0x10) - Primary for Accent 2005 (Bosch/Kefico)
            TargetedProbe(ObdProtocol.KWP2000_FAST, "8110F1", listOf("ATIB10", "ATAL", "ATWM8110F13E"), 4500L),
            // 2. Hyundai KWP2000 Fast Init Broadcast Functional
            TargetedProbe(ObdProtocol.KWP2000_FAST, "C233F1", listOf("ATIB10", "ATAL"), 4500L),
            // 3. Hyundai KWP2000 5-baud Physical ECU (0x10)
            TargetedProbe(ObdProtocol.KWP2000, "8110F1", listOf("ATIB10", "ATAL"), 5000L),
            // 4. ISO 9141-2 Physical ECU (0x10)
            TargetedProbe(ObdProtocol.ISO9141, "686A10", listOf("ATIB10", "ATAL"), 5000L),
            // 5. ISO 9141-2 Functional
            TargetedProbe(ObdProtocol.ISO9141, "686AF1", listOf("ATIB10", "ATAL"), 5000L),
            // 6. Standard CAN 11-bit 500K
            TargetedProbe(ObdProtocol.CAN_11BIT_500K, "7DF", listOf("ATCAF1"), 3000L),
            // 7. Extended CAN 29-bit 500K
            TargetedProbe(ObdProtocol.CAN_29BIT_500K, "18DB33F1", listOf("ATCAF1"), 3000L),
            // 8. CAN 11-bit 250K
            TargetedProbe(ObdProtocol.CAN_11BIT_250K, "7DF", listOf("ATCAF1"), 3000L),
            // 9. CAN 29-bit 250K
            TargetedProbe(ObdProtocol.CAN_29BIT_250K, "18DB33F1", listOf("ATCAF1"), 3000L),
            // 10. J1850 PWM & VPW
            TargetedProbe(ObdProtocol.J1850_PWM, null, emptyList(), 3000L),
            TargetedProbe(ObdProtocol.J1850_VPW, null, emptyList(), 3000L)
        )

        for (probe in probes) {
            onProgress("Sincronizando ${probe.protocol.displayName}...")
            sendWithTimeout("ATSP${probe.protocol.atspCode}\r", 800)
            for (cmd in probe.initCommands) {
                sendWithTimeout("$cmd\r", 500)
            }
            if (probe.header != null) {
                sendWithTimeout("ATSH${probe.header}\r", 500)
            }
            delay(baseDelay.coerceAtLeast(80L))

            val resp = sendWithTimeout("0100\r", probe.timeoutMs)
            Log.i(TAG, "Probe [ATSP${probe.protocol.atspCode} Header=${probe.header}] -> '$resp'")

            if (isPositivePidSupportResponse(resp)) {
                Log.i(TAG, "✓ ECU SYNCHRONIZED on ${probe.protocol.displayName} (Header=${probe.header})")
                return probe.protocol
            }
        }

        // Step 5c: Fallback to ATSP0 (Auto)
        onProgress("Buscando en modo automático (ATSP0)...")
        sendWithTimeout("ATSP0\r", 1000)
        delay(baseDelay)

        for (attempt in 1..2) {
            val resp = sendWithTimeout("0100\r", 5000)
            if (isPositivePidSupportResponse(resp)) {
                return detectActiveProtocol()
            }
            if (resp.contains("UNABLE") || resp.contains("ERROR")) break
            delay(400)
        }

        throw ObdConnectionException("No se pudo enlazar con la ECU del vehículo. Verifica que el contacto esté en ON (motor encendido o ignición puesta).")
    }

    private suspend fun detectActiveProtocol(): ObdProtocol {
        val raw = sendWithTimeout("ATDPN\r", 1000)
        val cleanLines = raw.split(Regex("[\\r\\n]+"))
            .map { it.trim().replace(">", "").trim() }
            .filter { it.isNotBlank() && !it.startsWith("AT", ignoreCase = true) }
        
        val activeLine = cleanLines.firstOrNull { line ->
            val cleanLine = line.removePrefix("A").removePrefix("a").trim()
            cleanLine.length == 1 && (cleanLine[0].isDigit() || cleanLine[0].uppercaseChar() in 'A'..'F')
        } ?: "0"
        
        val protocolCode = activeLine.removePrefix("A").removePrefix("a").trim()
        return ObdProtocol.values().find { it.atspCode.equals(protocolCode, ignoreCase = true) } ?: ObdProtocol.AUTO
    }

    private fun buildInitSequence(isSTN: Boolean): List<String> = buildList {
        add("ATE0") // Echo off
        add("ATL0") // Linefeeds off
        add("ATS0") // Spaces off
        add("ATH0") // Headers off
        add("ATCAF1") // CAN Auto Formatting on
        add("ATAL")   // Allow Long messages (critical for KWP2000 multiline dumps)
        add("ATAT1") // Conservative adaptive timing while the vehicle protocol is unknown.
        add("ATSTFF") // Give slow ISO/KWP/J1850 ECUs enough time during detection.

        if (isSTN) {
            add("ST AT 1") // STN Advanced Timing
            add("STP31")   // Optimization
            add("STPBR 1") // Baud rate optimization
        }
    }

    private fun isPositivePidSupportResponse(resp: String): Boolean {
        val clean = resp.replace(Regex("[\\s\\r\\n>]+"), "").uppercase()
        if (clean.isEmpty() || clean == "?" || clean.contains("NODATA") || clean.contains("UNABLE") || clean.contains("ERROR") || clean.contains("BUSINIT:ERR") || clean.contains("STOPPED") || clean.contains("BUSERROR")) {
            return false
        }
        return clean.contains("4100") || clean.contains("410") || (clean.contains("41") && clean.length >= 6)
    }

    private fun runtimeBaseDelay(protocol: ObdProtocol, isClone: Boolean): Long {
        val isLegacy = protocol in setOf(
            ObdProtocol.ISO9141,
            ObdProtocol.KWP2000,
            ObdProtocol.KWP2000_FAST,
            ObdProtocol.J1850_PWM,
            ObdProtocol.J1850_VPW
        )
        return when {
            isLegacy && isClone -> 90L
            isLegacy -> 55L
            isClone -> 45L
            else -> 20L
        }
    }

    private suspend fun applyRuntimeSettings(protocol: ObdProtocol, isClone: Boolean) {
        val isCan = protocol.name.contains("CAN") || protocol.displayName.contains("CAN", ignoreCase = true)
        val isLegacy = protocol in setOf(
            ObdProtocol.ISO9141,
            ObdProtocol.KWP2000,
            ObdProtocol.KWP2000_FAST,
            ObdProtocol.J1850_PWM,
            ObdProtocol.J1850_VPW
        )
        val timeout = when {
            isLegacy -> "ATST96" // ~600ms for slow legacy ECUs.
            isClone -> "ATST64"  // ~400ms keeps clones stable without feeling sluggish.
            else -> "ATST32"     // ~200ms for genuine CAN/STN/OBDLink-style adapters.
        }

        sendWithTimeout("ATAT1\r", 800)
        sendWithTimeout("$timeout\r", 800)
        sendWithTimeout("ATH0\r", 800)
        sendWithTimeout("ATCAF1\r", 800)
        sendWithTimeout("ATCRA\r", 800)
        if (isCan) {
            sendWithTimeout("ATSH7DF\r", 800)
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
            .ifBlank { "ELM327-compatible adapter" }
    }

    private suspend fun sendWithTimeout(cmd: String, timeoutMs: Long): String {
        return withContext(Dispatchers.IO) {
            transport.write(cmd.toByteArray())
            val buffer = StringBuilder()
            val startTime = System.currentTimeMillis()
            val cleanCmd = cmd.trim()
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                // Low latency chunk polling of 50ms instead of 200ms
                val chunk = transport.read(1024, timeoutMs = 50)
                if (chunk != null) {
                    val part = String(chunk, Charsets.ISO_8859_1)
                    buffer.append(part)
                    
                    // Early exit on hardware/bus errors to prevent waiting for timeouts
                    val currentText = buffer.toString().uppercase()
                    if (currentText.contains(">") || 
                        currentText.contains("BUFFER FULL") || 
                        currentText.contains("CAN ERROR") || 
                        currentText.contains("STOPPED") || 
                        currentText.contains("BUS BUSY") || 
                        currentText.contains("FB ERROR") || 
                        currentText.contains("ERR1") || 
                        currentText.contains("ERR2")
                    ) {
                        break
                    }
                }
            }
            
            // Clean echo and prompt characters to protect the decoder parsers
            var response = buffer.toString()
            if (response.startsWith(cleanCmd, ignoreCase = true)) {
                response = response.substring(cleanCmd.length)
            }
            response.replace(">", "").trim()
        }
    }
}
