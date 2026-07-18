package com.elysium369.meet.core.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AccentVehicleProfilePackTest {
    private val importer = KnowledgePackImporter()

    @Test
    fun `Accent 2005 profile imports with conservative applicability`() {
        val pack = importer.parse(loadAsset()).getOrThrow()
        val result = importer.importPack(pack)

        assertTrue("Import failed: $result", result is PackImportResult.Success)
        assertEquals(
            PackImportResult.PublicationStatus.REVIEW_REQUIRED,
            (result as PackImportResult.Success).publicationStatus
        )
        assertEquals(1, pack.vehicleProfiles.size)
        assertTrue(pack.vehicleProfiles.single().requiresVinConfirmation)
        assertEquals(
            "baf4add3f22202fc7d66f7b7f4aee549d90780f1891da6fa66ffbc2db1820824",
            pack.sourceCitations.single().contentHash
        )
        assertTrue(pack.measurementSpecifications.isEmpty())
        assertTrue(pack.technicalClaims.none { it.confidence == ConfidenceLevel.VERIFIED })
    }

    @Test
    fun `MAF not documented never becomes a target vehicle fact`() {
        val pack = importer.parse(loadAsset()).getOrThrow()
        val claim = pack.technicalClaims.single { it.claimId == "claim_maf_not_documented" }
        val resolution = AutomotiveApplicabilityResolver().resolve(
            claim,
            ApplicabilityEvidence(
                vinConfirmed = true,
                oemConfirmed = true,
                visualConfirmed = true,
                physicalEvidenceIds = claim.evidenceRequired.toSet()
            )
        )

        assertEquals(ApplicabilityStatus.UNKNOWN_INSUFFICIENT_EVIDENCE, resolution.effectiveStatus)
        assertFalse(resolution.canUseAsVehicleFact)
    }

    @Test
    fun `later Accent OCS remains reference only`() {
        val pack = importer.parse(loadAsset()).getOrThrow()
        val claim = pack.technicalClaims.single {
            it.claimId == "claim_ocs_later_accent_reference_only"
        }
        val resolution = AutomotiveApplicabilityResolver().resolve(
            claim,
            ApplicabilityEvidence(
                vinConfirmed = true,
                oemConfirmed = true,
                visualConfirmed = true
            )
        )

        assertEquals(ApplicabilityStatus.REFERENCE_VEHICLE_ONLY, resolution.effectiveStatus)
        assertFalse(resolution.canUseAsVehicleFact)
    }

    @Test
    fun `EPS documented absence still requires independent confirmation`() {
        val pack = importer.parse(loadAsset()).getOrThrow()
        val claim = pack.technicalClaims.single {
            it.claimId == "claim_eps_absent_documented"
        }

        assertEquals(ConfidenceLevel.MEDIUM, claim.confidence)
        assertEquals(ApplicabilityStatus.ABSENT_DOCUMENTED, claim.applicability)
        assertTrue(claim.evidenceRequired.contains("oem_steering_diagram"))
        assertTrue(claim.requiresVinConfirmation)
        assertTrue(claim.requiresVisualConfirmation)
    }

    private fun loadAsset(): String {
        val file = File(
            "../app/src/main/assets/knowledge/packs/" +
                "pack_07_hyundai_accent_verna_2005_profile.json"
        )
        require(file.exists()) { "Asset not found at ${file.path}" }
        return file.readText()
    }
}
