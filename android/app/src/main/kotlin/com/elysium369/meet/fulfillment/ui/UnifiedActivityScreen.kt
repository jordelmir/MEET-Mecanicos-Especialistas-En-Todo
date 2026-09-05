package com.elysium369.meet.fulfillment.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.services.kernel.ServiceVertical
import com.elysium369.meet.core.services.tow.TowCommandRepository
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import java.text.SimpleDateFormat
import java.util.*

enum class ActivityFilter(val label: String) {
    ALL("Todos"),
    RIDES("Viajes"),
    TOW("Grúas"),
    SERVICES("Servicios"),
}

data class UnifiedActivityItem(
    val id: String,
    val vertical: ServiceVertical,
    val title: String,
    val subtitle: String,
    val status: String,
    val isActive: Boolean,
    val priceFormatted: String?,
    val timestampEpochMs: Long,
)

@Composable
fun UnifiedActivityScreen(
    viewModel: ObdViewModel,
    towRepository: TowCommandRepository,
    onNavigateToRide: (String) -> Unit = {},
    onNavigateToTow: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    var selectedFilter by remember { mutableStateOf(ActivityFilter.ALL) }

    val rideRequests by viewModel.rideRequests.collectAsState(initial = emptyList())
    val allTowJobs by towRepository.observeAllJobs().collectAsState(initial = emptyList())

    val activityItems = remember(rideRequests, allTowJobs, selectedFilter) {
        val list = mutableListOf<UnifiedActivityItem>()

        // 1. Tow items
        allTowJobs.forEach { tow ->
            list.add(
                UnifiedActivityItem(
                    id = tow.jobId.toString(),
                    vertical = ServiceVertical.TOW,
                    title = "Grúa: ${tow.vehicleSummary}",
                    subtitle = "Hacia ${tow.destinationAddress ?: "Destino"}",
                    status = tow.state.displayName,
                    isActive = tow.state.isActive,
                    priceFormatted = tow.finalSettlement?.formatted() ?: tow.quotedPrice?.formatted() ?: tow.estimatedPrice?.formatted(),
                    timestampEpochMs = tow.createdAtEpochMs
                )
            )
        }

        // 2. Ride items
        rideRequests.forEach { ride ->
            list.add(
                UnifiedActivityItem(
                    id = ride.requestId,
                    vertical = ServiceVertical.RIDE,
                    title = "Viaje a ${ride.destAddress}",
                    subtitle = "Recogida: ${ride.pickupAddress}",
                    status = ride.status,
                    isActive = ride.status in setOf("OPEN", "ACCEPTED", "IN_PROGRESS", "DRIVER_EN_ROUTE"),
                    priceFormatted = if (ride.priceOfferMinor > 0) "${ride.currency} ${ride.priceOfferMinor}" else null,
                    timestampEpochMs = ride.createdAt
                )
            )
        }

        // Filter
        val filtered = when (selectedFilter) {
            ActivityFilter.ALL -> list
            ActivityFilter.RIDES -> list.filter { it.vertical == ServiceVertical.RIDE }
            ActivityFilter.TOW -> list.filter { it.vertical == ServiceVertical.TOW }
            ActivityFilter.SERVICES -> list.filter { it.vertical !in setOf(ServiceVertical.RIDE, ServiceVertical.TOW) }
        }

        filtered.sortedByDescending { it.timestampEpochMs }
    }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = MeetColors.textPrimary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Actividad Unificada",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.textPrimary
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Filter chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActivityFilter.values().forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MeetColors.neonGreen,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        if (activityItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MeetColors.textSecondary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No hay actividad registrada",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MeetColors.textSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(activityItems, key = { it.id }) { item ->
                    ActivityItemCard(
                        item = item,
                        onClick = {
                            when (item.vertical) {
                                ServiceVertical.RIDE -> onNavigateToRide(item.id)
                                ServiceVertical.TOW -> onNavigateToTow(item.id)
                                else -> Unit
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityItemCard(
    item: UnifiedActivityItem,
    onClick: () -> Unit,
) {
    val dateStr = remember(item.timestampEpochMs) {
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        sdf.format(Date(item.timestampEpochMs))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(
            width = if (item.isActive) 1.5.dp else 1.dp,
            color = if (item.isActive) MeetColors.neonGreen else MeetColors.borderSubtle
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        when (item.vertical) {
                            ServiceVertical.RIDE -> MeetColors.neonGreen.copy(alpha = 0.15f)
                            ServiceVertical.TOW -> MeetColors.electricBlue.copy(alpha = 0.15f)
                            else -> Color.Gray.copy(alpha = 0.15f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (item.vertical) {
                        ServiceVertical.RIDE -> Icons.Default.DirectionsCar
                        ServiceVertical.TOW -> Icons.Default.LocalShipping
                        else -> Icons.Default.Build
                    },
                    contentDescription = null,
                    tint = when (item.vertical) {
                        ServiceVertical.RIDE -> MeetColors.neonGreen
                        ServiceVertical.TOW -> MeetColors.electricBlue
                        else -> MeetColors.textPrimary
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.textPrimary
                    )
                    item.priceFormatted?.let {
                        Text(it, fontWeight = FontWeight.Bold, color = MeetColors.neonGreen, fontSize = 13.sp)
                    }
                }

                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MeetColors.textSecondary,
                    maxLines = 1
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        item.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.isActive) MeetColors.neonGreen else MeetColors.textSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MeetColors.textSecondary)
                }
            }
        }
    }
}
