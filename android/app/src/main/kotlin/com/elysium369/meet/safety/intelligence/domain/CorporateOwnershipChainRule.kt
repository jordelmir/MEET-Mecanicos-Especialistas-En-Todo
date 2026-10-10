package com.elysium369.meet.safety.intelligence.domain

/**
 * Deterministic Anomaly Detection: Corporate Ownership Chains & Circular Control.
 *
 * Evaluates:
 * 1. Circular ownership loops (A -> B -> C -> A) that conceal beneficial ownership.
 * 2. Shared legal representatives or beneficial owners across multiple bidding entities.
 *
 * Invariants:
 * - Deterministic graph cycle detection (Tarjan or DFS cycle traversal).
 * - Mandatory legitimate alternative explanations (holding groups, joint ventures, family corporate structures).
 * - An anomaly is NOT a crime. It triggers human analyst review, not a judicial conclusion.
 */
enum class OwnershipAnomalyType {
    CIRCULAR_OWNERSHIP_CYCLE,
    SHARED_REPRESENTATIVE_ACROSS_BIDDERS,
    DEEP_OPACITY_CHAIN,
    NORMAL_STRUCTURE,
}

data class OwnershipAnomalyAssessment(
    val anomalyType: OwnershipAnomalyType,
    val involvedEntityIds: List<String>,
    val cyclePathNames: List<String>,
    val explanation: String,
    val alternativeLegitimateHypotheses: List<String>,
    val requiresHumanAnalystReview: Boolean,
)

object CorporateOwnershipChainRule {

    const val RULE_ID = "RULE_CR_CORPORATE_CIRCULAR_CONTROL_v1"

    private val STANDARD_ALTERNATIVE_HYPOTHESES = listOf(
        "Estructura estándar de grupo holding o consolidación patrimonial empresarial legal",
        "Empresa conjunta (Joint Venture) o consorcio constituido para un proyecto de infraestructura específico",
        "Estructura fiduciaria o fideicomiso de administración patrimonial familiar permitido por ley",
        "Participación cruzada minoritaria resultante de fusiones corporativas previas",
    )

    fun evaluate(
        entities: List<EconomicEntity>,
        relationships: List<EntityRelationship>,
    ): OwnershipAnomalyAssessment {
        val entityMap = entities.associateBy { it.entityId }

        // 1. Build adjacency list for ownership relationships (SHAREHOLDER, SUBSIDIARY_OF)
        val ownershipEdges = relationships.filter {
            it.relationshipType == RelationshipType.SHAREHOLDER ||
                it.relationshipType == RelationshipType.SUBSIDIARY_OF
        }

        val adj = mutableMapOf<String, MutableList<String>>()
        for (rel in ownershipEdges) {
            adj.getOrPut(rel.sourceEntityId) { mutableListOf() }.add(rel.targetEntityId)
        }

        // 2. Detect cycle using DFS
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val parentPath = mutableListOf<String>()
        var detectedCycle: List<String>? = null

        fun dfs(node: String): Boolean {
            visited.add(node)
            recursionStack.add(node)
            parentPath.add(node)

            for (neighbor in adj[node] ?: emptyList()) {
                if (neighbor in recursionStack) {
                    val cycleStartIndex = parentPath.indexOf(neighbor)
                    if (cycleStartIndex != -1) {
                        detectedCycle = parentPath.subList(cycleStartIndex, parentPath.size).toList() + neighbor
                        return true
                    }
                } else if (neighbor !in visited) {
                    if (dfs(neighbor)) return true
                }
            }

            parentPath.removeAt(parentPath.size - 1)
            recursionStack.remove(node)
            return false
        }

        for (node in adj.keys) {
            if (node !in visited) {
                if (dfs(node)) break
            }
        }

        val cycle = detectedCycle
        if (cycle != null && cycle.size >= 3) {
            val names = cycle.map { entityMap[it]?.registeredName ?: it }
            return OwnershipAnomalyAssessment(
                anomalyType = OwnershipAnomalyType.CIRCULAR_OWNERSHIP_CYCLE,
                involvedEntityIds = cycle.distinct(),
                cyclePathNames = names,
                explanation = "Se detectó una estructura de propiedad circular: ${names.joinToString(" ➔ ")}. " +
                    "Esta topología puede dificultar la identificación del beneficiario final real según estándares GAFI/OECD.",
                alternativeLegitimateHypotheses = STANDARD_ALTERNATIVE_HYPOTHESES,
                requiresHumanAnalystReview = true,
            )
        }

        // 3. Check for shared legal representatives across multiple corporate entities
        val repEdges = relationships.filter { it.relationshipType == RelationshipType.LEGAL_REPRESENTATIVE }
        val repsToCorps = mutableMapOf<String, MutableList<String>>()
        for (rel in repEdges) {
            repsToCorps.getOrPut(rel.targetEntityId) { mutableListOf() }.add(rel.sourceEntityId)
        }

        val suspiciousRep = repsToCorps.entries.firstOrNull { it.value.size >= 4 }
        if (suspiciousRep != null) {
            val repName = entityMap[suspiciousRep.key]?.registeredName ?: suspiciousRep.key
            val corpNames = suspiciousRep.value.map { entityMap[it]?.registeredName ?: it }
            return OwnershipAnomalyAssessment(
                anomalyType = OwnershipAnomalyType.SHARED_REPRESENTATIVE_ACROSS_BIDDERS,
                involvedEntityIds = listOf(suspiciousRep.key) + suspiciousRep.value,
                cyclePathNames = corpNames,
                explanation = "Un mismo apoderado o representante legal ($repName) figura en ${suspiciousRep.value.size} entidades distintas: ${corpNames.joinToString(", ")}. " +
                    "Relevante para auditar si estas entidades participan en los mismos concursos públicos.",
                alternativeLegitimateHypotheses = listOf(
                    "Bufete de abogados o agente fiduciario que presta servicios corporativos de secretaría",
                    "Grupo empresarial con un director ejecutivo común autorizado por asamblea de accionistas",
                ),
                requiresHumanAnalystReview = true,
            )
        }

        return OwnershipAnomalyAssessment(
            anomalyType = OwnershipAnomalyType.NORMAL_STRUCTURE,
            involvedEntityIds = emptyList(),
            cyclePathNames = emptyList(),
            explanation = "Estructura corporativa analizada dentro de parámetros lineales regulares.",
            alternativeLegitimateHypotheses = emptyList(),
            requiresHumanAnalystReview = false,
        )
    }
}
