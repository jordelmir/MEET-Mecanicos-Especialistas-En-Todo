package com.elysium369.meet.core.diagnostics

import com.elysium369.meet.core.obd.CanonicalJson
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class SignedCalibrationArtifact(
    val datasetId: String,
    val version: String,
    val datasetHash: String,
    val methodologyVersion: String,
    val scope: String,
    val trainingCutoffMs: Long,
    val holdoutHash: String,
    val sampleCount: Int,
    val metrics: Map<String, Double>,
    val issuer: String,
    val keyId: String,
    val reviewState: String,
    val signatureBase64: String,
) {
    init {
        require(datasetId.isNotBlank() && version.isNotBlank() && methodologyVersion.isNotBlank())
        require(datasetHash.matches(Regex("^[0-9a-fA-F]{64}$")))
        require(holdoutHash.matches(Regex("^[0-9a-fA-F]{64}$")))
        require(sampleCount > 0 && metrics.values.all(Double::isFinite))
    }

    fun canonicalBytes(): ByteArray = CanonicalJson.encode(
        mapOf(
            "datasetHash" to datasetHash.lowercase(),
            "datasetId" to datasetId,
            "holdoutHash" to holdoutHash.lowercase(),
            "issuer" to issuer,
            "keyId" to keyId,
            "methodologyVersion" to methodologyVersion,
            "metrics" to metrics.toSortedMap(),
            "reviewState" to reviewState,
            "sampleCount" to sampleCount,
            "scope" to scope,
            "trainingCutoffMs" to trainingCutoffMs,
            "version" to version,
        ),
    )
}

interface CalibrationTrustRegistry {
    fun authorize(artifact: SignedCalibrationArtifact?): Boolean

    object DenyAll : CalibrationTrustRegistry {
        override fun authorize(artifact: SignedCalibrationArtifact?): Boolean = false
    }
}

/** Registry can only be built from reviewed keys; unknown/revoked artifacts fail closed. */
class ReviewedCalibrationTrustRegistry private constructor(
    private val trustedKeys: Map<String, ByteArray>,
    private val revokedDatasetIds: Set<String>,
) : CalibrationTrustRegistry {
    override fun authorize(artifact: SignedCalibrationArtifact?): Boolean {
        artifact ?: return false
        if (artifact.datasetId in revokedDatasetIds || artifact.reviewState != "APPROVED") return false
        val publicKey = trustedKeys[artifact.keyId] ?: return false
        val digest = MessageDigest.getInstance("SHA-256").digest(
            "MEET-CALIBRATION-ARTIFACT-V1".toByteArray(Charsets.US_ASCII) +
                byteArrayOf(0) + artifact.canonicalBytes(),
        )
        return runCatching {
            val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKey))
            Signature.getInstance("NONEwithECDSA").run {
                initVerify(key)
                update(digest)
                verify(Base64.getDecoder().decode(artifact.signatureBase64))
            }
        }.getOrDefault(false)
    }

    companion object {
        internal fun fromReviewedTrust(
            trustedKeys: Map<String, ByteArray>,
            revokedDatasetIds: Set<String>,
        ): ReviewedCalibrationTrustRegistry = ReviewedCalibrationTrustRegistry(
            trustedKeys.mapValues { it.value.copyOf() },
            revokedDatasetIds.toSet(),
        )
    }
}
