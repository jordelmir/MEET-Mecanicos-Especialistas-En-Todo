package com.elysium369.meet.ride.wallet

import com.elysium369.meet.ride.domain.RideMoney

data class RideFundingProduct(
    val productId: String,
    val displayName: String,
    val creditAmount: RideMoney,
    val storePrice: String,
)

data class RideFundingPurchaseRequest(
    val driverId: String,
    val productId: String,
    val idempotencyKey: String,
)

sealed interface RideFundingResult {
    data class PendingVerification(
        val productId: String,
        val purchaseToken: String,
    ) : RideFundingResult

    data class Confirmed(
        val ledgerEntryId: String,
    ) : RideFundingResult

    data class Unavailable(
        val reason: String,
    ) : RideFundingResult
}

interface WalletFundingProvider {
    suspend fun catalog(): List<RideFundingProduct>
    suspend fun launchPurchase(request: RideFundingPurchaseRequest): RideFundingResult
    suspend fun confirmPurchase(purchaseToken: String): RideFundingResult
    suspend fun restore(driverId: String): List<RideFundingResult>
}
