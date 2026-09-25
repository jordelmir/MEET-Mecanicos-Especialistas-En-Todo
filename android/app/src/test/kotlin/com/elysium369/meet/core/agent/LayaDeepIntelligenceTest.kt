package com.elysium369.meet.core.agent

import com.elysium369.meet.core.agent.laya.PhysicsAnomalyEngine
import com.elysium369.meet.core.agent.laya.TinyEmbeddingIndex
import com.elysium369.meet.core.agent.laya.VehicleEpisodicMemory
import com.elysium369.meet.core.agent.laya.VehicleMemoryEntry
import com.elysium369.meet.core.agent.laya.VehiclePhysicsStream
import com.elysium369.meet.core.agent.laya.VoiceLayaBridge
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * World-Class Deep Intelligence Test Suite for MEET's Local-First AI Architecture:
 * 1. TinyEmbeddingIndex (Offline quantized semantic search)
 * 2. PhysicsAnomalyEngine (Crash detection, mechanical abuse ESI, pre-DTC forecasting)
 * 3. VoiceLayaBridge (Hands-free vocal copilot synthesis)
 * 4. VehicleEpisodicMemory (Forensic repair diary & duplicate charge fraud protection)
 */
class LayaDeepIntelligenceTest {

    private lateinit var embeddingIndex: TinyEmbeddingIndex
    private lateinit var physicsEngine: PhysicsAnomalyEngine
    private lateinit var voiceBridge: VoiceLayaBridge
    private lateinit var episodicMemory: VehicleEpisodicMemory

    @Before
    fun setUp() {
        embeddingIndex = TinyEmbeddingIndex()
        physicsEngine = PhysicsAnomalyEngine()
        voiceBridge = VoiceLayaBridge(embeddingIndex = embeddingIndex)
        episodicMemory = VehicleEpisodicMemory()
    }

    // ─── 1. TINY EMBEDDING INDEX TESTS ───

    @Test
    fun tinyEmbeddingIndex_semanticSearchFindsRelevantDtc() {
        val results = embeddingIndex.search("mi carro tiembla y parece que falla una bujia o bobina", topK = 1)

        assertTrue("Must return at least 1 result", results.isNotEmpty())
        val top = results.first()
        assertEquals("dtc_p0300", top.document.id)
        assertTrue("Similarity must be positive", top.similarityScore > 0.30)
        assertTrue("Search must execute in < 15ms", top.latencyMs < 15L)
    }

    @Test
    fun tinyEmbeddingIndex_searchesCostaRicaTrafficLaw() {
        val results = embeddingIndex.search("multa de transito por no tener rtv o dekra al dia", topK = 1)

        assertTrue(results.isNotEmpty())
        assertEquals("ley_9078_art_143", results.first().document.id)
        assertTrue(results.first().document.content.contains("Ley 9078"))
    }

    // ─── 2. PHYSICS ANOMALY ENGINE TESTS ───

    @Test
    fun physicsEngine_detectsSevereKineticCollision() {
        val stream = VehiclePhysicsStream(
            rpm = 0f, // Engine stalled upon impact
            speedKmh = 65f, // Was driving fast
            coolantTempC = 88f,
            accelGForce = 4.2f, // Severe crash impact > 3.8G
        )

        val assessment = physicsEngine.evaluate(stream)

        assertTrue("Must detect severe collision", assessment.isSevereCollisionDetected)
        assertEquals(100, assessment.engineStressIndex)
        assertEquals("SEVERE_ABUSE", assessment.stressCategory)
        assertTrue(assessment.activeWarnings.any { it.contains("IMPACTO CINÉTICO") })
    }

    @Test
    fun physicsEngine_coldEngineAbuse_calculatesStressIndex() {
        val stream = VehiclePhysicsStream(
            rpm = 4200f, // Revving high
            speedKmh = 30f,
            coolantTempC = 42f, // Cold engine < 60°C
            stftPercent = 0.5f,
            ltftPercent = 1.0f,
            accelGForce = 1.0f,
        )

        val assessment = physicsEngine.evaluate(stream)

        assertFalse(assessment.isSevereCollisionDetected)
        assertTrue("Stress index must reflect abuse", assessment.engineStressIndex >= 40)
        assertTrue(assessment.activeWarnings.any { it.contains("Sobrerrevolución con motor frío") })
    }

    @Test
    fun physicsEngine_preDtcForecast_detectsLeanDriftBeforeMil() {
        val stream = VehiclePhysicsStream(
            rpm = 2100f,
            speedKmh = 80f,
            coolantTempC = 89f,
            stftPercent = 9.5f,
            ltftPercent = 11.5f, // STFT + LTFT = 21.0% (> 18% lean threshold)
            batteryVoltage = 14.1f,
        )

        val assessment = physicsEngine.evaluate(stream)

        assertTrue(assessment.preDtcPredictions.isNotEmpty())
        val p0171Prediction = assessment.preDtcPredictions.find { it.prospectiveDtc == "P0171" }
        assertNotNull("Must forecast prospective P0171", p0171Prediction)
        assertTrue(p0171Prediction!!.description.contains("Fuga de vacío"))
        assertTrue("Must estimate km before MIL illumination", p0171Prediction.estimatedKmUntilMil in 1..200)
    }

    @Test
    fun physicsEngine_preDtcForecast_detectsAlternatorFailure() {
        val stream = VehiclePhysicsStream(
            rpm = 850f, // Engine running at idle
            speedKmh = 0f,
            coolantTempC = 85f,
            batteryVoltage = 11.8f, // Failing alternator (< 12.8V with running engine)
        )

        val assessment = physicsEngine.evaluate(stream)

        val altPrediction = assessment.preDtcPredictions.find { it.prospectiveDtc == "P0562" }
        assertNotNull("Must forecast alternator under-voltage P0562", altPrediction)
        assertTrue(altPrediction!!.component.contains("Alternador"))
    }

    // ─── 3. VOICE LAYA BRIDGE TESTS ───

    @Test
    fun voiceLayaBridge_synthesizesVocalAdviceForDtc() = runBlocking {
        val response = voiceBridge.processVoiceQuery("¿Qué pasa si la luz de check engine parpadea?")

        assertFalse(response.isEmergency)
        assertEquals("automotive", response.domain)
        assertTrue("Voice response must be concise and actionable", response.spokenAnswerEs.isNotBlank())
        assertTrue("Must warn about catalytic converter destruction", response.spokenAnswerEs.contains("catalítico") || response.spokenAnswerEs.contains("PARPADEANDO"))
    }

    @Test
    fun voiceLayaBridge_synthesizesImmediateEmergencyProtocol() = runBlocking {
        val response = voiceBridge.processVoiceQuery("¡Auxilio choque emergencia 911!")

        assertTrue(response.isEmergency)
        assertTrue(response.spokenAnswerEs.contains("911"))
        assertTrue(response.spokenAnswerEs.contains("emergencia"))
    }

    // ─── 4. VEHICLE EPISODIC MEMORY TESTS ───

    @Test
    fun episodicMemory_detectsDuplicateRepairAttempt() {
        val vehicleId = "veh_toyota_corolla_2014"

        // Log past repair 2 months ago
        episodicMemory.recordEntry(
            VehicleMemoryEntry(
                entryId = "rep_01",
                vehicleId = vehicleId,
                timestamp = System.currentTimeMillis() - (60L * 86_400_000L),
                category = "REPAIR",
                summary = "Reemplazo de alternador nuevo OEM Denso",
                oemPartsMentioned = listOf("27060-0T020"),
            )
        )

        // Mechanic now proposes replacing alternator again!
        val fraudAlert = episodicMemory.checkDuplicateRepairAlert(vehicleId, "Alternador")

        assertNotNull("Must trigger duplicate repair fraud alert", fraudAlert)
        assertTrue(fraudAlert!!.contains("ALERTA ANTI-FRAUDE FORENSE"))
        assertTrue(fraudAlert.contains("27060-0T020") || fraudAlert.contains("alternador nuevo OEM"))
    }

    @Test
    fun episodicMemory_summarizesVehicleForensicHistory() {
        val vehicleId = "veh_nissan_tiida"

        episodicMemory.recordEntry(
            VehicleMemoryEntry(
                entryId = "dtc_1",
                vehicleId = vehicleId,
                timestamp = 1000L,
                category = "DTC_EVENT",
                summary = "Lectura de códigos",
                dtcsInvolved = listOf("P0420")
            )
        )
        episodicMemory.recordEntry(
            VehicleMemoryEntry(
                entryId = "dtc_2",
                vehicleId = vehicleId,
                timestamp = 2000L,
                category = "DTC_EVENT",
                summary = "Segunda lectura de códigos recurrente",
                dtcsInvolved = listOf("P0420")
            )
        )

        val summary = episodicMemory.getForensicSummary(vehicleId)

        assertEquals(vehicleId, summary.vehicleId)
        assertTrue("P0420 must be recognized as recurrent", summary.knownRecurrentDtcs.contains("P0420"))
    }
}
