package com.elysium369.meet.ecu

import com.elysium369.meet.ecu.domain.ArtifactImmutabilityState
import com.elysium369.meet.ecu.domain.FirmwareArtifactType
import com.elysium369.meet.ecu.vault.DoubleReadVerificationResult
import com.elysium369.meet.ecu.vault.FirmwareVaultManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleReadBackupTest {

    @Test
    fun `identical double-read passes byte-by-byte comparison and creates immutable original artifact`() {
        val readA = ByteArray(4096) { (it % 256).toByte() }
        val readB = readA.clone()

        val result = FirmwareVaultManager.verifyDoubleReadAndStore(
            readPassA = readA,
            readPassB = readB,
            vehicleId = "VEH-12345",
            ecuFingerprint = "BOSCH_ME7:0261206123:1037359123:CAL_01",
            storagePath = "vault/originals/ORIG_01.bin"
        )

        assertTrue(result is DoubleReadVerificationResult.Verified)
        val verified = result as DoubleReadVerificationResult.Verified
        assertEquals(4096L, verified.artifact.byteLength)
        assertEquals(FirmwareArtifactType.ORIGINAL_READBACK, verified.artifact.artifactType)
        assertEquals(ArtifactImmutabilityState.FROZEN_IMMUTABLE, verified.artifact.immutabilityState)
    }

    @Test
    fun `single byte difference between read passes flags mismatch and rejects backup`() {
        val readA = ByteArray(4096) { 0x55.toByte() }
        val readB = readA.clone()
        readB[2048] = 0xAA.toByte() // Injected bitflip / transient read error

        val result = FirmwareVaultManager.verifyDoubleReadAndStore(
            readPassA = readA,
            readPassB = readB,
            vehicleId = "VEH-12345",
            ecuFingerprint = "BOSCH_ME7:0261206123:1037359123:CAL_01",
            storagePath = "vault/originals/ORIG_01.bin"
        )

        assertTrue(result is DoubleReadVerificationResult.Mismatch)
        val mismatch = result as DoubleReadVerificationResult.Mismatch
        assertEquals(2048, mismatch.firstDifferenceOffset)
        assertEquals(4096, mismatch.totalBytesCompared)
    }

    @Test
    fun `size mismatch between read passes is rejected`() {
        val readA = ByteArray(4096)
        val readB = ByteArray(2048) // Truncated read

        val result = FirmwareVaultManager.verifyDoubleReadAndStore(
            readPassA = readA,
            readPassB = readB,
            vehicleId = "VEH-12345",
            ecuFingerprint = "BOSCH_ME7:0261206123:1037359123:CAL_01",
            storagePath = "vault/originals/ORIG_01.bin"
        )

        assertTrue(result is DoubleReadVerificationResult.SizeMismatch)
    }
}
