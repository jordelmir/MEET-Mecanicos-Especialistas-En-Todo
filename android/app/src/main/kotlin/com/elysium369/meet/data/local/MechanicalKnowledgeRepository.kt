package com.elysium369.meet.data.local

import com.elysium369.meet.data.local.dao.MechanicalKnowledgeDao
import com.elysium369.meet.data.local.entities.AutomotiveChemicalEntity
import com.elysium369.meet.data.local.entities.ComponentRebuildGuideEntity
import com.elysium369.meet.data.local.entities.MechanicalProcedureEntity
import com.elysium369.meet.data.local.entities.MeetKnowledgeMatrixEntity
import com.elysium369.meet.data.local.entities.SafetyProtocolEntity
import com.elysium369.meet.data.local.entities.SymptomGuideEntity
import com.elysium369.meet.data.local.entities.ToolUsageGuideEntity
import com.elysium369.meet.data.local.entities.TrenchKnowledgeEntity
import javax.inject.Inject
import javax.inject.Singleton

data class MechanicalKnowledgeBundle(
    val safetyProtocols: List<SafetyProtocolEntity>,
    val symptomGuides: List<SymptomGuideEntity>,
    val procedures: List<MechanicalProcedureEntity>,
    val rebuildGuides: List<ComponentRebuildGuideEntity>,
    val trenchKnowledge: List<TrenchKnowledgeEntity>,
    val chemicals: List<AutomotiveChemicalEntity>,
    val tools: List<ToolUsageGuideEntity>,
    val matrixLinks: List<MeetKnowledgeMatrixEntity>
)

@Singleton
class MechanicalKnowledgeRepository @Inject constructor(
    private val dao: MechanicalKnowledgeDao
) {
    suspend fun search(query: String): MechanicalKnowledgeBundle {
        val normalized = normalizeQuery(query)
        val searchTerms = buildSearchTerms(normalized)

        val relatedDtc = Regex("[PCBU][0-3][0-9A-F]{3}", RegexOption.IGNORE_CASE)
            .find(query)
            ?.value
            ?.uppercase()

        return MechanicalKnowledgeBundle(
            safetyProtocols = collectDistinct(searchTerms, { dao.searchSafetyProtocols(it) }) { it.protocolId },
            symptomGuides = collectDistinct(searchTerms, {
                when {
                    it == "oil_leak" -> listOfNotNull(dao.getSymptomGuide("oil_leak"))
                    it == "coolant_leak" -> listOfNotNull(dao.getSymptomGuide("coolant_leak"))
                    it == "alternator_not_charging" -> listOfNotNull(dao.getSymptomGuide("alternator_not_charging"))
                    it == "hard_start" -> listOfNotNull(dao.getSymptomGuide("hard_start"))
                    else -> dao.searchSymptomGuides(it)
                }
            }) { it.symptomId },
            procedures = collectDistinct(searchTerms, { dao.searchMechanicalProcedures(it) }) { it.componentId },
            rebuildGuides = collectDistinct(searchTerms, { dao.searchRebuildGuides(it) }) { it.componentId },
            trenchKnowledge = collectDistinct(searchTerms, { dao.searchTrenchKnowledge(it) }) { it.scenarioId },
            chemicals = collectDistinct(searchTerms, { dao.searchChemicals(it) }) { it.chemicalId },
            tools = collectDistinct(searchTerms, { dao.searchTools(it) }) { it.toolId },
            matrixLinks = collectDistinct(searchTerms, { dao.searchKnowledgeMatrix(it, relatedDtc) }) { "${it.dtcCode}:${it.componentName}" }
        )
    }

    private fun normalizeQuery(query: String): String {
        return query
            .lowercase()
            .replace(Regex("[^a-z0-9áéíóúñü\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildSearchTerms(normalized: String): List<String> {
        if (normalized.isBlank()) return emptyList()

        val terms = linkedSetOf(normalized)
        if (normalized.contains("fuga") && normalized.contains("aceite")) {
            terms += listOf("oil_leak", "fuga aceite", "aceite")
        }
        if (normalized.contains("fuga") && (normalized.contains("agua") || normalized.contains("coolant") || normalized.contains("refrigerante"))) {
            terms += listOf("coolant_leak", "refrigerante", "water pump")
        }
        if (normalized.contains("alternador")) {
            terms += listOf("alternator_not_charging", "alternador", "charging")
        }
        if (normalized.contains("arranque") || normalized.contains("no arranca")) {
            terms += listOf("hard_start", "starter", "arranque")
        }
        if (normalized.contains("freno")) {
            terms += listOf("brake", "spongy brake pedal", "brake_service_full")
        }
        normalized.split(" ")
            .filter { it.length >= 4 }
            .forEach { terms += it }
        return terms.toList()
    }

    private suspend fun <T, K> collectDistinct(
        searchTerms: List<String>,
        loader: suspend (String) -> List<T>,
        keySelector: (T) -> K
    ): List<T> {
        val merged = linkedMapOf<K, T>()
        for (term in searchTerms) {
            loader(term).forEach { item ->
                merged.putIfAbsent(keySelector(item), item)
            }
            if (merged.size >= 12) break
        }
        return merged.values.toList()
    }
}
