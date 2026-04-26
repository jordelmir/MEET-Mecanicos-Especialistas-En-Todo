package com.elysium369.meet.core.obd

import com.elysium369.meet.core.transport.TransportInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ObdConnectionException(message: String) : Exception(message)

class ElmNegotiator(private val transport: TransportInterface) {

    data class AdapterProfile(
        val chipVersion: String,
        val isClone: Boolean,
        val supportedProtocols: List<ObdProtocol>,
        val optimalBaudRate: Int,
        val commandDelayMs: Long,
        val supportsSTN: Boolean,
        val supportsHeaders: Boolean,
        val maxLineLength: Int
    )

    suspend fun negotiate(): AdapterProfile {
        val baudRates = listOf(38400, 9600, 115200)
        var activeBaud = 38400
        var rawId = ""

        for (baud in baudRates) {
            // Note: TransportInterface would need setBaudRate, or we assume it's set natively
            // transport.setBaudRate(baud) 
            delay(100)
            val response = sendWithTimeout("ATZ\r", timeoutMs = 2000)
            if (response.contains("ELM") || response.contains("STN") || response.contains("OBD")) {
                activeBaud = baud
                rawId = response
                break
            }
            val wsResponse = sendWithTimeout("AT WS\r", timeoutMs = 1500)
            if (wsResponse.contains("ELM") || wsResponse.isNotBlank()) {
                activeBaud = baud
                rawId = wsResponse
                break
            }
        }

        if (rawId.isBlank()) {
            val blindResponse = sendWithTimeout("0100\r", timeoutMs = 3000)
            if (blindResponse.contains("41 00") || blindResponse.contains("4100")) {
                return buildMinimalProfile()
            }
            throw ObdConnectionException("Adaptador no responde en ningún baudrate")
        }

        val isClone = detectClone(rawId)
        val supportsSTN = rawId.contains("STN", ignoreCase = true)

        val initSequence = buildInitSequence(isClone, supportsSTN)
        for ((cmd, _) in initSequence) {
            val resp = sendWithTimeout(cmd, timeoutMs = if (isClone) 800 else 400)
            if (!resp.contains("OK") && !isClone) {
                // logWarning("Comando $cmd no recibió OK: $resp")
            }
            delay(if (isClone) 50 else 20)
        }

        sendWithTimeout("ATSP0\r", 1000)
        delay(500)
        sendWithTimeout("0100\r", 2000)
        delay(2000)
        val protocolResponse = sendWithTimeout("ATDP\r", timeoutMs = 1000)
        val detectedProtocol = parseProtocol(protocolResponse)

        return AdapterProfile(
            chipVersion = parseChipVersion(rawId),
            isClone = isClone,
            supportedProtocols = listOf(detectedProtocol),
            optimalBaudRate = activeBaud,
            commandDelayMs = if (isClone) 80L else 30L,
            supportsSTN = supportsSTN,
            supportsHeaders = !isClone || rawId.contains("v2"),
            maxLineLength = if (isClone) 48 else 256
        )
    }

    private fun buildInitSequence(isClone: Boolean, supportsSTN: Boolean): List<Pair<String, String>> = buildList {
        add("ATE0\r" to "OK")
        add("ATL0\r" to "OK")
        add("ATS0\r" to "OK")
        add("ATH0\r" to "OK")
        add("ATCAF1\r" to "OK")
        
        if (!isClone) {
            add("ATAT1\r" to "OK")
            add("ATSTFF\r" to "OK")
        } else {
            add("ATST64\r" to "OK")
        }
        
        if (supportsSTN) {
            add("STPX H:7DF, D:01 00\r" to "")
        }
    }

    private fun detectClone(idResponse: String): Boolean {
        val cloneSignatures = listOf(
            "ELM327 v1.5",
            "ELM327 v2.1",
            "OBDII v1.5",
            "ELM327 v1.3a"
        )
        val genuineSignatures = listOf("STN1", "STN2", "OBDLink", "Kiwi")
        
        if (genuineSignatures.any { idResponse.contains(it, ignoreCase = true) }) {
            return false
        }
        return cloneSignatures.any { idResponse.contains(it, ignoreCase = true) }
            || !idResponse.contains("ELM327 v2.2")
    }

    private fun parseProtocol(dpResponse: String): ObdProtocol = when {
        dpResponse.contains("ISO 15765", ignoreCase = true) -> ObdProtocol.CAN_ISO15765
        dpResponse.contains("11bit", ignoreCase = true) -> ObdProtocol.CAN_11BIT_500K
        dpResponse.contains("29bit", ignoreCase = true) -> ObdProtocol.CAN_29BIT_500K
        dpResponse.contains("ISO 9141", ignoreCase = true) -> ObdProtocol.ISO9141
        dpResponse.contains("KWP", ignoreCase = true) -> ObdProtocol.KWP2000
        dpResponse.contains("J1850", ignoreCase = true) -> ObdProtocol.J1850_PWM
        else -> ObdProtocol.AUTO
    }

    private fun buildMinimalProfile(): AdapterProfile {
        return AdapterProfile(
            "Unknown Clone", true, listOf(ObdProtocol.AUTO), 38400, 100L, false, false, 48
        )
    }

    private fun parseChipVersion(raw: String): String {
        return raw.replace(">", "").trim().replace("\r", "")
    }

    private suspend fun sendWithTimeout(cmd: String, timeoutMs: Long): String {
        return withContext(Dispatchers.IO) {
            transport.write(cmd.toByteArray())
            // Emulate read loop
            val buffer = StringBuilder()
            var elapsed = 0L
            while (elapsed < timeoutMs) {
                val chunk = transport.read(1024)
                if (chunk != null) {
                    val str = String(chunk)
                    buffer.append(str)
                    if (buffer.contains(">")) {
                        break
                    }
                } else {
                    delay(10)
                    elapsed += 10
                }
            }
            buffer.toString()
        }
    }
}
