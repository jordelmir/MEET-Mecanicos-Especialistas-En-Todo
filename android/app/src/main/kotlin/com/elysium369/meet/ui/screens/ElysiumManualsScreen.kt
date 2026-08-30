package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.navigation.backOrHome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.catalog.ProprietaryCatalogManifest
import com.elysium369.meet.core.catalog.ProprietaryCatalogSection
import com.elysium369.meet.core.catalog.ProprietaryPartsCatalogRepository
import com.elysium369.meet.core.catalog.ProprietarySectionShard
import com.elysium369.meet.core.catalog.ProprietarySourceBlock
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ElysiumManualsScreen(navController: NavController) {
    val context = LocalContext.current
    val repository = remember(context) { ProprietaryPartsCatalogRepository(context) }
    var manifest by remember { mutableStateOf<ProprietaryCatalogManifest?>(null) }
    var selectedDocumentId by remember { mutableStateOf("document_16") }
    var selectedSection by remember { mutableStateOf<ProprietaryCatalogSection?>(null) }
    var shard by remember { mutableStateOf<ProprietarySectionShard?>(null) }
    var sectionQuery by remember { mutableStateOf("") }
    var showSectionIndex by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repository) {
        runCatching { withContext(Dispatchers.IO) { repository.loadManifest() } }
            .onSuccess { manifest = it }
            .onFailure { loadError = it.message ?: "No fue posible abrir el corpus local." }
    }

    val sections = remember(manifest, selectedDocumentId, sectionQuery) {
        manifest?.sections.orEmpty()
            .asSequence()
            .filter { it.sourceDocumentId == selectedDocumentId }
            .filter { sectionQuery.isBlank() || it.titleOriginal.contains(sectionQuery, ignoreCase = true) }
            .sortedBy { it.sourceOrderStart }
            .toList()
    }

    LaunchedEffect(manifest, selectedDocumentId) {
        selectedSection = manifest?.sections.orEmpty()
            .filter { it.sourceDocumentId == selectedDocumentId }
            .minByOrNull { it.sourceOrderStart }
        showSectionIndex = true
    }

    LaunchedEffect(selectedSection?.id) {
        val section = selectedSection ?: return@LaunchedEffect
        shard = null
        loadError = null
        runCatching { withContext(Dispatchers.IO) { repository.loadSection(section.shardPath) } }
            .onSuccess { shard = it }
            .onFailure { loadError = it.message ?: "No fue posible abrir esta sección." }
    }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.backOrHome() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Manuales de Elysium", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("Corpus propietario literal · lectura local", color = MeetColors.cyberCyan, fontSize = 11.sp)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val loadedManifest = manifest
            if (loadedManifest == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (loadError == null) CircularProgressIndicator(color = MeetColors.cyberCyan)
                    else Text(loadError.orEmpty(), color = MeetColors.error)
                }
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                loadedManifest.sourceDocuments.forEach { document ->
                    val selected = document.id == selectedDocumentId
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) MeetColors.cyberCyan.copy(alpha = 0.18f) else MeetColors.cardBackground)
                            .border(1.dp, if (selected) MeetColors.cyberCyan else MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                            .clickable { selectedDocumentId = document.id }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(document.sourceFileName, color = if (selected) MeetColors.cyberCyan else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("${document.blockCount} bloques", color = MeetColors.textSecondary, fontSize = 9.sp)
                    }
                }
            }

            selectedSection?.let { section ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(section.titleOriginal, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Orden ${section.sourceOrderStart}-${section.sourceOrderEnd} · ${section.blockCount} bloques", color = MeetColors.textSecondary, fontSize = 9.sp)
                    }
                    Text(
                        if (showSectionIndex) "LEER" else "ÍNDICE",
                        color = MeetColors.neonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, MeetColors.neonGreen, RoundedCornerShape(6.dp))
                            .clickable { showSectionIndex = !showSectionIndex }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            if (showSectionIndex) {
                SectionIndex(
                    query = sectionQuery,
                    onQueryChanged = { sectionQuery = it },
                    sections = sections,
                    selectedId = selectedSection?.id,
                    onSelected = { selectedSection = it; showSectionIndex = false }
                )
            } else {
                LiteralSectionReader(
                    documentHash = loadedManifest.sourceDocuments.first { it.id == selectedDocumentId }.sourceSha256,
                    shard = shard,
                    error = loadError
                )
            }
        }
    }
}

@Composable
private fun SectionIndex(
    query: String,
    onQueryChanged: (String) -> Unit,
    sections: List<ProprietaryCatalogSection>,
    selectedId: String?,
    onSelected: (ProprietaryCatalogSection) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp)).background(MeetColors.cardBackground)
            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = MeetColors.cyberCyan)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChanged,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isBlank()) Text("Buscar sección literal...", color = MeetColors.textMuted, fontSize = 13.sp)
                inner()
            }
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(sections, key = { it.id }) { section ->
            val selected = section.id == selectedId
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp))
                    .background(if (selected) MeetColors.electricBlue.copy(alpha = 0.12f) else MeetColors.cardBackground)
                    .border(1.dp, if (selected) MeetColors.electricBlue else MeetColors.borderSubtle, RoundedCornerShape(7.dp))
                    .clickable { onSelected(section) }.padding(10.dp)
            ) {
                Text(section.titleOriginal, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("${section.blockCount} bloques · fuente ${section.sourceOrderStart}-${section.sourceOrderEnd}", color = MeetColors.textSecondary, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun LiteralSectionReader(documentHash: String, shard: ProprietarySectionShard?, error: String?) {
    if (shard == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = MeetColors.cyberCyan) else Text(error, color = MeetColors.error)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "provenance") {
            Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(6.dp)).padding(9.dp)) {
                Text(shard.sourceFileName, color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("SHA-256 $documentHash", color = MeetColors.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
                Text("Contenido mostrado sin traducción, resumen ni reescritura.", color = MeetColors.neonGreen, fontSize = 9.sp)
            }
        }
        items(shard.blocks, key = { it.blockId }) { block -> LiteralBlock(block) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LiteralBlock(block: ProprietarySourceBlock) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("#${block.order} · ${block.kind}", color = MeetColors.textMuted, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
        if (block.rows != null) {
            block.rows.forEach { row ->
                Row(Modifier.fillMaxWidth().border(0.5.dp, MeetColors.borderSubtle).padding(6.dp)) {
                    row.forEach { cell ->
                        Text(cell, color = Color.White, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f).padding(horizontal = 3.dp))
                    }
                }
            }
        } else {
            Text(
                text = block.text,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}
