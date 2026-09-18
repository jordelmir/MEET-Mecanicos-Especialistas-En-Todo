package com.elysium369.meet.ride.domain

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

enum class RidePetType {
    NONE,
    DOG,
    CAT;

    val displayName: String
        get() = when (this) {
            NONE -> "Sin mascota"
            DOG -> "Perro"
            CAT -> "Gato"
        }

    val emoji: String
        get() = when (this) {
            NONE -> "🚫"
            DOG -> "🐶"
            CAT -> "🐱"
        }
}

data class RidePreferenceBadge(
    val icon: String,
    val label: String,
    val color: Color,
)

@Serializable
data class RidePassengerPreferences(
    val pet: RidePetType = RidePetType.NONE,
    val kidsCount: Int = 0,
    val fivePassengers: Boolean = false,
) {
    init {
        require(kidsCount in 0..4) { "El número de niños debe estar entre 0 y 4" }
    }

    val hasSpecialPreferences: Boolean
        get() = pet != RidePetType.NONE || kidsCount > 0 || fivePassengers

    fun toBadges(): List<RidePreferenceBadge> {
        val badges = mutableListOf<RidePreferenceBadge>()
        when (pet) {
            RidePetType.DOG -> badges.add(
                RidePreferenceBadge("🐶", "Mascota: Perro", Color(0xFFFFB74D))
            )
            RidePetType.CAT -> badges.add(
                RidePreferenceBadge("🐱", "Mascota: Gato", Color(0xFFFF8A65))
            )
            RidePetType.NONE -> {}
        }
        if (kidsCount > 0) {
            badges.add(
                RidePreferenceBadge("👶", if (kidsCount == 1) "1 Niño" else "$kidsCount Niños", Color(0xFF4FC3F7))
            )
        }
        if (fivePassengers) {
            badges.add(
                RidePreferenceBadge("👥", "5 Pasajeros (Espacioso)", Color(0xFF81C784))
            )
        }
        return badges
    }

    fun toJson(): String {
        return jsonConfig.encodeToString(this)
    }

    companion object {
        private val jsonConfig = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        fun fromJson(rawJson: String?): RidePassengerPreferences {
            if (rawJson.isNullOrBlank()) return RidePassengerPreferences()
            return runCatching {
                val element = jsonConfig.parseToJsonElement(rawJson)
                if (element is kotlinx.serialization.json.JsonObject && element.containsKey("preferences")) {
                    jsonConfig.decodeFromJsonElement(serializer(), element["preferences"]!!)
                } else {
                    jsonConfig.decodeFromString(serializer(), rawJson)
                }
            }.getOrDefault(RidePassengerPreferences())
        }
    }
}

/**
 * PassengerPreferencesSelector — Interactive card allowing passengers to choose
 * traveling with pets (dog/cat), traveling with kids, or requiring a 5-passenger vehicle.
 */
@Composable
fun PassengerPreferencesSelector(
    preferences: RidePassengerPreferences,
    onPreferencesChange: (RidePassengerPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, if (preferences.hasSpecialPreferences) MeetColors.neonGreen.copy(alpha = 0.6f) else MeetColors.borderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Pets,
                        contentDescription = null,
                        tint = if (preferences.hasSpecialPreferences) MeetColors.neonGreen else MeetColors.cyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Preferencias de tu viaje",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.textPrimary
                    )
                }

                if (preferences.hasSpecialPreferences) {
                    Surface(
                        color = MeetColors.neonGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "Configurado",
                            color = MeetColors.neonGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // ── Pet Selection Row
            Text(
                text = "¿Viajas con mascota?",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MeetColors.textSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RidePetType.values().forEach { petType ->
                    val isSelected = preferences.pet == petType
                    val animatedBorder by animateColorAsState(
                        targetValue = if (isSelected) MeetColors.neonGreen else MeetColors.borderSubtle,
                        label = "pet_border"
                    )
                    val animatedBg by animateColorAsState(
                        targetValue = if (isSelected) MeetColors.neonGreen.copy(alpha = 0.18f) else Color(0xFF142032),
                        label = "pet_bg"
                    )

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onPreferencesChange(
                                    preferences.copy(
                                        pet = if (isSelected && petType != RidePetType.NONE) RidePetType.NONE else petType
                                    )
                                )
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = animatedBg,
                        border = BorderStroke(1.5.dp, animatedBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = petType.emoji, fontSize = 16.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = petType.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MeetColors.textSecondary
                            )
                        }
                    }
                }
            }

            Divider(color = MeetColors.borderSubtle.copy(alpha = 0.4f), thickness = 0.5.dp)

            // ── Kids Counter Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "👶", fontSize = 16.sp)
                        Text(
                            text = "Niños a bordo",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MeetColors.textPrimary
                        )
                    }
                    Text(
                        text = "Avisa al conductor para manejo prudente y espacio",
                        fontSize = 11.sp,
                        color = MeetColors.textSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (preferences.kidsCount > 0) {
                                onPreferencesChange(preferences.copy(kidsCount = preferences.kidsCount - 1))
                            }
                        },
                        enabled = preferences.kidsCount > 0,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (preferences.kidsCount > 0) Color(0xFF1F334E) else Color(0xFF142032))
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Menos niños",
                            tint = if (preferences.kidsCount > 0) MeetColors.cyberCyan else MeetColors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "${preferences.kidsCount}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (preferences.kidsCount > 0) MeetColors.cyberCyan else MeetColors.textSecondary,
                        modifier = Modifier.widthIn(min = 20.dp)
                    )

                    IconButton(
                        onClick = {
                            if (preferences.kidsCount < 4) {
                                onPreferencesChange(preferences.copy(kidsCount = preferences.kidsCount + 1))
                            }
                        },
                        enabled = preferences.kidsCount < 4,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (preferences.kidsCount < 4) MeetColors.cyberCyan.copy(alpha = 0.25f) else Color(0xFF142032))
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Más niños",
                            tint = if (preferences.kidsCount < 4) MeetColors.cyberCyan else MeetColors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Divider(color = MeetColors.borderSubtle.copy(alpha = 0.4f), thickness = 0.5.dp)

            // ── 5 Passengers Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        onPreferencesChange(preferences.copy(fivePassengers = !preferences.fivePassengers))
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "👥", fontSize = 16.sp)
                        Text(
                            text = "5 Pasajeros (Vehículo amplio)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MeetColors.textPrimary
                        )
                    }
                    Text(
                        text = "Requiere vehículo espacioso de alta capacidad",
                        fontSize = 11.sp,
                        color = MeetColors.textSecondary
                    )
                }

                Switch(
                    checked = preferences.fivePassengers,
                    onCheckedChange = { isChecked ->
                        onPreferencesChange(preferences.copy(fivePassengers = isChecked))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = MeetColors.neonGreen,
                        uncheckedThumbColor = MeetColors.textSecondary,
                        uncheckedTrackColor = Color(0xFF142032)
                    )
                )
            }
        }
    }
}
