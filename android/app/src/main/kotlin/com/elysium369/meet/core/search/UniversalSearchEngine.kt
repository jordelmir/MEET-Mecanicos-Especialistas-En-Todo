package com.elysium369.meet.core.search

import com.elysium369.meet.core.domain.EntityRef
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UniversalSearchEngine @Inject constructor() {

    private val providers = mutableListOf<MeetSearchProvider>()

    fun registerProvider(provider: MeetSearchProvider) {
        if (!providers.contains(provider)) {
            providers.add(provider)
        }
    }

    suspend fun query(
        searchTerm: String,
        vehicleId: String? = null
    ): List<SearchResult> = coroutineScope {
        if (searchTerm.isBlank()) return@coroutineScope emptyList()
        val cleanTerm = searchTerm.trim().lowercase()

        val deferredResults = providers.map { provider ->
            async {
                try {
                    provider.search(cleanTerm, vehicleId)
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

        val collected = deferredResults.flatMap { it.await() }
        collected.sortedByDescending { it.matchScore }
    }
}
