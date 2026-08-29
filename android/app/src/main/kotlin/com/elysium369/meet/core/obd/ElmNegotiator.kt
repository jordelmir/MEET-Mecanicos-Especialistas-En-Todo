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
        val maxLineLength: Int,
        val ecuHeader: String? = null,
        val initCommands: List<String> = emptyList(),
        val recipeId: String? = null,
        val vin: String? = null
    )

    enum class EvidenceType {
        ELM_RESET_STARTED,
        ELM_BANNER_RECEIVED,
        ADAPTER_VOLTAGE_OBSERVED,
        CACHED_PROTOCOL_ATTEMPT,
        PROTOCOL_ATTEMPT,
        ISO_INIT_STARTED,
        FIRST_VALID_ECU_FRAME,
        PROTOCOL_VERIFIED,
        PROTOCOL_FAILED,
        ADAPTIVE_FALLBACK_STARTED,
    }

    data class NegotiationEvidence(
        val type: EvidenceType,
        val protocol: ObdProtocol? = null,
        val recipeId: String? = null,
        val attemptOrdinal: Int? = null,
        val detail: String? = null,
    ) {
        fun redactedDetail(): String = buildList {
            protocol?.let { add("protocol=${it.name}") }
            recipeId?.let { add("recipe=$it") }
            attemptOrdinal?.let { add("attempt=$it") }
            detail?.takeIf(String::isNotBlank)?.let { add(it) }
        }.joinToString(" ")
    }

    /**
     * Fast-path reconnection for known good adapter + vehicle pairing.
     * Applies exact memorized protocol, header, baud and init sequence with dedicated focus.
     */
    suspend fun negotiateFastPath(
        knownProfile: AdapterProfile,
        onEvidence: (NegotiationEvidence) -> Unit = {},
        onProgress: (String) -> Unit
    ): AdapterProfile? {
        val knownProtocol = knownProfile.detectedProtocol
        if (knownProtocol == ObdProtocol.AUTO) return null
        return runCatching {
            onProgress("Reconexión ultra-rápida (${knownProtocol.displayName})...")
            Log.i(TAG, "── FAST-PATH ECU RECONNECT ── Protocol=${knownProtocol.name}, Header=${knownProfile.ecuHeader}")
            transport.drain()
            for (i in 1..3) {
                transport.write("\r".toByteArray())
                delay(30)
            }
            transport.drain()

            // Quick sync with generous timeout
            var ati = sendWithTimeout("ATI\r", 1200)
            if (!isValidIdResponse(ati)) {
                ati = sendWithTimeout("AT WS\r", 1200)
                if (!isValidIdResponse(ati)) {
                    ati = knownProfile.chipVersion
                }
            }

            val isClone = if (isValidIdResponse(ati)) detectClone(ati) else knownProfile.isClone
            val chipVersion = if (isValidIdResponse(ati)) parseChipVersion(ati) else knownProfile.chipVersion
            val isSTN = ati.contains("STN", true) || ati.contains("vLinker", true) || knownProfile.isSTN

            // Essential init commands
            sendWithTimeout("ATE0\r", 500)
            sendWithTimeout("ATL0\r", 400)
            sendWithTimeout("ATS0\r", 400)
            sendWithTimeout("ATH0\r", 400)
            sendWithTimeout("ATCAF1\r", 500)
            sendWithTimeout("ATAL\r", 400)
            sendWithTimeout("ATAT1\r", 400)

            // Apply memorized protocol
            onEvidence(NegotiationEvidence(EvidenceType.CACHED_PROTOCOL_ATTEMPT, knownProtocol))
            sendWithTimeout("ATSP${knownProtocol.atspCode}\r", 800)

            // Legacy K-Line / ISO baud rate if applicable
            if (knownProtocol in setOf(ObdProtocol.KWP2000_FAST, ObdProtocol.KWP2000, ObdProtocol.ISO9141)) {
                onEvidence(NegotiationEvidence(EvidenceType.ISO_INIT_STARTED, knownProtocol))
                sendWithTimeout("ATIB10\r", 600)
            }

            // Apply specific custom header or init commands if known
            if (!knownProfile.ecuHeader.isNullOrBlank()) {
                sendWithTimeout("ATSH${knownProfile.ecuHeader}\r", 600)
            }
            for (cmd in knownProfile.initCommands) {
                if (cmd.isNotBlank() && !cmd.startsWith("ATSP", ignoreCase = true)) {
                    sendWithTimeout("$cmd\r", 500)
                }
            }

            // Dedicated focus on known ECU: Up to 3 attempts with 4500ms timeout
            var positiveResponse = false
            for (attempt in 1..3) {
                onProgress("Enlazando ECU memorizada (${knownProtocol.displayName}) [$attempt/3]...")
                val resp = sendWithTimeout("0100\r", 4500)
                Log.d(TAG, "Fast-path probe attempt $attempt response: '$resp'")
                if (com.elysium369.meet.core.obd.handshake.Pid00HandshakeDecoder.isPositivePid00Response(resp) || isPositivePidSupportResponse(resp)) {
                    positiveResponse = true
                    onEvidence(NegotiationEvidence(EvidenceType.FIRST_VALID_ECU_FRAME, knownProtocol, attemptOrdinal = attempt))
                    onEvidence(NegotiationEvidence(EvidenceType.PROTOCOL_VERIFIED, knownProtocol, attemptOrdinal = attempt))
                    Log.i(TAG, "✓ Fast-path SUCCESS on attempt $attempt!")
                    break
                }
                delay(300)
            }

            if (!positiveResponse) {
                onEvidence(NegotiationEvidence(EvidenceType.PROTOCOL_FAILED, knownProtocol, detail = "cached_path"))
                Log.w(TAG, "Fast-path failed after 3 focused attempts, falling back to full negotiation")
                return@runCatching null
            }

            applyRuntimeSettings(knownProtocol, isClone)
            AdapterProfile(
                chipVersion = chipVersion,
                isClone = isClone,
                isSTN = isSTN,
                detectedProtocol = knownProtocol,
                baseDelayMs = runtimeBaseDelay(knownProtocol, isClone),
                maxLineLength = if (isClone) 64 else 512,
                ecuHeader = knownProfile.ecuHeader,
                initCommands = knownProfile.initCommands,
                recipeId = knownProfile.recipeId,
                vin = knownProfile.vin
            )
        }.getOrNull()
    }

    suspend fun negotiateFastPath(
        knownProtocol: ObdProtocol,
        onEvidence: (NegotiationEvidence) -> Unit = {},
        onProgress: (String) -> Unit
    ): AdapterProfile? {
        return negotiateFastPath(
            AdapterProfile(
                chipVersion = "ELM327",
                isClone = true,
                isSTN = false,
                detectedProtocol = knownProtocol,
                baseDelayMs = 40L,
                maxLineLength = 256
            ),
            onEvidence,
            onProgress
        )
    }

    /**
     * Executes the full ELM327/STN negotiation sequence.
     * @param hintProtocol Optional protocol to try first to speed up connection
     * @param onProgress Callback for UI status updates
     */
    suspend fun negotiate(
        hintProtocol: ObdProtocol = ObdProtocol.AUTO,
        manufacturerHint: String? = null,
        vehicleYear: Int? = null,
        onEvidence: (NegotiationEvidence) -> Unit = {},
        onProgress: (String) -> Unit
    ): AdapterProfile {
        Log.i(TAG, "═══ NEGOTIATION START ═══ (hint=${hintProtocol.displayName})")
        transport.drain()
        
        // 1. Physical Warm-up
        onProgress("Sincronizando enlace físico...")
        for (i in 1..3) {
            transport.write("\r".toByteArray())
            delay(40)
        }
        transport.drain()

        // 2. Identification (ATZ / AT WS)
        onProgress("Identificando adaptador...")
        onEvidence(NegotiationEvidence(EvidenceType.ELM_RESET_STARTED))
        var idResponse = ""
        for (attempt in 1..3) {
            idResponse = sendWithTimeout("ATZ\r", 2500)
            if (isValidIdResponse(idResponse)) {
                delay(800) // Essential post-reset stabilization for clone UARTs
                transport.drain()
                break
            }
            
            if (attempt == 2) {
                idResponse = sendWithTimeout("AT WS\r", 2000)
                if (isValidIdResponse(idResponse)) {
                    delay(600)
                    transport.drain()
                    break
                }
            }
            delay(400)
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
        onEvidence(NegotiationEvidence(EvidenceType.ELM_BANNER_RECEIVED, detail = "adapter_class=${if (isClone) "CLONE_COMPATIBLE" else "GENUINE_OR_STN"}"))

        parseAdapterVoltage(sendWithTimeout("ATRV\r", 800))?.let { voltage ->
            onEvidence(NegotiationEvidence(EvidenceType.ADAPTER_VOLTAGE_OBSERVED, detail = "voltage=${"%.1f".format(java.util.Locale.US, voltage)}V"))
        }
        
        // Test for STN specific support
        val stiResponse = sendWithTimeout("STI\r", 800)
        val isSTN = stiResponse.contains("STN", true) || idResponse.contains("STN", true) || idResponse.contains("vLinker", true)
        val negotiationDelay = if (isClone) 60L else 30L

        Log.i(TAG, "Adapter Identified: $chipVersion | Clone=$isClone | STN=$isSTN")
        onProgress("Adaptador: $chipVersion")

        // 3. Stabilization
        delay(if (isClone) 600 else 100)
        transport.drain()

        // 4. Initial AT Configuration
        val initSequence = buildInitSequence(isSTN)
        for (cmd in initSequence) {
            sendWithTimeout("$cmd\r", 800)
            delay(negotiationDelay)
        }

        // 5. Protocol Negotiation
        onProgress("Buscando protocolo del vehículo...")
        val protocolResult = sweepProtocols(
            hint = hintProtocol,
            baseDelay = negotiationDelay,
            manufacturerHint = manufacturerHint,
            vehicleYear = vehicleYear,
            onEvidence = onEvidence,
            onProgress = onProgress,
        )
        val protocol = protocolResult.first
        val recipe = protocolResult.second
        val baseDelay = runtimeBaseDelay(protocol, isClone)
        applyRuntimeSettings(protocol, isClone)

        Log.i(TAG, "═══ NEGOTIATION SUCCESS ═══ Protocol: ${protocol.displayName}")
        
        return AdapterProfile(
            chipVersion = chipVersion,
            isClone = isClone,
            isSTN = isSTN,
            detectedProtocol = protocol,
            baseDelayMs = baseDelay,
            maxLineLength = if (isClone) 64 else 512,
            ecuHeader = recipe?.requestHeader,
            initCommands = recipe?.initCommands ?: emptyList(),
            recipeId = recipe?.id
        )
    }

    private suspend fun sweepProtocols(
        hint: ObdProtocol,
        baseDelay: Long,
        manufacturerHint: String?,
        vehicleYear: Int?,
        onEvidence: (NegotiationEvidence) -> Unit,
        onProgress: (String) -> Unit
    ): Pair<ObdProtocol, com.elysium369.meet.core.obd.recipes.VehicleLinkRecipe?> {
        // Step 5a: Try hint first if specific
        if (hint != ObdProtocol.AUTO) {
            onProgress("Probando protocolo previo: ${hint.displayName}...")
            onEvidence(NegotiationEvidence(EvidenceType.CACHED_PROTOCOL_ATTEMPT, hint))
            sendWithTimeout("ATSP${hint.atspCode}\r", 1000)
            if (hint in setOf(ObdProtocol.KWP2000_FAST, ObdProtocol.KWP2000, ObdProtocol.ISO9141)) {
                onEvidence(NegotiationEvidence(EvidenceType.ISO_INIT_STARTED, hint))
                sendWithTimeout("ATIB10\r", 600) // Set ISO baud rate to 10400
            }
            delay(baseDelay)
            for (attempt in 1..2) {
                val resp = sendWithTimeout("0100\r", 4000)
                if (com.elysium369.meet.core.obd.handshake.Pid00HandshakeDecoder.isPositivePid00Response(resp)) {
                    onEvidence(NegotiationEvidence(EvidenceType.FIRST_VALID_ECU_FRAME, hint, attemptOrdinal = attempt))
                    onEvidence(NegotiationEvidence(EvidenceType.PROTOCOL_VERIFIED, hint, attemptOrdinal = attempt))
                    Log.i(TAG, "✓ Fast-probe SUCCESS on hinted protocol: ${hint.displayName}")
                    return Pair(hint, null)
                }
                delay(200)
            }
            onEvidence(NegotiationEvidence(EvidenceType.PROTOCOL_FAILED, hint, detail = "hint_path"))
            Log.w(TAG, "Hint protocol ${hint.name} failed, proceeding to structured recipe execution")
        }

        // Step 5b: Structured Vehicle Link Recipes (Standard CAN first, then legacy K-Line & J1850)
        val candidateRecipes = com.elysium369.meet.core.obd.recipes.VehicleLinkRecipe.negotiationCandidates(
            manufacturer = manufacturerHint,
            vehicleYear = vehicleYear,
        )

        for ((index, recipe) in candidateRecipes.withIndex()) {
            onProgress("Probando ${recipe.displayName}...")
            onEvidence(NegotiationEvidence(EvidenceType.PROTOCOL_ATTEMPT, recipe.protocol, recipe.id, index + 1))
            sendWithTimeout("ATSP${recipe.protocol.atspCode}\r", 800)
            if (recipe.protocol in setOf(ObdProtocol.KWP2000_FAST, ObdProtocol.KWP2000, ObdProtocol.ISO9141)) {
                onEvidence(NegotiationEvidence(EvidenceType.ISO_INIT_STARTED, recipe.protocol, recipe.id, index + 1))
            }
            for (cmd in recipe.initCommands) {
                sendWithTimeout("$cmd\r", 500)
            }
            if (recipe.requestHeader != null) {
                sendWithTimeout("ATSH${recipe.requestHeader}\r", 500)
            }
            delay(baseDelay.coerceAtLeast(80L))

            val resp = sendWithTimeout("0100\r", recipe.probeTimeoutMs)
            Log.i(TAG, "Recipe [${recipe.id} ATSP${recipe.protocol.atspCode} Header=${recipe.requestHeader}] -> '$resp'")

            if (com.elysium369.meet.core.obd.handshake.Pid00HandshakeDecoder.isPositivePid00Response(resp)) {
                onEvidence(NegotiationEvidence(EvidenceType.FIRST_VALID_ECU_FRAME, recipe.protocol, recipe.id, index + 1))
                onEvidence(NegotiationEvidence(EvidenceType.PROTOCOL_VERIFIED, recipe.protocol, recipe.id, index + 1))
                Log.i(TAG, "✓ ECU SYNCHRONIZED via recipe: ${recipe.displayName}")
                return Pair(recipe.protocol, recipe)
            }
            onEvidence(NegotiationEvidence(EvidenceType.PROTOCOL_FAILED, recipe.protocol, recipe.id, index + 1))
        }

        // Step 5c: Fallback to ATSP0 (Auto)
        onProgress("Buscando en modo automático universal (ATSP0)...")
        onEvidence(NegotiationEvidence(EvidenceType.ADAPTIVE_FALLBACK_STARTED, ObdProtocol.AUTO))
        sendWithTimeout("ATSP0\r", 1000)
        delay(baseDelay)

        for (attempt in 1..2) {
            val resp = sendWithTimeout("0100\r", 5000)
            if (com.elysium369.meet.core.obd.handshake.Pid00HandshakeDecoder.isPositivePid00Response(resp)) {
                val detected = detectActiveProtocol()
                onEvidence(NegotiationEvidence(EvidenceType.FIRST_VALID_ECU_FRAME, detected, attemptOrdinal = attempt))
                onEvidence(NegotiationEvidence(EvidenceType.PROTOCOL_VERIFIED, detected, attemptOrdinal = attempt))
                return Pair(detected, null)
            }
            if (resp.contains("UNABLE") || resp.contains("ERROR")) break
            delay(400)
        }

        throw ObdConnectionException("No se pudo enlazar con la ECU del vehículo. Verifica que el contacto esté en ON (motor encendido o ignición puesta).")
    }

    private fun parseAdapterVoltage(raw: String): Float? {
        return Regex("([0-9]{1,2}(?:\\.[0-9])?)\\s*V", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFloatOrNull()
            ?.takeIf { it in 5.0f..30.0f }
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
