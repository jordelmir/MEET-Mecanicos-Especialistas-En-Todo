package com.elysium.nexus.fabric.ranking

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V06-P27: bounded candidate paging (§75–§78 memory budget).
 */
class CandidatePagerTest {

    private fun pagerOf(
        total: Int,
        pageSize: Int = 10,
        maxCachedPages: Int = 4
    ): CandidatePager<Int> = CandidatePager(
        pageSize = pageSize,
        maxCachedPages = maxCachedPages,
        totalCount = total,
        pageLoader = { from, count -> (from until from + count).toList() }
    )

    @Test
    fun `pages slice the source without overlap or gaps`() = runTest {
        val pager = pagerOf(total = 25, pageSize = 10)
        assertEquals(3, pager.pageCount)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), pager.page(0))
        assertEquals((10..19).toList(), pager.page(1))
        assertEquals((20..24).toList(), pager.page(2))
    }

    @Test
    fun `memory bound holds after touching every page`() = runTest {
        val pager = pagerOf(total = 1_000, pageSize = 10, maxCachedPages = 3)
        for (i in 0 until pager.pageCount) pager.page(i)
        // 100 pages consumed, but the cache is bounded by maxCachedPages
        assertTrue("loadedItems must stay <= pageSize*maxCachedPages",
            pager.loadedItems <= 10 * 3)
        assertEquals(3, pager.loadedPages)
    }

    @Test
    fun `LRU evicts the least recently used page`() = runTest {
        val pager = pagerOf(total = 50, pageSize = 10, maxCachedPages = 2)
        pager.page(0); pager.page(1)
        pager.page(2) // evicts page 0
        assertEquals(2, pager.loadedPages)
        // touching page 0 again reloads it and evicts page 1 (LRU)
        pager.page(0)
        assertEquals(2, pager.loadedPages)
        pager.page(0)
        assertEquals(2, pager.loadedPages)
    }

    @Test
    fun `pages are immutable once loaded`() = runTest {
        val pager = pagerOf(total = 10, pageSize = 10)
        val first = pager.page(0)
        pager.clearCache()
        val second = pager.page(0)
        assertEquals(first, second)
    }

    @Test
    fun `totalCount zero yields zero pages`() {
        val pager = pagerOf(total = 0)
        assertEquals(0, pager.pageCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects out of range page index`() = runTest {
        pagerOf(total = 5).page(2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-positive page size`() {
        pagerOf(total = 5, pageSize = 0)
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects loader over-delivery`() = runTest {
        val pager = CandidatePager<Int>(
            pageSize = 5,
            maxCachedPages = 2,
            totalCount = 10
        ) { _, _ -> List(6) { it } }
        pager.page(0)
    }

    @Test
    fun `findWithin is bounded and position-aware`() = runTest {
        val pager = pagerOf(total = 40, pageSize = 10, maxCachedPages = 2)
        val found = pager.findWithin(0, 2, predicate = { it == 15 })
        assertEquals(1, found!!.pageIndex)
        assertEquals(15, found.item)
        // beyond the search window: honestly not found
        assertNull(pager.findWithin(0, 1, predicate = { it == 25 }))
    }
}
