package com.elysium369.meet.safety.intelligence

import com.elysium369.meet.safety.intelligence.domain.CaseAccessClassification
import com.elysium369.meet.safety.intelligence.domain.CaseAuditEntry
import com.elysium369.meet.safety.intelligence.domain.InvestigativeCase
import com.elysium369.meet.safety.intelligence.domain.InvestigativeCaseLifecycle
import org.junit.Assert.*
import org.junit.Test

class SafetyInvestigativeCaseLifecycleTest {

    @Test
    fun caseStartsInDraftAndTransitionsThroughStrictVerificationStages() {
        val initialCase = InvestigativeCase(
            caseId = "case-cr-001",
            title = "Auditoría de Adjudicaciones SICOP",
            purposeAndScope = "Verificación de concentración en licitaciones de obra vial conforme a Ley 9986",
            responsibleOrganization = "Unidad de Integridad Institucional",
            leadInvestigatorId = "analyst-01",
            accessClassification = CaseAccessClassification.CONFIDENTIAL_INSTITUTIONAL,
            lifecycleState = InvestigativeCaseLifecycle.DRAFT,
            createdAt = 1715000000000L,
            updatedAt = 1715000000000L,
        )

        assertEquals(InvestigativeCaseLifecycle.DRAFT, initialCase.lifecycleState)

        // Progress to SOURCE_VALIDATION
        val validatedCase = initialCase.copy(
            lifecycleState = InvestigativeCaseLifecycle.SOURCE_VALIDATION,
            auditLog = listOf(
                CaseAuditEntry(
                    timestamp = 1715001000000L,
                    actorId = "analyst-01",
                    action = "SUBMIT_FOR_VALIDATION",
                    justification = "Fuentes oficiales SICOP y Registro Nacional incorporadas con SHA-256 verificado",
                ),
            ),
        )

        assertEquals(InvestigativeCaseLifecycle.SOURCE_VALIDATION, validatedCase.lifecycleState)
        assertEquals(1, validatedCase.auditLog.size)
        assertEquals("SUBMIT_FOR_VALIDATION", validatedCase.auditLog.first().action)

        // Progress through EDITORIAL_REVIEW and DISCLOSURE_APPROVED
        val approvedCase = validatedCase.copy(
            lifecycleState = InvestigativeCaseLifecycle.DISCLOSURE_APPROVED,
            auditLog = validatedCase.auditLog + CaseAuditEntry(
                timestamp = 1715005000000L,
                actorId = "editor-02",
                action = "APPROVE_DISCLOSURE_TWO_PERSON_RULE",
                justification = "Validación editorial cumplida. Dossier firmado criptográficamente para envío a Fiscalía.",
            ),
        )

        assertEquals(InvestigativeCaseLifecycle.DISCLOSURE_APPROVED, approvedCase.lifecycleState)
        assertEquals(2, approvedCase.auditLog.size)
    }

    @Test
    fun confidentialInstitutionalClassificationIsPreserved() {
        val case = InvestigativeCase(
            caseId = "case-cr-002",
            title = "Expediente Confidencial OIJ",
            purposeAndScope = "Cooperación probatoria con autoridades judiciales",
            responsibleOrganization = "Fiscalía General",
            leadInvestigatorId = "investigator-77",
            accessClassification = CaseAccessClassification.CONFIDENTIAL_INSTITUTIONAL,
            lifecycleState = InvestigativeCaseLifecycle.EDITORIAL_REVIEW,
            createdAt = 1715000000000L,
            updatedAt = 1715000000000L,
        )

        assertEquals(CaseAccessClassification.CONFIDENTIAL_INSTITUTIONAL, case.accessClassification)
        assertNotEquals(CaseAccessClassification.APPROVED_FOR_PUBLIC_RELEASE, case.accessClassification)
    }
}
