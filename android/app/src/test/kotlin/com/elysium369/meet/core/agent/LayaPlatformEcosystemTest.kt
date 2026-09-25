package com.elysium369.meet.core.agent

import com.elysium369.meet.core.agent.laya.EcuConnectionLayaEngine
import com.elysium369.meet.core.agent.laya.EmissionsAuditInput
import com.elysium369.meet.core.agent.laya.EmissionsLayaEvaluator
import com.elysium369.meet.core.agent.laya.LayaDecisionEngine
import com.elysium369.meet.core.agent.laya.QuoteAntiFraudLayaEvaluator
import com.elysium369.meet.core.agent.laya.QuotePartItem
import com.elysium369.meet.core.agent.laya.RepairQuoteInput
import com.elysium369.meet.core.agent.laya.RideAnomalyLayaEvaluator
import com.elysium369.meet.core.agent.laya.RideTelemetrySnapshot
import com.elysium369.meet.core.agent.laya.VehicleNegotiationContext
import com.elysium369.meet.core.obd.AdapterCompatibilityTier
import com.elysium369.meet.core.obd.AdaptiveProtocolNegotiatorV2
import com.elysium369.meet.core.obd.DiagnosticProbeSpeed
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Master test suite for Laya AI Platform-Wide Integration:
 * 1. ECU Connection & Protocol Strategy Engine
 * 2. Emissions & Pre-ITV / Dekra In-Depth Evaluator
 * 3. Quote & Parts Anti-Fraud Evaluator
 * 4. Mobility & Ride Anomaly Evaluator
 * 5. ElysiumAgentBus Semantic Laya Intent Routing
 */
class LayaPlatformEcosystemTest {

    private lateinit var decisionEngine: LayaDecisionEngine
    private lateinit var ecuLayaEngine: EcuConnectionLayaEngine
    private lateinit var emissionsEvaluator: EmissionsLayaEvaluator
    private lateinit var antiFraudEvaluator: QuoteAntiFraudLayaEvaluator
    private lateinit var rideAnomalyEvaluator: RideAnomalyLayaEvaluator
    private lateinit var agentBus: ElysiumAgentBus

    @Before
    fun setUp() {
        decisionEngine = LayaDecisionEngine()
        ecuLayaEngine = EcuConnectionLayaEngine(decisionEngine)
        emissionsEvaluator = EmissionsLayaEvaluator(decisionEngine)
        antiFraudEvaluator = QuoteAntiFraudLayaEvaluator(decisionEngine)
        rideAnomalyEvaluator = RideAnomalyLayaEvaluator(decisionEngine)
        agentBus = ElysiumAgentBus(decisionEngine)
    }

    // ─── 1. ECU CONNECTION & PROTOCOL STRATEGY TESTS ───

    @Test
    fun ecuStrategy_toyotaModern_infersCan11BitAndBroadcastHeader() {
        val context = VehicleNegotiationContext(
            make = "TOYOTA",
            model = "COROLLA",
            year = 2015,
            vin = "2T1BURHE8FC123456",
        )

        val strategy = ecuLayaEngine.evaluateStrategy("ELM327 v1.5", context)

        assertEquals("ISO_15765_4_CAN_11BIT_500K", strategy.preferredProtocolId)
        assertEquals("7DF", strategy.targetEcuHeader)
        assertTrue(strategy.enableAdaptiveTiming)
        assertTrue(strategy.confidence >= 0.85)
    }

    @Test
    fun ecuStrategy_fordLegacy_infersJ1850Pwm() {
        val context = VehicleNegotiationContext(
            make = "FORD",
            model = "RANGER",
            year = 2002,
        )

        val strategy = ecuLayaEngine.evaluateStrategy("OBDLink MX+", context)

        assertEquals("SAE_J1850_PWM", strategy.preferredProtocolId)
        assertEquals(0L, strategy.recommendedInterCommandDelayMs) // Genuine STN/OBDLink
    }

    @Test
    fun ecuStrategy_gmLegacy_infersJ1850Vpw() {
        val context = VehicleNegotiationContext(
            make = "CHEVROLET",
            model = "SILVERADO",
            year = 2001,
        )

        val strategy = ecuLayaEngine.evaluateStrategy("ELM327 v1.5", context)

        assertEquals("SAE_J1850_VPW", strategy.preferredProtocolId)
    }

    @Test
    fun ecuStrategy_cloneAdapter_setsProtectionBypassAndDelay() {
        val context = VehicleNegotiationContext(
            make = "HYUNDAI",
            model = "ELANTRA",
            year = 2011,
        )

        val strategy = ecuLayaEngine.evaluateStrategy("ELM327 v2.1", context)

        assertTrue("Must bypass auto-search to avoid clone hang", strategy.bypassAutoSearch)
        assertEquals(50L, strategy.recommendedInterCommandDelayMs)
        assertFalse("Adaptive timing must be disabled on v2.1 clones", strategy.enableAdaptiveTiming)
        assertEquals(DiagnosticProbeSpeed.STABILITY_FIRST_PROBE, strategy.probeSpeed)
    }

    @Test
    fun adaptiveProtocolNegotiator_compilePlanWithLaya_preservesFallbacks() {
        val context = VehicleNegotiationContext(
            make = "NISSAN",
            model = "SENTRA",
            year = 2018,
        )

        val plan = AdaptiveProtocolNegotiatorV2.compilePlanWithLaya(
            adapterVersionString = "STN2120 v5.1.0",
            context = context,
            layaEngine = ecuLayaEngine
        )

        assertEquals(AdapterCompatibilityTier.GENUINE_STN, plan.adapterCompatibilityTier)
        assertEquals("ISO_15765_4_CAN_11BIT_500K", plan.preferredCandidate.protocolId)
        assertTrue(plan.fallbackCandidates.isNotEmpty())
        assertEquals(0L, plan.interCommandDelayMs)
    }

    // ─── 2. EMISSIONS & PRE-ITV / DEKRA EVALUATOR TESTS ───

    @Test
    fun emissionsEvaluator_cleanVehicle_passesDekraWithHighProbability() {
        val input = EmissionsAuditInput(
            vehicleYear = 2016,
            fuelType = "GASOLINE",
            lambda = 1.005,
            coPercent = 0.12,
            hcPpm = 45,
            smokeColor = "NONE",
            activeDtcs = emptyList(),
            incompleteMonitorsCount = 0,
            catalyticEfficiencyScore = 0.92,
        )

        val result = emissionsEvaluator.evaluate(input)

        assertTrue(result.willPassDekra)
        assertTrue("Approval prob must be high", result.approvalProbability >= 0.90)
        assertEquals(1, result.riskScore)
        assertEquals("IN_RANGE", result.lambdaStatus)
        assertTrue(result.rejectionReasons.isEmpty())
    }

    @Test
    fun emissionsEvaluator_outOfRangeLambdaAndBlueSmoke_failsInspection() {
        val input = EmissionsAuditInput(
            vehicleYear = 2008,
            fuelType = "GASOLINE",
            lambda = 0.940, // Rich < 0.970
            coPercent = 0.85, // Exceeds 0.5%
            hcPpm = 180,    // Exceeds 100ppm
            smokeColor = "BLUE",
            activeDtcs = listOf("P0420"),
            incompleteMonitorsCount = 2,
        )

        val result = emissionsEvaluator.evaluate(input)

        assertFalse(result.willPassDekra)
        assertTrue("Approval prob must be low", result.approvalProbability <= 0.40)
        assertTrue("Risk score must be high", result.riskScore >= 4)
        assertEquals("RICH", result.lambdaStatus)
        assertTrue(result.rejectionReasons.any { it.contains("humo azul") })
        assertTrue(result.rejectionReasons.any { it.contains("0.94") })
    }

    // ─── 3. QUOTE & PARTS ANTI-FRAUD EVALUATOR TESTS ───

    @Test
    fun quoteAntiFraud_honestQuote_verifiesFairness() {
        val input = RepairQuoteInput(
            quoteId = "quote_001",
            vehicleSummary = "Toyota Yaris 2015",
            reportedIssue = "Reemplazo de pastillas de frenos delanteras",
            laborHours = 1.0,
            laborRatePerHourCrc = 20000.0,
            parts = listOf(
                QuotePartItem(
                    partName = "Pastillas de freno cerámicas",
                    oemNumber = "04465-0D150",
                    unitPriceCrc = 28000.0,
                    quantity = 1,
                    declaredCompatibility = "EXACT"
                )
            ),
            diagnosedDtcs = emptyList()
        )

        val result = antiFraudEvaluator.auditQuote(input)

        assertTrue(result.isQuoteFair)
        assertEquals(1, result.fraudSuspicionScore)
        assertEquals("EXACT", result.compatibilityRating)
        assertEquals("REASONABLE", result.laborHoursAssessment)
        assertTrue(result.fraudFlags.isEmpty())
    }

    @Test
    fun quoteAntiFraud_inflatedHoursAndOverReplacement_flagsFraud() {
        val input = RepairQuoteInput(
            quoteId = "quote_fraud_002",
            vehicleSummary = "Nissan Tiida 2011",
            reportedIssue = "Falla de sensor de oxigeno P0135",
            laborHours = 5.5, // Standard is ~0.7h
            laborRatePerHourCrc = 52000.0, // Inflated > 45000
            parts = listOf(
                QuotePartItem(
                    partName = "Catalizador nuevo universal",
                    oemNumber = null, // No OEM number!
                    unitPriceCrc = 250000.0,
                    declaredCompatibility = "EXACT" // Violates Rule 2
                )
            ),
            diagnosedDtcs = listOf("P0135")
        )

        val result = antiFraudEvaluator.auditQuote(input)

        assertFalse(result.isQuoteFair)
        assertTrue("Fraud suspicion must be high", result.fraudSuspicionScore >= 3)
        assertEquals("INFLATED", result.laborHoursAssessment)
        assertEquals("PROBABLE_NEEDS_VERIFICATION", result.compatibilityRating)
        assertTrue(result.fraudFlags.any { it.contains("SOBRE-REEMPLAZO") })
        assertTrue(result.fraudFlags.any { it.contains("sin número OEM") })
    }

    // ─── 4. MOBILITY & RIDE ANOMALY EVALUATOR TESTS ───

    @Test
    fun rideAnomaly_normalRide_evaluatesNormal() {
        val snapshot = RideTelemetrySnapshot(
            rideId = "ride_norm_100",
            deviationFromRouteMeters = 30.0,
            stationaryDurationSeconds = 20L,
            currentSpeedKmh = 42.0,
            isLateNight = false,
            isUnpopulatedArea = false,
        )

        val result = rideAnomalyEvaluator.evaluate(snapshot)

        assertFalse(result.isAnomalyDetected)
        assertEquals(1, result.riskScore)
        assertEquals("MONITOR_NORMAL", result.recommendedAction)
    }

    @Test
    fun rideAnomaly_severeDeviationLateNightAndDistress_escalatesGuardian() {
        val snapshot = RideTelemetrySnapshot(
            rideId = "ride_risk_911",
            deviationFromRouteMeters = 1500.0,
            stationaryDurationSeconds = 300L,
            currentSpeedKmh = 0.0,
            isLateNight = true,
            isUnpopulatedArea = true,
            passengerReportedConcern = "¡Auxilio, el chofer se desvió por un callejón oscuro y se detuvo!",
        )

        val result = rideAnomalyEvaluator.evaluate(snapshot)

        assertTrue(result.isAnomalyDetected)
        assertEquals(5, result.riskScore)
        assertEquals("ESCALATE_GUARDIAN_911", result.recommendedAction)
        assertTrue(result.explanation.contains("crítica"))
    }

    // ─── 5. AGENT BUS SEMANTIC LAYA ROUTING TESTS ───

    @Test
    fun agentBus_routesSemanticNaturalLanguageViaLaya() = runBlocking {
        // Register an automotive mechanic agent with specific pattern
        val autoAgent = AgentRegistration(
            agentId = AgentId("agent_mechanic_pro"),
            domain = AgentDomain.AUTOMOTIVE,
            capabilities = listOf(
                AgentCapability(
                    intentPattern = "auto.diagnose_engine_trouble",
                    description = "Diagnóstico integral de fallas y códigos de motor"
                )
            ),
            priority = 10
        )
        agentBus.registerAgent(autoAgent)

        // Request with natural language phrasing that does NOT match exact or regex
        val request = AgentRequest(
            messageId = "msg_user_help",
            intent = "el motor me tiembla y tira humo blanco por el escape",
        )

        val match = agentBus.routeWithMatch(request)

        assertNotNull(match)
        assertEquals("agent_mechanic_pro", match!!.agent.agentId.value)
        assertEquals("auto.diagnose_engine_trouble", match.matchedCapability.intentPattern)
        assertEquals("LAYA_SYSTEM_ONE_SEMANTIC", match.matchType)
        assertTrue(match.score >= 0.50)
    }
}
