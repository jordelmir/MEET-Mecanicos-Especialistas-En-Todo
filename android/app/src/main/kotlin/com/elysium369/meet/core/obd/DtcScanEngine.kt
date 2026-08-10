package com.elysium369.meet.core.obd

enum class DtcBucket {
    ACTIVE,
    PENDING,
    PERMANENT,
    HISTORY
}

enum class DtcStatusFlag {
    CURRENT,
    CONFIRMED,
    PENDING,
    PERMANENT,
    HISTORY,
    INTERMITTENT,
    TEST_FAILED,
    TEST_FAILED_THIS_CYCLE,
    TEST_NOT_COMPLETED_SINCE_LAST_CLEAR,
    TEST_FAILED_SINCE_LAST_CLEAR,
    TEST_NOT_COMPLETED_THIS_CYCLE,
    WARNING_INDICATOR_REQUESTED,
    UNKNOWN
}

enum class ModuleScanOutcome {
    COMPLETE,
    NO_DTC,
    NO_RESPONSE,
    UNSUPPORTED_SERVICE,
    NEGATIVE_RESPONSE,
    TIMEOUT,
    MALFORMED_RESPONSE,
    PARTIAL_RESPONSE,
    CANCELLED,
    FAILED;

    val provesBucketWasRead: Boolean
        get() = this == COMPLETE || this == NO_DTC
}

enum class ScanCompleteness {
    COMPLETE,
    PARTIAL,
    INCONCLUSIVE,
    FAILED,
}

enum class DiagnosticScanMode {
    QUICK,
    FULL_VEHICLE,
}

data class ScanProgressState(
    val modulesCompleted: Int,
    val modulesPlanned: Int,
    val servicesCompleted: Int,
    val servicesPlanned: Int,
) {
    val fraction: Float
        get() {
            val totalPlanned = modulesPlanned.coerceAtLeast(0) + servicesPlanned.coerceAtLeast(0)
            val totalCompleted = modulesCompleted.coerceAtLeast(0) + servicesCompleted.coerceAtLeast(0)
            return if (totalPlanned == 0) 0f
            else (totalCompleted.toFloat() / totalPlanned).coerceIn(0f, 1f)
        }
}

sealed interface DiagnosticScanEvent {
    val occurredAtMs: Long

    data class ScanStarted(
        val mode: DiagnosticScanMode,
        override val occurredAtMs: Long = System.currentTimeMillis(),
    ) : DiagnosticScanEvent

    data class ScanPlanCompiled(
        val modulesPlanned: Int,
        val servicesPlanned: Int,
        override val occurredAtMs: Long = System.currentTimeMillis(),
    ) : DiagnosticScanEvent

    data class ProgressUpdated(
        val state: ScanProgressState,
        override val occurredAtMs: Long = System.currentTimeMillis(),
    ) : DiagnosticScanEvent

    data class ModuleReading(
        val moduleIdentity: String,
        val moduleName: String,
        override val occurredAtMs: Long = System.currentTimeMillis(),
    ) : DiagnosticScanEvent

    data class ModuleDiscovered(
        val endpoint: EcuEndpoint,
        override val occurredAtMs: Long = System.currentTimeMillis(),
    ) : DiagnosticScanEvent

    data class ServiceStarted(
        val moduleIdentity: String,
        val command: String,
        val serviceIndex: Int,
        val servicesPlanned: Int,
        override val occurredAtMs: Long = System.currentTimeMillis(),
    ) : DiagnosticScanEvent

    data class ServiceCompleted(
        val moduleIdentity: String,
        val command: String,
        val outcome: ModuleScanOutcome,
        val serviceIndex: Int,
        val servicesPlanned: Int,
        override val occurredAtMs: Long = System.currentTimeMillis(),
    ) : DiagnosticScanEvent

    data class CoverageUpdated(
        val moduleIdentity: String,
        val coverage: DiagnosticCoverage,
        override val occurredAtMs: Long = System.currentTimeMillis(),
    ) : DiagnosticScanEvent

    data class FindingDiscovered(
        val finding: DtcRecord,
        override val occurredAtMs: Long = System.currentTimeMillis(),
    ) : DiagnosticScanEvent

    data class FindingObserved(
        val finding: DtcRecord,
        override val occurredAtMs: Long = System.currentTimeMillis(),
    ) : DiagnosticScanEvent

    data class ModuleCompleted(
        val moduleIdentity: String,
        val moduleName: String,
        val findingCount: Int,
        val outcome: ModuleScanOutcome,
        override val occurredAtMs: Long = System.currentTimeMillis(),
    ) : DiagnosticScanEvent

    data class ScanCancelled(
        val coveredModuleCount: Int,
        val findingCount: Int,
        override val occurredAtMs: Long = System.currentTimeMillis(),
    ) : DiagnosticScanEvent

    data class ScanCompleted(
        val report: DtcScanReport,
        override val occurredAtMs: Long = System.currentTimeMillis(),
    ) : DiagnosticScanEvent
}

data class DtcServiceRead(
    val command: String,
    val coverage: DiagnosticCoverage,
    val outcome: ModuleScanOutcome,
    val negativeResponse: NegativeDiagnosticResponse? = null,
)

enum class DiagnosticModuleDiscoveryState {
    DISCOVERY_CANDIDATE,
    EXPECTED,
    CONFIRMED,
}

enum class FreezeFrameOutcome {
    MATCHED,
    BELONGS_TO_ANOTHER_DTC,
    NO_RESPONSE,
    MALFORMED_RESPONSE,
}

data class FreezeFrameReadResult(
    val requestedDtc: String,
    val actualDtc: String?,
    val outcome: FreezeFrameOutcome,
    val values: Map<String, String> = emptyMap(),
    val identityRawResponse: String = "",
    val rawExchanges: Map<String, String> = emptyMap(),
)

data class DtcRecord(
    val code: String,
    val bucket: DtcBucket,
    val statusFlags: Set<DtcStatusFlag>,
    val sourceService: String,
    val namespace: DiagnosticNamespace = DiagnosticNamespace.SAE_OBD,
    val targetAddress: String? = null,
    val responseAddress: String? = null,
    val moduleName: String? = null,
    val rawPayload: String,
    val udsStatusByte: Int? = null,
    val udsFailureType: String? = null,
    val rawDtc24: Int? = null,
    val dtcFormat: DtcFormat = when (namespace) {
        DiagnosticNamespace.SAE_OBD -> DtcFormat.SAE_J2012_2_BYTE
        DiagnosticNamespace.UDS -> DtcFormat.ISO_14229_3_BYTE
        DiagnosticNamespace.KWP2000 -> DtcFormat.KWP2000
        DiagnosticNamespace.OEM -> DtcFormat.OEM
    },
) {
    val codeIdentity: DiagnosticCodeIdentity
        get() = DiagnosticCodeIdentity(
            displayCode = code.uppercase(),
            rawCode = rawDtc24?.let { "%06X".format(it and 0xFFFFFF) } ?: code.uppercase(),
            rawDtc24 = rawDtc24,
            failureType = udsFailureType?.toIntOrNull(16),
            format = dtcFormat,
            namespace = namespace,
        )
}

data class DtcRawExchange(
    val command: String,
    val targetAddress: String?,
    val rawResponse: String,
    val parsedRecordCount: Int,
    val outcome: ModuleScanOutcome = ModuleScanOutcome.COMPLETE,
    val negativeResponse: NegativeDiagnosticResponse? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val sessionId: String = "",
    val transport: DiagnosticTransport = DiagnosticTransport.UNKNOWN,
    val requestScope: DiagnosticRequestScope = DiagnosticRequestScope.LegacyUnaddressed,
    val responseAddress: String? = null,
    val latencyMs: Long? = null,
    val retryCount: Int = 0,
    val parserVersion: String = "diagnostic-pdu-v2",
)

data class DtcModuleReport(
    val targetAddress: String?,
    val responseAddress: String?,
    val moduleName: String,
    val isAlive: Boolean,
    val dtcs: List<DtcRecord>,
    val rawExchanges: List<DtcRawExchange>,
    val serviceReads: List<DtcServiceRead> = emptyList(),
    val outcome: ModuleScanOutcome = ModuleScanOutcome.FAILED,
    val discoveryState: DiagnosticModuleDiscoveryState = if (isAlive) {
        DiagnosticModuleDiscoveryState.CONFIRMED
    } else {
        DiagnosticModuleDiscoveryState.DISCOVERY_CANDIDATE
    },
    val requiredForCompleteness: Boolean = isAlive,
) {
    fun completedBucket(bucket: DtcBucket): Boolean =
        serviceReads.any {
            it.coverage.covers(bucket) && it.outcome.provesBucketWasRead
        }

    fun completedSemantics(semantics: Set<DiagnosticSemantic>): Boolean =
        serviceReads.any {
            it.coverage.fullyCovers(semantics) && it.outcome.provesBucketWasRead
        }

    val moduleIdentity: String
        get() = DiagnosticModuleIdentity.canonical(targetAddress, responseAddress, moduleName)
}

data class DtcScanReport(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val protocol: String,
    val records: List<DtcRecord>,
    val modules: List<DtcModuleReport>,
    val rawExchanges: List<DtcRawExchange>,
    val completeness: ScanCompleteness = ScanCompleteness.INCONCLUSIVE,
    val warnings: List<String> = emptyList(),
    val mode: DiagnosticScanMode = DiagnosticScanMode.FULL_VEHICLE,
    val wasCancelled: Boolean = false,
) {
    fun codesForBucket(bucket: DtcBucket): List<String> =
        records.filter { it.bucket == bucket }.map { it.code }.distinct()
}

object DtcObservationPolicy {
    /**
     * A missing code is meaningful only when the same live module completed
     * the service/bucket that could have returned it. It is still not proof
     * of repair; callers may mark it as not observed, never resolved.
     */
    fun canMarkNotObserved(
        module: DtcModuleReport?,
        bucket: DtcBucket,
        code: String,
        records: List<DtcRecord>,
        namespace: DiagnosticNamespace = DiagnosticNamespace.SAE_OBD,
        moduleIdentity: String? = module?.moduleIdentity,
        semantics: Set<DiagnosticSemantic> = defaultSemantics(namespace, bucket),
        rawDtcIdentity: String = code.uppercase(),
    ): Boolean {
        if (module == null || !module.isAlive) return false
        val covered = when (namespace) {
            DiagnosticNamespace.SAE_OBD -> module.completedBucket(bucket)
            DiagnosticNamespace.UDS,
            DiagnosticNamespace.KWP2000,
            DiagnosticNamespace.OEM -> module.completedSemantics(semantics)
        }
        if (!covered) return false
        val expectedModule = moduleIdentity ?: module.moduleIdentity
        return records.none { record ->
            record.codeIdentity.stableRawIdentity.equals(rawDtcIdentity, ignoreCase = true) &&
                record.namespace == namespace &&
                DiagnosticModuleIdentity.canonical(
                    record.targetAddress,
                    record.responseAddress,
                    record.moduleName,
                ) == expectedModule &&
                record.observationSemantics().any(semantics::contains)
        }
    }

    fun defaultSemantics(
        namespace: DiagnosticNamespace,
        bucket: DtcBucket,
    ): Set<DiagnosticSemantic> = when (namespace) {
        DiagnosticNamespace.SAE_OBD -> setOf(
            when (bucket) {
                DtcBucket.ACTIVE -> DiagnosticSemantic.SAE_ACTIVE_DTC
                DtcBucket.PENDING -> DiagnosticSemantic.SAE_PENDING_DTC
                DtcBucket.PERMANENT -> DiagnosticSemantic.SAE_PERMANENT_DTC
                DtcBucket.HISTORY -> return emptySet()
            },
        )
        DiagnosticNamespace.UDS -> when (bucket) {
            DtcBucket.ACTIVE -> setOf(DiagnosticSemantic.UDS_TEST_FAILED)
            DtcBucket.PENDING -> setOf(DiagnosticSemantic.UDS_PENDING)
            DtcBucket.PERMANENT -> emptySet()
            DtcBucket.HISTORY -> setOf(
                DiagnosticSemantic.UDS_CONFIRMED,
                DiagnosticSemantic.UDS_FAILED_SINCE_CLEAR,
            )
        }
        DiagnosticNamespace.KWP2000,
        DiagnosticNamespace.OEM -> emptySet()
    }
}

fun DtcRecord.observationSemantics(): Set<DiagnosticSemantic> = when (namespace) {
    DiagnosticNamespace.SAE_OBD -> DtcObservationPolicy.defaultSemantics(namespace, bucket)
    DiagnosticNamespace.UDS -> buildSet {
        if (DtcStatusFlag.TEST_FAILED in statusFlags) add(DiagnosticSemantic.UDS_TEST_FAILED)
        if (DtcStatusFlag.PENDING in statusFlags) add(DiagnosticSemantic.UDS_PENDING)
        if (DtcStatusFlag.CONFIRMED in statusFlags) add(DiagnosticSemantic.UDS_CONFIRMED)
        if (DtcStatusFlag.TEST_FAILED_SINCE_LAST_CLEAR in statusFlags) {
            add(DiagnosticSemantic.UDS_FAILED_SINCE_CLEAR)
        }
    }
    DiagnosticNamespace.KWP2000,
    DiagnosticNamespace.OEM -> emptySet()
}

object DtcScanEngine {

    private val HEADER_FALLBACK_REGEX = Regex("^([0-9A-F]{3}|[0-9A-F]{8})[:\\s]+(.+)$")

    private fun isHexChar(c: Char): Boolean = c in '0'..'9' || c in 'A'..'F' || c in 'a'..'f'


    fun parseStandardByEcu(
        rawResponse: String,
        mode: String,
        targetAddress: String? = null,
        moduleName: String? = null
    ): List<DtcRecord> {
        val grouped = groupRawByEcu(rawResponse)
        val bucket = bucketForMode(mode)
        val flags = flagsForMode(mode)

        if (grouped.isEmpty()) {
            return DtcDecoder.decode(rawResponse, mode).map { code ->
                DtcRecord(
                    code = code,
                    bucket = bucket,
                    statusFlags = flags,
                    sourceService = mode.uppercase(),
                    namespace = DiagnosticNamespace.SAE_OBD,
                    targetAddress = targetAddress,
                    responseAddress = targetAddress,
                    moduleName = moduleName,
                    rawPayload = rawResponse
                )
            }
        }

        return grouped.flatMap { (responseAddress, lines) ->
            val perEcuRaw = lines.joinToString("\n")
            val payload = CanMultiFrameParser.parse(perEcuRaw)
            DtcDecoder.decode(payload, mode).map { code ->
                DtcRecord(
                    code = code,
                    bucket = bucket,
                    statusFlags = flags,
                    sourceService = mode.uppercase(),
                    namespace = DiagnosticNamespace.SAE_OBD,
                    targetAddress = targetAddress,
                    responseAddress = responseAddress,
                    moduleName = moduleName,
                    rawPayload = perEcuRaw
                )
            }
        }.distinctBy { "${it.code}|${it.bucket}|${it.responseAddress}|${it.targetAddress}|${it.sourceService}" }
    }

    fun parseUdsService19ByEcu(
        rawResponse: String,
        targetAddress: String? = null,
        moduleName: String? = null
    ): List<DtcRecord> {
        val grouped = groupRawByEcu(rawResponse)
        if (grouped.isEmpty()) {
            return parseUdsPayload(
                payload = CanMultiFrameParser.parse(rawResponse),
                rawPayload = rawResponse,
                targetAddress = targetAddress,
                responseAddress = targetAddress,
                moduleName = moduleName
            )
        }

        return grouped.flatMap { (responseAddress, lines) ->
            val perEcuRaw = lines.joinToString("\n")
            parseUdsPayload(
                payload = CanMultiFrameParser.parse(perEcuRaw),
                rawPayload = perEcuRaw,
                targetAddress = targetAddress,
                responseAddress = responseAddress,
                moduleName = moduleName
            )
        }.distinctBy { "${it.code}|${it.bucket}|${it.responseAddress}|${it.targetAddress}|${it.udsStatusByte}|${it.udsFailureType}" }
    }

    fun groupRawByEcu(rawResponse: String): Map<String, List<String>> {
        val grouped = linkedMapOf<String, MutableList<String>>()
        rawResponse
            .replace("\r", "\n")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val cleaned = line.replace(" ", "").removeSuffix(">").trim().uppercase()
                if (isNoiseLine(cleaned)) return@forEach

                var header: String? = null
                var data: String? = null

                if (cleaned.startsWith("18DA") && cleaned.length >= 10) {
                    header = cleaned.substring(0, 8)
                    data = cleaned.substring(8)
                } else if (cleaned.length >= 5) {
                    val firstThree = cleaned.substring(0, 3)
                    if (firstThree[0] == '7' && isHexChar(firstThree[1]) && isHexChar(firstThree[2])) {
                        header = firstThree
                        data = cleaned.substring(3)
                    }
                }

                // Regex fallback for spaced lines or non-standard headers
                if (header == null) {
                    val match = HEADER_FALLBACK_REGEX.find(line.uppercase())
                    if (match != null) {
                        header = match.groupValues[1]
                        data = match.groupValues[2].replace(" ", "")
                    }
                }

                if (header != null && data != null && isDiagnosticCanHeader(header)) {
                    grouped.getOrPut(header) { mutableListOf() }.add("$header $data")
                }
            }
        return grouped
    }

    private fun parseUdsPayload(
        payload: String,
        rawPayload: String,
        targetAddress: String?,
        responseAddress: String?,
        moduleName: String?
    ): List<DtcRecord> {
        val responses = DiagnosticPduDecoder.decodeResponses(
            rawResponse = rawPayload.ifBlank { payload },
            expectedPositiveService = 0x59,
            requestedService = 0x19,
        )
        val positive = responses.filterIsInstance<ProtocolResponse.Positive>().firstOrNull()
            ?: return emptyList()
        val data = positive.payload
        if (data.size < 2) return emptyList()
        val subFunction = data[0].toInt() and 0xFF
        if (subFunction !in setOf(0x02, 0x07, 0x08, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x14)) {
            return emptyList()
        }
        // byte 1 is DTCStatusAvailabilityMask. Remaining bytes must be complete 4-byte records.
        val recordsPayload = data.copyOfRange(2, data.size)
        if (recordsPayload.size % 4 != 0) return emptyList()

        return recordsPayload.asList().chunked(4).mapNotNull { recordBytes ->
            val dtcHex = recordBytes.take(3).joinToString("") { "%02X".format(it.toInt() and 0xFF) }
            if (dtcHex == "000000" || dtcHex == "FFFFFF") return@mapNotNull null
            val statusByte = recordBytes[3].toInt() and 0xFF
            val code = DtcDecoder.hexToDtcOrNull(dtcHex.substring(0, 4)) ?: return@mapNotNull null
            val flags = flagsForUdsStatus(statusByte)
            DtcRecord(
                code = code,
                bucket = bucketForUdsFlags(flags),
                statusFlags = flags,
                sourceService = "19%02X".format(subFunction),
                namespace = DiagnosticNamespace.UDS,
                targetAddress = targetAddress,
                responseAddress = responseAddress,
                moduleName = moduleName,
                rawPayload = rawPayload,
                udsStatusByte = statusByte,
                udsFailureType = dtcHex.substring(4, 6),
                rawDtc24 = dtcHex.toInt(16),
                dtcFormat = DtcFormat.ISO_14229_3_BYTE,
            )
        }
    }

    private fun bucketForMode(mode: String): DtcBucket = when (mode.uppercase()) {
        "07" -> DtcBucket.PENDING
        "0A" -> DtcBucket.PERMANENT
        else -> DtcBucket.ACTIVE
    }

    private fun flagsForMode(mode: String): Set<DtcStatusFlag> = when (mode.uppercase()) {
        "07" -> setOf(DtcStatusFlag.PENDING)
        "0A" -> setOf(DtcStatusFlag.PERMANENT)
        else -> setOf(DtcStatusFlag.CONFIRMED, DtcStatusFlag.CURRENT)
    }

    internal fun flagsForUdsStatus(statusByte: Int): Set<DtcStatusFlag> {
        val flags = mutableSetOf<DtcStatusFlag>()
        if (statusByte and 0x01 != 0) {
            flags += DtcStatusFlag.TEST_FAILED
        }
        if (statusByte and 0x02 != 0) flags += DtcStatusFlag.TEST_FAILED_THIS_CYCLE
        if (statusByte and 0x04 != 0) flags += DtcStatusFlag.PENDING
        if (statusByte and 0x08 != 0) flags += DtcStatusFlag.CONFIRMED
        if (statusByte and 0x10 != 0) flags += DtcStatusFlag.TEST_NOT_COMPLETED_SINCE_LAST_CLEAR
        if (statusByte and 0x20 != 0) flags += DtcStatusFlag.TEST_FAILED_SINCE_LAST_CLEAR
        if (statusByte and 0x40 != 0) flags += DtcStatusFlag.TEST_NOT_COMPLETED_THIS_CYCLE
        if (statusByte and 0x80 != 0) flags += DtcStatusFlag.WARNING_INDICATOR_REQUESTED
        if (flags.isEmpty()) flags += DtcStatusFlag.UNKNOWN
        return flags
    }

    private fun bucketForUdsFlags(flags: Set<DtcStatusFlag>): DtcBucket = when {
        DtcStatusFlag.TEST_FAILED in flags -> DtcBucket.ACTIVE
        DtcStatusFlag.PENDING in flags -> DtcBucket.PENDING
        DtcStatusFlag.CONFIRMED in flags || DtcStatusFlag.TEST_FAILED_SINCE_LAST_CLEAR in flags -> DtcBucket.HISTORY
        else -> DtcBucket.HISTORY
    }

    fun classifyExchange(
        rawResponse: String,
        positiveResponseService: String,
        parsedRecordCount: Int,
        failed: Boolean = false,
    ): ModuleScanOutcome = classifyExchangeDetailed(
        rawResponse = rawResponse,
        positiveResponseService = positiveResponseService,
        parsedRecordCount = parsedRecordCount,
        failed = failed,
    ).outcome

    fun classifyExchangeDetailed(
        rawResponse: String,
        positiveResponseService: String,
        parsedRecordCount: Int,
        failed: Boolean = false,
    ): DiagnosticExchangeClassification {
        if (failed) return DiagnosticExchangeClassification(ModuleScanOutcome.FAILED)
        val compact = rawResponse.uppercase().replace(Regex("[^0-9A-Z?]"), "")
        if (compact.isBlank()) return DiagnosticExchangeClassification(ModuleScanOutcome.NO_RESPONSE)
        if (compact.contains("STOPPED")) return DiagnosticExchangeClassification(ModuleScanOutcome.CANCELLED)
        if (compact.contains("UNABLETOCONNECT") || compact.contains("BUSERROR") || compact.contains("CANERROR")) {
            return DiagnosticExchangeClassification(ModuleScanOutcome.NO_RESPONSE)
        }
        if (compact.contains("NODATA") || compact == "?") {
            return DiagnosticExchangeClassification(ModuleScanOutcome.UNSUPPORTED_SERVICE)
        }
        val positiveService = positiveResponseService.toIntOrNull(16)
            ?: return DiagnosticExchangeClassification(ModuleScanOutcome.MALFORMED_RESPONSE)
        val requestService = when (positiveService) {
            0x43 -> 0x03
            0x47 -> 0x07
            0x4A -> 0x0A
            0x59 -> 0x19
            else -> positiveService
        }
        val grouped = groupRawByEcu(rawResponse)
        val responseInputs = if (grouped.isEmpty()) {
            listOf(rawResponse)
        } else {
            grouped.values.map { it.joinToString("\n") }
        }
        val responses = responseInputs.flatMap { input ->
            DiagnosticPduDecoder.decodeResponses(input, positiveService, requestService)
        }
        if (responses.any { it is ProtocolResponse.Positive }) {
            return DiagnosticExchangeClassification(
                if (parsedRecordCount > 0) ModuleScanOutcome.COMPLETE else ModuleScanOutcome.NO_DTC,
            )
        }
        responses.filterIsInstance<ProtocolResponse.Negative>().firstOrNull()?.let {
            return DiagnosticExchangeClassification(
                outcome = ModuleScanOutcome.NEGATIVE_RESPONSE,
                negativeResponse = it.response,
            )
        }
        if (compact.contains("ERROR")) return DiagnosticExchangeClassification(ModuleScanOutcome.FAILED)
        return DiagnosticExchangeClassification(ModuleScanOutcome.MALFORMED_RESPONSE)
    }

    /** Parses Mode 02 PID 02 and deliberately skips the echoed frame byte. */
    fun parseFreezeFrameIdentity(rawResponse: String): String? {
        val clean = CanMultiFrameParser.parse(rawResponse)
            .replace(Regex("[^0-9A-Fa-f]"), "")
            .uppercase()
        val marker = "4202"
        val index = clean.indexOf(marker)
        if (index < 0) return null
        val dataWithFrame = clean.substring(index + marker.length)
        if (dataWithFrame.length < 6) return null
        val dtcBytes = dataWithFrame.substring(2, 6)
        return DtcDecoder.hexToDtcOrNull(dtcBytes)
    }

    private fun isNoiseLine(line: String): Boolean {
        return line == "OK" ||
            line == ">" ||
            line.startsWith("AT") ||
            line.startsWith("SEARCHING") ||
            line.startsWith("NO DATA") ||
            line.startsWith("UNABLE") ||
            line.startsWith("ERROR") ||
            line.startsWith("?")
    }

    private fun isDiagnosticCanHeader(header: String): Boolean {
        val len = header.length
        if (len == 3) {
            return header[0] == '7' && isHexChar(header[1]) && isHexChar(header[2])
        }
        if (len == 8) {
            if (header.startsWith("18DA") || header.startsWith("18DB")) {
                return isHexChar(header[4]) && isHexChar(header[5]) &&
                       isHexChar(header[6]) && isHexChar(header[7])
            }
        }
        return false
    }
}
