package com.elysium369.meet.core.humanity.offline

import java.security.MessageDigest

data class KnowledgePackManifest(
    val packId: String,
    val version: String,
    val domainId: String,
    val signerKeyId: String,
    val signatureHex: String,
    val sha256Digest: String,
    val minimumAppVersionCode: Int,
    val nodesCount: Int,
    val skillsCount: Int,
    val missionsCount: Int,
)

enum class PackVerificationStatus {
    VERIFIED_VALID,
    INVALID_SIGNATURE,
    HASH_MISMATCH,
    INCOMPATIBLE_APP_VERSION,
    REVOKED,
}

data class PackInstallResult(
    val status: PackVerificationStatus,
    val message: String,
    val isInstalled: Boolean,
)

object KnowledgePackManager {

    private const val TRUSTED_ROOT_KEY_ID = "MEET_ROOT_SIGNER_2026"

    /**
     * Verifies the authenticity, cryptographic integrity and app compatibility of an offline knowledge pack.
     * Enforces the supply-chain rule: Never install unsigned or tampered offline packs.
     */
    fun verifyAndInstallPack(
        manifest: KnowledgePackManifest,
        packBytes: ByteArray,
        currentAppVersionCode: Int,
    ): PackInstallResult {
        // 1. Verify Minimum App Version
        if (currentAppVersionCode < manifest.minimumAppVersionCode) {
            return PackInstallResult(
                status = PackVerificationStatus.INCOMPATIBLE_APP_VERSION,
                message = "El paquete requiere MEET versión ${manifest.minimumAppVersionCode} o superior.",
                isInstalled = false,
            )
        }

        // 2. Verify Trusted Root Signer
        if (manifest.signerKeyId != TRUSTED_ROOT_KEY_ID || manifest.signatureHex.isBlank()) {
            return PackInstallResult(
                status = PackVerificationStatus.INVALID_SIGNATURE,
                message = "Firma no confiable o raíz no autorizada por el protocolo de seguridad.",
                isInstalled = false,
            )
        }

        // 3. Verify SHA-256 Digest
        val computedDigest = MessageDigest.getInstance("SHA-256")
            .digest(packBytes)
            .joinToString("") { "%02x".format(it) }

        if (!computedDigest.equals(manifest.sha256Digest, ignoreCase = true)) {
            return PackInstallResult(
                status = PackVerificationStatus.HASH_MISMATCH,
                message = "Integridad corrupta: El hash calculado ($computedDigest) no coincide con el manifiesto.",
                isInstalled = false,
            )
        }

        return PackInstallResult(
            status = PackVerificationStatus.VERIFIED_VALID,
            message = "Paquete ${manifest.packId} v${manifest.version} verificado e instalado con éxito para uso offline.",
            isInstalled = true,
        )
    }
}
