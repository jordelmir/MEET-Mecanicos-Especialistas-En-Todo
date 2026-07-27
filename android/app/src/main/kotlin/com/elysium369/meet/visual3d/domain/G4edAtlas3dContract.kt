package com.elysium369.meet.visual3d.domain

import android.content.Context
import com.elysium369.meet.core.catalog.G4ED_ENGINE_ATLAS_CONTENT_SHA256
import com.elysium369.meet.core.catalog.G4edAtlasElement
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

const val G4ED_ATLAS_MODEL_ROOT = "models/g4ed_atlas"
const val G4ED_ATLAS_GROUP_NODE_PREFIX = "asset_part__"
const val G4ED_ATLAS_MESH_NODE_PREFIX = "asset_mesh__"

@Serializable
data class G4edAtlas3dManifest(
    val schemaVersion: Int,
    val packId: String,
    val atlasId: String,
    val atlasVersion: String,
    val atlasContentSha256: String,
    val sourceSha256: String,
    val assetFile: String,
    val assetPath: String,
    val geometryAuthority: String,
    val dimensionalState: String,
    val oemClaim: Boolean,
    val vehicleSpecificClaim: Boolean,
    val generatedBy: String,
    val generatorVersion: String,
    val threeVersion: String,
    val groupNodePrefix: String,
    val meshNodePrefix: String,
    val elementCount: Int,
    val ordinalRange: List<Int>,
    val meshCount: Int,
    val triangleCount: Int,
    val sha256: String,
    val license: String,
    val warning: String,
    val bindings: List<G4edAtlas3dBinding>,
)

@Serializable
data class G4edAtlas3dBinding(
    val ordinal: Int,
    val canonicalId: String,
    val nodeKey: String,
    val groupNode: String,
    val meshNodePrefix: String,
    val parentCanonicalId: String?,
    val elementKind: String,
    val renderStrategy: String,
    val authority: String,
    val cameraPreset: String,
    val interactionModes: List<String>,
    val animationMode: String,
    val originalTransform: G4edAtlas3dTransform,
    val explodeVector: List<Float>,
    val bounds: G4edAtlas3dBounds,
    val directlySellable: Boolean,
    val dimensional: Boolean,
    val oemClaim: Boolean,
)

@Serializable
data class G4edAtlas3dTransform(
    val position: List<Float>,
    val rotation: List<Float>,
    val scale: List<Float>,
)

@Serializable
data class G4edAtlas3dBounds(
    val center: List<Float>,
    val radius: Float,
)

object G4edAtlas3dManifestParser {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }

    fun decode(raw: String): G4edAtlas3dManifest =
        json.decodeFromString<G4edAtlas3dManifest>(raw).also(::validate)

    private fun validate(manifest: G4edAtlas3dManifest) {
        require(manifest.schemaVersion == 1) { "Unsupported G4ED 3D manifest schema" }
        require(manifest.packId.matches(Regex("g4ed_[a-z0-9_]+"))) {
            "Invalid G4ED 3D pack ID"
        }
        require(manifest.atlasId == "meet.g4ed.engine.parts.420") {
            "Unexpected atlas binding"
        }
        require(manifest.atlasContentSha256 == G4ED_ENGINE_ATLAS_CONTENT_SHA256) {
            "3D pack was built from a different atlas"
        }
        require(
            manifest.assetPath ==
                "$G4ED_ATLAS_MODEL_ROOT/${manifest.packId}/${manifest.assetFile}",
        ) { "Unexpected G4ED asset path" }
        require(!manifest.oemClaim && !manifest.vehicleSpecificClaim) {
            "Unverified OEM or vehicle-specific geometry claim"
        }
        require(manifest.dimensionalState == "ILLUSTRATIVE_PROPORTIONS_ONLY") {
            "G4ED reconstruction must remain non-dimensional"
        }
        require(manifest.groupNodePrefix == G4ED_ATLAS_GROUP_NODE_PREFIX) {
            "Unexpected G4ED group-node prefix"
        }
        require(manifest.meshNodePrefix == G4ED_ATLAS_MESH_NODE_PREFIX) {
            "Unexpected G4ED mesh-node prefix"
        }
        require(manifest.bindings.size == manifest.elementCount) {
            "G4ED manifest element count mismatch"
        }
        require(manifest.bindings.map { it.ordinal }.distinct().size == manifest.bindings.size) {
            "Duplicate G4ED 3D ordinal"
        }
        require(manifest.bindings.map { it.canonicalId }.distinct().size == manifest.bindings.size) {
            "Duplicate G4ED 3D canonical ID"
        }
        require(manifest.ordinalRange.size == 2) { "Invalid G4ED manifest ordinal range" }
        require(manifest.bindings.minOf { it.ordinal } == manifest.ordinalRange.first()) {
            "G4ED manifest range start mismatch"
        }
        require(manifest.bindings.maxOf { it.ordinal } == manifest.ordinalRange.last()) {
            "G4ED manifest range end mismatch"
        }
        require(manifest.sha256.matches(Regex("[a-f0-9]{64}"))) {
            "Invalid G4ED GLB SHA-256"
        }

        manifest.bindings.forEach { binding ->
            require(binding.groupNode == "$G4ED_ATLAS_GROUP_NODE_PREFIX${binding.nodeKey}") {
                "Wrong group node for G4ED ordinal ${binding.ordinal}"
            }
            require(binding.meshNodePrefix == "$G4ED_ATLAS_MESH_NODE_PREFIX${binding.nodeKey}__") {
                "Wrong mesh prefix for G4ED ordinal ${binding.ordinal}"
            }
            require(binding.interactionModes.contains("ORBIT_360")) {
                "Missing 360 interaction for G4ED ordinal ${binding.ordinal}"
            }
            require(binding.originalTransform.position.size == 3) {
                "Invalid original position for G4ED ordinal ${binding.ordinal}"
            }
            require(binding.originalTransform.rotation.size == 3) {
                "Invalid original rotation for G4ED ordinal ${binding.ordinal}"
            }
            require(binding.originalTransform.scale.size == 3) {
                "Invalid original scale for G4ED ordinal ${binding.ordinal}"
            }
            require(binding.explodeVector.size == 3) {
                "Invalid explode vector for G4ED ordinal ${binding.ordinal}"
            }
            require(binding.bounds.center.size == 3 && binding.bounds.radius > 0f) {
                "Invalid camera bounds for G4ED ordinal ${binding.ordinal}"
            }
            require(!binding.oemClaim && !binding.dimensional) {
                "Unsupported authority for G4ED ordinal ${binding.ordinal}"
            }
            if (binding.renderStrategy == "SEMANTIC_REGION") {
                require(!binding.directlySellable && binding.parentCanonicalId != null) {
                    "Semantic G4ED region cannot be sold independently"
                }
            }
        }
    }
}

object G4edAtlas3dCatalog {
    fun manifestAssetPath(packId: String): String =
        "$G4ED_ATLAS_MODEL_ROOT/$packId/manifest.json"

    fun bindingFor(
        element: G4edAtlasElement,
        manifest: G4edAtlas3dManifest,
    ): G4edAtlas3dBinding? {
        if (element.visual.packId != manifest.packId) return null
        return manifest.bindings.singleOrNull { binding ->
            binding.ordinal == element.ordinal &&
                binding.canonicalId == element.canonicalId &&
                binding.nodeKey == element.visual.nodeKey
        }
    }

    fun isNodeForBinding(nodeName: String?, binding: G4edAtlas3dBinding): Boolean =
        nodeName == binding.groupNode || nodeName?.startsWith(binding.meshNodePrefix) == true
}

class G4edAtlas3dRepository(context: Context) {
    private val assets = context.applicationContext.assets
    private val manifests = mutableMapOf<String, G4edAtlas3dManifest>()

    fun manifest(packId: String): G4edAtlas3dManifest =
        synchronized(manifests) {
            manifests.getOrPut(packId) {
                assets.open(G4edAtlas3dCatalog.manifestAssetPath(packId))
                    .bufferedReader(Charsets.UTF_8)
                    .use { G4edAtlas3dManifestParser.decode(it.readText()) }
            }
        }
}

