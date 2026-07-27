package com.elysium369.meet.core.catalog

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class VehicleTechnicalAtlas(
    val schemaVersion: Int,
    val atlasId: String,
    val domainId: String,
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

data class VehicleTechnicalAtlasDescriptor(
    val domainId: String,
    val assetPath: String,
    val atlasId: String,
    val sourceSha256: String,
    val contentSha256: String,
    val elementCount: Int,
    val sectionCount: Int,
)

object VehicleTechnicalAtlasDescriptors {
    val transmissionHydraulics = VehicleTechnicalAtlasDescriptor(
        domainId = "transmission_hydraulics",
        assetPath = "knowledge/vehicle_technical_atlases/transmission_hydraulics_atlas.json",
        atlasId = "meet.accent2005.transmission-hydraulics.parts.838",
        sourceSha256 = "77973385cceafee8cb5c35f01463264df816501d81ed060e390cbb36cd226b2d",
        contentSha256 = "49d03589da5bb1f848d8facfc41c0f0b023668cbe1a1a7778a4daf012315c03c",
        elementCount = 838,
        sectionCount = 13,
    )
    val electrical = VehicleTechnicalAtlasDescriptor(
        domainId = "electrical",
        assetPath = "knowledge/vehicle_technical_atlases/electrical_atlas.json",
        atlasId = "meet.accent2005.electrical.parts.1529",
        sourceSha256 = "b511b2085fc96a1c2d2cd23066ca63ab553af5791529084dc1a28579c36c6efb",
        contentSha256 = "368e61afddca8461026b80257a8b657365988ca0f5affc66fe02c444e7126b48",
        elementCount = 1529,
        sectionCount = 34,
    )
    val body = VehicleTechnicalAtlasDescriptor(
        domainId = "body",
        assetPath = "knowledge/vehicle_technical_atlases/body_atlas.json",
        atlasId = "meet.accent2005.sedan-body.parts.1665",
        sourceSha256 = "719fbb72f6994d1e37a6072395a23ad84caa82b2bedc4b65b1a06586ed568e5f",
        contentSha256 = "fbc45bbacd051909754336b64512a7b20b8e675dea18a5910800641ed52d498d",
        elementCount = 1665,
        sectionCount = 38,
    )
    val remainingSystems = VehicleTechnicalAtlasDescriptor(
        domainId = "remaining_systems",
        assetPath = "knowledge/vehicle_technical_atlases/remaining_systems_atlas.json",
        atlasId = "meet.accent2005.remaining-systems.parts.1953",
        sourceSha256 = "e9d82c61d08bfda44867666ecf5a7b4ba0d3bf67ced2fc32ba0271eeee3d9364",
        contentSha256 = "b1889977beca8d1d8a221391ef40052fc9fd8006e8455da53981cf1889cae059",
        elementCount = 1953,
        sectionCount = 25,
    )
    val all = listOf(transmissionHydraulics, electrical, body, remainingSystems)

    fun forDomain(domainId: String): VehicleTechnicalAtlasDescriptor =
        all.singleOrNull { it.domainId == domainId }
            ?: error("Unknown technical atlas domain: $domainId")

    fun forCanonicalId(canonicalId: String): VehicleTechnicalAtlasDescriptor? =
        all.firstOrNull { canonicalId.startsWith("${it.domainId}-") }
}

object VehicleTechnicalAtlasParser {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }

    fun decode(raw: String, descriptor: VehicleTechnicalAtlasDescriptor): VehicleTechnicalAtlas =
        json.decodeFromString<VehicleTechnicalAtlas>(raw).also { validate(it, descriptor) }

    private fun validate(
        atlas: VehicleTechnicalAtlas,
        descriptor: VehicleTechnicalAtlasDescriptor,
    ) {
        require(atlas.schemaVersion == 1) { "Unsupported technical atlas schema" }
        require(atlas.domainId == descriptor.domainId && atlas.atlasId == descriptor.atlasId) {
            "Unexpected technical atlas identity"
        }
        require(atlas.source.sha256 == descriptor.sourceSha256) {
            "Technical atlas source traceability mismatch"
        }
        require(atlas.contentSha256 == descriptor.contentSha256) {
            "Technical atlas canonical content mismatch"
        }
        require(!atlas.geometryPolicy.oemClaim && !atlas.geometryPolicy.vehicleSpecificClaim) {
            "Unverified OEM or vehicle-specific geometry claim is forbidden"
        }
        require(atlas.geometryPolicy.dimensionalState == "ILLUSTRATIVE_PROPORTIONS_ONLY") {
            "Technical atlas geometry must remain non-dimensional"
        }
        require(
            atlas.sections.size == descriptor.sectionCount &&
                atlas.statistics.sectionCount == descriptor.sectionCount,
        ) { "Technical atlas section count mismatch" }
        require(atlas.sections.map { it.sectionNumber } == (1..descriptor.sectionCount).toList()) {
            "Technical atlas sections must remain contiguous"
        }
        require(
            atlas.elements.size == descriptor.elementCount &&
                atlas.statistics.elementCount == descriptor.elementCount,
        ) { "Technical atlas element count mismatch" }
        require(atlas.elements.map { it.ordinal } == (1..descriptor.elementCount).toList()) {
            "Technical atlas ordinals must remain contiguous"
        }
        val ids = atlas.elements.map { it.canonicalId }
        val knownIds = ids.toSet()
        require(ids.size == knownIds.size) { "Duplicate technical atlas canonical identifier" }
        require(
            atlas.statistics.directlySellableCount ==
                atlas.elements.count { it.commerce.directlySellable },
        ) { "Technical atlas sellable statistics mismatch" }
        require(
            atlas.statistics.semanticRegionCount ==
                atlas.elements.count { it.visual.renderStrategy == "SEMANTIC_REGION" },
        ) { "Technical atlas semantic-region statistics mismatch" }
        require(
            atlas.statistics.conditionalVariantCount ==
                atlas.elements.count { it.elementKind == "CONDITIONAL_VARIANT" },
        ) { "Technical atlas conditional-variant statistics mismatch" }

        atlas.elements.forEach { element ->
            require(element.canonicalId.startsWith("${descriptor.domainId}-")) {
                "Wrong domain binding for ${element.canonicalId}"
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
            require(element.applicability.side in setOf(
                "LEFT",
                "RIGHT",
                "LEFT_AND_RIGHT",
                "NOT_SIDE_SPECIFIC",
            )) { "Invalid side classification for ${element.canonicalId}" }
            require(
                element.applicability.bodyStyleCondition != "HATCHBACK_ONLY" ||
                    !element.applicability.vehicleScope.contains("sedán 4 puertas"),
            ) { "Hatchback-only part cannot be presented as sedan-applicable" }
            require(element.normalization?.oemResolutionState == "PENDING_VIN_EPC") {
                "Technical element cannot claim unresolved OEM authority"
            }
            require(element.normalization.oemNumber == null && element.normalization.quantity == null) {
                "OEM number or quantity cannot be invented"
            }
            require(element.visual.interactionModes.contains("ORBIT_360")) {
                "Every technical element must support 360 interaction"
            }
            if (element.visual.renderStrategy == "SEMANTIC_REGION") {
                require(!element.commerce.directlySellable) {
                    "Semantic region cannot be sold independently"
                }
                require(element.commerce.redirectToParent && element.parentCanonicalId != null) {
                    "Semantic region must redirect to a known parent"
                }
            }
            if (element.elementKind == "CONDITIONAL_VARIANT") {
                require(element.applicability.installedState == "PENDING_PHYSICAL_CONFIRMATION") {
                    "Conditional equipment requires physical confirmation"
                }
            }
        }
    }
}

class VehicleTechnicalAtlasRepository(context: Context) {
    private val assets = context.applicationContext.assets
    private val atlases = mutableMapOf<String, VehicleTechnicalAtlas>()

    fun atlas(domainId: String): VehicleTechnicalAtlas {
        val descriptor = VehicleTechnicalAtlasDescriptors.forDomain(domainId)
        return synchronized(atlases) {
            atlases.getOrPut(domainId) {
                assets.open(descriptor.assetPath)
                    .bufferedReader(Charsets.UTF_8)
                    .use { VehicleTechnicalAtlasParser.decode(it.readText(), descriptor) }
            }
        }
    }

    fun all(): List<VehicleTechnicalAtlas> =
        VehicleTechnicalAtlasDescriptors.all.map { atlas(it.domainId) }
}

data class CanonicalVehiclePart(
    val atlasId: String,
    val atlasDisplayName: String,
    val vehicleLabel: String,
    val geometryWarning: String,
    val section: G4edAtlasSection,
    val element: G4edAtlasElement,
)

class CanonicalVehiclePartRepository(context: Context) {
    private val g4ed by lazy { G4edEngineAtlasRepository(context).atlas }
    private val technical = VehicleTechnicalAtlasRepository(context)

    fun find(canonicalId: String): CanonicalVehiclePart? {
        if (canonicalId.startsWith("g4ed-")) {
            val element = g4ed.elements.singleOrNull { it.canonicalId == canonicalId } ?: return null
            return CanonicalVehiclePart(
                atlasId = g4ed.atlasId,
                atlasDisplayName = g4ed.displayName,
                vehicleLabel = g4ed.vehicleLabel,
                geometryWarning = g4ed.geometryPolicy.warning,
                section = g4ed.sections.single { it.systemId == element.systemId },
                element = element,
            )
        }
        val descriptor = VehicleTechnicalAtlasDescriptors.forCanonicalId(canonicalId) ?: return null
        val atlas = technical.atlas(descriptor.domainId)
        val element = atlas.elements.singleOrNull { it.canonicalId == canonicalId } ?: return null
        return CanonicalVehiclePart(
            atlasId = atlas.atlasId,
            atlasDisplayName = atlas.displayName,
            vehicleLabel = atlas.vehicleLabel,
            geometryWarning = atlas.geometryPolicy.warning,
            section = atlas.sections.single { it.systemId == element.systemId },
            element = element,
        )
    }
}
