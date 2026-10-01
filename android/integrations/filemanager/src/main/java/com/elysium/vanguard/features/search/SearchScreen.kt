package com.elysium.vanguard.features.search

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elysium.vanguard.ui.theme.TitanColors

/**
 * PHASE 1.10 — Search screen with combined fuzzy + filter input.
 *
 * The user types freely; tokens matching `ext:`, `size:`, `type:` are parsed
 * as filters. Anything else becomes the fuzzy needle (typo-tolerant).
 *
 * Matched characters in the result rows are highlighted in cyan so the user
 * can see why a fuzzy match scored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onResultClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val initialRoot = remember { Environment.getExternalStorageDirectory() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SearchBar(
                query = state.query,
                onQueryChange = { viewModel.setQuery(it, listOf(initialRoot)) },
                onClear = { viewModel.clear() },
                onSubmit = { keyboard?.hide() }
            )

            StatsRow(
                count = state.results.size,
                scanned = state.scannedFiles,
                elapsedMs = state.elapsedMs,
                isSearching = state.isSearching
            )

            if (state.errorMessage != null) {
                ErrorBanner(state.errorMessage!!)
            }

            if (state.results.isEmpty() && !state.isSearching && state.query.isNotBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No matches", color = Color.White.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.results, key = { it.file.absolutePath }) { row ->
                        ResultRowView(row = row, onClick = { onResultClick(row.path) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = {
            Text(
                "Search — try `pdf size:>5MB type:doc`",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp
            )
        },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TitanColors.NeonCyan) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = Color.White)
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = TitanColors.NeonCyan,
            focusedBorderColor = TitanColors.NeonCyan,
            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
        )
    )
}

@Composable
private fun StatsRow(count: Int, scanned: Int, elapsedMs: Long, isSearching: Boolean) {
    if (count == 0 && scanned == 0 && !isSearching) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSearching) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = TitanColors.NeonCyan
            )
            Spacer(Modifier.width(8.dp))
            Text("Searching… $scanned files", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        } else {
            Text(
                "$count results · scanned $scanned files in ${elapsedMs}ms",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = TitanColors.QuantumPink.copy(alpha = 0.2f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            message,
            color = TitanColors.QuantumPink,
            fontSize = 12.sp,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
private fun ResultRowView(row: SearchViewModel.ResultRow, onClick: () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                buildHighlightedName(row.displayName, row.matchedIndices),
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                row.path,
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildHighlightedName(name: String, indices: IntArray): AnnotatedString = buildAnnotatedString {
    val set = indices.toSet()
    name.forEachIndexed { idx, ch ->
        if (idx in set) {
            withStyle(SpanStyle(color = TitanColors.NeonCyan, fontWeight = FontWeight.Bold)) {
                append(ch)
            }
        } else {
            withStyle(SpanStyle(color = Color.White)) {
                append(ch)
            }
        }
    }
}
