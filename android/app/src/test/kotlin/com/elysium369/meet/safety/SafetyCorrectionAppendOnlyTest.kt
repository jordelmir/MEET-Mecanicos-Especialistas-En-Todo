package com.elysium369.meet.safety

import com.elysium369.meet.safety.domain.SafetyCorrectionPolicy
import com.elysium369.meet.safety.domain.CorrectionRecord
import org.junit.Assert.*
import org.junit.Test

class SafetyCorrectionAppendOnlyTest {

    @Test
    fun `create with valid inputs succeeds`() {
        val correction = SafetyCorrectionPolicy.create(
            correctionId = "corr-1",
            publicationId = "pub-1",
            replacesRevision = 1L,
            reason = "Valid reason for correction",
            createdAt = 123456789L
        )
        assertNotNull(correction)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `create with blank correctionId throws`() {
        SafetyCorrectionPolicy.create(
            correctionId = "  ",
            publicationId = "pub-1",
            replacesRevision = 1L,
            reason = "Valid reason for correction",
            createdAt = 123456789L
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `create with blank publicationId throws`() {
        SafetyCorrectionPolicy.create(
            correctionId = "corr-1",
            publicationId = "",
            replacesRevision = 1L,
            reason = "Valid reason for correction",
            createdAt = 123456789L
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `create with replacesRevision 0 throws`() {
        SafetyCorrectionPolicy.create(
            correctionId = "corr-1",
            publicationId = "pub-1",
            replacesRevision = 0L,
            reason = "Valid reason for correction",
            createdAt = 123456789L
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `create with reason less than 10 chars throws`() {
        SafetyCorrectionPolicy.create(
            correctionId = "corr-1",
            publicationId = "pub-1",
            replacesRevision = 1L,
            reason = "short",
            createdAt = 123456789L
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `create with createdAt 0 throws`() {
        SafetyCorrectionPolicy.create(
            correctionId = "corr-1",
            publicationId = "pub-1",
            replacesRevision = 1L,
            reason = "Valid reason for correction",
            createdAt = 0L
        )
    }
}
