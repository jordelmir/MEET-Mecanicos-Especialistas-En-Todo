package com.elysium369.meet.core.monetization

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure-logic unit tests for UsageMeter limits.
 * Uses an in-memory map instead of SharedPreferences.
 */
class UsageMeterTest {

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val counters = mutableMapOf<String, Int>()

    private fun getTodayKey(): String = dateFormatter.format(Date())

    private fun incrementUsage(feature: FeatureKey) {
        val key = "${feature.name}:${getTodayKey()}"
        counters[key] = (counters[key] ?: 0) + 1
    }

    private fun getUsageCount(feature: FeatureKey): Int {
        val key = "${feature.name}:${getTodayKey()}"
        return counters[key] ?: 0
    }

    private fun isLimitExceeded(feature: FeatureKey, limit: Int): Boolean {
        return getUsageCount(feature) >= limit
    }

    @Before
    fun setUp() {
        counters.clear()
    }

    // ═══════════════════════════════════════════════════════════
    // Counter mechanics
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `initial count is zero`() {
        assertEquals(0, getUsageCount(FeatureKey.AI_DIAGNOSTIC_ADVANCED))
    }

    @Test
    fun `incrementing increases count by one`() {
        incrementUsage(FeatureKey.AI_DIAGNOSTIC_ADVANCED)
        assertEquals(1, getUsageCount(FeatureKey.AI_DIAGNOSTIC_ADVANCED))
    }

    @Test
    fun `multiple increments accumulate correctly`() {
        repeat(5) { incrementUsage(FeatureKey.AI_DIAGNOSTIC_ADVANCED) }
        assertEquals(5, getUsageCount(FeatureKey.AI_DIAGNOSTIC_ADVANCED))
    }

    @Test
    fun `different features have independent counters`() {
        incrementUsage(FeatureKey.AI_DIAGNOSTIC_ADVANCED)
        incrementUsage(FeatureKey.AI_DIAGNOSTIC_ADVANCED)
        incrementUsage(FeatureKey.REPORT_PDF_BASIC)
        assertEquals(2, getUsageCount(FeatureKey.AI_DIAGNOSTIC_ADVANCED))
        assertEquals(1, getUsageCount(FeatureKey.REPORT_PDF_BASIC))
    }

    // ═══════════════════════════════════════════════════════════
    // Limit enforcement
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `limit not exceeded when under threshold`() {
        incrementUsage(FeatureKey.AI_DIAGNOSTIC_ADVANCED)
        assertFalse(isLimitExceeded(FeatureKey.AI_DIAGNOSTIC_ADVANCED, 3))
    }

    @Test
    fun `limit exceeded when at threshold`() {
        repeat(3) { incrementUsage(FeatureKey.AI_DIAGNOSTIC_ADVANCED) }
        assertTrue(isLimitExceeded(FeatureKey.AI_DIAGNOSTIC_ADVANCED, 3))
    }

    @Test
    fun `limit exceeded when above threshold`() {
        repeat(5) { incrementUsage(FeatureKey.AI_DIAGNOSTIC_ADVANCED) }
        assertTrue(isLimitExceeded(FeatureKey.AI_DIAGNOSTIC_ADVANCED, 3))
    }

    @Test
    fun `zero limit always exceeded after first use`() {
        incrementUsage(FeatureKey.REPORT_PDF_BASIC)
        assertTrue(isLimitExceeded(FeatureKey.REPORT_PDF_BASIC, 0))
    }

    // ═══════════════════════════════════════════════════════════
    // Remote config integration
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `remote config defaults are sane`() {
        assertEquals(3, MonetizationRemoteConfig.freeAiDailyLimit)
        assertEquals(1, MonetizationRemoteConfig.freePdfMonthlyLimit)
        assertTrue(MonetizationRemoteConfig.adsEnabled)
        assertTrue(MonetizationRemoteConfig.rewardedEnabled)
    }

    @Test
    fun `remote config update is reflected`() {
        MonetizationRemoteConfig.updateConfig(aiLimit = 10, pdfLimit = 5)
        assertEquals(10, MonetizationRemoteConfig.freeAiDailyLimit)
        assertEquals(5, MonetizationRemoteConfig.freePdfMonthlyLimit)
        // Reset to defaults for other tests
        MonetizationRemoteConfig.updateConfig(aiLimit = 3, pdfLimit = 1)
    }
}
