package com.elysium369.meet.core.agent.ui

import com.elysium369.meet.core.agent.laya.LayaDecisionEngine
import com.elysium369.meet.core.agent.laya.LayaQuestion

data class UiTargetMatch(
    val control: AgentUiControlSnapshot,
    val confidence: Double,
    val matchType: String = "LEXICAL",
)

sealed interface UiTargetResolutionResult {
    data class Exact(val match: UiTargetMatch) : UiTargetResolutionResult
    data class Ambiguous(val query: String, val candidates: List<UiTargetMatch>) : UiTargetResolutionResult
    data class NotFound(val query: String) : UiTargetResolutionResult
}

/**
 * ══════════════════════════════════════════════════════════════════════
 *  U I   T A R G E T   R E S O L V E R
 *  ──────────────────────────────────────────────────────────────
 *  Resuelve texto hablado contra la identidad textual de los controles
 *  visibles reales en la pantalla.
 *  Regla inviolable: Solo resuelve contra controles reales, jamás inventa.
 * ══════════════════════════════════════════════════════════════════════
 */
class UiTargetResolver(
    private val layaDecisionEngine: LayaDecisionEngine? = null,
) {

    suspend fun resolve(
        spokenTarget: String,
        visibleControls: List<AgentUiControlSnapshot>,
    ): UiTargetResolutionResult {
        val query = UiTextNormalizer.normalize(spokenTarget)
        if (query.isBlank() || visibleControls.isEmpty()) {
            return UiTargetResolutionResult.NotFound(spokenTarget)
        }

        // 1. Coincidencia exacta con la etiqueta visible (1.00)
        val exactLabels = visibleControls.filter { control ->
            UiTextNormalizer.normalize(control.label) == query
        }
        if (exactLabels.size > 1) return UiTargetResolutionResult.Ambiguous(spokenTarget,
            exactLabels.map { UiTargetMatch(it, 1.0, "EXACT_LABEL") })
        val exactLabel = exactLabels.singleOrNull()
        if (exactLabel != null) {
            return UiTargetResolutionResult.Exact(
                UiTargetMatch(control = exactLabel, confidence = 1.0, matchType = "EXACT_LABEL")
            )
        }

        // 2. Coincidencia exacta con un alias registrado (0.99)
        val exactAliases = visibleControls.filter { control ->
            control.aliases.any { UiTextNormalizer.normalize(it) == query }
        }
        if (exactAliases.size > 1) return UiTargetResolutionResult.Ambiguous(spokenTarget,
            exactAliases.map { UiTargetMatch(it, 0.99, "EXACT_ALIAS") })
        val exactAlias = exactAliases.singleOrNull()
        if (exactAlias != null) {
            return UiTargetResolutionResult.Exact(
                UiTargetMatch(control = exactAlias, confidence = 0.99, matchType = "EXACT_ALIAS")
            )
        }

        // 3. Búsqueda léxica clasificada (Prefijo, Contenido, Similitud de tokens)
        val candidates = visibleControls.map { control ->
            val labelNorm = UiTextNormalizer.normalize(control.label)
            val aliasNorms = control.aliases.map { UiTextNormalizer.normalize(it) }

            val score = calculateScore(query, labelNorm, aliasNorms)
            UiTargetMatch(control = control, confidence = score, matchType = "FUZZY_MATCH")
        }
            .filter { it.confidence >= 0.70 }
            .sortedByDescending { it.confidence }
            .take(6)

        if (candidates.isEmpty()) {
            return UiTargetResolutionResult.NotFound(spokenTarget)
        }

        val first = candidates[0]
        val second = candidates.getOrNull(1)

        // Si el primero supera el umbral estricto (>= 0.90) y tiene margen de seguridad (>= 0.08)
        if (first.confidence >= 0.90 && (second == null || (first.confidence - second.confidence >= 0.08))) {
            return UiTargetResolutionResult.Exact(first)
        }

        // Si hay ambigüedad y tenemos Laya disponible, permitir reranking de candidatos reales visibles
        if (candidates.size > 1 && layaDecisionEngine != null) {
            val reranked = rerankWithLaya(spokenTarget, candidates)
            if (reranked != null) {
                return UiTargetResolutionResult.Exact(reranked)
            }
        }

        return UiTargetResolutionResult.Ambiguous(spokenTarget, candidates)
    }

    private fun calculateScore(query: String, label: String, aliases: List<String>): Double {
        var best = 0.0

        fun evaluateSingle(target: String) {
            when {
                target == query -> best = maxOf(best, 1.0)
                target.startsWith(query) -> best = maxOf(best, 0.95)
                query.startsWith(target) -> best = maxOf(best, 0.94)
                target.contains(query) -> best = maxOf(best, 0.92)
                query.contains(target) -> best = maxOf(best, 0.91)
                else -> {
                    val sim = tokenSimilarity(query, target)
                    best = maxOf(best, sim)
                }
            }
        }

        evaluateSingle(label)
        aliases.forEach { evaluateSingle(it) }
        return best
    }

    private fun tokenSimilarity(s1: String, s2: String): Double {
        val t1 = s1.split(" ").filter { it.isNotBlank() }.toSet()
        val t2 = s2.split(" ").filter { it.isNotBlank() }.toSet()
        if (t1.isEmpty() || t2.isEmpty()) return 0.0

        val intersection = t1.intersect(t2).size
        val union = t1.union(t2).size
        if (union == 0) return 0.0

        val jaccard = intersection.toDouble() / union.toDouble()
        // Bonus si hay coincidencia de prefijos de palabras clave
        val prefixMatches = t1.count { w1 -> t2.any { w2 -> w1.startsWith(w2) || w2.startsWith(w1) } }
        val prefixBonus = (prefixMatches.toDouble() / maxOf(t1.size, t2.size)) * 0.15

        return (jaccard * 0.75 + prefixBonus).coerceIn(0.0, 0.89)
    }

    private suspend fun rerankWithLaya(query: String, candidates: List<UiTargetMatch>): UiTargetMatch? {
        return try {
            val engine = layaDecisionEngine ?: return null
            val options = candidates.map { it.control.id.value }
            val question = LayaQuestion.Choice(name = "target_control", options = options)
            val result = engine.evaluate("Usuario busca: $query", listOf(question))
            val choice = result.choice("target_control")
            if (choice != null && choice.confidence >= 0.85) {
                candidates.firstOrNull { it.control.id.value == choice.value }
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
