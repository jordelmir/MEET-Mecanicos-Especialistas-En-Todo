package com.elysium369.meet.education.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElysiumDiplomaServiceTest {

    private val sampleEvidenceHashes = listOf(
        "71b393aeb4ddbb23dc4fdeb3720450a91734ebf567a0698620b273f4b545072e",
        "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0",
        "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
    )

    @Test
    fun `minting diploma requires mastery greater or equal to 0_75`() {
        val diploma = ElysiumDiplomaService.mintDiploma(
            principalId = "user_cr_test_001",
            trackTitle = "Bachillerato por Madurez: Matemática",
            trackCode = "MATEMATICA_BXM",
            academicCycle = "Diversificada",
            mastery = 0.88,
            confidence = 0.95,
            evidenceHashes = sampleEvidenceHashes
        )

        assertNotNull(diploma)
        assertEquals(64, diploma.merkleRootHash.length) // SHA-256
        assertEquals(3, diploma.totalEvidenceCount)

        // Verify AGENTS.md Rule 4 (6-field minimal QR payload: report_id, integrity_hash, vehicle_id, generated_at, report_type, verifier_url)
        val fields = diploma.qrPayload.split("|")
        assertEquals("QR payload must contain exactly 6 fields", 6, fields.size)
        assertEquals(diploma.diplomaId, fields[0])
        assertEquals(diploma.merkleRootHash, fields[1])
        assertEquals("user_cr_test_001", fields[2])
        assertEquals("EDUCATION_CERTIFICATE", fields[4])
        assertTrue(fields[5].startsWith("https://elysium369.meet/verify?id="))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `minting diploma fails if mastery is below 0_75 threshold`() {
        ElysiumDiplomaService.mintDiploma(
            principalId = "user_cr_test_002",
            trackTitle = "Fontanería 7",
            trackCode = "FONTANERIA_7",
            academicCycle = "III Ciclo",
            mastery = 0.70, // Below 0.75 threshold
            confidence = 0.80,
            evidenceHashes = sampleEvidenceHashes
        )
    }

    @Test
    fun `diploma integrity verification passes for identical evidences and fails for tampered evidences`() {
        val diploma = ElysiumDiplomaService.mintDiploma(
            principalId = "user_cr_test_003",
            trackTitle = "Electricidad 9",
            trackCode = "ELECTRICIDAD_9",
            academicCycle = "III Ciclo",
            mastery = 0.92,
            confidence = 0.98,
            evidenceHashes = sampleEvidenceHashes
        )

        // Valid verify
        val isValid = ElysiumDiplomaService.verifyDiploma(diploma, sampleEvidenceHashes)
        assertTrue("Legitimate evidence hashes must verify diploma", isValid)

        // Tampered verify
        val tamperedHashes = sampleEvidenceHashes.toMutableList().apply {
            this[0] = "0000000000000000000000000000000000000000000000000000000000000000"
        }
        val isTamperedValid = ElysiumDiplomaService.verifyDiploma(diploma, tamperedHashes)
        assertFalse("Tampered evidence hashes must fail diploma verification", isTamperedValid)
    }
}
