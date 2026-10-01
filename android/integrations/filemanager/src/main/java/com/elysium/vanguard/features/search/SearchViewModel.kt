package com.elysium.vanguard.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.vanguard.core.search.FileFilterParser
import com.elysium.vanguard.core.search.FuzzySearchEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * PHASE 1.10 — Search VM driving the global search bar.
 *
 * Combines [FuzzySearchEngine] (typo tolerance) with [FileFilterParser] (typed
 * filters) and walks the file tree on background dispatchers. Cancellation is
 * supported: every new search cancels the previous job so the user can keep
 * typing without flooding the UI.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val fuzzy: FuzzySearchEngine,
    private val filterParser: FileFilterParser
) : ViewModel() {

    data class ResultRow(
        val file: File,
        val displayName: String,
        val path: String,
        val sizeBytes: Long,
        val score: Double,
        val matchedIndices: IntArray
    ) {
        override fun equals(other: Any?): Boolean = other is ResultRow && other.file == file
        override fun hashCode(): Int = file.hashCode()
    }

    data class UiState(
        val query: String = "",
        val results: List<ResultRow> = emptyList(),
        val isSearching: Boolean = false,
        val scannedFiles: Int = 0,
        val elapsedMs: Long = 0L,
        val errorMessage: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    /**
     * Update the query and trigger a debounced search across [roots].
     * Cancellation: a previous search is cancelled when a new query arrives.
     */
    fun setQuery(query: String, roots: List<File>) {
        searchJob?.cancel()
        _state.value = _state.value.copy(query = query, errorMessage = null)
        if (query.isBlank()) {
            _state.value = UiState(query = query)
            return
        }
        searchJob = viewModelScope.launch {
            // Debounce: 200 ms is enough to feel live without burning CPU.
            delay(200)
            _state.value = _state.value.copy(isSearching = true)
            val started = System.currentTimeMillis()
            try {
                val filter = filterParser.parse(query)
                val candidates = withContext(Dispatchers.IO) {
                    scanFiles(roots, maxDepth = 6, maxFiles = 50_000) { scanned ->
                        // Emit progress periodically.
                        if (scanned % 1000 == 0) {
                            _state.value = _state.value.copy(scannedFiles = scanned)
                        }
                    }
                }
                val filtered = withContext(Dispatchers.Default) {
                    candidates
                        .asSequence()
                        .filter { filterParser.matches(it, filter) }
                        .toList()
                }
                val scored = withContext(Dispatchers.Default) {
                    // Use the first non-filter token as the fuzzy needle.
                    val needle = filter.nameContains ?: query
                    if (filter.extensions.isNotEmpty() || filter.minSize != null ||
                        filter.maxSize != null || filter.type != FileFilterParser.TypeFilter.ANY) {
                        // Pure filter mode — no fuzzy ranking.
                        filtered.take(200).map {
                            ResultRow(it, it.name, it.absolutePath, it.length(), 1.0, IntArray(0))
                        }
                    } else {
                        fuzzy.search(needle, filtered.map { it.name }, limit = 200).mapNotNull { sc ->
                            val orig = filtered.firstOrNull { it.name == sc.candidate } ?: return@mapNotNull null
                            ResultRow(
                                file = orig,
                                displayName = orig.name,
                                path = orig.absolutePath,
                                sizeBytes = orig.length(),
                                score = sc.score,
                                matchedIndices = sc.matchedIndices
                            )
                        }
                    }
                }
                _state.value = UiState(
                    query = query,
                    results = scored,
                    isSearching = false,
                    scannedFiles = candidates.size,
                    elapsedMs = System.currentTimeMillis() - started
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isSearching = false,
                    errorMessage = t.message ?: "Search failed"
                )
            }
        }
    }

    fun clear() {
        searchJob?.cancel()
        _state.value = UiState()
    }

    /**
     * Walks the file tree from each root, collecting files up to [maxDepth]
     * and [maxFiles]. Symlinks are not followed (avoid infinite loops).
     */
    private fun scanFiles(
        roots: List<File>,
        maxDepth: Int,
        maxFiles: Int,
        onProgress: (Int) -> Unit
    ): List<File> {
        val out = ArrayList<File>(1024)
        for (root in roots) {
            if (!root.exists()) continue
            walk(root, 0, maxDepth, out, maxFiles, onProgress)
            if (out.size >= maxFiles) break
        }
        return out
    }

    private fun walk(
        dir: File,
        depth: Int,
        maxDepth: Int,
        out: MutableList<File>,
        maxFiles: Int,
        onProgress: (Int) -> Unit
    ) {
        if (depth > maxDepth || out.size >= maxFiles) return
        val children = try {
            dir.listFiles() ?: return
        } catch (_: SecurityException) {
            return
        }
        for (child in children) {
            if (out.size >= maxFiles) return
            // Skip symlinks to avoid cycles.
            if (java.nio.file.Files.isSymbolicLink(child.toPath())) continue
            if (child.isDirectory) {
                walk(child, depth + 1, maxDepth, out, maxFiles, onProgress)
            } else {
                out += child
                if (out.size % 1000 == 0) onProgress(out.size)
            }
        }
    }
}
