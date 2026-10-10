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
        assertEquals("1.º Matemática (I Ciclo)", state.activeSubject)
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
        assertEquals("cr_font7_u01", state.selectedUnitId)
        assertEquals("cr_font7_c_safety_epp", state.selectedConceptId)
        assertNotNull(state.activeTask)
        assertEquals("task_font7_safety_glasses", state.activeTask?.id)

        // Switch to Unit 3 to verify PVC joinery
        viewModel.selectUnit("cr_font7_u03")
        assertEquals("cr_font7_u03", viewModel.uiState.value.selectedUnitId)
        assertEquals("cr_font7_c_pvc_joinery", viewModel.uiState.value.selectedConceptId)
        assertEquals("task_font7_pvc_holding_time", viewModel.uiState.value.activeTask?.id)

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

    @Test
    fun `switching track across national curriculum from Grade 1 to Grade 2, Grade 8 CAD and Grade 11 BxM`() = runBlocking {
        val repository = ElysiumLearningRepository()
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val viewModel = ElysiumLearningViewModel(repository, testScope)

        // 1. Grade 2 Matemática
        viewModel.selectTrack(CurriculumTrack.MATEMATICA_2)
        assertEquals(CurriculumTrack.MATEMATICA_2, viewModel.uiState.value.track)
        assertEquals(2, viewModel.uiState.value.activeGrade)
        assertTrue(viewModel.uiState.value.units.isNotEmpty())
        assertEquals("cr_mat2_c_centenas", viewModel.uiState.value.selectedConceptId)

        // 2. Grade 8 Dibujo Técnico CAD
        viewModel.selectTrack(CurriculumTrack.DIBUJO_TECNICO_8)
        assertEquals(CurriculumTrack.DIBUJO_TECNICO_8, viewModel.uiState.value.track)
        assertEquals(8, viewModel.uiState.value.activeGrade)
        assertEquals("cr_art_c_dibujo_tecnico", viewModel.uiState.value.selectedConceptId)

        // 3. Grade 11 Matemática BxM
        viewModel.selectTrack(CurriculumTrack.MATEMATICA_BXM)
        assertEquals(CurriculumTrack.MATEMATICA_BXM, viewModel.uiState.value.track)
        assertEquals(11, viewModel.uiState.value.activeGrade)
        assertEquals("cr_mat_bxm_c_circunferencia", viewModel.uiState.value.selectedConceptId)
    }

    @Test
    fun `all 40 tracks load official curriculum units and concepts without exception`() = runBlocking {
        val repository = ElysiumLearningRepository()
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val viewModel = ElysiumLearningViewModel(repository, testScope)

        assertEquals("Total tracks must equal 40", 40, CurriculumTrack.values().size)

        for (track in CurriculumTrack.values()) {
            viewModel.selectTrack(track)
            val state = viewModel.uiState.value
            assertEquals("Track should match requested track", track, state.track)
            assertTrue("Track ${track.name} must have at least one unit", state.units.isNotEmpty())
            assertNotNull("Track ${track.name} must have selected unit", state.selectedUnitId)
            assertNotNull("Track ${track.name} must have selected concept", state.selectedConceptId)
        }
    }

    @Test
    fun `selectGrade dynamically sets cycle and defaults to corresponding grade track`() = runBlocking {
        val repository = ElysiumLearningRepository()
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val viewModel = ElysiumLearningViewModel(repository, testScope)

        // Select Grade 7
        viewModel.selectGrade(7)
        assertEquals(7, viewModel.uiState.value.activeGrade)
        assertEquals("III Ciclo", viewModel.uiState.value.selectedCycle)
        assertEquals(7, viewModel.uiState.value.track.gradeNumber)

        // Select Grade 11 / BxM
        viewModel.selectGrade(11)
        assertEquals(11, viewModel.uiState.value.activeGrade)
        assertEquals("Diversificada", viewModel.uiState.value.selectedCycle)
        assertTrue(viewModel.uiState.value.track.isDiversifiedOrAdult)

        // Select Grade 3
        viewModel.selectGrade(3)
        assertEquals(3, viewModel.uiState.value.activeGrade)
        assertEquals("I Ciclo", viewModel.uiState.value.selectedCycle)
        assertEquals(CurriculumTrack.MATEMATICA_3, viewModel.uiState.value.track)
    }

    @Test
    fun `verifying multi-grade economic bridges for ISCO 3118 CAD, ISCO 7411 Electrician and ISCO 7231 Automotive`() = runBlocking {
        val repository = ElysiumLearningRepository()
        val mappings = repository.getEconomicBridgeMappings()

        val isco3118 = mappings.firstOrNull { it.iscoCode == "3118" }
        assertNotNull("Must include ISCO 3118 CAD mapping", isco3118)
        assertEquals("ARCHITECTURAL_AND_TECHNICAL_CAD", isco3118?.serviceVertical)

        val isco7411 = mappings.firstOrNull { it.iscoCode == "7411" }
        assertNotNull("Must include ISCO 7411 Electrician mapping", isco7411)
        assertEquals("RESIDENTIAL_ELECTRICAL_SERVICES", isco7411?.serviceVertical)

        val isco7126 = mappings.firstOrNull { it.iscoCode == "7126" }
        assertNotNull("Must include ISCO 7126 Plumbing mapping", isco7126)
        assertEquals("RESIDENTIAL_PLUMBING", isco7126?.serviceVertical)

        val isco7231 = mappings.firstOrNull { it.iscoCode == "7231" }
        assertNotNull("Must include ISCO 7231 Automotive Diagnostics mapping", isco7231)
        assertEquals("AUTOMOTIVE_MECHANICAL_DIAGNOSTICS", isco7231?.serviceVertical)
    }

    @Test
    fun `socratic tutor bottom sheet opens, updates dialogue with hints, analogies and closes cleanly`() = runBlocking {
        val repository = ElysiumLearningRepository()
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val viewModel = ElysiumLearningViewModel(repository, testScope)

        assertFalse(viewModel.uiState.value.isSocraticSheetVisible)
        viewModel.openSocraticTutor()
        assertTrue(viewModel.uiState.value.isSocraticSheetVisible)

        // Request hint synchronously
        viewModel.requestSocraticHintSync()
        val stateAfterHint = viewModel.uiState.value
        assertEquals(2, stateAfterHint.socraticHintTierCount) // Incremented to tier 2 for next time
        assertTrue(stateAfterHint.socraticDialogue.isNotEmpty())
        assertFalse(stateAfterHint.socraticDialogue.last().isUser)

        // Request real world analogy synchronously
        viewModel.requestRealWorldAnalogySync()
        val stateAfterAnalogy = viewModel.uiState.value
        assertTrue(stateAfterAnalogy.socraticDialogue.size >= 2)

        // Dismiss
        viewModel.dismissSocraticTutor()
        assertFalse(viewModel.uiState.value.isSocraticSheetVisible)
    }

    @Test
    fun `socratic free inquiry records student message and responds with pedagogical guide`() = runBlocking {
        val repository = ElysiumLearningRepository()
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val viewModel = ElysiumLearningViewModel(repository, testScope)

        viewModel.openSocraticTutor()
        viewModel.sendSocraticQuerySync("¿Dónde queda la parte de atrás?")

        val dialogue = viewModel.uiState.value.socraticDialogue
        assertTrue("Dialogue should have at least 2 entries (user query and tutor reply)", dialogue.size >= 2)
        val userEntry = dialogue[dialogue.size - 2]
        val tutorEntry = dialogue[dialogue.size - 1]
        assertTrue(userEntry.isUser)
        assertEquals("¿Dónde queda la parte de atrás?", userEntry.text)
        assertFalse(tutorEntry.isUser)
    }

    @Test
    fun `advanceToNextTaskOrConcept advances through tasks, concepts, and units sequentially across full course`() = runBlocking {
        val repository = ElysiumLearningRepository()
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val viewModel = ElysiumLearningViewModel(repository, testScope)

        // 1. Initial state: Unit 1, Concept 1, Task 0
        val initial = viewModel.uiState.value
        assertEquals("cr_mat1_u02", initial.selectedUnitId)
        assertEquals("cr_mat1_c_spatial_pos", initial.selectedConceptId)
        assertEquals("task_mat1_spatial_cat_house", initial.activeTask?.id)
        assertEquals(0, initial.currentTaskIndex)

        // 2. Advance to Task 1 in same concept (transfer task)
        viewModel.advanceToNextTaskOrConcept()
        val afterTask1 = viewModel.uiState.value
        assertEquals("cr_mat1_c_spatial_pos", afterTask1.selectedConceptId)
        assertEquals("task_mat1_spatial_transfer_exhaust", afterTask1.activeTask?.id)
        assertEquals(1, afterTask1.currentTaskIndex)

        // 3. Advance past last task of Concept 1 -> Advances to Concept 2 in same unit!
        viewModel.advanceToNextTaskOrConcept()
        val afterConcept1 = viewModel.uiState.value
        assertEquals("cr_mat1_u02", afterConcept1.selectedUnitId)
        assertEquals("cr_mat1_c_dimensions", afterConcept1.selectedConceptId)
        assertEquals("task_mat1_dim_wrench", afterConcept1.activeTask?.id)
        assertEquals(0, afterConcept1.currentTaskIndex)

        // 4. Advance past last task of Concept 2 -> Advances to Unit 2 (Marzo)!
        viewModel.advanceToNextTaskOrConcept()
        val afterUnit1 = viewModel.uiState.value
        assertEquals("cr_mat1_u03", afterUnit1.selectedUnitId)
        assertEquals("cr_mat1_c_counting_100", afterUnit1.selectedConceptId)
        assertEquals("task_mat1_counting_bolts", afterUnit1.activeTask?.id)

        // 5. Test previousTaskOrConcept goes back seamlessly!
        viewModel.previousTaskOrConcept()
        val afterBack = viewModel.uiState.value
        assertEquals("cr_mat1_u02", afterBack.selectedUnitId)
        assertEquals("cr_mat1_c_dimensions", afterBack.selectedConceptId)
        assertEquals("task_mat1_dim_wrench", afterBack.activeTask?.id)
    }

    @Test
    fun `retryCurrentTask clears selection and closes error modal`() = runBlocking {
        val repository = ElysiumLearningRepository()
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val viewModel = ElysiumLearningViewModel(repository, testScope)

        viewModel.selectOption(1)
        viewModel.submitAnswerSync() // Incorrect answer triggers modal
        assertTrue(viewModel.uiState.value.isEvidenceModalVisible)
        assertEquals(1, viewModel.uiState.value.selectedOptionIndex)

        viewModel.retryCurrentTask()
        assertFalse(viewModel.uiState.value.isEvidenceModalVisible)
        assertEquals(null, viewModel.uiState.value.selectedOptionIndex)
    }
}
