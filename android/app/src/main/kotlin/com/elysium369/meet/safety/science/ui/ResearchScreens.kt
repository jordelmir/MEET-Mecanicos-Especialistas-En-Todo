package com.elysium369.meet.safety.science.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elysium369.meet.safety.science.data.*

// ═══════════════════════════════════════════════════════════════════
// §60 — Research Hub (nav tree)
//
// Safety → Research → Evidence Graph / Timeline / Claims /
//                     Entities / Hypotheses / Contradictions /
//                     Causality / Replications / Publications
// ═══════════════════════════════════════════════════════════════════

enum class ResearchSection(
    val title: String,
    val icon: ImageVector,
    val description: String,
) {
    ENTITIES("Entidades", Icons.Default.Person, "Personas, instituciones, documentos"),
    CLAIMS("Claims", Icons.Default.CheckCircle, "Proposiciones con estado epistémico"),
    EVENTS("Timeline", Icons.Default.DateRange, "Eventos con 4 timestamps"),
    HYPOTHESES("Hipótesis", Icons.Default.Lightbulb, "Propuestas y falsificación"),
    CONTRADICTIONS("Contradicciones", Icons.Default.Warning, "Evidencia contradictoria"),
    KNOWLEDGE("Conocimiento", Icons.Default.Info, "Quién sabía qué y cuándo"),
    REPLICATIONS("Replicaciones", Icons.Default.Refresh, "Reproducibilidad"),
    PUBLICATIONS("Publicaciones", Icons.Default.Description, "Research packages"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResearchHubScreen(
    onBack: () -> Unit,
    onNavigate: (ResearchSection) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔬 Research") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            // §78 — "No sabemos" disclaimer prominent at top
            item {
                EpistemicDisclaimer()
                Spacer(Modifier.height(8.dp))
            }

            items(ResearchSection.entries) { section ->
                ResearchSectionCard(
                    section = section,
                    onClick = { onNavigate(section) },
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// §78 — The system MUST be able to say "no sabemos"
//
// UNKNOWN / INSUFFICIENT EVIDENCE / DISPUTED / NOT VERIFIED
// are shown PROMINENTLY, not hidden behind menus.
// ═══════════════════════════════════════════════════════════════════

@Composable
fun EpistemicDisclaimer() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Principio epistémico",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Evidence ≠ Guilt · Claim ≠ Conviction · AI Output ≠ Fact\n" +
                    "Correlación ≠ Causalidad · Omisión ≠ Responsabilidad penal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun ResearchSectionCard(
    section: ResearchSection,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                section.icon,
                contentDescription = section.title,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    section.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// §61 — Entity List Screen
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityListScreen(
    entities: List<SciEntityEntity>,
    onBack: () -> Unit,
    onEntityClick: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entidades") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entities, key = { it.id }) { entity ->
                EntityCard(entity, onClick = { onEntityClick(entity.id) })
            }

            if (entities.isEmpty()) {
                item { EmptyState("No hay entidades registradas") }
            }
        }
    }
}

@Composable
private fun EntityCard(entity: SciEntityEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entity.canonicalName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    entity.entityType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AssertionStateBadge(entity.assertionState)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// §62 — Timeline Screen
//
// Must distinguish: EVENT TIME / KNOWLEDGE TIME / RECORD TIME /
//                    PUBLICATION TIME
// ═══════════════════════════════════════════════════════════════════

enum class TimelineMode(val label: String) {
    EVENT("Evento"),
    KNOWLEDGE("Conocimiento"),
    RECORDED("Registro"),
    PUBLISHED("Publicación"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    events: List<SciEventEntity>,
    onBack: () -> Unit,
) {
    var mode by remember { mutableStateOf(TimelineMode.EVENT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timeline") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Timeline mode selector
            ScrollableTabRow(
                selectedTabIndex = mode.ordinal,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TimelineMode.entries.forEach { m ->
                    Tab(
                        selected = mode == m,
                        onClick = { mode = m },
                        text = { Text(m.label) },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val sorted = events.sortedBy { sortKey(it, mode) }
                items(sorted, key = { it.id }) { event ->
                    TimelineEventCard(event, mode)
                }

                if (events.isEmpty()) {
                    item { EmptyState("No hay eventos registrados") }
                }
            }
        }
    }
}

private fun sortKey(event: SciEventEntity, mode: TimelineMode): Long =
    when (mode) {
        TimelineMode.EVENT -> event.occurredAt ?: event.recordedAt
        TimelineMode.KNOWLEDGE -> event.knownAt ?: event.recordedAt
        TimelineMode.RECORDED -> event.recordedAt
        TimelineMode.PUBLISHED -> event.publishedAt ?: Long.MAX_VALUE
    }

@Composable
private fun TimelineEventCard(event: SciEventEntity, mode: TimelineMode) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Timeline dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .offset(y = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                event.eventType,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val ts = sortKey(event, mode)
            if (ts != Long.MAX_VALUE) {
                Text(
                    formatTimestamp(ts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AssertionStateBadge(event.assertionState)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// §63 — Contradiction Screen
//
// §72 — FIRST show contradicting evidence, then supporting.
// Never hide contradictions.
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaimDetailScreen(
    claim: SciClaimEntity,
    supportingEvidence: List<SciClaimEvidenceEntity>,
    contradictingEvidence: List<SciClaimEvidenceEntity>,
    alternativeHypotheses: List<SciHypothesisEntity>,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Claim") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Proposition
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            claim.proposition,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssertionStateBadge(claim.assertionState)
                            CausalStatusBadge(claim.causalStatus)
                        }
                    }
                }
            }

            // §72 — CONTRADICTING evidence FIRST
            if (contradictingEvidence.isNotEmpty()) {
                item {
                    SectionHeader(
                        "⚠️ Evidencia contradictoria",
                        count = contradictingEvidence.size,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                items(contradictingEvidence) { ev ->
                    EvidenceChip(ev, isContradicting = true)
                }
            }

            // Supporting evidence second
            if (supportingEvidence.isNotEmpty()) {
                item {
                    SectionHeader(
                        "✅ Evidencia de soporte",
                        count = supportingEvidence.size,
                    )
                }
                items(supportingEvidence) { ev ->
                    EvidenceChip(ev, isContradicting = false)
                }
            }

            // Missing evidence / unknown
            if (supportingEvidence.isEmpty() && contradictingEvidence.isEmpty()) {
                item {
                    InsufficientEvidenceBanner()
                }
            }

            // Alternative explanations
            if (alternativeHypotheses.isNotEmpty()) {
                item {
                    SectionHeader(
                        "🔄 Hipótesis alternativas",
                        count = alternativeHypotheses.size,
                    )
                }
                items(alternativeHypotheses) { h ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                h.proposition,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                h.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// §78 — Prominent "insufficient evidence" banner
// ═══════════════════════════════════════════════════════════════════

@Composable
fun InsufficientEvidenceBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "EVIDENCIA INSUFICIENTE",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "No hay evidencia registrada para esta proposición. " +
                        "El sistema no puede evaluar la veracidad.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

// ── Shared Components ──────────────────────────────────────────────

@Composable
fun AssertionStateBadge(state: String) {
    val (color, text) = when (state) {
        "OBSERVED" -> Color(0xFF2196F3) to "Observado"
        "DOCUMENTED" -> Color(0xFF4CAF50) to "Documentado"
        "AUTHORITATIVE" -> Color(0xFF009688) to "Autoritativo"
        "CORROBORATED" -> Color(0xFF00BCD4) to "Corroborado"
        "DERIVED" -> Color(0xFF9C27B0) to "Derivado"
        "STATISTICALLY_SUPPORTED" -> Color(0xFF3F51B5) to "Estadístico"
        "CAUSALLY_SUPPORTED" -> Color(0xFF1A237E) to "Causal"
        "PEER_REVIEWED" -> Color(0xFF006064) to "Peer reviewed"
        "INDEPENDENTLY_REPLICATED" -> Color(0xFF1B5E20) to "Replicado"
        "DISPUTED" -> Color(0xFFFF9800) to "⚠ Disputado"
        "CONTRADICTED" -> Color(0xFFF44336) to "✗ Contradicho"
        "INSUFFICIENT_EVIDENCE" -> Color(0xFF795548) to "? Insuficiente"
        "UNKNOWN" -> Color(0xFF9E9E9E) to "? Desconocido"
        else -> Color(0xFF9E9E9E) to state
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

@Composable
private fun CausalStatusBadge(status: String) {
    val label = when (status) {
        "NOT_ASSESSED" -> "Causalidad: no evaluada"
        "TEMPORAL_ASSOCIATION" -> "Solo temporal"
        "CORRELATIONAL" -> "Correlacional"
        "MECHANISTIC_SUPPORT" -> "Mecanismo"
        "CAUSAL_INFERENCE" -> "Inferencia causal"
        "CAUSALLY_SUPPORTED" -> "Causal"
        "CONTRADICTED" -> "Contradicho"
        "UNKNOWN" -> "Desconocido"
        else -> status
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            color = color.copy(alpha = 0.15f),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                "$count",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

@Composable
private fun EvidenceChip(
    evidence: SciClaimEvidenceEntity,
    isContradicting: Boolean,
) {
    val color = if (isContradicting) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isContradicting) Icons.Default.Close else Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                evidence.evidenceId,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatTimestamp(epochMillis: Long): String {
    val instant = java.time.Instant.ofEpochMilli(epochMillis)
    val zdt = java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
    return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(zdt)
}

// ═══════════════════════════════════════════════════════════════════
// §63 — Claims List Screen
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaimsListScreen(
    claims: List<SciClaimEntity>,
    onBack: () -> Unit,
    onClaimClick: (SciClaimEntity) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Claims & Proposiciones") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(claims, key = { it.id }) { claim ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClaimClick(claim) },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                claim.predicate,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            AssertionStateBadge(claim.assertionState)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            claim.proposition,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CausalStatusBadge(claim.causalStatus)
                            Text(
                                claim.methodologyVersion,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (claims.isEmpty()) {
                item { EmptyState("No hay claims registrados") }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// §64 — Hypotheses List Screen
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HypothesesListScreen(
    hypotheses: List<SciHypothesisEntity>,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hipótesis Científicas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(hypotheses, key = { it.id }) { hyp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                hyp.status,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    hyp.methodologyVersion,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            hyp.proposition,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        if (hyp.falsificationCriteriaJson.isNotBlank() && hyp.falsificationCriteriaJson != "[]") {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Falsificación: ${hyp.falsificationCriteriaJson}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            if (hypotheses.isEmpty()) {
                item { EmptyState("No hay hipótesis formuladas") }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// §65 — Replications Screen
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplicationsScreen(
    replications: List<SciReplicationEntity>,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Replicaciones Independientes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(replications, key = { it.id }) { rep ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                rep.result,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (rep.result == "CONFIRMED" || rep.result == "SUCCESS") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                            Text(
                                formatTimestamp(rep.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Run original: ${rep.originalRunId.take(12)}...",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Replicador: ${rep.replicatorEntityId.take(12)}...",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (replications.isEmpty()) {
                item { EmptyState("No hay replicaciones registradas") }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// §66 — Publications Screen
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicationsScreen(
    publications: List<SciPublicationEntity>,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Publicaciones Científicas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(publications, key = { it.id }) { pub ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                pub.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    pub.status,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            pub.abstractText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                        )
                    }
                }
            }

            if (publications.isEmpty()) {
                item { EmptyState("No hay publicaciones disponibles") }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// §67 — Top-Level Container Screen
// ═══════════════════════════════════════════════════════════════════

@Composable
fun ResearchContainerScreen(
    onBack: () -> Unit,
    viewModel: ResearchViewModel = hiltViewModel(),
) {
    var currentSection by remember { mutableStateOf<ResearchSection?>(null) }
    var selectedClaim by remember { mutableStateOf<SciClaimEntity?>(null) }

    val entities by viewModel.entities.collectAsState()
    val claims by viewModel.claims.collectAsState()
    val events by viewModel.events.collectAsState()
    val hypotheses by viewModel.hypotheses.collectAsState()
    val replications by viewModel.replications.collectAsState()
    val publications by viewModel.publications.collectAsState()

    androidx.activity.compose.BackHandler(enabled = currentSection != null || selectedClaim != null) {
        if (selectedClaim != null) {
            selectedClaim = null
        } else {
            currentSection = null
        }
    }

    if (selectedClaim != null) {
        val claim = selectedClaim!!
        ClaimDetailScreen(
            claim = claim,
            supportingEvidence = emptyList(),
            contradictingEvidence = emptyList(),
            alternativeHypotheses = emptyList(),
            onBack = { selectedClaim = null },
        )
        return
    }

    when (val section = currentSection) {
        null -> {
            ResearchHubScreen(
                onBack = onBack,
                onNavigate = { currentSection = it },
            )
        }
        ResearchSection.ENTITIES -> {
            EntityListScreen(
                entities = entities,
                onBack = { currentSection = null },
                onEntityClick = { },
            )
        }
        ResearchSection.CLAIMS -> {
            ClaimsListScreen(
                claims = claims,
                onBack = { currentSection = null },
                onClaimClick = { selectedClaim = it },
            )
        }
        ResearchSection.EVENTS, ResearchSection.KNOWLEDGE -> {
            TimelineScreen(
                events = events,
                onBack = { currentSection = null },
            )
        }
        ResearchSection.HYPOTHESES, ResearchSection.CONTRADICTIONS -> {
            HypothesesListScreen(
                hypotheses = hypotheses,
                onBack = { currentSection = null },
            )
        }
        ResearchSection.REPLICATIONS -> {
            ReplicationsScreen(
                replications = replications,
                onBack = { currentSection = null },
            )
        }
        ResearchSection.PUBLICATIONS -> {
            PublicationsScreen(
                publications = publications,
                onBack = { currentSection = null },
            )
        }
    }
}

