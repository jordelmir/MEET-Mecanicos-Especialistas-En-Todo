package com.elysium369.meet.core.monetization

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageMeter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entitlementManager: EntitlementManager
) {
    private val prefs = context.getSharedPreferences("meet_usage_meter_prefs", Context.MODE_PRIVATE)
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun getTodayKey(): String = dateFormatter.format(Date())

    fun incrementUsage(feature: FeatureKey) {
        val today = getTodayKey()
        val key = "${feature.name}:$today"
        val count = prefs.getInt(key, 0)
        prefs.edit().putInt(key, count + 1).apply()
        Log.d("UsageMeter", "Incremented usage for $feature today. Count: ${count + 1}")
    }

    fun getUsageCount(feature: FeatureKey): Int {
        val today = getTodayKey()
        val key = "${feature.name}:$today"
        return prefs.getInt(key, 0)
    }

    fun isLimitExceeded(feature: FeatureKey): Boolean {
        // Evaluate if PRO or above is already active
        val decision = entitlementManager.requireAccess(feature)
        if (decision is AccessDecision.Allowed) {
            return false
        }

        val current = getUsageCount(feature)
        val limit = getLimitForFeature(feature)
        return current >= limit
    }

    fun getLimitForFeature(feature: FeatureKey): Int {
        return when (feature) {
            FeatureKey.AI_DIAGNOSTIC_ADVANCED -> MonetizationRemoteConfig.freeAiDailyLimit
            FeatureKey.REPORT_PDF_BASIC -> MonetizationRemoteConfig.freePdfMonthlyLimit
            else -> 9999
        }
    }
}
