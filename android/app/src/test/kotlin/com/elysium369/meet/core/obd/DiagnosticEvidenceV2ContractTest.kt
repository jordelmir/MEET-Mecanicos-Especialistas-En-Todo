package com.elysium369.meet.core.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Executable contract for the v2 evidence rules. Not a hardware-conformance substitute. */
class DiagnosticEvidenceV2ContractTest {

    @Test
    fun absenceRequiresCompleteSemanticCoverageNotOverlap() {
        val coverage = DiagnosticCoverage.udsForStatusMask(0x09)

        assertTrue(coverage.fullyCovers(setOf(DiagnosticSemantic.UDS_TEST_FAILED)))
        assertFalse(
            coverage.fullyCovers(
                setOf(DiagnosticSemantic.UDS_TEST_FAILED, DiagnosticSemantic.UDS_PENDING),
            ),
        )
    }

    @Test
    fun udsIdentityPreservesFullTwentyFourBitsAndFailureType() {
        val record = DtcScanEngine.parseUdsService19ByEcu(
            rawResponse = "7E8 59 02 FF 12 34 A5 09",
            targetAddress = "7E0",
            moduleName = "ECM",
        ).single()

        assertEquals(0x1234A5, record.rawDtc24)
        assertEquals("1234A5", record.codeIdentity.stableRawIdentity)
        assertEquals(0xA5, record.codeIdentity.failureType)
        assertEquals(DtcFormat.ISO_14229_3_BYTE, record.dtcFormat)
    }

    @Test
    fun adapterOkIsNeverDecodedAsEcuClearAcceptance() {
        val responses = DiagnosticPduDecoder.decodeResponses(
            rawResponse = "OK",
            expectedPositiveService = 0x54,
            requestedService = 0x14,
        )

        assertTrue(responses.isEmpty())
    }

    @Test
    fun securityNrcCreatesCapabilityBarrierInsteadOfBlindRetry() {
        val response = NegativeDiagnosticResponse.from(0x19, 0x33)

        assertTrue(UdsNegativeResponsePolicy.actionFor(response, attempt = 1) is UdsNrcAction.CapabilityBarrier)
    }

    @Test
    fun partialClearNeverResolvesOtherEcuOrUncoveredAbs() {
        fun target(id: String, module: String, raw: String) = ClearVerificationTarget(
            findingId = id,
            vehicleId = "vehicle-1",
            findingKey = DiagnosticFindingKey(
                vehicleId = "vehicle-1",
                namespace = DiagnosticNamespace.UDS,
                moduleIdentity = module,
                rawDtcIdentity = raw,
                displayCode = "P0606",
            ),
            requiredSemantics = setOf(DiagnosticSemantic.UDS_CONFIRMED),
            sourceService = "1902FF",
        )
        val ecmTarget = target("finding-ecm", "7E0", "060601")
        val tcmTarget = target("finding-tcm", "7E1", "060602")
        val absTarget = target("finding-abs", "760", "060603")
        val confirmedCoverage = DiagnosticCoverage.udsForStatusMask(0x08)
        val postReport = DtcScanReport(
            startedAtMs = 1,
            endedAtMs = 2,
            protocol = "ISO15765-CAN",
            records = listOf(
                DtcRecord(
                    code = "P0606",
                    bucket = DtcBucket.HISTORY,
                    statusFlags = setOf(DtcStatusFlag.CONFIRMED),
                    sourceService = "1902FF",
                    namespace = DiagnosticNamespace.UDS,
                    targetAddress = "7E1",
                    responseAddress = "7E9",
                    moduleName = "TCM",
                    rawPayload = "7E9 59 02 FF 06 06 02 08",
                    udsStatusByte = 0x08,
                    udsFailureType = "02",
                    rawDtc24 = 0x060602,
                ),
            ),
            modules = listOf(
                DtcModuleReport(
                    targetAddress = "7E0", responseAddress = "7E8", moduleName = "ECM",
                    isAlive = true, dtcs = emptyList(), rawExchanges = emptyList(),
                    serviceReads = listOf(DtcServiceRead("1902FF", confirmedCoverage, ModuleScanOutcome.NO_DTC)),
                    outcome = ModuleScanOutcome.NO_DTC,
                ),
                DtcModuleReport(
                    targetAddress = "7E1", responseAddress = "7E9", moduleName = "TCM",
                    isAlive = true, dtcs = emptyList(), rawExchanges = emptyList(),
                    serviceReads = listOf(DtcServiceRead("1902FF", confirmedCoverage, ModuleScanOutcome.COMPLETE)),
                    outcome = ModuleScanOutcome.COMPLETE,
                ),
                DtcModuleReport(
                    targetAddress = "760", responseAddress = "768", moduleName = "ABS",
                    isAlive = false, dtcs = emptyList(), rawExchanges = emptyList(),
                    serviceReads = listOf(DtcServiceRead("1902FF", confirmedCoverage, ModuleScanOutcome.NO_RESPONSE)),
                    outcome = ModuleScanOutcome.NO_RESPONSE,
                ),
            ),
            rawExchanges = emptyList(),
        )
        val ecmEndpoint = EcuEndpoint(
            busId = "CAN0",
            networkType = DiagnosticTransport.CAN,
            addressingMode = DiagnosticAddressingMode.PHYSICAL,
            requestAddress = "7E0",
            responseAddress = "7E8",
            moduleRole = "ECM",
            discoveryProvenance = "TEST_FIXTURE",
        )
        val evaluation = ClearVerificationEvaluator.evaluate(
            plan = ClearVerificationPlan(0, listOf(ecmTarget, tcmTarget, absTarget), null),
            postClearReport = postReport,
            commandEvidence = listOf(
                ClearCommandEvidence(
                    protocol = DiagnosticApplicationProtocol.UDS,
                    requestScope = DiagnosticRequestScope.Physical(ecmEndpoint),
                    command = "14FFFFFF",
                    rawResponse = "7E8 01 54",
                    positiveService = 0x54,
                    acceptedByEcu = true,
                    adapterAcknowledged = false,
                ),
            ),
        )

        assertEquals(setOf("finding-ecm"), evaluation.verifiedFindingIds)
        assertEquals(setOf("finding-tcm", "finding-abs"), evaluation.unverifiedFindingIds)
    }
}
