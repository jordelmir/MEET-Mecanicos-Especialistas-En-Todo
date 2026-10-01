package com.elysium.vanguard.core.undo

import com.elysium.vanguard.core.trash.TrashRepository
import com.elysium.vanguard.core.trash.TrashSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PHASE 1.4 — In-memory undo stack for reversible file operations.
 *
 * The stack is intentionally narrow:
 *   - bounded to 50 entries (older ops drop off; matches Total Commander behaviour).
 *   - only operations that can be cleanly reversed are recorded.
 *   - storage stays in memory only; the trash manifest is already persisted by
 *     TrashRepository so we don't need to also persist the undo history.
 *
 * Threading: all mutations happen on Dispatchers.Main via the calling ViewModel.
 * Reads from [UndoStack.events] are safe from any dispatcher because StateFlow
 * is thread-safe.
 */
@Singleton
class UndoStack @Inject constructor(
    private val trashRepository: TrashRepository
) {

    sealed interface UndoableEvent {
        val timestampMs: Long

        data class Delete(
            override val timestampMs: Long,
            val trashId: Long,
            val displayName: String
        ) : UndoableEvent

        data class BatchDelete(
            override val timestampMs: Long,
            val trashIds: List<Long>,
            val totalCount: Int
        ) : UndoableEvent
    }

    private val maxSize = 50
    private val deque: ArrayDeque<UndoableEvent> = ArrayDeque()

    private val _events = MutableStateFlow<List<UndoableEvent>>(emptyList())
    val events: StateFlow<List<UndoableEvent>> = _events.asStateFlow()

    fun push(event: UndoableEvent) {
        deque.addFirst(event)
        while (deque.size > maxSize) deque.removeLast()
        _events.value = deque.toList()
    }

    fun latest(): UndoableEvent? = deque.firstOrNull()

    /**
     * Reverse the most recent undoable event. Returns true if an undo was
     * actually executed (false when the stack was empty or the operation failed).
     *
     * The caller is responsible for showing user feedback; this method is silent
     * and only mutates state.
     */
    suspend fun undoLatest(): Boolean {
        val event = deque.removeFirstOrNull() ?: return false
        _events.value = deque.toList()
        return when (event) {
            is UndoableEvent.Delete -> {
                val item = trashRepository.restoreById(event.trashId)
                item != null
            }
            is UndoableEvent.BatchDelete -> {
                var allOk = true
                for (id in event.trashIds) {
                    val restored = trashRepository.restoreById(id)
                    if (restored == null) allOk = false
                }
                allOk
            }
        }
    }

    fun clear() {
        deque.clear()
        _events.value = emptyList()
    }
}
