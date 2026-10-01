@file:SuppressLint("UnsafeOptInUsageError")

package com.elysium.vanguard.features.player

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.media3.common.Player
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.vanguard.ui.theme.TitanColors
import com.elysium.vanguard.ui.theme.neonGlass
import com.elysium.vanguard.ui.theme.pulsingNeonBorder
import com.elysium.vanguard.ui.components.NeonGlowIcon
import com.elysium.vanguard.ui.components.MatrixRain
import com.elysium.vanguard.ui.components.SovereignCard
import com.elysium.vanguard.ui.components.SovereignLifeWrapper
import com.elysium.vanguard.ui.components.AnimatedEmptyState
import com.elysium.vanguard.ui.components.EqualizerBars
import com.elysium.vanguard.ui.components.GlassPillBadge
import com.elysium.vanguard.ui.theme.SectionColorManager
import com.elysium.vanguard.ui.components.ColorCustomizerIcon
import com.elysium.vanguard.ui.components.ColorSelectionDialog
import com.elysium.vanguard.ui.components.TitanHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicHubScreen(
    viewModel: MusicHubViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showColorDialog by remember { mutableStateOf(false) }
    val accentColor = SectionColorManager.musicAccent

    val pagerState = rememberPagerState(pageCount = { 5 })
    val tabs = listOf("RECENTS", "PLAYER", "SONGS", "PLAYLISTS", "FAVORITES")
    val tabIcons = listOf(Icons.Default.History, Icons.Default.QueueMusic, Icons.Default.MusicNote, Icons.Default.PlaylistPlay, Icons.Default.Favorite)
    val tabColors = listOf(accentColor, accentColor, accentColor, accentColor, accentColor)

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var trackToAddByPlaylist by remember { mutableStateOf<MusicTrack?>(null) }
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()

    val handleBack = {
        if (selectedPlaylist != null) {
            viewModel.selectPlaylist(null)
        } else {
            onBack()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            val currentTitle = if (selectedPlaylist != null) selectedPlaylist!!.name else tabs[pagerState.currentPage]
            TitanHeader(
                title = currentTitle,
                onBack = handleBack,
                sectionName = "MUSIC",
                actions = {
                    if (pagerState.currentPage == 2 && selectedPlaylist == null) {
                        IconButton(onClick = { showCreatePlaylistDialog = true }) {
                            Icon(Icons.Default.Add, null, tint = TitanColors.NeonCyan)
                        }
                    }
                }
            )
        },
        bottomBar = {
            // ── HOLOGRAPHIC TAB BAR ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neonGlass(cornerRadius = 0.dp, glowColor = TitanColors.QuantumPink.copy(alpha = 0.1f))
                    .padding(vertical = 8.dp, horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = pagerState.currentPage == index
                        val color = tabColors[index]

                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                                .then(
                                    if (isSelected) Modifier
                                        .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                        .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    else Modifier
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                tabIcons[index],
                                contentDescription = null,
                                tint = if (isSelected) color else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = title,
                                color = if (isSelected) color else Color.White.copy(alpha = 0.3f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(TitanColors.DeepVoidGradient).padding(padding)) {
            MatrixRain(
                color = accentColor,
                alpha = 0.15f,
                speed = 40L,
                isMulticolor = SectionColorManager.isMulticolor
            )
            val accentColor = SectionColorManager.musicAccent
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> RecentsTab(viewModel, onAddToPlaylist = { trackToAddByPlaylist = it })
                    1 -> NowPlayingTab(viewModel, accentColor)
                    2 -> SongsTab(viewModel, onAddToPlaylist = { trackToAddByPlaylist = it })
                    3 -> PlaylistsTab(
                        viewModel = viewModel,
                        selectedPlaylist = selectedPlaylist,
                        onPlaylistClick = { viewModel.selectPlaylist(it) }
                    )
                    4 -> FavoritesTab(viewModel, onAddToPlaylist = { trackToAddByPlaylist = it })
                }
            }

            // ── COLOR CUSTOMIZER TRIGGER ──


            if (showColorDialog) {
                ColorSelectionDialog(
                    sectionName = "MUSIC HUB",
                    onColorSelected = { SectionColorManager.musicAccent = it },
                    onDismiss = { showColorDialog = false }
                )
            }
        }

        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreatePlaylistDialog = false },
                onCreate = { name -> viewModel.createPlaylist(name); showCreatePlaylistDialog = false }
            )
        }

        if (trackToAddByPlaylist != null) {
            AddToPlaylistDialog(
                viewModel = viewModel,
                onDismiss = { trackToAddByPlaylist = null },
                onSelect = { id -> viewModel.addTrackToPlaylist(id, trackToAddByPlaylist!!); trackToAddByPlaylist = null }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════
// RECENTS TAB
// ══════════════════════════════════════════════════════════════

@Composable
private fun RecentsTab(viewModel: MusicHubViewModel, onAddToPlaylist: (MusicTrack) -> Unit) {
    val recents by viewModel.recents.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val accentColor = SectionColorManager.musicAccent

    if (recents.isEmpty()) {
        AnimatedEmptyState(icon = Icons.Default.History, message = "NO RECENT TRACKS", color = accentColor)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                GlassPillBadge(
                    text = "RECENT TRACKS",
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            itemsIndexed(recents) { index, track ->
                SovereignLifeWrapper {
                    TrackItem(
                        index = index + 1,
                        track = track,
                        isCurrentlyPlaying = currentTrack?.id == track.id && isPlaying,
                        onPlay = { viewModel.playTrack(track, recents) },
                        onFavorite = { viewModel.toggleFavorite(track.id) },
                        onAddToPlaylist = { onAddToPlaylist(track) }
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// SONGS TAB
// ══════════════════════════════════════════════════════════════

@Composable
private fun SongsTab(viewModel: MusicHubViewModel, onAddToPlaylist: (MusicTrack) -> Unit) {
    val songs by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val accentColor = SectionColorManager.musicAccent

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accentColor, strokeWidth = 2.dp)
        }
    } else if (songs.isEmpty()) {
        AnimatedEmptyState(icon = Icons.Default.MusicOff, message = "NO TRACKS FOUND", color = accentColor)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    GlassPillBadge(
                        text = "${songs.size} TRACKS LOADED",
                        color = accentColor
                    )

                    IconButton(
                        onClick = { viewModel.refreshLibrary() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Refresh",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            itemsIndexed(songs) { index, track ->
                SovereignLifeWrapper { // Wrap for life effect
                    TrackItem(
                        index = index + 1,
                        track = track,
                        isCurrentlyPlaying = currentTrack?.id == track.id && isPlaying,
                        onPlay = { viewModel.playTrack(track, songs) }, // Pass the full songs list as queue
                        onFavorite = { viewModel.toggleFavorite(track.id) },
                        onAddToPlaylist = { onAddToPlaylist(track) }
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// TRACK ITEM
// ══════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackItem(
    index: Int,
    track: MusicTrack,
    isCurrentlyPlaying: Boolean = false,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val accentColor = if (isCurrentlyPlaying) TitanColors.NeonCyan else TitanColors.QuantumPink

    SovereignCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onPlay() },
                onLongClick = { showMenu = true }
            ),
        cornerRadius = 14.dp,
        glassAlpha = if (isCurrentlyPlaying) 0.2f else 0.1f,
        glowRadius = if (isCurrentlyPlaying) 16.dp else 8.dp
    ) {
        Box(modifier = Modifier.fillMaxSize().background(accentColor.copy(alpha = 0.12f))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Track number or equalizer
                Box(
                    modifier = Modifier.width(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCurrentlyPlaying) {
                        EqualizerBars(isAnimating = true, color = SectionColorManager.musicAccent)
                    } else {
                        Text(
                            text = "%02d".format(index),
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))

                // Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    NeonGlowIcon(
                        icon = Icons.Default.MusicNote,
                        color = accentColor,
                        size = 22.dp,
                        glowRadius = 8.dp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))

                // Track info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.name,
                        color = if (isCurrentlyPlaying) TitanColors.NeonCyan else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${track.artist ?: "Unknown"} • ${track.album ?: "Unknown"}",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Duration
                Text(
                    formatTime(track.duration),
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                // Favorite
                IconButton(onClick = onFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (track.isFavorite) TitanColors.QuantumPink else Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showMenu) {
        TrackOptionsDialog(
            track = track,
            onDismiss = { showMenu = false },
            onAddToPlaylist = { showMenu = false; onAddToPlaylist() }
        )
    }
}

// ══════════════════════════════════════════════════════════════
// PLAYLISTS TAB
// ══════════════════════════════════════════════════════════════

@Composable
private fun PlaylistsTab(
    viewModel: MusicHubViewModel,
    selectedPlaylist: Playlist?,
    onPlaylistClick: (Playlist) -> Unit
) {
    if (selectedPlaylist != null) {
        PlaylistDetailView(viewModel = viewModel, playlist = selectedPlaylist)
    } else {
        val playlists by viewModel.playlists.collectAsState()

        if (playlists.isEmpty()) {
            AnimatedEmptyState(icon = Icons.Default.PlaylistAdd, message = "NO PLAYLISTS YET", color = TitanColors.RadioactiveGreen)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(playlists) { playlist ->
                    PlaylistItem(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) },
                        onDelete = { viewModel.deletePlaylist(playlist.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailView(viewModel: MusicHubViewModel, playlist: Playlist) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (playlist.tracks.isEmpty()) {
            item {
                AnimatedEmptyState(
                    icon = Icons.Default.MusicOff,
                    message = "NO TRACKS IN PLAYLIST",
                    color = TitanColors.RadioactiveGreen,
                    modifier = Modifier.height(300.dp)
                )
            }
        }
        itemsIndexed(playlist.tracks) { index, track ->
            TrackItem(
                index = index + 1,
                track = track,
                isCurrentlyPlaying = currentTrack?.id == track.id && isPlaying,
                onPlay = { viewModel.playTrack(track, playlist.tracks) }, // Pass the playlist tracks as queue
                onFavorite = { viewModel.toggleFavorite(track.id) },
                onAddToPlaylist = { /* Already in playlist */ }
            )
        }
    }
}

@Composable
private fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    SovereignCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        cornerRadius = 16.dp,
        glassAlpha = 0.15f,
        glowRadius = 12.dp
    ) {
        Box(modifier = Modifier.fillMaxSize().background(TitanColors.NeonCyan.copy(alpha = 0.1f))) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Playlist icon mosaic
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    TitanColors.RadioactiveGreen.copy(alpha = 0.15f),
                                    TitanColors.NeonCyan.copy(alpha = 0.1f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    NeonGlowIcon(
                        icon = Icons.Default.PlaylistPlay,
                        color = TitanColors.RadioactiveGreen,
                        size = 28.dp,
                        glowRadius = 12.dp
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(playlist.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${playlist.tracks.size} tracks",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, tint = TitanColors.NeonRed.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                }
                Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// FAVORITES TAB
// ══════════════════════════════════════════════════════════════

@Composable
private fun FavoritesTab(viewModel: MusicHubViewModel, onAddToPlaylist: (MusicTrack) -> Unit) {
    val songs by viewModel.songs.collectAsState()
    val favorites = songs.filter { it.isFavorite }
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    if (favorites.isEmpty()) {
        AnimatedEmptyState(icon = Icons.Default.FavoriteBorder, message = "NO FAVORITES YET", color = TitanColors.NeonRed)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(favorites) { index, track ->
                TrackItem(
                    index = index + 1,
                    track = track,
                    isCurrentlyPlaying = currentTrack?.id == track.id && isPlaying,
                    onPlay = { viewModel.playTrack(track, favorites) }, // Pass the favorites list as queue
                    onFavorite = { viewModel.toggleFavorite(track.id) },
                    onAddToPlaylist = { onAddToPlaylist(track) }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// NOW PLAYING
// ══════════════════════════════════════════════════════════════

@Composable
fun NowPlayingTab(viewModel: MusicHubViewModel, accentColor: Color) {
    val currentTrack by viewModel.currentTrack.collectAsState()

    if (currentTrack == null) {
        AnimatedEmptyState(icon = Icons.Default.QueueMusic, message = "SELECT A TRACK TO START", color = accentColor)
    } else {
        SovereignNowPlaying(viewModel, accentColor)
    }
}

@Composable
private fun SovereignNowPlaying(viewModel: MusicHubViewModel, accentColor: Color) {
    val track by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val volume by viewModel.volume.collectAsState()

    // Vinyl rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl")
    val vinylRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinylSpin"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.3f))

        // ── ARTWORK / VINYL ──
        SovereignCard(
            modifier = Modifier
                .size(240.dp)
                .graphicsLayer {
                    if (isPlaying) rotationZ = vinylRotation
                },
            cornerRadius = 120.dp,
            glassAlpha = 0.2f,
            glowRadius = 32.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Vinyl rings
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .border(1.dp, TitanColors.QuantumPink.copy(alpha = 0.1f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .border(0.5.dp, TitanColors.QuantumPink.copy(alpha = 0.08f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(accentColor.copy(alpha = 0.05f), CircleShape)
                        .border(1.dp, accentColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    NeonGlowIcon(
                        icon = Icons.Default.MusicNote,
                        color = accentColor,
                        size = 40.dp,
                        glowRadius = 20.dp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── TRACK INFO ──
        Text(
            track?.name ?: "",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            track?.artist ?: "Unknown Artist",
            color = accentColor.copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── SEEK BAR ──
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = progress,
                onValueChange = { viewModel.seekTo(it) },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = TitanColors.NeonCyan,
                    activeTrackColor = TitanColors.NeonCyan,
                    inactiveTrackColor = Color.White.copy(alpha = 0.08f)
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(currentPosition), color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text(formatTime(duration), color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── VOLUME ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.VolumeDown, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
            Slider(
                value = volume,
                onValueChange = { viewModel.setVolume(it) },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = TitanColors.QuantumPink,
                    activeTrackColor = TitanColors.QuantumPink,
                    inactiveTrackColor = Color.White.copy(alpha = 0.06f)
                )
            )
            Icon(Icons.Default.VolumeUp, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── TRANSPORT CONTROLS ──
        SovereignCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            glassAlpha = 0.1f,
            glowRadius = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { viewModel.toggleShuffle() }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Shuffle, null,
                        tint = if (isShuffle) TitanColors.NeonCyan else Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { viewModel.skipPrevious() }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }

                // Play/Pause — main button
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .pulsingNeonBorder(cornerRadius = 32.dp, glowColor = TitanColors.NeonCyan)
                        .border(1.dp, TitanColors.NeonCyan.copy(alpha = 0.3f), CircleShape)
                        .clip(CircleShape)
                        .clickable { viewModel.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    NeonGlowIcon(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        color = accentColor,
                        size = 36.dp,
                        glowRadius = 16.dp
                    )
                }

                IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = { viewModel.toggleRepeat() }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        null,
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) TitanColors.NeonCyan else Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.3f))
    }
}

// ══════════════════════════════════════════════════════════════
// DIALOGS
// ══════════════════════════════════════════════════════════════

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("NEW PLAYLIST", color = TitanColors.NeonCyan, fontWeight = FontWeight.Bold, letterSpacing = 2.sp) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name", color = Color.White.copy(alpha = 0.4f)) },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = TitanColors.NeonCyan,
                    focusedIndicatorColor = TitanColors.NeonCyan,
                    unfocusedIndicatorColor = Color.White.copy(alpha = 0.1f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name) }) {
                Text("CREATE", color = TitanColors.NeonCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = Color.White.copy(alpha = 0.5f)) }
        },
        containerColor = TitanColors.AbsoluteBlack.copy(alpha = 0.95f)
    )
}

@Composable
private fun AddToPlaylistDialog(
    viewModel: MusicHubViewModel,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SELECT PLAYLIST", color = TitanColors.NeonCyan, fontWeight = FontWeight.Bold, letterSpacing = 2.sp) },
        text = {
            if (playlists.isEmpty()) {
                Text("No playlists found. Create one first.", color = Color.White.copy(alpha = 0.5f))
            } else {
                LazyColumn {
                    items(playlists) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(playlist.id) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlaylistPlay, null, tint = TitanColors.NeonCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(playlist.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("${playlist.tracks.size} tracks", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = Color.White.copy(alpha = 0.5f)) }
        },
        containerColor = TitanColors.AbsoluteBlack.copy(alpha = 0.95f)
    )
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    return "%02d:%02d".format(min, sec)
}

@Composable
fun TrackOptionsDialog(
    track: MusicTrack,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val accentColor = SectionColorManager.musicAccent

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TitanColors.AbsoluteBlack,
        title = {
            Column {
                Text(
                    "TRACK OPTIONS",
                    color = accentColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    track.name,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                OptionItem(Icons.Default.PlaylistAdd, "Add to Playlist", Color.White) { onAddToPlaylist() }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("DISMISS", color = accentColor)
            }
        },
        modifier = Modifier
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
    )
}

@Composable
private fun OptionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, color = Color.White, fontSize = 14.sp)
    }
}
