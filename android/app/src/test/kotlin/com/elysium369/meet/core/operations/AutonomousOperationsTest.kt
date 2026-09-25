package com.elysium369.meet.core.operations

import com.elysium369.meet.core.finance.Money
import com.elysium369.meet.core.owner.domain.DataFreshness
import com.elysium369.meet.core.owner.domain.OwnerCommandCenterSnapshot
import com.elysium369.meet.core.owner.domain.OwnerMobilitySnapshot
import com.elysium369.meet.core.owner.domain.OwnerMoneySnapshot
import com.elysium369.meet.core.owner.domain.OwnerSystemHealthSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Enforces Master Order Omega §53–§69, §95:
 * Tests for the Elysium Autonomous Operations OS:
 * 1. Incident correlation (5,000 downstream errors coalesce into 1 incident record)
 * 2. SRE Poison Pill / DLQ escalation to P0 REQUIRES_OWNER
 * 3. Finance ledger mismatch detection and preventative HOLD
 * 4. Conversational Owner Agent responses to "¿Qué necesita mi atención?"
 */
class AutonomousOperationsTest {

    private lateinit var engine: AutonomousOperationsEngine

    @Before
    fun setUp() {
        engine = AutonomousOperationsEngine()
    }

    @Test
    fun incidentCorrelator_deduplicates5000AlertsIntoSingleIncident() {
        // Simulate a burst of 5,000 failed database queries from upstream outage (§54)
        for (i in 1..5000) {
            engine.ingestEvent(
                domain = "SRE",
                correlationKey = "db_pool_timeout_signature",
                title = "Timeout en conexión a base de datos",
                severity = CaseSeverity.P0,
                autoRemediate = true,
            )
        }

        // Must produce exactly 1 incident record with eventCount = 5000
        assertEquals(1, engine.totalIncidentsCount)
        val incident = engine.ingestEvent("SRE", "db_pool_timeout_signature", "Timeout", CaseSeverity.P0)
        assertEquals(5001, incident.eventCount)
        assertTrue(incident.isAutoRemediated)
    }

    @Test
    fun sreOperationsAgent_escalatesDeadLetterQueueToOwnerP0() {
        // DLQ event detected with poison pills
        val sreCase = engine.evaluateSreHealth(
            outboxLagSeconds = 15L,
            deadLetterCount = 3,
            apiAvailabilityPercent = 99.95,
        )

        assertNotNull(sreCase)
        assertEquals(CaseSeverity.P0, sreCase!!.severity)
        assertEquals(CaseState.REQUIRES_OWNER, sreCase.state)
        assertTrue(sreCase.requiresHumanAttention)
        assertTrue(sreCase.whatHappened.contains("3 mensajes"))
        assertTrue(sreCase.evidenceSummary.contains("status = 'DEAD_LETTER'"))
    }

    @Test
    fun financeOperationsAgent_holdsMismatchedMoneyWithZeroInference() {
        val exposure = Money.ofCrc(75_000L)
        val financeCase = engine.evaluateFinancialIntegrity(
            mismatchedTransactionsCount = 2,
            unbalancedMoneyExposure = exposure,
        )

        assertNotNull(financeCase)
        assertEquals(CaseSeverity.P0, financeCase!!.severity)
        assertEquals(CaseState.REQUIRES_OWNER, financeCase.state)
        assertEquals(exposure, financeCase.moneyExposure)
        assertTrue(financeCase.whatAutomationDid.contains("HOLD financiero preventivo"))
    }

    @Test
    fun conversationalOwnerAgent_answersActionableQuestionsAccurately() {
        // 1. When operating completely cleanly
        val cleanAnswer = engine.answerOwnerQuery("¿Qué necesita mi atención?", null)
        assertTrue(cleanAnswer.contains("Elysium opera con total normalidad autónoma"))

        // 2. Introduce an SRE DLQ emergency
        engine.evaluateSreHealth(outboxLagSeconds = 10L, deadLetterCount = 1, apiAvailabilityPercent = 99.9)

        val alertAnswer = engine.answerOwnerQuery("¿Qué necesita mi atención?", null)
        assertTrue(alertAnswer.contains("Tienes 1 caso(s) prioritario(s)"))
        assertTrue(alertAnswer.contains("Dead-Letter Queue"))

        // 3. Financial inquiry
        val noMoneyProblem = engine.answerOwnerQuery("¿Hay dinero descuadrado?", null)
        assertTrue(noMoneyProblem.contains("Libro mayor 100% conciliado"))

        engine.evaluateFinancialIntegrity(1, Money.ofCrc(12_500L))
        val moneyProblem = engine.answerOwnerQuery("¿Hay dinero descuadrado?", null)
        assertTrue(moneyProblem.contains("ALERTA: Se detectaron 1 discrepancia(s)"))
        assertTrue(moneyProblem.contains("12500"))

        // 4. System health with authoritative snapshot
        val snapshot = OwnerCommandCenterSnapshot(
            freshness = DataFreshness.LIVE,
            money = OwnerMoneySnapshot(Money.zero(), Money.zero(), Money.zero()),
            mobility = OwnerMobilitySnapshot(activeUsers = 450, activeProviders = 32, completedTrips = 180),
            systemHealth = OwnerSystemHealthSnapshot(apiAvailabilityPercent = 99.99, p95LatencyMs = 85L, outboxLagSeconds = 2L, deadLetterCount = 0, workersHealthy = true),
            servicesCompleted = 12,
            towCallsCompleted = 4,
        )
        val healthAnswer = engine.answerOwnerQuery("¿Cómo está el sistema?", snapshot)
        assertTrue(healthAnswer.contains("Disponibilidad API 99.99%"))
        assertTrue(healthAnswer.contains("Usuarios activos 450"))
    }

    @Test
    fun ownerDecision_approvesAndClosesCase() {
        val sreCase = engine.evaluateSreHealth(10L, 2, 99.9)!!
        val resolved = engine.resolveCaseByOwner(sreCase.id, approved = true, reason = "Reinyectado tras parche de deserialización")

        assertNotNull(resolved)
        assertEquals(CaseState.OWNER_APPROVED, resolved!!.state)
        assertEquals("Reinyectado tras parche de deserialización", resolved.resolutionReason)
        assertNotNull(resolved.resolvedAtEpochMs)

        // Must no longer require human attention
        assertTrue(engine.listCasesRequiringOwner().isEmpty())
    }
}
