package com.elysium.nexus.fabric.profile

import android.content.Context
import android.util.Log
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.core.device.InstalledIrProfile
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCommandBinding
import com.elysium.nexus.core.device.VerificationStatus
import com.elysium.nexus.fabric.infrared.IrProbeEngine
import com.elysium.nexus.fabric.infrared.RuntimePolicy
import com.elysium.nexus.fabric.infrared.database.IrCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ElysiumNexus.Revalidation"

/**
 * V06-P2: Narrow catalog seam so revalidation decisions are JVM-testable.
 * Production uses [IrCatalogRepository] (which also implements [IrCatalog]).
 */
interface RevalidationCatalog {
    suspend fun getSignal(signalId: String): IrSignal?
    suspend fun getCodeSet(codeSetId: String): IrCodeSet?

    /**
     * V0.7 Phase 5 — policy-gated signal access. EXPERIMENTAL codecs are
     * LAB_ONLY and must never re-enter a saved profile.
     */
    suspend fun resolveExecutableSignal(
        signalId: String,
        policy: RuntimePolicy
    ): IrSignal?
}

/**
 * P0.1: Result of revalidating a single binding within a profile.
 */
sealed interface BindingRevalidationResult {
    /** Signal still exists with identical physical fingerprint. */
    data class Keep(val binding: IrCommandBinding) : BindingRevalidationResult

    /** SignalId changed but physical fingerprint has a unique match in current catalog. */
    data class Migrate(val updatedBinding: IrCommandBinding) : BindingRevalidationResult

    /**
     * Signal fingerprint changed and multiple candidates share the same
     * fingerprint in the code set — cannot determine the correct migration
     * without user input. Caller must present options or re-probe.
     */
    data class Ambiguous(
        val action: IrAction,
        val reason: String,
        val candidateSignalIds: List<String> = emptyList()
    ) : BindingRevalidationResult

    /** Signal no longer valid or fingerprint changed without equivalent. */
    data class NeedsRevalidation(val action: IrAction, val reason: String) : BindingRevalidationResult

    /** Codec/protocol no longer supported. */
    data class UnsupportedCodec(val action: IrAction, val reason: String) : BindingRevalidationResult
}

/**
 * P0.1: Result of revalidating an entire profile.
 */
data class ProfileRevalidationResult(
    val profileId: String,
    val catalogHashMatches: Boolean,
    val bindingResults: Map<IrAction, BindingRevalidationResult>,
    val allBindingsValid: Boolean,
    val revalidatedAtEpochMs: Long = System.currentTimeMillis()
) {
    val needsUserAction: Boolean
        get() = bindingResults.values.any {
            it is BindingRevalidationResult.NeedsRevalidation ||
                it is BindingRevalidationResult.UnsupportedCodec ||
                it is BindingRevalidationResult.Ambiguous
        }
}

/**
 * P0.1: ProfileRevalidationService — Correct revalidation logic.
 *
 * Compares the catalog hash stored at profile creation time against the
 * current catalog hash. For each binding, verifies that the signalId still
 * exists and the physical fingerprint matches.
 *
 * This replaces the incorrect comparison: catalogHash != sourceRevision.
 */
/**
 * V06-P2: Nano profile-store seam so applyRevalidation is JVM-testable.
 */
interface ProfileRevalidationStore {
    suspend fun saveProfile(profile: InstalledIrProfile, verifiedActions: Set<IrAction>)
}

class ProfileRevalidationService(
    private val catalog: RevalidationCatalog,
    private val profileStore: ProfileRevalidationStore,
    private val currentCatalogHash: () -> String
) {
    /**
     * Production wiring: full catalog + manufacturer manifest hash + Room store.
     */
    constructor(context: Context) : this(
        catalog = IrCatalogRepository.getInstance(context),
        profileStore = object : ProfileRevalidationStore {
            private val repo = InstalledIrProfileRepository(context)
            override suspend fun saveProfile(profile: InstalledIrProfile, verifiedActions: Set<IrAction>) {
                repo.saveProfileSuspend(profile, verifiedActions)
            }
        },
        currentCatalogHash = { Companion.readManifestHash(context) }
    )

    /**
     * Revalidate a single profile against the current catalog.
     *
     * Algorithm:
     * 1. Compare catalogCanonicalHashAtInstall with current catalog hash.
     * 2. For each InstalledCommandBinding:
     *    a. Locate signalId in current catalog.
     *    b. Verify physicalSha256 matches.
     *    c. Verify runtime eligibility (codec support).
     * 3. If signal identical → KEEP.
     * 4. If signalId changed but physicalSha256 has unique equivalent → MIGRATE_BINDING.
     * 5. If not found → NEEDS_REVALIDATION.
     * 6. Only after full revalidation update catalogCanonicalHashAtInstall.
     */
    suspend fun revalidateProfile(profile: InstalledIrProfile): ProfileRevalidationResult {
        return withContext(Dispatchers.IO) {
            val currentCatalogHash = currentCatalogHash.invoke()
            val catalogHashMatches = currentCatalogHash != "unknown" &&
                profile.catalogCanonicalHashAtInstall != "unknown" &&
                currentCatalogHash == profile.catalogCanonicalHashAtInstall

            val bindingResults = mutableMapOf<IrAction, BindingRevalidationResult>()

            for ((action, binding) in profile.commands) {
                val result = revalidateBinding(profile.codeSetId, action, binding)
                bindingResults[action] = result
            }

            val allValid = bindingResults.values.all {
                it is BindingRevalidationResult.Keep || it is BindingRevalidationResult.Migrate
            }

            val revalidationResult = ProfileRevalidationResult(
                profileId = profile.id,
                catalogHashMatches = catalogHashMatches,
                bindingResults = bindingResults,
                allBindingsValid = allValid
            )

            Log.d(TAG, "Profile ${profile.id}: catalogMatch=$catalogHashMatches, " +
                "bindings=${bindingResults.size}, allValid=$allValid")

            revalidationResult
        }
    }

    /**
     * Apply successful revalidation to a profile: update the stored catalog hash
     * and apply any binding migrations.
     */
    suspend fun applyRevalidation(
        profile: InstalledIrProfile,
        result: ProfileRevalidationResult
    ): InstalledIrProfile {
        if (!result.allBindingsValid) {
            Log.w(TAG, "Cannot apply revalidation: some bindings are invalid")
            return profile
        }

        val updatedCommands = profile.commands.toMutableMap()
        for ((action, bindingResult) in result.bindingResults) {
            when (bindingResult) {
                is BindingRevalidationResult.Keep -> {
                    // No change needed
                }
                is BindingRevalidationResult.Migrate -> {
                    updatedCommands[action] = bindingResult.updatedBinding
                    Log.d(TAG, "Migrated binding for $action: ${bindingResult.updatedBinding.signalId}")
                }
                else -> {
                    Log.e(TAG, "Unexpected invalid binding in applyRevalidation: $bindingResult")
                    return profile
                }
            }
        }

        val updatedProfile = profile.copy(
            commands = updatedCommands,
            catalogCanonicalHashAtInstall = currentCatalogHash.invoke()
        )

        profileStore.saveProfile(updatedProfile, profile.verifiedActions)
        Log.d(TAG, "Applied revalidation to profile ${profile.id}")
        return updatedProfile
    }

    /**
     * V0.6.2 PR3 Phase 11: Apply a single binding's revalidation result.
     * Returns the updated profile (saved to store) or the original if unchanged.
     */
    suspend fun applyBindingRevalidation(
        profile: InstalledIrProfile,
        action: IrAction,
        bindingResult: BindingRevalidationResult
    ): InstalledIrProfile {
        when (bindingResult) {
            is BindingRevalidationResult.Keep -> {
                Log.d(TAG, "Binding $action kept unchanged")
                return profile
            }
            is BindingRevalidationResult.Migrate -> {
                val updatedCommands = profile.commands.toMutableMap()
                updatedCommands[action] = bindingResult.updatedBinding
                val updatedProfile = profile.copy(
                    commands = updatedCommands,
                    catalogCanonicalHashAtInstall = currentCatalogHash.invoke()
                )
                profileStore.saveProfile(updatedProfile, profile.verifiedActions)
                Log.d(TAG, "Migrated binding $action: ${bindingResult.updatedBinding.signalId}")
                return updatedProfile
            }
            is BindingRevalidationResult.Ambiguous -> {
                Log.w(TAG, "Cannot auto-apply ambiguous binding $action: ${bindingResult.reason}")
                return profile
            }
            is BindingRevalidationResult.NeedsRevalidation -> {
                Log.w(TAG, "Cannot auto-apply binding $action: ${bindingResult.reason}")
                return profile
            }
            is BindingRevalidationResult.UnsupportedCodec -> {
                Log.w(TAG, "Cannot apply unsupported codec for $action: ${bindingResult.reason}")
                return profile
            }
        }
    }

    private suspend fun revalidateBinding(
        codeSetId: String,
        action: IrAction,
        binding: IrCommandBinding
    ): BindingRevalidationResult {
        // 1. Check if signalId exists in current catalog (commercial policy:
        //    EXPERIMENTAL codecs are LAB_ONLY and never re-enter profiles).
        val catalogSignal = catalog.resolveExecutableSignal(
            binding.signalId,
            RuntimePolicy.COMMERCIAL
        )

        if (catalogSignal != null) {
            // 2. Verify physical fingerprint matches
            val currentFingerprint = IrProbeEngine.fingerprintSignal(catalogSignal)
            if (currentFingerprint == binding.physicalFingerprint) {
                return BindingRevalidationResult.Keep(binding)
            }

            // 3. Fingerprint changed — look for unique equivalent via selectedCommands (single authority)
            val codeSet = catalog.getCodeSet(codeSetId)
            if (codeSet != null) {
                // P0.2: selectedCommands is the single authority — no commandBindings fallback
                val equivalent = codeSet.selectedCommands[action]?.takeIf {
                    it.physicalSha256 == binding.physicalFingerprint
                }
                if (equivalent != null) {
                    val newBinding = binding.copy(signalId = equivalent.signalId)
                    return BindingRevalidationResult.Migrate(newBinding)
                }

                // Check if multiple bindings share the same fingerprint → Ambiguous
                val matchingBindings = codeSet.selectedCommands.values.filter {
                    it.physicalSha256 == binding.physicalFingerprint
                }
                if (matchingBindings.size > 1) {
                    return BindingRevalidationResult.Ambiguous(
                        action = action,
                        reason = "Multiple signals share fingerprint ${binding.physicalFingerprint.take(12)}…",
                        candidateSignalIds = matchingBindings.map { it.signalId }
                    )
                }
            }

            return BindingRevalidationResult.NeedsRevalidation(
                action = action,
                reason = "Signal fingerprint changed and no equivalent found"
            )
        }

        // 4. Signal not found — check if codeSet still exists
        val codeSet = catalog.getCodeSet(codeSetId)
        if (codeSet == null) {
            return BindingRevalidationResult.NeedsRevalidation(
                action = action,
                reason = "CodeSet no longer exists in catalog"
            )
        }

        // 5. CodeSet exists but signal doesn't — try selectedCommands as single authority
        val alternativeSignalId = codeSet.selectedCommands[action]?.signalId
        if (alternativeSignalId != null) {
            val alternativeSignal = catalog.resolveExecutableSignal(
                alternativeSignalId,
                RuntimePolicy.COMMERCIAL
            )
            if (alternativeSignal != null) {
                val newFingerprint = IrProbeEngine.fingerprintSignal(alternativeSignal)
                val newBinding = binding.copy(
                    signalId = alternativeSignalId,
                    physicalFingerprint = newFingerprint
                )
                return BindingRevalidationResult.Migrate(newBinding)
            }
        }

        return BindingRevalidationResult.NeedsRevalidation(
            action = action,
            reason = "Signal not found in current catalog"
        )
    }

    companion object {
        private fun readManifestHash(context: Context): String {
            return try {
                val manifestJson = context.assets.open("ir/ir_catalog.manifest.json")
                    .bufferedReader().use { it.readText() }
                val manifest = org.json.JSONObject(manifestJson)
                manifest.optString("canonicalContentSha256", "unknown")
            } catch (e: Exception) {
                "unknown"
            }
        }
    }
}
