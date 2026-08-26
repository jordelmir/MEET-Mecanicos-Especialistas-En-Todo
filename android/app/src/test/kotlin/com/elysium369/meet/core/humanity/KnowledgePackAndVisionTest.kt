package com.elysium369.meet.core.humanity

import com.elysium369.meet.core.humanity.offline.KnowledgePackManager
import com.elysium369.meet.core.humanity.offline.KnowledgePackManifest
import com.elysium369.meet.core.humanity.offline.PackVerificationStatus
import com.elysium369.meet.core.humanity.vision.VisualComponentVerifier
import com.elysium369.meet.core.humanity.vision.VisualDetectionCandidate
import com.elysium369.meet.core.humanity.vision.VisualVerificationStatus
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class KnowledgePackAndVisionTest {

    @Test
    fun `valid knowledge pack installs successfully`() {
        val payload = "PACK_PAYLOAD_CONTENT_V1_DOMAINS_NODES".toByteArray()
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(payload)
            .joinToString("") { "%02x".format(it) }

        val manifest = KnowledgePackManifest(
            packId = "pack.automotive.fundamentals",
            version = "1.0.0",
            domainId = "domain.automotive.fundamentals",
            signerKeyId = "MEET_ROOT_SIGNER_2026",
            signatureHex = "sig_valid_root_369",
            sha256Digest = hash,
            minimumAppVersionCode = 46,
            nodesCount = 25,
            skillsCount = 8,
            missionsCount = 4,
        )

        val result = KnowledgePackManager.verifyAndInstallPack(manifest, payload, currentAppVersionCode = 47)

        assertTrue(result.isInstalled)
        assertEquals(PackVerificationStatus.VERIFIED_VALID, result.status)
    }

    @Test
    fun `tampered knowledge pack payload is rejected on hash mismatch`() {
        val realPayload = "REAL_UNMODIFIED_CONTENT".toByteArray()
        val tamperedPayload = "TAMPERED_INJECTED_CONTENT".toByteArray()
        val realHash = MessageDigest.getInstance("SHA-256")
            .digest(realPayload)
            .joinToString("") { "%02x".format(it) }

        val manifest = KnowledgePackManifest(
            packId = "pack.automotive.fundamentals",
            version = "1.0.0",
            domainId = "domain.automotive.fundamentals",
            signerKeyId = "MEET_ROOT_SIGNER_2026",
            signatureHex = "sig_valid_root_369",
            sha256Digest = realHash,
            minimumAppVersionCode = 46,
            nodesCount = 25,
            skillsCount = 8,
            missionsCount = 4,
        )

        val result = KnowledgePackManager.verifyAndInstallPack(manifest, tamperedPayload, currentAppVersionCode = 47)

        assertFalse(result.isInstalled)
        assertEquals(PackVerificationStatus.HASH_MISMATCH, result.status)
    }

    @Test
    fun `visual detection with high confidence without VIN match outputs identification not verified`() {
        val candidate = VisualDetectionCandidate(
            detectedLabel = "Ignition Coil Pack (Bobina)",
            modelConfidencePct = 84,
            boundingBox = "0.2,0.3,0.8,0.9",
            visualFeaturesNote = "Conector de 3 pines, forma rectangular",
        )

        val result = VisualComponentVerifier.evaluateVisualDetection(candidate, hasOemVinMatch = false)

        assertEquals(VisualVerificationStatus.IDENTIFICATION_NOT_VERIFIED, result.status)
        assertEquals(TruthState.ESTIMATED, result.truthState)
        assertTrue(result.requiresPhysicalConfirmation)
        assertTrue(result.disclaimer.contains("Requiere confirmación"))
    }

    @Test
    fun `visual detection with oem vin match outputs exact verification`() {
        val candidate = VisualDetectionCandidate(
            detectedLabel = "Bobina OEM 27301-26640",
            modelConfidencePct = 95,
            boundingBox = "0.2,0.3,0.8,0.9",
            visualFeaturesNote = "Número de parte visible y cotejado",
        )

        val result = VisualComponentVerifier.evaluateVisualDetection(candidate, hasOemVinMatch = true)

        assertEquals(VisualVerificationStatus.OEM_VERIFIED_EXACT, result.status)
        assertEquals(TruthState.AUTHORITATIVE, result.truthState)
        assertFalse(result.requiresPhysicalConfirmation)
    }
}
