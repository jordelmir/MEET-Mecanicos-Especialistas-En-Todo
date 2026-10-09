package com.elysium369.meet.safety.ui.report

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.elysium369.meet.R
import com.elysium369.meet.safety.evidence.SafetyEvidenceEntity
import com.elysium369.meet.safety.ui.common.SafetyCategoryIcons
import com.elysium369.meet.safety.ui.common.SafetyEmptyState
import com.elysium369.meet.safety.ui.common.SafetyShimmer
import com.elysium369.meet.ui.theme.MeetColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyMyReportsScreen(
    viewModel: SafetyMyReportsViewModel,
    onBack: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.state.collectAsState()
    var reportToWithdraw by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.safety_my_reports_title),
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = MeetColors.textPrimary,
                        )
                        Text(
                            stringResource(R.string.safety_my_reports_subtitle, state.totalReports, state.pendingCount),
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.safety_back),
                            tint = MeetColors.textPrimary,
                        )
                    }
                },
                actions = {
                    if (state.pendingCount > 0) {
                        IconButton(onClick = { viewModel.retrySyncAll() }) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Reintentar sincronización",
                                tint = MeetColors.cyberCyan,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.reports.isEmpty() -> {
                SafetyShimmer(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(top = 16.dp),
                )
            }

            state.error != null && state.reports.isEmpty() -> {
                SafetyEmptyState(
                    icon = Icons.Filled.Assignment,
                    title = stringResource(R.string.safety_error),
                    message = state.error!!,
                    modifier = Modifier.padding(padding),
                )
            }

            state.reports.isEmpty() -> {
                SafetyEmptyState(
                    icon = Icons.Filled.Assignment,
                    title = stringResource(R.string.safety_my_reports_empty_title),
                    message = stringResource(R.string.safety_my_reports_empty_message),
                    modifier = Modifier.padding(padding),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    item {
                        ReportsCommandDeck(
                            totalCount = state.totalReports,
                            pendingCount = state.pendingCount,
                            failedCount = state.reports.count { it.syncState == "FAILED" },
                        )
                    }

                    // Action message banner
                    state.actionMessage?.let { message ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MeetColors.neonGreen.copy(alpha = 0.15f)),
                            ) {
                                Text(
                                    message,
                                    modifier = Modifier.padding(12.dp),
                                    color = MeetColors.neonGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    items(state.reports, key = { it.reportId }) { report ->
                        val evidence = state.evidenceByReport[report.reportId] ?: emptyList()
                        val privatePoint = state.privatePointsByReport[report.reportId]
                        val narrativeUrls = remember(privatePoint?.narrative) {
                            privatePoint?.narrative?.let { nar ->
                                Regex("""(https?://[^\s]+)""").findAll(nar).map { it.value.trimEnd('.', ',', ';', ')', ']', '>') }.toList()
                            } ?: emptyList()
                        }
                        val videoUrls = ((privatePoint?.videoUrls ?: emptyList()) + narrativeUrls).filter { it.isNotBlank() }.distinct()
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically { it / 4 },
                        ) {
                            MyReportCard(
                                report = report,
                                evidence = evidence,
                                videoUrls = videoUrls,
                                withdrawing = state.withdrawingReportId == report.reportId,
                                onWithdraw = { reportToWithdraw = report.reportId },
                                onRetrySync = { viewModel.retrySyncAll() },
                                onOpenEvidence = { item -> viewModel.openEvidence(context, item.evidenceId) },
                                onOpenVideo = { url ->
                                    try {
                                        val uri = Uri.parse(url)
                                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        try {
                                            val uri = Uri.parse(url)
                                            val chooser = Intent.createChooser(
                                                Intent(Intent.ACTION_VIEW, uri).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                },
                                                "Ver video"
                                            )
                                            context.startActivity(chooser)
                                        } catch (e: Exception) {
                                            android.util.Log.e("SafetyMyReports", "Error opening video $url", e)
                                        }
                                    }
                                },
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    reportToWithdraw?.let { reportId ->
        AlertDialog(
            onDismissRequest = { reportToWithdraw = null },
            containerColor = MeetColors.cardBackground,
            title = {
                Text(
                    stringResource(R.string.safety_my_reports_withdraw_dialog_title),
                    color = MeetColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    stringResource(R.string.safety_my_reports_withdraw_dialog_text),
                    color = MeetColors.textSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        reportToWithdraw = null
                        viewModel.withdraw(reportId)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.error,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(R.string.safety_withdraw), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { reportToWithdraw = null }) {
                    Text(stringResource(R.string.safety_cancel), color = MeetColors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun MyReportCard(
    report: com.elysium369.meet.safety.data.local.SafetyReportEntity,
    evidence: List<SafetyEvidenceEntity>,
    videoUrls: List<String> = emptyList(),
    withdrawing: Boolean,
    onWithdraw: () -> Unit,
    onRetrySync: () -> Unit,
    onOpenEvidence: (SafetyEvidenceEntity) -> Unit = {},
    onOpenVideo: (String) -> Unit = {},
) {
    val categoryColor = SafetyCategoryIcons.colorForString(report.category)
    val categoryIcon = SafetyCategoryIcons.iconForString(report.category)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // === Header: Category + Sync Badge ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(categoryColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            report.category.replace("_", " "),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MeetColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            formatTimestamp(report.createdAt),
                            fontSize = 10.sp,
                            color = MeetColors.textMuted,
                        )
                    }
                }

                SyncStatusBadge(report)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // === Status Row: Local state + Server version ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MeetColors.backgroundDeep)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (statusIcon, statusColor, statusLabel) = resolveLocalState(report)
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            "ESTADO",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MeetColors.textMuted,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            statusLabel,
                            fontSize = 12.sp,
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (report.serverVersion > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MeetColors.cyberCyan.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("v${report.serverVersion} autorizada", fontSize = 10.sp, color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // === Evidence/Attachments Section ===
            if (evidence.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "ARCHIVOS ADJUNTOS (${evidence.size})",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MeetColors.textMuted,
                    letterSpacing = 1.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(evidence, key = { it.evidenceId }) { item ->
                        EvidenceChip(item, onClick = { onOpenEvidence(item) })
                    }
                }
            }

            // === Video Links Section ===
            if (videoUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "VIDEOS ADJUNTOS (${videoUrls.size})",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MeetColors.neonGreen,
                    letterSpacing = 1.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(videoUrls) { url ->
                        VideoLinkChip(url = url, onClick = { onOpenVideo(url) })
                    }
                }
            }

            // === Retry button for failed sync ===
            if (report.syncState == "FAILED") {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRetrySync,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.cyberCyan.copy(alpha = 0.15f),
                        contentColor = MeetColors.cyberCyan,
                    ),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reintentar sincronización", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // === Remove button ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onWithdraw,
                    enabled = !withdrawing,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MeetColors.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (withdrawing) stringResource(R.string.safety_withdrawing)
                        else stringResource(R.string.safety_my_reports_remove),
                        color = MeetColors.error,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncStatusBadge(report: com.elysium369.meet.safety.data.local.SafetyReportEntity) {
    val (syncColor, syncLabel, syncIcon) = when (report.syncState) {
        "SYNCED" -> Triple(MeetColors.neonGreen, "ONLINE", Icons.Filled.CloudDone)
        "SYNCING" -> Triple(MeetColors.cyberCyan, "SYNCING", Icons.Filled.CloudSync)
        "FAILED" -> Triple(MeetColors.error, "FAILED", Icons.Filled.CloudOff)
        "QUEUED" -> Triple(Color(0xFFFFB020), "QUEUED", Icons.Filled.CloudUpload)
        else -> Triple(MeetColors.textMuted, report.syncState, Icons.Filled.CloudUpload)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(syncColor.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = syncIcon,
            contentDescription = null,
            tint = syncColor,
            modifier = Modifier.size(12.dp),
        )
        Text(
            syncLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = syncColor,
        )
    }
}

@Composable
private fun EvidenceChip(item: SafetyEvidenceEntity, onClick: () -> Unit = {}) {
    val (icon, label) = resolveEvidenceType(item.mimeType)
    val uploadColor = when (item.uploadState) {
        "RECEIVED" -> MeetColors.neonGreen
        "UPLOADED" -> MeetColors.cyberCyan
        "UPLOADING" -> Color(0xFFFFB020)
        "FAILED" -> MeetColors.error
        else -> MeetColors.textMuted
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MeetColors.backgroundDeep)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MeetColors.cyberCyan,
            modifier = Modifier.size(16.dp),
        )
        Column {
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MeetColors.textPrimary,
            )
            Text(
                "${item.byteCount / 1024} KB · ${resolveUploadLabel(item.uploadState)}",
                fontSize = 8.sp,
                color = uploadColor,
            )
        }
    }
}

/** Map localState to a human-friendly triple: (icon, color, label) */
private fun resolveLocalState(report: com.elysium369.meet.safety.data.local.SafetyReportEntity): Triple<ImageVector, Color, String> {
    // Priority: if synced online, show that. Otherwise show raw state.
    return when {
        report.localState == "SYNCED_ONLINE" || report.syncState == "SYNCED" ->
            Triple(Icons.Filled.CloudDone, Color(0xFF10B981), "Sincronizado mundialmente")
        report.syncState == "SYNCING" ->
            Triple(Icons.Filled.CloudSync, Color(0xFF06B6D4), "Sincronizando...")
        report.syncState == "QUEUED" ->
            Triple(Icons.Filled.CloudUpload, Color(0xFFFFB020), "En cola de sincronización")
        report.syncState == "FAILED" ->
            Triple(Icons.Filled.CloudOff, Color(0xFFEF4444), "Error de sincronización")
        report.localState == "LOCAL_ONLY" ->
            Triple(Icons.Filled.CloudUpload, Color(0xFFFFB020), "Pendiente de sincronización")
        else ->
            Triple(Icons.Filled.CloudUpload, Color(0xFF94A3B8), report.localState)
    }
}

private fun resolveEvidenceType(mimeType: String): Pair<ImageVector, String> = when {
    mimeType.startsWith("image/") -> Icons.Filled.Image to "Imagen"
    mimeType.startsWith("video/") -> Icons.Filled.Videocam to "Video"
    mimeType.startsWith("audio/") -> Icons.Filled.AudioFile to "Audio"
    mimeType == "application/pdf" -> Icons.Filled.PictureAsPdf to "PDF"
    else -> Icons.Filled.AttachFile to "Archivo"
}

private fun resolveUploadLabel(state: String): String = when (state) {
    "STAGED" -> "Pendiente"
    "UPLOADING" -> "Subiendo..."
    "UPLOADED" -> "Subido"
    "RECEIVED" -> "Verificado ✓"
    "RETRY" -> "Reintentando..."
    "FAILED" -> "Error"
    else -> state
}

private fun formatTimestamp(epochMs: Long): String = try {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es")).format(Date(epochMs))
} catch (_: Exception) { "" }

@Composable
private fun VideoLinkChip(url: String, onClick: () -> Unit) {
    val platformName = when {
        url.contains("youtube", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true) -> "YouTube ▶️"
        url.contains("tiktok", ignoreCase = true) -> "TikTok 🎵"
        url.contains("drive.google", ignoreCase = true) -> "Drive 📁"
        url.contains("vimeo", ignoreCase = true) -> "Vimeo 🎬"
        url.contains("x.com", ignoreCase = true) || url.contains("twitter", ignoreCase = true) -> "X / Twitter 🐦"
        url.contains("instagram", ignoreCase = true) -> "Instagram 📸"
        else -> "Video 🎥"
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MeetColors.backgroundDeep)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Videocam,
            contentDescription = null,
            tint = MeetColors.neonGreen,
            modifier = Modifier.size(16.dp),
        )
        Column {
            Text(
                platformName,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MeetColors.neonGreen,
            )
            Text(
                "Toca para abrir",
                fontSize = 8.sp,
                color = MeetColors.textSecondary,
            )
        }
    }
}



@Composable
private fun ReportsCommandDeck(totalCount: Int, pendingCount: Int, failedCount: Int) {
    val glow by animateFloatAsState(
        targetValue = if (pendingCount > 0 || failedCount > 0) 0.95f else 0.55f,
        animationSpec = tween(durationMillis = 700), label = "reports-command-glow",
    )
    val statusColor = when {
        failedCount > 0 -> MeetColors.error
        pendingCount > 0 -> MeetColors.warning
        else -> MeetColors.neonGreen
    }
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.2.dp, Brush.linearGradient(listOf(
            MeetColors.electricBlue.copy(alpha = glow),
            statusColor.copy(alpha = glow),
            MeetColors.hotMagenta.copy(alpha = glow * 0.52f),
        ))),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(
            MeetColors.electricBlue.copy(alpha = 0.10f),
            MeetColors.cardBackground,
            statusColor.copy(alpha = 0.06f),
        ))).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("REPORTS COMMAND", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.4.sp)
                    Text("Estado real de tus reportes", color = MeetColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Surface(shape = RoundedCornerShape(7.dp), color = statusColor.copy(alpha = 0.13f), border = BorderStroke(1.dp, statusColor.copy(alpha = glow))) {
                    Text(when {
                        failedCount > 0 -> "REVISAR SINCRONIZACIÓN"
                        pendingCount > 0 -> "PENDIENTES"
                        else -> "SIN PENDIENTES LOCALES"
                    }, color = statusColor, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ReportsCommandMetric("REPORTES", totalCount.toString(), MeetColors.cyberCyan)
                ReportsCommandMetric("PENDIENTES", pendingCount.toString(), MeetColors.warning)
                ReportsCommandMetric("CON ERROR", failedCount.toString(), MeetColors.error)
            }
        }
    }
}

@Composable
private fun ReportsCommandMetric(label: String, value: String, color: Color) {
    Column {
        Text(label, fontSize = 8.sp, color = MeetColors.textSecondary, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 17.sp, color = color, fontWeight = FontWeight.Black)
    }
}
