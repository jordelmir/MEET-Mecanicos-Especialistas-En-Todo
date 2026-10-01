package com.elysium.nexus.fabric.profile

import com.elysium.nexus.core.device.InstalledIrProfile
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCommandBinding
import com.elysium.nexus.core.device.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class InstalledIrProfileRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storageDir: File
    private lateinit var repository: InstalledIrProfileRepository

    @Before
    fun setUp() {
        storageDir = tempFolder.newFolder("profiles_test")
        repository = InstalledIrProfileRepository(storageDir)
    }

    @Test
    fun saveAndGetProfile_persistsInMemory_WithNoAndroidContext() {
        val binding = IrCommandBinding(
            signalId = "nec-lg-vol-up",
            physicalFingerprint = "7f8a9b1c2d3e4f",
            sourceId = "flipper-irdb",
            action = IrAction.VOLUME_UP
        )

        val profile = InstalledIrProfile(
            id = "test-profile-1",
            displayName = "LG Smart TV Remote",
            brand = "LG",
            deviceType = "TV",
            model = "OLED55C3",
            remoteModel = "AKB75095307",
            codeSetId = "code-set-lg-001",
            sourceRevision = "v0.3.0",
            commands = mapOf(IrAction.VOLUME_UP to binding),
            verifiedActions = setOf(IrAction.VOLUME_UP),
            verificationStatus = VerificationStatus.VERIFIED_LAB
        )

        val result = repository.saveProfile(profile, verifiedActions = setOf(IrAction.VOLUME_UP))
        assertTrue("Expected Saved without an Android context", result is SaveProfileResult.Saved)

        // Without an Android Context the repository is in-memory only (Room is the
        // persistent authority on-device; covered by device tests + RoomProfileRepositoryTest).
        val retrieved = repository.getProfile("test-profile-1")
        assertEquals("LG Smart TV Remote", retrieved?.displayName)
        assertEquals("LG", retrieved?.brand)
        assertEquals("code-set-lg-001", retrieved?.codeSetId)
        assertEquals(1, retrieved?.commands?.size)
        assertEquals("nec-lg-vol-up", retrieved?.commands?.get(IrAction.VOLUME_UP)?.signalId)
        assertEquals(VerificationStatus.VERIFIED_LAB, retrieved?.verificationStatus)
    }

    @Test
    fun saveProfile_rejectsBlankCodeSet_failClosed() {
        val profile = InstalledIrProfile(
            id = "test-invalid",
            displayName = "Invalid Remote",
            brand = "Sony",
            codeSetId = "",
            commands = emptyMap()
        )
        val result = repository.saveProfile(profile)
        assertTrue(result is SaveProfileResult.ValidationFailure)
    }

    @Test
    fun deleteProfile_removesFromMemoryCache() {
        val profile = InstalledIrProfile(
            id = "test-profile-to-delete",
            displayName = "Delete Me Remote",
            brand = "Sony",
            codeSetId = "sony-001",
            commands = emptyMap()
        )
        val profileWithCommand = profile.copy(
            commands = mapOf(
                IrAction.VOLUME_UP to IrCommandBinding(
                    signalId = "sony-vol-up",
                    physicalFingerprint = "abc123",
                    sourceId = "flipper-irdb",
                    action = IrAction.VOLUME_UP
                )
            )
        )

        val saved = repository.saveProfile(profileWithCommand)
        assertTrue(saved is SaveProfileResult.Saved)
        assertNotNull(repository.getProfile("test-profile-to-delete"))

        val deleted = repository.deleteProfile("test-profile-to-delete")
        assertTrue(deleted)
        assertNull(repository.getProfile("test-profile-to-delete"))

        // Confirm deletion is durable in a fresh in-memory repository
        val reloadedRepository = InstalledIrProfileRepository(storageDir)
        assertNull(reloadedRepository.getProfile("test-profile-to-delete"))
    }
}
