package com.elysium369.meet.core.catalog

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

const val G4ED_ENGINE_ATLAS_ASSET = "knowledge/g4ed/g4ed_engine_atlas.json"
const val G4ED_ENGINE_ATLAS_SOURCE_SHA256 =
    "99a2dc92a2acd5364d9f85e257b382b93998065647617fed4ddd11165785a89f"
const val G4ED_ENGINE_ATLAS_CONTENT_SHA256 =
    "17f41f9f18a4dddf07433e5252b5b8742679354b6d95debfc435b956a87bc3de"

@Serializable
data class G4edEngineAtlas(
    val schemaVersion: Int,
    val atlasId: String,
    val atlasVersion: String,
    val displayName: String,
    val vehicleLabel: String,
    val engineLabel: String,
    val source: G4edAtlasSource,
    val geometryPolicy: G4edGeometryPolicy,
    val statistics: G4edAtlasStatistics,
    val sections: List<G4edAtlasSection>,
    val elements: List<G4edAtlasElement>,
    val contentSha256: String,
)

@Serializable
data class G4edAtlasSource(
    val sha256: String,
    val lineCount: Int,
    val ownership: String,
    val referenceCount: Int,
)

@Serializable
data class G4edGeometryPolicy(
    val oemClaim: Boolean,
    val vehicleSpecificClaim: Boolean,
    val dimensionalState: String,
    val defaultAuthority: String,
    val warning: String,
)

@Serializable
data class G4edAtlasStatistics(
    val elementCount: Int,
    val sectionCount: Int,
    val directlySellableCount: Int,
    val semanticRegionCount: Int,
    val conditionalVariantCount: Int,
)

@Serializable
data class G4edAtlasSection(
    val sectionNumber: Int,
    val systemId: String,
    val title: String,
    val knowledge: String,
    val sourceReferences: List<String>,
)

@Serializable
data class G4edAtlasElement(
    val ordinal: Int,
    val canonicalId: String,
    val nameOriginal: String,
    val aliases: List<String>,
    val systemId: String,
    val sectionNumber: Int,
    val subsectionTitle: String?,
    val elementKind: String,
    val parentCanonicalId: String?,
    val applicability: G4edApplicability,
    val evidenceRequirements: List<String>,
    val commerce: G4edCommerceContract,
    val visual: G4edVisualContract,
    val knowledgeBinding: G4edKnowledgeBinding,
    val normalization: G4edNormalizationState? = null,
)

@Serializable
data class G4edApplicability(
    val vehicleScope: String,
    val engineScope: String,
    val installedState: String,
    val compatibilityCeiling: String,
    val side: String = "NOT_SIDE_SPECIFIC",
    val bodyStyleCondition: String = "ALL_REFERENCED_BODY_STYLES",
    val equipmentConditions: List<String> = emptyList(),
)

@Serializable
data class G4edNormalizationState(
    val identityKey: String,
    val oemResolutionState: String,
    val oemNumber: String?,
    val quantity: Int?,
    val supersededBy: String?,
    val fastenerRelationshipState: String,
)

@Serializable
data class G4edCommerceContract(
    val directlySellable: Boolean,
    val redirectToParent: Boolean,
    val comparisonChecks: List<String>,
    val visualMatchIsExactCompatibility: Boolean,
)

@Serializable
data class G4edVisualContract(
    val packId: String,
    val nodeKey: String,
    val renderStrategy: String,
    val authority: String,
    val cameraPreset: String,
    val interactionModes: List<String>,
    val animationMode: String,
    val dimensional: Boolean,
    val oemClaim: Boolean,
)

@Serializable
data class G4edKnowledgeBinding(
    val sourceSha256: String,
    val sectionNumber: Int,
    val sourceOrdinal: Int,
    val sourceLocalOrdinal: Int? = null,
    val provenance: String,
)

object G4edEngineAtlasParser {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }

    fun decode(raw: String): G4edEngineAtlas =
        json.decodeFromString<G4edEngineAtlas>(raw).also(::validate)

    private fun validate(atlas: G4edEngineAtlas) {
        require(atlas.schemaVersion == 1) { "Unsupported G4ED atlas schema" }
        require(atlas.atlasId == "meet.g4ed.engine.parts.420") {
            "Unexpected G4ED atlas identity"
        }
        require(atlas.source.sha256 == G4ED_ENGINE_ATLAS_SOURCE_SHA256) {
            "G4ED source traceability mismatch"
        }
        require(atlas.contentSha256 == G4ED_ENGINE_ATLAS_CONTENT_SHA256) {
            "G4ED canonical content mismatch"
        }
        require(!atlas.geometryPolicy.oemClaim) {
            "Unverified OEM geometry claim is forbidden"
        }
        require(!atlas.geometryPolicy.vehicleSpecificClaim) {
            "Unverified vehicle-specific geometry claim is forbidden"
        }
        require(atlas.geometryPolicy.dimensionalState == "ILLUSTRATIVE_PROPORTIONS_ONLY") {
            "G4ED geometry must remain explicitly non-dimensional"
        }
        require(atlas.sections.size == 20 && atlas.statistics.sectionCount == 20) {
            "G4ED atlas must contain exactly 20 systems"
        }
        require(atlas.sections.map { it.sectionNumber } == (1..20).toList()) {
            "G4ED systems must remain ordered and contiguous"
        }
        require(atlas.sections.map { it.systemId }.toSet().size == 20) {
            "G4ED system identifiers must be unique"
        }
        require(atlas.elements.size == 420 && atlas.statistics.elementCount == 420) {
            "G4ED atlas must contain exactly 420 elements"
        }
        require(atlas.elements.map { it.ordinal } == (1..420).toList()) {
            "G4ED ordinals must remain ordered and contiguous"
        }

        val ids = atlas.elements.map { it.canonicalId }
        val knownIds = ids.toSet()
        require(knownIds.size == ids.size) { "Duplicate G4ED canonical identifier" }
        require(
            atlas.statistics.directlySellableCount ==
                atlas.elements.count { it.commerce.directlySellable },
        ) { "G4ED sellable statistics mismatch" }
        require(
            atlas.statistics.semanticRegionCount ==
                atlas.elements.count { it.visual.renderStrategy == "SEMANTIC_REGION" },
        ) { "G4ED semantic-region statistics mismatch" }
        require(
            atlas.statistics.conditionalVariantCount ==
                atlas.elements.count { it.elementKind == "CONDITIONAL_VARIANT" },
        ) { "G4ED conditional-variant statistics mismatch" }

        atlas.elements.forEach { element ->
            require(element.sectionNumber in 1..20) {
                "Invalid section for ${element.canonicalId}"
            }
            require(element.knowledgeBinding.sourceSha256 == atlas.source.sha256) {
                "Source binding mismatch for ${element.canonicalId}"
            }
            require(element.knowledgeBinding.sourceOrdinal == element.ordinal) {
                "Source ordinal mismatch for ${element.canonicalId}"
            }
            require(element.parentCanonicalId == null || element.parentCanonicalId in knownIds) {
                "Unknown parent for ${element.canonicalId}"
            }
            require(!element.visual.oemClaim && !element.visual.dimensional) {
                "Unsupported OEM or dimensional claim for ${element.canonicalId}"
            }
            require(!element.commerce.visualMatchIsExactCompatibility) {
                "Visual similarity cannot establish exact compatibility"
            }
            require(element.visual.packId.isNotBlank() && element.visual.nodeKey.isNotBlank()) {
                "Missing visual contract for ${element.canonicalId}"
            }
            require(element.visual.interactionModes.contains("ORBIT_360")) {
                "Every G4ED element must support a 360 interaction contract"
            }

            if (element.elementKind in setOf("INTEGRATED_FEATURE", "REFERENCE_MARK")) {
                require(!element.commerce.directlySellable) {
                    "Integrated feature cannot be sold independently"
                }
                require(element.commerce.redirectToParent && element.parentCanonicalId != null) {
                    "Integrated feature must redirect to a known parent"
                }
                require(element.visual.renderStrategy == "SEMANTIC_REGION") {
                    "Integrated feature must use a semantic 3D region"
                }
            }
            if (element.elementKind == "CONDITIONAL_VARIANT") {
                require(element.applicability.installedState == "PENDING_PHYSICAL_CONFIRMATION") {
                    "Conditional variants require physical confirmation"
                }
            }
        }
    }
}

object G4edEngineAtlasEngine {
    fun search(
        elements: List<G4edAtlasElement>,
        query: String = "",
        systemId: String? = null,
        directlySellableOnly: Boolean = false,
    ): List<G4edAtlasElement> {
        val normalizedQuery = normalize(query)
        return elements.asSequence()
            .filter { systemId == null || it.systemId == systemId }
            .filter { !directlySellableOnly || it.commerce.directlySellable }
            .filter { element ->
                normalizedQuery.isBlank() ||
                    searchableText(element).contains(normalizedQuery)
            }
            .sortedBy { it.ordinal }
            .toList()
    }

    private fun searchableText(element: G4edAtlasElement): String =
        normalize(
            buildString {
                append(element.nameOriginal)
                append(' ')
                append(element.aliases.joinToString(" "))
                append(' ')
                append(element.canonicalId)
                append(' ')
                append(element.systemId)
            },
        )

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .trim()
}

class G4edEngineAtlasRepository(context: Context) {
    private val appContext = context.applicationContext

    val atlas: G4edEngineAtlas by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        appContext.assets.open(G4ED_ENGINE_ATLAS_ASSET)
            .bufferedReader(Charsets.UTF_8)
            .use { G4edEngineAtlasParser.decode(it.readText()) }
    }
}
