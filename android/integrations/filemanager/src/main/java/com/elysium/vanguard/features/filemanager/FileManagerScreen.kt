package com.elysium.vanguard.features.filemanager

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.elysium.vanguard.ui.theme.TitanColors
import com.elysium.vanguard.ui.theme.premiumGlass
import com.elysium.vanguard.ui.theme.neonGlass
import com.elysium.vanguard.ui.theme.holographicGlass
import com.elysium.vanguard.ui.theme.pulsingNeonBorder
import com.elysium.vanguard.ui.components.BreathingFolderIcon
import com.elysium.vanguard.ui.components.NeonGlowIcon
import com.elysium.vanguard.ui.components.MatrixRain
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import com.elysium.vanguard.core.util.CompressionEngine
import com.elysium.vanguard.core.util.ConversionEngine
import com.elysium.vanguard.core.util.FileCategory
import com.elysium.vanguard.ui.components.TitanLogo
import com.elysium.vanguard.ui.components.TitanLogoStyle
import com.elysium.vanguard.ui.components.TitanHeader
import com.elysium.vanguard.ui.components.SovereignLifeWrapper
import com.elysium.vanguard.ui.components.PulseContainer
import com.elysium.vanguard.ui.components.SovereignCard
import com.elysium.vanguard.core.util.FileOpenerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.elysium.vanguard.ui.theme.SectionColorManager
import com.elysium.vanguard.ui.components.ColorCustomizerIcon
import com.elysium.vanguard.ui.components.ColorSelectionDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * THE OMNI-CORE INTERFACE
 * The master orchestration layer for the Titan Glass File Manager.
 */
@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isTerminalMode by remember { mutableStateOf(false) } // Terminal Mode State
    val files by viewModel.files.collectAsState()

    // Recovery Fix: Local filtering logic to restore search functionality
    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isEmpty()) files
        else files.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    var showColorDialog by remember { mutableStateOf(false) }
    val accentColor = SectionColorManager.fileAccent
    val currentPath by viewModel.currentPath.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val storageStats by viewModel.storageStats.collectAsState()

    val isSelectionMode = selectedFiles.isNotEmpty()

    // Progress dialog state
    var showProgressDialog by remember { mutableStateOf(false) }
    var progressPercent by remember { mutableIntStateOf(0) }
    var progressFileName by remember { mutableStateOf("") }
    var progressTitle by remember { mutableStateOf("") }

    // Operation States
    var fileToRename by remember { mutableStateOf<TitanFile?>(null) }
    var fileForDetails by remember { mutableStateOf<TitanFile?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissionsAndLoad()
                viewModel.updateStorageStats()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(TitanColors.DeepVoidGradient)) {
        // ── MATRIX RAIN BACKGROUND ──
        MatrixRain(
            color = TitanColors.RadioactiveGreen.copy(alpha = 0.3f), // Subtler rain
            speed = 90L,
            trailLength = 12,
            alpha = 0.15f,
            isMulticolor = SectionColorManager.isMulticolor
        )
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (isSelectionMode) {
                    SelectionToolbar(
                        selectedCount = selectedFiles.size,
                        onClear = { viewModel.clearSelection() },
                        onDelete = { viewModel.deleteSelected() },
                        onMove = { viewModel.moveSelected() },
                        onCopy = { viewModel.copySelected() }
                    )
                } else {
                    val pendingOp by viewModel.pendingOperation.collectAsState()
                    TitanHeader(
                        title = if (pendingOp != null) {
                            if (pendingOp!!.type == FileOperationType.COPY) "SELECT DESTINATION TO COPY"
                            else "SELECT DESTINATION TO MOVE"
                        } else {
                            val lastPart = currentPath.split("/").last()
                            when {
                                currentPath == "/storage/emulated/0" -> "INTERNAL STORAGE"
                                lastPart == "0" -> "INTERNAL STORAGE"
                                else -> lastPart.ifEmpty { "FILE MANAGER" }
                            }
                        },
                        onBack = {
                            if (pendingOp != null) {
                                viewModel.cancelPendingOperation()
                            } else if (currentPath == android.os.Environment.getExternalStorageDirectory().absolutePath || currentPath == "/") {
                                onBack()
                            } else {
                                viewModel.navigateUp()
                            }
                        },
                        sectionName = "FILEMANAGER",
                        actions = {
                            val viewMode by viewModel.viewMode.collectAsState()
                            IconButton(onClick = { viewModel.toggleViewMode() }) {
                                Icon(
                                    imageVector = if (viewMode == FileViewMode.TACTICAL) Icons.Default.GridView else Icons.Default.ViewList,
                                    contentDescription = "Toggle View",
                                    tint = TitanColors.NeonCyan
                                )
                            }
                            IconButton(onClick = { isTerminalMode = !isTerminalMode }) {
                                Icon(
                                    imageVector = if (isTerminalMode) Icons.Default.Apps else Icons.Default.Terminal,
                                    contentDescription = "Switch Mode",
                                    tint = TitanColors.NeonCyan
                                )
                            }
                        }
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                // ── COLOR CUSTOMIZER TRIGGER ──


                // BACK NAVIGATION MASTER SHIELD
                androidx.activity.compose.BackHandler(enabled = !isSelectionMode && currentPath != android.os.Environment.getExternalStorageDirectory().absolutePath && currentPath != "/") {
                    viewModel.navigateUp()
                }
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    // FLOATING PENDING OPERATION BAR
                    val pendingOp by viewModel.pendingOperation.collectAsState()
                    if (pendingOp != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .neonGlass(cornerRadius = 16.dp, glowColor = TitanColors.RadioactiveGreen)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (pendingOp!!.type == FileOperationType.COPY) "COPYING ${pendingOp!!.sourcePaths.size} ITEMS"
                                    else "MOVING ${pendingOp!!.sourcePaths.size} ITEMS",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "NAVIGATE TO DESTINATION",
                                    color = TitanColors.RadioactiveGreen,
                                    fontSize = 10.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { viewModel.cancelPendingOperation() }) {
                                    Text("CANCEL", color = TitanColors.NeonRed)
                                }
                                Button(
                                    onClick = { viewModel.pasteSelected() },
                                    colors = ButtonDefaults.buttonColors(containerColor = TitanColors.RadioactiveGreen)
                                ) {
                                    Text("PASTE", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    storageStats?.let { stats ->
                        StorageMetricsBar(stats)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    NeuralSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = { /* Reactive filtering via filteredFiles */ }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val shortcuts by viewModel.commonShortcuts.collectAsState()
                    if (shortcuts.isNotEmpty()) {
                        ShortcutsQuickAccess(
                            shortcuts = shortcuts,
                            onShortcutClick = { viewModel.loadDirectory(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    StorageCategoryRow(
                        onAddClick = { /* TODO */ },
                        onPathClick = { viewModel.loadDirectory(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TacticalBreadcrumbs(
                        path = currentPath,
                        onPathClick = { viewModel.loadDirectory(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    when (uiState) {
                        is FileManagerUiState.Loading -> {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                LoadingDiagnosticPulse(text = "QUANTUM SCANNING...")
                            }
                        }
                        is FileManagerUiState.PermissionRequired -> {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                StoragePermissionOverlay(context)
                            }
                        }
                        is FileManagerUiState.Error -> {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("CORE ERROR: ${(uiState as FileManagerUiState.Error).message}", color = TitanColors.NeonRed)
                            }
                        }
                        else -> {
                            // EMPTY or SUCCESS
                            if (isTerminalMode) {
                                TerminalList(
                                    files = filteredFiles,
                                    onFileClick = { file ->
                                        if (file.isFolder) viewModel.loadDirectory(file.path)
                                        else viewModel.openFile(file)
                                    },
                                    onCalculateSize = { viewModel.calculateRecursiveSize(it) }
                                )
                            } else {
                                val viewMode by viewModel.viewMode.collectAsState()
                                if (viewMode == FileViewMode.TACTICAL) {
                                    if (filteredFiles.isEmpty()) {
                                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            RadarScanningPlaceholder("NO DATA NODES DETECTED")
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            contentPadding = PaddingValues(bottom = 100.dp)
                                        ) {
                                            item { TacticalHeaderRow() }
                                            items(
                                                count = filteredFiles.size,
                                                key = { index -> filteredFiles[index].path }
                                            ) { index ->
                                                val file = filteredFiles[index]
                                                var showOptions by remember { mutableStateOf(false) }
                                                TacticalFileRow(
                                                    file = file,
                                                    isSelected = selectedFiles.contains(file.path),
                                                    onSelectionToggle = { viewModel.toggleSelection(file.path) },
                                                    onClick = {
                                                        if (isSelectionMode) viewModel.toggleSelection(file.path)
                                                        else if (file.isFolder) viewModel.loadDirectory(file.path)
                                                        else viewModel.openFile(file)
                                                    },
                                                    onLongClick = {
                                                        if (isSelectionMode) viewModel.toggleSelection(file.path)
                                                        else showOptions = true
                                                    }
                                                )
                                                if (showOptions) {
                                                    SovereignOptionsDialog(
                                                        file = file,
                                                        onDismiss = { showOptions = false },
                                                        onAction = { action ->
                                                            showOptions = false
                                                            if (action == "DETAILS") {
                                                                fileForDetails = file
                                                            } else {
                                                                handleFileAction(action, file, viewModel, scope, currentPath) { f -> fileToRename = f }
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // AESTHETIC MODE (Grid)
                                    if (filteredFiles.isEmpty()) {
                                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            RadarScanningPlaceholder("EMPTY DATA CLUSTER")
                                        }
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(3),
                                            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
                                            verticalArrangement = Arrangement.spacedBy(24.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            items(
                                                count = filteredFiles.size,
                                                key = { index -> filteredFiles[index].path }
                                            ) { index ->
                                                val file = filteredFiles[index]
                                                var showOptions by remember { mutableStateOf(false) }
                                                ReactorFileCard(
                                                    file = file,
                                                    isSelected = selectedFiles.contains(file.path),
                                                    onSelectionToggle = { viewModel.toggleSelection(file.path) },
                                                    onClick = {
                                                        if (isSelectionMode) viewModel.toggleSelection(file.path)
                                                        else if (file.isFolder) viewModel.loadDirectory(file.path)
                                                        else viewModel.openFile(file)
                                                    },
                                                    onLongClick = {
                                                        if (isSelectionMode) viewModel.toggleSelection(file.path)
                                                        else showOptions = true
                                                    }
                                                )
                                                if (showOptions) {
                                                    SovereignOptionsDialog(
                                                        file = file,
                                                        onDismiss = { showOptions = false },
                                                        onAction = { action ->
                                                            showOptions = false
                                                            if (action == "DETAILS") {
                                                                fileForDetails = file
                                                            } else {
                                                                handleFileAction(action, file, viewModel, scope, currentPath) { f -> fileToRename = f }
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // DIALOGS
            if (fileToRename != null) {
                RenameDialog(
                    file = fileToRename!!,
                    onDismiss = { fileToRename = null },
                    onConfirm = { newName ->
                        viewModel.renameFile(fileToRename!!.path, newName)
                        fileToRename = null
                    }
                )
            }

            if (fileForDetails != null) {
                FileDetailsDialog(
                    file = fileForDetails!!,
                    onDismiss = { fileForDetails = null }
                )
            }

            val compressionState by viewModel.compressionProgress.collectAsState()
            val keepScreenOn by viewModel.keepScreenOn.collectAsState()

            if (compressionState != null) {
                AdvancedProgressDialog(
                    state = compressionState!!,
                    keepScreenOn = keepScreenOn,
                    onKeepScreenOnChange = { viewModel.setKeepScreenOn(it) },
                    onCancel = { viewModel.cancelCompression() },
                    onBackground = { viewModel.dismissCompressionDialog() }
                )
            }

            if (showColorDialog) {
                ColorSelectionDialog(
                    sectionName = "FILEMANAGER",
                    onColorSelected = { SectionColorManager.fileAccent = it },
                    onDismiss = { showColorDialog = false }
                )
            }
        }
    }
}
@Composable
fun StorageMetricsBar(stats: StorageStats) {
    val infiniteTransition = rememberInfiniteTransition(label = "storage")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "storage_glow"
    )
    val accentColor = SectionColorManager.fileAccent
    val statusColor = if (stats.percentUsed > 90) TitanColors.NeonRed else accentColor

    SovereignCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        cornerRadius = 16.dp,
        glassAlpha = 0.2f,
        glowRadius = 24.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "STORAGE CENTRAL",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "${stats.percentUsed}% USED",
                color = statusColor.copy(alpha = glowAlpha),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = stats.percentUsed / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = statusColor,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "${stats.usedLabel} USED OF ${stats.totalLabel}",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
}

@Composable
fun SelectionToolbar(
    selectedCount: Int,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit
) {
    val accentColor = SectionColorManager.fileAccent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .background(TitanColors.AbsoluteBlack)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClear) {
            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "$selectedCount ITEMS ARMED",
                color = TitanColors.NeonRed,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "BATCH PROTOCOL ACTIVE",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        IconButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = accentColor)
        }
        IconButton(onClick = onMove) {
            Icon(Icons.Default.DriveFileMove, contentDescription = "Move", tint = accentColor)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TitanColors.NeonRed)
        }
    }
}

@Composable
fun TacticalBreadcrumbs(
    path: String,
    onPathClick: (String) -> Unit
) {
    val accentColor = SectionColorManager.fileAccent
    val parts = path.split("/").filter { it.isNotEmpty() }
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .horizontalScroll(scrollState)
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "ROOT",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable { onPathClick("/storage/emulated/0") }
        )

        parts.forEachIndexed { index, part ->
            Text(" > ", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
            val fullPath = "/" + parts.take(index + 1).joinToString("/")
            Text(
                text = part.uppercase(),
                color = if (index == parts.size - 1) SectionColorManager.fileAccent else Color.White,
                fontSize = 10.sp,
                fontWeight = if (index == parts.size - 1) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { onPathClick(fullPath) }
            )
        }
    }
}

@Composable
private fun ShortcutsQuickAccess(
    shortcuts: List<Pair<String, String>>,
    onShortcutClick: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(shortcuts) { (name, path) ->
            val icon = when (name.lowercase()) {
                "whatsapp" -> Icons.Default.Chat
                "documents" -> Icons.Default.Description
                "music" -> Icons.Default.MusicNote
                "pictures" -> Icons.Default.Image
                "downloads" -> Icons.Default.Download
                "dcim" -> Icons.Default.CameraAlt
                else -> Icons.Default.Folder
            }
            val color = when (name.lowercase()) {
                "whatsapp" -> Color(0xFF25D366)
                "documents" -> SectionColorManager.fileAccent
                else -> TitanColors.RadioactiveGreen
            }

            SovereignLifeWrapper {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.1f))
                        .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { onShortcutClick(path) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = name.uppercase(),
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun BreadcrumbItem(
    name: String,
    onClick: () -> Unit,
    isFirst: Boolean = false
) {
    val accentColor = SectionColorManager.fileAccent
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!isFirst) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = name.uppercase(),
            modifier = Modifier
                .clickable { onClick() }
                .padding(horizontal = 4.dp),
            color = accentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun SovereignAccessDeniedScreen() {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TitanColors.AbsoluteBlack),
        contentAlignment = Alignment.Center
    ) {
        // Matrix Rain background for denied screen
        MatrixRain(
            color = TitanColors.NeonRed.copy(alpha = 0.4f),
            speed = 100L,
            trailLength = 12,
            alpha = 0.15f
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NeonGlowIcon(
                icon = Icons.Default.Lock,
                color = TitanColors.NeonRed,
                size = 64.dp,
                glowRadius = 32.dp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "ACCESS DENIED",
                color = TitanColors.NeonRed,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = "SOVEREIGN DATA AUTHORIZATION REQUIRED",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(
                                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + context.packageName)
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) { }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TitanColors.NeonCyan.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, TitanColors.NeonCyan)
            ) {
                Text("GRANT ROOT ACCESS", color = TitanColors.NeonCyan)
            }
        }
    }
}

@Composable
private fun TitanTopBar(
    title: String,
    currentPath: String,
    isTerminalMode: Boolean,
    onTerminalClick: () -> Unit,
    onBack: () -> Unit,
    accentColor: Color = SectionColorManager.fileAccent
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .neonGlass(cornerRadius = 12.dp, glowColor = accentColor.copy(alpha = 0.5f))
                .size(48.dp)
        ) {
            NeonGlowIcon(
                icon = Icons.Default.ArrowBack,
                color = Color.White,
                size = 22.dp,
                glowRadius = 8.dp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        TitanLogo(style = TitanLogoStyle.ICON, size = 44.dp)

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = if (currentPath == "/storage/emulated/0") "Internal Storage" else currentPath.split("/").last(),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        NeonGlowIcon(
            icon = Icons.Default.Terminal,
            color = if (isTerminalMode) TitanColors.RadioactiveGreen else accentColor,
            size = 24.dp,
            glowRadius = if (isTerminalMode) 16.dp else 10.dp,
            modifier = Modifier.clickable { onTerminalClick() }
        )
    }
}

@Composable
private fun NeuralSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(56.dp)
            .neonGlass(cornerRadius = 12.dp, glowColor = SectionColorManager.fileAccent.copy(alpha = 0.4f))
    ) {
        val accentColor = SectionColorManager.fileAccent
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxSize(),
            placeholder = {
                Text(
                    "Search files...",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = TitanColors.NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = TitanColors.NeonCyan,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
            singleLine = true
        )
    }
}

@Composable
private fun StorageCategoryRow(
    onAddClick: () -> Unit,
    onPathClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(TitanColors.CarbonGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .border(1.dp, TitanColors.NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .clickable { onAddClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = TitanColors.NeonCyan)
        }

        StorageChip(
            title = "Internal Storage",
            icon = Icons.Default.SdStorage,
            onClick = { onPathClick("/storage/emulated/0") }
        )

        StorageChip(
            title = "Downloads",
            icon = Icons.Default.Download,
            onClick = { onPathClick("/storage/emulated/0/Download") }
        )

        StorageChip(
            title = "Termux",
            icon = Icons.Default.Terminal,
            onClick = { onPathClick("/data/data/com.termux/files/home") }
        )

        StorageChip(
            title = "WhatsApp",
            icon = Icons.Default.Share, // Using Share icon as generic chat/social icon
            onClick = { onPathClick("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media") }
        )
    }
}

@Composable
private fun StorageChip(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    SovereignCard(
        modifier = Modifier
            .height(48.dp)
            .clickable { onClick() },
        cornerRadius = 12.dp,
        glassAlpha = 0.2f,
        glowRadius = 16.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeonGlowIcon(
                icon = icon,
                color = TitanColors.NeonCyan,
                size = 18.dp,
                glowRadius = 8.dp
            )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif
        )
    }
}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReactorFileCard(
    file: TitanFile,
    isSelected: Boolean,
    onSelectionToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SovereignCard(
            modifier = Modifier
                .size(90.dp)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, TitanColors.NeonRed, RoundedCornerShape(24.dp))
                    } else {
                        Modifier
                    }
                ),
            cornerRadius = 24.dp,
            glassAlpha = if (isSelected) 0.25f else 0.2f,
            glowRadius = if (isSelected) 32.dp else 24.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(file.thematicColor.copy(alpha = 0.15f)), // Global vivid tint
                contentAlignment = Alignment.Center
            ) {
                // Enhanced Preview Logic: Images, Videos, APKs
                if (file.mimeType.startsWith("image/") || file.mimeType.startsWith("video/") || file.category == com.elysium.vanguard.core.util.FileCategory.SYSTEM) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(file.path)
                            .crossfade(true)
                            .size(200)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(file.thematicColor.copy(alpha = 0.2f))  // Vivid fallback
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else if (file.isFolder) {
                    // 3D BREATHING FOLDER
                    BreathingFolderIcon(baseColor = file.thematicColor)
                } else {
                    // FALLBACK ICON FROM THEMATICS
                    NeonGlowIcon(
                        icon = com.elysium.vanguard.core.util.FileThematics.getCategoryIcon(file.category),
                        color = file.thematicColor,
                        size = 42.dp,
                        glowRadius = 16.dp
                    )
                }
            }

            // SELECTION INDICATOR
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = TitanColors.NeonRed,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = file.name,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun SovereignOptionsDialog(
    file: TitanFile,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit
) {
    val ext = file.name.substringAfterLast(".", "").lowercase()
    val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heif", "heic")
    val isDocument = ext in listOf("doc", "docx", "txt", "rtf", "csv", "html", "htm", "epub", "mobi")
    val isZip = ext == "zip"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TitanColors.AbsoluteBlack,
        title = {
            Text(
                text = "SOVEREIGN OPTIONS",
                color = TitanColors.NeonCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = file.name, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))

                // Convert to PDF: show for images, docs, and text files
                if (isImage || isDocument) {
                    OptionItem(Icons.Default.Description, "Convert to PDF", TitanColors.NeonRed) { onAction("PDF") }
                }

                // Decompress: show for ZIP files
                if (isZip) {
                    OptionItem(Icons.Default.FolderOpen, "Decompress ZIP", TitanColors.RadioactiveGreen) { onAction("UNZIP") }
                }

                // Compress: show for all non-ZIP files
                if (!isZip) {
                    OptionItem(Icons.Default.Archive, "Compress to ZIP", TitanColors.NeonCyan) { onAction("ZIP") }
                }

                OptionItem(Icons.Default.Edit, "Rename", Color.White) { onAction("RENAME") }
                OptionItem(Icons.Default.Info, "Details", Color.White) { onAction("DETAILS") }
                OptionItem(Icons.Default.Share, "Share", Color.White) { onAction("SHARE") }
                Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
                OptionItem(Icons.Default.Delete, "Delete", TitanColors.NeonRed) { onAction("DELETE") }

                OptionItem(Icons.Default.Lock, "Secure Vault", Color.Yellow) { onAction("VAULT") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("DISMISS", color = TitanColors.NeonCyan)
            }
        },
        modifier = Modifier
            .border(1.dp, TitanColors.NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
    )
}

@Composable
private fun OptionItem(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
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

@Composable
fun AdvancedProgressDialog(
    state: CompressionState,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onBackground: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .neonGlass(cornerRadius = 24.dp, glowColor = TitanColors.NeonCyan)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "QUANTUM COMPRESSION",
                color = TitanColors.NeonCyan,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Pulse the progress indicator
            PulseContainer(pulseEnabled = true) {
                Box(contentAlignment = Alignment.Center) {
                    if (state.status == CompressionStatus.SUCCESS) {
                        NeonGlowIcon(
                            icon = Icons.Default.CheckCircle,
                            color = TitanColors.RadioactiveGreen,
                            size = 80.dp,
                            glowRadius = 40.dp
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = state.percentage / 100f,
                            modifier = Modifier.size(120.dp),
                            color = TitanColors.NeonCyan,
                            strokeWidth = 8.dp,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                    }
                    Text(
                        if (state.status == CompressionStatus.SUCCESS) "COMPLETE" else "${state.percentage}%",
                        color = if (state.status == CompressionStatus.SUCCESS) TitanColors.RadioactiveGreen else Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                state.currentFile,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onKeepScreenOnChange(!keepScreenOn) }
            ) {
                Checkbox(
                    checked = keepScreenOn,
                    onCheckedChange = { onKeepScreenOnChange(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = TitanColors.NeonCyan,
                        uncheckedColor = Color.White.copy(alpha = 0.5f),
                        checkmarkColor = TitanColors.AbsoluteBlack
                    )
                )
                Text(
                    "KEEP SCREEN ON",
                    color = if (keepScreenOn) TitanColors.NeonCyan else Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (state.status != CompressionStatus.SUCCESS) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TitanColors.NeonRed.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, TitanColors.NeonRed)
                    ) {
                        Text("CANCEL", color = TitanColors.NeonRed, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onBackground,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TitanColors.NeonCyan.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, TitanColors.NeonCyan)
                    ) {
                        Text("BACKGROUND", color = TitanColors.NeonCyan, fontSize = 12.sp)
                    }
                }
            } else {
                 Button(
                    onClick = onBackground, // Dismiss
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TitanColors.RadioactiveGreen.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, TitanColors.RadioactiveGreen)
                ) {
                    Text("CLOSE", color = TitanColors.RadioactiveGreen, fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TacticalFileRow(
    file: TitanFile,
    isSelected: Boolean,
    onSelectionToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val dateStr = remember(file.lastModified) { dateFormatter.format(Date(file.lastModified)) }

    SovereignLifeWrapper {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .background(if (isSelected) TitanColors.NeonRed.copy(alpha = 0.1f) else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(file.thematicColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = com.elysium.vanguard.core.util.FileThematics.getCategoryIcon(file.category),
                    contentDescription = null,
                    tint = file.thematicColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Main Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = file.permissions,
                        color = TitanColors.NeonCyan.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dateStr,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }

            // Size
            Text(
                text = file.size,
                color = if (file.isFolder) TitanColors.NeonCyan else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

private fun handleFileAction(
    action: String,
    file: TitanFile,
    viewModel: FileManagerViewModel,
    scope: CoroutineScope,
    currentPath: String,
    onRenameRequest: (TitanFile) -> Unit
) {
    val srcFile = File(file.path)
    when (action) {
        "RENAME" -> onRenameRequest(file)
        "DETAILS" -> { /* Handled at call site for UI state */ }
        "DELETE" -> viewModel.deleteFile(file.path)
        "COPY" -> viewModel.copySelected(listOf(file.path))
        "MOVE" -> viewModel.moveSelected(listOf(file.path))
        "SHARE" -> {
            // Share logic
        }
        "PDF" -> {
            scope.launch {
                val outFile = File(srcFile.parent, "${srcFile.nameWithoutExtension}.pdf")
                val ext = srcFile.extension.lowercase()
                withContext(Dispatchers.IO) {
                    when {
                        ext in listOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heif", "heic") ->
                            ConversionEngine.imagesToPdf(listOf(srcFile), outFile)
                        ext in listOf("doc", "docx") ->
                            ConversionEngine.docxToPdf(srcFile, outFile)
                        else ->
                            ConversionEngine.fileToPdf(srcFile, outFile)
                    }
                }
                viewModel.loadDirectory(currentPath)
            }
        }
        "ZIP" -> {
            val output = File(file.path + ".zip")
            viewModel.compressFiles(listOf(File(file.path)), output)
        }
        "UNZIP" -> {
            viewModel.decompressFile(srcFile, File(srcFile.parent, srcFile.nameWithoutExtension))
        }
        "VAULT" -> { /* Secure Vault logic */ }
    }
}

@Composable
fun TerminalList(
    files: List<TitanFile>,
    onFileClick: (TitanFile) -> Unit,
    onCalculateSize: (TitanFile) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(TitanColors.CarbonGray.copy(alpha=0.9f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            Text(
                text = "ELYSIUM TERMINAL v2.1 // SYSTEM READY",
                color = TitanColors.RadioactiveGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            HorizontalDivider(color = TitanColors.RadioactiveGreen, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(files) { file ->
            val color = if (file.isFolder) TitanColors.NeonCyan else file.thematicColor
            val prefix = if (file.isFolder) "DIR" else "FIL"

            SovereignLifeWrapper {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFileClick(file) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "> $ ",
                        color = TitanColors.RadioactiveGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )

                    // Icon Preview in Terminal Mode
                    Icon(
                        imageVector = if (file.isFolder) Icons.Default.Folder else com.elysium.vanguard.core.util.FileThematics.getCategoryIcon(file.category),
                        contentDescription = null,
                        tint = color.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp).padding(end = 4.dp)
                    )

                    Text(
                        text = "[$prefix] ",
                        color = TitanColors.NeonYellow,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Text(
                        text = file.name,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = file.size,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.clickable {
                            if (file.isFolder) onCalculateSize(file)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RenameDialog(
    file: TitanFile,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(file.name) }
    val accentColor = SectionColorManager.fileAccent

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TitanColors.AbsoluteBlack,
        title = { Text("RENAME PROTOCOL", color = accentColor, fontFamily = FontFamily.Monospace) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .neonGlass(cornerRadius = 16.dp, glowColor = accentColor.copy(alpha = 0.1f)),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = accentColor,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = accentColor,
                    unfocusedIndicatorColor = Color.White.copy(alpha = 0.1f)
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                colors = ButtonDefaults.buttonColors(containerColor = TitanColors.NeonCyan.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, TitanColors.NeonCyan)
            ) {
                Text("EXECUTE", color = TitanColors.NeonCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ABORT", color = Color.White.copy(alpha = 0.5f)) }
        }
    )
}

@Composable
fun FileDetailsDialog(
    file: TitanFile,
    onDismiss: () -> Unit
) {
    val accentColor = SectionColorManager.fileAccent

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TitanColors.AbsoluteBlack,
        title = { Text("FILE INTEL", color = accentColor, fontFamily = FontFamily.Monospace) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow("NAME", file.name)
                DetailRow("SIZE", file.size)
                DetailRow("TYPE", file.mimeType)
                DetailRow("PATH", file.path)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", color = TitanColors.NeonCyan) }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    val accentColor = SectionColorManager.fileAccent
    Column {
        Text(label, color = accentColor.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun TacticalHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon spacer
        Box(modifier = Modifier.size(40.dp))

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = "NAME / PERMS",
            modifier = Modifier.weight(1f),
            color = TitanColors.NeonCyan.copy(alpha = 0.5f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace
        )

        Text(
            text = "SIZE",
            color = TitanColors.NeonCyan.copy(alpha = 0.5f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun LoadingDiagnosticPulse(text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(TitanColors.NeonCyan.copy(alpha = 0.1f * alpha), CircleShape)
                .border(1.dp, TitanColors.NeonCyan.copy(alpha = 0.5f * alpha), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Refresh,
                null,
                tint = TitanColors.NeonCyan,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text,
            color = TitanColors.NeonCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.alpha(alpha)
        )
    }
}

@Composable
fun RadarScanningPlaceholder(mainText: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val scanAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            listOf(0.3f, 0.6f, 0.9f).forEach { radiusScale ->
                Box(
                    modifier = Modifier
                        .fillMaxSize(radiusScale)
                        .border(0.5.dp, TitanColors.NeonCyan.copy(alpha = 0.1f), CircleShape)
                )
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.25f to TitanColors.NeonCyan.copy(alpha = 0.2f),
                        0.5f to Color.Transparent,
                        center = center
                    ),
                    startAngle = scanAnim * 360f,
                    sweepAngle = 90f,
                    useCenter = true
                )
            }

            Icon(
                Icons.Default.Radar,
                null,
                tint = TitanColors.NeonCyan.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            mainText,
            color = TitanColors.NeonCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            "SYNCHRONIZING WITH STORAGE CLUSTER...",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun StoragePermissionOverlay(context: android.content.Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
            .background(TitanColors.DeepVoidGradient, RoundedCornerShape(16.dp), alpha = 0.9f)
            .border(1.dp, TitanColors.NeonRed.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Security,
            null,
            tint = TitanColors.NeonRed,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "STORAGE ACCESS DENIED",
            color = TitanColors.NeonRed,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "CORE PROTOCOLS REQUIRE ALL FILES ACCESS TO MANAGE SYSTEM DATA. IDENTITY VERIFICATION PENDING.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val intent = android.content.Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION").apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = TitanColors.NeonRed),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("AUTHORIZE ACCESS", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}
