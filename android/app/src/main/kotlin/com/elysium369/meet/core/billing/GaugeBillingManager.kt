package com.elysium369.meet.core.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.elysium369.meet.data.supabase.GaugePriceTiers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GaugeBilling"

/**
 * Wrapper over Google Play Billing Library v7 for gauge marketplace IAP.
 *
 * 10 consumable products: gauge_tier_1 ($0.99) → gauge_tier_10 ($9.99)
 *
 * Flow:
 * 1. Connect BillingClient
 * 2. Query product details for the specific tier
 * 3. Launch purchase flow
 * 4. On success → consume + callback to repository
 */
@Singleton
class GaugeBillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var billingClient: BillingClient? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Purchase result callback
    var onPurchaseCompleted: ((purchaseToken: String, productId: String) -> Unit)? = null
    var onPurchaseError: ((message: String) -> Unit)? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    scope.launch { handlePurchase(purchase) }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i(TAG, "User canceled purchase")
                _isProcessing.value = false
                onPurchaseError?.invoke("Compra cancelada")
            }
            else -> {
                Log.e(TAG, "Purchase error: ${billingResult.debugMessage}")
                _isProcessing.value = false
                onPurchaseError?.invoke("Error: ${billingResult.debugMessage}")
            }
        }
    }

    /** Connect to Google Play Billing */
    fun connect() {
        if (billingClient?.isReady == true) {
            _isConnected.value = true
            return
        }

        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "Billing client connected")
                    _isConnected.value = true
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    _isConnected.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                _isConnected.value = false
            }
        })
    }

    /** Disconnect billing client */
    fun disconnect() {
        billingClient?.endConnection()
        _isConnected.value = false
    }

    /**
     * Launch the purchase flow for a specific price tier.
     * @param activity The current activity for the purchase dialog
     * @param priceTier 1-10 corresponding to $0.99-$9.99
     */
    suspend fun launchPurchaseFlow(activity: Activity, priceTier: Int) {
        val client = billingClient
        if (client == null || !client.isReady) {
            onPurchaseError?.invoke("Billing client not ready")
            return
        }

        _isProcessing.value = true
        val productId = GaugePriceTiers.productId(priceTier)

        // Query product details
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val (billingResult, productDetailsList) = withContext(Dispatchers.IO) {
            client.queryProductDetails(params).let { it.billingResult to it.productDetailsList }
        }

        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK || productDetailsList.isNullOrEmpty()) {
            Log.e(TAG, "Failed to query product details for $productId: ${billingResult.debugMessage}")
            _isProcessing.value = false
            onPurchaseError?.invoke("Producto no disponible: $productId")
            return
        }

        val productDetails = productDetailsList[0]

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                )
            )
            .build()

        withContext(Dispatchers.Main) {
            val launchResult = client.launchBillingFlow(activity, flowParams)
            if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e(TAG, "Failed to launch billing flow: ${launchResult.debugMessage}")
                _isProcessing.value = false
                onPurchaseError?.invoke("Error al iniciar compra")
            }
        }
    }

    /** Handle a completed purchase: acknowledge, consume, and notify */
    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            Log.w(TAG, "Purchase not in PURCHASED state: ${purchase.purchaseState}")
            _isProcessing.value = false
            return
        }

        // Acknowledge if needed
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            val ackResult = withContext(Dispatchers.IO) {
                billingClient?.acknowledgePurchase(ackParams)
            }

            if (ackResult?.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e(TAG, "Failed to acknowledge purchase: ${ackResult?.debugMessage}")
                _isProcessing.value = false
                onPurchaseError?.invoke("Error al confirmar compra")
                return
            }
        }

        // Consume the purchase (so it can be bought again by others)
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        val consumeResult = withContext(Dispatchers.IO) {
            billingClient?.consumePurchase(consumeParams)
        }

        if (consumeResult?.billingResult?.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.i(TAG, "Purchase consumed successfully")
            val productId = purchase.products.firstOrNull() ?: ""
            withContext(Dispatchers.Main) {
                onPurchaseCompleted?.invoke(purchase.purchaseToken, productId)
            }
        } else {
            Log.e(TAG, "Failed to consume purchase: ${consumeResult?.billingResult?.debugMessage}")
            onPurchaseError?.invoke("Error al procesar compra")
        }

        _isProcessing.value = false
    }
}
