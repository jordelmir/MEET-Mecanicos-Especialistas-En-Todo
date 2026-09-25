package com.elysium369.meet.core.agent

import com.elysium369.meet.core.agent.context.AgentContextSnapshot
import com.elysium369.meet.core.agent.domain.AgentInput
import com.elysium369.meet.core.agent.intent.LayaIntentResolver
import com.elysium369.meet.core.agent.laya.ActionType
import com.elysium369.meet.core.agent.laya.LayaAssistantEngine
import com.elysium369.meet.core.agent.laya.LayaDecisionEngine
import com.elysium369.meet.core.agent.laya.LayaQuestion
import com.elysium369.meet.core.agent.laya.RideAssistantContext
import com.elysium369.meet.core.agent.laya.VehicleAssistantContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying Laya AI integration, System 1 non-autoregressive decision model,
 * and the EVAIR Conversational Assistant Engine across Mobility, Automotive, Emissions, and Safety.
 */
class LayaAssistantTest {

    private lateinit var decisionEngine: LayaDecisionEngine
    private lateinit var assistantEngine: LayaAssistantEngine
    private lateinit var intentResolver: LayaIntentResolver

    @Before
    fun setUp() {
        decisionEngine = LayaDecisionEngine()
        assistantEngine = LayaAssistantEngine(decisionEngine)
        intentResolver = LayaIntentResolver(decisionEngine)
    }

    @Test
    fun layaDecisionEngine_evaluatesChoiceScoreAndNoulCorrectly() = runBlocking {
        val state = "Choque grave en la autopista, necesito auxilio urgente 911"
        val questions = listOf(
            LayaQuestion.Choice(name = "domain", options = listOf("mobility", "safety", "automotive")),
            LayaQuestion.Noul(name = "is_emergency"),
            LayaQuestion.Score(name = "urgency", levels = 5),
        )

        val batch = decisionEngine.evaluate(state, questions)

        val domain = batch.choice("domain")
        assertNotNull(domain)
        assertEquals("safety", domain!!.value)
        assertTrue(domain.confidence > 0.5)

        val isEmergency = batch.noul("is_emergency")
        assertNotNull(isEmergency)
        assertTrue("Must detect emergency in state", isEmergency!!.value)
        assertTrue(isEmergency.pTrue >= 0.8)

        val urgency = batch.score("urgency")
        assertNotNull(urgency)
        assertTrue("Urgency level must be high (4 or 5)", urgency!!.level >= 4)
    }

    @Test
    fun assistantEngine_mobilityEtaInquiry_injectsLiveDriverData() = runBlocking {
        val ride = RideAssistantContext(
            rideId = "ride_778899",
            driverName = "Carlos Mora",
            driverPlate = "ABC-456",
            driverVehicle = "Hyundai Accent Azul",
            state = "ARRIVING",
            etaMinutes = 3,
            pickupAddress = "San José Centro",
            dropoffAddress = "Multiplaza Escazú",
            fareFormatted = "₡4.200 CRC",
        )

        val response = assistantEngine.ask("¿Por dónde viene el chofer y cuánto falta?", rideContext = ride)

        assertEquals("mobility", response.domain)
        assertEquals("ride.eta_status", response.intent)
        assertFalse(response.isEmergency)

        // Verifies truth and live context injection
        assertTrue("Must mention driver name", response.text.contains("Carlos Mora"))
        assertTrue("Must mention plate", response.text.contains("ABC-456"))
        assertTrue("Must mention ETA minutes", response.text.contains("3 minutos"))

        // Verifies actionable shortcuts
        val actionTypes = response.suggestedActions.map { it.type }
        assertTrue(actionTypes.contains(ActionType.CALL_DRIVER))
        assertTrue(actionTypes.contains(ActionType.MESSAGE_DRIVER))
    }

    @Test
    fun assistantEngine_sinpeMovilInquiry_explainsPaymentMethod() = runBlocking {
        val ride = RideAssistantContext(
            rideId = "ride_123",
            fareFormatted = "₡3.500 CRC",
        )

        val response = assistantEngine.ask("¿Puedo pagar con Sinpe Móvil?", rideContext = ride)

        assertEquals("mobility", response.domain)
        assertEquals("ride.payment_method", response.intent)
        assertTrue(response.text.contains("SINPE Móvil"))
        assertTrue(response.text.contains("₡3.500 CRC"))
        assertTrue("Must advise keeping comprobante", response.text.contains("comprobante"))
    }

    @Test
    fun assistantEngine_petPolicy_returnsPetFriendlyGuidance() = runBlocking {
        val response = assistantEngine.ask("¿Puedo viajar con mi perro o gato?")

        assertEquals("mobility", response.domain)
        assertEquals("ride.pet_policy", response.intent)
        assertTrue(response.text.contains("mascotas"))
        assertTrue(response.text.contains("kennel"))
    }

    @Test
    fun assistantEngine_checkEngineBlinking_givesVitalSafetyWarning() = runBlocking {
        val response = assistantEngine.ask("¿Qué pasa si la luz de check engine parpadea?")

        assertEquals("automotive", response.domain)
        assertEquals("auto.check_engine", response.intent)
        assertTrue(response.text.contains("PARPADEANDO"))
        assertTrue("Must warn about catalytic converter destruction", response.text.contains("catalítico"))
    }

    @Test
    fun assistantEngine_dtcExplanationP0420_givesTechnicalInsight() = runBlocking {
        val vehicle = VehicleAssistantContext(
            vehicleName = "Nissan Tiida 2011",
            activeDtcs = listOf("P0420"),
            isObdConnected = true,
        )

        val response = assistantEngine.ask("Tengo el código P0420 en el escáner", vehicleContext = vehicle)

        assertEquals("automotive", response.domain)
        assertEquals("auto.dtc_explanation", response.intent)
        assertTrue(response.text.contains("catalizador"))
    }

    @Test
    fun assistantEngine_dekraEmissionsRules_explainsLambdaLimits() = runBlocking {
        val response = assistantEngine.ask("¿Qué me van a revisar de gases en Dekra o RTV?")

        assertEquals("emissions", response.domain)
        assertEquals("emissions.dekra_rules", response.intent)
        assertTrue(response.text.contains("Lambda"))
        assertTrue(response.text.contains("0.97 y 1.03"))
    }

    @Test
    fun assistantEngine_emergency911_activatesGuardianProtocol() = runBlocking {
        val response = assistantEngine.ask("¡Auxilio emergencia choque en la pista 911!")

        assertTrue("Must be classified as emergency", response.isEmergency)
        assertTrue(response.text.contains("PROTOCOLO DE EMERGENCIA"))
        assertTrue(response.text.contains("911"))

        val actionTypes = response.suggestedActions.map { it.type }
        assertTrue(actionTypes.contains(ActionType.SAFETY_CENTER))
        assertTrue(actionTypes.contains(ActionType.SHARE_LOCATION))
    }

    @Test
    fun layaIntentResolver_resolvesNaturalLanguageToTypedCapability() = runBlocking {
        val input = AgentInput("Escanear motor y leer fallas DTC del vehículo")
        val context = AgentContextSnapshot(
            principalId = "user_1",
            currentScreen = "scanner",
            contextGeneration = 1L,
        )

        val resolution = intentResolver.resolve(input, context)

        assertNotNull(resolution.primaryIntent)
        assertEquals("vehicle.read_dtc", resolution.primaryIntent!!.name)
        assertEquals("vehicle", resolution.primaryIntent!!.targetDomain)
        assertTrue(resolution.primaryIntent!!.calibratedConfidence > 0.4)
    }
}
