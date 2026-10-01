package com.elysium.nexus.fabric.ranking

/**
 * V06-P27 — Candidate paging (MASTER_ORDER §75–§78 "Memory Budget":
 * candidate paging, bounded cache).
 *
 * [CandidatePager] walks a *pre-ranked* candidate source slice by slice
 * (the IR catalog's `getAllCandidates` sweep is ordered by probability,
 * §P0-8, and returned in ranked pages) and caches only a bounded window
 * of pages (LRU) — the consumer never materializes the whole candidate
 * list in memory.
 *
 * Memory bound: `loadedItemsCount <= pageSize * maxCachedPages`
 * (test-enforced).
 */
class CandidatePager<T>(
    /** Number of candidates per page. */
    val pageSize: Int,
    /** How many pages may stay in the LRU cache at once. */
    val maxCachedPages: Int = 4,
    /** Total candidate count from the source. */
    val totalCount: Int,
    /** Loads one ranked page: items [fromIndex, fromIndex + count). */
    private val pageLoader: suspend (fromIndex: Int, count: Int) -> List<T>
) {
    init {
        require(pageSize > 0) { "pageSize must be positive (got $pageSize)." }
        require(maxCachedPages > 0) { "maxCachedPages must be positive (got $maxCachedPages)." }
        require(totalCount >= 0) { "totalCount must be non-negative (got $totalCount)." }
    }

    /** Pages are addressable while items remain. */
    val pageCount: Int get() = (totalCount + pageSize - 1) / pageSize

    private val cache = object : LinkedHashMap<Int, List<T>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<T>>?): Boolean =
            size > maxCachedPages
    }

    /** Pages currently materialized in the cache. */
    val loadedPages: Int get() = cache.size

    /** Candidates currently materialized (the memory budget). */
    val loadedItems: Int get() = cache.values.sumOf { it.size }

    /** Non-suspend page accessor — returns null if the page isn't in cache. */
    fun getCachedPage(index: Int): List<T>? = cache[index]

    /**
     * Loads (or serves from cache) page [index].
     * Pages are immutable once loaded.
     */
    suspend fun page(index: Int): List<T> {
        require(index in 0 until pageCount) {
            "page index $index out of bounds (0..${pageCount - 1})"
        }
        cache[index]?.let { return it }
        val fromIndex = index * pageSize
        val count = minOf(pageSize, totalCount - fromIndex)
        val items = pageLoader(fromIndex, count)
        if (items.size > count) {
            throw IllegalStateException(
                "pageLoader returned ${items.size} items for requested $count (page $index)"
            )
        }
        cache[index] = items
        return items
    }

    /**
     * Bounded search for the first item matching [predicate] within
     * [searchPageLimit] pages starting at [startPage]. Returns the page
     * index and item (null when not found). Never loads beyond the limit,
     * so misses are possible past the search window — callers must not
     * assume global completeness (honest bound).
     */
    suspend fun findWithin(
        startPage: Int,
        searchPageLimit: Int,
        predicate: (T) -> Boolean
    ): Found<T>? {
        require(startPage in 0 until pageCount) { "startPage out of bounds" }
        require(searchPageLimit > 0) { "searchPageLimit must be positive" }
        val end = minOf(startPage + searchPageLimit, pageCount)
        for (p in startPage until end) {
            val idx = page(p).indexOfFirst(predicate)
            if (idx >= 0) return Found(p, page(p)[idx])
        }
        return null
    }

    /** Clears the LRU cache (memory release). */
    fun clearCache() = cache.clear()

    data class Found<T>(
        val pageIndex: Int,
        val item: T
    )
}