package com.elysium369.meet.data.local.entities

import kotlinx.serialization.Serializable

/**
 * Serializable data class representing a complete DIY gauge configuration.
 * Used for:
 * - Exporting gauge configs to JSON for marketplace uploads
 * - Importing gauge configs from marketplace purchases
 * - Sharing configs between devices
 *
 * Note: bgImageUri is NOT included because it's a local device path.
 */
@Serializable
data class GaugeConfig(
    val name: String,
    val bgType: Int,
    val bgPresetIndex: Int,
    val bezelStyle: Int,
    val needleStyle: Int,
    val ticksStyle: Int,
    val accentColor: Int,        // ARGB integer
    val accentColor2: Int,       // ARGB integer
    val glowIntensity: Float,
    val imageOpacity: Float,
    val animationIndex: Int,
    val typographyIndex: Int = 0,
    val bgImageUri: String = ""
)
