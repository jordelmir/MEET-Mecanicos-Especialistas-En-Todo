package com.elysium.vanguard.features.duplicates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elysium.vanguard.core.duplicates.DuplicatesDetector
import com.elysium.vanguard.ui.theme.TitanColors
import java.io.File

/**
 * PHASE 1.12 — Duplicates list.
 *
 * Each group shows N copies of the same file. Tapping a row toggles a checkbox
 * for "send to trash"; the bottom bar offers "Smart select" (keep oldest)
 * and a destructive action button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    onBack: () -> Unit,
    viewModel: DuplicatesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.startScan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Duplicates", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "${state.groups.size} groups · ${formatSize(state.totalWastedBytes)} reclaimable",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        bottomBar = {
            if (state.selection.isNotEmpty()) {
                BottomAppBar(containerColor = Color.Black) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewModel.clearSelection() }) {
                        Text("Clear", color = Color.White)
                    }
                    TextButton(
                        onClick = {
                            viewModel.trashSelection { moved ->
                                // surface feedback via state.errorMessage path
                            }
                        }
                    ) {
                        Text("Trash ${state.selection.size}", color = TitanColors.QuantumPink)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isScanning) {
                ScanningView(scanned = state.filesScanned)
            } else if (state.groups.isEmpty()) {
                EmptyView()
            } else {
                Column {
                    SmartSelectBar(onClick = { viewModel.smartSelectKeepOldest() })
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.groups, key = { it.hash }) { group ->
                            DuplicateGroupView(
                                group = group,
                                selected = state.selection,
                                onToggle = { viewModel.toggleSelection(it) }
                            )
                        }
                    }
                }
            }
            state.errorMessage?.let {
                LaunchedEffect(it) {
                    snackbarHostState.showSnackbar(it)
                }
            }
        }
    }
}

@Composable
private fun SmartSelectBar(onClick: () -> Unit) {
    Surface(
        color = TitanColors.NeonCyan.copy(alpha = 0.1f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = TitanColors.NeonCyan
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "Smart select",
                    color = TitanColors.NeonCyan,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    "Keep the oldest copy in each group, mark the rest for trash",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun DuplicateGroupView(
    group: DuplicatesDetector.DuplicateGroup,
    selected: Set<String>,
    onToggle: (File) -> Unit
) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "${group.files.size} copies · ${formatSize(group.sizeBytes)} each · ${formatSize(group.wastedBytes)} wasted",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
            Spacer(Modifier.height(6.dp))
            group.files.forEach { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(file) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = file.absolutePath in selected,
                        onCheckedChange = { onToggle(file) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = TitanColors.QuantumPink,
                            uncheckedColor = Color.White.copy(alpha = 0.5f)
                        )
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            file.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            file.parent ?: "",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        formatTimestamp(file.lastModified()),
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanningView(scanned: Int) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = TitanColors.NeonCyan)
        Spacer(Modifier.height(16.dp))
        Text(
            "Scanning… $scanned files",
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            "Hashing in parallel — first by size, then by content",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun EmptyView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = null,
            tint = TitanColors.NeonCyan.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No duplicates found",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
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
    return if (idx == 0) "${bytes} ${units[0]}" else String.format(java.util.Locale.US, "%.1f %s", size, units[idx])
}

private fun formatTimestamp(ts: Long): String {
    val df = java.text.SimpleDateFormat("MMM dd", java.util.Locale.US)
    return df.format(java.util.Date(ts))
}
