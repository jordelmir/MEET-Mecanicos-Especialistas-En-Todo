package com.elysium.vanguard.features.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.vanguard.core.database.TrashEntity
import com.elysium.vanguard.core.trash.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PHASE 1.5 — UI state for the trash screen.
 */
data class TrashUiState(
    val items: List<TrashEntity> = emptyList(),
    val totalBytes: Long = 0L,
    val isLoading: Boolean = true,
    val lastRestoredId: Long? = null,
    val lastPurgedId: Long? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val trashRepository: TrashRepository
) : ViewModel() {

    private val _local = MutableStateFlow(LocalState())
    private data class LocalState(
        val lastRestoredId: Long? = null,
        val lastPurgedId: Long? = null,
        val errorMessage: String? = null
    )

    val state: StateFlow<TrashUiState> = combine(
        trashRepository.observeTrash(),
        _local
    ) { items, local ->
        TrashUiState(
            items = items,
            totalBytes = items.sumOf { it.sizeBytes },
            isLoading = false,
            lastRestoredId = local.lastRestoredId,
            lastPurgedId = local.lastPurgedId,
            errorMessage = local.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TrashUiState()
    )

    fun restore(item: TrashEntity) {
        viewModelScope.launch {
            val ok = trashRepository.restore(item)
            if (ok) _local.value = _local.value.copy(lastRestoredId = item.id, errorMessage = null)
            else _local.value = _local.value.copy(errorMessage = "Could not restore ${item.originalName}")
        }
    }

    fun purge(item: TrashEntity) {
        viewModelScope.launch {
            val ok = trashRepository.purge(item)
            if (ok) _local.value = _local.value.copy(lastPurgedId = item.id, errorMessage = null)
            else _local.value = _local.value.copy(errorMessage = "Could not delete ${item.originalName}")
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            trashRepository.purgeAll()
        }
    }

    fun consumeError() {
        _local.value = _local.value.copy(errorMessage = null)
    }
}
