package com.elysium369.meet.core.obd

import android.util.Log

/**
 * Professional Mode $06 (On-Board Diagnostic Test Results) Parser.
 * Strictly implements SAE J1979 / SAE J1979-DA / ISO 15765-4.
 *
 * Enforces Metrological Truth Safety:
 * - Unknown UASID -> DecodeStatus.UNKNOWN_UASID, verdict UNKNOWN, no invented unit/scaling.
 * - Missing limits (no min and no max) -> DecodeStatus.NO_LIMITS, verdict UNKNOWN (never silent PASS).
 * - Distinguishes CAN vs Legacy OBD protocols.
 */
class Mode06Parser(
    private val registry: Mode06DefinitionRegistry = DefaultMode06DefinitionRegistry()
) {

    companion object {
        private const val TAG = "Mode06Parser"

        /**
         * SAE J1979-DA UASID Scaling and Offset Table.
         * Returns (multiplier, offset, unit) if known, or null if unknown.
         */
        fun getUasidDefinition(uasid: Int): Triple<Double, Double, String>? {
            return when (uasid) {
                0x01 -> Triple(1.0, 0.0, "cnt")
                0x02 -> Triple(0.1, 0.0, "cnt")
                0x07 -> Triple(0.001, 0.0, "V")
                0x08 -> Triple(0.01, 0.0, "V")
                0x0B -> Triple(1.0, 0.0, "ms")
                0x0C -> Triple(0.1, 0.0, "ms")
                0x0D -> Triple(0.001, 0.0, "s")
                0x10 -> Triple(1.0, 0.0, "Pa")
                0x11 -> Triple(0.1, 0.0, "kPa")
                0x13 -> Triple(0.01, 0.0, "kPa")
                0x1B -> Triple(0.1, 0.0, "ratio")
                0x23 -> Triple(1.0, 0.0, "g/s")
                0x25 -> Triple(0.01, 0.0, "g/s")
                0x2D -> Triple(0.1, -40.0, "°C")
                0x30 -> Triple(0.01, 0.0, "A")
                0x31 -> Triple(0.001, 0.0, "A")
                else -> null
            }
        }
    }

    private val canDecoder = CanMode06Decoder(registry)
    private val legacyDecoder = LegacyMode06Decoder(registry)

    /**
     * Backward-compatible parse function. Defaults to CAN decoding if protocol is unstated.
     */
    fun parse(rawResponse: String): List<Mode06TestResult> {
        return parse(rawResponse, ProtocolFamily.CAN_11BIT, null)
    }

    /**
     * Protocol-aware strict parse function.
     */
    fun parse(
        rawResponse: String,
        protocolFamily: ProtocolFamily,
        ecuAddress: String? = null
    ): List<Mode06TestResult> {
        val clean = sanitizeResponse(rawResponse)
        if (!clean.contains("46")) return emptyList()

        return when (protocolFamily) {
            ProtocolFamily.CAN_11BIT, ProtocolFamily.CAN_29BIT -> {
                canDecoder.decode(clean, protocolFamily, ecuAddress, rawResponse)
            }
            ProtocolFamily.ISO_K_LINE, ProtocolFamily.J1850_PWM, ProtocolFamily.J1850_VPW -> {
                legacyDecoder.decode(clean, protocolFamily, ecuAddress, rawResponse)
            }
            ProtocolFamily.UNKNOWN -> {
                // If unknown, attempt CAN first; fallback to legacy if CAN produces nothing
                val canResults = canDecoder.decode(clean, ProtocolFamily.CAN_11BIT, ecuAddress, rawResponse)
                if (canResults.isNotEmpty()) canResults
                else legacyDecoder.decode(clean, ProtocolFamily.ISO_K_LINE, ecuAddress, rawResponse)
            }
        }
    }

    private fun sanitizeResponse(rawResponse: String): String {
        val filtered = rawResponse
            .replace("\r", "\n")
            .split("\n")
            .map { it.trim() }
            .filter { line ->
                val upper = line.uppercase().replace(" ", "")
                upper.isNotBlank() &&
                    upper != "OK" && upper != ">" &&
                    !upper.startsWith("AT") &&
                    !upper.startsWith("SEARCHING") &&
                    !upper.startsWith("NODATA") &&
                    !upper.startsWith("NO DATA") &&
                    !upper.startsWith("UNABLE") &&
                    !upper.startsWith("ERROR") &&
                    !upper.startsWith("?")
            }
            .joinToString(" ")

        val unified = CanMultiFrameParser.parse(filtered)
        return unified.uppercase().replace(Regex("[^0-9A-F]"), "")
    }
}

/**
 * ISO 15765-4 CAN Mode $06 Decoder.
 * Expects records formatted as:
 * 46 [MID 1B] [TID 1B] [UASID 1B] [VAL 2B] [MIN 2B] [MAX 2B] = 10 bytes (20 hex characters).
 */
class CanMode06Decoder(
    private val registry: Mode06DefinitionRegistry
) {
    companion object {
        private const val TAG = "CanMode06Decoder"
    }

    fun decode(
        cleanHex: String,
        protocolFamily: ProtocolFamily,
        ecuAddress: String?,
        originalRaw: String
    ): List<Mode06TestResult> {
        val results = mutableListOf<Mode06TestResult>()
        var i = 0

        while (i < cleanHex.length) {
            val start = cleanHex.indexOf("46", i)
            if (start < 0) break

            // A standard CAN record with 46 prefix requires at least 20 hex characters:
            // 46 (2) + MID (2) + TID (2) + UASID (2) + VAL (4) + MIN (4) + MAX (4) = 20
            if (start + 20 > cleanHex.length) {
                i = start + 2
                continue
            }

            try {
                val midHex = cleanHex.substring(start + 2, start + 4)
                val tidHex = cleanHex.substring(start + 4, start + 6)
                val uasidHex = cleanHex.substring(start + 6, start + 8)
                val valHex = cleanHex.substring(start + 8, start + 12)
                val minHex = cleanHex.substring(start + 12, start + 16)
                val maxHex = cleanHex.substring(start + 16, start + 20)

                val mid = midHex.toInt(16)
                val tid = tidHex.toInt(16)
                val uasid = uasidHex.toInt(16)

                val rawValue = valHex.toIntOrNull(16)
                val rawMin = if (minHex != "FFFF") minHex.toIntOrNull(16) else null
                val rawMax = if (maxHex != "FFFF") maxHex.toIntOrNull(16) else null

                val uasidDef = Mode06Parser.getUasidDefinition(uasid)

                val decodeStatus: DecodeStatus
                val scaledValue: Double?
                val scaledMin: Double?
                val scaledMax: Double?
                val unit: String
                val verdict: Mode06Verdict

                if (uasidDef == null) {
                    // Strict Truth Safety: Unknown UASID must NEVER assume scaling 1.0 or verdict PASS
                    decodeStatus = DecodeStatus.UNKNOWN_UASID
                    scaledValue = null
                    scaledMin = null
                    scaledMax = null
                    unit = ""
                    verdict = Mode06Verdict.UNKNOWN
                } else if (rawMin == null && rawMax == null) {
                    // Strict Truth Safety: Missing limits cannot evaluate to PASS
                    decodeStatus = DecodeStatus.NO_LIMITS
                    val (scaling, offset, u) = uasidDef
                    scaledValue = rawValue?.let { it * scaling + offset }
                    scaledMin = null
                    scaledMax = null
                    unit = u
                    verdict = Mode06Verdict.UNKNOWN
                } else {
                    decodeStatus = DecodeStatus.DECODED
                    val (scaling, offset, u) = uasidDef
                    scaledValue = rawValue?.let { it * scaling + offset }
                    scaledMin = rawMin?.let { it * scaling + offset }
                    scaledMax = rawMax?.let { it * scaling + offset }
                    unit = u

                    verdict = when {
                        scaledValue == null -> Mode06Verdict.UNKNOWN
                        scaledMin != null && scaledMax != null -> {
                            if (scaledValue in scaledMin..scaledMax) Mode06Verdict.PASS else Mode06Verdict.FAIL
                        }
                        scaledMax != null -> {
                            if (scaledValue <= scaledMax) Mode06Verdict.PASS else Mode06Verdict.FAIL
                        }
                        scaledMin != null -> {
                            if (scaledValue >= scaledMin) Mode06Verdict.PASS else Mode06Verdict.FAIL
                        }
                        else -> Mode06Verdict.UNKNOWN
                    }
                }

                val passed = verdict == Mode06Verdict.PASS

                // Severity calculation
                var severity = when (verdict) {
                    Mode06Verdict.FAIL -> DiagnosticSeverity.HIGH
                    Mode06Verdict.PASS -> DiagnosticSeverity.INFO
                    Mode06Verdict.UNKNOWN -> DiagnosticSeverity.MODERATE
                    Mode06Verdict.NOT_APPLICABLE -> DiagnosticSeverity.INFO
                }

                if (verdict == Mode06Verdict.PASS && scaledValue != null) {
                    if (scaledMax != null && scaledValue > (scaledMax * 0.9)) severity = DiagnosticSeverity.MODERATE
                    if (scaledMin != null && scaledValue < (scaledMin * 1.1)) severity = DiagnosticSeverity.MODERATE
                }

                val semanticKey = Mode06SemanticKey(protocolFamily, mid, tid, uasid)
                val definition = registry.resolve(semanticKey)
                val compName = definition?.componentName ?: String.format("Monitor ID \$%02X", mid)
                val testName = definition?.testName ?: String.format("Prueba ID \$%02X", tid)

                val proTip = generateProTip(mid, tid, scaledValue, scaledMin, scaledMax, verdict, severity)

                results.add(
                    Mode06TestResult(
                        mid = String.format("\$%02X", mid),
                        tid = String.format("\$%02X", tid),
                        value = scaledValue?.toFloat() ?: 0f,
                        minLimit = scaledMin?.toFloat(),
                        maxLimit = scaledMax?.toFloat(),
                        unit = unit,
                        passed = passed,
                        testName = testName,
                        componentName = compName,
                        proTip = proTip,
                        severity = severity,
                        ecuAddress = ecuAddress,
                        protocol = protocolFamily.name,
                        midInt = mid,
                        tidInt = tid,
                        uasid = uasid,
                        rawValue = rawValue,
                        rawMin = rawMin,
                        rawMax = rawMax,
                        valueDouble = scaledValue,
                        minLimitDouble = scaledMin,
                        maxLimitDouble = scaledMax,
                        verdict = verdict,
                        decodeStatus = decodeStatus,
                        rawResponse = cleanHex.substring(start, start + 20),
                        capturedAtMonotonicMs = System.currentTimeMillis()
                    )
                )

                i = start + 20
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed CAN Mode 06 record at offset $start: ${e.message}")
                i = start + 2
            }
        }

        return results
    }

    private fun generateProTip(
        mid: Int,
        tid: Int,
        value: Double?,
        min: Double?,
        max: Double?,
        verdict: Mode06Verdict,
        severity: DiagnosticSeverity
    ): String? {
        if (verdict == Mode06Verdict.PASS && severity == DiagnosticSeverity.INFO) return null

        if (verdict == Mode06Verdict.PASS && severity == DiagnosticSeverity.MODERATE) {
            return "ALERTA PREVENTIVA: El valor está muy cerca del límite de falla. Aunque el test pasó, este componente está empezando a degradarse. Recomiende revisión preventiva."
        }

        return when {
            mid in 0xA1..0xAC -> {
                "Falla de encendido (Misfire) detectada en cilindro ${mid - 0xA0}. Verifique bujías, bobinas e inyector para evitar daños al catalizador."
            }
            mid in 0x21..0x24 -> {
                "Baja eficiencia catalítica detectada. Verifique si existen fugas de escape antes del catalizador o sensores de O2 degradados antes de cambiar el convertidor."
            }
            mid in 0x31..0x32 -> {
                "Falla en monitor EGR / VVT. Limpie conductos de carbonilla o revise la electroválvula antes de sustituir componentes."
            }
            mid in 0x01..0x08 -> {
                "Sensor de Oxígeno fuera de rango o con conmutación lenta. Impacta directamente las emisiones y el consumo."
            }
            mid in 0x35..0x3B -> {
                "Fuga detectada en sistema EVAP. Verifique el tapón de combustible y mangueras de vacío del cánister."
            }
            else -> "Parámetro fuera de especificación según reporte de ECU."
        }
    }
}

/**
 * Pre-CAN (Legacy J1850 / ISO 9141-2 / ISO 14230 KWP) Mode $06 Decoder.
 * Non-CAN Mode $06 has format: 46 [TID 1B] [VAL 2B] [LIM 2B] or 46 [MID 1B] [TID 1B] [VAL 2B] [MIN 2B] [MAX 2B] without UASID.
 */
class LegacyMode06Decoder(
    private val registry: Mode06DefinitionRegistry
) {
    companion object {
        private const val TAG = "LegacyMode06Decoder"
    }

    fun decode(
        cleanHex: String,
        protocolFamily: ProtocolFamily,
        ecuAddress: String?,
        originalRaw: String
    ): List<Mode06TestResult> {
        val results = mutableListOf<Mode06TestResult>()
        var i = 0

        while (i < cleanHex.length) {
            val start = cleanHex.indexOf("46", i)
            if (start < 0) break

            // Need at least 14 hex chars for a legacy record: 46 (2) + MID (2) + TID (2) + VAL (4) + LIM (4) = 14
            if (start + 14 > cleanHex.length) {
                i = start + 2
                continue
            }

            try {
                val midHex = cleanHex.substring(start + 2, start + 4)
                val tidHex = cleanHex.substring(start + 4, start + 6)
                val valHex = cleanHex.substring(start + 6, start + 10)

                val mid = midHex.toInt(16)
                val tid = tidHex.toInt(16)
                val rawValue = valHex.toIntOrNull(16)

                // Check if min/max (18 hex chars total) or single limit (14 hex chars total)
                val hasDualLimits = start + 18 <= cleanHex.length
                val minHex = if (hasDualLimits) cleanHex.substring(start + 10, start + 14) else null
                val maxHex = if (hasDualLimits) cleanHex.substring(start + 14, start + 18) else cleanHex.substring(start + 10, start + 14)

                val rawMin = if (minHex != null && minHex != "FFFF") minHex.toIntOrNull(16) else null
                val rawMax = if (maxHex != "FFFF") maxHex.toIntOrNull(16) else null

                // In legacy Mode 06, scaling is defined by TID or OEM definition
                val scaledValue = rawValue?.toDouble()
                val scaledMin = rawMin?.toDouble()
                val scaledMax = rawMax?.toDouble()

                val verdict: Mode06Verdict
                val decodeStatus: DecodeStatus

                if (scaledMin == null && scaledMax == null) {
                    decodeStatus = DecodeStatus.NO_LIMITS
                    verdict = Mode06Verdict.UNKNOWN
                } else {
                    decodeStatus = DecodeStatus.DECODED
                    verdict = when {
                        scaledValue == null -> Mode06Verdict.UNKNOWN
                        scaledMin != null && scaledMax != null -> {
                            if (scaledValue in scaledMin..scaledMax) Mode06Verdict.PASS else Mode06Verdict.FAIL
                        }
                        scaledMax != null -> {
                            if (scaledValue <= scaledMax) Mode06Verdict.PASS else Mode06Verdict.FAIL
                        }
                        scaledMin != null -> {
                            if (scaledValue >= scaledMin) Mode06Verdict.PASS else Mode06Verdict.FAIL
                        }
                        else -> Mode06Verdict.UNKNOWN
                    }
                }

                val semanticKey = Mode06SemanticKey(protocolFamily, mid, tid, null)
                val definition = registry.resolve(semanticKey)
                val compName = definition?.componentName ?: String.format("Monitor ID \$%02X", mid)
                val testName = definition?.testName ?: String.format("Prueba ID \$%02X", tid)

                val recordLen = if (hasDualLimits) 18 else 14
                results.add(
                    Mode06TestResult(
                        mid = String.format("\$%02X", mid),
                        tid = String.format("\$%02X", tid),
                        value = scaledValue?.toFloat() ?: 0f,
                        minLimit = scaledMin?.toFloat(),
                        maxLimit = scaledMax?.toFloat(),
                        unit = "raw",
                        passed = verdict == Mode06Verdict.PASS,
                        testName = testName,
                        componentName = compName,
                        severity = if (verdict == Mode06Verdict.FAIL) DiagnosticSeverity.HIGH else DiagnosticSeverity.INFO,
                        ecuAddress = ecuAddress,
                        protocol = protocolFamily.name,
                        midInt = mid,
                        tidInt = tid,
                        uasid = null,
                        rawValue = rawValue,
                        rawMin = rawMin,
                        rawMax = rawMax,
                        valueDouble = scaledValue,
                        minLimitDouble = scaledMin,
                        maxLimitDouble = scaledMax,
                        verdict = verdict,
                        decodeStatus = decodeStatus,
                        rawResponse = cleanHex.substring(start, start + recordLen),
                        capturedAtMonotonicMs = System.currentTimeMillis()
                    )
                )

                i = start + recordLen
            } catch (e: Exception) {
                Log.w(TAG, "Skipping legacy Mode 06 record at offset $start: ${e.message}")
                i = start + 2
            }
        }

        return results
    }
}
