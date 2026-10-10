package com.elysium369.meet.safety.intelligence.domain

/**
 * Conservative Entity Resolution Engine for Corporate & Institutional Intelligence.
 *
 * Invariant: Never merge two companies, institutions or individuals solely based on
 * name similarity, shared municipality, or shared legal address.
 * High-confidence resolution requires verified official identifiers (Tax ID / Cédula Jurídica).
 */
enum class EntityResolutionOutcome {
    MATCH_CONFIRMED_IDENTIFIER,
    DISTINCT_ENTITIES_IDENTIFIER_MISMATCH,
    REJECTED_WEAK_MATCH_NAME_OR_ADDRESS_ONLY,
    CANDIDATE_REQUIRES_HUMAN_AUDIT,
}

data class EntityResolutionEvaluation(
    val outcome: EntityResolutionOutcome,
    val confidenceScore: Double,
    val rationale: String,
    val leftEntityId: String,
    val rightEntityId: String,
    val canAutomaticallyMerge: Boolean,
)

object EntityResolutionEngine {

    fun evaluate(left: EconomicEntity, right: EconomicEntity): EntityResolutionEvaluation {
        val leftTax = left.canonicalTaxId?.trim()?.replace("-", "")?.lowercase()
        val rightTax = right.canonicalTaxId?.trim()?.replace("-", "")?.lowercase()

        // 1. If both have canonical Tax IDs:
        if (!leftTax.isNullOrBlank() && !rightTax.isNullOrBlank()) {
            return if (leftTax == rightTax) {
                EntityResolutionEvaluation(
                    outcome = EntityResolutionOutcome.MATCH_CONFIRMED_IDENTIFIER,
                    confidenceScore = 1.0,
                    rationale = "Coincidencia exacta de identificador tributario / Cédula Jurídica (${left.canonicalTaxId}).",
                    leftEntityId = left.entityId,
                    rightEntityId = right.entityId,
                    canAutomaticallyMerge = true,
                )
            } else {
                EntityResolutionEvaluation(
                    outcome = EntityResolutionOutcome.DISTINCT_ENTITIES_IDENTIFIER_MISMATCH,
                    confidenceScore = 0.0,
                    rationale = "Identificadores tributarios distintos (${left.canonicalTaxId} vs ${right.canonicalTaxId}). Son entidades jurídicas independientes.",
                    leftEntityId = left.entityId,
                    rightEntityId = right.entityId,
                    canAutomaticallyMerge = false,
                )
            }
        }

        // 2. If one or both lack Tax ID, check name similarity:
        val leftNormName = normalizeName(left.registeredName)
        val rightNormName = normalizeName(right.registeredName)

        val namesMatch = leftNormName == rightNormName
        val similarity = calculateSimilarity(leftNormName, rightNormName)

        // Strict Conservative Rule: Name similarity alone is REJECTED for automatic merge
        if (namesMatch || similarity > 0.85) {
            val hasCrossReferencedAlias = left.aliases.any { alias ->
                right.canonicalTaxId != null && normalizeName(alias).contains(rightTax ?: "")
            } || right.aliases.any { alias ->
                left.canonicalTaxId != null && normalizeName(alias).contains(leftTax ?: "")
            }

            return if (hasCrossReferencedAlias) {
                EntityResolutionEvaluation(
                    outcome = EntityResolutionOutcome.CANDIDATE_REQUIRES_HUMAN_AUDIT,
                    confidenceScore = 0.65,
                    rationale = "Similitud fonética o de alias con referencia cruzada pero sin Cédula Jurídica canónica. Requiere validación por analista humano.",
                    leftEntityId = left.entityId,
                    rightEntityId = right.entityId,
                    canAutomaticallyMerge = false,
                )
            } else {
                EntityResolutionEvaluation(
                    outcome = EntityResolutionOutcome.REJECTED_WEAK_MATCH_NAME_OR_ADDRESS_ONLY,
                    confidenceScore = similarity * 0.5,
                    rationale = "Rechazado para fusión: Nombres similares o dirección compartida no constituyen prueba de identidad sin Cédula Jurídica verificada. Principio contra falsos positivos.",
                    leftEntityId = left.entityId,
                    rightEntityId = right.entityId,
                    canAutomaticallyMerge = false,
                )
            }
        }

        return EntityResolutionEvaluation(
            outcome = EntityResolutionOutcome.DISTINCT_ENTITIES_IDENTIFIER_MISMATCH,
            confidenceScore = 0.1,
            rationale = "Entidades claramente distintas sin convergencia en identificadores ni denominaciones.",
            leftEntityId = left.entityId,
            rightEntityId = right.entityId,
            canAutomaticallyMerge = false,
        )
    }

    private fun normalizeName(name: String): String {
        return name.lowercase()
            .replace(Regex("[,.\\-()'\"]"), "")
            .replace("sociedad anonima", "sa")
            .replace("s.a.", "sa")
            .replace("limitada", "ltda")
            .replace("s.r.l.", "srl")
            .trim()
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val maxLen = maxOf(s1.length, s2.length)
        val dist = levenshteinDistance(s1, s2)
        return 1.0 - (dist.toDouble() / maxLen.toDouble())
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        var prev = IntArray(rhs.length + 1) { it }
        var curr = IntArray(rhs.length + 1)

        for (i in 1..lhs.length) {
            curr[0] = i
            for (j in 1..rhs.length) {
                val cost = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost,
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[rhs.length]
    }
}
