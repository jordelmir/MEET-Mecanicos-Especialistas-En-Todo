package com.elysium.vanguard.features.analyzer

import android.os.Environment
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.vanguard.core.analyzer.TreemapLayout
import com.elysium.vanguard.ui.theme.TitanColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * PHASE 1.14 — Storage analyzer screen with interactive treemap.
 *
 * Top-level view: 200 largest items under /storage/emulated/0/. Tap a node to
 * drill into it (pushes a new state into the back-stack). The breadcrumb at
 * the top lets the user navigate back.
 */
@HiltViewModel
class StorageAnalyzerViewModel @Inject constructor(
    private val layout: TreemapLayout
) : ViewModel() {

    data class UiState(
        val currentDir: File = Environment.getExternalStorageDirectory(),
        val nodes: List<TreemapLayout.Node> = emptyList(),
        val totalBytes: Long = 0L,
        val history: List<File> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        navigate(Environment.getExternalStorageDirectory())
    }

    fun navigate(dir: File) {
        viewModelScope.launch {
            val nodes = withContext(Dispatchers.IO) { layout.analyze(dir) }
            _state.value = _state.value.copy(
                currentDir = dir,
                nodes = nodes,
                totalBytes = nodes.sumOf { it.sizeBytes },
                history = _state.value.history + dir
            )
        }
    }

    fun goBack() {
        val hist = _state.value.history
        if (hist.size <= 1) return
        val newHist = hist.dropLast(1)
        val target = newHist.last()
        viewModelScope.launch {
            val nodes = withContext(Dispatchers.IO) { layout.analyze(target) }
            _state.value = _state.value.copy(
                currentDir = target,
                nodes = nodes,
                totalBytes = nodes.sumOf { it.sizeBytes },
                history = newHist
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalyzerScreen(
    onBack: () -> Unit,
    viewModel: StorageAnalyzerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.currentDir.absolutePath,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            "${state.nodes.size} items · ${formatSize(state.totalBytes)}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (state.history.size > 1) {
                        TextButton(onClick = { viewModel.goBack() }) {
                            Text("Up", color = TitanColors.NeonCyan)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.nodes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No contents",
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            } else {
                TreemapCanvas(
                    nodes = state.nodes,
                    onTap = { node ->
                        if (node.file.isDirectory) viewModel.navigate(node.file)
                    }
                )
            }
        }
    }
}

@Composable
private fun TreemapCanvas(
    nodes: List<TreemapLayout.Node>,
    onTap: (TreemapLayout.Node) -> Unit
) {
    val layout = remember { TreemapLayout() }
    val density = LocalDensity.current

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .pointerInput(nodes) {
                detectTapGestures { offset ->
                    // Find which node was tapped; tap navigates into directories.
                    val widthPx = size.width.toFloat()
                    val heightPx = size.height.toFloat()
                    val rects = layout.layout(nodes, widthPx, heightPx)
                    val hit = rects.firstOrNull {
                        offset.x >= it.x && offset.x <= it.x + it.width &&
                            offset.y >= it.y && offset.y <= it.y + it.height
                    }
                    if (hit != null) {
                        onTap(nodes.first { it.file == hit.file })
                    }
                }
            }
    ) {
        val widthPx = size.width
        val heightPx = size.height
        val rects = layout.layout(nodes, widthPx, heightPx)
        val maxSize = nodes.maxOf { it.sizeBytes }.toFloat().coerceAtLeast(1f)
        for (rect in rects) {
            val intensity = (rect.sizeBytes / maxSize).coerceIn(0.05f, 1f)
            val color = Color(
                red = 0.05f + 0.10f * intensity,
                green = 0.05f + 0.55f * intensity,
                blue = 0.15f + 0.40f * intensity,
                alpha = 1f
            )
            drawRect(
                color = color,
                topLeft = Offset(rect.x, rect.y),
                size = Size(rect.width, rect.height)
            )
            // Faint border so adjacent rectangles are visually distinct.
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(rect.x, rect.y),
                size = Size(rect.width, rect.height),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
            )
        }
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
