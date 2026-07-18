package com.elysium369.meet.core.billing

import com.android.billingclient.api.BillingClient
import com.elysium369.meet.core.monetization.EntitlementKey

enum class MeetProductType(val playType: String) {
    InApp(BillingClient.ProductType.INAPP),
    Subscription(BillingClient.ProductType.SUBS)
}

data class MeetBillingProduct(
    val productId: String,
    val type: MeetProductType,
    val entitlementKey: EntitlementKey,
    val isConsumable: Boolean = false
)

object PlayBillingCatalog {
    // Subscriptions
    const val PRO_MONTHLY = "pro_monthly"
    const val PRO_YEARLY = "pro_yearly"
    const val ELITE_MONTHLY = "elite_monthly"
    const val ELITE_YEARLY = "elite_yearly"
    const val FLEET_STARTER_MONTHLY = "fleet_starter_monthly"
    const val FLEET_PRO_MONTHLY = "fleet_pro_monthly"

    // One-time products (Non-consumable / Consumable)
    const val REPORT_PDF_SINGLE = "report_pdf_single"
    const val PRE_PURCHASE_REPORT_SINGLE = "pre_purchase_report_single"
    const val LIVELINK_30MIN = "livelink_30min"
    const val LIVELINK_60MIN = "livelink_60min"
    const val GAUGE_PACK_PREMIUM = "gauge_pack_premium"
    const val OSCILLOSCOPE_PACK = "oscilloscope_pack"
    const val MANUAL_INDEX_PACK_LOCAL = "manual_index_pack_local"

    // Consumables / Credit Packs
    const val AI_CREDIT_PACK_10 = "ai_credit_pack_10"
    const val REPORT_CREDIT_PACK_5 = "report_credit_pack_5"
    const val LIVELINK_CREDIT_PACK_3 = "livelink_credit_pack_3"

    // Lifetimes
    const val REMOVE_ADS_LIFETIME = "remove_ads_lifetime"
    const val PREMIUM_GAUGE_PACK_LIFETIME = "premium_gauge_pack_lifetime"
    const val OFFLINE_MANUAL_TOOLS_LIFETIME = "offline_manual_tools_lifetime"

    val products: List<MeetBillingProduct> = listOf(
        // Subscriptions
        MeetBillingProduct(PRO_MONTHLY, MeetProductType.Subscription, EntitlementKey.PRO_ACCESS),
        MeetBillingProduct(PRO_YEARLY, MeetProductType.Subscription, EntitlementKey.PRO_ACCESS),
        MeetBillingProduct(ELITE_MONTHLY, MeetProductType.Subscription, EntitlementKey.ELITE_ACCESS),
        MeetBillingProduct(ELITE_YEARLY, MeetProductType.Subscription, EntitlementKey.ELITE_ACCESS),
        MeetBillingProduct(FLEET_STARTER_MONTHLY, MeetProductType.Subscription, EntitlementKey.FLEET_ACCESS),
        MeetBillingProduct(FLEET_PRO_MONTHLY, MeetProductType.Subscription, EntitlementKey.FLEET_ACCESS),

        // One-time
        MeetBillingProduct(REPORT_PDF_SINGLE, MeetProductType.InApp, EntitlementKey.PDF_REPORTS),
        MeetBillingProduct(PRE_PURCHASE_REPORT_SINGLE, MeetProductType.InApp, EntitlementKey.PERITO_REPORT),
        MeetBillingProduct(LIVELINK_30MIN, MeetProductType.InApp, EntitlementKey.LIVELINK_BASIC, isConsumable = true),
        MeetBillingProduct(LIVELINK_60MIN, MeetProductType.InApp, EntitlementKey.LIVELINK_PRO, isConsumable = true),
        MeetBillingProduct(GAUGE_PACK_PREMIUM, MeetProductType.InApp, EntitlementKey.GAUGE_MARKET_ACCESS),
        MeetBillingProduct(OSCILLOSCOPE_PACK, MeetProductType.InApp, EntitlementKey.OSCILLOSCOPE_ADVANCED),
        MeetBillingProduct(MANUAL_INDEX_PACK_LOCAL, MeetProductType.InApp, EntitlementKey.MANUALS_OFFLINE),

        // Consumables
        MeetBillingProduct(AI_CREDIT_PACK_10, MeetProductType.InApp, EntitlementKey.AI_ADVANCED, isConsumable = true),
        MeetBillingProduct(REPORT_CREDIT_PACK_5, MeetProductType.InApp, EntitlementKey.PDF_REPORTS, isConsumable = true),
        MeetBillingProduct(LIVELINK_CREDIT_PACK_3, MeetProductType.InApp, EntitlementKey.LIVELINK_BASIC, isConsumable = true),

        // Lifetimes
        MeetBillingProduct(REMOVE_ADS_LIFETIME, MeetProductType.InApp, EntitlementKey.ADS_REMOVED),
        MeetBillingProduct(PREMIUM_GAUGE_PACK_LIFETIME, MeetProductType.InApp, EntitlementKey.GAUGE_MARKET_ACCESS),
        MeetBillingProduct(OFFLINE_MANUAL_TOOLS_LIFETIME, MeetProductType.InApp, EntitlementKey.MANUALS_OFFLINE)
    )

    fun product(productId: String): MeetBillingProduct {
        return products.firstOrNull { it.productId == productId }
            ?: MeetBillingProduct(productId, MeetProductType.InApp, EntitlementKey.PRO_ACCESS, isConsumable = false)
    }

    fun productType(productId: String): String = product(productId).type.playType

    fun isConsumable(productId: String): Boolean = product(productId).isConsumable
}
