package com.elysium.nexus.fabric.profile

import android.content.Context
import android.util.Log
import com.elysium.nexus.core.device.InstalledIrProfile
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCommandBinding
import com.elysium.nexus.core.device.VerificationStatus
import com.elysium.nexus.fabric.profile.db.ElysiumUserDatabase
import com.elysium.nexus.fabric.profile.db.InstalledIrCommandEntity
import com.elysium.nexus.fabric.profile.db.InstalledIrProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "ElysiumNexus.ProfileRepo"
private const val PROFILES_FILE = "installed_ir_profiles.json"

/**
 * §9 Save result — UI must not navigate until Saved is received.
 */
sealed interface SaveProfileResult {
    data class Saved(val profileId: String) : SaveProfileResult
    data class ValidationFailure(val reason: String) : SaveProfileResult
    data class StorageFailure(val cause: Throwable) : SaveProfileResult
}

/**
 * §2/§9 Authoritative IR Profile Repository.
 *
 * Room is the single source of truth. JSON is used ONLY as a one-shot
 * migration path for profiles created before Room integration.
 * After migration, JSON is deleted and never written again.
 *
 * All public methods are either suspend (coroutine-safe) or synchronous
 * read-only from an in-memory cache populated from Room at startup.
 */
class InstalledIrProfileRepository(
    private val storageDir: File,
    private val context: Context? = null,
    private val scope: CoroutineScope? = null
) {
    constructor(context: Context) : this(context.noBackupFilesDir, context)

    private val storageFile: File
        get() = File(storageDir, PROFILES_FILE)

    private val memoryCache = mutableMapOf<String, InstalledIrProfile>()

    @Volatile
    private var loadedFromRoom = false

    private suspend fun ensureLoaded() {
        if (!loadedFromRoom) {
            loadFromRoom()
            loadedFromRoom = true
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Load: Room first, then one-shot JSON migration
    // ═══════════════════════════════════════════════════════════════════

    private suspend fun loadFromRoom() {
        memoryCache.clear()
        if (context == null) return

        try {
            val db = ElysiumUserDatabase.getInstance(context)
            val profileEntities = db.profileDao().getAllProfiles()
            for (pe in profileEntities) {
                val commandEntities = db.profileDao().getCommandsForProfile(pe.profileId)
                val profile = mapEntityToProfile(pe, commandEntities)
                memoryCache[profile.id] = profile
            }
            Log.d(TAG, "Loaded ${memoryCache.size} profiles from Room")

            if (memoryCache.isNotEmpty()) {
                migrateJsonToRoomIfPresent()
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Room load failed: ${e.message}")
        }

        migrateJsonToRoomIfPresent()
    }

    /**
     * One-shot migration: if a JSON file exists, import all profiles into Room
     * then delete the JSON file. Never reads JSON again after this.
     */
    private suspend fun migrateJsonToRoomIfPresent() {
        val file = storageFile
        if (!file.exists() || file.length() == 0L) return

        try {
            val jsonStr = file.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(jsonStr)
            val migrated = mutableListOf<InstalledIrProfile>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val profile = deserializeProfile(obj)
                migrated.add(profile)
            }

            if (migrated.isNotEmpty() && context != null) {
                val db = ElysiumUserDatabase.getInstance(context)
                for (profile in migrated) {
                    val pe = mapProfileToEntity(profile, setOf())
                    val ces = profile.commands.map { (action, binding) ->
                        mapBindingToEntity(profile.id, profile.codeSetId, action, binding, VerificationStatus.PARTIALLY_VERIFIED)
                    }
                    db.profileDao().saveProfileWithCommands(pe, ces)
                    memoryCache[profile.id] = profile
                }
                Log.d(TAG, "Migrated ${migrated.size} profiles from JSON to Room")
            }

            file.delete()
            Log.d(TAG, "JSON migration file deleted")
        } catch (e: Exception) {
            Log.e(TAG, "JSON migration failed: ${e.message}", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Save: Room authoritative + in-memory cache
    // ═══════════════════════════════════════════════════════════════════

    /**
     * V06.2 PR3 Phase 10: DURABLE profile save.
     *
     * "Saved" means a durable COMMIT in Room, nothing less. The in-memory
     * cache is only updated AFTER the commit succeeds — a failed Room write
     * can never leave a ghost profile in the cache (P0-18).
     *
     * Transaction (P0-19): the exact current command set replaces previous
     * commands atomically (DELETE old → INSERT new, inside one transaction).
     */
    suspend fun installProfile(profile: InstalledIrProfile, verifiedActions: Set<IrAction> = emptySet()): SaveProfileResult {
        if (profile.codeSetId.isBlank()) {
            return SaveProfileResult.ValidationFailure("codeSetId is blank")
        }
        if (profile.commands.isEmpty()) {
            return SaveProfileResult.ValidationFailure("commands map is empty")
        }
        if (context == null) {
            memoryCache[profile.id] = profile
            return SaveProfileResult.Saved(profile.id)
        }
        try {
            val db = ElysiumUserDatabase.getInstance(context)
            val pe = mapProfileToEntity(profile, verifiedActions)
            val ces = profile.commands.map { (action, binding) ->
                val wasVerified = action in verifiedActions
                mapBindingToEntity(profile.id, profile.codeSetId, action, binding, profile.verificationStatus, wasVerified)
            }
            // Room @Transaction: insert profile + delete stale commands + insert exact set.
            db.profileDao().saveProfileWithCommands(pe, ces)
            // Commit succeeded → now (and only now) update the cache projection.
            memoryCache[profile.id] = profile
            Log.d(TAG, "Saved profile ${profile.id} to Room with ${ces.size} commands, verified=$verifiedActions")
            return SaveProfileResult.Saved(profile.id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save profile ${profile.id} to Room: ${e.message}")
            // V06.2 PR3 Phase 10: never return Saved before the durable commit;
            // never enqueue a fire-and-forget write that returns Saved anyway.
            return SaveProfileResult.StorageFailure(e)
        }
    }

    /**
     * V06.2 PR3 Phase 10: compatibility facade — delegates to the durable
     * suspend path and returns the REAL result. Never fire-and-forget.
     */
    fun saveProfile(profile: InstalledIrProfile, verifiedActions: Set<IrAction> = emptySet()): SaveProfileResult {
        return runBlocking { installProfile(profile, verifiedActions) }
    }

    /** Legacy alias kept for callers that migrated to suspend semantics. */
    suspend fun saveProfileSuspend(profile: InstalledIrProfile, verifiedActions: Set<IrAction> = emptySet()): SaveProfileResult {
        return installProfile(profile, verifiedActions)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Read: from in-memory cache (populated from Room at startup)
    // ═══════════════════════════════════════════════════════════════════

    fun getProfile(id: String): InstalledIrProfile? {
        if (!loadedFromRoom && context != null) {
            kotlinx.coroutines.runBlocking { ensureLoaded() }
        }
        return memoryCache[id]
    }

    suspend fun getProfileSuspend(id: String): InstalledIrProfile? {
        memoryCache[id]?.let { return it }
        if (context != null) {
            try {
                val db = ElysiumUserDatabase.getInstance(context)
                val pe = db.profileDao().getProfileById(id) ?: return null
                val ces = db.profileDao().getCommandsForProfile(id)
                val profile = mapEntityToProfile(pe, ces)
                memoryCache[profile.id] = profile
                return profile
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load profile $id from Room: ${e.message}")
            }
        }
        return null
    }

    fun getAllProfiles(): List<InstalledIrProfile> {
        if (!loadedFromRoom && context != null) {
            kotlinx.coroutines.runBlocking { ensureLoaded() }
        }
        return memoryCache.values.toList()
    }

    suspend fun getAllProfilesSuspend(): List<InstalledIrProfile> {
        if (memoryCache.isNotEmpty()) return memoryCache.values.toList()
        loadFromRoom()
        return memoryCache.values.toList()
    }

    // ═══════════════════════════════════════════════════════════════════
    // Delete: Room authoritative
    // ═══════════════════════════════════════════════════════════════════

    fun deleteProfile(id: String): Boolean {
        val removed = memoryCache.remove(id) != null
        if (removed && context != null) {
            val dbDelete: suspend () -> Unit = {
                try {
                    ElysiumUserDatabase.getInstance(context).profileDao().deleteProfileWithCommands(id)
                    Log.d(TAG, "Deleted profile $id from Room")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete profile $id from Room: ${e.message}")
                }
            }
            if (scope != null) {
                scope.launch(Dispatchers.IO) { dbDelete() }
            } else {
                runBlocking(Dispatchers.IO) { dbDelete() }
            }
        }
        return removed
    }

    // ═══════════════════════════════════════════════════════════════════
    // Probe Session Tracking — §23
    // ═══════════════════════════════════════════════════════════════════

    suspend fun saveProbeAttempt(
        sessionId: String,
        attemptId: String,
        candidateId: String,
        codeSetId: String,
        signalId: String,
        actionKey: String,
        result: String,
        transmitDurationMs: Long
    ) {
        if (context == null) return
        try {
            val db = ElysiumUserDatabase.getInstance(context)
            db.profileDao().insertProbeAttempt(
                com.elysium.nexus.fabric.profile.db.ProbeAttemptEntity(
                    attemptId = attemptId,
                    sessionId = sessionId,
                    candidateId = candidateId,
                    codeSetId = codeSetId,
                    signalId = signalId,
                    actionKey = actionKey,
                    transmittedAtEpochMs = System.currentTimeMillis(),
                    result = result,
                    transmitDurationMs = transmitDurationMs,
                    physicalSha256 = null,
                    carrierHz = null,
                    catalogBuildId = null,
                    confirmedAtEpochMs = null,
                    confirmedBy = null
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save probe attempt: ${e.message}")
        }
    }

    suspend fun recordCompatibilityEvidence(
        codeSetId: String,
        brand: String,
        deviceType: String,
        actionKey: String,
        success: Boolean,
        source: String = "local_probe"
    ) {
        if (context == null) return
        try {
            val db = ElysiumUserDatabase.getInstance(context)
            db.profileDao().insertEvidence(
                com.elysium.nexus.fabric.profile.db.CompatibilityEvidenceEntity(
                    codeSetId = codeSetId,
                    brand = brand,
                    deviceType = deviceType,
                    actionKey = actionKey,
                    success = success,
                    reportSource = source,
                    reportedAtEpochMs = System.currentTimeMillis(),
                    notes = null
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record evidence: ${e.message}")
        }
    }

    suspend fun penalizeCandidate(codeSetId: String, reason: String) {
        if (context == null) return
        try {
            val db = ElysiumUserDatabase.getInstance(context)
            val existing = db.profileDao().getPenalty(codeSetId)
            if (existing != null) {
                db.profileDao().insertPenalty(
                    existing.copy(
                        penaltyScore = existing.penaltyScore + 10,
                        failCount = existing.failCount + 1,
                        lastFailEpochMs = System.currentTimeMillis(),
                        reason = reason
                    )
                )
            } else {
                db.profileDao().insertPenalty(
                    com.elysium.nexus.fabric.profile.db.CandidatePenaltyEntity(
                        codeSetId = codeSetId,
                        penaltyScore = 10,
                        failCount = 1,
                        lastFailEpochMs = System.currentTimeMillis(),
                        reason = reason
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to penalize candidate: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Mapping helpers
    // ═══════════════════════════════════════════════════════════════════

    private fun mapEntityToProfile(
        pe: InstalledIrProfileEntity,
        commandEntities: List<InstalledIrCommandEntity>
    ): InstalledIrProfile {
        val commandsMap = mutableMapOf<IrAction, IrCommandBinding>()
        for (ce in commandEntities) {
            val action = try { IrAction.valueOf(ce.actionKey) } catch (e: Exception) { null }
            if (action != null) {
                commandsMap[action] = IrCommandBinding(
                    signalId = ce.signalId,
                    physicalFingerprint = ce.physicalSha256,
                    sourceId = ce.sourceRevisionId,
                    action = action
                )
            }
        }
        val status = try {
            VerificationStatus.valueOf(pe.verificationStatus)
        } catch (e: Exception) {
            VerificationStatus.PARTIALLY_VERIFIED
        }
        // P0-4: verifiedActions derived from actual successCount, NOT commandsMap.keys
        val verifiedFromDb = commandEntities
            .filter { it.successCount > 0 }
            .mapNotNull { ce ->
                try { IrAction.valueOf(ce.actionKey) } catch (e: Exception) { null }
            }
            .toSet()
        return InstalledIrProfile(
            id = pe.profileId,
            displayName = pe.displayName,
            brand = pe.brandId,
            deviceType = pe.deviceTypeId,
            model = pe.deviceModelId,
            remoteModel = pe.remoteId,
            codeSetId = pe.codeSetId,
            sourceRevision = pe.catalogVersion,
            catalogSchemaVersionAtInstall = pe.catalogSchemaVersionAtInstall,
            catalogCanonicalHashAtInstall = pe.catalogCanonicalHashAtInstall,
            catalogBuildIdAtInstall = pe.catalogBuildIdAtInstall,
            commands = commandsMap,
            verifiedActions = verifiedFromDb,
            verificationStatus = status,
            createdAtEpochMs = pe.createdAtEpochMs
        )
    }

    private fun mapProfileToEntity(profile: InstalledIrProfile, verifiedActions: Set<IrAction>): InstalledIrProfileEntity {
        // P0.1: On FIRST save, store the current catalog hash as the install-time hash.
        // On subsequent saves, PRESERVE the original install-time hash (don't overwrite).
        val isFirstSave = profile.catalogCanonicalHashAtInstall == "unknown"
        val catalogHashToStore = if (isFirstSave) {
            computeCatalogHash()
        } else {
            profile.catalogCanonicalHashAtInstall
        }

        // P0.1: needsRevalidation = true when current catalog hash differs from install-time hash
        val currentCatalogHash = computeCatalogHash()
        val needsRevalidation = currentCatalogHash != "unknown" &&
            catalogHashToStore != "unknown" &&
            currentCatalogHash != catalogHashToStore

        return InstalledIrProfileEntity(
            profileId = profile.id,
            displayName = profile.displayName,
            brandId = profile.brand,
            deviceTypeId = profile.deviceType,
            deviceModelId = profile.model,
            remoteId = profile.remoteModel,
            codeSetId = profile.codeSetId,
            catalogVersion = profile.sourceRevision,
            catalogCanonicalHashAtInstall = catalogHashToStore,
            catalogSchemaVersionAtInstall = profile.catalogSchemaVersionAtInstall,
            catalogBuildIdAtInstall = profile.catalogBuildIdAtInstall,
            verificationStatus = profile.verificationStatus.name,
            createdAtEpochMs = profile.createdAtEpochMs,
            updatedAtEpochMs = System.currentTimeMillis(),
            lastSuccessfulUseEpochMs = 0L,
            needsRevalidation = needsRevalidation,
            isEnabled = true
        )
    }

    private fun mapBindingToEntity(
        profileId: String,
        profileCodeSetId: String,
        action: IrAction,
        binding: IrCommandBinding,
        verificationStatus: VerificationStatus,
        wasVerified: Boolean = false
    ): InstalledIrCommandEntity {
        return InstalledIrCommandEntity(
            profileId = profileId,
            actionKey = action.name,
            signalId = binding.signalId,
            codeSetId = profileCodeSetId,
            physicalSha256 = binding.physicalFingerprint,
            sourceRevisionId = binding.sourceId,
            verificationStatus = verificationStatus.name,
            successCount = if (wasVerified) 1 else 0,
            failureCount = 0,
            lastSuccessEpochMs = if (wasVerified) System.currentTimeMillis() else 0L,
            lastFailureEpochMs = 0L
        )
    }

    private fun computeCatalogHash(): String {
        // V06.2 PR3 Phase 11: ONE source for catalog truth — the installer's
        // verified metadata (validated manifest), never a regex over the asset.
        val metadata = try {
            if (context != null) {
                com.elysium.nexus.fabric.infrared.database.IrCatalogDatabaseManager
                    .getInstance(context).currentCatalogMetadata()
            } else null
        } catch (e: Exception) {
            null
        }
        val hash = metadata?.canonicalContentSha256
        return if (hash.isNullOrBlank() || hash == "unknown") "unknown" else hash
    }

    // ═══════════════════════════════════════════════════════════════════
    // JSON Deserialization — only used during one-shot migration
    // ═══════════════════════════════════════════════════════════════════

    private fun deserializeProfile(obj: JSONObject): InstalledIrProfile {
        val id = obj.getString("id")
        val displayName = obj.getString("displayName")
        val brand = obj.getString("brand")
        val deviceType = obj.optString("deviceType", "TV")
        val model = obj.optString("model").ifBlank { null }
        val remoteModel = obj.optString("remoteModel").ifBlank { null }
        val codeSetId = obj.getString("codeSetId")
        val sourceRevision = obj.optString("sourceRevision", "v0.5.0")
        val verificationStatus = try {
            VerificationStatus.valueOf(obj.optString("verificationStatus", "PARTIALLY_VERIFIED"))
        } catch (e: Exception) {
            VerificationStatus.PARTIALLY_VERIFIED
        }
        val createdAtEpochMs = obj.optLong("createdAtEpochMs", System.currentTimeMillis())

        val verifiedActions = mutableSetOf<IrAction>()
        val vArr = obj.optJSONArray("verifiedActions")
        if (vArr != null) {
            for (i in 0 until vArr.length()) {
                try {
                    verifiedActions.add(IrAction.valueOf(vArr.getString(i)))
                } catch (_: Exception) {}
            }
        }

        val commandsMap = mutableMapOf<IrAction, IrCommandBinding>()
        val cObj = obj.optJSONObject("commands")
        if (cObj != null) {
            val keys = cObj.keys()
            while (keys.hasNext()) {
                val actionName = keys.next()
                try {
                    val action = IrAction.valueOf(actionName)
                    val bObj = cObj.getJSONObject(actionName)
                    val binding = IrCommandBinding(
                        signalId = bObj.getString("signalId"),
                        physicalFingerprint = bObj.getString("physicalFingerprint"),
                        sourceId = bObj.getString("sourceId"),
                        action = action
                    )
                    commandsMap[action] = binding
                } catch (_: Exception) {}
            }
        }

        return InstalledIrProfile(
            id = id,
            displayName = displayName,
            brand = brand,
            deviceType = deviceType,
            model = model,
            remoteModel = remoteModel,
            codeSetId = codeSetId,
            sourceRevision = sourceRevision,
            commands = commandsMap,
            verifiedActions = verifiedActions,
            verificationStatus = verificationStatus,
            createdAtEpochMs = createdAtEpochMs
        )
    }
}
