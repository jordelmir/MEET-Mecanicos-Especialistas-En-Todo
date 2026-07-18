package com.elysium369.meet.visual3d.domain

import com.elysium369.meet.core.engine3d.UniversalCatalogSceneNode
import java.text.Normalizer

data class GenericEngineAssetBinding(
    val meshKey: String,
    val literalSourceNames: Set<String>,
    val serviceStage: Int,
    val explodedOffset: CatalogServiceOffset
) {
    val isSelectable: Boolean
        get() = literalSourceNames.isNotEmpty()
}

/**
 * Stable contract for the project-generated generic L4 GLB.
 *
 * Scene offsets are illustrative renderer units. They are not measurements and cannot be
 * promoted to L3 dimensional authority without a separate evidence record.
 */
object GenericInlineFourAssetContract {
    const val ASSET_PATH = "models/engine_inline4_generic/generic_inline4_engine.glb"
    const val MANIFEST_PATH = "models/engine_inline4_generic/manifest.json"
    const val MESH_NODE_PREFIX = "asset_mesh__"
    const val AUTHORITY = "L2_GENERIC_ASSEMBLY"
    private const val SERVICE_OFFSET_HORIZONTAL_SCALE = 0.20f
    private const val SERVICE_OFFSET_VERTICAL_SCALE = 0.17f
    private const val SERVICE_OFFSET_DEPTH_SCALE = 0.06f

    val bindings: List<GenericEngineAssetBinding> = listOf(
        binding("engine_block", 6, 0f, -0.3f, 0f, "Bloque de motor"),
        binding("main_bearing_caps", 4, 0f, -1.45f, 0f, "Tapas de bancada"),
        binding("oil_pan", 1, 0f, -2.25f, 0f),
        binding("oil_pan_gasket", 1, 0f, -1.8f, 0f, "Junta del cárter", "Junta del carter"),
        binding("crankshaft", 5, 0f, -2.15f, 0f, "Cigüeñal"),
        binding("connecting_rods", 4, 0f, 1.4f, 0f, "Bielas"),
        binding("pistons", 4, 0f, 2.35f, 0f, "Pistones"),
        binding("piston_pins", 3, 0f, 2.75f, -0.8f, "Pernos de pistón"),
        binding("piston_rings", 3, 0f, 3.15f, 0.8f, "Anillos de pistón"),
        binding("flywheel", 2, -2.7f, 0f, 0f, "Volante de inercia / flexplate"),
        binding("crank_pulley", 2, 2.6f, 0f, 0f, "Polea de cigüeñal"),
        binding("crank_sprocket", 2, 2.35f, 0.4f, 0f, "Piñón de cigüeñal"),
        binding("head_gasket", 3, 0f, 2.35f, 0f, "Junta de culata"),
        binding("cylinder_head", 3, 0f, 2.8f, 0f, "Culata"),
        binding("head_bolts", 2, 0f, 3.35f, 0f, "Pernos de culata"),
        binding("camshafts_context", 3, 0f, 3.15f, 0f),
        binding("intake_valves", 3, 0f, 2.6f, -1.5f, "Válvulas de admisión"),
        binding("exhaust_valves", 3, 0f, 2.6f, 1.5f, "Válvulas de escape"),
        binding("valve_springs", 2, 0f, 3.1f, 1.2f, "Resortes de válvula"),
        binding("valve_retainers", 2, 0f, 3.45f, 1.45f, "Retenedores de resorte"),
        binding("cam_caps", 2, 0f, 3.65f, 0f, "Tapas de árbol de levas"),
        binding("valve_cover_gasket", 1, 0f, 3.5f, 0f, "Junta de tapa de válvulas"),
        binding("valve_cover", 1, 0f, 4.05f, 0f, "Tapa de válvulas"),
        binding("cam_sprockets_context", 2, 2.5f, 1.4f, 0f),
        binding("timing_idler", 2, 2.75f, 0.45f, 1.0f, "Polea loca", "Polea loca / idler pulley"),
        binding("timing_tensioner", 2, 2.75f, 0.1f, -1.0f, "Polea tensora", "Tensor de correa"),
        binding("timing_belt", 1, 3.15f, 0f, 0f, "Correa de distribución"),
        binding("timing_cover_lower", 1, 3.65f, -0.35f, 0f, "Tapa inferior de distribución"),
        binding("timing_cover_upper", 1, 3.65f, 1.65f, 0f, "Tapa superior de distribución"),
        binding("oil_pump", 2, 2.4f, -1.25f, -0.8f, "Bomba de aceite"),
        binding("oil_filter", 1, -1.6f, -0.6f, -2.0f, "Filtro de aceite"),
        binding("water_pump", 1, 2.5f, 0.4f, 1.45f, "Bomba de agua"),
        binding("thermostat_housing", 1, -2.15f, 1.4f, 1.4f, "Caja del termostato"),
        binding("thermostat", 2, -2.65f, 1.4f, 1.65f, "Termostato"),
        binding("intake_manifold", 1, 0f, 0.7f, -2.8f, "Múltiple de admisión"),
        binding("throttle_body", 1, 2.45f, 0.7f, -2.8f, "Mariposa de aceleración"),
        binding("exhaust_manifold", 1, 0f, 0.45f, 2.9f, "Múltiple de escape")
    )

    val requiredMeshKeys: Set<String> = bindings.mapTo(linkedSetOf(), GenericEngineAssetBinding::meshKey)

    private val byMeshKey = bindings.associateBy(GenericEngineAssetBinding::meshKey)
    private val byLiteralName = buildMap {
        bindings.forEach { binding ->
            binding.literalSourceNames.forEach { literalName ->
                put(canonical(literalName), binding)
            }
        }
    }

    fun meshKeyForNodeName(nodeName: String?): String? {
        if (nodeName == null || !nodeName.startsWith(MESH_NODE_PREFIX)) return null
        return nodeName.removePrefix(MESH_NODE_PREFIX).substringBefore("__").takeIf(String::isNotBlank)
    }

    fun bindingForNodeName(nodeName: String?): GenericEngineAssetBinding? =
        meshKeyForNodeName(nodeName)?.let(byMeshKey::get)

    fun bindingForSourceName(sourceName: String): GenericEngineAssetBinding? =
        byLiteralName[canonical(sourceName)]

    fun sourceBackedNodes(nodes: List<UniversalCatalogSceneNode>): List<UniversalCatalogSceneNode> {
        val claimedMeshKeys = mutableSetOf<String>()
        return nodes.filter { node ->
            val binding = bindingForSourceName(node.name) ?: return@filter false
            binding.isSelectable && claimedMeshKeys.add(binding.meshKey)
        }
    }

    fun placementForNodeName(
        nodeName: String?,
        placements: List<CatalogSemanticPlacement>
    ): CatalogSemanticPlacement? {
        val binding = bindingForNodeName(nodeName) ?: return null
        if (!binding.isSelectable) return null
        return placements.firstOrNull { placement ->
            placement.occurrence == 0 && bindingForSourceName(placement.node.name)?.meshKey == binding.meshKey
        }
    }

    fun isNodeSelected(
        nodeName: String?,
        placements: List<CatalogSemanticPlacement>,
        selectedEntityId: String?
    ): Boolean {
        if (selectedEntityId == null) return false
        return placementForNodeName(nodeName, placements)?.node?.id == selectedEntityId
    }

    fun serviceOffset(nodeName: String?, progress: Float): CatalogServiceOffset {
        val binding = bindingForNodeName(nodeName) ?: return CatalogServiceOffset.ZERO
        val stageStart = (binding.serviceStage - 1f) / 6f
        val localProgress = ((progress.coerceIn(0f, 1f) - stageStart) * 6f).coerceIn(0f, 1f)
        val eased = localProgress * localProgress * (3f - 2f * localProgress)
        return CatalogServiceOffset(
            x = binding.explodedOffset.x * eased * SERVICE_OFFSET_HORIZONTAL_SCALE,
            y = binding.explodedOffset.y * eased * SERVICE_OFFSET_VERTICAL_SCALE,
            z = binding.explodedOffset.z * eased * SERVICE_OFFSET_DEPTH_SCALE
        )
    }

    private fun binding(
        meshKey: String,
        serviceStage: Int,
        offsetX: Float,
        offsetY: Float,
        offsetZ: Float,
        vararg literalSourceNames: String
    ) = GenericEngineAssetBinding(
        meshKey = meshKey,
        literalSourceNames = literalSourceNames.toSet(),
        serviceStage = serviceStage,
        explodedOffset = CatalogServiceOffset(offsetX, offsetY, offsetZ)
    )

    private fun canonical(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase()
        .trim()
        .replace("\\s+".toRegex(), " ")
}
