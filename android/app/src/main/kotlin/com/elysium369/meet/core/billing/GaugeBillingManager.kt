package com.elysium369.meet.core.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ConsumeResponseListener
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.elysium369.meet.data.supabase.GaugePriceTiers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ElysiumBilling"

@Singleton
class GaugeBillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var billingClient: BillingClient? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onPurchaseCompleted: ((purchaseToken: String, productId: String) -> Unit)? = null
    var onPurchaseError: ((message: String) -> Unit)? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.orEmpty().forEach { purchase ->
                    scope.launch { handlePurchase(purchase) }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _isProcessing.value = false
                onPurchaseError?.invoke("Compra cancelada")
            }
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                _isProcessing.value = false
                onPurchaseError?.invoke("Google Play Billing no está disponible en este dispositivo o cuenta.")
            }
            else -> {
                Log.e(TAG, "Purchase error: ${billingResult.responseCode} ${billingResult.debugMessage}")
                _isProcessing.value = false
                onPurchaseError?.invoke("Error de compra: ${billingResult.debugMessage}")
            }
        }
    }

    fun connect() {
        if (billingClient?.isReady == true) {
            _isConnected.value = true
            return
        }

        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                _isConnected.value = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                if (!_isConnected.value) {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                _isConnected.value = false
            }
        })
    }

    fun disconnect() {
        billingClient?.endConnection()
        billingClient = null
        _isConnected.value = false
    }

    suspend fun launchPurchaseFlow(activity: Activity, priceTier: Int) {
        val productId = GaugePriceTiers.productId(priceTier)
        launchProductPurchaseFlow(activity, productId, BillingClient.ProductType.INAPP)
    }

    suspend fun launchProductPurchaseFlow(
        activity: Activity,
        productId: String,
        productType: String = PlayBillingCatalog.productType(productId)
    ) {
        val client = billingClient
        if (client == null || !client.isReady) {
            emitError("Google Play Billing aún no está listo")
            return
        }

        setProcessing(true)

        val productDetails = queryProductDetails(client, productId, productType)
        if (productDetails == null) {
            setProcessing(false)
            emitError("Producto no disponible en Google Play: $productId")
            return
        }

        val detailsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        if (productType == BillingClient.ProductType.SUBS) {
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken.isNullOrBlank()) {
                setProcessing(false)
                emitError("La suscripción no tiene una oferta activa en Play Console.")
                return
            }
            detailsBuilder.setOfferToken(offerToken)
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(detailsBuilder.build()))
            .build()

        withContext(Dispatchers.Main) {
            val launchResult = client.launchBillingFlow(activity, flowParams)
            if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _isProcessing.value = false
                onPurchaseError?.invoke("No se pudo iniciar compra: ${launchResult.debugMessage}")
            }
        }
    }

    private suspend fun queryProductDetails(
        client: BillingClient,
        productId: String,
        productType: String
    ): ProductDetails? {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(productType)
                        .build()
                )
            )
            .build()

        return suspendCancellableCoroutine { continuation ->
            client.queryProductDetailsAsync(
                params,
                ProductDetailsResponseListener { billingResult, productDetailsResult ->
                    if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                        Log.e(TAG, "queryProductDetails failed: ${billingResult.debugMessage}")
                        if (continuation.isActive) continuation.resume(null)
                    } else if (continuation.isActive) {
                        continuation.resume(productDetailsResult.productDetailsList.firstOrNull())
                    }
                }
            )
        }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            setProcessing(false)
            return
        }

        val productId = purchase.products.firstOrNull().orEmpty()
        if (productId.isBlank()) {
            setProcessing(false)
            emitError("Google Play no devolvió productId para la compra.")
            return
        }

        val processed = if (PlayBillingCatalog.isConsumable(productId)) {
            consumePurchase(purchase)
        } else {
            acknowledgePurchase(purchase)
        }

        setProcessing(false)
        if (processed) {
            withContext(Dispatchers.Main) {
                onPurchaseCompleted?.invoke(purchase.purchaseToken, productId)
            }
        }
    }

    private suspend fun acknowledgePurchase(purchase: Purchase): Boolean {
        if (purchase.isAcknowledged) return true
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val result = suspendCancellableCoroutine { continuation ->
            billingClient?.acknowledgePurchase(
                params,
                AcknowledgePurchaseResponseListener { billingResult ->
                    if (continuation.isActive) continuation.resume(billingResult)
                }
            ) ?: continuation.resume(null)
        }

        return if (result?.responseCode == BillingClient.BillingResponseCode.OK) {
            true
        } else {
            emitError("No se pudo confirmar la compra: ${result?.debugMessage.orEmpty()}")
            false
        }
    }

    private suspend fun consumePurchase(purchase: Purchase): Boolean {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val result = suspendCancellableCoroutine { continuation ->
            billingClient?.consumeAsync(
                params,
                ConsumeResponseListener { billingResult, _ ->
                    if (continuation.isActive) continuation.resume(billingResult)
                }
            ) ?: continuation.resume(null)
        }

        return if (result?.responseCode == BillingClient.BillingResponseCode.OK) {
            true
        } else {
            emitError("No se pudo procesar la compra: ${result?.debugMessage.orEmpty()}")
            false
        }
    }

    private suspend fun setProcessing(value: Boolean) {
        withContext(Dispatchers.Main) {
            _isProcessing.value = value
        }
    }

    private suspend fun emitError(message: String) {
        withContext(Dispatchers.Main) {
            onPurchaseError?.invoke(message)
        }
    }
}
