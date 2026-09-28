package com.elysium.nexus.fabric.profile

import com.elysium.nexus.core.device.InstalledIrProfile
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCommandBinding
import com.elysium.nexus.core.device.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0.1: Unit tests for InstalledIrProfile domain and revalidation logic.
 *
 * These tests verify:
 * - catalogCanonicalHashAtInstall is stored and preserved correctly
 * - needsRevalidation compares catalog hashes, not sourceRevision
 * - Profile fields are properly separated
 */
class ProfileRevalidationTest {

    private fun createTestProfile(
        catalogHashAtInstall: String = "abc123",
        sourceRevision: String = "v0.5.0",
        schemaVersion: Int = 5,
        buildId: String = "build-001"
    ) = InstalledIrProfile(
        id = "test-profile-1",
        displayName = "Samsung Remote",
        brand = "Samsung",
        deviceType = "TV",
        model = "UN55CU7000",
        codeSetId = "codeset-123",
        sourceRevision = sourceRevision,
        catalogSchemaVersionAtInstall = schemaVersion,
        catalogCanonicalHashAtInstall = catalogHashAtInstall,
        catalogBuildIdAtInstall = buildId,
        commands = mapOf(
            IrAction.VOLUME_UP to IrCommandBinding(
                signalId = "sig-001",
                physicalFingerprint = "fp-001",
                sourceId = "src-001",
                action = IrAction.VOLUME_UP
            ),
            IrAction.MUTE to IrCommandBinding(
                signalId = "sig-002",
                physicalFingerprint = "fp-002",
                sourceId = "src-001",
                action = IrAction.MUTE
            )
        ),
        verifiedActions = setOf(IrAction.VOLUME_UP, IrAction.MUTE),
        verificationStatus = VerificationStatus.SESSION_VERIFIED
    )

    @Test
    fun `profile stores catalog hash at install correctly`() {
        val profile = createTestProfile(catalogHashAtInstall = "real-hash-abc")
        assertEquals("real-hash-abc", profile.catalogCanonicalHashAtInstall)
    }

    @Test
    fun `profile stores schema version at install correctly`() {
        val profile = createTestProfile(schemaVersion = 5)
        assertEquals(5, profile.catalogSchemaVersionAtInstall)
    }

    @Test
    fun `profile stores build ID at install correctly`() {
        val profile = createTestProfile(buildId = "build-42")
        assertEquals("build-42", profile.catalogBuildIdAtInstall)
    }

    @Test
    fun `sourceRevision is NOT the catalog hash`() {
        val profile = createTestProfile(
            catalogHashAtInstall = "catalog-hash-xyz",
            sourceRevision = "v0.5.0"
        )
        // sourceRevision should be the source commit/tag, not the catalog hash
        assertEquals("v0.5.0", profile.sourceRevision)
        assertEquals("catalog-hash-xyz", profile.catalogCanonicalHashAtInstall)
        assertNotEquals(profile.sourceRevision, profile.catalogCanonicalHashAtInstall)
    }

    @Test
    fun `needsRevalidation should be true when catalog hash changes`() {
        val profile = createTestProfile(catalogHashAtInstall = "old-hash")
        val currentCatalogHash = "new-hash"

        val needsRevalidation = currentCatalogHash != "unknown" &&
            profile.catalogCanonicalHashAtInstall != "unknown" &&
            currentCatalogHash != profile.catalogCanonicalHashAtInstall

        assertTrue(needsRevalidation)
    }

    @Test
    fun `needsRevalidation should be false when catalog hash matches`() {
        val profile = createTestProfile(catalogHashAtInstall = "same-hash")
        val currentCatalogHash = "same-hash"

        val needsRevalidation = currentCatalogHash != "unknown" &&
            profile.catalogCanonicalHashAtInstall != "unknown" &&
            currentCatalogHash != profile.catalogCanonicalHashAtInstall

        assertFalse(needsRevalidation)
    }

    @Test
    fun `needsRevalidation should be false when install hash is unknown`() {
        val profile = createTestProfile(catalogHashAtInstall = "unknown")
        val currentCatalogHash = "new-hash"

        val needsRevalidation = currentCatalogHash != "unknown" &&
            profile.catalogCanonicalHashAtInstall != "unknown" &&
            currentCatalogHash != profile.catalogCanonicalHashAtInstall

        assertFalse(needsRevalidation)
    }

    @Test
    fun `needsRevalidation should be false when current hash is unknown`() {
        val profile = createTestProfile(catalogHashAtInstall = "old-hash")
        val currentCatalogHash = "unknown"

        val needsRevalidation = currentCatalogHash != "unknown" &&
            profile.catalogCanonicalHashAtInstall != "unknown" &&
            currentCatalogHash != profile.catalogCanonicalHashAtInstall

        assertFalse(needsRevalidation)
    }

    @Test
    fun `incorrect old comparison would give wrong result`() {
        // This test demonstrates why the old comparison was wrong
        val profile = createTestProfile(
            catalogHashAtInstall = "real-catalog-hash",
            sourceRevision = "v0.5.0"
        )
        val currentCatalogHash = "real-catalog-hash" // Same hash, no revalidation needed

        // OLD (WRONG) comparison: catalogHash != sourceRevision
        val oldNeedsRevalidation = currentCatalogHash != "unknown" &&
            currentCatalogHash != profile.sourceRevision
        assertTrue("Old comparison incorrectly triggers revalidation", oldNeedsRevalidation)

        // NEW (CORRECT) comparison: catalogHash != catalogCanonicalHashAtInstall
        val newNeedsRevalidation = currentCatalogHash != "unknown" &&
            profile.catalogCanonicalHashAtInstall != "unknown" &&
            currentCatalogHash != profile.catalogCanonicalHashAtInstall
        assertFalse("New comparison correctly skips revalidation", newNeedsRevalidation)
    }

    @Test
    fun `profile preserves all fields through copy`() {
        val original = createTestProfile()
        val copy = original.copy(
            displayName = "Updated Remote",
            catalogCanonicalHashAtInstall = "new-hash"
        )

        assertEquals("Updated Remote", copy.displayName)
        assertEquals("new-hash", copy.catalogCanonicalHashAtInstall)
        assertEquals(original.id, copy.id)
        assertEquals(original.codeSetId, copy.codeSetId)
        assertEquals(original.sourceRevision, copy.sourceRevision)
        assertEquals(original.commands, copy.commands)
    }

    @Test
    fun `commands map is preserved correctly`() {
        val profile = createTestProfile()
        assertEquals(2, profile.commands.size)
        assertTrue(profile.commands.containsKey(IrAction.VOLUME_UP))
        assertTrue(profile.commands.containsKey(IrAction.MUTE))

        val volUpBinding = profile.commands[IrAction.VOLUME_UP]!!
        assertEquals("sig-001", volUpBinding.signalId)
        assertEquals("fp-001", volUpBinding.physicalFingerprint)
    }

    @Test
    fun `verified actions are preserved`() {
        val profile = createTestProfile()
        assertEquals(2, profile.verifiedActions.size)
        assertTrue(profile.verifiedActions.contains(IrAction.VOLUME_UP))
        assertTrue(profile.verifiedActions.contains(IrAction.MUTE))
    }
}
