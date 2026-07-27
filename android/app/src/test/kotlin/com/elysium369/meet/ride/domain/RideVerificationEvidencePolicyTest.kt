package com.elysium369.meet.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideVerificationEvidencePolicyTest {

    private val validPhoto = VerificationFileEvidence(
        label = "profile",
        path = "/private/profile.jpg",
        byteCount = 42L,
    )

    @Test
    fun `passenger evidence requires identity fields and three nonempty files`() {
        val result = RideVerificationEvidencePolicy.evaluatePassenger(
            fullName = "María López",
            phone = "+506 8888-8888",
            files = listOf(
                validPhoto,
                validPhoto.copy(label = "id"),
                validPhoto.copy(label = "selfie_with_id"),
            ),
        )

        assertTrue(result.isReady)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `empty or missing passenger files cannot receive pilot access`() {
        val result = RideVerificationEvidencePolicy.evaluatePassenger(
            fullName = "María López",
            phone = "88888888",
            files = listOf(
                validPhoto,
                validPhoto.copy(label = "id", byteCount = 0L),
                validPhoto.copy(label = "selfie_with_id", path = ""),
            ),
        )

        assertFalse(result.isReady)
        assertEquals(
            setOf("EMPTY_FILE:id", "MISSING_FILE:selfie_with_id"),
            result.issues.toSet(),
        )
    }

    @Test
    fun `driver evidence validates contact vehicle and all required files`() {
        val validFiles = (1..14).map {
            validPhoto.copy(label = "document_$it", path = "/private/$it.jpg")
        }
        val valid = RideVerificationEvidencePolicy.evaluateDriver(
            fullName = "Jorge Del Valle",
            phone = "72812570",
            email = "jorge@example.com",
            dateOfBirth = "1990-05-10",
            vehicleMake = "Hyundai",
            vehicleModel = "Accent",
            vehicleYear = 2005,
            vehicleColor = "Gris",
            vehiclePlate = "ABC123",
            currentYear = 2026,
            files = validFiles,
        )
        val invalid = RideVerificationEvidencePolicy.evaluateDriver(
            fullName = "J",
            phone = "123",
            email = "not-an-email",
            dateOfBirth = "10/05/1990",
            vehicleMake = "",
            vehicleModel = "",
            vehicleYear = 1800,
            vehicleColor = "",
            vehiclePlate = "",
            currentYear = 2026,
            files = validFiles.dropLast(1),
        )

        assertTrue(valid.isReady)
        assertFalse(invalid.isReady)
        assertTrue("INVALID_NAME" in invalid.issues)
        assertTrue("INVALID_PHONE" in invalid.issues)
        assertTrue("INVALID_EMAIL" in invalid.issues)
        assertTrue("INVALID_DATE_OF_BIRTH" in invalid.issues)
        assertTrue("INVALID_VEHICLE_YEAR" in invalid.issues)
        assertTrue("REQUIRED_FILE_COUNT:14" in invalid.issues)
    }
}
