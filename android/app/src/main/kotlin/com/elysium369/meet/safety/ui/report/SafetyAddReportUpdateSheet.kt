package com.elysium369.meet.safety.ui.report

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.safety.evidence.SafetyEvidencePolicy
import com.elysium369.meet.ui.theme.MeetColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class LocalAttachedFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyAddReportUpdateSheet(
    reportId: String,
    reportCategory: String,
    onDismiss: () -> Unit,
    onSaveUpdate: (
        occurredAt: Long,
        locationLabel: String,
        clothingAndFeatures: String,
        narrative: String,
        videoUrls: List<String>,
        attachmentUris: List<Uri>,
    ) -> Unit,
) {
    val context = LocalContext.current
    var showExplainer by remember { mutableStateOf(false) }

    // Campos del formulario
    var locationLabel by remember { mutableStateOf("") }
    var clothingAndFeatures by remember { mutableStateOf("") }
    var narrative by remember { mutableStateOf("") }
    var videoInputUrl by remember { mutableStateOf("") }
    var videoUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var attachedFiles by remember { mutableStateOf<List<LocalAttachedFile>>(emptyList()) }
    var occurredAtMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedTimePreset by remember { mutableStateOf("NOW") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    fun openDatePicker(onConfirmed: (year: Int, month: Int, dayOfMonth: Int) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = occurredAtMs }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                onConfirmed(year, month, dayOfMonth)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun openTimePicker(onConfirmed: (hour: Int, minute: Int) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = occurredAtMs }
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                onConfirmed(hourOfDay, minute)
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            DateFormat.is24HourFormat(context)
        ).show()
    }

    fun openFullDateTimePicker() {
        openDatePicker { year, month, day ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = occurredAtMs
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            occurredAtMs = cal.timeInMillis
            openTimePicker { hour, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                occurredAtMs = cal.timeInMillis
                selectedTimePreset = "CUSTOM"
            }
        }
    }

    fun resolveLocalFileInfo(uri: Uri): LocalAttachedFile? {
        return try {
            var name = "Archivo"
            var size = 0L
            val mime = context.contentResolver.getType(uri)?.lowercase() ?: "application/octet-stream"

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
            LocalAttachedFile(uri, name, size, mime)
        } catch (_: Exception) {
            null
        }
    }

    fun addFiles(newUris: List<Uri>) {
        val existingUris = attachedFiles.map { it.uri }.toSet()
        val toProcess = newUris.filter { it !in existingUris }
        var oversizedCount = 0
        val validItems = mutableListOf<LocalAttachedFile>()

        for (uri in toProcess) {
            val fileInfo = resolveLocalFileInfo(uri)
            if (fileInfo != null) {
                if (fileInfo.sizeBytes > SafetyEvidencePolicy.MAX_BYTES) {
                    oversizedCount++
                } else {
                    validItems.add(fileInfo)
                }
            }
        }

        if (oversizedCount > 0) {
            errorMessage = "Uno o más archivos superan el límite de 20 MB y fueron omitidos."
        }

        val combined = attachedFiles + validItems
        if (combined.size > SafetyEvidencePolicy.MAX_ATTACHMENTS) {
            errorMessage = "Límite: Puedes adjuntar hasta ${SafetyEvidencePolicy.MAX_ATTACHMENTS} archivos por avistamiento."
            attachedFiles = combined.take(SafetyEvidencePolicy.MAX_ATTACHMENTS)
        } else {
            attachedFiles = combined
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        addFiles(uris)
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        addFiles(uris)
    }

    fun formatFileSize(bytes: Long): String = when {
        bytes <= 0L -> "0 B"
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
    }

    fun getFileDisplayType(mimeType: String): Pair<ImageVector, String> = when {
        mimeType.startsWith("image/") -> Icons.Filled.Image to "Foto / Imagen"
        mimeType == "application/pdf" -> Icons.Filled.PictureAsPdf to "Documento PDF"
        mimeType.contains("word") || mimeType.contains("document") -> Icons.Filled.Description to "Documento Word"
        mimeType.contains("sheet") || mimeType.contains("excel") -> Icons.Filled.Description to "Hoja de cálculo"
        mimeType.startsWith("text/") -> Icons.Filled.Description to "Archivo de texto"
        mimeType.startsWith("audio/") -> Icons.Filled.AttachFile to "Grabación de audio"
        else -> Icons.Filled.AttachFile to "Archivo adjunto"
    }

    if (showExplainer) {
        SafetyCaseArchitectureExplainerSheet(
            onDismiss = { showExplainer = false },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MeetColors.backgroundDeep,
        contentColor = MeetColors.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MeetColors.cyberCyan.copy(alpha = 0.5f))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MeetColors.neonGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = MeetColors.neonGreen,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "AGREGAR AVISTAMIENTO AL CASO",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = MeetColors.textPrimary,
                        )
                        Text(
                            "Construyendo el expediente continuo",
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showExplainer = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "¿Cómo funciona?",
                            tint = MeetColors.cyberCyan,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Cerrar",
                            tint = MeetColors.textSecondary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Banner explicativo rápido
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.cyberCyan.copy(alpha = 0.08f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showExplainer = true },
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        tint = MeetColors.cyberCyan,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "¿Por qué agregar avistamientos al mismo caso? Toca aquí para ver cómo la app correlaciona prendas, lugares y horarios.",
                        fontSize = 11.sp,
                        color = MeetColors.textSecondary,
                        lineHeight = 15.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 1. Fecha y Hora de los Hechos (occurredAt) ──
            Text(
                "1. FECHA Y HORA DE LOS HECHOS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MeetColors.neonGreen,
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Indica cuándo viste u ocurrió este nuevo evento para construir la línea temporal del caso:",
                fontSize = 11.sp,
                color = MeetColors.textSecondary,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Fila de opciones preestablecidas + Opción de fecha y hora personalizada
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = selectedTimePreset == "NOW",
                    onClick = {
                        selectedTimePreset = "NOW"
                        occurredAtMs = System.currentTimeMillis()
                    },
                    label = { Text("Ahora", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MeetColors.neonGreen.copy(alpha = 0.2f),
                        selectedLabelColor = MeetColors.neonGreen,
                    ),
                )
                FilterChip(
                    selected = selectedTimePreset == "1H_AGO",
                    onClick = {
                        selectedTimePreset = "1H_AGO"
                        occurredAtMs = System.currentTimeMillis() - 3600_000L
                    },
                    label = { Text("Hace 1h", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MeetColors.neonGreen.copy(alpha = 0.2f),
                        selectedLabelColor = MeetColors.neonGreen,
                    ),
                )
                FilterChip(
                    selected = selectedTimePreset == "EARLIER_TODAY",
                    onClick = {
                        selectedTimePreset = "EARLIER_TODAY"
                        occurredAtMs = System.currentTimeMillis() - (4 * 3600_000L)
                    },
                    label = { Text("Hoy más temprano", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MeetColors.neonGreen.copy(alpha = 0.2f),
                        selectedLabelColor = MeetColors.neonGreen,
                    ),
                )
                FilterChip(
                    selected = selectedTimePreset == "YESTERDAY",
                    onClick = {
                        selectedTimePreset = "YESTERDAY"
                        occurredAtMs = System.currentTimeMillis() - (24 * 3600_000L)
                    },
                    label = { Text("Ayer", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MeetColors.neonGreen.copy(alpha = 0.2f),
                        selectedLabelColor = MeetColors.neonGreen,
                    ),
                )
                FilterChip(
                    selected = selectedTimePreset == "CUSTOM",
                    onClick = {
                        openFullDateTimePicker()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (selectedTimePreset == "CUSTOM") MeetColors.neonGreen else MeetColors.cyberCyan,
                        )
                    },
                    label = {
                        Text(
                            "Elegir fecha y hora",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTimePreset == "CUSTOM") FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MeetColors.neonGreen.copy(alpha = 0.2f),
                        selectedLabelColor = MeetColors.neonGreen,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Card interactiva con fecha y hora seleccionada + botones de modificación rápida
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (selectedTimePreset == "CUSTOM") MeetColors.neonGreen.copy(alpha = 0.35f) else MeetColors.borderSubtle
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.AccessTime,
                                contentDescription = null,
                                tint = if (selectedTimePreset == "CUSTOM") MeetColors.neonGreen else MeetColors.cyberCyan,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Fecha y hora del evento:",
                                    fontSize = 10.sp,
                                    color = MeetColors.textMuted,
                                )
                                Text(
                                    dateFormat.format(Date(occurredAtMs)),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedTimePreset == "CUSTOM") MeetColors.neonGreen else MeetColors.textPrimary,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val cal = Calendar.getInstance().apply { timeInMillis = occurredAtMs }
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        cal.set(Calendar.YEAR, year)
                                        cal.set(Calendar.MONTH, month)
                                        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                        occurredAtMs = cal.timeInMillis
                                        selectedTimePreset = "CUSTOM"
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH),
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.cyberCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f)),
                        ) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cambiar día/fecha", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                val cal = Calendar.getInstance().apply { timeInMillis = occurredAtMs }
                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                        cal.set(Calendar.MINUTE, minute)
                                        cal.set(Calendar.SECOND, 0)
                                        occurredAtMs = cal.timeInMillis
                                        selectedTimePreset = "CUSTOM"
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    DateFormat.is24HourFormat(context),
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.neonGreen),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f)),
                        ) {
                            Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cambiar hora", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Nota de hora de grabación inmutable
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = MeetColors.textMuted, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "🔒 Hora de registro del sistema: Grabación automática e inmutable al guardar.",
                    fontSize = 9.sp,
                    color = MeetColors.textMuted,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── 2. Ubicación o Comercio de Referencia ──
            Text(
                "2. UBICACIÓN O COMERCIO DE REFERENCIA",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MeetColors.cyberCyan,
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = locationLabel,
                onValueChange = { locationLabel = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ej. Supermercado X, Panadería X, Esquina parque...", color = MeetColors.textMuted, fontSize = 12.sp) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(18.dp))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeetColors.cyberCyan,
                    unfocusedBorderColor = MeetColors.borderSubtle,
                    focusedTextColor = MeetColors.textPrimary,
                    unfocusedTextColor = MeetColors.textPrimary,
                    cursorColor = MeetColors.cyberCyan,
                ),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── 3. Prendas, Calzado y Señas Particulares ──
            Text(
                "3. PRENDAS, CALZADO Y SEÑAS DEL INDIVIDUO",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MeetColors.neonGreen,
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = clothingAndFeatures,
                onValueChange = { clothingAndFeatures = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Ej. Tenis negras, gorra negra, pantaloneta negra, camisa blanca, tez morena...",
                        color = MeetColors.textMuted,
                        fontSize = 12.sp,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(18.dp))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeetColors.neonGreen,
                    unfocusedBorderColor = MeetColors.borderSubtle,
                    focusedTextColor = MeetColors.textPrimary,
                    unfocusedTextColor = MeetColors.textPrimary,
                    cursorColor = MeetColors.neonGreen,
                ),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── 4. Relato y Hechos Observados (Hasta 100,000 caracteres) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "4. RELATO DETALLADO DE LOS HECHOS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MeetColors.textPrimary,
                    letterSpacing = 1.sp,
                )
                Text(
                    "${narrative.length} / 100,000",
                    fontSize = 10.sp,
                    color = if (narrative.length > 90_000) MeetColors.error else MeetColors.textMuted,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = narrative,
                onValueChange = {
                    if (it.length <= 100_000) narrative = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 110.dp, max = 220.dp),
                placeholder = {
                    Text(
                        "Describe lo ocurrido, conducta observada, cómplices, vehículos, placas o cualquier dato relevante para robustecer el caso...",
                        color = MeetColors.textMuted,
                        fontSize = 12.sp,
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeetColors.cyberCyan,
                    unfocusedBorderColor = MeetColors.borderSubtle,
                    focusedTextColor = MeetColors.textPrimary,
                    unfocusedTextColor = MeetColors.textPrimary,
                    cursorColor = MeetColors.cyberCyan,
                ),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── 5. Fotos, Archivos y Documentos Adjuntos ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "5. FOTOS, ARCHIVOS Y DOCUMENTOS ADJUNTOS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MeetColors.cyberCyan,
                    letterSpacing = 1.sp,
                )
                if (attachedFiles.isNotEmpty()) {
                    Text(
                        "${attachedFiles.size} / ${SafetyEvidencePolicy.MAX_ATTACHMENTS}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.cyberCyan,
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Sube fotografías del avistamiento, documentos (PDF, Word, hojas de cálculo, notas) o audios para robustecer las pruebas del caso:",
                fontSize = 10.sp,
                color = MeetColors.textMuted,
                lineHeight = 14.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Botones para subir fotos y documentos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    enabled = attachedFiles.size < SafetyEvidencePolicy.MAX_ATTACHMENTS && !isSaving,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MeetColors.neonGreen,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.5f)),
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Subir Fotos", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        documentPickerLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "text/*",
                                "application/msword",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "image/*",
                                "*/*",
                            )
                        )
                    },
                    enabled = attachedFiles.size < SafetyEvidencePolicy.MAX_ATTACHMENTS && !isSaving,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MeetColors.cyberCyan,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Subir Archivo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Lista de archivos adjuntados
            if (attachedFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    attachedFiles.forEach { fileItem ->
                        val (icon, typeLabel) = getFileDisplayType(fileItem.mimeType)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MeetColors.cardBackground)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = MeetColors.cyberCyan,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        fileItem.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MeetColors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "$typeLabel · ${formatFileSize(fileItem.sizeBytes)}",
                                        fontSize = 9.sp,
                                        color = MeetColors.textMuted,
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    attachedFiles = attachedFiles.filter { it.uri != fileItem.uri }
                                },
                                modifier = Modifier.size(24.dp),
                                enabled = !isSaving,
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Quitar archivo",
                                    tint = MeetColors.error,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── 6. Videos Adjuntos (VIDEOS SOLO URLs) ──
            Text(
                "6. VIDEOS ADJUNTOS (VIDEOS SOLO URLs)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFB020),
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "⚠️ Para no saturar tu celular ni la red, los videos deben ser enlaces externos (YouTube, Rumble, almacenamiento en la nube, enlace web). No se permiten archivos locales de video.",
                fontSize = 10.sp,
                color = MeetColors.textMuted,
                lineHeight = 14.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = videoInputUrl,
                    onValueChange = { videoInputUrl = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("https://youtube.com/... o https://...", color = MeetColors.textMuted, fontSize = 12.sp) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color(0xFFFFB020), modifier = Modifier.size(18.dp))
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFB020),
                        unfocusedBorderColor = MeetColors.borderSubtle,
                        focusedTextColor = MeetColors.textPrimary,
                        unfocusedTextColor = MeetColors.textPrimary,
                        cursorColor = Color(0xFFFFB020),
                    ),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val clean = videoInputUrl.trim()
                        if (clean.isNotBlank()) {
                            val formatted = if (!clean.startsWith("http://") && !clean.startsWith("https://")) "https://$clean" else clean
                            if (!videoUrls.contains(formatted)) {
                                videoUrls = videoUrls + formatted
                            }
                            videoInputUrl = ""
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFB020),
                        contentColor = Color.Black,
                    ),
                ) {
                    Text("+ Añadir", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // Lista de URLs agregadas
            if (videoUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    videoUrls.forEach { url ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MeetColors.cardBackground)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Link, contentDescription = null, tint = Color(0xFFFFB020), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(url, fontSize = 11.sp, color = MeetColors.textPrimary, maxLines = 1)
                            }
                            IconButton(
                                onClick = { videoUrls = videoUrls.filter { it != url } },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Quitar", tint = MeetColors.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(err, color = MeetColors.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Botón de guardar
            Button(
                onClick = {
                    if (narrative.isBlank() && clothingAndFeatures.isBlank() && locationLabel.isBlank() && attachedFiles.isEmpty() && videoUrls.isEmpty()) {
                        errorMessage = "Por favor ingresa al menos la ubicación, prendas, un relato, fotos o documentos de lo observado."
                        return@Button
                    }
                    errorMessage = null
                    isSaving = true
                    onSaveUpdate(
                        occurredAtMs,
                        locationLabel.ifBlank { "Ubicación del caso" },
                        clothingAndFeatures,
                        narrative,
                        videoUrls,
                        attachedFiles.map { it.uri },
                    )
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeetColors.neonGreen,
                    contentColor = MeetColors.backgroundDeep,
                ),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MeetColors.backgroundDeep,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Encriptando y Guardando...", fontWeight = FontWeight.Black, fontSize = 13.sp)
                } else {
                    Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar Avistamiento y Actualizar Caso", fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }
    }
}
