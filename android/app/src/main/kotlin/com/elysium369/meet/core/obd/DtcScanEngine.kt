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
    WARNING_INDICATOR_REQUESTED,
    UNKNOWN
}

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
    val timestampMs: Long = System.currentTimeMillis()
)

data class DtcModuleReport(
    val targetAddress: String?,
    val responseAddress: String?,
    val moduleName: String,
    val isAlive: Boolean,
    val dtcs: List<DtcRecord>,
    val rawExchanges: List<DtcRawExchange>
)

data class DtcScanReport(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val protocol: String,
    val records: List<DtcRecord>,
    val modules: List<DtcModuleReport>,
    val rawExchanges: List<DtcRawExchange>
) {
    fun codesForBucket(bucket: DtcBucket): List<String> =
        records.filter { it.bucket == bucket }.map { it.code }.distinct()
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

    private fun flagsForUdsStatus(statusByte: Int): Set<DtcStatusFlag> {
        val flags = mutableSetOf<DtcStatusFlag>()
        if (statusByte and 0x01 != 0) {
            flags += DtcStatusFlag.TEST_FAILED
            flags += DtcStatusFlag.CURRENT
        }
        if (statusByte and 0x02 != 0) flags += DtcStatusFlag.TEST_FAILED_THIS_CYCLE
        if (statusByte and 0x04 != 0) flags += DtcStatusFlag.PENDING
        if (statusByte and 0x08 != 0) flags += DtcStatusFlag.CONFIRMED
        if (statusByte and 0x20 != 0) flags += DtcStatusFlag.HISTORY
        if (statusByte and 0x80 != 0) flags += DtcStatusFlag.WARNING_INDICATOR_REQUESTED
        if (statusByte and 0x20 != 0 && statusByte and 0x01 == 0) flags += DtcStatusFlag.INTERMITTENT
        if (flags.isEmpty()) flags += DtcStatusFlag.UNKNOWN
        return flags
    }

    private fun bucketForUdsFlags(flags: Set<DtcStatusFlag>): DtcBucket = when {
        DtcStatusFlag.CONFIRMED in flags || DtcStatusFlag.CURRENT in flags || DtcStatusFlag.TEST_FAILED in flags -> DtcBucket.ACTIVE
        DtcStatusFlag.PENDING in flags -> DtcBucket.PENDING
        DtcStatusFlag.HISTORY in flags || DtcStatusFlag.INTERMITTENT in flags -> DtcBucket.HISTORY
        else -> DtcBucket.HISTORY
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
