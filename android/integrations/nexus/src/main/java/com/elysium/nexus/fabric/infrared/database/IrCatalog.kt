package com.elysium.nexus.fabric.infrared.database

import com.elysium.nexus.core.device.CatalogCommandBinding
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.fabric.infrared.RuntimePolicy
import com.elysium.nexus.fabric.infrared.RuntimeSignalPolicy

data class DeviceSearchResult(
    val id: String,
    val brand: String,
    val model: String,
    val category: String,
    val remoteModel: String = "",
    val source: String = ""
)

data class CatalogStats(
    val brands: Int,
    val deviceTypes: Int,
    val remotes: Int,
    val encodedCommands: Int,
    val rawCommands: Int,
    val totalCommands: Int,
    val protocols: Int
)

data class SignalMetadata(
    val signalId: String,
    val encodingType: String,
    val codecId: String?,
    val carrierHz: Int,
    val addressValue: Int,
    val subDeviceValue: Int,
    val commandValue: Int,
    val physicalSha256: String,
    val sourceRevisionSha: String?
)

/**
 * V06.2 Phase 2: ONE RUNTIME PROTOCOL AUTHORITY.
 *
 * Resolved from catalog V5 FKs (protocol_definitions + protocol_variants),
 * NOT from legacy strings (codec_id, protocol_name_original, protocol_variant).
 *
 * Legacy fields are kept as SOURCE PROVENANCE ONLY — they document where
 * the signal came from, but do NOT drive runtime codec selection.
 */
data class RuntimeProtocolBinding(
    /** protocol_definitions.id — the authoritative protocol family FK. */
    val definitionId: String?,
    /** protocol_definitions.family_name — e.g. "NEC", "SIRC", "Samsung". */
    val familyName: String?,
    /** protocol_variants.id — the authoritative variant FK. */
    val variantId: String?,
    /** protocol_variants.variant_name — e.g. "SIRC_12", "NEC_32". */
    val variantName: String?,
    /** Carrier from protocol_definitions (canonical), or signal carrier_hz as fallback. */
    val carrierHz: Int,
    /** Address from signal. */
    val address: Int?,
    /** Sub-device from signal. */
    val subDevice: Int?,
    /** Command from signal. */
    val command: Int?,
    /** Evidence level from signal (SOURCE_IMPORTED, SESSION_VERIFIED, etc.). */
    val evidenceLevel: String,
    /** Eligibility status from signal (PROBE_ELIGIBLE, CLAIM_ELIGIBLE, etc.). */
    val eligibilityStatus: String,
    /** PROVENANCE ONLY: original codec_id from ingestion (not for runtime dispatch). */
    val legacyCodecId: String?,
    /** PROVENANCE ONLY: original protocol name from source file. */
    val legacyProtocolName: String?,
    /** PROVENANCE ONLY: original variant string from source file. */
    val legacyVariant: String?
)

/**
 * P1-PROVENANCE: Multi-source provenance for a single signal.
 * A signal may originate from Flipper, SmartIR, local capture, etc.
 */
data class SignalProvenance(
    val signalId: String,
    val sourceId: String,
    val sourceRevisionId: String,
    val evidenceLevel: String,
    val verificationSource: String?,
    val verifiedAtEpochMs: Long?,
    val deviceModel: String?,
    val notes: String?
)

/**
 * Domain interface for querying IR remote control catalogs.
 * Decouples the UI and probe engine from concrete SQLite or in-memory persistence.
 */
interface IrCatalog {
    /**
     * Search brands in the catalog matching [query].
     */
    suspend fun searchBrands(query: String): List<String>

    /**
     * Search specific device remotes in the catalog matching [query].
     */
    suspend fun searchDevices(query: String): List<DeviceSearchResult>

    /**
     * Query candidate code sets for a given brand and action.
     */
    suspend fun getCandidatesForBrand(
        brand: String,
        deviceType: String = "TV",
        action: IrAction = IrAction.VOLUME_UP
    ): List<IrCodeSet>

    /**
     * Universal sweep: query ALL production-approved code sets of a device
     * type that contain [action], across every brand in the catalog.
     * Powers the "Control Universal" auto-sweep experience.
     *
     * P0-8: Progressive search — returns candidates ordered by probability:
     *   1. Popular global brands (Samsung, LG, Sony, Panasonic, Philips)
     *   2. Regional brands (Sankey, Kintech, Kalley, Hisense, TCL)
     *   3. All remaining brands
     * No LIMIT 400 — caller drains via [ProbeCursor.nextCandidate].
     */
    suspend fun getAllCandidates(
        deviceType: String = "TV",
        action: IrAction = IrAction.VOLUME_UP,
        limit: Int = 400
    ): List<IrCodeSet>

    // V0.6.2 PR3 Phase 14 — Paged probe: bounded-memory candidate access

    /**
     * Count of production-approved code sets containing [action] for [deviceType].
     * Used to initialise the [CandidatePager] total count.
     */
    suspend fun getCandidateCount(
        deviceType: String = "TV",
        action: IrAction = IrAction.VOLUME_UP
    ): Int

    /**
     * A single page of candidates, deterministically ordered by
     * (brand display_name, code_set id).  No duplicates across pages.
     * [fromIndex] is 0-based inclusive; [count] is the page size.
     */
    suspend fun getCandidatePage(
        deviceType: String = "TV",
        action: IrAction = IrAction.VOLUME_UP,
        fromIndex: Int,
        count: Int
    ): List<IrCodeSet>

    /**
     * Phase A — Multi-key universal sweep: count of production-approved
     * code sets for [deviceType] containing ANY of [actions]. The sweep
     * pool is the union of the probe keys (VOLUME_UP ∪ MUTE ∪ POWER_TOGGLE),
     * so TVs reachable only via POWER or MUTE are not lost.
     */
    suspend fun getCandidateCountForActions(
        deviceType: String = "TV",
        actions: List<IrAction>
    ): Int

    /**
     * Phase A — Multi-key universal sweep: a single page of candidates
     * containing ANY of [actions], deterministically ordered by
     * (brand display_name, code_set id).
     */
    suspend fun getCandidatePageForActions(
        deviceType: String = "TV",
        actions: List<IrAction>,
        fromIndex: Int,
        count: Int
    ): List<IrCodeSet>

    /**
     * Retrieve single physical [IrSignal] by exact signal ID.
     */
    suspend fun getSignal(signalId: String): IrSignal?

    /**
     * V0.7 Phase 5 — The SINGLE executable-signal entry point for every
     * runtime path (probe, saved profiles, brand lookup, direct lookup,
     * automation).
     *
     * Returns the signal only when [RuntimeSignalPolicy.isExecutable]
     * admits it under the given [RuntimePolicy]. Default [RuntimePolicy.COMMERCIAL]
     * blocks EXPERIMENTAL codecs (RC5, RC6, Kaseikyo) and unknown codecs —
     * zero bypass, fail closed.
     */
    suspend fun resolveExecutableSignal(
        signalId: String,
        policy: RuntimePolicy = RuntimePolicy.COMMERCIAL
    ): IrSignal?

    /**
     * §7 Retrieve all command bindings for a code set with deterministic selection.
     */
    suspend fun getCommandsForCodeSet(codeSetId: String): Map<IrAction, List<CatalogCommandBinding>>

    /**
     * §7 Retrieve signal metadata including source revision SHA.
     */
    suspend fun getSignalMetadata(signalId: String): SignalMetadata?

    /**
     * §7 Retrieve a full code set by ID for authoritative re-read during installation.
     */
    suspend fun getCodeSet(codeSetId: String): IrCodeSet?

    /**
     * Query overall catalog statistics.
     */
    suspend fun getStats(): CatalogStats

    /**
     * P1-PROVENANCE: Get all source provenance records for a signal.
     * A single signal may have multiple sources (Flipper + SmartIR + local capture).
     */
    suspend fun getSignalProvenance(signalId: String): List<SignalProvenance>
}

/**
 * In-Memory IR Catalog implementation for fast JVM testing and standalone mocks.
 */
class InMemoryIrCatalog(
    private val candidateMap: Map<String, List<IrCodeSet>> = emptyMap(),
    private val devices: List<DeviceSearchResult> = emptyList(),
    private val signalMap: Map<String, IrSignal> = emptyMap()
) : IrCatalog {

    override suspend fun searchBrands(query: String): List<String> {
        return candidateMap.keys.filter { it.contains(query, ignoreCase = true) }
    }

    override suspend fun searchDevices(query: String): List<DeviceSearchResult> {
        return devices.filter {
            it.brand.contains(query, ignoreCase = true) ||
            it.model.contains(query, ignoreCase = true)
        }
    }

    override suspend fun getCandidatesForBrand(
        brand: String,
        deviceType: String,
        action: IrAction
    ): List<IrCodeSet> {
        return candidateMap[brand] ?: candidateMap.entries.firstOrNull {
            it.key.equals(brand, ignoreCase = true)
        }?.value ?: emptyList()
    }

    override suspend fun getAllCandidates(
        deviceType: String,
        action: IrAction,
        limit: Int
    ): List<IrCodeSet> {
        return candidateMap.values.flatten()
            .filter { action in it.commands }
            .take(limit)
    }

    override suspend fun getCandidateCount(
        deviceType: String,
        action: IrAction
    ): Int = candidateMap.values.flatten().count { action in it.commands }

    override suspend fun getCandidatePage(
        deviceType: String,
        action: IrAction,
        fromIndex: Int,
        count: Int
    ): List<IrCodeSet> = candidateMap.values.flatten()
        .filter { action in it.commands }
        .sortedBy { it.brand }
        .drop(fromIndex)
        .take(count)

    override suspend fun getCandidateCountForActions(
        deviceType: String,
        actions: List<IrAction>
    ): Int = candidateMap.values.flatten().count { cs -> actions.any { it in cs.commands } }

    override suspend fun getCandidatePageForActions(
        deviceType: String,
        actions: List<IrAction>,
        fromIndex: Int,
        count: Int
    ): List<IrCodeSet> = candidateMap.values.flatten()
        .filter { cs -> actions.any { it in cs.commands } }
        .sortedBy { it.brand }
        .drop(fromIndex)
        .take(count)

    override suspend fun getSignal(signalId: String): IrSignal? {
        return signalMap[signalId]
    }

    override suspend fun resolveExecutableSignal(
        signalId: String,
        policy: RuntimePolicy
    ): IrSignal? {
        val signal = signalMap[signalId] ?: return null
        return if (RuntimeSignalPolicy.isExecutable(signal, policy)) signal else null
    }

    override suspend fun getCommandsForCodeSet(codeSetId: String): Map<IrAction, List<CatalogCommandBinding>> {
        return emptyMap()
    }

    override suspend fun getSignalMetadata(signalId: String): SignalMetadata? {
        return null
    }

    override suspend fun getCodeSet(codeSetId: String): IrCodeSet? {
        return candidateMap.values.flatten().firstOrNull { it.id == codeSetId }
    }

    override suspend fun getStats(): CatalogStats {
        val totalRemotes = candidateMap.values.sumOf { it.size }
        return CatalogStats(
            brands = candidateMap.size,
            deviceTypes = 1,
            remotes = totalRemotes,
            encodedCommands = totalRemotes,
            rawCommands = 0,
            totalCommands = totalRemotes,
            protocols = 1
        )
    }

    override suspend fun getSignalProvenance(signalId: String): List<SignalProvenance> {
        return emptyList()
    }
}
