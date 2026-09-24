package com.elysium369.meet.safety.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Description
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.elysium369.meet.safety.domain.SafetyReportCategory

data class SafetyCategoryVisual(
    val icon: ImageVector,
    val color: Color,
    val emoji: String,
    val labelEs: String,
)

object SafetyCategoryIcons {

    private val visuals = mapOf(
        SafetyReportCategory.HOMICIDE to SafetyCategoryVisual(
            icon = Icons.Filled.Dangerous,
            color = Color(0xFFFF4444),
            emoji = "☠️",
            labelEs = "Homicidio",
        ),
        SafetyReportCategory.VIOLENT_INCIDENT to SafetyCategoryVisual(
            icon = Icons.Filled.ElectricBolt,
            color = Color(0xFFFF8C00),
            emoji = "⚡",
            labelEs = "Violencia",
        ),
        SafetyReportCategory.DRUG_SALE_ACTIVITY to SafetyCategoryVisual(
            icon = Icons.Filled.LocalPharmacy,
            color = Color(0xFF9C27B0),
            emoji = "💊",
            labelEs = "Narcotráfico",
        ),
        SafetyReportCategory.THREAT to SafetyCategoryVisual(
            icon = Icons.Filled.Warning,
            color = Color(0xFFFFD600),
            emoji = "⚠️",
            labelEs = "Amenaza",
        ),
        SafetyReportCategory.MISSING_PERSON to SafetyCategoryVisual(
            icon = Icons.Filled.PersonSearch,
            color = Color(0xFF2196F3),
            emoji = "🔍",
            labelEs = "Desaparecido",
        ),
        SafetyReportCategory.INSTITUTIONAL_CONDUCT to SafetyCategoryVisual(
            icon = Icons.Filled.AccountBalance,
            color = Color(0xFF4CAF50),
            emoji = "🏛️",
            labelEs = "Institucional",
        ),
        SafetyReportCategory.OTHER to SafetyCategoryVisual(
            icon = Icons.Filled.Description,
            color = Color(0xFF00BCD4),
            emoji = "📋",
            labelEs = "Otro",
        ),
    )

    fun of(category: SafetyReportCategory): SafetyCategoryVisual =
        visuals[category] ?: visuals[SafetyReportCategory.OTHER]!!

    fun colorForString(category: String): Color = runCatching {
        of(SafetyReportCategory.valueOf(category)).color
    }.getOrDefault(Color(0xFF00BCD4))

    fun iconForString(category: String): ImageVector = runCatching {
        of(SafetyReportCategory.valueOf(category)).icon
    }.getOrDefault(Icons.Filled.Description)

    fun emojiForString(category: String): String = runCatching {
        of(SafetyReportCategory.valueOf(category)).emoji
    }.getOrDefault("📋")
}
