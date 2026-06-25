package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_gauges")
data class SavedGaugeEntity(
    @PrimaryKey val id: String,          // UUID
    val name: String,
    val bgType: Int,
    val bgPresetIndex: Int,
    val bgImageUri: String,
    val bezelStyle: Int,
    val needleStyle: Int,
    val ticksStyle: Int,
    val accentColor: Int,                // ARGB
    val accentColor2: Int,               // ARGB
    val glowIntensity: Float,
    val imageOpacity: Float,
    val animationIndex: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val isPublished: Boolean = false,    // true si fue compartido al marketplace
    val marketplaceId: String? = null,   // ID en Supabase si fue publicado
    val thumbnailPath: String? = null,   // Path local al screenshot del gauge
    val typographyIndex: Int = 0
)
