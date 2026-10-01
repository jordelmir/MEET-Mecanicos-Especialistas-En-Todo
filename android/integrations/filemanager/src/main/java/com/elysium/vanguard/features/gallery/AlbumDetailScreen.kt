package com.elysium.vanguard.features.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.vanguard.ui.theme.TitanColors
import com.elysium.vanguard.ui.theme.neonGlass
import com.elysium.vanguard.ui.components.MatrixRain
import com.elysium.vanguard.ui.components.NeonGlowIcon
import com.elysium.vanguard.ui.components.SovereignLifeWrapper

@Composable
fun AlbumDetailScreen(
    albumName: String,
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
    onMediaClick: (GalleryMedia) -> Unit
) {
    val albums by viewModel.albums.collectAsState()
    val album = albums.find { it.name == albumName }
    val mediaList = album?.media ?: emptyList()

    Box(modifier = Modifier.fillMaxSize().background(TitanColors.AbsoluteBlack)) {
        MatrixRain(
            color = TitanColors.NeonCyan.copy(alpha = 0.3f),
            speed = 60L
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .neonGlass(cornerRadius = 12.dp, glowColor = TitanColors.NeonCyan)
                        .size(48.dp)
                ) {
                    NeonGlowIcon(
                        icon = Icons.Default.ArrowBack,
                        color = Color.White,
                        size = 24.dp,
                        glowRadius = 8.dp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        albumName,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${mediaList.size} Items",
                        color = TitanColors.NeonCyan,
                        fontSize = 12.sp
                    )
                }
            }

            // Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(mediaList) { media ->
                    SovereignLifeWrapper {
                        MediaThumbnail(
                            media = media,
                            onClick = { onMediaClick(media) },
                            onOptionClick = { /* TODO: Context Menu */ }
                        )
                    }
                }
            }
        }
    }
}
