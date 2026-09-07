package com.elysium369.meet.education.domain

import java.security.MessageDigest

data class CertifiedCompetencyDiploma(
    val diplomaId: String,
    val recipientPrincipalId: String,
    val trackTitle: String,
    val trackCode: String,
    val academicCycle: String,
    val masteryEstimate: Double,
    val confidence: Double,
    val totalEvidenceCount: Int,
    val merkleRootHash: String,
    val issuedAtEpochMs: Long,
    val qrPayload: String
)

/**
 * Servicio de Generación y Validación de Diplomas Criptográficos Certificados.
 * Cumple con la Regla 4 de AGENTS.md:
 * Payload QR minimal de 6 campos (report_id, integrity_hash, vehicle_or_user_id, generated_at, report_type, verifier_url).
 */
object ElysiumDiplomaService {

    private const val VERIFIER_BASE_URL = "https://elysium369.meet/verify"

    /**
     * Calcula la raíz del árbol de Merkle a partir de los hashes SHA-256 de las evidencias de aprendizaje.
     */
    fun computeMerkleRoot(evidenceHashes: List<String>): String {
        if (evidenceHashes.isEmpty()) {
            return sha256Hex("ELYSIUM_EMPTY_EVIDENCE_TREE")
        }
        var currentLevel = evidenceHashes.map { it.lowercase().trim() }.sorted()

        while (currentLevel.size > 1) {
            val nextLevel = mutableListOf<String>()
            for (i in currentLevel.indices step 2) {
                if (i + 1 < currentLevel.size) {
                    val combined = currentLevel[i] + currentLevel[i + 1]
                    nextLevel.add(sha256Hex(combined))
                } else {
                    // Elemento impar: duplicar según convención Bitcoin/Merkle
                    val combined = currentLevel[i] + currentLevel[i]
                    nextLevel.add(sha256Hex(combined))
                }
            }
            currentLevel = nextLevel
        }
        return currentLevel.first()
    }

    /**
     * Emite un Diploma Criptográfico Certificado si el estudiante o técnico supera el umbral de maestría (> 0.75).
     */
    fun mintDiploma(
        principalId: String,
        trackTitle: String,
        trackCode: String,
        academicCycle: String,
        mastery: Double,
        confidence: Double,
        evidenceHashes: List<String>,
        issuedAtMs: Long = System.currentTimeMillis()
    ): CertifiedCompetencyDiploma {
        require(mastery >= 0.75) { "Maestría insuficiente ($mastery) para certificar diploma. Requiere >= 0.75" }
        require(evidenceHashes.isNotEmpty()) { "Se requiere al menos una evidencia criptográfica para acuñar el diploma." }

        val diplomaId = "dip_${trackCode.lowercase()}_${principalId.take(8)}_${issuedAtMs}"
        val merkleRoot = computeMerkleRoot(evidenceHashes)

        // AGENTS.md Regla 4: 6-field minimal payload
        // (report_id, integrity_hash, vehicle_id, generated_at, report_type, verifier_url)
        val qrPayload = "$diplomaId|$merkleRoot|$principalId|$issuedAtMs|EDUCATION_CERTIFICATE|$VERIFIER_BASE_URL?id=$diplomaId"

        return CertifiedCompetencyDiploma(
            diplomaId = diplomaId,
            recipientPrincipalId = principalId,
            trackTitle = trackTitle,
            trackCode = trackCode,
            academicCycle = academicCycle,
            masteryEstimate = mastery,
            confidence = confidence,
            totalEvidenceCount = evidenceHashes.size,
            merkleRootHash = merkleRoot,
            issuedAtEpochMs = issuedAtMs,
            qrPayload = qrPayload
        )
    }

    /**
     * Valida de forma forense la integridad matemática del diploma.
     */
    fun verifyDiploma(diploma: CertifiedCompetencyDiploma, evidenceHashes: List<String>): Boolean {
        if (diploma.totalEvidenceCount != evidenceHashes.size) return false
        val expectedMerkle = computeMerkleRoot(evidenceHashes)
        return diploma.merkleRootHash.equals(expectedMerkle, ignoreCase = true)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
