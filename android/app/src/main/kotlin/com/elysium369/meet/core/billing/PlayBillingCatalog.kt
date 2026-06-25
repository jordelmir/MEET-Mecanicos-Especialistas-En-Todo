package com.elysium369.meet.core.billing

import com.android.billingclient.api.BillingClient

enum class MeetProductType(val playType: String) {
    InApp(BillingClient.ProductType.INAPP),
    Subscription(BillingClient.ProductType.SUBS)
}

data class MeetBillingProduct(
    val productId: String,
    val type: MeetProductType,
    val entitlementKey: String,
    val isConsumable: Boolean = false
)

object PlayBillingCatalog {
    const val PRO_MONTHLY = "pro_monthly"
    const val PRO_YEARLY = "pro_yearly"
    const val WORKSHOP_MONTHLY = "workshop_monthly"
    const val PRO_LIFETIME = "pro_lifetime"
    const val GAUGE_PACK_ELITE = "gauge_pack_elite"
    const val REPORT_PACK = "report_pack"

    val products: List<MeetBillingProduct> =
        listOf(
            MeetBillingProduct(PRO_MONTHLY, MeetProductType.Subscription, "pro"),
            MeetBillingProduct(PRO_YEARLY, MeetProductType.Subscription, "pro"),
            MeetBillingProduct(WORKSHOP_MONTHLY, MeetProductType.Subscription, "workshop"),
            MeetBillingProduct(PRO_LIFETIME, MeetProductType.InApp, "pro_lifetime"),
            MeetBillingProduct(GAUGE_PACK_ELITE, MeetProductType.InApp, "gauge_pack_elite"),
            MeetBillingProduct(REPORT_PACK, MeetProductType.InApp, "report_pack", isConsumable = true)
        ) + (1..10).map { tier ->
            MeetBillingProduct(
                productId = "gauge_tier_$tier",
                type = MeetProductType.InApp,
                entitlementKey = "gauge_marketplace_purchase",
                isConsumable = true
            )
        }

    fun product(productId: String): MeetBillingProduct {
        return products.firstOrNull { it.productId == productId }
            ?: MeetBillingProduct(productId, MeetProductType.InApp, "unknown", isConsumable = false)
    }

    fun productType(productId: String): String = product(productId).type.playType

    fun isConsumable(productId: String): Boolean = product(productId).isConsumable
}

