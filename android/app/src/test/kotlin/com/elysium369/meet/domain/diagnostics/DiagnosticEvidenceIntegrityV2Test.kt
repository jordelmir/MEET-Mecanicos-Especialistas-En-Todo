package com.elysium369.meet.domain.diagnostics

import com.elysium369.meet.data.local.entities.DiagnosticExchangeEntity
import com.elysium369.meet.data.local.entities.DiagnosticObservationEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticEvidenceIntegrityV2Test {
    @Test
    fun wallClockRollbackDoesNotChangeFindingCausalOrder() {
        val firstDraft = observation(id = "a", observedAt = fourteenHundred, findingSequence = 1, previousHash = "")
        val first = firstDraft.copy(observationHash = DiagnosticEvidenceIntegrity.observationHash(firstDraft))
        val secondDraft = observation(
            id = "b",
            observedAt = thirteenHundred,
            findingSequence = 2,
            previousHash = first.observationHash,
        )
        val second = secondDraft.copy(observationHash = DiagnosticEvidenceIntegrity.observationHash(secondDraft))

        assertTrue(DiagnosticEvidenceIntegrity.verifyObservations(listOf(second, first)).valid)
    }

    @Test
    fun v2ExchangeCommitsProtocolAndLatencyMetadata() {
        val draft = exchange()
        val signed = draft.copy(exchangeHash = DiagnosticEvidenceIntegrity.exchangeHash(draft))
        assertFalse(
            signed.exchangeHash == DiagnosticEvidenceIntegrity.exchangeHash(
                signed.copy(applicationProtocol = "UDS", latencyMs = 999),
            ),
        )
    }

    private fun observation(
        id: String,
        observedAt: Long,
        findingSequence: Long,
        previousHash: String,
    ) = DiagnosticObservationEntity(
        id = id,
        findingId = "finding",
        sessionId = "session",
        observedAt = observedAt,
        observationState = "OBSERVED",
        semantics = "CONFIRMED",
        statusByte = null,
        sourceService = "19-02",
        exchangeId = null,
        rawPayloadHash = "raw",
        sessionSequence = findingSequence,
        elapsedRealtimeNanos = findingSequence * 10,
        previousObservationHash = previousHash,
        findingSequence = findingSequence,
        canonicalizationVersion = DiagnosticEvidenceIntegrity.OBSERVATION_CHAIN_V2,
    )

    private fun exchange() = DiagnosticExchangeEntity(
        id = "exchange",
        sessionId = "session",
        timestampMs = 100,
        transport = "CAN",
        applicationProtocol = "SAE_OBD",
        requestScope = "PHYSICAL",
        requestAddress = "7E0",
        responseAddress = "7E8",
        service = "03",
        rawRequest = "",
        rawResponse = "",
        decodedOutcome = "POSITIVE_RESPONSE",
        latencyMs = 12,
        retryCount = 0,
        negativeResponseCode = null,
        adapterConfiguration = "ISO15765-4",
        parserVersion = "1",
        sessionSequence = 1,
        elapsedRealtimeNanos = 10,
        rawRequestHash = "request-hash",
        rawResponseHash = "response-hash",
        previousExchangeHash = "",
        exchangeHash = "",
        canonicalizationVersion = DiagnosticEvidenceIntegrity.EXCHANGE_CHAIN_V2,
        rawPayloadBlobId = "blob",
    )

    private companion object {
        const val fourteenHundred = 14_00L
        const val thirteenHundred = 13_00L
    }
}
