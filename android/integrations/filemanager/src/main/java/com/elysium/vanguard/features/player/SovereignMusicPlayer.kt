package com.elysium.vanguard.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.elysium.vanguard.ui.theme.TitanColors
import com.elysium.vanguard.ui.theme.premiumGlass
import com.elysium.vanguard.ui.components.MatrixRain
import java.io.File
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun SovereignMusicPlayer(
    filePath: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(File(filePath).absolutePath)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var isShuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(0) } // 0: None, 1: All, 2: One
    var volume by remember { mutableFloatStateOf(1f) }

    val parentDir = remember { File(filePath).parentFile }
    val playlist = remember(parentDir) {
        parentDir?.listFiles()?.filter {
            val cat = com.elysium.vanguard.core.util.FileThematics.getCategory(it.name, it.isDirectory)
            cat == com.elysium.vanguard.core.util.FileCategory.AUDIO
        }?.sortedBy { it.name.lowercase() } ?: listOf(File(filePath))
    }
    var currentTrackIndex by remember { mutableIntStateOf(playlist.indexOfFirst { it.absolutePath == filePath }.coerceAtLeast(0)) }

    LaunchedEffect(currentTrackIndex) {
        val nextTrack = playlist[currentTrackIndex]
        exoPlayer.setMediaItem(MediaItem.fromUri(nextTrack.absolutePath))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            if (duration > 0) {
                progress = currentPosition.toFloat() / duration
            }
            isPlaying = exoPlayer.isPlaying
            kotlinx.coroutines.delay(500)
        }
    }

    LaunchedEffect(volume) {
        exoPlayer.volume = volume
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TitanColors.AbsoluteBlack),
        contentAlignment = Alignment.Center
    ) {
        MatrixRain(
            color = TitanColors.NeonRed,
            alpha = 0.25f,
            speed = 30L
        )

        // BACKGROUND GLOW
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(TitanColors.QuantumPink.copy(alpha = 0.05f), CircleShape)
                .align(Alignment.Center)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TOP BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.premiumGlass(cornerRadius = 12.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "NEURAL AUDIO",
                    color = TitanColors.NeonCyan,
                    style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // ALBUM ART (ANIMATED)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(240.dp)
            ) {
                val infiniteTransition = rememberInfiniteTransition()
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(10000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    )
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        style = Stroke(width = 1f)
                    )
                }

                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = TitanColors.QuantumPink,
                    modifier = Modifier
                        .size(120.dp)
                        .padding(16.dp)
                )

                // Pulsing rings
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .background(TitanColors.QuantumPink.copy(alpha = 0.05f * (1f - (pulseScale - 0.8f))), CircleShape)
                        .blur(20.dp)
                )
            }

            Spacer(modifier = Modifier.weight(0.2f))

            // VOLUME CONTROL
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.VolumeDown, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = TitanColors.NeonCyan,
                        activeTrackColor = TitanColors.NeonCyan,
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    )
                )
                Icon(Icons.Default.VolumeUp, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
            }

            Text(
                text = playlist[currentTrackIndex].nameWithoutExtension,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Text(
                text = "NEURAL STREAM • ${currentTrackIndex + 1}/${playlist.size}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // PROGRESS SLIDER
            Slider(
                value = progress,
                onValueChange = {
                    progress = it
                    exoPlayer.seekTo((it * duration).toLong())
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = TitanColors.NeonCyan,
                    activeTrackColor = TitanColors.NeonCyan,
                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPosition),
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = formatTime(duration),
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // CONTROLS
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                IconButton(onClick = { isShuffle = !isShuffle }) {
                    Icon(
                        Icons.Default.Shuffle,
                        null,
                        tint = if (isShuffle) TitanColors.NeonCyan else Color.White.copy(alpha = 0.3f)
                    )
                }

                IconButton(onClick = {
                    if (currentTrackIndex > 0) currentTrackIndex--
                    else currentTrackIndex = playlist.size - 1
                }) {
                    Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }

                IconButton(
                    onClick = {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        isPlaying = !isPlaying
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .premiumGlass(cornerRadius = 40.dp)
                        .border(1.dp, TitanColors.NeonCyan.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = TitanColors.NeonCyan,
                        modifier = Modifier.size(48.dp)
                    )
                }

                IconButton(onClick = {
                    if (currentTrackIndex < playlist.size - 1) currentTrackIndex++
                    else currentTrackIndex = 0
                }) {
                    Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }

                IconButton(onClick = { repeatMode = (repeatMode + 1) % 3 }) {
                    Icon(
                        imageVector = when(repeatMode) {
                            2 -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        null,
                        tint = if (repeatMode > 0) TitanColors.NeonCyan else Color.White.copy(alpha = 0.3f)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.4f))
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
