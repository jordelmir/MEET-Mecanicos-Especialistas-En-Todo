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

data class DtcServiceRead(
    val command: String,
    val bucket: DtcBucket?,
    val outcome: ModuleScanOutcome,
)

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
)

data class DtcRecord(
    val code: String,
    val bucket: DtcBucket,
    val statusFlags: Set<DtcStatusFlag>,
    val sourceService: String,
    val targetAddress: String? = null,
    val responseAddress: String? = null,
    val moduleName: String? = null,
    val rawPayload: String,
    val udsStatusByte: Int? = null,
    val udsFailureType: String? = null
)

data class DtcRawExchange(
    val command: String,
    val targetAddress: String?,
    val rawResponse: String,
    val parsedRecordCount: Int,
    val outcome: ModuleScanOutcome = ModuleScanOutcome.COMPLETE,
    val timestampMs: Long = System.currentTimeMillis()
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
) {
    fun completedBucket(bucket: DtcBucket): Boolean =
        serviceReads.any {
            (it.bucket == bucket || it.bucket == null) && it.outcome.provesBucketWasRead
        }
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
    ): Boolean = module != null &&
        module.isAlive &&
        module.completedBucket(bucket) &&
        records.none { it.bucket == bucket && it.code.equals(code, ignoreCase = true) }
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
        val clean = payload.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
        if (clean.isBlank() || clean.contains("7F19")) return emptyList()

        val records = mutableListOf<DtcRecord>()
        var idx = clean.indexOf("59")
        while (idx >= 0 && idx + 6 <= clean.length) {
            val subFunction = clean.substring(idx + 2, idx + 4)
            if (subFunction !in setOf("02", "07", "08", "0A", "0B", "0C", "0D", "0E", "0F", "14")) {
                idx = clean.indexOf("59", idx + 2)
                continue
            }

            var pos = idx + 6 // 59 + sub-function + availability mask
            while (pos + 8 <= clean.length) {
                val dtcBytes = clean.substring(pos, pos + 6)
                val statusByte = clean.substring(pos + 6, pos + 8).toIntOrNull(16) ?: break
                if (dtcBytes == "000000" || dtcBytes == "FFFFFF") {
                    pos += 8
                    continue
                }

                val code = DtcDecoder.hexToDtcOrNull(dtcBytes.substring(0, 4))
                if (code != null) {
                    val flags = flagsForUdsStatus(statusByte)
                    records.add(
                        DtcRecord(
                            code = code,
                            bucket = bucketForUdsFlags(flags),
                            statusFlags = flags,
                            sourceService = "19$subFunction",
                            targetAddress = targetAddress,
                            responseAddress = responseAddress,
                            moduleName = moduleName,
                            rawPayload = rawPayload,
                            udsStatusByte = statusByte,
                            udsFailureType = dtcBytes.substring(4, 6)
                        )
                    )
                }
                pos += 8
            }

            idx = clean.indexOf("59", idx + 2)
        }

        return records
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
    ): ModuleScanOutcome {
        if (failed) return ModuleScanOutcome.FAILED
        val compact = rawResponse.uppercase().replace(Regex("[^0-9A-Z?]"), "")
        if (compact.isBlank()) return ModuleScanOutcome.NO_RESPONSE
        if (compact.contains("STOPPED")) return ModuleScanOutcome.CANCELLED
        if (compact.contains("UNABLETOCONNECT") || compact.contains("BUSERROR") || compact.contains("CANERROR")) {
            return ModuleScanOutcome.NO_RESPONSE
        }
        if (compact.contains("NODATA") || compact == "?") return ModuleScanOutcome.UNSUPPORTED_SERVICE
        val requestService = when (positiveResponseService) {
            "43" -> "03"
            "47" -> "07"
            "4A" -> "0A"
            "59" -> "19"
            else -> positiveResponseService
        }
        if (compact.contains("7F$requestService")) {
            return ModuleScanOutcome.NEGATIVE_RESPONSE
        }
        if (compact.contains(positiveResponseService)) {
            return if (parsedRecordCount > 0) ModuleScanOutcome.COMPLETE else ModuleScanOutcome.NO_DTC
        }
        if (compact.contains("ERROR")) return ModuleScanOutcome.FAILED
        return ModuleScanOutcome.MALFORMED_RESPONSE
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
