package com.elysium.nexus.fabric.infrared.promotion

import com.elysium.nexus.fabric.infrared.database.model.PhysicalTestEvidence
import com.elysium.nexus.fabric.infrared.database.model.RetailCompatibilityCertificate
import com.elysium.nexus.fabric.infrared.database.model.RetailerName
import com.elysium.nexus.fabric.infrared.database.model.RetailerSku
import java.security.MessageDigest

/**
 * Phase 19 — Compatibility Certificate Engine
 *
 * Issues cryptographically verifiable Retail Compatibility Certificates.
 * A certificate can ONLY be generated when core actions (POWER_TOGGLE/POWER_ON, VOLUME_UP, VOLUME_DOWN, MUTE, INPUT_SELECT)
 * have verified physical evidence.
 */
object CompatibilityCertificateEngine {

    val REQUIRED_CORE_ACTIONS = setOf("POWER_TOGGLE", "VOLUME_UP", "VOLUME_DOWN", "MUTE", "INPUT_SELECT")

    /**
     * Issues a digital compatibility certificate for an exact retailer SKU if all core actions pass physical evidence verification.
     * Returns null if any core action lacks physical evidence.
     */
    fun issueCertificate(
        retailerSku: RetailerSku,
        evidenceList: List<PhysicalTestEvidence>,
        signerSecret: String = "ELYSIUM_NEXUS_RETAIL_TRUTH_KEY_2026"
    ): RetailCompatibilityCertificate? {
        if (evidenceList.isEmpty()) return null

        val verifiedActions = evidenceList.map { it.actionKey }.toSet()
        val missingActions = REQUIRED_CORE_ACTIONS - verifiedActions
        if (missingActions.isNotEmpty()) return null

        val evidenceShaList = evidenceList.map { it.physicalSha256 }.sorted()
        val rawPayload = "${retailerSku.retailer}:${retailerSku.skuCode}:${retailerSku.mpn}:${evidenceShaList.joinToString(",")}"

        val digest = MessageDigest.getInstance("SHA-256")
        val signatureBytes = digest.digest((rawPayload + signerSecret).toByteArray(Charsets.UTF_8))
        val digitalSignature = signatureBytes.joinToString("") { "%02x".format(it) }

        return RetailCompatibilityCertificate(
            certificateId = "CERT-${retailerSku.retailer}-${retailerSku.skuCode}",
            retailer = retailerSku.retailer,
            skuCode = retailerSku.skuCode,
            exactMpn = retailerSku.mpn,
            coreActionsVerified = verifiedActions,
            extendedActionsVerified = emptySet(),
            physicalEvidenceShaList = evidenceShaList,
            verifiedAtTimestamp = System.currentTimeMillis(),
            digitalSignature = digitalSignature
        )
    }

    /**
     * Verifies digital signature of a certificate.
     */
    fun verifyCertificate(
        cert: RetailCompatibilityCertificate,
        signerSecret: String = "ELYSIUM_NEXUS_RETAIL_TRUTH_KEY_2026"
    ): Boolean {
        val rawPayload = "${cert.retailer}:${cert.skuCode}:${cert.exactMpn}:${cert.physicalEvidenceShaList.sorted().joinToString(",")}"
        val digest = MessageDigest.getInstance("SHA-256")
        val expectedBytes = digest.digest((rawPayload + signerSecret).toByteArray(Charsets.UTF_8))
        val expectedSignature = expectedBytes.joinToString("") { "%02x".format(it) }
        return cert.digitalSignature == expectedSignature
    }
}
