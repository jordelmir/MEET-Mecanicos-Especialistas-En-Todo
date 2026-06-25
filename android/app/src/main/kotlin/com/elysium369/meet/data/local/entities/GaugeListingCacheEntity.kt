package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache entity for marketplace gauge listings.
 * Avoids repeated Supabase fetches for the browsing experience.
 */
@Entity(tableName = "gauge_listing_cache")
data class GaugeListingCacheEntity(
    @PrimaryKey val id: String,              // Supabase listing UUID
    val creatorId: String,
    val creatorName: String,
    val name: String,
    val description: String?,
    val configJson: String,                   // Serialized GaugeConfig JSON
    val thumbnailUrl: String?,
    val priceTier: Int,                       // 1-10 ($0.99 - $9.99)
    val totalSales: Int,
    val avgRating: Float,
    val reviewCount: Int,
    val isActive: Boolean,
    val cachedAt: Long                        // Timestamp for cache invalidation
)
