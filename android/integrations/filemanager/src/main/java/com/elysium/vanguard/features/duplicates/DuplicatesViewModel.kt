package com.elysium.vanguard.features.duplicates

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.vanguard.core.duplicates.DuplicatesDetector
import com.elysium.vanguard.core.trash.TrashRepository
import com.elysium.vanguard.core.trash.TrashSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * PHASE 1.12 — UI for the duplicates detector.
 */
@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val detector: DuplicatesDetector,
    private val trashRepository: TrashRepository
) : ViewModel() {

    data class UiState(
        val isScanning: Boolean = false,
        val groups: List<DuplicatesDetector.DuplicateGroup> = emptyList(),
        val totalWastedBytes: Long = 0L,
        val filesScanned: Int = 0,
        val selection: Set<String> = emptySet(),
        val errorMessage: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        if (_state.value.isScanning) return
        scanJob?.cancel()
        _state.value = UiState(isScanning = true)
        scanJob = viewModelScope.launch {
            try {
                val roots = listOf(Environment.getExternalStorageDirectory())
                val groups = detector.findDuplicates(roots) { progress ->
                    _state.value = _state.value.copy(filesScanned = progress.filesScanned)
                }
                _state.value = UiState(
                    isScanning = false,
                    groups = groups,
                    totalWastedBytes = groups.sumOf { it.wastedBytes },
                    filesScanned = _state.value.filesScanned
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isScanning = false,
                    errorMessage = t.message ?: "Scan failed"
                )
            }
        }
    }

    fun toggleSelection(file: File) {
        val key = file.absolutePath
        val current = _state.value.selection.toMutableSet()
        if (current.contains(key)) current -= key else current += key
        _state.value = _state.value.copy(selection = current)
    }

    /**
     * Smart-select: keep the OLDEST file in each group and mark the others
     * for deletion. Rationale: the oldest is most likely the original.
     */
    fun smartSelectKeepOldest() {
        val selected = mutableSetOf<String>()
        for (group in _state.value.groups) {
            // Skip the first (oldest by lastModified); mark the rest.
            val sorted = group.files.sortedBy { it.lastModified() }
            sorted.drop(1).forEach { selected += it.absolutePath }
        }
        _state.value = _state.value.copy(selection = selected)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selection = emptySet())
    }

    /**
     * Send every selected file to trash. Returns the number moved.
     */
    fun trashSelection(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val files = _state.value.selection.map { File(it) }
            var moved = 0
            for (file in files) {
                if (!file.exists()) continue
                val parentId = file.parent ?: continue
                val id = trashRepository.moveToTrash(
                    TrashSource.FromFile(file, parentId)
                )
                if (id > 0) moved++
            }
            // Refresh scan to reflect the deletions.
            clearSelection()
            startScan()
            onComplete(moved)
        }
    }
}
