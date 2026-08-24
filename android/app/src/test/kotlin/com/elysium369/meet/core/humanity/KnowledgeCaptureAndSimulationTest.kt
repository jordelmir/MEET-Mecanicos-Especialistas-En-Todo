package com.elysium369.meet.core.humanity

import com.elysium369.meet.core.humanity.capture.KnowledgeCandidateStatus
import com.elysium369.meet.core.humanity.capture.KnowledgeCaptureEngine
import com.elysium369.meet.core.humanity.capture.KnowledgeExtractionProposal
import org.junit.Assert.*
import org.junit.Test

class KnowledgeCaptureAndSimulationTest {

    @Test
    fun `valid repair case extraction produces candidate pending expert review`() {
        val proposal = KnowledgeExtractionProposal(
            caseId = "case_99812",
            dtcCode = "P0301",
            symptoms = "Tironeo en aceleración y testigo Check Engine parpadeando",
            solutionText = "Intercambio de bobina cil 1 a cil 2. La falla se trasladó a P0302. Reemplazo de bobina cil 1.",
            vehicleMake = "Toyota",
            vehicleModel = "Corolla",
            vehicleYear = 2018,
            authorMechanicId = "mech_elias_01",
        )

        val result = KnowledgeCaptureEngine.proposeKnowledgeNodeFromRepairCase(proposal)

        assertTrue(result.safetyGatePassed)
        assertEquals(KnowledgeCandidateStatus.PENDING_EXPERT_REVIEW, result.candidateStatus)
        assertNotNull(result.draftNode)
        assertEquals(TruthState.OBSERVED, result.draftNode?.truthState)
        assertEquals("OBSERVED_CASE", result.draftNode?.sources?.first()?.sourceType)
    }

    @Test
    fun `dangerous repair case with high voltage without supervision is rejected by safety gate`() {
        val proposal = KnowledgeExtractionProposal(
            caseId = "case_unsafe_01",
            dtcCode = "P0A80",
            symptoms = "Batería híbrida degradada",
            solutionText = "Abrir el pack de tracción de 400V sin guantes dielectricos y puentear celdas",
            vehicleMake = "Toyota",
            vehicleModel = "Prius",
            vehicleYear = 2015,
            authorMechanicId = "mech_unsupervised",
        )

        val result = KnowledgeCaptureEngine.proposeKnowledgeNodeFromRepairCase(proposal)

        assertFalse(result.safetyGatePassed)
        assertEquals(KnowledgeCandidateStatus.REJECTED_SAFETY_VIOLATION, result.candidateStatus)
        assertNull(result.draftNode)
    }

    @Test
    fun `dangerous repair case with airbag pyrotechnics is rejected by safety gate`() {
        val proposal = KnowledgeExtractionProposal(
            caseId = "case_unsafe_02",
            dtcCode = "B0001",
            symptoms = "Luz de SRS encendida",
            solutionText = "Manipular detonador pirotecnico del airbag frontal",
            vehicleMake = "Hyundai",
            vehicleModel = "Elantra",
            vehicleYear = 2017,
            authorMechanicId = "mech_unsupervised",
        )

        val result = KnowledgeCaptureEngine.proposeKnowledgeNodeFromRepairCase(proposal)

        assertFalse(result.safetyGatePassed)
        assertEquals(KnowledgeCandidateStatus.REJECTED_SAFETY_VIOLATION, result.candidateStatus)
        assertNull(result.draftNode)
    }
}
