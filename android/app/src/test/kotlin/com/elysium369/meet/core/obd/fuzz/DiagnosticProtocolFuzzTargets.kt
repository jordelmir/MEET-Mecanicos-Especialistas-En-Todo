package com.elysium369.meet.core.obd.fuzz

import com.elysium369.meet.core.obd.CanMultiFrameParser
import com.elysium369.meet.core.obd.DiagnosticPduDecoder
import com.elysium369.meet.core.obd.DtcScanEngine
import com.elysium369.meet.core.obd.Mode06Parser

/** Entry points compatible with a coverage-guided JVM fuzzer without production dependencies. */
object DiagnosticProtocolFuzzTargets {
    @JvmStatic
    fun fuzzerTestOneInput(input: ByteArray) {
        if (input.size > MAX_INPUT_BYTES) return
        val raw = input.toString(Charsets.ISO_8859_1)
        DiagnosticPduDecoder.decodePdus(raw)
        DiagnosticPduDecoder.decodeResponses(raw, expectedPositiveService = 0x59, requestedService = 0x19)
        CanMultiFrameParser.parse(raw)
        Mode06Parser().parse(raw)
        DtcScanEngine.parseStandardByEcu(raw, mode = "03")
        DtcScanEngine.parseUdsService19ByEcu(raw)
        DtcScanEngine.parseFreezeFrameIdentity(raw)
    }

    private const val MAX_INPUT_BYTES = 65_536
}
