package com.elysium369.meet.ride.map

import com.elysium369.meet.core.agent.laya.LayaSpatialSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ══════════════════════════════════════════════════════════════════════
 *  L A Y A   E N H A N C E D   P L A C E   S E A R C H   P R O V I D E R
 *  ──────────────────────────────────────────────────────────────
 *  Wraps any remote RidePlaceSearchProvider with Laya's spatial
 *  Costa Rican knowledge base and semantic reranking.
 *  - Guarantees local proximity priority (San José CR > San Jose CA).
 *  - 100% resilient: if network/Photon fails, local gazetteer serves in <2ms.
 * ══════════════════════════════════════════════════════════════════════
 */
class LayaEnhancedPlaceSearchProvider(
    private val delegate: RidePlaceSearchProvider? = null,
    private val spatialEngine: LayaSpatialSearchEngine = LayaSpatialSearchEngine(),
) : RidePlaceSearchProvider {

    override suspend fun search(
        query: String,
        biasLatitude: Double?,
        biasLongitude: Double?,
        limit: Int,
    ): List<RidePlaceSuggestion> = withContext(Dispatchers.Default) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        // 1. Fetch local Costa Rica gazetteer results
        val localResults = spatialEngine.search(trimmed, biasLatitude, biasLongitude, limit)

        // 2. Query delegate if available
        val externalResults = try {
            if (delegate != null) {
                delegate.search(trimmed, biasLatitude, biasLongitude, limit * 2)
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }

        // 3. Rerank external results with distance penalty for non-CR places
        val rerankedExternal = spatialEngine.rerankExternalResults(
            query = trimmed,
            externalResults = externalResults,
            biasLatitude = biasLatitude,
            biasLongitude = biasLongitude
        )

        // 4. Merge results: high-confidence local first, then reranked external
        val combined = mutableListOf<RidePlaceSuggestion>()
        val seenLabels = mutableSetOf<String>()

        for (item in localResults) {
            val key = "${item.primaryLabel}_${item.secondaryLabel}"
            if (seenLabels.add(key)) {
                combined.add(item)
            }
        }

        for (item in rerankedExternal) {
            val key = "${item.primaryLabel}_${item.secondaryLabel}"
            if (seenLabels.add(key)) {
                combined.add(item)
            }
        }

        combined.take(limit)
    }

    fun parseVoiceSpeech(speechText: String): LayaSpatialSearchEngine.ParsedVoiceSearchIntent {
        return spatialEngine.parseVoiceQuery(speechText)
    }
}
