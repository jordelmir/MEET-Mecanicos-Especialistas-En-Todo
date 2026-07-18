package com.elysium369.meet.core.obd

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.util.Locale

enum class ObdDataSource {
    REAL_OBD,
    OFFLINE_KNOWLEDGE,
    SIMULATED_DEMO,
    MANUAL_INPUT,
    NO_REAL_OBD,
}

enum class TelemetryQuality {
    VALID,
    STALE,
    UNSUPPORTED,
    TIMEOUT,
    PARSE_ERROR,
    OUT_OF_RANGE,
    MANUAL,
    SIMULATED,
}

enum class ObdConnectionPhase(
    val label: String,
    val recommendedAction: String,
) {
    DISCONNECTED("Adaptador desconectado.", "Conecta un adaptador OBD-II."),
    CONNECTING_TRANSPORT("Conectando transporte.", "Espera o revisa Bluetooth/WiFi."),
    TRANSPORT_CONNECTED("Transporte conectado.", "Iniciando handshake ELM327."),
    ADAPTER_HANDSHAKE("Handshake de adaptador.", "Validando comandos AT."),
    ADAPTER_READY("ELM327 responde, falta enlace ECU.", "Gira la llave a ON o revisa protocolo."),
    PROTOCOL_DETECTING("Detectando protocolo.", "Esperando respuesta ECU."),
    ECU_CONNECTED("ECU responde correctamente.", "Iniciar descubrimiento de PIDs."),
    PID_DISCOVERY("Descubriendo PIDs soportados.", "No saturar el adaptador."),
    LIVE_STREAMING("Scanner en vivo.", "Lecturas OBD reales activas."),
    DEGRADED("Conexion inestable: frecuencia reducida.", "Acerca el telefono o cambia adaptador."),
    RECONNECTING("Reconectando.", "Mantener contacto y llave en ON."),
    ERROR("Error de protocolo / timeout / adaptador.", "Revisar adaptador, protocolo o cableado."),
    USER_DISCONNECTED("Desconectado por usuario.", "Reconectar manualmente si quieres escanear."),
}

data class ObdConnectionState(
    val phase: ObdConnectionPhase,
    val technicalReason: String,
    val timestampMonotonicMs: Long,
    val lastError: String? = null,
    val adapterQuality: AdapterQuality = AdapterQuality.UNKNOWN,
    val protocol: String? = null,
) {
    val hasRealEcuLink: Boolean
        get() = phase in setOf(
            ObdConnectionPhase.ECU_CONNECTED,
            ObdConnectionPhase.PID_DISCOVERY,
            ObdConnectionPhase.LIVE_STREAMING,
            ObdConnectionPhase.DEGRADED,
        )
}

enum class AdapterQuality {
    GOOD,
    ACCEPTABLE,
    CLONE_LIMITED,
    UNSTABLE,
    UNSUPPORTED,
    UNKNOWN,
}

data class AdapterQualityMetrics(
    val ati: String? = null,
    val supportedBasicCommands: Int = 0,
    val avgLatencyMs: Long = 0,
    val timeoutRate: Double = 0.0,
    val errorsPerMinute: Double = 0.0,
    val protocolDetected: Boolean = false,
)

data class TelemetrySample(
    val pid: String,
    val name: String,
    val value: Double?,
    val unit: String,
    val timestampMonotonicMs: Long,
    val source: ObdDataSource,
    val quality: TelemetryQuality,
    val latencyMs: Long,
    val rawResponse: String,
    val formulaVersion: String = "SAE_J1979_LOCAL_V1",
) {
    val hasRealValue: Boolean
        get() = source == ObdDataSource.REAL_OBD && quality == TelemetryQuality.VALID && value != null

    fun displayText(): String = when (quality) {
        TelemetryQuality.VALID,
        TelemetryQuality.OUT_OF_RANGE -> value?.let { "${trimDouble(it)} $unit".trim() } ?: "--"
        TelemetryQuality.STALE -> value?.let { "${trimDouble(it)} $unit (stale)".trim() } ?: "--"
        TelemetryQuality.UNSUPPORTED -> "No soportado"
        TelemetryQuality.TIMEOUT,
        TelemetryQuality.PARSE_ERROR -> "--"
        TelemetryQuality.MANUAL -> value?.let { "${trimDouble(it)} $unit (manual)".trim() } ?: "Valor manual"
        TelemetryQuality.SIMULATED -> value?.let { "${trimDouble(it)} $unit (demo)".trim() } ?: "Demo"
    }
}

data class ParsedPidResponse(
    val sample: TelemetrySample,
    val dataBytes: List<Int> = emptyList(),
    val error: String? = null,
)

data class SupportedPidBlock(
    val command: String,
    val supportedPids: Set<String>,
    val rawResponse: String,
    val quality: TelemetryQuality,
)

data class DtcReadResult(
    val confirmed: List<String> = emptyList(),
    val pending: List<String> = emptyList(),
    val permanent: List<String> = emptyList(),
    val quality: TelemetryQuality = TelemetryQuality.VALID,
    val rawResponses: Map<String, String> = emptyMap(),
)

data class FreezeFrameSnapshot(
    val primaryDtc: String?,
    val pidValues: Map<String, TelemetrySample>,
    val available: Boolean,
    val reason: String? = null,
)

data class ObdEvent(
    val timestampMonotonicMs: Long,
    val state: ObdConnectionPhase,
    val command: String?,
    val rawResponse: String?,
    val parsedResult: String?,
    val latencyMs: Long,
    val error: String?,
    val adapterType: String?,
    val protocol: String?,
)

data class ObdTechnicalSnapshot(
    val connectionState: ObdConnectionState,
    val adapterQuality: AdapterQuality,
    val protocol: String?,
    val vinMasked: String?,
    val confirmedDtcs: List<String>,
    val pendingDtcs: List<String>,
    val permanentDtcs: List<String>,
    val freezeFrame: FreezeFrameSnapshot?,
    val liveSamples: List<TelemetrySample>,
    val unsupportedPids: List<String>,
    val stalePids: List<String>,
    val capturedAtMonotonicMs: Long,
    val rawHash: String,
    val evidenceFlag: ObdDataSource,
) {
    val hasRealObdEvidence: Boolean
        get() = evidenceFlag == ObdDataSource.REAL_OBD && connectionState.hasRealEcuLink
}

data class AiDiagnosticContext(
    val vehicleLabel: String?,
    val connectionState: ObdConnectionState,
    val confirmedDtcs: List<String>,
    val pendingDtcs: List<String>,
    val permanentDtcs: List<String>,
    val freezeFrame: FreezeFrameSnapshot?,
    val livePids: List<TelemetrySample>,
    val unsupportedPids: List<String>,
    val stalePids: List<String>,
    val manualNotes: List<String>,
    val evidenceFlags: Set<ObdDataSource>,
)

data class PollPlanItem(
    val command: String,
    val pid: String,
    val priority: Int,
    val intervalMs: Long,
)

interface ObdTransport {
    suspend fun send(command: String): String
}

data class QueuedObdCommand(
    val command: String,
    val timeoutMs: Long = 1_500,
    val retries: Int = 1,
    val priority: Int = 0,
)

data class QueuedObdResult(
    val command: String,
    val rawResponse: String,
    val latencyMs: Long,
    val success: Boolean,
    val attempts: Int,
    val error: String? = null,
)

object Elm327HandshakePlan {
    val required: List<QueuedObdCommand> = listOf(
        QueuedObdCommand("ATZ", timeoutMs = 2_500, retries = 1),
        QueuedObdCommand("ATE0", timeoutMs = 1_500, retries = 1),
        QueuedObdCommand("ATL0", timeoutMs = 1_500, retries = 1),
        QueuedObdCommand("ATS0", timeoutMs = 1_500, retries = 1),
        QueuedObdCommand("ATH0", timeoutMs = 1_500, retries = 1),
        QueuedObdCommand("ATSP0", timeoutMs = 2_500, retries = 1),
        QueuedObdCommand("0100", timeoutMs = 3_000, retries = 1),
    )

    val optionalAdvanced: List<QueuedObdCommand> = listOf(
        QueuedObdCommand("ATI", timeoutMs = 1_500),
        QueuedObdCommand("AT@1", timeoutMs = 1_500),
        QueuedObdCommand("ATDP", timeoutMs = 1_500),
        QueuedObdCommand("ATRV", timeoutMs = 1_500),
        QueuedObdCommand("ATDPN", timeoutMs = 1_500),
    )
}

class RobustObdCommandQueue(
    private val transport: ObdTransport,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val sleepMs: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()

    suspend fun execute(command: QueuedObdCommand): QueuedObdResult = mutex.withLock {
        var lastError: String? = null
        val started = nowMs()
        repeat(command.retries + 1) { attempt ->
            val before = nowMs()
            val raw = withTimeoutOrNull(command.timeoutMs) {
                runCatching { transport.send(command.command) }
                    .onFailure { lastError = it.message ?: it::class.java.simpleName }
                    .getOrNull()
            }
            val latency = (nowMs() - before).coerceAtLeast(0)
            if (raw == null) {
                lastError = lastError ?: "TIMEOUT"
            } else if (!ObdResponseParser.isTransportError(raw)) {
                return@withLock QueuedObdResult(
                    command = command.command,
                    rawResponse = raw,
                    latencyMs = latency,
                    success = true,
                    attempts = attempt + 1,
                )
            } else {
                lastError = ObdResponseParser.errorLabel(raw)
            }
            if (attempt < command.retries) {
                sleepMs(100L * (attempt + 1))
            }
        }
        QueuedObdResult(
            command = command.command,
            rawResponse = "",
            latencyMs = (nowMs() - started).coerceAtLeast(0),
            success = false,
            attempts = command.retries + 1,
            error = lastError,
        )
    }
}

object ObdResponseParser {
    private val bytesRegex = Regex("[0-9A-F]{2}")
    private val compactHexRegex = Regex("^[0-9A-F]+$")
    private val voltageRegex = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*V?", RegexOption.IGNORE_CASE)

    fun isTransportError(rawResponse: String): Boolean {
        val normalized = rawResponse.uppercase(Locale.ROOT)
        return normalized.contains("NO DATA") ||
            normalized.contains("STOPPED") ||
            normalized.contains("BUS INIT") ||
            normalized.contains("CAN ERROR") ||
            normalized.contains("UNABLE") ||
            normalized.trim() == "?"
    }

    fun errorLabel(rawResponse: String): String {
        val normalized = rawResponse.uppercase(Locale.ROOT)
        return when {
            normalized.contains("NO DATA") -> "NO_DATA"
            normalized.contains("STOPPED") -> "STOPPED"
            normalized.contains("BUS INIT") -> "BUS_INIT_ERROR"
            normalized.contains("CAN ERROR") -> "CAN_ERROR"
            normalized.contains("UNABLE") -> "UNABLE_TO_CONNECT"
            normalized.trim() == "?" -> "UNKNOWN_COMMAND"
            else -> "OBD_ERROR"
        }
    }

    fun parsePid(
        command: String,
        rawResponse: String,
        latencyMs: Long,
        timestampMonotonicMs: Long,
    ): ParsedPidResponse {
        val normalizedCommand = normalizeCommand(command)
        if (normalizedCommand == "ATRV") {
            return parseAdapterVoltage(rawResponse, latencyMs, timestampMonotonicMs)
        }

        if (isTransportError(rawResponse)) {
            return ParsedPidResponse(
                sample = errorSample(
                    pid = normalizedCommand,
                    name = normalizedCommand,
                    quality = qualityForError(rawResponse),
                    latencyMs = latencyMs,
                    rawResponse = rawResponse,
                    timestampMonotonicMs = timestampMonotonicMs,
                ),
                error = errorLabel(rawResponse),
            )
        }

        if (normalizedCommand.length < 4) {
            return ParsedPidResponse(
                sample = errorSample(
                    pid = normalizedCommand,
                    name = normalizedCommand,
                    quality = TelemetryQuality.PARSE_ERROR,
                    latencyMs = latencyMs,
                    rawResponse = rawResponse,
                    timestampMonotonicMs = timestampMonotonicMs,
                ),
                error = "COMMAND_TOO_SHORT",
            )
        }

        val mode = normalizedCommand.substring(0, 2)
        val pid = normalizedCommand.substring(2, 4)
        val expectedMode = responseModeFor(mode) ?: return ParsedPidResponse(
            sample = errorSample(pid, pid, TelemetryQuality.UNSUPPORTED, latencyMs, rawResponse, timestampMonotonicMs),
            error = "MODE_UNSUPPORTED",
        )
        val expectedPid = pid.toIntOrNull(16) ?: 0
        val payload = extractPayloadBytes(rawResponse, expectedMode, expectedPid)
        val definition = PidRegistry.getPid(mode, pid)
        if (payload == null || payload.isEmpty()) {
            return ParsedPidResponse(
                sample = errorSample(
                    pid = normalizedCommand,
                    name = definition?.name ?: normalizedCommand,
                    quality = TelemetryQuality.PARSE_ERROR,
                    latencyMs = latencyMs,
                    rawResponse = rawResponse,
                    timestampMonotonicMs = timestampMonotonicMs,
                ),
                error = "PAYLOAD_NOT_FOUND",
            )
        }
        if (definition == null) {
            return ParsedPidResponse(
                sample = errorSample(
                    pid = normalizedCommand,
                    name = normalizedCommand,
                    quality = TelemetryQuality.UNSUPPORTED,
                    latencyMs = latencyMs,
                    rawResponse = rawResponse,
                    timestampMonotonicMs = timestampMonotonicMs,
                ),
                dataBytes = payload,
                error = "PID_NOT_REGISTERED",
            )
        }
        val requiredBytes = requiredBytesFor(mode, pid)
        if (payload.size < requiredBytes) {
            return ParsedPidResponse(
                sample = errorSample(
                    pid = normalizedCommand,
                    name = definition.name,
                    quality = TelemetryQuality.PARSE_ERROR,
                    latencyMs = latencyMs,
                    rawResponse = rawResponse,
                    timestampMonotonicMs = timestampMonotonicMs,
                ),
                dataBytes = payload,
                error = "PAYLOAD_TOO_SHORT",
            )
        }
        val bytes = payload + List((4 - payload.size).coerceAtLeast(0)) { 0 }
        val value = runCatching {
            definition.formula(bytes[0], bytes[1], bytes[2], bytes[3]).toDouble()
        }.getOrNull()
        if (value == null || value.isNaN() || value.isInfinite()) {
            return ParsedPidResponse(
                sample = errorSample(
                    pid = normalizedCommand,
                    name = definition.name,
                    quality = TelemetryQuality.PARSE_ERROR,
                    latencyMs = latencyMs,
                    rawResponse = rawResponse,
                    timestampMonotonicMs = timestampMonotonicMs,
                ),
                dataBytes = payload,
                error = "FORMULA_ERROR",
            )
        }
        val quality = if (value < definition.minValue || value > definition.maxValue) {
            TelemetryQuality.OUT_OF_RANGE
        } else {
            TelemetryQuality.VALID
        }
        return ParsedPidResponse(
            sample = TelemetrySample(
                pid = normalizedCommand,
                name = definition.name,
                value = value,
                unit = definition.unit,
                timestampMonotonicMs = timestampMonotonicMs,
                source = ObdDataSource.REAL_OBD,
                quality = quality,
                latencyMs = latencyMs,
                rawResponse = rawResponse,
            ),
            dataBytes = payload,
        )
    }

    fun extractPayloadBytes(rawResponse: String, expectedMode: Int, expectedPid: Int): List<Int>? {
        val candidates = byteLines(rawResponse)
        for (line in candidates) {
            val normalized = stripCanHeaderAndPci(line, expectedMode)
            val index = normalized.windowed(2, 1)
                .indexOfFirst { it[0] == expectedMode && it[1] == expectedPid }
            if (index >= 0) {
                return normalized.drop(index + 2)
            }
        }
        val flat = candidates.flatten()
        val flatIndex = flat.windowed(2, 1)
            .indexOfFirst { it[0] == expectedMode && it[1] == expectedPid }
        return if (flatIndex >= 0) flat.drop(flatIndex + 2) else null
    }

    private fun parseAdapterVoltage(
        rawResponse: String,
        latencyMs: Long,
        timestampMonotonicMs: Long,
    ): ParsedPidResponse {
        if (isTransportError(rawResponse)) {
            return ParsedPidResponse(
                sample = errorSample("ATRV", "Voltaje adaptador", qualityForError(rawResponse), latencyMs, rawResponse, timestampMonotonicMs),
                error = errorLabel(rawResponse),
            )
        }
        val match = voltageRegex.find(rawResponse)
        val value = match?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val quality = when {
            value == null -> TelemetryQuality.PARSE_ERROR
            value < 0.0 || value > 65.535 -> TelemetryQuality.OUT_OF_RANGE
            else -> TelemetryQuality.VALID
        }
        return ParsedPidResponse(
            sample = TelemetrySample(
                pid = "ATRV",
                name = "Voltaje adaptador",
                value = value,
                unit = "V",
                timestampMonotonicMs = timestampMonotonicMs,
                source = if (value == null) ObdDataSource.NO_REAL_OBD else ObdDataSource.REAL_OBD,
                quality = quality,
                latencyMs = latencyMs,
                rawResponse = rawResponse,
            ),
            error = if (value == null) "VOLTAGE_PARSE_ERROR" else null,
        )
    }

    private fun byteLines(rawResponse: String): List<List<Int>> =
        rawResponse
            .uppercase(Locale.ROOT)
            .replace("SEARCHING...", " ")
            .replace("SEARCHING", " ")
            .replace(">", " ")
            .lines()
            .flatMap { it.split('\r') }
            .mapNotNull { line ->
                val words = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                val tokens = when {
                    words.isEmpty() -> emptyList()
                    words.size == 1 && words[0].matches(compactHexRegex) && words[0].length >= 4 ->
                        words[0].chunked(2)
                    else -> words.flatMap { word ->
                        if (word.length > 2 && word.matches(compactHexRegex) && word.length % 2 == 0) {
                            word.chunked(2)
                        } else {
                            bytesRegex.findAll(word).map { it.value }.toList()
                        }
                    }
                }
                tokens.mapNotNull { it.toIntOrNull(16) }.takeIf { it.isNotEmpty() }
            }

    private fun stripCanHeaderAndPci(bytes: List<Int>, expectedMode: Int): List<Int> {
        var out = bytes
        if (out.size >= 4 && out[0] in 0x7E0..0x7EF) {
            out = out.drop(1)
        }
        if (out.size >= 3 && out[0] in 0x00..0x08 && out[1] == expectedMode) {
            out = out.drop(1)
        }
        return out
    }

    private fun normalizeCommand(command: String): String =
        command.uppercase(Locale.ROOT).replace(Regex("[^0-9A-Z]"), "")

    private fun responseModeFor(mode: String): Int? =
        mode.toIntOrNull(16)?.let { it + 0x40 }?.takeIf { it in 0x40..0x7F }

    private fun requiredBytesFor(mode: String, pid: String): Int = when ("$mode$pid") {
        "010C", "0110", "011F", "0142", "0143", "0121", "0122", "0123", "0144", "015E" -> 2
        "01A6", "090D" -> 4
        else -> 1
    }

    private fun qualityForError(rawResponse: String): TelemetryQuality = when (errorLabel(rawResponse)) {
        "NO_DATA" -> TelemetryQuality.UNSUPPORTED
        "STOPPED",
        "BUS_INIT_ERROR",
        "CAN_ERROR",
        "UNABLE_TO_CONNECT" -> TelemetryQuality.TIMEOUT
        else -> TelemetryQuality.PARSE_ERROR
    }

    private fun errorSample(
        pid: String,
        name: String,
        quality: TelemetryQuality,
        latencyMs: Long,
        rawResponse: String,
        timestampMonotonicMs: Long,
    ) = TelemetrySample(
        pid = pid,
        name = name,
        value = null,
        unit = "",
        timestampMonotonicMs = timestampMonotonicMs,
        source = ObdDataSource.NO_REAL_OBD,
        quality = quality,
        latencyMs = latencyMs,
        rawResponse = rawResponse,
    )
}

object SupportedPidDiscovery {
    private val blocks = listOf("0100", "0120", "0140", "0160", "0180", "01A0", "01C0")

    fun discoveryCommands(): List<String> = blocks

    fun decodeSupportedPids(command: String, rawResponse: String): SupportedPidBlock {
        val normalized = command.uppercase(Locale.ROOT).replace(Regex("[^0-9A-F]"), "")
        if (ObdResponseParser.isTransportError(rawResponse)) {
            return SupportedPidBlock(normalized, emptySet(), rawResponse, TelemetryQuality.UNSUPPORTED)
        }
        val mode = normalized.substring(0, 2)
        val pid = normalized.substring(2, 4)
        val expectedMode = mode.toInt(16) + 0x40
        val expectedPid = pid.toInt(16)
        val payload = ObdResponseParser.extractPayloadBytes(rawResponse, expectedMode, expectedPid)
            ?: return SupportedPidBlock(normalized, emptySet(), rawResponse, TelemetryQuality.PARSE_ERROR)
        if (payload.size < 4) {
            return SupportedPidBlock(normalized, emptySet(), rawResponse, TelemetryQuality.PARSE_ERROR)
        }
        val start = pid.toInt(16)
        val supported = mutableSetOf<String>()
        payload.take(4).forEachIndexed { byteIndex, byte ->
            for (bit in 0 until 8) {
                if ((byte and (1 shl (7 - bit))) != 0) {
                    val pidNumber = start + byteIndex * 8 + bit + 1
                    supported += "01" + pidNumber.toString(16).uppercase(Locale.ROOT).padStart(2, '0')
                }
            }
        }
        return SupportedPidBlock(normalized, supported, rawResponse, TelemetryQuality.VALID)
    }
}

object TelemetryScheduler {
    private val high = listOf("010C", "010D", "0105", "0142", "0104", "0111")
    private val medium = listOf("0110", "010B", "0106", "0107", "0108", "0109", "012F", "010E")
    private val low = listOf("011F", "0133", "0144", "013C", "013D", "015E")

    fun buildPlan(
        supportedPids: Set<String>,
        avgLatencyMs: Long,
        timeoutRate: Double,
        temporarilyDisabledPids: Set<String> = emptySet(),
    ): List<PollPlanItem> {
        val multiplier = when {
            timeoutRate >= 0.35 || avgLatencyMs >= 1_500 -> 4
            timeoutRate >= 0.15 || avgLatencyMs >= 800 -> 2
            else -> 1
        }
        fun enabled(command: String): Boolean =
            command !in temporarilyDisabledPids && (supportedPids.isEmpty() || command in supportedPids || command == "ATRV")
        val items = mutableListOf<PollPlanItem>()
        high.filter(::enabled).forEach { items += PollPlanItem(it, it.takeLast(2), 1, 125L * multiplier) }
        medium.filter(::enabled).forEach { items += PollPlanItem(it, it.takeLast(2), 2, 750L * multiplier) }
        low.filter(::enabled).forEach { items += PollPlanItem(it, it.takeLast(2), 3, 5_000L * multiplier) }
        if ("0142" !in supportedPids && "ATRV" !in temporarilyDisabledPids) {
            items += PollPlanItem("ATRV", "ATRV", 1, 1_000L * multiplier)
        }
        return items.sortedWith(compareBy<PollPlanItem> { it.priority }.thenBy { it.intervalMs })
    }
}

object AdapterQualityClassifier {
    fun classify(metrics: AdapterQualityMetrics): AdapterQuality = when {
        metrics.supportedBasicCommands <= 0 -> AdapterQuality.UNSUPPORTED
        metrics.timeoutRate >= 0.45 || metrics.errorsPerMinute >= 12.0 -> AdapterQuality.UNSTABLE
        looksLikeClone(metrics.ati) || metrics.supportedBasicCommands < 4 -> AdapterQuality.CLONE_LIMITED
        metrics.avgLatencyMs > 900 || metrics.timeoutRate >= 0.15 -> AdapterQuality.ACCEPTABLE
        metrics.protocolDetected && metrics.avgLatencyMs <= 500 && metrics.timeoutRate < 0.05 -> AdapterQuality.GOOD
        else -> AdapterQuality.ACCEPTABLE
    }

    private fun looksLikeClone(ati: String?): Boolean {
        val value = ati?.uppercase(Locale.ROOT).orEmpty()
        return value.contains("ELM327 V2.1") ||
            value.contains("V1.5") ||
            value.contains("OBDII TO UART") ||
            value.contains("CLONE")
    }
}

object ObdDiagnosticEngine {
    const val CLEAR_DTC_WARNING =
        "Borrar codigos puede eliminar evidencia diagnostica. Guarda freeze frame primero."

    fun parseDtc(mode: String, rawResponse: String): List<String> =
        if (ObdResponseParser.isTransportError(rawResponse)) emptyList() else DtcDecoder.decode(rawResponse, mode)

    fun combine(
        confirmedRaw: String,
        pendingRaw: String,
        permanentRaw: String,
    ): DtcReadResult = DtcReadResult(
        confirmed = parseDtc("03", confirmedRaw),
        pending = parseDtc("07", pendingRaw),
        permanent = parseDtc("0A", permanentRaw),
        quality = if (listOf(confirmedRaw, pendingRaw, permanentRaw).all { ObdResponseParser.isTransportError(it) }) {
            TelemetryQuality.UNSUPPORTED
        } else {
            TelemetryQuality.VALID
        },
        rawResponses = mapOf("03" to confirmedRaw, "07" to pendingRaw, "0A" to permanentRaw),
    )
}

object ObdSnapshotEngine {
    fun capture(
        connectionState: ObdConnectionState,
        adapterQuality: AdapterQuality,
        protocol: String?,
        vin: String?,
        dtcReadResult: DtcReadResult,
        freezeFrame: FreezeFrameSnapshot?,
        samples: List<TelemetrySample>,
        rawEvents: List<ObdEvent>,
        capturedAtMonotonicMs: Long,
    ): ObdTechnicalSnapshot {
        val realSamples = samples.filter { it.source == ObdDataSource.REAL_OBD && it.quality == TelemetryQuality.VALID }
        val unsupported = samples.filter { it.quality == TelemetryQuality.UNSUPPORTED }.map { it.pid }.distinct()
        val stale = samples.filter { it.quality == TelemetryQuality.STALE }.map { it.pid }.distinct()
        val evidenceFlag = if (connectionState.hasRealEcuLink || realSamples.isNotEmpty()) {
            ObdDataSource.REAL_OBD
        } else {
            ObdDataSource.NO_REAL_OBD
        }
        val rawHash = sha256(rawEvents.joinToString("|") { "${it.command}:${it.rawResponse}:${it.error}" })
        return ObdTechnicalSnapshot(
            connectionState = connectionState,
            adapterQuality = adapterQuality,
            protocol = protocol,
            vinMasked = maskVin(vin),
            confirmedDtcs = dtcReadResult.confirmed,
            pendingDtcs = dtcReadResult.pending,
            permanentDtcs = dtcReadResult.permanent,
            freezeFrame = freezeFrame,
            liveSamples = samples,
            unsupportedPids = unsupported,
            stalePids = stale,
            capturedAtMonotonicMs = capturedAtMonotonicMs,
            rawHash = rawHash,
            evidenceFlag = evidenceFlag,
        )
    }

    fun emptyNoRealObd(nowMs: Long, reason: String): ObdTechnicalSnapshot {
        val state = ObdConnectionState(
            phase = ObdConnectionPhase.DISCONNECTED,
            technicalReason = reason,
            timestampMonotonicMs = nowMs,
            lastError = reason,
        )
        return capture(
            connectionState = state,
            adapterQuality = AdapterQuality.UNKNOWN,
            protocol = null,
            vin = null,
            dtcReadResult = DtcReadResult(quality = TelemetryQuality.UNSUPPORTED),
            freezeFrame = FreezeFrameSnapshot(null, emptyMap(), available = false, reason = "Sin evidencia OBD real"),
            samples = emptyList(),
            rawEvents = emptyList(),
            capturedAtMonotonicMs = nowMs,
        )
    }

    private fun maskVin(vin: String?): String? {
        val clean = vin?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
        return if (clean.length <= 6) "VIN-REDACTED" else "VIN-****${clean.takeLast(6)}"
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

private fun trimDouble(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.2f".format(Locale.US, value)
