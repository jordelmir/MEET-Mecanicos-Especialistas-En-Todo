package com.elysium.vanguard.recordshield.ui.screen.player

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.elysium.vanguard.recordshield.domain.model.EvidenceChunk
import com.elysium.vanguard.recordshield.ui.theme.*
import java.io.File

/**
 * ============================================================================
 * PlayerScreen — Evidence Chunk Playback
 * ============================================================================
 *
 * Design: Full-screen Media3 player with RESIZE_MODE_FILL (stretches
 * to fill every pixel as required). Below the player, a horizontal
 * scrollable timeline shows all chunks with their upload status.
 *
 * Why Media3 over raw MediaPlayer:
 *   - Media3 handles codec selection, buffering, and surface management
 *   - PlayerView provides built-in transport controls
 *   - Seamless playlist support for sequential chunk playback
 * ============================================================================
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    recordingId: String,
    chunks: List<EvidenceChunk>,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    // Build playlist from local chunk files
    val mediaItems = remember(chunks) {
        chunks
            .filter { File(it.localPath).exists() }
            .sortedBy { it.chunkIndex }
            .map { chunk ->
                MediaItem.fromUri(Uri.fromFile(File(chunk.localPath)))
            }
    }

    // ExoPlayer instance
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // Set media items when they change
    LaunchedEffect(mediaItems) {
        player.setMediaItems(mediaItems)
        player.prepare()
    }

    // Release player on dispose
    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        // Top Bar
        PlayerTopBar(
            chunkCount = chunks.size,
            onBackClick = onBackClick
        )

        // Video Player — RESIZE_MODE_FILL as required
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(DeepBlack)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        // RESIZE_MODE_FILL: Stretches video to fill every pixel
                        // without maintaining aspect ratio — per requirements
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Chunk Timeline
        ChunkTimeline(
            chunks = chunks,
            onChunkClick = { index ->
                player.seekTo(index, 0L)
            }
        )

        // Recording Info Bar
        RecordingInfoBar(chunks = chunks)
    }
}

@Composable
fun PlayerTopBar(chunkCount: Int, onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
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

        Column {
            Text(
                text = "EVIDENCE PLAYBACK",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = 2.sp
            )
            Text(
                text = "$chunkCount fragments • sequential playback",
                style = MaterialTheme.typography.labelSmall,
                color = ElectricBlue
            )
        }
    }
}

@Composable
fun ChunkTimeline(
    chunks: List<EvidenceChunk>,
    onChunkClick: (Int) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(chunks.sortedBy { it.chunkIndex }) { index, chunk ->
            ChunkTimelineItem(
                chunk = chunk,
                index = index,
                onClick = { onChunkClick(index) }
            )
        }
    }
}

@Composable
fun ChunkTimelineItem(
    chunk: EvidenceChunk,
    index: Int,
    onClick: () -> Unit
) {
    val isUploaded = chunk.uploadStatus.name == "UPLOADED"
    val statusColor = when (chunk.uploadStatus.name) {
        "UPLOADED" -> SuccessGreen
        "UPLOADING" -> ElectricBlue
        "FAILED" -> RecordingRed
        else -> WarningAmber
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 56.dp, height = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardSurface)
            .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "#${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            // Status indicator dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(statusColor)
            )
        }
    }
}

@Composable
fun RecordingInfoBar(chunks: List<EvidenceChunk>) {
    val totalSize = chunks.sumOf { it.sizeBytes }
    val uploaded = chunks.count { it.uploadStatus.name == "UPLOADED" }
    val formatSize = when {
        totalSize < 1024 -> "${totalSize}B"
        totalSize < 1024 * 1024 -> "${totalSize / 1024}KB"
        else -> String.format("%.1fMB", totalSize / (1024.0 * 1024.0))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .background(CardSurface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            InfoItem(label = "CHUNKS", value = "${chunks.size}")
            InfoItem(label = "UPLOADED", value = "$uploaded/${chunks.size}")
            InfoItem(label = "SIZE", value = formatSize)
        }
    }
}

@Composable
fun InfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            letterSpacing = 1.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MatrixGreen
        )
    }
}
