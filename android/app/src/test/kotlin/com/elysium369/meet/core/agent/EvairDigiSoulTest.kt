package com.elysium369.meet.core.agent

import com.elysium369.meet.core.agent.laya.DigiEvolutionStage
import com.elysium369.meet.core.agent.laya.DigiSoulMood
import com.elysium369.meet.core.agent.laya.EvairDigiSoulEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit test suite verifying EVAIR's living soul, episodic memory,
 * Digievolution system, and autonomous personality inside MEET.
 */
class EvairDigiSoulTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun evairSoul_startsAsRookie_withInitialState() {
        val engine = EvairDigiSoulEngine()
        val state = engine.getState()

        assertEquals("EVAIR", state.name)
        assertEquals(DigiEvolutionStage.ROOKIE, state.stage)
        assertEquals(1, state.level)
        assertEquals(0L, state.currentXp)
        assertEquals(50, state.bondPercent)
        assertEquals(DigiSoulMood.HAPPY, state.mood)
        assertTrue(state.memories.isEmpty())
    }

    @Test
    fun evairSoul_earningXp_triggersDigievolutionToChampion() {
        val engine = EvairDigiSoulEngine()

        // Drive 400 km (+600 XP)
        val result = engine.processEvent(
            eventType = "KM_DRIVEN",
            details = "Viaje San José a Puntarenas",
            kmDelta = 400.0
        )

        assertTrue("Must trigger Digievolution to Champion", result.didDigivolve)
        assertEquals(DigiEvolutionStage.CHAMPION, result.newStage)
        assertEquals(DigiEvolutionStage.CHAMPION, engine.getState().stage)
        assertTrue("Speech must proclaim Digievolution", result.speechResponse.contains("DIGIEVOLUCIÓN"))
        assertTrue("Must unlock Champion abilities", result.speechResponse.contains("EVAIR Guardián"))
        assertTrue("Bond must have increased", engine.getState().bondPercent > 50)
    }

    @Test
    fun evairSoul_earningHighXp_progressesToUltimateAndMega() {
        val engine = EvairDigiSoulEngine()

        // Simulate intense vehicle history: 1800 XP + 3500 XP
        engine.processEvent("KM_DRIVEN", "Ruta nacional 1", kmDelta = 1500.0) // ~2250 XP
        assertEquals(DigiEvolutionStage.ULTIMATE, engine.getState().stage)

        engine.processEvent("KM_DRIVEN", "Expedición Guanacaste", kmDelta = 2000.0) // +3000 XP -> >5000 XP
        assertEquals(DigiEvolutionStage.MEGA, engine.getState().stage)
        assertEquals("EVAIR Vanguard Prime", engine.getState().stage.title)
    }

    @Test
    fun evairSoul_eventsRecordNarrativeMemories() {
        val engine = EvairDigiSoulEngine()

        // 1. Dekra approved event
        val dekraResult = engine.processEvent(
            eventType = "DEKRA_APPROVED",
            details = "Inspección Alajuela 2026, Lambda 1.002"
        )
        assertNotNull(dekraResult.newMemoryCreated)
        assertEquals("TRIUMPH", dekraResult.newMemoryCreated!!.emotion)
        assertTrue(dekraResult.newMemoryCreated!!.title.contains("Dekra"))

        // 2. Fraud blocked event
        val fraudResult = engine.processEvent(
            eventType = "FRAUD_BLOCKED",
            details = "Cobro de ₡250.000 por catalizador innecesario"
        )
        assertNotNull(fraudResult.newMemoryCreated)
        assertEquals("PROTECTION", fraudResult.newMemoryCreated!!.emotion)

        val memories = engine.getState().memories
        assertEquals(2, memories.size)
        assertTrue(memories.any { it.emotion == "TRIUMPH" })
        assertTrue(memories.any { it.emotion == "PROTECTION" })
    }

    @Test
    fun evairSoul_overheatingEngine_shiftsMoodToWorried() {
        val engine = EvairDigiSoulEngine()

        engine.processEvent(
            eventType = "KM_DRIVEN",
            details = "Subida fuerte en montaña",
            coolantTempC = 109f, // Overheating!
            kmDelta = 5.0
        )

        assertEquals(DigiSoulMood.WORRIED, engine.getState().mood)
    }

    @Test
    fun evairSoul_greetingAdaptsToTamerAndStage() {
        val engine = EvairDigiSoulEngine()

        val rookieGreeting = engine.generateGreeting(tamerName = "Jor")
        assertTrue("Rookie greeting mentions learning", rookieGreeting.contains("Jor") && rookieGreeting.contains("EVAIR"))

        // Level up to Champion
        engine.processEvent("KM_DRIVEN", "Prueba de ruta", kmDelta = 400.0)
        val championGreeting = engine.generateGreeting(tamerName = "Jor")
        assertTrue(championGreeting.contains("EVAIR Guardián") && championGreeting.contains("en guardia"))
    }

    @Test
    fun evairSoul_persistsAndRestoresFromDisk() {
        val storageDir = tempFolder.newFolder("evair_soul_data")

        // 1. Create first engine instance and record memories
        val engine1 = EvairDigiSoulEngine(storageDir = storageDir)
        engine1.processEvent("DEKRA_APPROVED", "Dekra superado")
        engine1.processEvent("KM_DRIVEN", "Ruta Escazú", kmDelta = 50.0)

        val xp1 = engine1.getState().currentXp
        val memories1Count = engine1.getState().memories.size

        // 2. Create second engine instance loading from the same folder
        val engine2 = EvairDigiSoulEngine(storageDir = storageDir)

        assertEquals(xp1, engine2.getState().currentXp)
        assertEquals(memories1Count, engine2.getState().memories.size)
        assertEquals(engine1.getState().bondPercent, engine2.getState().bondPercent)
        assertEquals(engine1.getState().stage, engine2.getState().stage)
    }
}
