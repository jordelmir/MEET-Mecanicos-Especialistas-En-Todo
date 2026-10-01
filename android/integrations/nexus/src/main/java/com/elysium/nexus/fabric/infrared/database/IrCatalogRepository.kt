package com.elysium.nexus.fabric.infrared.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.elysium.nexus.core.device.CatalogCommandBinding
import com.elysium.nexus.core.device.CodeProvenance
import com.elysium.nexus.core.device.EvidenceLevel
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.core.device.SelectedCommandBinding
import com.elysium.nexus.fabric.profile.RevalidationCatalog
import com.elysium.nexus.core.device.VerificationStatus
import com.elysium.nexus.fabric.infrared.IrProtocol
import com.elysium.nexus.fabric.infrared.CodecSpec
import com.elysium.nexus.fabric.infrared.CodecResolution
import com.elysium.nexus.fabric.infrared.ProtocolCodecRegistry
import com.elysium.nexus.fabric.infrared.ProtocolVariant
import com.elysium.nexus.fabric.infrared.CodecVerificationStatus
import com.elysium.nexus.fabric.infrared.RuntimePolicy
import com.elysium.nexus.fabric.infrared.RuntimeSignalPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

private const val TAG = "ElysiumNexus.IrCatalogV4"
private const val MAX_PATTERN_SLICES = 4096

/**
 * V06.2 Phase 2: Map catalog protocol_definitions.family_name → IrProtocol enum.
 * This is the SINGLE mapping from catalog truth to runtime truth.
 * Legacy codec_id resolution via ProtocolCodecRegistry is provenance-only.
 */
private fun resolveProtocolFromFamily(familyName: String?): IrProtocol? = when {
    familyName == null -> null
    familyName.equals("NEC", ignoreCase = true) -> IrProtocol.Nec
    familyName.equals("NECx", ignoreCase = true) || familyName.equals("NEC Extended", ignoreCase = true) -> IrProtocol.NecExtended
    familyName.equals("Samsung", ignoreCase = true) -> IrProtocol.Samsung
    familyName.equals("SIRC", ignoreCase = true) ||
        familyName.equals("Sony SIRC", ignoreCase = true) ||
        // RC-12: SIRC15/SIRC20 are SIRC variants the encoder supports.
        familyName.equals("SIRC15", ignoreCase = true) ||
        familyName.equals("SIRC20", ignoreCase = true) -> IrProtocol.SonySirc
    familyName.equals("RC5", ignoreCase = true) -> IrProtocol.Rc5
    familyName.equals("RC6", ignoreCase = true) -> IrProtocol.Rc6
    familyName.equals("Kaseikyo", ignoreCase = true) || familyName.equals("Panasonic", ignoreCase = true) -> IrProtocol.Kaseikyo
    else -> null
}

private data class CodeSetCommandsResult(
    val commands: Map<IrAction, IrSignal>,
    val commandSignalIds: Map<IrAction, String>,
    val commandBindings: List<CatalogCommandBinding>,
    /** V06.2 PR3 Phase 9: single authority built from the SELECTED binding (no firstOrNull re-lookup). */
    val selectedCommands: Map<IrAction, SelectedCommandBinding>
)

// V06.2 PR3 Phase 9: The parallel map projections are built FROM the selected
// bindings, never the other way around. selectedCommands is the source of truth.

/**
 * §2 Canonical Schema v4 SQLite IR Catalog Repository.
 *
 * Provides authoritative query access to Schema v4 [code_sets], [command_bindings],
 * and [signals] tables.
 * Guaranteed: A single [IrCodeSet] contains ALL command bindings belonging to that remote
 * with exact database [signalId]s. Zero manufactured signal IDs.
 *
 * Thread-safe singleton — one read-only SQLite connection shared across the process.
 */
class IrCatalogRepository private constructor(
    private val context: Context
) : IrCatalog, RevalidationCatalog {

    companion object {
        @Volatile
        private var instance: IrCatalogRepository? = null

        fun getInstance(context: Context): IrCatalogRepository {
            return instance ?: synchronized(this) {
                instance ?: IrCatalogRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Probabilistic brand-first ordering for universal probes (§35).
     * Full brand list and SQL live in [BrandRanking].
     */
    private val BRAND_RANK_ORDER: String by lazy { BrandRanking.ORDER_BY_SQL }

    /**
     * §7 The catalog is installed exactly once per process and a single read-only
     * connection is cached for its lifetime. Never open a fresh connection per
     * query: the manager replaces the on-disk file (delete + re-copy) when the
     * asset hash drifts, which orphans any previously opened descriptor and
     * surfaces as "no such table: code_sets / brands (OS error - 2)" on the next
     * prepare. The connection is only opened AFTER a successful install, so the
     * replace window can never overlap a live connection.
     */
    private val databaseLock = Any()
    private var database: SQLiteDatabase? = null

    private fun getDatabase(): SQLiteDatabase {
        database?.let { return it }
        synchronized(databaseLock) {
            database?.let { return it }
            val manager = IrCatalogDatabaseManager.getInstance(context)
            val result = manager.ensureDatabaseInstalled()
            if (result is IrCatalogDatabaseManager.InstallResult.Failed) {
                throw IllegalStateException("IR catalog unavailable: ${result.reason}", result.cause)
            }
            val dbFile = manager.databaseFile
            return SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            ).also { database = it }
        }
    }

    override suspend fun getCandidatesForBrand(
        brand: String,
        deviceType: String,
        action: IrAction
    ): List<IrCodeSet> = withContext(Dispatchers.IO) {
        val database = getDatabase()
        val actionKey = action.name
        val results = mutableListOf<IrCodeSet>()
        // PTG-02 §3: probe candidates deliberately include INTERNAL_UNVERIFIED
        // code sets (probing exists to VERIFY candidates — unverified is not
        // blocked, only BLOCKED is). Evidence floors apply to production
        // claims, not to the probe surface.

        // Phase 2 & 3: Strict TV device type enforcement and unlimited brand candidate paging
        val effectiveDevType = if (deviceType.isBlank()) "TV" else deviceType.trim()

        val query = """
            SELECT cs.id AS cs_id, b.display_name AS brand_name, r.display_remote_model,
                   s.id AS source_name, s.license_id, dt.canonical_name AS device_type,
                   cs.verification_status
            FROM code_sets cs
            JOIN remotes r ON cs.remote_id = r.id
            JOIN brands b ON r.brand_id = b.id
            JOIN device_types dt ON r.device_type_id = dt.id
            JOIN source_revisions sr ON cs.source_revision_id = sr.id
            JOIN sources s ON sr.source_id = s.id
            JOIN command_bindings cb ON cb.code_set_id = cs.id
            JOIN actions a ON cb.action_id = a.id
            WHERE (b.display_name LIKE ? OR b.normalized_name LIKE ?)
              AND a.canonical_key = ?
              AND dt.canonical_name = ?
              AND s.production_approved = 1
              AND cs.verification_status != 'BLOCKED'
            GROUP BY cs.id
            ORDER BY cs.id
        """.trimIndent()

        val brandArg = "%${brand.trim()}%"
        database.rawQuery(query, arrayOf(brandArg, brandArg, actionKey, effectiveDevType)).use { cursor ->
            while (cursor.moveToNext()) {
                val csId = cursor.getString(0)
                val brandName = cursor.getString(1) ?: brand
                val remoteModel = cursor.getString(2) ?: ""
                val sourceName = cursor.getString(3) ?: "Elysium Nexus Data Fabric"
                val licenseSpdx = cursor.getString(4) ?: "MIT"
                val verificationStr = cursor.getString(6) ?: "UNVERIFIED"

                val codeSetResult = getCommandsForCodeSetInternal(database, csId)
                // §7 A candidate is only a candidate for `action` if that
                // exact action is decodeable from the catalog (zero "phantom"
                // candidates whose VOLUME_UP codec could not be decoded).
                if (action in codeSetResult.commands && codeSetResult.commandBindings.isNotEmpty()) {
                    val verification = parseVerificationStatus(verificationStr)
                    results.add(
                        IrCodeSet(
                            id = csId,
                            brand = brandName,
                            modelPatterns = setOf(remoteModel),
                            remoteModels = if (remoteModel.isNotBlank()) setOf(remoteModel) else emptySet(),
                            commands = codeSetResult.commands,
                            commandSignalIds = codeSetResult.commandSignalIds,
                            commandBindings = codeSetResult.commandBindings,
                            selectedCommands = codeSetResult.selectedCommands,
                            provenance = CodeProvenance(
                                sourceName = sourceName,
                                sourceUrl = "",
                                licenseSpdx = licenseSpdx
                            ),
                            verification = verification
                        )
                    )
                }
            }
        }

        // §7 Connection is cached per-process; do NOT close it here.
        Log.d(TAG, "getCandidatesForBrand(brand=$brand, deviceType=$deviceType, action=$actionKey): ${results.size} multi-command Code Sets from Schema v4")
        results
    }

    override suspend fun getAllCandidates(
        deviceType: String,
        action: IrAction,
        limit: Int
    ): List<IrCodeSet> = withContext(Dispatchers.IO) {
        val database = getDatabase()
        val actionKey = action.name
        val results = mutableListOf<IrCodeSet>()

        // P0-8: Progressive brand-first heuristic.
        // Tier 1: Popular global brands (most likely to match any TV)
        // Tier 2: Regional/Latin American brands
        // Tier 3: All remaining brands
        val tier1 = listOf("Samsung", "LG", "Sony", "Panasonic", "Philips")
        val tier2 = listOf("Sankey", "Kintech", "Kalley", "Challenger", "Daewoo", "Hyundai", "Hisense", "TCL", "Noblex", "RCA", "Akai", "Sanyo", "Funai", "Magnavox")

        val baseWhere = """
            FROM code_sets cs
            JOIN remotes r ON cs.remote_id = r.id
            JOIN brands b ON r.brand_id = b.id
            JOIN device_types dt ON r.device_type_id = dt.id
            JOIN source_revisions sr ON cs.source_revision_id = sr.id
            JOIN sources s ON sr.source_id = s.id
            JOIN command_bindings cb ON cb.code_set_id = cs.id
            JOIN actions a ON cb.action_id = a.id
            WHERE a.canonical_key = ?
              AND (dt.canonical_name LIKE ? OR dt.canonical_name = 'Universal_Tv_Remotes' OR ? = '')
              AND s.production_approved = 1
              AND cs.verification_status != 'BLOCKED'
        """.trimIndent()

        val selectCols = """
            SELECT cs.id AS cs_id, b.display_name AS brand_name, r.display_remote_model,
                   s.id AS source_name, s.license_id, dt.canonical_name AS device_type,
                   cs.verification_status
        """.trimIndent()

        val devTypeArg = deviceType.trim()

        // Execute progressive search: tier1 → tier2 → remaining
        for (tierBrands in listOf(tier1, tier2, listOf<String>())) {
            if (results.size >= limit) break
            val remaining = limit - results.size

            val query: String
            val params: Array<String>

            if (tierBrands.isEmpty()) {
                // Remaining brands: exclude already-seen brand names
                val seenBrands = results.map { it.brand }.distinct()
                if (seenBrands.isEmpty()) {
                    query = "$selectCols $baseWhere GROUP BY cs.id ${BRAND_RANK_ORDER} LIMIT ?"
                    params = arrayOf(actionKey, "$devTypeArg%", devTypeArg, remaining.toString())
                } else {
                    val placeholders = seenBrands.joinToString(",") { "?" }
                    query = "$selectCols $baseWhere AND b.display_name NOT IN ($placeholders) GROUP BY cs.id ${BRAND_RANK_ORDER} LIMIT ?"
                    params = arrayOf(actionKey, "$devTypeArg%", devTypeArg, *seenBrands.toTypedArray(), remaining.toString())
                }
            } else {
                val placeholders = tierBrands.joinToString(",") { "?" }
                query = "$selectCols $baseWhere AND b.display_name IN ($placeholders) GROUP BY cs.id ${BRAND_RANK_ORDER} LIMIT ?"
                params = arrayOf(actionKey, "$devTypeArg%", devTypeArg, *tierBrands.toTypedArray(), remaining.toString())
            }

            database.rawQuery(query, params).use { cursor ->
                while (cursor.moveToNext()) {
                    val csId = cursor.getString(0)
                    val brandName = cursor.getString(1) ?: "Desconocido"
                    val remoteModel = cursor.getString(2) ?: ""
                    val sourceName = cursor.getString(3) ?: "Elysium Nexus Data Fabric"
                    val licenseSpdx = cursor.getString(4) ?: "MIT"
                    val verificationStr = cursor.getString(6) ?: "UNVERIFIED"

                    val codeSetResult = getCommandsForCodeSetInternal(database, csId)
                    if (action in codeSetResult.commands && codeSetResult.commandBindings.isNotEmpty()) {
                        val verification = parseVerificationStatus(verificationStr)
                        results.add(
                            IrCodeSet(
                                id = csId,
                                brand = brandName,
                                modelPatterns = setOf(remoteModel),
                                remoteModels = if (remoteModel.isNotBlank()) setOf(remoteModel) else emptySet(),
                                commands = codeSetResult.commands,
                                commandSignalIds = codeSetResult.commandSignalIds,
                                commandBindings = codeSetResult.commandBindings,
                                selectedCommands = codeSetResult.selectedCommands,
                                provenance = CodeProvenance(
                                    sourceName = sourceName,
                                    sourceUrl = "",
                                    licenseSpdx = licenseSpdx
                                ),
                                verification = verification
                            )
                        )
                    }
                }
            }
        }

        Log.d(TAG, "getAllCandidates(deviceType=$deviceType, action=$actionKey): ${results.size} Code Sets (progressive brand-first)")
        results
    }

    // V0.6.2 PR3 Phase 14 — Paged probe: bounded-memory candidate access.
    // Deterministic stable ordering (brand, id) — no duplicates across pages.

    private fun pagedBaseWhere(deviceType: String, actionKey: String) = """
        FROM code_sets cs
        JOIN remotes r ON cs.remote_id = r.id
        JOIN brands b ON r.brand_id = b.id
        JOIN device_types dt ON r.device_type_id = dt.id
        JOIN source_revisions sr ON cs.source_revision_id = sr.id
        JOIN sources s ON sr.source_id = s.id
        JOIN command_bindings cb ON cb.code_set_id = cs.id
        JOIN actions a ON cb.action_id = a.id
        WHERE a.canonical_key = ?
          AND (dt.canonical_name LIKE ? OR dt.canonical_name = 'Universal_Tv_Remotes' OR ? = '')
          AND s.production_approved = 1
          AND cs.verification_status != 'BLOCKED'
    """.trimIndent()

    /** Phase A — multi-key variant: any of the probe keys (a.canonical_key IN (...)). */
    private fun pagedBaseWhereIn(deviceType: String, keyCount: Int) = """
        FROM code_sets cs
        JOIN remotes r ON cs.remote_id = r.id
        JOIN brands b ON r.brand_id = b.id
        JOIN device_types dt ON r.device_type_id = dt.id
        JOIN source_revisions sr ON cs.source_revision_id = sr.id
        JOIN sources s ON sr.source_id = s.id
        JOIN command_bindings cb ON cb.code_set_id = cs.id
        JOIN actions a ON cb.action_id = a.id
        WHERE a.canonical_key IN (${(1..keyCount).joinToString(",") { "?" }})
          AND (dt.canonical_name LIKE ? OR dt.canonical_name = 'Universal_Tv_Remotes' OR ? = '')
          AND s.production_approved = 1
          AND cs.verification_status != 'BLOCKED'
    """.trimIndent()

    override suspend fun getCandidateCountForActions(
        deviceType: String,
        actions: List<IrAction>
    ): Int = withContext(Dispatchers.IO) {
        if (actions.isEmpty()) return@withContext 0
        val database = getDatabase()
        val actionKeys = actions.map { it.name }
        val devTypeArg = deviceType.trim()
        val query = "SELECT COUNT(DISTINCT cs.id) ${pagedBaseWhereIn(devTypeArg, actionKeys.size)}"
        val params = actionKeys + arrayOf("$devTypeArg%", devTypeArg)
        database.rawQuery(query, params.toTypedArray()).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    override suspend fun getCandidatePageForActions(
        deviceType: String,
        actions: List<IrAction>,
        fromIndex: Int,
        count: Int
    ): List<IrCodeSet> = withContext(Dispatchers.IO) {
        if (actions.isEmpty()) return@withContext emptyList()
        val database = getDatabase()
        val actionKeys = actions.map { it.name }
        val devTypeArg = deviceType.trim()
        val results = mutableListOf<IrCodeSet>()

        val selectCols = """
            SELECT cs.id AS cs_id, b.display_name AS brand_name, r.display_remote_model,
                   s.id AS source_name, s.license_id, dt.canonical_name AS device_type,
                   cs.verification_status
        """.trimIndent()
        val query = "$selectCols ${pagedBaseWhereIn(devTypeArg, actionKeys.size)} GROUP BY cs.id ${BRAND_RANK_ORDER} LIMIT ? OFFSET ?"
        val params = actionKeys + listOf("$devTypeArg%", devTypeArg, count.toString(), fromIndex.toString())

        database.rawQuery(query, params.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val csId = cursor.getString(0)
                val brandName = cursor.getString(1) ?: "Desconocido"
                val remoteModel = cursor.getString(2) ?: ""
                val sourceName = cursor.getString(3) ?: "Elysium Nexus Data Fabric"
                val licenseSpdx = cursor.getString(4) ?: "MIT"
                val verificationStr = cursor.getString(6) ?: "UNVERIFIED"

                val codeSetResult = getCommandsForCodeSetInternal(database, csId)
                if (codeSetResult.commands.keys.any { it in actions } && codeSetResult.commandBindings.isNotEmpty()) {
                    val verification = parseVerificationStatus(verificationStr)
                    results.add(
                        IrCodeSet(
                            id = csId,
                            brand = brandName,
                            modelPatterns = setOf(remoteModel),
                            remoteModels = if (remoteModel.isNotBlank()) setOf(remoteModel) else emptySet(),
                            commands = codeSetResult.commands,
                            commandSignalIds = codeSetResult.commandSignalIds,
                            commandBindings = codeSetResult.commandBindings,
                            selectedCommands = codeSetResult.selectedCommands,
                            provenance = CodeProvenance(
                                sourceName = sourceName,
                                sourceUrl = "",
                                licenseSpdx = licenseSpdx
                            ),
                            verification = verification
                        )
                    )
                }
            }
        }
        results
    }

    override suspend fun getCandidateCount(
        deviceType: String,
        action: IrAction
    ): Int = withContext(Dispatchers.IO) {
        val database = getDatabase()
        val actionKey = action.name
        val devTypeArg = deviceType.trim()
        val query = "SELECT COUNT(DISTINCT cs.id) ${pagedBaseWhere(devTypeArg, actionKey)}"
        val params = arrayOf(actionKey, "$devTypeArg%", devTypeArg)
        database.rawQuery(query, params).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    override suspend fun getCandidatePage(
        deviceType: String,
        action: IrAction,
        fromIndex: Int,
        count: Int
    ): List<IrCodeSet> = withContext(Dispatchers.IO) {
        val database = getDatabase()
        val actionKey = action.name
        val devTypeArg = deviceType.trim()
        val results = mutableListOf<IrCodeSet>()

        val selectCols = """
            SELECT cs.id AS cs_id, b.display_name AS brand_name, r.display_remote_model,
                   s.id AS source_name, s.license_id, dt.canonical_name AS device_type,
                   cs.verification_status
        """.trimIndent()
        val query = "$selectCols ${pagedBaseWhere(devTypeArg, actionKey)} GROUP BY cs.id ${BRAND_RANK_ORDER} LIMIT ? OFFSET ?"
        val params = arrayOf(actionKey, "$devTypeArg%", devTypeArg, count.toString(), fromIndex.toString())

        database.rawQuery(query, params).use { cursor ->
            while (cursor.moveToNext()) {
                val csId = cursor.getString(0)
                val brandName = cursor.getString(1) ?: "Desconocido"
                val remoteModel = cursor.getString(2) ?: ""
                val sourceName = cursor.getString(3) ?: "Elysium Nexus Data Fabric"
                val licenseSpdx = cursor.getString(4) ?: "MIT"
                val verificationStr = cursor.getString(6) ?: "UNVERIFIED"

                val codeSetResult = getCommandsForCodeSetInternal(database, csId)
                if (action in codeSetResult.commands && codeSetResult.commandBindings.isNotEmpty()) {
                    val verification = parseVerificationStatus(verificationStr)
                    results.add(
                        IrCodeSet(
                            id = csId,
                            brand = brandName,
                            modelPatterns = setOf(remoteModel),
                            remoteModels = if (remoteModel.isNotBlank()) setOf(remoteModel) else emptySet(),
                            commands = codeSetResult.commands,
                            commandSignalIds = codeSetResult.commandSignalIds,
                            commandBindings = codeSetResult.commandBindings,
                            selectedCommands = codeSetResult.selectedCommands,
                            provenance = CodeProvenance(
                                sourceName = sourceName,
                                sourceUrl = "",
                                licenseSpdx = licenseSpdx
                            ),
                            verification = verification
                        )
                    )
                }
            }
        }
        Log.d(TAG, "getCandidatePage(deviceType=$deviceType, from=$fromIndex, count=$count): ${results.size} candidates")
        results
    }

    private data class PendingBinding(
        val action: IrAction,
        val signal: IrSignal,
        val signalId: String,
        val bindingId: String,
        val codeSetId: String,
        val physicalSha256: String,
        val encodingType: String,
        val sourcePriority: Int,
        val sourceRevisionSha: String,
        val verificationStatus: String
    )

    /** P0-14: Parse verification_status from SQLite into VerificationStatus enum. */
    private fun parseVerificationStatus(status: String?): VerificationStatus = when {
        status == null -> VerificationStatus.UNVERIFIED
        status.startsWith("VERIFIED_LAB") -> VerificationStatus.VERIFIED_LAB
        status.startsWith("VERIFIED_COMMUNITY") -> VerificationStatus.VERIFIED_COMMUNITY
        status.startsWith("SESSION_VERIFIED") -> VerificationStatus.SESSION_VERIFIED
        status.startsWith("PARTIALLY_VERIFIED") -> VerificationStatus.PARTIALLY_VERIFIED
        status.startsWith("STRUCTURALLY_VALID") -> VerificationStatus.STRUCTURALLY_VALID
        status.startsWith("PROTOCOL_VALIDATED") -> VerificationStatus.PROTOCOL_VALIDATED
        status.startsWith("IMPORTED_UNREVIEWED") -> VerificationStatus.IMPORTED_UNREVIEWED
        status.startsWith("REGRESSION") -> VerificationStatus.REGRESSION
        status.startsWith("BLOCKED") -> VerificationStatus.BLOCKED
        else -> VerificationStatus.UNVERIFIED
    }

    /**
     * §7 Determining selection rank for a binding, per the dictamen policy:
     * VERIFIED_LAB > VERIFIED_COMMUNITY > SESSION_VERIFIED > PARTIALLY_VERIFIED >
     * STRUCTURALLY_VALID/PROTOCOL_VALIDATED > raw.
     */
    private fun verificationRank(codeSetStatus: String): Int = when {
        codeSetStatus == "VERIFIED_LAB" -> 6
        codeSetStatus == "VERIFIED_COMMUNITY" -> 5
        codeSetStatus == "SESSION_VERIFIED" -> 4
        codeSetStatus == "PARTIALLY_VERIFIED" -> 3
        codeSetStatus == "STRUCTURALLY_VALID" || codeSetStatus == "PROTOCOL_VALIDATED" -> 2
        codeSetStatus == "VERIFIED" -> 1
        else -> 0
    }

    private fun getCommandsForCodeSetInternal(
        database: SQLiteDatabase,
        codeSetId: String
    ): CodeSetCommandsResult {
        // §7 Collect ALL bindings per action, then select deterministically
        val allBindingsPerAction = mutableMapOf<IrAction, MutableList<PendingBinding>>()
        val allBindings = mutableListOf<CatalogCommandBinding>()

        // V06.2 Phase 2: JOIN protocol_definitions/protocol_variants via V5 FKs.
        // Legacy columns (codec_id, protocol_name_original, protocol_variant) are
        // selected as PROVENANCE ONLY — they document source identity, not runtime dispatch.
        val query = """
            SELECT a.canonical_key, sig.encoding_type, sig.carrier_hz,
                   sig.address_value, sig.sub_device_value, sig.command_value,
                   sig.pattern_blob, sig.id AS signal_id, cb.id AS binding_id,
                   sig.physical_sha256, cb.source_priority, sr.content_sha256 AS revision_sha,
                   cs.verification_status,
                   pd.id AS pd_id, pd.family_name, pd.carrier_hz AS pd_carrier_hz,
                   pv.id AS pv_id, pv.variant_name,
                   sig.evidence_level, sig.eligibility_status,
                   sig.codec_id, sig.protocol_name_original, sig.protocol_variant
            FROM command_bindings cb
            JOIN actions a ON cb.action_id = a.id
            JOIN signals sig ON cb.signal_id = sig.id
            JOIN code_sets cs ON cb.code_set_id = cs.id
            JOIN source_revisions sr ON cs.source_revision_id = sr.id
            LEFT JOIN protocol_definitions pd ON sig.protocol_definition_id = pd.id
            LEFT JOIN protocol_variants pv ON sig.protocol_variant_id = pv.id
            WHERE cb.code_set_id = ?
        """.trimIndent()

        database.rawQuery(query, arrayOf(codeSetId)).use { cursor ->
            while (cursor.moveToNext()) {
                val actionStr = cursor.getString(0)
                val encodingType = cursor.getString(1)
                val carrierHz = cursor.getInt(2)
                val address = cursor.getInt(3)
                val subDevice = cursor.getInt(4)
                val command = cursor.getInt(5)
                val blob = cursor.getBlob(6)
                val signalId = cursor.getString(7)
                val bindingId = cursor.getString(8)
                val physicalSha256 = cursor.getString(9) ?: signalId
                val sourcePriority = cursor.getInt(10)
                val revisionSha = cursor.getString(11) ?: "catalog-legacy"
                val codeSetStatus = cursor.getString(12) ?: "UNVERIFIED"
                // V06.2 Phase 2: V5 FK columns (runtime authority)
                val pdId = cursor.getString(13)
                val pdFamilyName = cursor.getString(14)
                val pdCarrierHz = cursor.getInt(15)
                val pvId = cursor.getString(16)
                val pvVariantName = cursor.getString(17)
                // Evidence/eligibility from signal
                val evidenceLevel = cursor.getString(18) ?: "SOURCE_IMPORTED"
                val eligibilityStatus = cursor.getString(19) ?: "RESEARCH_ONLY"
                // Legacy provenance (source identity, NOT runtime authority)
                val legacyCodecId = cursor.getString(20)
                val legacyProtocolName = cursor.getString(21)
                val legacyVariant = cursor.getString(22)

                val irAction = mapActionKeyToIrAction(actionStr) ?: continue

                val signal: IrSignal? = if (encodingType == "PARAMETRIC") {
                    // V06.2 Phase 2: Resolve protocol from catalog V5 FKs first.
                    // Fall back to legacy codec_id only if FK is null (backward compat).
                    val protocol = resolveProtocolFromFamily(pdFamilyName)
                        ?: legacyCodecId?.let { ProtocolCodecRegistry.getCodec(it)?.protocol }
                    if (protocol == null) {
                        Log.w(TAG, "No protocol resolution for signalId=$signalId " +
                            "(pdFamilyName=$pdFamilyName, legacyCodecId=$legacyCodecId). Skipping.")
                        null
                    } else if (protocol == IrProtocol.Raw) {
                        null
                    } else {
                        // V06.2 Phase 2: Use pv.variant_name directly (e.g. "SIRC_12").
                        // Fall back to legacy string only if pv is null.
                        val variantHint = pvVariantName ?: legacyProtocolName ?: legacyVariant
                        val resolved = ProtocolCodecRegistry.resolve(
                            legacyCodecId ?: pdFamilyName ?: "",
                            variantHint
                        )
                        when (resolved) {
                            is CodecResolution.Resolved -> {
                                // V0.7 Phase 5 & 8: Commercial codec policy enforcement.
                                // EXPERIMENTAL codecs (RC5, RC6, Kaseikyo) are LAB ONLY and blocked from candidate pools.
                                if (!ProtocolCodecRegistry.isCodecTransmittable(resolved.codec.codecId)) {
                                    Log.d(TAG, "Codec '${resolved.codec.codecId}' is EXPERIMENTAL/LAB_ONLY. Skipping from candidate query.")
                                    null
                                } else {
                                    IrSignal.Encoded(
                                        carrierHz = if (pdCarrierHz > 0) pdCarrierHz else carrierHz,
                                        protocol = protocol,
                                        address = address,
                                        subDevice = if (subDevice >= 0) subDevice else null,
                                        command = command,
                                        codecId = legacyCodecId ?: pdFamilyName ?: "",
                                        variantId = pvVariantName ?: resolved.variant?.variantId
                                    )
                                }
                            }
                            is CodecResolution.VariantAmbiguous -> {
                                Log.w(TAG, "Variant ambiguous for signalId=$signalId " +
                                    "(${resolved.candidates.size} candidates). Skipping.")
                                null
                            }
                            is CodecResolution.VariantUnsupported -> {
                                Log.w(TAG, "Variant '${variantHint}' unsupported for " +
                                    "codec '${legacyCodecId}'. Skipping.")
                                null
                            }
                            else -> null
                        }
                    }
                } else if (encodingType == "RAW" && blob != null) {
                    val pattern = decompressPattern(blob)
                    if (pattern != null && pattern.all { it > 0 }) {
                        IrSignal.Raw(carrierHz = carrierHz, patternUs = pattern)
                    } else null
                } else null

                if (signal != null) {
                    val pending = PendingBinding(irAction, signal, signalId, bindingId, codeSetId, physicalSha256, encodingType, sourcePriority, revisionSha, codeSetStatus)
                    allBindingsPerAction.getOrPut(irAction) { mutableListOf() }.add(pending)
                    allBindings.add(
                        CatalogCommandBinding(
                            bindingId = bindingId,
                            codeSetId = codeSetId,
                            action = irAction,
                            signalId = signalId,
                            physicalSha256 = physicalSha256,
                            signal = signal,
                            sourceRevisionId = revisionSha
                        )
                    )
                }
            }
        }

        // P0-15: Deterministic selection policy (clear priority order):
        //   1. VERIFIED_LAB > VERIFIED_COMMUNITY > PARTIALLY_VERIFIED > UNVERIFIED
        //   2. RAW signals preferred over PARAMETRIC
        //   3. Higher source_priority wins
        //   4. Stable tie-break by bindingId (alphabetical)
        // V06.2 PR3 Phase 9: selectedCommands built from the WINNING binding —
        // all parallel projections derive from it, never the reverse.
        val commands = mutableMapOf<IrAction, IrSignal>()
        val commandSignalIds = mutableMapOf<IrAction, String>()
        val selectedCommands = mutableMapOf<IrAction, SelectedCommandBinding>()
        val codeSetStatus = allBindingsPerAction.values
            .flatten()
            .maxByOrNull { verificationRank(it.verificationStatus) }
            ?.verificationStatus
            ?: "UNVERIFIED"
        val verification = parseVerificationStatus(codeSetStatus)
        for ((action, bindings) in allBindingsPerAction) {
            val selected = bindings.sortedWith(
                compareByDescending<PendingBinding> { verificationRank(it.verificationStatus) }
                    .thenByDescending { if (it.encodingType == "RAW") 1 else 0 }
                    .thenByDescending { it.sourcePriority }
                    .thenBy { it.bindingId }
            ).firstOrNull() ?: continue
            commands[action] = selected.signal
            commandSignalIds[action] = selected.signalId
            selectedCommands[action] = SelectedCommandBinding(
                bindingId = selected.bindingId,
                codeSetId = codeSetId,
                action = action,
                signalId = selected.signalId,
                signal = selected.signal,
                physicalSha256 = selected.physicalSha256,
                sourceId = selected.sourceRevisionSha,
                sourceRevisionId = selected.sourceRevisionSha,
                verificationStatus = verification,
                evidenceLevel = when (verification) {
                    VerificationStatus.VERIFIED_LAB -> EvidenceLevel.LAB_MATRIX_VERIFIED
                    VerificationStatus.VERIFIED_COMMUNITY -> EvidenceLevel.EXTERNAL_HIL_VERIFIED
                    VerificationStatus.SESSION_VERIFIED -> EvidenceLevel.SESSION_VERIFIED
                    VerificationStatus.PARTIALLY_VERIFIED -> EvidenceLevel.MODEL_INFERRED
                    else -> EvidenceLevel.INTERNAL_UNVERIFIED
                }
            )
        }

        return CodeSetCommandsResult(commands, commandSignalIds, allBindings, selectedCommands)
    }

    override suspend fun getSignal(signalId: String): IrSignal? = withContext(Dispatchers.IO) {
        val database = getDatabase()
        var resultSignal: IrSignal? = null

        // V06.2 Phase 2: JOIN protocol_definitions/protocol_variants via V5 FKs.
        val query = """
            SELECT sig.encoding_type, sig.codec_id, sig.carrier_hz, sig.address_value,
                   sig.sub_device_value, sig.command_value, sig.pattern_blob,
                   pd.family_name, pd.carrier_hz AS pd_carrier_hz,
                   pv.variant_name,
                   sig.protocol_name_original, sig.protocol_variant
            FROM signals sig
            LEFT JOIN protocol_definitions pd ON sig.protocol_definition_id = pd.id
            LEFT JOIN protocol_variants pv ON sig.protocol_variant_id = pv.id
            WHERE sig.id = ?
        """.trimIndent()

        database.rawQuery(query, arrayOf(signalId)).use { cursor ->
            if (cursor.moveToFirst()) {
                val encodingType = cursor.getString(0)
                val legacyCodecId = cursor.getString(1)
                val carrierHz = cursor.getInt(2)
                val address = cursor.getInt(3)
                val subDevice = cursor.getInt(4)
                val command = cursor.getInt(5)
                val blob = cursor.getBlob(6)
                val pdFamilyName = cursor.getString(7)
                val pdCarrierHz = cursor.getInt(8)
                val pvVariantName = cursor.getString(9)
                val legacyProtocolName = cursor.getString(10)
                val legacyVariant = cursor.getString(11)

                if (encodingType == "PARAMETRIC") {
                    val protocol = resolveProtocolFromFamily(pdFamilyName)
                        ?: legacyCodecId?.let { ProtocolCodecRegistry.getCodec(it)?.protocol }
                    if (protocol != null && protocol != IrProtocol.Raw) {
                        val variantHint = pvVariantName ?: legacyProtocolName ?: legacyVariant
                        val resolved = ProtocolCodecRegistry.resolve(
                            legacyCodecId ?: pdFamilyName ?: "",
                            variantHint
                        )
                        if (resolved is CodecResolution.Resolved) {
                            resultSignal = IrSignal.Encoded(
                                carrierHz = if (pdCarrierHz > 0) pdCarrierHz else carrierHz,
                                protocol = protocol,
                                address = address,
                                subDevice = if (subDevice >= 0) subDevice else null,
                                command = command,
                                codecId = legacyCodecId ?: pdFamilyName ?: "",
                                variantId = pvVariantName ?: resolved.variant?.variantId
                            )
                        } else if (resolved is CodecResolution.VariantAmbiguous) {
                            Log.w(TAG, "Variant ambiguous in single-signal lookup: ${resolved.candidates.size} candidates")
                        } else if (resolved is CodecResolution.VariantUnsupported) {
                            Log.w(TAG, "Variant '${variantHint}' unsupported in single-signal lookup")
                        }
                    }
                } else if (encodingType == "RAW" && blob != null) {
                    val pattern = decompressPattern(blob)
                    if (pattern != null && pattern.all { it > 0 }) {
                        resultSignal = IrSignal.Raw(carrierHz = carrierHz, patternUs = pattern)
                    }
                }
            }
        }

        resultSignal
    }

    override suspend fun resolveExecutableSignal(
        signalId: String,
        policy: RuntimePolicy
    ): IrSignal? = withContext(Dispatchers.IO) {
        val signal = getSignal(signalId)
        if (signal != null && RuntimeSignalPolicy.isExecutable(signal, policy)) {
            signal
        } else {
            if (signal != null) {
                Log.d(
                    TAG,
                    "Signal $signalId blocked by RuntimePolicy=$policy " +
                        "(codecId=${(signal as? IrSignal.Encoded)?.codecId}). LAB_ONLY."
                )
            }
            null
        }
    }

    override suspend fun searchBrands(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val database = getDatabase()
        val results = mutableListOf<String>()

        database.rawQuery(
            "SELECT DISTINCT display_name FROM brands WHERE display_name LIKE ? OR normalized_name LIKE ? ORDER BY display_name LIMIT 50",
            arrayOf("%$query%", "%${query.lowercase()}%")
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(cursor.getString(0))
            }
        }
        results
    }

    override suspend fun searchDevices(query: String): List<DeviceSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val database = getDatabase()
        val results = mutableListOf<DeviceSearchResult>()

        val searchQuery = """
            SELECT r.id, b.display_name, dt.canonical_name, r.display_remote_model, s.display_name
            FROM remotes r
            JOIN brands b ON r.brand_id = b.id
            JOIN device_types dt ON r.device_type_id = dt.id
            JOIN source_files sf ON r.source_file_id = sf.id
            JOIN source_revisions sr ON sf.source_revision_id = sr.id
            JOIN sources s ON sr.source_id = s.id
            WHERE (b.display_name LIKE ? OR r.display_remote_model LIKE ?)
              AND s.production_approved = 1
            LIMIT 100
        """.trimIndent()

        val q = "%$query%"
        database.rawQuery(searchQuery, arrayOf(q, q)).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(
                    DeviceSearchResult(
                        id = cursor.getString(0),
                        brand = cursor.getString(1),
                        model = cursor.getString(3) ?: "",
                        category = cursor.getString(2) ?: "TV",
                        remoteModel = cursor.getString(3) ?: "",
                        source = cursor.getString(4) ?: "Elysium Data Fabric"
                    )
                )
            }
        }
        results
    }

    override suspend fun getStats(): CatalogStats = withContext(Dispatchers.IO) {
        val database = getDatabase()
        var brands = 0; var types = 0; var remotes = 0
        var codeSets = 0; var signals = 0; var bindings = 0

        database.rawQuery("SELECT COUNT(*) FROM brands", null).use { if (it.moveToFirst()) brands = it.getInt(0) }
        database.rawQuery("SELECT COUNT(*) FROM device_types", null).use { if (it.moveToFirst()) types = it.getInt(0) }
        database.rawQuery("SELECT COUNT(*) FROM remotes", null).use { if (it.moveToFirst()) remotes = it.getInt(0) }
        database.rawQuery("SELECT COUNT(*) FROM code_sets", null).use { if (it.moveToFirst()) codeSets = it.getInt(0) }
        database.rawQuery("SELECT COUNT(*) FROM signals", null).use { if (it.moveToFirst()) signals = it.getInt(0) }
        database.rawQuery("SELECT COUNT(*) FROM command_bindings", null).use { if (it.moveToFirst()) bindings = it.getInt(0) }

        CatalogStats(brands, types, remotes, codeSets, signals, bindings, 7)
    }

    // §7 New interface methods — authoritative re-read and metadata

    override suspend fun getCommandsForCodeSet(codeSetId: String): Map<IrAction, List<CatalogCommandBinding>> = withContext(Dispatchers.IO) {
        val database = getDatabase()
        val result = mutableMapOf<IrAction, MutableList<CatalogCommandBinding>>()

        // V06.2 Phase 2: JOIN protocol_definitions/protocol_variants via V5 FKs.
        val query = """
            SELECT a.canonical_key, sig.encoding_type, sig.carrier_hz,
                   sig.address_value, sig.sub_device_value, sig.command_value,
                   sig.pattern_blob, sig.id AS signal_id, cb.id AS binding_id,
                   sig.physical_sha256,
                   pd.family_name, pd.carrier_hz AS pd_carrier_hz,
                   pv.variant_name,
                   sig.codec_id, sig.protocol_name_original, sig.protocol_variant
            FROM command_bindings cb
            JOIN actions a ON cb.action_id = a.id
            JOIN signals sig ON cb.signal_id = sig.id
            LEFT JOIN protocol_definitions pd ON sig.protocol_definition_id = pd.id
            LEFT JOIN protocol_variants pv ON sig.protocol_variant_id = pv.id
            WHERE cb.code_set_id = ?
        """.trimIndent()

        database.rawQuery(query, arrayOf(codeSetId)).use { cursor ->
            while (cursor.moveToNext()) {
                val actionStr = cursor.getString(0) ?: continue
                val encodingType = cursor.getString(1)
                val carrierHz = cursor.getInt(2)
                val address = cursor.getInt(3)
                val subDevice = cursor.getInt(4)
                val command = cursor.getInt(5)
                val blob = cursor.getBlob(6)
                val signalId = cursor.getString(7)
                val bindingId = cursor.getString(8)
                val physicalSha256 = cursor.getString(9) ?: signalId
                val pdFamilyName = cursor.getString(10)
                val pdCarrierHz = cursor.getInt(11)
                val pvVariantName = cursor.getString(12)
                val legacyCodecId = cursor.getString(13)
                val legacyProtocolName = cursor.getString(14)
                val legacyVariant = cursor.getString(15)

                val irAction = mapActionKeyToIrAction(actionStr) ?: continue

                val signal: IrSignal? = if (encodingType == "PARAMETRIC") {
                    val protocol = resolveProtocolFromFamily(pdFamilyName)
                        ?: legacyCodecId?.let { ProtocolCodecRegistry.getCodec(it)?.protocol }
                    protocol?.let {
                        if (it == IrProtocol.Raw) return@let null
                        val variantHint = pvVariantName ?: legacyProtocolName ?: legacyVariant
                        val resolved = ProtocolCodecRegistry.resolve(
                            legacyCodecId ?: pdFamilyName ?: "",
                            variantHint
                        )
                        if (resolved is CodecResolution.Resolved) {
                            IrSignal.Encoded(
                                carrierHz = if (pdCarrierHz > 0) pdCarrierHz else carrierHz,
                                protocol = it,
                                address = address,
                                subDevice = if (subDevice >= 0) subDevice else null,
                                command = command,
                                codecId = legacyCodecId ?: pdFamilyName ?: "",
                                variantId = pvVariantName ?: resolved.variant?.variantId
                            )
                        } else {
                            if (resolved is CodecResolution.VariantAmbiguous) {
                                Log.w(TAG, "Variant ambiguous in candidates query: ${resolved.candidates.size} candidates")
                            } else if (resolved is CodecResolution.VariantUnsupported) {
                                Log.w(TAG, "Variant '${variantHint}' unsupported in candidates query")
                            }
                            null
                        }
                    }
                } else if (encodingType == "RAW" && blob != null) {
                    val pattern = decompressPattern(blob)
                    if (pattern != null && pattern.all { it > 0 }) {
                        IrSignal.Raw(carrierHz = carrierHz, patternUs = pattern)
                    } else null
                } else null

                if (signal != null && RuntimeSignalPolicy.isExecutable(signal)) {
                    result.getOrPut(irAction) { mutableListOf() }.add(
                        CatalogCommandBinding(
                            bindingId = bindingId,
                            codeSetId = codeSetId,
                            action = irAction,
                            signalId = signalId,
                            physicalSha256 = physicalSha256,
                            signal = signal
                        )
                    )
                }
            }
        }

        result
    }

    override suspend fun getSignalMetadata(signalId: String): SignalMetadata? = withContext(Dispatchers.IO) {
        val database = getDatabase()
        var metadata: SignalMetadata? = null

        // V06.2 Phase 6: Fix sr.version → sr.commit_sha (V5 schema has commit_sha, not version).
        val query = """
            SELECT sig.encoding_type, sig.codec_id, sig.carrier_hz,
                   sig.address_value, sig.sub_device_value, sig.command_value,
                   sig.physical_sha256, sr.commit_sha AS source_revision_sha
            FROM signals sig
            LEFT JOIN command_bindings cb ON cb.signal_id = sig.id
            LEFT JOIN code_sets cs ON cb.code_set_id = cs.id
            LEFT JOIN source_revisions sr ON cs.source_revision_id = sr.id
            WHERE sig.id = ?
            LIMIT 1
        """.trimIndent()

        database.rawQuery(query, arrayOf(signalId)).use { cursor ->
            if (cursor.moveToFirst()) {
                metadata = SignalMetadata(
                    signalId = signalId,
                    encodingType = cursor.getString(0) ?: "",
                    codecId = cursor.getString(1),
                    carrierHz = cursor.getInt(2),
                    addressValue = cursor.getInt(3),
                    subDeviceValue = cursor.getInt(4),
                    commandValue = cursor.getInt(5),
                    physicalSha256 = cursor.getString(6) ?: "",
                    sourceRevisionSha = cursor.getString(7)
                )
            }
        }

        metadata
    }

    override suspend fun getCodeSet(codeSetId: String): IrCodeSet? = withContext(Dispatchers.IO) {
        val database = getDatabase()
        var result: IrCodeSet? = null

        val query = """
            SELECT cs.id, b.display_name AS brand_name, r.display_remote_model,
                   s.id AS source_name, s.license_id, dt.canonical_name AS device_type,
                   cs.verification_status
            FROM code_sets cs
            JOIN remotes r ON cs.remote_id = r.id
            JOIN brands b ON r.brand_id = b.id
            JOIN device_types dt ON r.device_type_id = dt.id
            JOIN source_revisions sr ON cs.source_revision_id = sr.id
            JOIN sources s ON sr.source_id = s.id
            WHERE cs.id = ?
        """.trimIndent()

        database.rawQuery(query, arrayOf(codeSetId)).use { cursor ->
            if (cursor.moveToFirst()) {
                val brandName = cursor.getString(1) ?: ""
                val remoteModel = cursor.getString(2) ?: ""
                val sourceName = cursor.getString(3) ?: "Elysium Nexus Data Fabric"
                val licenseSpdx = cursor.getString(4) ?: "MIT"
                val verificationStr = cursor.getString(6) ?: "UNVERIFIED"

                val codeSetResult = getCommandsForCodeSetInternal(database, codeSetId)
                if (codeSetResult.commands.isNotEmpty()) {
                    val verification = parseVerificationStatus(verificationStr)
                    result = IrCodeSet(
                        id = codeSetId,
                        brand = brandName,
                        modelPatterns = setOf(remoteModel),
                        remoteModels = if (remoteModel.isNotBlank()) setOf(remoteModel) else emptySet(),
                        commands = codeSetResult.commands,
                        commandSignalIds = codeSetResult.commandSignalIds,
                        commandBindings = codeSetResult.commandBindings,
                        selectedCommands = codeSetResult.selectedCommands,
                        provenance = CodeProvenance(
                            sourceName = sourceName,
                            sourceUrl = "",
                            licenseSpdx = licenseSpdx
                        ),
                        verification = verification
                    )
                }
            }
        }

        result
    }

    /**
     * P0-8: Match protocol variant from SQLite's protocol_name_original or codec_id
     * against the CodecSpec's registered variants. This ensures SIRC12/15/20 are
     * correctly distinguished instead of always picking the first variant.
     */
    private fun resolveVariant(
        codecSpec: CodecSpec,
        protocolNameOriginal: String?,
        protocolVariant: String?
    ): ProtocolVariant? {
        // Try matching by protocol_name_original first (e.g., "SIRC15")
        if (!protocolNameOriginal.isNullOrBlank()) {
            val match = codecSpec.variants.firstOrNull { v ->
                v.variantId.equals(protocolNameOriginal, ignoreCase = true) ||
                v.variantId.replace("_", "").equals(protocolNameOriginal.replace(" ", ""), ignoreCase = true)
            }
            if (match != null) return match
        }
        // Try matching by protocol_variant (e.g., carrier Hz or other metadata)
        if (!protocolVariant.isNullOrBlank()) {
            val match = codecSpec.variants.firstOrNull { v ->
                v.variantId.equals(protocolVariant, ignoreCase = true)
            }
            if (match != null) return match
        }
        // Fallback: try matching codec_id itself against variants (e.g., "SIRC" → SIRC_12)
        // Only if there's exactly one variant, use it
        return if (codecSpec.variants.size == 1) codecSpec.variants.firstOrNull() else null
    }

    private fun mapActionKeyToIrAction(actionKey: String): IrAction? = try {
        IrAction.valueOf(actionKey)
    } catch (e: Exception) {
        when (actionKey) {
            "POWER_TOGGLE", "POWER" -> IrAction.POWER_TOGGLE
            "POWER_ON" -> IrAction.POWER_ON
            "POWER_OFF" -> IrAction.POWER_OFF
            "VOLUME_UP", "VOL_UP" -> IrAction.VOLUME_UP
            "VOLUME_DOWN", "VOL_DN" -> IrAction.VOLUME_DOWN
            "MUTE" -> IrAction.MUTE
            "CHANNEL_UP", "CH_UP" -> IrAction.CHANNEL_UP
            "CHANNEL_DOWN", "CH_DN" -> IrAction.CHANNEL_DOWN
            "INPUT", "SOURCE" -> IrAction.INPUT
            "MENU" -> IrAction.MENU
            "OK", "ENTER" -> IrAction.OK
            "UP" -> IrAction.UP
            "DOWN" -> IrAction.DOWN
            "LEFT" -> IrAction.LEFT
            "RIGHT" -> IrAction.RIGHT
            "BACK", "RETURN" -> IrAction.BACK
            "HOME" -> IrAction.HOME
            "PLAY" -> IrAction.PLAY
            "PAUSE" -> IrAction.PAUSE
            "STOP" -> IrAction.STOP
            else -> null
        }
    }

    private fun decompressPattern(blob: ByteArray): IntArray? {
        return try {
            val inflater = Inflater()
            inflater.setInput(blob)
            val output = ByteArray(MAX_PATTERN_SLICES * 4)
            val decompressedLen = inflater.inflate(output)
            inflater.end()

            if (decompressedLen <= 0 || decompressedLen % 4 != 0) return null
            val count = decompressedLen / 4
            val buffer = ByteBuffer.wrap(output, 0, decompressedLen).order(ByteOrder.LITTLE_ENDIAN)
            val result = IntArray(count)
            for (i in 0 until count) {
                val valUs = buffer.getInt()
                if (valUs <= 0) return null
                result[i] = valUs
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    // P1-PROVENANCE: Multi-source provenance lookup

    override suspend fun getSignalProvenance(signalId: String): List<SignalProvenance> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SignalProvenance>()

        // V06.2 Phase 6: Query catalog signal_sources table (immutable catalog DB).
        // signal_sources is the canonical source of truth for per-signal source attribution.
        // User Room DB signal_sources is SECONDARY — used only for session-verified extras.
        try {
            val database = getDatabase()
            val query = """
                SELECT ss.source_id, sr.commit_sha, ss.evidence_level,
                       ss.device_model, ss.notes, s.license_id
                FROM signal_sources ss
                JOIN source_revisions sr ON ss.source_revision_id = sr.id
                JOIN sources s ON ss.source_id = s.id
                WHERE ss.signal_id = ?
                ORDER BY ss.created_at DESC
            """.trimIndent()
            database.rawQuery(query, arrayOf(signalId)).use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(SignalProvenance(
                        signalId = signalId,
                        sourceId = cursor.getString(0) ?: "",
                        sourceRevisionId = cursor.getString(1) ?: "",
                        evidenceLevel = cursor.getString(2) ?: "SOURCE_IMPORTED",
                        verificationSource = cursor.getString(4),
                        verifiedAtEpochMs = null,
                        deviceModel = cursor.getString(3),
                        notes = cursor.getString(4)
                    ))
                }
            }
        } catch (_: Exception) {
            // signal_sources table may not exist in older catalog versions
        }

        // Secondary: user Room DB signal_sources (session-verified extras from profile)
        try {
            if (context != null) {
                val userDb = com.elysium.nexus.fabric.profile.db.ElysiumUserDatabase.getInstance(context)
                val entities = userDb.profileDao().getSourcesForSignal(signalId)
                for (entity in entities) {
                    results.add(SignalProvenance(
                        signalId = entity.signalId,
                        sourceId = entity.sourceId,
                        sourceRevisionId = entity.sourceRevisionId,
                        evidenceLevel = entity.evidenceLevel,
                        verificationSource = entity.verificationSource,
                        verifiedAtEpochMs = entity.verifiedAtEpochMs?.takeIf { it > 0 },
                        deviceModel = entity.deviceModel,
                        notes = entity.notes
                    ))
                }
            }
        } catch (_: Exception) {
            // signal_sources table may not exist in older user DB versions
        }

        // Fallback: derive provenance from command_bindings → source_revisions
        if (results.isEmpty()) {
            val database = getDatabase()
            // V06.2 Phase 6: Fix sr.version → sr.commit_sha (V5 schema).
            val fallbackQuery = """
                SELECT DISTINCT sr.commit_sha, s.id, s.license_id
                FROM command_bindings cb
                JOIN code_sets cs ON cb.code_set_id = cs.id
                JOIN source_revisions sr ON cs.source_revision_id = sr.id
                JOIN sources s ON sr.source_id = s.id
                WHERE cb.signal_id = ?
            """.trimIndent()
            database.rawQuery(fallbackQuery, arrayOf(signalId)).use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(SignalProvenance(
                        signalId = signalId,
                        sourceId = cursor.getString(1) ?: "",
                        sourceRevisionId = cursor.getString(0) ?: "",
                        evidenceLevel = "INTERNAL_UNVERIFIED",
                        verificationSource = null,
                        verifiedAtEpochMs = null,
                        deviceModel = null,
                        notes = "Derived from source_revisions (legacy fallback)"
                    ))
                }
            }
        }

        results
    }
}
