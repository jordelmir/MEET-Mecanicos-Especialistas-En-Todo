package com.elysium369.meet.ecu.vault

import com.elysium369.meet.ecu.domain.ArtifactImmutabilityState
import com.elysium369.meet.ecu.domain.FirmwareArtifact
import com.elysium369.meet.ecu.domain.FirmwareArtifactType
import java.security.MessageDigest
import java.util.Arrays

/**
 * Section 28 & 56: Firmware Vault & Double-Read Verification Engine.
 *
 * DOCTRINE:
 * Original readback firmware is permanently frozen and immutable.
 * High-risk programming requires double-read verification (Pass A vs Pass B)
 * before committing original backup into vault.
 */

sealed interface DoubleReadVerificationResult {
    data class Verified(val sha256: String, val artifact: FirmwareArtifact) : DoubleReadVerificationResult
    data class Mismatch(val firstDifferenceOffset: Int, val totalBytesCompared: Int) : DoubleReadVerificationResult
    data class SizeMismatch(val sizeA: Int, val sizeB: Int) : DoubleReadVerificationResult
    data class Invalid(val reason: String) : DoubleReadVerificationResult
}

object FirmwareVaultManager {

    fun computeSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun verifyDoubleReadAndStore(
        readPassA: ByteArray,
        readPassB: ByteArray,
        vehicleId: String,
        ecuFingerprint: String,
        storagePath: String,
    ): DoubleReadVerificationResult {
        if (readPassA.isEmpty()) return DoubleReadVerificationResult.Invalid("Read pass A is empty")
        if (readPassB.isEmpty()) return DoubleReadVerificationResult.Invalid("Read pass B is empty")

        if (readPassA.size != readPassB.size) {
            return DoubleReadVerificationResult.SizeMismatch(readPassA.size, readPassB.size)
        }

        // Byte-by-byte comparison
        for (i in readPassA.indices) {
            if (readPassA[i] != readPassB[i]) {
                return DoubleReadVerificationResult.Mismatch(
                    firstDifferenceOffset = i,
                    totalBytesCompared = readPassA.size
                )
            }
        }

        val sha256 = computeSha256(readPassA)
        val artifact = FirmwareArtifact(
            artifactId = "ORIG_${System.currentTimeMillis()}_${sha256.take(8).uppercase()}",
            vehicleId = vehicleId,
            ecuFingerprint = ecuFingerprint,
            artifactType = FirmwareArtifactType.ORIGINAL_READBACK,
            format = "RAW_BINARY",
            byteLength = readPassA.size.toLong(),
            sha256 = sha256,
            parentArtifactId = null,
            baselineHash = null,
            immutabilityState = ArtifactImmutabilityState.FROZEN_IMMUTABLE,
            storageReference = storagePath,
        )

        return DoubleReadVerificationResult.Verified(sha256, artifact)
    }
}
