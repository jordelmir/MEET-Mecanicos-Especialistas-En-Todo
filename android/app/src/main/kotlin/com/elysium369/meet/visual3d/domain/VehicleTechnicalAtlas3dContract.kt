package com.elysium369.meet.visual3d.domain

import android.content.Context
import com.elysium369.meet.core.catalog.G4edAtlasElement
import com.elysium369.meet.core.catalog.VehicleTechnicalAtlasDescriptor
import com.elysium369.meet.core.catalog.VehicleTechnicalAtlasDescriptors
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

const val VEHICLE_TECHNICAL_ATLAS_MODEL_ROOT = "models/vehicle_technical_atlases"

object VehicleTechnicalAtlas3dManifestParser {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }

    fun decode(
        raw: String,
        descriptor: VehicleTechnicalAtlasDescriptor,
    ): G4edAtlas3dManifest =
        json.decodeFromString<G4edAtlas3dManifest>(raw).also { validate(it, descriptor) }

    private fun validate(
        manifest: G4edAtlas3dManifest,
        descriptor: VehicleTechnicalAtlasDescriptor,
    ) {
        require(manifest.schemaVersion == 1) { "Unsupported technical 3D manifest schema" }
        require(manifest.packId.matches(Regex("${descriptor.domainId}_[0-9]{2}"))) {
            "Invalid technical 3D pack ID"
        }
        require(manifest.atlasId == descriptor.atlasId) { "Unexpected technical atlas binding" }
        require(manifest.atlasContentSha256 == descriptor.contentSha256) {
            "3D pack was built from a different technical atlas"
        }
        require(
            manifest.assetPath ==
                "$VEHICLE_TECHNICAL_ATLAS_MODEL_ROOT/${descriptor.domainId}/${manifest.packId}/${manifest.assetFile}",
        ) { "Unexpected technical atlas asset path" }
        require(!manifest.oemClaim && !manifest.vehicleSpecificClaim) {
            "Unverified OEM or vehicle-specific geometry claim"
        }
        require(manifest.dimensionalState == "ILLUSTRATIVE_PROPORTIONS_ONLY") {
            "Technical reconstruction must remain non-dimensional"
        }
        require(manifest.groupNodePrefix == G4ED_ATLAS_GROUP_NODE_PREFIX) {
            "Unexpected technical group-node prefix"
        }
        require(manifest.meshNodePrefix == G4ED_ATLAS_MESH_NODE_PREFIX) {
            "Unexpected technical mesh-node prefix"
        }
        require(manifest.bindings.size == manifest.elementCount && manifest.bindings.isNotEmpty()) {
            "Technical manifest element count mismatch"
        }
        require(manifest.bindings.map { it.ordinal }.distinct().size == manifest.bindings.size) {
            "Duplicate technical 3D ordinal"
        }
        require(manifest.bindings.map { it.canonicalId }.distinct().size == manifest.bindings.size) {
            "Duplicate technical 3D canonical ID"
        }
        require(manifest.ordinalRange.size == 2) { "Invalid technical manifest ordinal range" }
        require(manifest.bindings.minOf { it.ordinal } == manifest.ordinalRange.first()) {
            "Technical manifest range start mismatch"
        }
        require(manifest.bindings.maxOf { it.ordinal } == manifest.ordinalRange.last()) {
            "Technical manifest range end mismatch"
        }
        require(manifest.sha256.matches(Regex("[a-f0-9]{64}"))) { "Invalid technical GLB SHA-256" }
        manifest.bindings.forEach { binding ->
            require(binding.groupNode == "$G4ED_ATLAS_GROUP_NODE_PREFIX${binding.nodeKey}") {
                "Wrong technical group node for ${binding.canonicalId}"
            }
            require(binding.meshNodePrefix == "$G4ED_ATLAS_MESH_NODE_PREFIX${binding.nodeKey}__") {
                "Wrong technical mesh prefix for ${binding.canonicalId}"
            }
            require(binding.interactionModes.contains("ORBIT_360")) {
                "Missing 360 interaction for ${binding.canonicalId}"
            }
            require(binding.originalTransform.position.size == 3) { "Invalid original position" }
            require(binding.originalTransform.rotation.size == 3) { "Invalid original rotation" }
            require(binding.originalTransform.scale.size == 3) { "Invalid original scale" }
            require(binding.explodeVector.size == 3) { "Invalid explode vector" }
            require(binding.bounds.center.size == 3 && binding.bounds.radius > 0f) {
                "Invalid technical camera bounds"
            }
            require(!binding.oemClaim && !binding.dimensional) {
                "Unsupported technical geometry authority"
            }
            if (binding.renderStrategy == "SEMANTIC_REGION") {
                require(!binding.directlySellable && binding.parentCanonicalId != null) {
                    "Semantic technical region cannot be sold independently"
                }
            }
        }
    }
}

object VehicleTechnicalAtlas3dCatalog {
    fun manifestAssetPath(domainId: String, packId: String): String =
        "$VEHICLE_TECHNICAL_ATLAS_MODEL_ROOT/$domainId/$packId/manifest.json"

    fun bindingFor(
        element: G4edAtlasElement,
        manifest: G4edAtlas3dManifest,
    ): G4edAtlas3dBinding? {
        if (element.visual.packId != manifest.packId) return null
        return manifest.bindings.singleOrNull {
            it.ordinal == element.ordinal &&
                it.canonicalId == element.canonicalId &&
                it.nodeKey == element.visual.nodeKey
        }
    }
}

class VehicleTechnicalAtlas3dRepository(context: Context) {
    private val assets = context.applicationContext.assets
    private val manifests = mutableMapOf<String, G4edAtlas3dManifest>()

    fun manifest(domainId: String, packId: String): G4edAtlas3dManifest {
        val descriptor = VehicleTechnicalAtlasDescriptors.forDomain(domainId)
        val key = "$domainId/$packId"
        return synchronized(manifests) {
            manifests.getOrPut(key) {
                assets.open(VehicleTechnicalAtlas3dCatalog.manifestAssetPath(domainId, packId))
                    .bufferedReader(Charsets.UTF_8)
                    .use { VehicleTechnicalAtlas3dManifestParser.decode(it.readText(), descriptor) }
            }
        }
    }
}
