package com.elysium369.meet.ui.home.activity

import java.util.UUID
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

/**
 * HomeActivityStrip — Global cross-domain activity dashboard.
 * Shows what's happening across ALL domains in one unified strip.
 *
 * Laws:
 * - Shows only ACTIVE operations (no history in the strip)
 * - Priority ordering: Emergency > Active Ride > Fuel > Communications > Properties
 * - Each item has a domain tag for filtering
 * - Maximum 10 items in the strip (older items drop off)
 */
enum class ActivityDomain {
    RIDE,
    FUEL,
    COMMUNICATIONS,
    PROPERTY,
    SAFE_JOURNEY,
    PTT,
    AI,
    MARKET,
    LEGAL,
    VEHICLE,
}

enum class ActivityPriority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    BACKGROUND,
}

data class ActivityItem(
    val activityId: String = UUID.randomUUID().toString(),
    val domain: ActivityDomain,
    val priority: ActivityPriority,
    val title: String,
    val subtitle: String?,
    val state: String,
    val progress: Float? = null,
    val actionable: Boolean = false,
    val actionRoute: String? = null,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val domainMetadata: Map<String, String> = emptyMap(),
)

data class HomeActivityStrip(
    val items: List<ActivityItem>,
    val lastUpdatedEpochMs: Long,
    val activeDomains: Set<ActivityDomain>,
) {
    val hasActiveOperations: Boolean get() = items.isNotEmpty()
    val criticalCount: Int get() = items.count { it.priority == ActivityPriority.CRITICAL }
    val highCount: Int get() = items.count { it.priority == ActivityPriority.HIGH }

    fun itemsByDomain(domain: ActivityDomain): List<ActivityItem> {
        return items.filter { it.domain == domain }
    }

    fun hasDomain(domain: ActivityDomain): Boolean = domain in activeDomains
}

object HomeActivityStripPolicy {
    const val MAX_ITEMS = 10
    const val STALE_THRESHOLD_MS = 5 * 60 * 1000L

    fun buildFromState(
        activeRides: List<ActiveRideState>,
        fuelAlerts: List<FuelAlert>,
        activeJourneys: List<ActiveJourney>,
        activePttChannels: List<ActivePttChannel>,
        pendingMessages: Int,
        activeListings: Int,
        vehicleAlerts: List<VehicleAlert>,
    ): HomeActivityStrip {
        val items = mutableListOf<ActivityItem>()

        activeJourneys.filter { it.stateName == "EMERGENCY" }.forEach { journey ->
            items.add(ActivityItem(
                domain = ActivityDomain.SAFE_JOURNEY,
                priority = ActivityPriority.CRITICAL,
                title = "Journey Emergency",
                subtitle = journey.name,
                state = journey.stateName,
                actionRoute = "/safe-journey/${journey.journeyId}",
            ))
        }

        activeRides.filter { it.isActive }.forEach { ride ->
            items.add(ActivityItem(
                domain = ActivityDomain.RIDE,
                priority = ActivityPriority.HIGH,
                title = "Active Ride",
                subtitle = ride.vehicleName,
                state = ride.stateName,
                progress = ride.progress,
                actionRoute = "/ride/${ride.rideId}",
            ))
        }

        fuelAlerts.filter { it.isCritical }.forEach { alert ->
            items.add(ActivityItem(
                domain = ActivityDomain.FUEL,
                priority = ActivityPriority.HIGH,
                title = alert.title,
                subtitle = alert.message,
                state = alert.severity,
                actionRoute = "/fuel",
            ))
        }

        vehicleAlerts.filter { it.isCritical }.forEach { alert ->
            items.add(ActivityItem(
                domain = ActivityDomain.VEHICLE,
                priority = ActivityPriority.HIGH,
                title = alert.title,
                subtitle = alert.message,
                state = alert.severity,
                actionRoute = "/vehicle",
            ))
        }

        activePttChannels.filter { it.isLive }.forEach { channel ->
            items.add(ActivityItem(
                domain = ActivityDomain.PTT,
                priority = ActivityPriority.MEDIUM,
                title = "PTT Active",
                subtitle = channel.name,
                state = channel.stateName,
                actionRoute = "/ptt/${channel.channelId}",
            ))
        }

        if (pendingMessages > 0) {
            items.add(ActivityItem(
                domain = ActivityDomain.COMMUNICATIONS,
                priority = ActivityPriority.MEDIUM,
                title = "Pending Messages",
                subtitle = "$pendingMessages unread",
                state = "PENDING",
                actionable = true,
                actionRoute = "/messages",
            ))
        }

        activeJourneys.filter { it.isActive }.forEach { journey ->
            items.add(ActivityItem(
                domain = ActivityDomain.SAFE_JOURNEY,
                priority = ActivityPriority.MEDIUM,
                title = "Journey Active",
                subtitle = journey.name,
                state = journey.stateName,
                progress = journey.progress,
                actionRoute = "/safe-journey/${journey.journeyId}",
            ))
        }

        if (activeListings > 0) {
            items.add(ActivityItem(
                domain = ActivityDomain.PROPERTY,
                priority = ActivityPriority.LOW,
                title = "Active Listings",
                subtitle = "$activeListings properties",
                state = "ACTIVE",
                actionRoute = "/properties",
            ))
        }

        val sorted = items.sortedBy { it.priority.ordinal }.take(MAX_ITEMS)
        val activeDomains = sorted.map { it.domain }.toSet()

        return HomeActivityStrip(
            items = sorted,
            lastUpdatedEpochMs = System.currentTimeMillis(),
            activeDomains = activeDomains,
        )
    }
}

data class ActiveRideState(
    val rideId: UUID,
    val vehicleName: String,
    val stateName: String,
    val isActive: Boolean,
    val progress: Float? = null,
)

data class FuelAlert(
    val title: String,
    val message: String,
    val severity: String,
    val isCritical: Boolean,
)

data class ActiveJourney(
    val journeyId: String,
    val name: String,
    val stateName: String,
    val isActive: Boolean,
    val progress: Float? = null,
)

data class ActivePttChannel(
    val channelId: String,
    val name: String,
    val stateName: String,
    val isLive: Boolean,
)

data class VehicleAlert(
    val title: String,
    val message: String,
    val severity: String,
    val isCritical: Boolean,
)

@Composable
fun HomeActivityStripWidget(
    strip: HomeActivityStrip,
    onItemClick: (ActivityItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!strip.hasActiveOperations) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MeetColors.neonGreen)
                    )
                    Text(
                        "ACTIVIDADES EN VIVO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.neonGreen,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    "${strip.items.size} activas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeetColors.textSecondary
                )
            }

            strip.items.forEach { item ->
                val domainColor = when (item.domain) {
                    ActivityDomain.RIDE -> MeetColors.neonGreen
                    ActivityDomain.PTT -> MeetColors.electricBlue
                    ActivityDomain.SAFE_JOURNEY -> MeetColors.hotMagenta
                    else -> MeetColors.cyberCyan
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = domainColor.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, domainColor.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(item) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = domainColor.copy(alpha = 0.2f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        imageVector = when (item.domain) {
                                            ActivityDomain.RIDE -> Icons.Default.DirectionsCar
                                            ActivityDomain.PTT -> Icons.Default.Mic
                                            else -> Icons.Default.Security
                                        },
                                        contentDescription = null,
                                        tint = domainColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MeetColors.textPrimary
                                )
                                item.subtitle?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MeetColors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = domainColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = item.state,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = domainColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

