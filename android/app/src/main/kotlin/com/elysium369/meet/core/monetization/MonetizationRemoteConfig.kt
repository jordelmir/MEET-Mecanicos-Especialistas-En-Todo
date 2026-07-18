package com.elysium369.meet.core.monetization

object MonetizationRemoteConfig {
    var adsEnabled: Boolean = true
    var interstitialFrequencyMin: Int = 3
    var rewardedEnabled: Boolean = true
    var paywallVariant: String = "cyberpunk_neon"
    var freeAiDailyLimit: Int = 3
    var freePdfMonthlyLimit: Int = 1
    var livelinkFreeMinutes: Int = 5
    
    var proPriceCopy: String = "$9.99/mes"
    var elitePriceCopy: String = "$24.99/mes"
    var showRemoveAds: Boolean = true
    var marketplaceCommissionPercent: Double = 10.0
    var fleetTrialDays: Int = 14

    fun updateConfig(
        ads: Boolean = adsEnabled,
        frequency: Int = interstitialFrequencyMin,
        rewarded: Boolean = rewardedEnabled,
        variant: String = paywallVariant,
        aiLimit: Int = freeAiDailyLimit,
        pdfLimit: Int = freePdfMonthlyLimit,
        livelinkMin: Int = livelinkFreeMinutes
    ) {
        adsEnabled = ads
        interstitialFrequencyMin = frequency
        rewardedEnabled = rewarded
        paywallVariant = variant
        freeAiDailyLimit = aiLimit
        freePdfMonthlyLimit = pdfLimit
        livelinkFreeMinutes = livelinkMin
    }
}
