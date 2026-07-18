package com.elysium369.meet.visual3d.domain

import com.elysium369.meet.core.engine3d.UniversalCatalogSceneNode

enum class SemanticPrimitive {
    BOX,
    CYLINDER,
    SHAFT,
    DISC,
    PISTON,
    CHAIN
}

data class CatalogSemanticPlacement(
    val node: UniversalCatalogSceneNode,
    val x: Float,
    val y: Float,
    val z: Float,
    val scale: Float,
    val primitive: SemanticPrimitive,
    val occurrence: Int
)

object CatalogSemanticScenePlanner {
    const val MAX_VISIBLE_COMPONENTS = 72
    private const val INSPECTION_MAGNIFICATION = 2.15f

    private data class MechanicalAnchor(
        val x: Float,
        val y: Float,
        val z: Float,
        val scale: Float
    )

    fun placements(
        nodes: List<UniversalCatalogSceneNode>,
        selectedNodeId: String?,
        maxVisible: Int = MAX_VISIBLE_COMPONENTS
    ): List<CatalogSemanticPlacement> {
        if (nodes.isEmpty() || maxVisible <= 0) return emptyList()

        val bounded = nodes.take(maxVisible).toMutableList()
        val selected = selectedNodeId?.let { id -> nodes.firstOrNull { it.id == id } }
        if (selected != null && bounded.none { it.id == selected.id }) {
            if (bounded.size == maxVisible) bounded.removeAt(bounded.lastIndex)
            bounded.add(selected)
        }

        val occurrences = mutableMapOf<String, Int>()
        var duplicateRecordIndex = 0
        return bounded.distinctBy { it.id }.mapIndexed { index, node ->
            val normalizedName = node.name.lowercase().trim()
            val occurrence = occurrences.getOrDefault(normalizedName, 0)
            occurrences[normalizedName] = occurrence + 1
            val anchor = mechanicalAnchor(node.name, occurrence, index)
            val selectedNode = node.id == selectedNodeId
            val duplicateRecord = occurrence > 0
            val duplicateIndex = if (duplicateRecord) duplicateRecordIndex++ else -1
            CatalogSemanticPlacement(
                node = node,
                x = if (duplicateRecord) (duplicateIndex % 12 - 5.5f) * 0.18f else anchor.x * INSPECTION_MAGNIFICATION,
                y = if (duplicateRecord) 0.28f + (duplicateIndex % 3) * 0.025f else anchor.y * INSPECTION_MAGNIFICATION,
                z = if (duplicateRecord) -0.92f - (duplicateIndex / 12) * 0.12f else anchor.z * INSPECTION_MAGNIFICATION,
                scale = if (duplicateRecord) {
                    if (selectedNode) 4.2f else 3.4f
                } else {
                    anchor.scale * INSPECTION_MAGNIFICATION * if (selectedNode) 1.18f else 1f
                },
                primitive = primitiveFor(node.name),
                occurrence = occurrence
            )
        }
    }

    private fun mechanicalAnchor(name: String, occurrence: Int, fallbackIndex: Int): MechanicalAnchor {
        val normalized = name.lowercase()
        val lane = occurrence % 4
        val cylinderX = floatArrayOf(-0.48f, -0.16f, 0.16f, 0.48f)[lane]
        return when {
            "bloque de motor" in normalized -> MechanicalAnchor(0f, 0.05f, 0.02f, 6.4f)
            "tapas de bancada" in normalized -> MechanicalAnchor(cylinderX, -0.06f, -0.28f, 2.4f)
            normalized == "cigüeñal" || normalized == "ciguenal" -> MechanicalAnchor(0f, -0.14f, -0.16f, 5.6f)
            "muñequilla" in normalized || "munequilla" in normalized -> MechanicalAnchor(cylinderX, -0.19f, -0.12f, 2.0f)
            "contrapeso" in normalized -> MechanicalAnchor(cylinderX, -0.12f, -0.19f, 2.3f)
            "cojinetes principales" in normalized -> MechanicalAnchor(cylinderX, -0.22f, -0.25f, 1.8f)
            "cojinetes de biela" in normalized -> MechanicalAnchor(cylinderX, -0.11f, -0.02f, 1.65f)
            normalized.startsWith("biela") -> MechanicalAnchor(cylinderX, -0.03f, 0.16f, 2.5f)
            normalized.startsWith("pist") -> MechanicalAnchor(cylinderX, 0.01f, 0.43f, 2.75f)
            "pernos de pist" in normalized -> MechanicalAnchor(cylinderX, -0.04f, 0.43f, 1.55f)
            "seguros de pist" in normalized -> MechanicalAnchor(cylinderX + 0.08f, -0.03f, 0.43f, 1.25f)
            "anillos de pist" in normalized -> MechanicalAnchor(cylinderX, 0.04f, 0.53f, 1.65f)
            "volante" in normalized || "flexplate" in normalized -> MechanicalAnchor(-0.91f, 0.02f, -0.13f, 4.5f)
            "polea de cig" in normalized -> MechanicalAnchor(0.91f, 0.02f, -0.13f, 3.5f)
            "retén delantero" in normalized || "reten delantero" in normalized -> MechanicalAnchor(0.78f, -0.04f, -0.13f, 1.8f)
            "retén trasero" in normalized || "reten trasero" in normalized -> MechanicalAnchor(-0.78f, -0.04f, -0.13f, 1.8f)
            "tapones de bloque" in normalized -> MechanicalAnchor(-0.70f + lane * 0.46f, 0.12f, 0.08f, 1.55f)
            "sensor de posición" in normalized || "sensor de posicion" in normalized -> MechanicalAnchor(0.70f, -0.04f, -0.01f, 1.65f)
            "rueda fónica" in normalized || "rueda fonica" in normalized -> MechanicalAnchor(0.68f, 0.02f, -0.13f, 2.4f)
            normalized == "eje balanceador" -> MechanicalAnchor(0f, 0.13f, -0.37f, 4.8f)
            "cojinetes de eje balanceador" in normalized -> MechanicalAnchor(cylinderX, 0.08f, -0.37f, 1.55f)
            "cadena" in normalized || "correa" in normalized -> MechanicalAnchor(0.88f, 0.09f, 0.18f, 3.6f)
            else -> {
                val column = fallbackIndex % 8
                val row = (fallbackIndex / 8) % 6
                MechanicalAnchor(
                    x = (column - 3.5f) * 0.24f,
                    y = ((fallbackIndex % 3) - 1f) * 0.07f,
                    z = (row - 2.5f) * 0.18f,
                    scale = 1.8f
                )
            }
        }
    }

    internal fun primitiveFor(name: String): SemanticPrimitive {
        val normalized = name.lowercase()
        return when {
            listOf("cadena", "correa")
                .any(normalized::contains) -> SemanticPrimitive.CHAIN
            listOf("cigüeñal", "ciguenal", "árbol", "arbol", "eje ", "muñequilla", "munequilla")
                .any(normalized::contains) -> SemanticPrimitive.SHAFT
            listOf("pistón", "piston", "biela", "válvula", "valvula", "perno")
                .any(normalized::contains) -> SemanticPrimitive.PISTON
            listOf("volante", "flexplate", "polea", "rueda fónica", "rueda fonica", "cojinete", "anillo", "retén", "reten")
                .any(normalized::contains) -> SemanticPrimitive.DISC
            listOf("bloque", "culata", "tapa", "cárter", "carter", "múltiple", "multiple")
                .any(normalized::contains) -> SemanticPrimitive.BOX
            else -> SemanticPrimitive.CYLINDER
        }
    }
}
