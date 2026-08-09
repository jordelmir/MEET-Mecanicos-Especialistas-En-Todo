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
            serviceReads = listOf(
                DtcServiceRead(
                    "03",
                    DiagnosticCoverage.sae(DtcBucket.ACTIVE),
                    ModuleScanOutcome.NO_RESPONSE,
                ),
            ),
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
            serviceReads = listOf(
                DtcServiceRead(
                    "03",
                    DiagnosticCoverage.sae(DtcBucket.ACTIVE),
                    ModuleScanOutcome.NO_DTC,
                ),
            ),
            outcome = ModuleScanOutcome.NO_DTC,
        )

        assertTrue(
            DtcObservationPolicy.canMarkNotObserved(module, DtcBucket.ACTIVE, "P0300", emptyList())
        )
    }

    @Test
    fun udsCoverageCannotProveSaePermanentBucketWasRead() {
        val module = DtcModuleReport(
            targetAddress = "7E0",
            responseAddress = "7E8",
            moduleName = "ECM",
            isAlive = true,
            dtcs = emptyList(),
            rawExchanges = emptyList(),
            serviceReads = listOf(
                DtcServiceRead(
                    command = "1902FF",
                    coverage = DiagnosticCoverage.udsForStatusMask(0xFF),
                    outcome = ModuleScanOutcome.NO_DTC,
                ),
                DtcServiceRead(
                    command = "0A",
                    coverage = DiagnosticCoverage.sae(DtcBucket.PERMANENT),
                    outcome = ModuleScanOutcome.TIMEOUT,
                ),
            ),
            outcome = ModuleScanOutcome.PARTIAL_RESPONSE,
        )

        assertFalse(module.completedBucket(DtcBucket.PERMANENT))
        assertFalse(
            DtcObservationPolicy.canMarkNotObserved(
                module = module,
                bucket = DtcBucket.PERMANENT,
                code = "P0606",
                records = emptyList(),
            ),
        )
    }

    @Test
    fun sameCodeInAnotherModuleDoesNotBlockModuleScopedAbsence() {
        val ecm = DtcModuleReport(
            targetAddress = "7E0",
            responseAddress = "7E8",
            moduleName = "ECM",
            isAlive = true,
            dtcs = emptyList(),
            rawExchanges = emptyList(),
            serviceReads = listOf(
                DtcServiceRead(
                    "03",
                    DiagnosticCoverage.sae(DtcBucket.ACTIVE),
                    ModuleScanOutcome.NO_DTC,
                ),
            ),
            outcome = ModuleScanOutcome.NO_DTC,
        )
        val tcmFinding = DtcRecord(
            code = "P0606",
            bucket = DtcBucket.ACTIVE,
            statusFlags = setOf(DtcStatusFlag.CURRENT),
            sourceService = "03",
            targetAddress = "7E1",
            responseAddress = "7E9",
            moduleName = "TCM",
            rawPayload = "7E9 43 06 06",
        )

        assertTrue(
            DtcObservationPolicy.canMarkNotObserved(
                module = ecm,
                bucket = DtcBucket.ACTIVE,
                code = "P0606",
                records = listOf(tcmFinding),
            ),
        )
    }

    @Test
    fun udsDecoderNeverTreatsEmbeddedServiceByteAsResponse() {
        val records = DtcScanEngine.parseUdsService19ByEcu(
            rawResponse = "7E8 62 F1 90 59 02 FF 03 00 00 0C",
            targetAddress = "7E0",
            moduleName = "ECM",
        )

        assertTrue(records.isEmpty())
    }

    @Test
    fun udsDecoderRejectsMalformedRecordLength() {
        val records = DtcScanEngine.parseUdsService19ByEcu(
            rawResponse = "7E8 59 02 FF 03 00 00",
            targetAddress = "7E0",
            moduleName = "ECM",
        )

        assertTrue(records.isEmpty())
    }

    @Test
    fun udsDecoderReassemblesIsoTpBeforeParsingRecords() {
        val records = DtcScanEngine.parseUdsService19ByEcu(
            rawResponse = """
                7E8 10 0B 59 02 FF 03 00 00
                7E8 21 0C 01 71 00 08 00 00
            """.trimIndent(),
            targetAddress = "7E0",
            moduleName = "ECM",
        )

        assertEquals(listOf("P0300", "P0171"), records.map { it.code })
    }

    @Test
    fun negativeUdsResponsePreservesRetryPendingSemantics() {
        val result = DtcScanEngine.classifyExchangeDetailed(
            rawResponse = "7E8 7F 19 78",
            positiveResponseService = "59",
            parsedRecordCount = 0,
        )

        assertEquals(ModuleScanOutcome.NEGATIVE_RESPONSE, result.outcome)
        assertEquals(NegativeResponseSemantics.RETRY_PENDING, result.negativeResponse?.semantics)
        assertEquals(0x19, result.negativeResponse?.requestedService)
        assertEquals(0x78, result.negativeResponse?.responseCode)
    }

    @Test
    fun findingIdentityNeverCollapsesSameCodeAcrossModules() {
        val ecmFinding = DtcRecord(
            code = "U0100",
            bucket = DtcBucket.ACTIVE,
            statusFlags = setOf(DtcStatusFlag.CURRENT),
            sourceService = "03",
            targetAddress = "7E0",
            responseAddress = "7E8",
            moduleName = "ECM",
            rawPayload = "7E8 43 01 00",
        )
        val tcmFinding = ecmFinding.copy(
            targetAddress = "7E1",
            responseAddress = "7E9",
            moduleName = "TCM",
        )

        assertFalse(ecmFinding.findingKey("vehicle-1") == tcmFinding.findingKey("vehicle-1"))
        assertEquals("7E0", ecmFinding.findingKey("vehicle-1").moduleIdentity)
        assertEquals("7E1", tcmFinding.findingKey("vehicle-1").moduleIdentity)
    }

    @Test
    fun functionalAndPhysicalAddressesResolveToSameModuleIdentity() {
        assertEquals(
            "7E0",
            DiagnosticModuleIdentity.canonical(
                targetAddress = "7DF",
                responseAddress = "7E8",
                moduleName = "Functional Broadcast",
            ),
        )
        assertEquals(
            "7E0",
            DiagnosticModuleIdentity.canonical(
                targetAddress = "7E0",
                responseAddress = "7E8",
                moduleName = "ECM",
            ),
        )
    }

    @Test
    fun physicalBusLeaseOnlyAllowsTheExclusiveOwner() {
        assertTrue(PhysicalBusLeasePolicy.allows(PhysicalBusOwner.IDLE, null))
        assertTrue(
            PhysicalBusLeasePolicy.allows(
                PhysicalBusOwner.DIAGNOSTIC_SCAN,
                PhysicalBusOwner.DIAGNOSTIC_SCAN,
            ),
        )
        assertFalse(
            PhysicalBusLeasePolicy.allows(
                PhysicalBusOwner.OSCILLOSCOPE,
                PhysicalBusOwner.DIAGNOSTIC_SCAN,
            ),
        )
        assertFalse(PhysicalBusLeasePolicy.allows(PhysicalBusOwner.ACTIVE_TEST, null))
    }

    @Test
    fun scanPlanPrioritizesConfirmedRespondersWithoutMakingCandidatesRequired() {
        val confirmed = NetworkModule(
            id = "7E1",
            name = "TCM confirmado",
            isAlive = true,
            responseId = "7E9",
        )
        val candidates = linkedMapOf("7E0" to "ECM candidato", "7E1" to "TCM genérico")

        val full = DiagnosticScanPlanCompiler.compile(
            DiagnosticScanMode.FULL_VEHICLE,
            listOf(confirmed),
            candidates,
        )
        val quick = DiagnosticScanPlanCompiler.compile(
            DiagnosticScanMode.QUICK,
            listOf(confirmed),
            candidates,
        )

        assertEquals("7E1", full.first().requestAddress)
        assertEquals("TCM confirmado", full.first().moduleName)
        assertTrue(full.first().requiredForCompleteness)
        assertFalse(full.single { it.requestAddress == "7E0" }.requiredForCompleteness)
        assertEquals(listOf("7E1"), quick.map { it.requestAddress })
    }

    @Test
    fun coincidentalServiceBytesNeverCreateFindingsAcrossVariedPayloads() {
        val markerBytes = listOf(0x59, 0x7F)
        repeat(128) { seed ->
            val payload = List(7) { index ->
                when (index) {
                    0 -> 0x62
                    1 -> 0xF1
                    2 -> markerBytes[seed % markerBytes.size]
                    else -> (seed * 37 + index * 19) and 0xFF
                }
            }.joinToString(" ") { "%02X".format(it) }
            val records = DtcScanEngine.parseUdsService19ByEcu(
                rawResponse = "7E8 07 $payload",
                targetAddress = "7E0",
                moduleName = "ECM",
            )
            assertTrue("seed=$seed payload=$payload", records.isEmpty())
        }
    }
}
