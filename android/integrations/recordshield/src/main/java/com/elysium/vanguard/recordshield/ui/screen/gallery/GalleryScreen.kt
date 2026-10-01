package com.elysium.vanguard.recordshield.ui.screen.gallery

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.elysium.vanguard.recordshield.domain.model.Recording
import com.elysium.vanguard.recordshield.domain.model.RecordingStatus
import com.elysium.vanguard.recordshield.domain.model.RecordingType
import com.elysium.vanguard.recordshield.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * ============================================================================
 * GalleryScreen — Evidence Vault Browser (Responsive Grid)
 * ============================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    recordings: List<Recording>,
    onRecordingClick: (Recording) -> Unit,
    onBackClick: () -> Unit,
    onDeleteRecording: (Recording) -> Unit,
    onShareRecording: ((Recording) -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp > 600

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar
        GalleryTopBar(
            recordingCount = recordings.size,
            onBackClick = onBackClick
        )

        if (recordings.isEmpty()) {
            EmptyGalleryState()
        } else {
            // Responsive Grid: 1 column on outer phone screen, 2-3 on inner screen
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isLargeScreen) 2 else 1),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = recordings,
                    key = { it.id }
                ) { recording ->
                    RecordingCard(
                        recording = recording,
                        isLargeScreen = isLargeScreen,
                        onClick = { onRecordingClick(recording) },
                        onDelete = { onDeleteRecording(recording) },
                        onShare = onShareRecording?.let { share -> { share(recording) } }
                    )
                }
            }
        }
    }
}

@Composable
fun GalleryTopBar(recordingCount: Int, onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(GlassSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "EVIDENCE VAULT",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = 2.sp
            )
            Text(
                text = "$recordingCount recording${if (recordingCount != 1) "s" else ""} secured",
                style = MaterialTheme.typography.labelSmall,
                color = MatrixGreen,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun RecordingCard(
    recording: Recording,
    isLargeScreen: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onShare: (() -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val isVideo = recording.type == RecordingType.VIDEO
    val accentColor = if (isVideo) ElectricBlue else DeepPurple
    val typeIcon = if (isVideo) Icons.Default.Videocam else Icons.Default.Mic

    val statusColor = when (recording.status) {
        RecordingStatus.COMPLETED -> SuccessGreen
        RecordingStatus.RECORDING -> RecordingRed
        RecordingStatus.INTERRUPTED -> WarningAmber
        RecordingStatus.UPLOADING -> ElectricBlue
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()) }
    val duration = recording.endedAt?.let {
        val totalSec = (it - recording.startedAt) / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        "${min}m ${sec}s"
    } ?: "Recording..."

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        CardSurface,
                        CardSurface.copy(alpha = 0.8f)
                    )
                )
            )
            .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(if (configuration.screenWidthDp > 600) 20.dp else 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type icon with glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (isLargeScreen) 52.dp else 44.dp)
                    .drawBehind {
                        drawCircle(
                            color = accentColor.copy(alpha = 0.15f),
                            radius = size.minDimension / 2 + 8f
                        )
                    }
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.1f))
                    .border(1.dp, accentColor.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(if (isLargeScreen) 24.dp else 20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isVideo) "Video Recording" else "Audio Recording",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Status dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = dateFormat.format(Date(recording.startedAt)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Stats row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatChip(
                        icon = Icons.Default.Timer,
                        text = duration,
                        color = TextSecondary
                    )
                    StatChip(
                        icon = Icons.Default.Layers,
                        text = "${recording.totalChunks} chunks",
                        color = TextSecondary
                    )
                    StatChip(
                        icon = Icons.Default.Storage,
                        text = formatBytes(recording.totalSizeBytes),
                        color = TextSecondary
                    )
                }
            }

            // Actions row
            Row {
                // Share button
                if (onShare != null) {
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = ElectricBlue
                        )
                    }
                }
                // Delete button
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun EmptyGalleryState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "NO RECORDINGS YET",
                style = MaterialTheme.typography.titleMedium,
                color = TextTertiary,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Start recording to secure evidence",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        }
    }
}

// Helper
private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.1fGB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
