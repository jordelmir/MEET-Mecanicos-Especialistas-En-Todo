package com.elysium.nexus.fabric.profile

import com.elysium.nexus.core.device.CodeProvenance
import com.elysium.nexus.core.device.EvidenceLevel
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrCommandBinding
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.core.device.InstalledIrProfile
import com.elysium.nexus.core.device.SelectedCommandBinding
import com.elysium.nexus.core.device.VerificationStatus
import com.elysium.nexus.fabric.infrared.IrProbeEngine
import com.elysium.nexus.fabric.infrared.IrProtocol
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V06-PHASE 2 — Revalidation upgrade/downgrade decision tests.
 *
 * Proves ProfileRevalidationService against a fake [RevalidationCatalog]
 * and fake store (no Room, no Context):
 * - catalog upgrade: identical signals → Keep, apply refreshes hash
 * - catalog upgrade with physical-equivalent signal → Migrate
 * - catalog downgrade: signal removed → NeedsRevalidation
 * - catalog downgrade: codeSet removed → NeedsRevalidation
 * - applyRevalidation refuses invalid results
 */
class ProfileRevalidationServiceUpgradeTest {

    private class FakeCatalog(
        private val signals: Map<String, IrSignal>,
        private val codeSets: Map<String, IrCodeSet>
    ) : RevalidationCatalog {
        override suspend fun getSignal(signalId: String): IrSignal? = signals[signalId]
        override suspend fun getCodeSet(codeSetId: String): IrCodeSet? = codeSets[codeSetId]
        override suspend fun resolveExecutableSignal(
            signalId: String,
            policy: com.elysium.nexus.fabric.infrared.RuntimePolicy
        ): IrSignal? = signals[signalId]
    }

    private class FakeStore : ProfileRevalidationStore {
        var savedProfile: InstalledIrProfile? = null
        var savedActions: Set<IrAction> = emptySet()
        override suspend fun saveProfile(profile: InstalledIrProfile, verifiedActions: Set<IrAction>) {
            savedProfile = profile
            savedActions = verifiedActions
        }
    }

    private fun nec(address: Int, command: Int) = IrSignal.Encoded(
        carrierHz = 38000,
        protocol = IrProtocol.Nec,
        address = address,
        command = command
    )

    private fun selectedSignal(action: IrAction, signal: IrSignal, suffix: String) = SelectedCommandBinding(
        bindingId = "b-$suffix",
        action = action,
        signalId = "sig-$suffix",
        signal = signal,
        physicalSha256 = IrProbeEngine.fingerprintSignal(signal),
        sourceId = "src-1",
        sourceRevisionId = "rev-1",
        verificationStatus = VerificationStatus.SESSION_VERIFIED,
        evidenceLevel = EvidenceLevel.MODEL_INFERRED
    )

    private fun codeSet(id: String, entries: Map<IrAction, Pair<String, IrSignal>>) = IrCodeSet(
        id = id,
        brand = "LG",
        modelPatterns = setOf("OLED*"),
        remoteModels = setOf("MR20"),
        commands = entries.mapValues { it.value.second },
        commandSignalIds = entries.mapValues { it.value.first },
        selectedCommands = entries.mapValues { (action, pair) ->
            selectedSignal(action, pair.second, pair.first)
        },
        provenance = CodeProvenance(
            sourceName = "test",
            sourceUrl = "https://example.invalid",
            licenseSpdx = "test-only"
        )
    )

    private fun service(
        catalog: RevalidationCatalog,
        store: ProfileRevalidationStore,
        currentHash: String
    ) = ProfileRevalidationService(catalog, store) { currentHash }

    private fun binding(action: IrAction, signalId: String, fingerprint: String) = IrCommandBinding(
        signalId = signalId,
        physicalFingerprint = fingerprint,
        sourceId = "src-1",
        action = action
    )

    private fun profile(
        hashAtInstall: String,
        commands: Map<IrAction, IrCommandBinding>
    ) = InstalledIrProfile(
        id = "profile-1",
        displayName = "LG TV",
        brand = "LG",
        deviceType = "TV",
        model = "OLED55",
        codeSetId = "cs-lg-1",
        sourceRevision = "v0.4",
        catalogSchemaVersionAtInstall = 5,
        catalogCanonicalHashAtInstall = hashAtInstall,
        catalogBuildIdAtInstall = "build-040",
        commands = commands,
        verifiedActions = commands.keys,
        verificationStatus = VerificationStatus.SESSION_VERIFIED
    )

    // ════════════════════════════════════════════════════════════════
    // Upgrade scenarios
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `upgrade with identical signals keeps binding and refreshes hash on apply`() = runBlocking {
        val vol = nec(0x04, 0x10)
        val fp = IrProbeEngine.fingerprintSignal(vol)
        val catalog = FakeCatalog(
            signals = mapOf("sig-vu-1" to vol),
            codeSets = mapOf(
                "cs-lg-1" to codeSet("cs-lg-1", mapOf(IrAction.VOLUME_UP to ("vu-1" to vol)))
            )
        )
        val store = FakeStore()
        val prof = profile(
            hashAtInstall = "hash-v1",
            commands = mapOf(IrAction.VOLUME_UP to binding(IrAction.VOLUME_UP, "sig-vu-1", fp))
        )

        val svc = service(catalog, store, currentHash = "hash-v2")
        val result = svc.revalidateProfile(prof)

        assertFalse("catalog hash changed → mismatch", result.catalogHashMatches)
        assertTrue(result.allBindingsValid)
        assertTrue(result.bindingResults[IrAction.VOLUME_UP] is BindingRevalidationResult.Keep)

        val applied = svc.applyRevalidation(prof, result)
        assertEquals("hash-v2", applied.catalogCanonicalHashAtInstall)
        assertEquals("hash-v2", store.savedProfile!!.catalogCanonicalHashAtInstall)
        // keep → binding untouched
        assertEquals("sig-vu-1", applied.commands[IrAction.VOLUME_UP]!!.signalId)
    }

    @Test
    fun `upgrade with matching hash reports catalogHashMatches true`() = runBlocking {
        val vol = nec(0x04, 0x10)
        val fp = IrProbeEngine.fingerprintSignal(vol)
        val svc = service(
            FakeCatalog(signals = mapOf("sig-vu-1" to vol), codeSets = emptyMap()),
            FakeStore(),
            currentHash = "hash-same"
        )
        val prof = profile(
            hashAtInstall = "hash-same",
            commands = mapOf(IrAction.VOLUME_UP to binding(IrAction.VOLUME_UP, "sig-vu-1", fp))
        )
        val result = svc.revalidateProfile(prof)
        assertTrue(result.catalogHashMatches)
    }

    @Test
    fun `upgrade migrates binding when fingerprint changed but unique equivalent exists`() = runBlocking {
        // Profile bound to an OLD signalId whose physical code is gone.
        // New catalog selects a different signalId with the SAME physical code.
        val phys = nec(0x04, 0x10)
        val fp = IrProbeEngine.fingerprintSignal(phys)
        val catalog = FakeCatalog(
            signals = mapOf("sig-new-1" to phys),
            codeSets = mapOf(
                "cs-lg-1" to codeSet(
                    "cs-lg-1",
                    mapOf(IrAction.VOLUME_UP to ("new-1" to phys))
                )
            )
        )
        val prof = profile(
            hashAtInstall = "hash-v1",
            commands = mapOf(IrAction.VOLUME_UP to binding(IrAction.VOLUME_UP, "sig-old-1", fp))
        )

        val svc = service(catalog, FakeStore(), currentHash = "hash-v2")
        val result = svc.revalidateProfile(prof)

        // SignalId changed → MIGRATE path (codeSet exists, selectedCommands has the
        // equivalent by fingerprint)
        assertTrue(result.bindingResults[IrAction.VOLUME_UP] is BindingRevalidationResult.Migrate)
        val migrated = (result.bindingResults[IrAction.VOLUME_UP] as BindingRevalidationResult.Migrate)
            .updatedBinding
        assertEquals("sig-new-1", migrated.signalId)
    }

    @Test
    fun `upgrade with stale fingerprint and no equivalent needs revalidation`() = runBlocking {
        val new = nec(0x04, 0x11) // different command → different fingerprint
        val catalog = FakeCatalog(
            signals = mapOf("sig-new-1" to new),
            codeSets = mapOf(
                "cs-lg-1" to codeSet("cs-lg-1", mapOf(IrAction.VOLUME_UP to ("new-1" to new)))
            )
        )
        val prof = profile(
            hashAtInstall = "hash-v1",
            commands = mapOf(
                IrAction.VOLUME_UP to binding(IrAction.VOLUME_UP, "sig-new-1", "STALE-FP")
            )
        )

        val svc = service(catalog, FakeStore(), currentHash = "hash-v2")
        val result = svc.revalidateProfile(prof)

        assertTrue(result.bindingResults[IrAction.VOLUME_UP] is BindingRevalidationResult.NeedsRevalidation)
    }

    // ════════════════════════════════════════════════════════════════
    // Downgrade scenarios
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `downgrade removing signal marks NEEDS_REVALIDATION`() = runBlocking {
        val vol = nec(0x04, 0x10)
        val fp = IrProbeEngine.fingerprintSignal(vol)
        val svc = service(
            FakeCatalog(signals = emptyMap(), codeSets = emptyMap()),
            FakeStore(),
            currentHash = "hash-v2"
        )
        val prof = profile(
            "hash-v1",
            mapOf(IrAction.VOLUME_UP to binding(IrAction.VOLUME_UP, "sig-gone", fp))
        )

        val result = svc.revalidateProfile(prof)
        assertTrue(result.bindingResults[IrAction.VOLUME_UP] is BindingRevalidationResult.NeedsRevalidation)
        assertFalse(result.allBindingsValid)
        assertTrue(result.needsUserAction)
    }

    @Test
    fun `downgrade removing codeSet reports explicit reason`() = runBlocking {
        val vol = nec(0x04, 0x10)
        val fp = IrProbeEngine.fingerprintSignal(vol)
        val prof = profile(
            "hash-v1",
            mapOf(IrAction.VOLUME_UP to binding(IrAction.VOLUME_UP, "sig-x", fp))
        ).copy(codeSetId = "cs-removed")

        val svc = service(
            FakeCatalog(signals = emptyMap(), codeSets = emptyMap()),
            FakeStore(),
            currentHash = "hash-v2"
        )
        val result = svc.revalidateProfile(prof)
        val r = result.bindingResults[IrAction.VOLUME_UP]
        assertTrue(r is BindingRevalidationResult.NeedsRevalidation)
        assertEquals(
            "CodeSet no longer exists in catalog",
            (r as BindingRevalidationResult.NeedsRevalidation).reason
        )
    }

    @Test
    fun `downgrade with alternative signal in codeSet migrates to it`() = runBlocking {
        // Signal gone but codeSet still lists an alternative for the action.
        val alt = nec(0x04, 0x12)
        val catalog = FakeCatalog(
            signals = mapOf("sig-alt-1" to alt),
            codeSets = mapOf(
                "cs-lg-1" to codeSet("cs-lg-1", mapOf(IrAction.VOLUME_UP to ("alt-1" to alt)))
            )
        )
        val prof = profile(
            "hash-v1",
            mapOf(IrAction.VOLUME_UP to binding(IrAction.VOLUME_UP, "sig-old-x", "fp"))
        )

        val svc = service(catalog, FakeStore(), currentHash = "hash-v2")
        val result = svc.revalidateProfile(prof)

        assertTrue(result.bindingResults[IrAction.VOLUME_UP] is BindingRevalidationResult.Migrate)
        val migrated = (result.bindingResults[IrAction.VOLUME_UP] as BindingRevalidationResult.Migrate)
            .updatedBinding
        assertEquals("sig-alt-1", migrated.signalId)
    }

    @Test
    fun `applyRevalidation refuses invalid bindings and does not persist`() = runBlocking {
        val store = FakeStore()
        val prof = profile(
            "hash-v1",
            mapOf(IrAction.VOLUME_UP to binding(IrAction.VOLUME_UP, "sig-lost", "fp"))
        )
        val result = ProfileRevalidationResult(
            profileId = prof.id,
            catalogHashMatches = false,
            bindingResults = mapOf(
                IrAction.VOLUME_UP to BindingRevalidationResult.NeedsRevalidation(
                    IrAction.VOLUME_UP, "gone"
                )
            ),
            allBindingsValid = false
        )

        val svc = service(FakeCatalog(emptyMap(), emptyMap()), store, "hash-v2")
        val applied = svc.applyRevalidation(prof, result)

        assertNull("must not persist", store.savedProfile)
        assertEquals("hash-v1", applied.catalogCanonicalHashAtInstall)
    }

    @Test
    fun `applyRevalidation persists migrated binding`() = runBlocking {
        val phys = nec(0x04, 0x10)
        val fp = IrProbeEngine.fingerprintSignal(phys)
        val catalog = FakeCatalog(
            signals = mapOf("sig-new-1" to phys),
            codeSets = mapOf(
                "cs-lg-1" to codeSet("cs-lg-1", mapOf(IrAction.VOLUME_UP to ("new-1" to phys)))
            )
        )
        val store = FakeStore()
        val prof = profile(
            "hash-v1",
            mapOf(IrAction.VOLUME_UP to binding(IrAction.VOLUME_UP, "sig-old-1", fp))
        )

        val svc = service(catalog, store, currentHash = "hash-v2")
        val result = svc.revalidateProfile(prof)
        val applied = svc.applyRevalidation(prof, result)

        assertTrue(result.bindingResults[IrAction.VOLUME_UP] is BindingRevalidationResult.Migrate)
        assertEquals("sig-new-1", applied.commands[IrAction.VOLUME_UP]!!.signalId)
        assertEquals("sig-new-1", store.savedProfile!!.commands[IrAction.VOLUME_UP]!!.signalId)
        assertEquals("hash-v2", store.savedProfile!!.catalogCanonicalHashAtInstall)
    }
}
