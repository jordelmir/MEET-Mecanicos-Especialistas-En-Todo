package com.elysium.vanguard.features.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.elysium.vanguard.ui.theme.TitanColors
import com.elysium.vanguard.ui.theme.premiumGlass
import java.io.File

import androidx.compose.material.icons.filled.Edit

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.elysium.vanguard.core.util.FileThematics
import com.elysium.vanguard.core.util.FileCategory

import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HighFidelityImageViewer(
    filePath: String,
    onEdit: (String) -> Unit,
    onBack: () -> Unit
) {
    val initialFile = File(filePath)
    val parentDir = initialFile.parentFile
    val mediaFiles = remember(parentDir) {
        parentDir?.listFiles()?.filter { file ->
            FileThematics.getCategory(file.name, file.isDirectory) == FileCategory.IMAGE
        }?.sortedBy { it.name.lowercase() } ?: listOf(initialFile)
    }

    val initialIndex = mediaFiles.indexOfFirst { it.absolutePath == filePath }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex) { mediaFiles.size }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TitanColors.AbsoluteBlack)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val currentFile = mediaFiles[page]
            AsyncImage(
                model = currentFile,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Overlay controls
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.premiumGlass(cornerRadius = 12.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
            }

            IconButton(
                onClick = { onEdit(mediaFiles[pagerState.currentPage].absolutePath) },
                modifier = Modifier.premiumGlass(cornerRadius = 12.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TitanColors.NeonCyan)
            }
        }
    }
}
