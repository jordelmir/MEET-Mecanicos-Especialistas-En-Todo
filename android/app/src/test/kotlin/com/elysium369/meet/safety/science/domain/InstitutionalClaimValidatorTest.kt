package com.elysium369.meet.safety.science.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * §58 — InstitutionalClaimType restrictions.
 * CRIMINAL_LIABILITY_ASSERTION requires judicial/court/prosecutorial source,
 * cannot be AI-created, and must have evidence.
 */
class InstitutionalClaimValidatorTest {

    @Test
    fun `normal claim types are always allowed`() {
        InstitutionalClaimType.entries
            .filter { it != InstitutionalClaimType.CRIMINAL_LIABILITY_ASSERTION }
            .forEach { type ->
                assertTrue(
                    "$type should be allowed",
                    InstitutionalClaimValidator.isAllowed(
                        claimType = type,
                        sourceEntityType = null,
                        createdByAi = true,
                        hasEvidence = false,
                    ),
                )
            }
    }

    @Test
    fun `criminal liability from court is allowed`() {
        assertTrue(
            InstitutionalClaimValidator.isAllowed(
                claimType = InstitutionalClaimType.CRIMINAL_LIABILITY_ASSERTION,
                sourceEntityType = EntityType.COURT,
                createdByAi = false,
                hasEvidence = true,
            ),
        )
    }

    @Test
    fun `criminal liability from judicial body is allowed`() {
        assertTrue(
            InstitutionalClaimValidator.isAllowed(
                claimType = InstitutionalClaimType.CRIMINAL_LIABILITY_ASSERTION,
                sourceEntityType = EntityType.JUDICIAL_BODY,
                createdByAi = false,
                hasEvidence = true,
            ),
        )
    }

    @Test
    fun `criminal liability from prosecutorial body is allowed`() {
        assertTrue(
            InstitutionalClaimValidator.isAllowed(
                claimType = InstitutionalClaimType.CRIMINAL_LIABILITY_ASSERTION,
                sourceEntityType = EntityType.PROSECUTORIAL_BODY,
                createdByAi = false,
                hasEvidence = true,
            ),
        )
    }

    @Test
    fun `criminal liability by AI is BLOCKED`() {
        assertFalse(
            "AI must never create criminal liability assertions",
            InstitutionalClaimValidator.isAllowed(
                claimType = InstitutionalClaimType.CRIMINAL_LIABILITY_ASSERTION,
                sourceEntityType = EntityType.COURT,
                createdByAi = true,
                hasEvidence = true,
            ),
        )
    }

    @Test
    fun `criminal liability without evidence is BLOCKED`() {
        assertFalse(
            "Criminal liability requires evidence",
            InstitutionalClaimValidator.isAllowed(
                claimType = InstitutionalClaimType.CRIMINAL_LIABILITY_ASSERTION,
                sourceEntityType = EntityType.COURT,
                createdByAi = false,
                hasEvidence = false,
            ),
        )
    }

    @Test
    fun `criminal liability from person is BLOCKED`() {
        assertFalse(
            "Only judicial bodies can source criminal liability",
            InstitutionalClaimValidator.isAllowed(
                claimType = InstitutionalClaimType.CRIMINAL_LIABILITY_ASSERTION,
                sourceEntityType = EntityType.PERSON,
                createdByAi = false,
                hasEvidence = true,
            ),
        )
    }

    @Test
    fun `criminal liability from police is BLOCKED`() {
        assertFalse(
            "Police cannot determine criminal liability",
            InstitutionalClaimValidator.isAllowed(
                claimType = InstitutionalClaimType.CRIMINAL_LIABILITY_ASSERTION,
                sourceEntityType = EntityType.POLICE_BODY,
                createdByAi = false,
                hasEvidence = true,
            ),
        )
    }

    @Test
    fun `criminal liability from organization is BLOCKED`() {
        assertFalse(
            InstitutionalClaimValidator.isAllowed(
                claimType = InstitutionalClaimType.CRIMINAL_LIABILITY_ASSERTION,
                sourceEntityType = EntityType.ORGANIZATION,
                createdByAi = false,
                hasEvidence = true,
            ),
        )
    }
}
