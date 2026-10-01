package com.elysium.vanguard.features.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elysium.vanguard.core.database.TrashEntity
import com.elysium.vanguard.ui.theme.TitanColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PHASE 1.5 — Trash screen.
 *
 * Lists every file moved to trash, sorted by deletion time (newest first).
 * Each row exposes Restore and Delete-forever actions. A sticky bottom bar
 * surfaces the total size and an "Empty trash" button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Trash",
                            color = TitanColors.NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${state.items.size} items · ${formatSize(state.totalBytes)}",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (state.items.isNotEmpty()) {
                        TextButton(onClick = { viewModel.emptyTrash() }) {
                            Text("Empty", color = TitanColors.QuantumPink)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black
    ) { padding ->
        if (state.items.isEmpty() && !state.isLoading) {
            EmptyTrashView(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { it.id }) { item ->
                    TrashRow(
                        item = item,
                        onRestore = { viewModel.restore(item) },
                        onPurge = { viewModel.purge(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashRow(
    item: TrashEntity,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    val df = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.originalName,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatSize(item.sizeBytes)} · ${df.format(Date(item.deletedAt))}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onRestore) {
                Icon(
                    Icons.Filled.Restore,
                    contentDescription = "Restore",
                    tint = TitanColors.NeonCyan
                )
            }
            IconButton(onClick = onPurge) {
                Icon(
                    Icons.Filled.DeleteForever,
                    contentDescription = "Delete forever",
                    tint = TitanColors.QuantumPink
                )
            }
        }
    }
}

@Composable
private fun EmptyTrashView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.DeleteSweep,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Trash is empty",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            "Deleted files appear here for 30 days",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 13.sp
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var idx = 0
    while (size >= 1024 && idx < units.lastIndex) {
        size /= 1024
        idx++
    }
    return if (idx == 0) "${bytes} ${units[0]}" else String.format(Locale.US, "%.1f %s", size, units[idx])
}
