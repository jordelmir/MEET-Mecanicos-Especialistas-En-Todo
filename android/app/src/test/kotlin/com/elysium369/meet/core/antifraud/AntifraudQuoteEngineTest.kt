package com.elysium369.meet.core.antifraud

import org.junit.Assert.*
import org.junit.Test

class AntifraudQuoteEngineTest {
    @Test fun `clean quote scores high`() {
        val e = AntifraudQuoteEngine()
        val q = RepairQuote("q1", "m1", "Carlos", "v1", "Corolla 2020",
            lines = listOf(QuoteLineItem("l1", "Cambio de aceite", LineCategory.LABOR, 1, 15000)),
            relatedDtcCodes = listOf("P0301"))
        val a = e.analyze(q)
        assertTrue(a.overallTrustScore >= 80)
        assertTrue(a.isClean)
    }
    @Test fun `overpriced part triggers warning`() {
        val e = AntifraudQuoteEngine()
        e.registerReferencePrice("pastillas de freno", ReferencePrice("Pastillas", 20000, 15000, 28000, 50))
        val q = RepairQuote("q1", "m1", "Mec", "v1", "Car",
            lines = listOf(QuoteLineItem("l1", "Pastillas de freno", LineCategory.PART, 1, 50000)))
        val a = e.analyze(q)
        assertTrue(a.alerts.any { it.type == FraudType.PARTS_OVERPRICED })
    }
    @Test fun `double billing detected`() {
        val e = AntifraudQuoteEngine()
        val q = RepairQuote("q1", "m1", "Mec", "v1", "Car",
            lines = listOf(
                QuoteLineItem("l1", "Cambio de aceite motor", LineCategory.LABOR, 1, 15000),
                QuoteLineItem("l2", "Cambio de aceite motor", LineCategory.LABOR, 1, 15000)))
        val a = e.analyze(q)
        assertTrue(a.alerts.any { it.type == FraudType.DOUBLE_BILLING })
    }
    @Test fun `excessive labor hours flagged`() {
        val e = AntifraudQuoteEngine()
        val q = RepairQuote("q1", "m1", "Mec", "v1", "Car",
            lines = listOf(QuoteLineItem("l1", "Reparación motor", LineCategory.LABOR, 20, 5000)))
        val a = e.analyze(q)
        assertTrue(a.alerts.any { it.type == FraudType.LABOR_TIME_EXCESSIVE })
    }
    @Test fun `integrity hash generated`() {
        val e = AntifraudQuoteEngine()
        val q = RepairQuote("q1", "m1", "Mec", "v1", "Car",
            lines = listOf(QuoteLineItem("l1", "Test", LineCategory.LABOR, 1, 5000)))
        val a = e.analyze(q)
        assertTrue(a.integrityHash.length == 64)
    }
    @Test fun `trust label maps correctly`() {
        val e = AntifraudQuoteEngine()
        val q = RepairQuote("q1", "m1", "Mec", "v1", "Car",
            lines = listOf(QuoteLineItem("l1", "Test", LineCategory.LABOR, 1, 5000)))
        val a = e.analyze(q)
        assertTrue(a.trustLabel.isNotEmpty())
    }
}
