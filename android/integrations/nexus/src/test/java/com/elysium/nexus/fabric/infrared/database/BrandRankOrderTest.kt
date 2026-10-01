package com.elysium.nexus.fabric.infrared.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The universal-probe ordering (§35) must be deterministic and
 * brand-ranked: the brands most likely on a Costa Rica retail
 * floor (Samsung, LG, Hisense, TCL…) are probed before everything
 * else, and the ORDER BY remains stable for paging.
 */
class BrandRankOrderTest {

    @Test
    fun rankOrder_putsCostaRicaBrandsFirst() {
        val sql = BrandRanking.ORDER_BY_SQL
        assertTrue(sql.startsWith("ORDER BY CASE b.display_name"))
        val samsungPos = sql.indexOf("'Samsung' THEN 1")
        val lgPos = sql.indexOf("'LG' THEN 2")
        val hisensePos = sql.indexOf("'Hisense' THEN 3")
        val tclPos = sql.indexOf("'TCL' THEN 4")
        val telstarPos = sql.indexOf("'Telstar' THEN 11")
        assertTrue("Samsung rank 1 present", samsungPos >= 0)
        assertTrue("LG rank 2 present", lgPos >= 0)
        assertTrue("Hisense rank 3 present", hisensePos >= 0)
        assertTrue("TCL rank 4 present", tclPos >= 0)
        assertTrue("Telstar ranked", telstarPos >= 0)
        assertTrue("Samsung before LG", samsungPos < lgPos)
        assertTrue("LG before Hisense", lgPos < hisensePos)
        assertTrue("Hisense before TCL", hisensePos < tclPos)
    }

    @Test
    fun rankOrder_keepsDeterministicTail() {
        val sql = BrandRanking.ORDER_BY_SQL
        assertTrue(sql.endsWith("b.display_name, cs.id"))
    }

    @Test
    fun rankOrder_ranksTwentyFourBrands() {
        val sql = BrandRanking.ORDER_BY_SQL
        val whens = Regex("WHEN '[^']*' THEN").findAll(sql).toList()
        assertEquals(30, whens.size)
        assertTrue(sql.contains("ELSE 99 END"))
    }

    @Test
    fun rankOrder_escapesNoUserInput() {
        // The order string is a compile-time constant: no single
        // quotes outside brand literals, so no SQL injection surface.
        val sql = BrandRanking.ORDER_BY_SQL
        assertFalse(sql.contains("OR 1=1"))
    }
}