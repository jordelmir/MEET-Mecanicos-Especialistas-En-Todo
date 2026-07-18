package com.elysium369.meet.core.monetization

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class AdFormat {
    BANNER,
    NATIVE,
    INTERSTITIAL,
    REWARDED
}

data class AdGate(
    val id: String,
    val featureKey: FeatureKey,
    val adFormat: AdFormat,
    val rewardDurationMs: Long = 0,
    val maxUsesPerDay: Int = 5,
    val cooldownMinutes: Int = 5
)

@Singleton
class AdGateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entitlementManager: EntitlementManager
) {
    private val prefs = context.getSharedPreferences("meet_ad_gate_prefs", Context.MODE_PRIVATE)
    
    private val _isShowingAd = MutableStateFlow(false)
    val isShowingAd = _isShowingAd.asStateFlow()

    private val lastAdShowTimes = mutableMapOf<AdFormat, Long>()
    
    private val adGates = listOf(
        AdGate("gate_pdf", FeatureKey.REPORT_PDF_BASIC, AdFormat.REWARDED, rewardDurationMs = 30 * 60 * 1000), // 30 mins
        AdGate("gate_ai", FeatureKey.AI_DIAGNOSTIC_ADVANCED, AdFormat.REWARDED, rewardDurationMs = 15 * 60 * 1000), // 15 mins
        AdGate("gate_livelink", FeatureKey.LIVELINK_30_MIN, AdFormat.REWARDED, rewardDurationMs = 15 * 60 * 1000) // 15 mins
    )

    fun canShowAd(format: AdFormat, currentScreen: String, obdConnected: Boolean = false): Boolean {
        // Hard rule: No ads during safety or active session operations
        if (isSafetyOrEmergencyContext(currentScreen, obdConnected)) {
            Log.w("AdGateManager", "Ad placement BLOCKED by Safety policy in screen: $currentScreen")
            return false
        }

        if (format == AdFormat.INTERSTITIAL) {
            val lastShow = lastAdShowTimes[AdFormat.INTERSTITIAL] ?: 0L
            val frequencyMinutes = MonetizationRemoteConfig.interstitialFrequencyMin
            val cooldownMs = frequencyMinutes * 60 * 1000L
            if (System.currentTimeMillis() - lastShow < cooldownMs) {
                Log.d("AdGateManager", "Interstitial on cooldown. Remaining: ${(cooldownMs - (System.currentTimeMillis() - lastShow)) / 1000}s")
                return false
            }
        }
        return true
    }

    private fun isSafetyOrEmergencyContext(screen: String?, obdConnected: Boolean): Boolean {
        if (screen == null) return false
        val restricted = listOf(
            "hud",
            "active_tests",
            "oscilloscope",
            "service_resets",
            "tow_truck_service",
            "mechanic_service",
            "live_link",
            "connect",
            "dvir",
            "dashcam",
            "premium"
        )
        val normalized = screen.substringBefore("/").substringBefore("?")
        return normalized in restricted || (obdConnected && normalized == "scanner")
    }

    fun showAd(
        format: AdFormat,
        featureKey: FeatureKey? = null,
        onAdClosed: () -> Unit,
        onRewardEarned: (() -> Unit)? = null
    ) {
        if (_isShowingAd.value) return
        
        _isShowingAd.value = true
        lastAdShowTimes[format] = System.currentTimeMillis()

        // Simulate Ad Presentation with a 2.5s Delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            _isShowingAd.value = false
            if (format == AdFormat.REWARDED && featureKey != null) {
                val gate = adGates.find { it.featureKey == featureKey }
                val duration = gate?.rewardDurationMs ?: (15 * 60 * 1000L)
                entitlementManager.consumeRewardedUnlock(featureKey, duration)
                onRewardEarned?.invoke()
            }
            onAdClosed()
        }, 2500)
    }
}
