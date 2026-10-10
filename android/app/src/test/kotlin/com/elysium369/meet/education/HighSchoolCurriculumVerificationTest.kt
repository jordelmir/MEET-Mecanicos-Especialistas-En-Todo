package com.elysium369.meet.education

import com.elysium369.meet.education.data.CurriculumTrack
import com.elysium369.meet.education.data.EducationTrackProgress
import com.elysium369.meet.education.data.ElysiumLearningRepository
import com.elysium369.meet.education.data.InMemoryEducationProgressStorage
import com.elysium369.meet.education.data.NationalCurriculumCatalogSeed
import com.elysium369.meet.education.presentation.ElysiumLearningViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HighSchoolCurriculumVerificationTest {

    private val highSchoolTracks = listOf(
        // ── 7.º Año (III Ciclo / Zapandí) ──────────────────────────────────
        CurriculumTrack.MATEMATICA_7,
        CurriculumTrack.FONTANERIA_7,
        CurriculumTrack.ESPANOL_7,
        CurriculumTrack.CIENCIAS_7,
        CurriculumTrack.ESTUDIOS_SOCIALES_7,
        CurriculumTrack.CIVICA_7,
        CurriculumTrack.INGLES_7,
        CurriculumTrack.CIENCIAS_III_CICLO,

        // ── 8.º Año (III Ciclo / Ujarrás) ──────────────────────────────────
        CurriculumTrack.MATEMATICA_8,
        CurriculumTrack.DIBUJO_TECNICO_8,
        CurriculumTrack.ESPANOL_8,
        CurriculumTrack.CIENCIAS_8,
        CurriculumTrack.ESTUDIOS_SOCIALES_8,
        CurriculumTrack.CIVICA_8,
        CurriculumTrack.INGLES_8,

        // ── 9.º Año (III Ciclo / Tárcoles) ─────────────────────────────────
        CurriculumTrack.MATEMATICA_9,
        CurriculumTrack.ELECTRICIDAD_9,
        CurriculumTrack.ESPANOL_9,
        CurriculumTrack.CIENCIAS_9,
        CurriculumTrack.ESTUDIOS_SOCIALES_9,
        CurriculumTrack.CIVICA_9,
        CurriculumTrack.INGLES_9,

        // ── Diversificada & Bachillerato por Madurez (10.º - 11.º / BxM) ───
        CurriculumTrack.MATEMATICA_BXM,
        CurriculumTrack.ESPANOL_BXM,
        CurriculumTrack.BIOLOGIA_BXM,
        CurriculumTrack.QUIMICA_BXM,
        CurriculumTrack.FISICA_BXM,
        CurriculumTrack.SOCIALES_BXM,
        CurriculumTrack.CIVICA_BXM,
        CurriculumTrack.INGLES_BXM,
    )

    @Test
    fun `all 30 high school tracks exist and have complete curriculum units`() {
        assertEquals(30, highSchoolTracks.size)

        for (track in highSchoolTracks) {
            val units = NationalCurriculumCatalogSeed.getUnitsForTrack(track)
            assertTrue(
                "Track ${track.name} must have at least 4 units, but had ${units.size}",
                units.size >= 4,
            )

            for (unit in units) {
                assertTrue("Unit ID must not be blank in ${track.name}", unit.id.isNotBlank())
                assertTrue("Unit title must not be blank in ${track.name}", unit.title.isNotBlank())
                assertTrue("Unit month must not be blank in ${track.name}", unit.monthName.isNotBlank())
                assertTrue(
                    "Unit ${unit.id} in ${track.name} must contain at least 1 concept",
                    unit.concepts.isNotEmpty(),
                )

                for (concept in unit.concepts) {
                    assertTrue("Concept ID must not be blank in ${track.name}", concept.id.isNotBlank())
                    assertTrue("Concept title must not be blank in ${track.name}", concept.title.isNotBlank())
                    assertTrue(
                        "Concept ${concept.id} in ${track.name} must have tasks",
                        concept.tasks.isNotEmpty(),
                    )

                    for (task in concept.tasks) {
                        assertTrue("Task prompt must not be blank in ${concept.id}", task.prompt.isNotBlank())
                        assertTrue("Task options must not be empty in ${task.id}", task.options.isNotEmpty())
                        assertTrue("Task explanation must not be blank in ${task.id}", task.explanation.isNotBlank())
                    }
                }
            }
        }
    }

    @Test
    fun `every high school track contains PISA contextual transfer tasks`() {
        for (track in highSchoolTracks) {
            val units = NationalCurriculumCatalogSeed.getUnitsForTrack(track)
            val transferTasksCount = units.sumOf { u ->
                u.concepts.sumOf { c -> c.tasks.count { it.isTransferTask } }
            }

            assertTrue(
                "High school track ${track.name} must contain at least 3 PISA transfer tasks, found $transferTasksCount",
                transferTasksCount >= 3,
            )
        }
    }

    @Test
    fun `local progress storage persists and restores state across sessions without resetting to zero`() = runBlocking {
        val storage = InMemoryEducationProgressStorage()
        val repository = ElysiumLearningRepository(null, storage)
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)

        // Session 1: Student opens 8.º Dibujo Técnico, navigates to Unit 2, Concept 2
        val vm1 = ElysiumLearningViewModel(repository, testScope)
        vm1.selectTrack(CurriculumTrack.DIBUJO_TECNICO_8)

        val units = repository.getCurriculumUnits(CurriculumTrack.DIBUJO_TECNICO_8)
        val unit2 = units[1]
        val concept2 = unit2.concepts.first()

        vm1.selectSection(unit2.id, concept2.id)
        vm1.selectOption(0)
        vm1.submitAnswerSync()

        val stateAfterAnswer = vm1.uiState.value
        val savedEvidenceHash = stateAfterAnswer.lastEvidenceHash
        assertNotNull(savedEvidenceHash)
        assertTrue(stateAfterAnswer.currentMasteryEstimate > 0.0)

        // Simulate App Closing and Reopening (New ViewModel instance with same repository/storage)
        val vm2 = ElysiumLearningViewModel(repository, testScope)
        val restoredState = vm2.uiState.value

        assertEquals("Should restore track", CurriculumTrack.DIBUJO_TECNICO_8, restoredState.track)
        assertEquals("Should restore unit", unit2.id, restoredState.selectedUnitId)
        assertEquals("Should restore concept", concept2.id, restoredState.selectedConceptId)
        assertEquals(
            "Mastery estimate must not reset to 0",
            stateAfterAnswer.currentMasteryEstimate,
            restoredState.currentMasteryEstimate,
            0.001,
        )
    }

    @Test
    fun `bidirectional section navigation allows moving forwards and backwards through concepts and units`() = runBlocking {
        val storage = InMemoryEducationProgressStorage()
        val repository = ElysiumLearningRepository(null, storage)
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)

        val vm = ElysiumLearningViewModel(repository, testScope)
        vm.selectTrack(CurriculumTrack.MATEMATICA_7)

        val units = repository.getCurriculumUnits(CurriculumTrack.MATEMATICA_7)
        val firstUnit = units.first()
        val secondUnit = units[1]

        assertEquals(firstUnit.id, vm.uiState.value.selectedUnitId)
        assertEquals(firstUnit.concepts.first().id, vm.uiState.value.selectedConceptId)

        // Advance to next unit
        vm.advanceToNextUnit()
        assertEquals("Should advance to unit 2", secondUnit.id, vm.uiState.value.selectedUnitId)

        // Go back to previous unit
        vm.previousUnit()
        assertEquals("Should go back to unit 1", firstUnit.id, vm.uiState.value.selectedUnitId)

        // Advance concept forward
        vm.advanceToNextConcept()
        val stateAfterConceptAdvance = vm.uiState.value
        assertNotNull(stateAfterConceptAdvance.selectedConceptId)

        // Previous concept backward
        vm.previousConcept()
        assertEquals(firstUnit.concepts.first().id, vm.uiState.value.selectedConceptId)

        // Direct section selection
        vm.selectSection(secondUnit.id, secondUnit.concepts.first().id)
        assertEquals(secondUnit.id, vm.uiState.value.selectedUnitId)
        assertEquals(secondUnit.concepts.first().id, vm.uiState.value.selectedConceptId)
    }
}
