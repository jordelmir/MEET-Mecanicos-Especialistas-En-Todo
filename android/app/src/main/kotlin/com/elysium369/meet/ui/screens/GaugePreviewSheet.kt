package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.components.AnimatedNeonGlyph

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elysium369.meet.data.local.entities.GaugeConfig
import com.elysium369.meet.data.local.entities.SavedGaugeEntity
import com.elysium369.meet.data.supabase.GaugeListing
import com.elysium369.meet.data.supabase.GaugePriceTiers
import com.elysium369.meet.data.supabase.GaugeReview
import com.elysium369.meet.ui.components.gauges.*
import kotlinx.serialization.json.Json

// ═══════════════════════════════════════════════════════
// GAUGE PREVIEW SHEET — Full-screen dialog with preview
// ═══════════════════════════════════════════════════════

@Composable
fun GaugePreviewSheet(
    listing: GaugeListing,
    isOwned: Boolean,
    isMonetizationUnlocked: Boolean = false,
    reviews: List<GaugeReview> = emptyList(),
    isLoadingReviews: Boolean = false,
    isPurchaseInProgress: Boolean = false,
    onDismiss: () -> Unit,
    onBuy: (GaugeListing) -> Unit,
    onApply: (GaugeConfig) -> Unit
) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    val config = remember(listing.config_json) {
        try { json.decodeFromString<GaugeConfig>(listing.config_json) } catch (e: Exception) { null }
    }
    val previewGauge = remember(config, listing.name, listing.id) {
        config?.toPreviewGauge(listing.id ?: "preview", listing.name)
    }
    val accentCyan = Color(0xFF00FFCC)
    val accentPurple = Color(0xFF7C4DFF)

    val inf = rememberInfiniteTransition(label = "preview")
    val glowPulse by inf.animateFloat(
        0.3f, 0.8f,
        infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowPulse"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0D1117),
                            Color(0xFF0A0E1A),
                            Color(0xFF060810)
                        )
                    )
                )
                .border(
                    1.5.dp,
                    Brush.verticalGradient(
                        colors = listOf(
                            accentCyan.copy(alpha = glowPulse * 0.4f),
                            accentPurple.copy(alpha = glowPulse * 0.2f),
                            accentCyan.copy(alpha = glowPulse * 0.3f)
                        )
                    ),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // ── Close Button ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🔍 Preview",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        AnimatedNeonIcon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Live Gauge Preview ──
                if (config != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF080C14))
                            .border(1.dp, accentCyan.copy(alpha = 0.15f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Render the gauge with imported config
                        // For preview, we temporarily apply and render
                        val accentColor = Color(config.accentColor)
                        Gauge3DWrapper(
                            glowColor = previewGauge?.let { Color(it.accentColor) } ?: accentColor,
                            style = GaugeStyleSet.CUSTOM_DIY,
                            modifier = Modifier.size(230.dp)
                        ) {
                            GaugeDiyWidget(
                                label = config.name.ifEmpty { listing.name },
                                value = 72f,
                                minVal = 0f,
                                maxVal = 100f,
                                unit = "%",
                                warningThreshold = 70f,
                                criticalThreshold = 90f,
                                diyConfig = previewGauge,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // "GRATIS" badge over preview
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF00E676).copy(alpha = 0.2f))
                                .border(0.5.dp, Color(0xFF00E676).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "👁️ PREVIEW GRATIS",
                                color = Color(0xFF00E676),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                } else {
                    // Fallback if config fails to parse
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF080C14)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚠️ Config inválida", color = Color.White.copy(alpha = 0.5f))
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Gauge Info ──
                Text(
                    listing.name,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "por ${listing.creator_name}",
                    color = accentCyan.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                if (!listing.description.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        listing.description,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── Stats Row ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x0DFFFFFF))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("⭐", "%.1f".format(listing.avg_rating), "Rating")
                    StatItem("🛒", "${listing.total_sales}", "Ventas")
                    StatItem("💬", "${listing.review_count}", "Reviews")
                    StatItem("👁️", "${listing.download_count}", "Previews")
                }

                Spacer(Modifier.height(20.dp))

                // ── Price & Action Buttons ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    accentCyan.copy(alpha = 0.08f),
                                    accentPurple.copy(alpha = 0.06f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(accentCyan.copy(alpha = 0.2f), accentPurple.copy(alpha = 0.15f))
                            ),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isMonetizationUnlocked) "Acceso" else "Precio",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                            Text(
                                if (isMonetizationUnlocked) "LIBERADO" else GaugePriceTiers.displayPrice(listing.price_tier),
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        if (isOwned) {
                            // APPLY button (already purchased)
                            Button(
                                onClick = { config?.let { onApply(it) } },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E676)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    if (isMonetizationUnlocked) "✅ APLICAR SIN COBRO" else "✅ APLICAR A MI GAUGE",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                            }
                        } else {
                            // BUY button
                            Button(
                                onClick = { onBuy(listing) },
                                enabled = !isPurchaseInProgress,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentCyan
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                if (isPurchaseInProgress) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.Black
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "PROCESANDO",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp
                                    )
                                } else {
                                    Text(
                                        "🛒 COMPRAR ${GaugePriceTiers.displayPrice(listing.price_tier)}",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Reviews Section ──
                Text(
                    "💬 Reviews",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(8.dp))

                if (isLoadingReviews) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = accentCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else if (reviews.isEmpty()) {
                    Text(
                        "Aún no hay reviews",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        reviews.take(6).forEach { review ->
                            ReviewCard(review = review)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StatItem(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedNeonGlyph(icon, contentDescription = null, fontSize = 16.sp)
        Text(
            value,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            label,
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ReviewCard(review: GaugeReview) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Usuario verificado",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = buildString {
                    repeat(review.rating.coerceIn(0, 5)) { append("★") }
                    repeat((5 - review.rating).coerceAtLeast(0)) { append("☆") }
                },
                color = Color(0xFFFFD600),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (!review.comment.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = review.comment,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

private fun GaugeConfig.toPreviewGauge(id: String, fallbackName: String): SavedGaugeEntity {
    return SavedGaugeEntity(
        id = id,
        name = name.ifBlank { fallbackName },
        bgType = bgType,
        bgPresetIndex = bgPresetIndex,
        bgImageUri = bgImageUri,
        bezelStyle = bezelStyle,
        needleStyle = needleStyle,
        ticksStyle = ticksStyle,
        accentColor = accentColor,
        accentColor2 = accentColor2,
        glowIntensity = glowIntensity,
        imageOpacity = imageOpacity,
        animationIndex = animationIndex,
        createdAt = 0L,
        updatedAt = 0L,
        typographyIndex = typographyIndex
    )
}
