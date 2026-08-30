package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.navigation.backOrHome

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.data.local.entities.DvirReportEntity
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DvirScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingHealth.collectAsState()

    // Form inputs
    var driverId by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    // Component Checklist Status (Default true = OK)
    var brakesOk by remember { mutableStateOf(true) }
    var lightsOk by remember { mutableStateOf(true) }
    var tiresOk by remember { mutableStateOf(true) }
    var fluidsOk by remember { mutableStateOf(true) }
    var batteryOk by remember { mutableStateOf(true) }

    // Signature Pad logic
    var lines = remember { mutableStateListOf<List<Offset>>() }
    var currentLine by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Dialog & UI Status States
    var showSuccessDialog by remember { mutableStateOf(false) }
    var generatedFile by remember { mutableStateOf<File?>(null) }
    var isGeneratingPdf by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            EliteTopAppBar(
                title = "DVIR PRE-VIAJE",
                subtitle = "Reporte de Seguridad Diario",
                onBackClick = { navController.backOrHome() }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Vehicle Selection Header ──
                EliteCard(
                    glowColor = MeetColors.neonGreen,
                    borderColor = MeetColors.neonGreen.copy(alpha = 0.15f),
                    backgroundColor = MeetColors.cardBackground,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        PhantomSectionHeader("VEHÍCULO BAJO INSPECCIÓN")
                        Spacer(Modifier.height(10.dp))
                        if (selectedVehicle != null) {
                            Text(
                                text = "${selectedVehicle?.make} ${selectedVehicle?.model} (${selectedVehicle?.year})",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "VIN: ${selectedVehicle?.vin ?: "N/A"}",
                                color = MeetColors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Text(
                                text = "⚠️ NO HAY VEHÍCULO SELECCIONADO",
                                color = MeetColors.error,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            EliteOutlinedButton(
                                text = "SELECCIONAR EN GARAGE",
                                onClick = { navController.navigate("garage") },
                                modifier = Modifier.fillMaxWidth(),
                                color = MeetColors.cyberCyan
                            )
                        }
                    }
                }

                // ── Operator Details Form ──
                EliteCard(
                    borderColor = MeetColors.borderSubtle,
                    backgroundColor = MeetColors.cardBackground,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PhantomSectionHeader("INFORMACIÓN DEL OPERADOR")
                        
                        OutlinedTextField(
                            value = driverId,
                            onValueChange = { driverId = it },
                            label = { Text("ID del Conductor / Operador") },
                            placeholder = { Text("Escriba su ID o nombre") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.neonGreen,
                                unfocusedBorderColor = MeetColors.borderBlue,
                                focusedLabelColor = MeetColors.neonGreen,
                                unfocusedLabelColor = MeetColors.textSecondary,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ── Component Checklist Status ──
                EliteCard(
                    borderColor = MeetColors.borderSubtle,
                    backgroundColor = MeetColors.cardBackground,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PhantomSectionHeader("ESTADO DE COMPONENTES CRÍTICOS")

                        ChecklistItem(
                            title = "Sistema de Frenos",
                            status = brakesOk,
                            onStatusChange = { brakesOk = it }
                        )
                        ChecklistItem(
                            title = "Luces y Señaladores",
                            status = lightsOk,
                            onStatusChange = { lightsOk = it }
                        )
                        ChecklistItem(
                            title = "Llantas y Neumáticos",
                            status = tiresOk,
                            onStatusChange = { tiresOk = it }
                        )
                        ChecklistItem(
                            title = "Fluidos y Aceites (Fugas)",
                            status = fluidsOk,
                            onStatusChange = { fluidsOk = it }
                        )
                        ChecklistItem(
                            title = "Batería y Alternador",
                            status = batteryOk,
                            onStatusChange = { batteryOk = it }
                        )
                    }
                }

                // ── Remarks / Observations ──
                EliteCard(
                    borderColor = MeetColors.borderSubtle,
                    backgroundColor = MeetColors.cardBackground,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PhantomSectionHeader("OBSERVACIONES Y COMENTARIOS")
                        
                        OutlinedTextField(
                            value = remarks,
                            onValueChange = { remarks = it },
                            label = { Text("Comentarios sobre el estado") },
                            placeholder = { Text("Indique fallas encontradas o detalles relevantes...") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.neonGreen,
                                unfocusedBorderColor = MeetColors.borderBlue,
                                focusedLabelColor = MeetColors.neonGreen,
                                unfocusedLabelColor = MeetColors.textSecondary,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                        )
                    }
                }

                // ── Signature Pad ──
                EliteCard(
                    borderColor = MeetColors.borderSubtle,
                    backgroundColor = MeetColors.cardBackground,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PhantomSectionHeader("FIRMA DEL OPERADOR")
                            Text(
                                text = "LIMPIAR",
                                color = MeetColors.error,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable { lines.clear() }
                                    .padding(4.dp)
                            )
                        }

                        // Drawing Area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .border(1.dp, MeetColors.borderBlue, RoundedCornerShape(10.dp))
                                .onSizeChanged { canvasSize = it }
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                currentLine = listOf(offset)
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                currentLine = currentLine + change.position
                                            },
                                            onDragEnd = {
                                                if (currentLine.isNotEmpty()) {
                                                    lines.add(currentLine)
                                                    currentLine = emptyList()
                                                }
                                            }
                                        )
                                    }
                            ) {
                                // Draw completed lines
                                lines.forEach { line ->
                                    if (line.size > 1) {
                                        for (i in 0 until line.size - 1) {
                                            drawLine(
                                                color = Color.Black,
                                                start = line[i],
                                                end = line[i + 1],
                                                strokeWidth = 4f,
                                                cap = StrokeCap.Round
                                            )
                                        }
                                    }
                                }
                                // Draw active drawing line
                                if (currentLine.size > 1) {
                                    for (i in 0 until currentLine.size - 1) {
                                        drawLine(
                                            color = Color.Black,
                                            start = currentLine[i],
                                            end = currentLine[i + 1],
                                            strokeWidth = 4f,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }

                            if (lines.isEmpty() && currentLine.isEmpty()) {
                                Text(
                                    text = "Dibuje su firma aquí",
                                    color = Color.LightGray,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                )
                            }
                        }
                    }
                }

                // ── Submit DVIR Button ──
                Spacer(modifier = Modifier.height(8.dp))
                EliteButton(
                    text = if (isGeneratingPdf) "Generando Reporte..." else "ENVIAR REPORTE DVIR",
                    onClick = {
                        if (selectedVehicle == null) {
                            Toast.makeText(context, "Por favor seleccione un vehículo primero", Toast.LENGTH_SHORT).show()
                            return@EliteButton
                        }
                        if (driverId.isBlank()) {
                            Toast.makeText(context, "Por favor introduzca el ID del Operador", Toast.LENGTH_SHORT).show()
                            return@EliteButton
                        }
                        if (lines.isEmpty()) {
                            Toast.makeText(context, "Por favor firme el reporte antes de enviarlo", Toast.LENGTH_SHORT).show()
                            return@EliteButton
                        }

                        isGeneratingPdf = true
                        val reportId = UUID.randomUUID().toString()
                        val sigFile = File(context.cacheDir, "dvir_sig_${reportId}.png")
                        
                        val success = saveSignatureToPng(
                            lines = lines,
                            width = canvasSize.width,
                            height = canvasSize.height,
                            outputFile = sigFile
                        )

                        if (!success) {
                            Toast.makeText(context, "Error al guardar firma digital", Toast.LENGTH_SHORT).show()
                            isGeneratingPdf = false
                            return@EliteButton
                        }

                        val report = DvirReportEntity(
                            id = reportId,
                            vehicleId = selectedVehicle!!.id,
                            driverId = driverId,
                            timestamp = System.currentTimeMillis(),
                            brakesOk = brakesOk,
                            lightsOk = lightsOk,
                            tiresOk = tiresOk,
                            fluidsOk = fluidsOk,
                            batteryOk = batteryOk,
                            remarks = remarks.takeIf { it.isNotBlank() },
                            signaturePath = sigFile.absolutePath
                        )

                        // 1. Insert in SQLite DB
                        viewModel.insertDvirReport(report)

                        // 2. Generate PDF
                        val vehicleLabel = "${selectedVehicle!!.make} ${selectedVehicle!!.model} ${selectedVehicle!!.year}"
                        viewModel.generateDvirReportPdf(
                            report = report,
                            vehicleInfo = vehicleLabel,
                            onSuccess = { file ->
                                generatedFile = file
                                isGeneratingPdf = false
                                showSuccessDialog = true
                            },
                            onError = { exception ->
                                isGeneratingPdf = false
                                Toast.makeText(context, "Error al compilar PDF: ${exception.message}", Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    isEnabled = !isGeneratingPdf && selectedVehicle != null,
                    modifier = Modifier.fillMaxWidth(),
                    color = MeetColors.neonGreen
                )
                Spacer(modifier = Modifier.height(30.dp))
            }

            // Loading overlay
            if (isGeneratingPdf) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MeetColors.neonGreen)
                }
            }
        }
    }

    // Success dialog
    if (showSuccessDialog) {
        EliteDialog(
            title = "REPORTE COMPILADO",
            message = "El reporte de inspección DVIR ha sido guardado exitosamente en la base de datos local y el documento PDF firmado ha sido compilado. ¿Desea compartirlo?",
            confirmText = "COMPARTIR PDF",
            dismissText = "CERRAR",
            onDismiss = { showSuccessDialog = false },
            onConfirm = {
                showSuccessDialog = false
                generatedFile?.let { viewModel.shareReport(it) }
            }
        )
    }
}

@Composable
private fun ChecklistItem(
    title: String,
    status: Boolean,
    onStatusChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MeetColors.cardBackgroundLighter.copy(alpha = 0.4f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = if (status) "Operación Normal (OK)" else "Requiere Atención / Falla",
                color = if (status) MeetColors.neonGreen else MeetColors.error,
                fontSize = 11.sp
            )
        }

        Switch(
            checked = status,
            onCheckedChange = onStatusChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MeetColors.backgroundDeep,
                checkedTrackColor = MeetColors.neonGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MeetColors.error
            )
        )
    }
}

/**
 * Saves drawing offsets to a physical white-background PNG.
 */
private fun saveSignatureToPng(
    lines: List<List<Offset>>,
    width: Int,
    height: Int,
    outputFile: File
): Boolean {
    if (width <= 0 || height <= 0 || lines.isEmpty()) return false
    return try {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Draw solid white background for high-contrast PDF compilation
        canvas.drawColor(android.graphics.Color.WHITE)
        
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            strokeWidth = 6f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        lines.forEach { line ->
            if (line.size > 1) {
                val path = AndroidPath()
                path.moveTo(line[0].x, line[0].y)
                for (i in 1 until line.size) {
                    path.lineTo(line[i].x, line[i].y)
                }
                canvas.drawPath(path, paint)
            }
        }
        
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
