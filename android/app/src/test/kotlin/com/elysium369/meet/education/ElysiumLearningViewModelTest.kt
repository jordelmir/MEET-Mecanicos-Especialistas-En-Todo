package com.elysium369.meet.education

import com.elysium369.meet.education.data.CourseZeroCurriculumSeed
import com.elysium369.meet.education.data.CurriculumTrack
import com.elysium369.meet.education.data.ElysiumLearningRepository
import com.elysium369.meet.education.presentation.ElysiumLearningViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElysiumLearningViewModelTest {

    @Test
    fun `initial state loads Course Zero 1 ano and curriculum units`() = runBlocking {
        val repository = ElysiumLearningRepository()
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val viewModel = ElysiumLearningViewModel(repository, testScope)
        viewModel.refreshFrontierSync(1, "MATEMATICA")

        val state = viewModel.uiState.value
        assertEquals(CurriculumTrack.MATEMATICA_1, state.track)
        assertEquals(1, state.activeGrade)
        assertEquals("Matemática 1.º Año (MEP 2026)", state.activeSubject)
        assertEquals(10, state.units.size) // 10 official MEP months
        assertEquals("cr_mat1_u02", state.selectedUnitId)
        assertEquals("cr_mat1_c_spatial_pos", state.selectedConceptId)
        assertNotNull(state.activeTask)
        assertEquals("task_mat1_spatial_cat_house", state.activeTask?.id)
        assertTrue("Frontier should contain reachable concepts", state.frontierConcepts.isNotEmpty())
    }

    @Test
    fun `answering spatial task correctly updates mastery and records sha256 evidence`() = runBlocking {
        val repository = ElysiumLearningRepository()
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val viewModel = ElysiumLearningViewModel(repository, testScope)

        // Select correct option 0: "Detrás de la casita"
        viewModel.selectOption(0)
        val result = viewModel.submitAnswerSync()

        assertNotNull(result)
        assertTrue(result!!.isSuccess)
        val record = result.getOrThrow()
        assertTrue(record.isCorrect)
        assertEquals(64, record.rawEvidenceHash.length) // SHA-256

        val state = viewModel.uiState.value
        assertTrue(state.feedbackSuccess == true)
        assertNotNull(state.lastEvidenceHash)
        assertEquals(record.rawEvidenceHash, state.lastEvidenceHash)
        assertTrue("Mastery should increase from 0.0", state.currentMasteryEstimate > 0.0)
    }

    @Test
    fun `answering spatial task incorrectly applies pedagogical diagnosis and records misconception`() = runBlocking {
        val repository = ElysiumLearningRepository()
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val viewModel = ElysiumLearningViewModel(repository, testScope)

        // Select incorrect option 1: "Delante de la casita"
        viewModel.selectOption(1)
        val result = viewModel.submitAnswerSync()

        assertNotNull(result)
        assertTrue(result!!.isSuccess)
        val record = result.getOrThrow()
        assertFalse(record.isCorrect)
        assertEquals("MISCONCEPTION_REVERSED_DELANTE_DETRAS", record.misconceptionCode)

        val state = viewModel.uiState.value
        assertFalse(state.feedbackSuccess == true)
        assertEquals("MISCONCEPTION_REVERSED_DELANTE_DETRAS", state.misconceptionDetected)
    }

    @Test
    fun `currency calculator task accumulates colones and confirms exact payment`() = runBlocking {
        val repository = ElysiumLearningRepository()
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val viewModel = ElysiumLearningViewModel(repository, testScope)

        // Switch to Unit 4 (Mayo - Economía y Datos)
        viewModel.selectUnit("cr_mat1_u05")
        val task = viewModel.uiState.value.activeTask
        assertNotNull(task)
        assertEquals("task_mat1_currency_juice", task?.id)
        assertEquals(375, task?.targetAmountCrc)

        // Add coins: 100 + 100 + 100 + 50 + 25 = 375 colones
        viewModel.addColones(100)
        viewModel.addColones(100)
        viewModel.addColones(100)
        viewModel.addColones(50)
        viewModel.addColones(25)

        assertEquals(375, viewModel.uiState.value.accumulatedColones)

        val result = viewModel.submitAnswerSync()
        assertNotNull(result)
        assertTrue(result!!.isSuccess)
        val record = result.getOrThrow()
        assertTrue(record.isCorrect)
        assertEquals(64, record.rawEvidenceHash.length)
        assertTrue(viewModel.uiState.value.feedbackSuccess == true)
    }

    @Test
    fun `switching track to Fontaneria 7 loads technical units and economic bridge`() = runBlocking {
        val repository = ElysiumLearningRepository()
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val viewModel = ElysiumLearningViewModel(repository, testScope)

        viewModel.selectTrack(CurriculumTrack.FONTANERIA_7)
        viewModel.refreshFrontierSync(7, "ARTES_INDUSTRIALES")

        val state = viewModel.uiState.value
        assertEquals(CurriculumTrack.FONTANERIA_7, state.track)
        assertEquals(7, state.activeGrade)
        assertEquals(4, state.units.size)
        assertEquals("cr_font7_u03", state.selectedUnitId)
        assertEquals("cr_font7_c_pvc_joinery", state.selectedConceptId)
        assertNotNull(state.activeTask)
        assertEquals("task_font7_pvc_holding_time", state.activeTask?.id)

        val mappings = repository.getEconomicBridgeMappings()
        assertTrue("Must include ISCO-08 7126 mapping", mappings.any { it.iscoCode == "7126" && it.serviceVertical == "RESIDENTIAL_PLUMBING" })
    }

    @Test
    fun `transfer challenge task breaks past 0_75 mastery cap to full mastery`() = runBlocking {
        val repository = ElysiumLearningRepository()
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val viewModel = ElysiumLearningViewModel(repository, testScope)

        // Answer multiple standard tasks to max out at 0.75 cap
        for (i in 1..8) {
            viewModel.selectOption(0)
            viewModel.submitAnswerSync()
        }

        val cappedState = viewModel.uiState.value
        assertTrue("Without transfer, mastery cannot exceed 0.75", cappedState.currentMasteryEstimate <= 0.75)
        assertFalse("Concept is not yet fully mastered", cappedState.isTransferUnlocked)

        // Select the transfer task
        viewModel.selectTask("task_mat1_spatial_transfer_exhaust")
        assertTrue(viewModel.uiState.value.activeTask?.isTransferTask == true)

        // Solve transfer task correctly
        viewModel.selectOption(0)
        viewModel.submitAnswerSync()

        val finalState = viewModel.uiState.value
        assertTrue("Transfer task breaks through 0.75 cap", finalState.currentMasteryEstimate > 0.75)
        assertTrue("With transfer, mastery reaches full mastery", finalState.currentMasteryEstimate >= 0.85)
        assertTrue("Concept is officially mastered with transfer", finalState.isTransferUnlocked)
    }
}
