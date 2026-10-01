package com.elysium.vanguard.features.filemanager

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.vanguard.core.ai.DownloadState
import com.elysium.vanguard.core.ai.MediaPipeManager
import com.elysium.vanguard.core.ai.ModelDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import android.content.BroadcastReceiver
import android.content.IntentFilter
import javax.inject.Inject

import android.app.Application

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    private val repository: FileManagerRepository,
    private val modelManager: ModelDownloadManager,
    private val mediaPipeManager: MediaPipeManager,
    private val app: Application
) : ViewModel() {
    private val context get() = app.applicationContext

    private val _currentPath = MutableStateFlow<String>(android.os.Environment.getExternalStorageDirectory().absolutePath)
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _uiState = MutableStateFlow<FileManagerUiState>(FileManagerUiState.Loading)
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    private val _files = MutableStateFlow<List<TitanFile>>(emptyList())
    val files: StateFlow<List<TitanFile>> = _files.asStateFlow()

    private val _aiState = MutableStateFlow<String>("") // Response text or empty
    val aiState: StateFlow<String> = _aiState.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _storageStats = MutableStateFlow<StorageStats?>(null)
    val storageStats: StateFlow<StorageStats?> = _storageStats.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedFiles: StateFlow<Set<String>> = _selectedFiles.asStateFlow()

    private val _operationProgress = MutableStateFlow<Float?>(null)
    val operationProgress: StateFlow<Float?> = _operationProgress.asStateFlow()

    private val _events = MutableSharedFlow<FileManagerEvent>()
    val events: SharedFlow<FileManagerEvent> = _events.asSharedFlow()

    private val _pendingOperation = MutableStateFlow<PendingFileOperation?>(null)
    val pendingOperation: StateFlow<PendingFileOperation?> = _pendingOperation.asStateFlow()

    private val _commonShortcuts = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val commonShortcuts: StateFlow<List<Pair<String, String>>> = _commonShortcuts.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(false)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _compressionProgress = MutableStateFlow<CompressionState?>(null)
    val compressionProgress: StateFlow<CompressionState?> = _compressionProgress.asStateFlow()

    private val _viewMode = MutableStateFlow(FileViewMode.TACTICAL) // Default to Tactical as requested
    val viewMode: StateFlow<FileViewMode> = _viewMode.asStateFlow()

    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
    }

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val percentage = intent?.getIntExtra("percentage", 0) ?: 0
            val currentFile = intent?.getStringExtra("currentFile") ?: ""
            val done = intent?.getBooleanExtra("done", false) ?: false

            if (done) {
                _compressionProgress.value = CompressionState(100, "COMPLETE", CompressionStatus.SUCCESS)
                updateStorageStats()
                loadDirectory(_currentPath.value)

                // Auto-dismiss after 2 seconds
                viewModelScope.launch {
                    kotlinx.coroutines.delay(2000)
                    if (_compressionProgress.value?.status == CompressionStatus.SUCCESS) {
                        _compressionProgress.value = null
                    }
                }
            } else {
                _compressionProgress.value = CompressionState(percentage, currentFile, CompressionStatus.RUNNING)
            }
        }
    }

    init {
        checkPermissionsAndLoad()
        updateStorageStats()
        loadShortcuts()

        try {
            ContextCompat.registerReceiver(
                context,
                progressReceiver,
                IntentFilter("com.elysium.vanguard.COMPRESSION_PROGRESS"),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(progressReceiver)
        } catch (_: Exception) {}
    }

    private fun loadShortcuts() {
        _commonShortcuts.value = repository.getCommonShortcuts()
    }

    fun updateStorageStats() {
        viewModelScope.launch {
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun checkPermissionsAndLoad() {
        val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true // Legacy handling usually done at Activity level
        }

        if (hasPermission) {
            loadDirectory(_currentPath.value)
        } else {
            _uiState.value = FileManagerUiState.PermissionRequired
        }
    }

    fun loadDirectory(path: String) {
        if (path.isEmpty()) return
        _currentPath.value = path
        _selectedFiles.value = emptySet() // Clear selection on navigate

        _uiState.value = FileManagerUiState.Loading

        viewModelScope.launch {
            try {
                repository.getFiles(path).collect { list ->
                    _files.value = list
                    _uiState.value = if (list.isEmpty()) {
                        FileManagerUiState.Empty
                    } else {
                        FileManagerUiState.Success(list)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = FileManagerUiState.Error(e.message ?: "Unknown Error")
            }
        }
    }

    fun navigateUp() {
        val current = _currentPath.value
        if (current == android.os.Environment.getExternalStorageDirectory().absolutePath || current == "/") {
            // At root, let the screen handle the exit (to Dashboard)
            return
        }
        val parent = java.io.File(current).parent ?: return
        loadDirectory(parent)
    }

    fun toggleSelection(path: String) {
        val current = _selectedFiles.value.toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
        } else {
            current.add(path)
        }
        _selectedFiles.value = current
    }

    fun toggleKeepScreenOn() {
        _keepScreenOn.value = !_keepScreenOn.value
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == FileViewMode.TACTICAL) FileViewMode.AESTHETIC else FileViewMode.TACTICAL
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
    }

    /**
     * INDUSTRIAL OPERATIONS
     */
    fun deleteSelected() {
        viewModelScope.launch {
            val toDelete = _selectedFiles.value.toList()
            toDelete.forEach { path ->
                repository.deleteFile(path)
            }
            clearSelection()
            loadDirectory(_currentPath.value)
            updateStorageStats()
        }
    }

    fun copySelected(paths: List<String> = emptyList()) {
        val finalPaths = if (paths.isNotEmpty()) paths else _selectedFiles.value.toList()
        if (finalPaths.isEmpty()) return

        _pendingOperation.value = PendingFileOperation(
            sourcePaths = finalPaths,
            type = FileOperationType.COPY
        )
        clearSelection()
    }

    fun moveSelected(paths: List<String> = emptyList()) {
        val finalPaths = if (paths.isNotEmpty()) paths else _selectedFiles.value.toList()
        if (finalPaths.isEmpty()) return

        _pendingOperation.value = PendingFileOperation(
            sourcePaths = finalPaths,
            type = FileOperationType.MOVE
        )
        clearSelection()
    }

    fun cancelPendingOperation() {
        _pendingOperation.value = null
    }

    fun pasteSelected() {
        val op = _pendingOperation.value ?: return
        val targetDir = _currentPath.value

        viewModelScope.launch {
            _operationProgress.value = 0f
            val total = op.sourcePaths.size
            op.sourcePaths.forEachIndexed { index, path ->
                val file = java.io.File(path)
                val dest = java.io.File(targetDir, file.name)

                when (op.type) {
                    FileOperationType.COPY -> repository.copyFile(path, dest.absolutePath)
                    FileOperationType.MOVE -> repository.moveFile(path, dest.absolutePath)
                }
                _operationProgress.value = (index + 1).toFloat() / total
            }
            _operationProgress.value = null
            _pendingOperation.value = null
            loadDirectory(targetDir)
            updateStorageStats()
        }
    }

    fun renameFile(path: String, newName: String) {
        viewModelScope.launch {
            if (repository.renameFile(path, newName)) {
                loadDirectory(_currentPath.value)
            }
        }
    }

    fun deleteFile(path: String) {
        viewModelScope.launch {
            if (repository.deleteFile(path)) {
                loadDirectory(_currentPath.value)
                updateStorageStats()
            }
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            modelManager.downloadModel(context).collect {
                _downloadState.value = it
            }
        }
    }

    fun performSemanticSearch(query: String) {
        viewModelScope.launch {
            _aiState.value = "Thinking..."
            val contextData = _files.value.take(100).joinToString("\n") { "${it.name} (${it.size})" }
            val response = mediaPipeManager.performSemanticSearch(query, contextData)
            _aiState.value = response
        }
    }

    fun openFile(file: TitanFile) {
        viewModelScope.launch {
            _events.emit(FileManagerEvent.OpenFile(file))
        }
    }

    fun shareFile(file: TitanFile) {
        viewModelScope.launch {
            _events.emit(FileManagerEvent.ShareFile(file))
        }
    }

    fun calculateRecursiveSize(file: TitanFile) {
        viewModelScope.launch {
            val size = repository.getFolderSizeRecursive(java.io.File(file.path))
            val formattedSize = android.text.format.Formatter.formatFileSize(context, size)

            // Update the file in the list with the new size
            _files.value = _files.value.map {
                if (it.path == file.path) it.copy(size = formattedSize) else it
            }
        }
    }

    fun compressFiles(files: List<File>, outputFile: File) {
        val intent = Intent(context, com.elysium.vanguard.core.services.CompressionService::class.java).apply {
            action = com.elysium.vanguard.core.services.CompressionService.ACTION_COMPRESS
            putExtra(com.elysium.vanguard.core.services.CompressionService.EXTRA_FILES, files.map { it.absolutePath }.toTypedArray())
            putExtra(com.elysium.vanguard.core.services.CompressionService.EXTRA_OUTPUT, outputFile.absolutePath)
            putExtra(com.elysium.vanguard.core.services.CompressionService.EXTRA_KEEP_SCREEN_ON, _keepScreenOn.value)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun decompressFile(zipFile: File, outputDir: File) {
        val intent = Intent(context, com.elysium.vanguard.core.services.CompressionService::class.java).apply {
            action = com.elysium.vanguard.core.services.CompressionService.ACTION_DECOMPRESS
            putExtra(com.elysium.vanguard.core.services.CompressionService.EXTRA_FILES, arrayOf(zipFile.absolutePath))
            putExtra(com.elysium.vanguard.core.services.CompressionService.EXTRA_OUTPUT, outputDir.absolutePath)
            putExtra(com.elysium.vanguard.core.services.CompressionService.EXTRA_KEEP_SCREEN_ON, _keepScreenOn.value)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        _compressionProgress.value = CompressionState(0, "Initializing...", CompressionStatus.RUNNING)
    }

    fun cancelCompression() {
        val intent = Intent(context, com.elysium.vanguard.core.services.CompressionService::class.java).apply {
            action = com.elysium.vanguard.core.services.CompressionService.ACTION_CANCEL
        }
        context.startService(intent)
        _compressionProgress.value = null
    }

    fun dismissCompressionDialog() {
        _compressionProgress.value = null
    }

    fun decompressSelected(zipFile: File) {
        val targetDir = File(zipFile.parent, zipFile.nameWithoutExtension)
        decompressFile(zipFile, targetDir)
    }
}

enum class FileViewMode {
    AESTHETIC, TACTICAL
}

data class CompressionState(
    val percentage: Int,
    val currentFile: String,
    val status: CompressionStatus = CompressionStatus.RUNNING
)

enum class CompressionStatus {
    RUNNING, SUCCESS
}

sealed class FileManagerEvent {
    data class OpenFile(val file: TitanFile) : FileManagerEvent()
    data class ShareFile(val file: TitanFile) : FileManagerEvent()
}

data class PendingFileOperation(
    val sourcePaths: List<String>,
    val type: FileOperationType
)

enum class FileOperationType {
    COPY, MOVE
}
