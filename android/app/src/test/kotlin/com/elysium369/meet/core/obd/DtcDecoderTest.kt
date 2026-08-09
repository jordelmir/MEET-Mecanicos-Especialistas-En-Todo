package com.elysium369.meet.core.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcDecoderTest {

    @Test
    fun decodeMode03StandardResponse() {
        val codes = DtcDecoder.decode("43 03 00 01 71 00 00", "03")

        assertEquals(listOf("P0300", "P0171"), codes)
    }

    @Test
    fun decodeMode03ResponseWithCountByte() {
        val codes = DtcDecoder.decode("43 02 03 00 01 71 00 00", "03")

        assertEquals(listOf("P0300", "P0171"), codes)
    }

    @Test
    fun decodeIgnoresInvalidPayloadInsteadOfInventingP0000() {
        val codes = DtcDecoder.decode("43 ZZ ZZ 00 00", "03")

        assertTrue(codes.isEmpty())
    }

    @Test
    fun parseStandardByEcuKeepsResponseAddress() {
        val records = DtcScanEngine.parseStandardByEcu(
            rawResponse = """
                7E8 43 03 00 00 00
                7E9 43 01 71 00 00
            """.trimIndent(),
            mode = "03",
            targetAddress = "7DF"
        )

        assertEquals(setOf("P0300", "P0171"), records.map { it.code }.toSet())
        assertEquals(setOf("7E8", "7E9"), records.mapNotNull { it.responseAddress }.toSet())
    }

    @Test
    fun parseUdsService19StatusPayload() {
        val records = DtcScanEngine.parseUdsService19ByEcu(
            rawResponse = "7E8 59 02 FF 03 00 00 0C",
            targetAddress = "7E0",
            moduleName = "ECM"
        )

        assertEquals(1, records.size)
        assertEquals("P0300", records.first().code)
        assertEquals(DtcBucket.PENDING, records.first().bucket)
        assertTrue(DtcStatusFlag.CONFIRMED in records.first().statusFlags)
        assertTrue(DtcStatusFlag.TEST_FAILED !in records.first().statusFlags)
        assertTrue(DtcStatusFlag.CURRENT !in records.first().statusFlags)
    }

    @Test
    fun udsConfirmedAloneIsStoredNotCurrentlyFailing() {
        val records = DtcScanEngine.parseUdsService19ByEcu(
            rawResponse = "7E8 59 02 FF 03 00 00 08",
            targetAddress = "7E0",
            moduleName = "ECM",
        )

        assertEquals(DtcBucket.HISTORY, records.single().bucket)
        assertTrue(DtcStatusFlag.CONFIRMED in records.single().statusFlags)
        assertTrue(DtcStatusFlag.TEST_FAILED !in records.single().statusFlags)
    }

    @Test
    fun udsStatusPreservesAllEightNormativeBits() {
        val flags = DtcScanEngine.flagsForUdsStatus(0xFF)

        assertTrue(DtcStatusFlag.TEST_FAILED in flags)
        assertTrue(DtcStatusFlag.TEST_FAILED_THIS_CYCLE in flags)
        assertTrue(DtcStatusFlag.PENDING in flags)
        assertTrue(DtcStatusFlag.CONFIRMED in flags)
        assertTrue(DtcStatusFlag.TEST_NOT_COMPLETED_SINCE_LAST_CLEAR in flags)
        assertTrue(DtcStatusFlag.TEST_FAILED_SINCE_LAST_CLEAR in flags)
        assertTrue(DtcStatusFlag.TEST_NOT_COMPLETED_THIS_CYCLE in flags)
        assertTrue(DtcStatusFlag.WARNING_INDICATOR_REQUESTED in flags)
    }

    @Test
    fun exchangeClassifierNeverTurnsMissingEvidenceIntoNoDtc() {
        assertEquals(
            ModuleScanOutcome.NO_RESPONSE,
            DtcScanEngine.classifyExchange("", "43", 0),
        )
        assertEquals(
            ModuleScanOutcome.UNSUPPORTED_SERVICE,
            DtcScanEngine.classifyExchange("NO DATA", "43", 0),
        )
        assertEquals(
            ModuleScanOutcome.NO_DTC,
            DtcScanEngine.classifyExchange("7E8 43 00 00 00", "43", 0),
        )
    }

    @Test
    fun freezeFrameIdentitySkipsFrameNumber() {
        assertEquals(
            "P0171",
            DtcScanEngine.parseFreezeFrameIdentity("7E8 42 02 00 01 71"),
        )
    }

    @Test
    fun failedServiceCannotMarkPriorCodeNotObserved() {
        val module = DtcModuleReport(
            targetAddress = "7E0",
            responseAddress = "7E8",
            moduleName = "ECM",
            isAlive = true,
            dtcs = emptyList(),
            rawExchanges = emptyList(),
            serviceReads = listOf(DtcServiceRead("03", DtcBucket.ACTIVE, ModuleScanOutcome.NO_RESPONSE)),
            outcome = ModuleScanOutcome.PARTIAL_RESPONSE,
        )

        assertFalse(
            DtcObservationPolicy.canMarkNotObserved(module, DtcBucket.ACTIVE, "P0300", emptyList())
        )
    }

    @Test
    fun conclusiveEmptyServiceCanMarkPriorCodeNotObservedButNotResolved() {
        val module = DtcModuleReport(
            targetAddress = "7E0",
            responseAddress = "7E8",
            moduleName = "ECM",
            isAlive = true,
            dtcs = emptyList(),
            rawExchanges = emptyList(),
            serviceReads = listOf(DtcServiceRead("03", DtcBucket.ACTIVE, ModuleScanOutcome.NO_DTC)),
            outcome = ModuleScanOutcome.NO_DTC,
        )

        assertTrue(
            DtcObservationPolicy.canMarkNotObserved(module, DtcBucket.ACTIVE, "P0300", emptyList())
        )
    }
}
