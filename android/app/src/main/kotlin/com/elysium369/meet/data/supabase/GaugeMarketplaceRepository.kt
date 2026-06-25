package com.elysium369.meet.data.supabase

import android.content.Context
import android.util.Log
import com.elysium369.meet.data.local.entities.GaugeConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GaugeMarketRepo"

// ─── Supabase DTOs ───────────────────────────────────────

@Serializable
data class GaugeListing(
    val id: String? = null,
    val creator_id: String? = null,
    val creator_name: String = "",
    val name: String = "",
    val description: String? = null,
    val config_json: String = "{}",
    val thumbnail_url: String? = null,
    val price_tier: Int = 1,
    val total_sales: Int = 0,
    val total_revenue_cents: Int = 0,
    val creator_earnings_cents: Int = 0,
    val platform_earnings_cents: Int = 0,
    val avg_rating: Float = 0f,
    val review_count: Int = 0,
    val download_count: Int = 0,
    val is_active: Boolean = true,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class GaugePurchase(
    val id: String? = null,
    val listing_id: String = "",
    val buyer_id: String? = null,
    val purchase_token: String = "",
    val price_cents: Int = 0,
    val creator_share_cents: Int = 0,
    val platform_share_cents: Int = 0,
    val purchased_at: String? = null
)

@Serializable
data class GaugeReview(
    val id: String? = null,
    val listing_id: String = "",
    val reviewer_id: String? = null,
    val rating: Int = 5,
    val comment: String? = null,
    val created_at: String? = null
)

// ─── Price Tiers ─────────────────────────────────────────

object GaugePriceTiers {
    /** Maps tier (1-10) to price in cents */
    val TIER_PRICES = mapOf(
        1 to 99, 2 to 199, 3 to 299, 4 to 399, 5 to 499,
        6 to 599, 7 to 699, 8 to 799, 9 to 899, 10 to 999
    )

    /** Google Play product ID for each tier */
    fun productId(tier: Int): String = "gauge_tier_${tier}"

    /** Display price string */
    fun displayPrice(tier: Int): String {
        val cents = TIER_PRICES[tier] ?: 99
        return "$${cents / 100}.${"%02d".format(cents % 100)}"
    }

    /**
     * Revenue split calculation.
     * Google takes ~15% (small developer program).
     * Of the net: 10% → platform (Jordelmir), 90% → creator.
     */
    fun calculateSplit(tier: Int): Triple<Int, Int, Int> {
        val gross = TIER_PRICES[tier] ?: 99
        val googleCut = (gross * 0.15).toInt()
        val net = gross - googleCut
        val platformShare = (net * 0.10).toInt()
        val creatorShare = net - platformShare
        return Triple(creatorShare, platformShare, googleCut)
    }

    /** Estimated creator earnings display */
    fun creatorEarningsDisplay(tier: Int): String {
        val (creatorCents, _, _) = calculateSplit(tier)
        return "$${creatorCents / 100}.${"%02d".format(creatorCents % 100)}"
    }
}

// ─── Repository ──────────────────────────────────────────

@Singleton
class GaugeMarketplaceRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    /** Current user ID from Supabase Auth */
    private fun currentUserId(): String? {
        return try {
            supabaseClient.auth.currentUserOrNull()?.id
        } catch (e: Exception) {
            Log.w(TAG, "No authenticated user: ${e.message}")
            null
        }
    }

    // ── PUBLISH ──

    /**
     * Publishes a gauge config to the marketplace.
     * Uploads thumbnail to Supabase Storage, creates listing row.
     */
    suspend fun publishGauge(
        config: GaugeConfig,
        name: String,
        description: String,
        priceTier: Int,
        thumbnailBytes: ByteArray?
    ): Result<String> = runCatching {
        val userId = currentUserId() ?: throw IllegalStateException("User not authenticated")
        val userName = supabaseClient.auth.currentUserOrNull()?.email?.substringBefore("@") ?: "Anonymous"

        // Upload thumbnail if provided
        var thumbnailUrl: String? = null
        if (thumbnailBytes != null) {
            val fileName = "gauges/$userId/${System.currentTimeMillis()}.png"
            try {
                supabaseClient.storage.from("gauge-thumbnails").upload(fileName, thumbnailBytes)
                thumbnailUrl = supabaseClient.storage.from("gauge-thumbnails").publicUrl(fileName)
            } catch (e: Exception) {
                Log.w(TAG, "Thumbnail upload failed: ${e.message}")
            }
        }

        val configJsonStr = json.encodeToString(config)

        val listing = GaugeListing(
            creator_id = userId,
            creator_name = userName,
            name = name,
            description = description,
            config_json = configJsonStr,
            thumbnail_url = thumbnailUrl,
            price_tier = priceTier.coerceIn(1, 10)
        )

        val result = supabaseClient.postgrest["gauge_listings"]
            .insert(listing) {
                select()
            }
            .decodeSingle<GaugeListing>()

        Log.i(TAG, "Published gauge: ${result.id}")
        result.id ?: throw IllegalStateException("No ID returned")
    }

    // ── BROWSE ──

    /** Fetch listings sorted by popularity (total_sales DESC) */
    suspend fun fetchPopularListings(limit: Int = 20, offset: Int = 0): List<GaugeListing> {
        return try {
            supabaseClient.postgrest["gauge_listings"]
                .select {
                    filter { eq("is_active", true) }
                    order("total_sales", Order.DESCENDING)
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }
                .decodeList<GaugeListing>()
        } catch (e: Exception) {
            Log.e(TAG, "fetchPopularListings failed: ${e.message}")
            emptyList()
        }
    }

    /** Fetch listings sorted by newest first */
    suspend fun fetchRecentListings(limit: Int = 20, offset: Int = 0): List<GaugeListing> {
        return try {
            supabaseClient.postgrest["gauge_listings"]
                .select {
                    filter { eq("is_active", true) }
                    order("created_at", Order.DESCENDING)
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }
                .decodeList<GaugeListing>()
        } catch (e: Exception) {
            Log.e(TAG, "fetchRecentListings failed: ${e.message}")
            emptyList()
        }
    }

    /** Fetch listings by the current user (their shop) */
    suspend fun fetchMyListings(): List<GaugeListing> {
        val userId = currentUserId() ?: return emptyList()
        return try {
            supabaseClient.postgrest["gauge_listings"]
                .select {
                    filter { eq("creator_id", userId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<GaugeListing>()
        } catch (e: Exception) {
            Log.e(TAG, "fetchMyListings failed: ${e.message}")
            emptyList()
        }
    }

    /** Fetch a single listing detail by ID */
    suspend fun getListingById(id: String): GaugeListing? {
        return try {
            supabaseClient.postgrest["gauge_listings"]
                .select { filter { eq("id", id) } }
                .decodeSingleOrNull<GaugeListing>()
        } catch (e: Exception) {
            Log.e(TAG, "getListingById failed: ${e.message}")
            null
        }
    }

    // ── PURCHASES ──

    /** Record a purchase after successful IAP */
    suspend fun recordPurchase(
        listingId: String,
        purchaseToken: String,
        priceTier: Int
    ): Result<Unit> = runCatching {
        val userId = currentUserId() ?: throw IllegalStateException("User not authenticated")
        val priceCents = GaugePriceTiers.TIER_PRICES[priceTier] ?: 99
        val (creatorShare, platformShare, _) = GaugePriceTiers.calculateSplit(priceTier)

        val purchase = GaugePurchase(
            listing_id = listingId,
            buyer_id = userId,
            purchase_token = purchaseToken,
            price_cents = priceCents,
            creator_share_cents = creatorShare,
            platform_share_cents = platformShare
        )

        supabaseClient.postgrest["gauge_purchases"].insert(purchase)

        // Update listing counters
        val listing = getListingById(listingId)
        if (listing != null) {
            supabaseClient.postgrest["gauge_listings"]
                .update({
                    set("total_sales", listing.total_sales + 1)
                    set("total_revenue_cents", listing.total_revenue_cents + priceCents)
                    set("creator_earnings_cents", listing.creator_earnings_cents + creatorShare)
                    set("platform_earnings_cents", listing.platform_earnings_cents + platformShare)
                }) {
                    filter { eq("id", listingId) }
                }
        }
        Log.i(TAG, "Purchase recorded for listing $listingId")
    }

    /** Check if current user has already purchased this listing */
    suspend fun checkOwnership(listingId: String): Boolean {
        val userId = currentUserId() ?: return false
        return try {
            val purchases = supabaseClient.postgrest["gauge_purchases"]
                .select {
                    filter {
                        eq("listing_id", listingId)
                        eq("buyer_id", userId)
                    }
                }
                .decodeList<GaugePurchase>()
            purchases.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "checkOwnership failed: ${e.message}")
            false
        }
    }

    /** Get all purchases by the current user */
    suspend fun fetchMyPurchases(): List<GaugePurchase> {
        val userId = currentUserId() ?: return emptyList()
        return try {
            supabaseClient.postgrest["gauge_purchases"]
                .select {
                    filter { eq("buyer_id", userId) }
                    order("purchased_at", Order.DESCENDING)
                }
                .decodeList<GaugePurchase>()
        } catch (e: Exception) {
            Log.e(TAG, "fetchMyPurchases failed: ${e.message}")
            emptyList()
        }
    }

    // ── REVIEWS ──

    /** Add a review for a listing */
    suspend fun addReview(listingId: String, rating: Int, comment: String?): Result<Unit> = runCatching {
        val userId = currentUserId() ?: throw IllegalStateException("User not authenticated")

        val review = GaugeReview(
            listing_id = listingId,
            reviewer_id = userId,
            rating = rating.coerceIn(1, 5),
            comment = comment
        )

        supabaseClient.postgrest["gauge_reviews"].insert(review)

        // Update listing avg_rating and review_count
        val reviews = getReviews(listingId)
        val avgRating = if (reviews.isNotEmpty()) reviews.map { it.rating }.average().toFloat() else 0f
        supabaseClient.postgrest["gauge_listings"]
            .update({
                set("avg_rating", avgRating)
                set("review_count", reviews.size)
            }) {
                filter { eq("id", listingId) }
            }
    }

    /** Get reviews for a listing */
    suspend fun getReviews(listingId: String): List<GaugeReview> {
        return try {
            supabaseClient.postgrest["gauge_reviews"]
                .select {
                    filter { eq("listing_id", listingId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<GaugeReview>()
        } catch (e: Exception) {
            Log.e(TAG, "getReviews failed: ${e.message}")
            emptyList()
        }
    }

    // ── EARNINGS ──

    /** Total earnings for the current creator */
    suspend fun getCreatorEarnings(): Int {
        val userId = currentUserId() ?: return 0
        return try {
            val listings = supabaseClient.postgrest["gauge_listings"]
                .select {
                    filter { eq("creator_id", userId) }
                }
                .decodeList<GaugeListing>()
            listings.sumOf { it.creator_earnings_cents }
        } catch (e: Exception) {
            Log.e(TAG, "getCreatorEarnings failed: ${e.message}")
            0
        }
    }

    /** Parse a GaugeConfig from JSON string */
    fun parseConfig(configJson: String): GaugeConfig? {
        return try {
            json.decodeFromString<GaugeConfig>(configJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse config: ${e.message}")
            null
        }
    }
}
