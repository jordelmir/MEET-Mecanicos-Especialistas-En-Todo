package com.elysium369.meet.education.ai

import com.elysium369.meet.education.data.CourseZeroCurriculumSeed
import com.elysium369.meet.education.data.CurriculumConceptData
import com.elysium369.meet.education.data.NationalCurriculumDeepKnowledge
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocraticTutorEngineTest {

    private val engine = SocraticTutorEngine()
    private val sampleConcept = CourseZeroCurriculumSeed.MATEMATICA_1_UNITS[0].concepts[0]
    private val sampleTask = sampleConcept.tasks[0]

    @Test
    fun `hint tier progression starts at Tier 1 and progresses through Tier 3 without revealing answer`() = runBlocking {
        // First hint -> Tier 1 (Orientativa)
        val msg1 = engine.consultTutor(
            concept = sampleConcept,
            task = sampleTask,
            mode = SocraticMode.HINT,
            hintTier = 1
        )
        val hint1 = msg1.text
        assertTrue("Tier 1 hint must have content", hint1.isNotEmpty())
        assertFalse("Tutor must never reveal direct answer", hint1.contains("La respuesta es"))

        // Second hint -> Tier 2 (Focalizada en principio)
        val msg2 = engine.consultTutor(
            concept = sampleConcept,
            task = sampleTask,
            mode = SocraticMode.HINT,
            hintTier = 2
        )
        val hint2 = msg2.text
        assertTrue("Tier 2 hint must have content", hint2.isNotEmpty())
        assertFalse("Tutor must never reveal direct answer", hint2.contains("La respuesta correcta es"))

        // Third hint -> Tier 3 (Analogía física guiada)
        val msg3 = engine.consultTutor(
            concept = sampleConcept,
            task = sampleTask,
            mode = SocraticMode.HINT,
            hintTier = 3
        )
        val hint3 = msg3.text
        assertTrue("Tier 3 hint must have content", hint3.isNotEmpty())
        assertFalse("Tutor must never reveal direct answer", hint3.contains("La respuesta es"))
    }

    @Test
    fun `real world analogy mode generates workshop and daily life bridges`() = runBlocking {
        val msg = engine.consultTutor(
            concept = sampleConcept,
            task = sampleTask,
            mode = SocraticMode.REAL_WORLD_ANALOGY
        )
        val analogy = msg.text
        assertTrue("Analogy must be non-empty", analogy.isNotEmpty())
        assertTrue("Should include vehicle or practical analogy",
            analogy.contains("sensores") || analogy.contains("reversa") || analogy.contains("Navegación") || analogy.contains("planos") || analogy.contains("automotrices"))
    }

    @Test
    fun `misconception help mode guides reflection for detected misconception`() = runBlocking {
        val msg = engine.consultTutor(
            concept = sampleConcept,
            task = sampleTask,
            mode = SocraticMode.MISCONCEPTION_HELP,
            misconceptionCode = "MISCONCEPTION_REVERSED_DELANTE_DETRAS"
        )
        val guidance = msg.text
        assertTrue("Misconception guidance must have content", guidance.isNotEmpty())
        assertTrue("Must prompt reflection about point of view or front vs back",
            guidance.contains("frente") || guidance.contains("detrás") || guidance.contains("ojos") || guidance.contains("perspectiva") || guidance.contains("espalda"))
    }

    @Test
    fun `step by step mode returns structured scaffolding`() = runBlocking {
        val msg = engine.consultTutor(
            concept = sampleConcept,
            task = sampleTask,
            mode = SocraticMode.STEP_BY_STEP
        )
        val steps = msg.text
        assertTrue("Step-by-step guidance must have content", steps.isNotEmpty())
        assertTrue("Must include numbered steps", steps.contains("1.") || steps.contains("Paso"))
    }

    @Test
    fun `free inquiry offline fallback responds helpfully with pedagogical inquiry`() = runBlocking {
        val msg = engine.consultTutor(
            concept = sampleConcept,
            task = sampleTask,
            mode = SocraticMode.FREE_INQUIRY,
            userQuery = "¿Por qué el gato no está adentro?"
        )
        val response = msg.text
        assertTrue("Response must have content", response.isNotEmpty())
        assertTrue("Must encourage thinking", response.contains("?") || response.contains("piensas") || response.contains("observas"))
    }

    @Test
    fun `procedural deep knowledge synthesis works for concepts outside static catalog`() = runBlocking {
        val unknownConcept = CurriculumConceptData(
            id = "cr_random_unregistered_concept",
            conceptCode = "CR-TEST-99",
            title = "Concepto No Registrado",
            description = "Concepto experimental de prueba para síntesis procedimental",
            targetMonth = 1
        )
        val dynamicKnowledge = NationalCurriculumDeepKnowledge.getKnowledgeForConcept(unknownConcept)
        assertNotNull("Dynamic knowledge must never be null", dynamicKnowledge)
        assertEquals(3, dynamicKnowledge.socraticHintTiers.size)
        assertTrue(dynamicKnowledge.misconceptions.isNotEmpty())
        assertTrue(dynamicKnowledge.expertMentalModel.isNotEmpty())
    }
}
