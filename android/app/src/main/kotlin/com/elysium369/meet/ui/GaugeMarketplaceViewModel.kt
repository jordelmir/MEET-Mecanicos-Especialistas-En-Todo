package com.elysium369.meet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.data.local.entities.GaugeConfig
import com.elysium369.meet.data.supabase.GaugeListing
import com.elysium369.meet.data.supabase.GaugeMarketplaceRepository
import com.elysium369.meet.data.supabase.GaugeReview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GaugeMarketTab(val label: String, val icon: String) {
    POPULAR("🔥 Populares", "🔥"),
    RECENT("⏰ Recientes", "⏰"),
    MY_SALES("💰 Mis Ventas", "💰"),
    MY_PURCHASES("📦 Mis Compras", "📦")
}

data class GaugeMarketplaceUiState(
    val selectedTab: GaugeMarketTab = GaugeMarketTab.POPULAR,
    val listings: List<GaugeListing> = emptyList(),
    val isLoading: Boolean = true,
    val isPublishing: Boolean = false,
    val publishingSourceGaugeId: String? = null,
    val purchaseRecordingListingId: String? = null,
    val creatorEarningsCents: Int = 0,
    val ownershipByListingId: Map<String, Boolean> = emptyMap(),
    val reviewsByListingId: Map<String, List<GaugeReview>> = emptyMap(),
    val errorMessage: String? = null
)

@HiltViewModel
class GaugeMarketplaceViewModel @Inject constructor(
    private val repository: GaugeMarketplaceRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(GaugeMarketplaceUiState())
    val uiState: StateFlow<GaugeMarketplaceUiState> = _uiState.asStateFlow()
    private val inFlightPurchaseKeys = mutableSetOf<String>()

    init {
        refresh()
    }

    fun selectTab(tab: GaugeMarketTab) {
        if (_uiState.value.selectedTab == tab) return
        _uiState.update { it.copy(selectedTab = tab) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val listings = when (_uiState.value.selectedTab) {
                    GaugeMarketTab.POPULAR -> repository.fetchPopularListings()
                    GaugeMarketTab.RECENT -> repository.fetchRecentListings()
                    GaugeMarketTab.MY_SALES -> repository.fetchMyListings()
                    GaugeMarketTab.MY_PURCHASES -> {
                        repository.fetchMyPurchases()
                            .mapNotNull { purchase -> repository.getListingById(purchase.listing_id) }
                            .distinctBy { it.id }
                    }
                }
                val earnings = repository.getCreatorEarnings()
                listings to earnings
            }.onSuccess { (listings, earnings) ->
                _uiState.update {
                    it.copy(
                        listings = listings,
                        creatorEarningsCents = earnings,
                        isLoading = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        listings = emptyList(),
                        isLoading = false,
                        errorMessage = error.message ?: "No se pudo cargar el marketplace."
                    )
                }
            }
        }
    }

    fun ensureOwnership(listingId: String?) {
        if (listingId.isNullOrBlank() || _uiState.value.ownershipByListingId.containsKey(listingId)) return
        viewModelScope.launch {
            val isOwned = repository.checkOwnership(listingId)
            _uiState.update {
                it.copy(ownershipByListingId = it.ownershipByListingId + (listingId to isOwned))
            }
        }
    }

    fun ensureReviews(listingId: String?) {
        if (listingId.isNullOrBlank() || _uiState.value.reviewsByListingId.containsKey(listingId)) return
        viewModelScope.launch {
            val reviews = repository.getReviews(listingId)
            _uiState.update {
                it.copy(reviewsByListingId = it.reviewsByListingId + (listingId to reviews))
            }
        }
    }

    fun recordPurchase(listing: GaugeListing, purchaseToken: String) {
        val listingId = listing.id?.takeIf { it.isNotBlank() } ?: return
        val normalizedToken = purchaseToken.trim()
        if (normalizedToken.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Google Play no devolvió token de compra.") }
            return
        }
        val purchaseKey = "$listingId:$normalizedToken"
        if (_uiState.value.ownershipByListingId[listingId] == true || !inFlightPurchaseKeys.add(purchaseKey)) {
            return
        }
        _uiState.update {
            it.copy(
                purchaseRecordingListingId = listingId,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            repository.recordPurchase(listingId, normalizedToken, listing.price_tier)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            ownershipByListingId = it.ownershipByListingId + (listingId to true),
                            purchaseRecordingListingId = null
                        )
                    }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            purchaseRecordingListingId = null,
                            errorMessage = error.message ?: "No se pudo registrar la compra."
                        )
                    }
                }
            inFlightPurchaseKeys.remove(purchaseKey)
        }
    }

    fun publishGauge(
        config: GaugeConfig,
        name: String,
        description: String,
        priceTier: Int,
        saleCategory: String,
        tags: String,
        publishedFromSavedGaugeId: String?,
        sellerTermsAccepted: Boolean,
        onResult: (Result<String>) -> Unit
    ) {
        if (_uiState.value.isPublishing) {
            onResult(Result.failure(IllegalStateException("Ya hay una publicación en proceso.")))
            return
        }
        val publishSourceKey = publishedFromSavedGaugeId?.takeIf { it.isNotBlank() }
            ?: "draft:${name.trim().ifBlank { config.name }.take(48)}"
        _uiState.update {
            it.copy(
                isPublishing = true,
                publishingSourceGaugeId = publishSourceKey,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            val result = repository.publishGauge(
                config = config.copy(name = name.trim().ifBlank { config.name }),
                name = name,
                description = description,
                priceTier = priceTier,
                thumbnailBytes = null,
                saleCategory = saleCategory,
                tags = tags,
                publishedFromSavedGaugeId = publishedFromSavedGaugeId,
                sellerTermsAccepted = sellerTermsAccepted
            )
            result
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            isPublishing = false,
                            publishingSourceGaugeId = null
                        )
                    }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isPublishing = false,
                            publishingSourceGaugeId = null,
                            errorMessage = error.message ?: "No se pudo publicar el gauge."
                        )
                    }
                }
            onResult(result)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
