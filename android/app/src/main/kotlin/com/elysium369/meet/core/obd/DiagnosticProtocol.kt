package com.elysium369.meet.core.obd

/** Protocol namespace is part of finding identity; SAE buckets and UDS status bits are not interchangeable. */
enum class DiagnosticNamespace {
    SAE_OBD,
    UDS,
    KWP2000,
    OEM,
}

enum class DiagnosticSemantic {
    SAE_ACTIVE_DTC,
    SAE_PENDING_DTC,
    SAE_PERMANENT_DTC,
    UDS_TEST_FAILED,
    UDS_PENDING,
    UDS_CONFIRMED,
    UDS_FAILED_SINCE_CLEAR,
}

data class DiagnosticCoverage(
    val namespace: DiagnosticNamespace,
    val semantics: Set<DiagnosticSemantic>,
) {
    init {
        require(semantics.isNotEmpty()) { "Diagnostic coverage must be explicit and non-empty" }
    }

    fun covers(bucket: DtcBucket): Boolean = when (namespace) {
        DiagnosticNamespace.SAE_OBD -> when (bucket) {
            DtcBucket.ACTIVE -> DiagnosticSemantic.SAE_ACTIVE_DTC in semantics
            DtcBucket.PENDING -> DiagnosticSemantic.SAE_PENDING_DTC in semantics
            DtcBucket.PERMANENT -> DiagnosticSemantic.SAE_PERMANENT_DTC in semantics
            DtcBucket.HISTORY -> false
        }
        DiagnosticNamespace.UDS,
        DiagnosticNamespace.KWP2000,
        DiagnosticNamespace.OEM -> false
    }

    fun overlaps(required: Set<DiagnosticSemantic>): Boolean = required.any(semantics::contains)

    /** Compatibility alias for presentation/discovery code. Never use for absence proof. */
    fun coversAny(required: Set<DiagnosticSemantic>): Boolean = overlaps(required)

    /**
     * Evidence-grade coverage proof. Overlap is useful while exploring a vehicle,
     * but absence/resolution claims require every requested semantic to have been read.
     */
    fun fullyCovers(required: Set<DiagnosticSemantic>): Boolean =
        required.isNotEmpty() && semantics.containsAll(required)

    companion object {
        fun sae(bucket: DtcBucket): DiagnosticCoverage = DiagnosticCoverage(
            namespace = DiagnosticNamespace.SAE_OBD,
            semantics = setOf(
                when (bucket) {
                    DtcBucket.ACTIVE -> DiagnosticSemantic.SAE_ACTIVE_DTC
                    DtcBucket.PENDING -> DiagnosticSemantic.SAE_PENDING_DTC
                    DtcBucket.PERMANENT -> DiagnosticSemantic.SAE_PERMANENT_DTC
                    DtcBucket.HISTORY -> error("SAE OBD has no authoritative generic history bucket")
                },
            ),
        )

        fun udsForStatusMask(mask: Int): DiagnosticCoverage {
            val semantics = buildSet {
                if (mask and 0x01 != 0) add(DiagnosticSemantic.UDS_TEST_FAILED)
                if (mask and 0x04 != 0) add(DiagnosticSemantic.UDS_PENDING)
                if (mask and 0x08 != 0) add(DiagnosticSemantic.UDS_CONFIRMED)
                if (mask and 0x20 != 0) add(DiagnosticSemantic.UDS_FAILED_SINCE_CLEAR)
            }
            return DiagnosticCoverage(
                namespace = DiagnosticNamespace.UDS,
                semantics = semantics.ifEmpty {
                    error("UDS status mask does not cover a supported finding semantic")
                },
            )
        }
    }
}

enum class DiagnosticTransport {
    CAN,
    DOIP,
    K_LINE,
    BLUETOOTH_ADAPTER,
    BLE_ADAPTER,
    WIFI_ADAPTER,
    UNKNOWN,
}

enum class DiagnosticApplicationProtocol {
    SAE_OBD,
    UDS,
    OBD_ON_UDS,
    KWP2000,
    OEM,
    UNKNOWN,
}

enum class DiagnosticAddressingMode {
    FUNCTIONAL,
    PHYSICAL,
    LOGICAL,
    LEGACY_UNADDRESSED,
}

data class EcuEndpoint(
    val busId: String,
    val networkType: DiagnosticTransport,
    val addressingMode: DiagnosticAddressingMode,
    val requestAddress: String?,
    val responseAddress: String?,
    val logicalAddress: String? = null,
    val moduleRole: String? = null,
    val discoveryProvenance: String,
) {
    val stableId: String
        get() = listOf(
            busId.ifBlank { "UNKNOWN_BUS" },
            networkType.name,
            responseAddress ?: logicalAddress ?: requestAddress ?: "UNADDRESSED",
        ).joinToString(":") { it.trim().uppercase() }
}

sealed interface DiagnosticRequestScope {
    data class Functional(val requestAddress: String) : DiagnosticRequestScope
    data class Physical(val endpoint: EcuEndpoint) : DiagnosticRequestScope
    data class Logical(val endpoint: EcuEndpoint) : DiagnosticRequestScope
    data object LegacyUnaddressed : DiagnosticRequestScope
}

enum class DtcFormat {
    SAE_J2012_2_BYTE,
    ISO_14229_3_BYTE,
    KWP2000,
    OEM,
    UNKNOWN,
}

data class DiagnosticCodeIdentity(
    val displayCode: String,
    val rawCode: String,
    val rawDtc24: Int?,
    val failureType: Int?,
    val format: DtcFormat,
    val namespace: DiagnosticNamespace,
) {
    val stableRawIdentity: String
        get() = when {
            rawDtc24 != null -> "%06X".format(rawDtc24 and 0xFFFFFF)
            rawCode.isNotBlank() -> rawCode.trim().uppercase()
            else -> displayCode.trim().uppercase()
        }
}

enum class NegativeResponseSemantics {
    RETRY_PENDING,
    RETRY_AFTER_DELAY,
    SECURITY_REQUIRED,
    CONDITIONS_NOT_CORRECT,
    REQUEST_OUT_OF_RANGE,
    UNSUPPORTED,
    GENERAL_REJECT,
    UNKNOWN,
}

data class NegativeDiagnosticResponse(
    val requestedService: Int,
    val responseCode: Int,
    val semantics: NegativeResponseSemantics,
) {
    companion object {
        fun from(requestedService: Int, responseCode: Int): NegativeDiagnosticResponse =
            NegativeDiagnosticResponse(
                requestedService = requestedService,
                responseCode = responseCode,
                semantics = when (responseCode) {
                    0x21 -> NegativeResponseSemantics.RETRY_AFTER_DELAY
                    0x78 -> NegativeResponseSemantics.RETRY_PENDING
                    0x33, 0x35, 0x36, 0x37 -> NegativeResponseSemantics.SECURITY_REQUIRED
                    0x22 -> NegativeResponseSemantics.CONDITIONS_NOT_CORRECT
                    0x31 -> NegativeResponseSemantics.REQUEST_OUT_OF_RANGE
                    0x11, 0x12 -> NegativeResponseSemantics.UNSUPPORTED
                    0x10 -> NegativeResponseSemantics.GENERAL_REJECT
                    else -> NegativeResponseSemantics.UNKNOWN
                },
            )
    }
}

sealed interface ProtocolResponse {
    data class Positive(val serviceId: Int, val payload: ByteArray) : ProtocolResponse
    data class Negative(val response: NegativeDiagnosticResponse) : ProtocolResponse
    data class Unexpected(val bytes: ByteArray) : ProtocolResponse
}

data class DiagnosticExchangeClassification(
    val outcome: ModuleScanOutcome,
    val negativeResponse: NegativeDiagnosticResponse? = null,
)

/**
 * Decodes ELM text into transport PDUs. Service bytes are only interpreted at
 * PDU position zero; a coincidental 0x59/0x7F inside data can never create a response.
 */
object DiagnosticPduDecoder {
    private val indexedPrefix = Regex("^\\s*[0-9A-Fa-f]+:\\s*")
    private val hexToken = Regex("^[0-9A-Fa-f]+$")

    fun decodeResponses(
        rawResponse: String,
        expectedPositiveService: Int,
        requestedService: Int,
    ): List<ProtocolResponse> = decodePdus(rawResponse).map { pdu ->
        when {
            pdu.isNotEmpty() && (pdu[0].toInt() and 0xFF) == expectedPositiveService ->
                ProtocolResponse.Positive(expectedPositiveService, pdu.copyOfRange(1, pdu.size))
            pdu.size >= 3 &&
                (pdu[0].toInt() and 0xFF) == 0x7F &&
                (pdu[1].toInt() and 0xFF) == requestedService ->
                ProtocolResponse.Negative(
                    NegativeDiagnosticResponse.from(
                        requestedService = requestedService,
                        responseCode = pdu[2].toInt() and 0xFF,
                    ),
                )
            else -> ProtocolResponse.Unexpected(pdu)
        }
    }

    fun decodePdus(rawResponse: String): List<ByteArray> {
        val framePayloads = rawResponse
            .replace('\r', '\n')
            .split('\n')
            .mapNotNull(::parseFramePayload)

        if (framePayloads.isEmpty()) return emptyList()

        val pdus = mutableListOf<ByteArray>()
        var index = 0
        while (index < framePayloads.size) {
            val frame = framePayloads[index]
            if (frame.isEmpty()) {
                index++
                continue
            }
            when ((frame[0].toInt() ushr 4) and 0x0F) {
                0x0 -> {
                    val size = frame[0].toInt() and 0x0F
                    if (size > 0 && frame.size >= size + 1) {
                        pdus += frame.copyOfRange(1, size + 1)
                    }
                    index++
                }
                0x1 -> {
                    if (frame.size < 2) {
                        index++
                        continue
                    }
                    val expectedSize = ((frame[0].toInt() and 0x0F) shl 8) or
                        (frame[1].toInt() and 0xFF)
                    val assembled = ArrayList<Byte>(expectedSize)
                    assembled.addAll(frame.drop(2))
                    var expectedSequence = 1
                    var cursor = index + 1
                    var valid = expectedSize > 0
                    while (valid && assembled.size < expectedSize && cursor < framePayloads.size) {
                        val continuation = framePayloads[cursor]
                        valid = continuation.isNotEmpty() &&
                            ((continuation[0].toInt() ushr 4) and 0x0F) == 0x2 &&
                            (continuation[0].toInt() and 0x0F) == (expectedSequence and 0x0F)
                        if (valid) {
                            assembled.addAll(continuation.drop(1))
                            expectedSequence++
                            cursor++
                        }
                    }
                    if (valid && assembled.size >= expectedSize) {
                        pdus += assembled.take(expectedSize).toByteArray()
                        index = cursor
                    } else {
                        index++
                    }
                }
                0x2, 0x3 -> index++ // orphan continuation / flow-control frame
                else -> {
                    // ATCAF1 and DoIP can deliver an already decapsulated application PDU.
                    pdus += frame
                    index++
                }
            }
        }
        return pdus
    }

    private fun parseFramePayload(rawLine: String): ByteArray? {
        var line = rawLine.trim().removeSuffix(">").trim()
        if (line.isBlank()) return null
        val upper = line.uppercase()
        if (
            upper == "OK" || upper == "ERROR" || upper == "?" ||
            upper.startsWith("SEARCHING") || upper.startsWith("NO DATA") ||
            upper.startsWith("UNABLE TO CONNECT") || upper.startsWith("BUS ERROR") ||
            upper.startsWith("CAN ERROR") || upper.startsWith("STOPPED") ||
            upper.startsWith("AT")
        ) return null

        line = line.replace(indexedPrefix, "")
        val compact = line.filterNot(Char::isWhitespace)
        if (compact.isBlank() || !hexToken.matches(compact)) return null

        val payloadHex = when {
            compact.length >= 10 &&
                (compact.startsWith("18DA", ignoreCase = true) || compact.startsWith("18DB", ignoreCase = true)) ->
                compact.drop(8)
            compact.length >= 5 && compact[0] == '7' && compact.take(3).all { it.isDigit() || it.uppercaseChar() in 'A'..'F' } ->
                compact.drop(3)
            else -> compact
        }
        if (payloadHex.length < 2 || payloadHex.length % 2 != 0) return null
        return runCatching {
            ByteArray(payloadHex.length / 2) { byteIndex ->
                payloadHex.substring(byteIndex * 2, byteIndex * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }
}

object DiagnosticModuleIdentity {
    fun canonical(
        targetAddress: String?,
        responseAddress: String?,
        moduleName: String?,
    ): String {
        val target = targetAddress?.trim()?.uppercase().orEmpty()
        val response = responseAddress?.trim()?.uppercase().orEmpty()
        if (target.isNotBlank() && target != "7DF" && target != "LEGACY") return target
        if (response.length == 3) {
            response.toIntOrNull(16)?.minus(8)?.takeIf { it >= 0 }?.let {
                return String.format("%03X", it)
            }
        }
        if (response.isNotBlank()) return response
        if (target.isNotBlank()) return target
        return moduleName?.trim()?.uppercase().takeUnless { it.isNullOrBlank() } ?: "LEGACY"
    }
}

class DiagnosticFindingKey(
    val vehicleId: String,
    val namespace: DiagnosticNamespace,
    val moduleIdentity: String,
    val rawDtcIdentity: String,
    val displayCode: String,
) {
    /** Backwards-compatible presentation alias; never use as binary identity. */
    val code: String get() = displayCode

    override fun equals(other: Any?): Boolean =
        other is DiagnosticFindingKey &&
            vehicleId == other.vehicleId &&
            namespace == other.namespace &&
            moduleIdentity == other.moduleIdentity &&
            rawDtcIdentity == other.rawDtcIdentity

    override fun hashCode(): Int {
        var result = vehicleId.hashCode()
        result = 31 * result + namespace.hashCode()
        result = 31 * result + moduleIdentity.hashCode()
        result = 31 * result + rawDtcIdentity.hashCode()
        return result
    }

    override fun toString(): String =
        "$vehicleId|${namespace.name}|$moduleIdentity|$rawDtcIdentity"
}

fun DtcRecord.primaryObservationSemantic(): DiagnosticSemantic = when (namespace) {
    DiagnosticNamespace.SAE_OBD -> when (bucket) {
        DtcBucket.ACTIVE -> DiagnosticSemantic.SAE_ACTIVE_DTC
        DtcBucket.PENDING -> DiagnosticSemantic.SAE_PENDING_DTC
        DtcBucket.PERMANENT -> DiagnosticSemantic.SAE_PERMANENT_DTC
        DtcBucket.HISTORY -> error("Generic SAE history requires an explicit OEM/KWP namespace")
    }
    DiagnosticNamespace.UDS -> when {
        DtcStatusFlag.TEST_FAILED in statusFlags -> DiagnosticSemantic.UDS_TEST_FAILED
        DtcStatusFlag.PENDING in statusFlags -> DiagnosticSemantic.UDS_PENDING
        DtcStatusFlag.CONFIRMED in statusFlags -> DiagnosticSemantic.UDS_CONFIRMED
        DtcStatusFlag.TEST_FAILED_SINCE_LAST_CLEAR in statusFlags ->
            DiagnosticSemantic.UDS_FAILED_SINCE_CLEAR
        else -> DiagnosticSemantic.UDS_CONFIRMED
    }
    DiagnosticNamespace.KWP2000,
    DiagnosticNamespace.OEM -> when (bucket) {
        DtcBucket.ACTIVE -> DiagnosticSemantic.SAE_ACTIVE_DTC
        DtcBucket.PENDING -> DiagnosticSemantic.SAE_PENDING_DTC
        DtcBucket.PERMANENT -> DiagnosticSemantic.SAE_PERMANENT_DTC
        DtcBucket.HISTORY -> DiagnosticSemantic.UDS_CONFIRMED
    }
}

fun DtcRecord.findingKey(vehicleId: String): DiagnosticFindingKey = DiagnosticFindingKey(
    vehicleId = vehicleId,
    namespace = namespace,
    moduleIdentity = DiagnosticModuleIdentity.canonical(targetAddress, responseAddress, moduleName),
    rawDtcIdentity = codeIdentity.stableRawIdentity,
    displayCode = code.uppercase(),
)
