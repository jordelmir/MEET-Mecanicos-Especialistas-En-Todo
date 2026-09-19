package com.elysium369.meet.safety.ui.report

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.safety.domain.SafetyReportCategory
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyReportScreen(
    onBack: () -> Unit = {},
    onReportSubmitted: (String) -> Unit = {},
) {
    var category by remember { mutableStateOf(SafetyReportCategory.OTHER) }
    var narrative by remember { mutableStateOf("") }
    var occurredAtText by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Location obtained via FusedLocationProviderClient in real impl
        }
    }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("REPORTAR INCIDENTE", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color.White)
                        Text("Reporte offline-first · cifrado local", fontSize = 11.sp, color = MeetColors.cyberCyan)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("CATEGORÍA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        SafetyReportCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat.name.replace("_", " "), fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MeetColors.neonGreen.copy(alpha = 0.15f),
                                    selectedLabelColor = MeetColors.neonGreen,
                                ),
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("DESCRIPCIÓN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = narrative,
                            onValueChange = { narrative = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Mínimo 10 caracteres...", color = MeetColors.textMuted) },
                            minLines = 4,
                            maxLines = 10,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.neonGreen,
                                unfocusedBorderColor = MeetColors.borderSubtle,
                                focusedTextColor = MeetColors.textPrimary,
                                unfocusedTextColor = MeetColors.textPrimary,
                                cursorColor = MeetColors.neonGreen,
                            ),
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("¿CUÁNDO OCURRIÓ? (opcional)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = occurredAtText,
                            onValueChange = { occurredAtText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Ej: 2026-09-18 14:30", color = MeetColors.textMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.neonGreen,
                                unfocusedBorderColor = MeetColors.borderSubtle,
                                focusedTextColor = MeetColors.textPrimary,
                                unfocusedTextColor = MeetColors.textPrimary,
                                cursorColor = MeetColors.neonGreen,
                            ),
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("UBICACIÓN (opcional)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeetColors.cyberCyan,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Usar mi ubicación")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = latitude,
                                onValueChange = { latitude = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("Lat", color = MeetColors.textSecondary) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MeetColors.neonGreen,
                                    unfocusedBorderColor = MeetColors.borderSubtle,
                                    focusedTextColor = MeetColors.textPrimary,
                                    unfocusedTextColor = MeetColors.textPrimary,
                                    cursorColor = MeetColors.neonGreen,
                                ),
                            )
                            OutlinedTextField(
                                value = longitude,
                                onValueChange = { longitude = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("Lng", color = MeetColors.textSecondary) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MeetColors.neonGreen,
                                    unfocusedBorderColor = MeetColors.borderSubtle,
                                    focusedTextColor = MeetColors.textPrimary,
                                    unfocusedTextColor = MeetColors.textPrimary,
                                    cursorColor = MeetColors.neonGreen,
                                ),
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        if (narrative.length >= 10) {
                            onReportSubmitted(category.name)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = narrative.length >= 10,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.neonGreen,
                        contentColor = Color.Black,
                        disabledContainerColor = MeetColors.cardBackground,
                        disabledContentColor = MeetColors.textSecondary,
                    ),
                ) {
                    Text("ENVIAR REPORTE (offline-first)", fontWeight = FontWeight.Bold)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
