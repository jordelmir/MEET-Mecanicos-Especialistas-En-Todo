package com.elysium369.meet.core.agentstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.agentstore.domain.AgentManifest
import com.elysium369.meet.core.agentstore.presentation.AgentStoreItemUi
import com.elysium369.meet.core.agentstore.presentation.AgentStoreViewModel
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteTextButton
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentStoreScreen(
    navController: NavController,
    viewModel: AgentStoreViewModel,
) {
    val storeItems by viewModel.storeItems.collectAsState()
    val selectedAgent by viewModel.selectedAgent.collectAsState()
    val isPreviewOpen by viewModel.previewModalOpen.collectAsState()

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "ELYSIUM AGENT STORE",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = MeetColors.backgroundDark,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Agentes Profesionales Especializados",
                    color = MeetColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Instala inteligencias autónomas para desbloquear capacidades avanzadas de diagnóstico, telemetría y operaciones.",
                    color = MeetColors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }

            items(storeItems) { item ->
                AgentCard(
                    item = item,
                    onPreviewClick = { viewModel.openPreview(item.manifest) },
                    onUnlockClick = { viewModel.purchaseAndUnlock(item.manifest) }
                )
            }
        }
    }

    if (isPreviewOpen && selectedAgent != null) {
        AgentPreviewDialog(
            agent = selectedAgent!!,
            onDismiss = { viewModel.closePreview() },
            onUnlock = {
                viewModel.purchaseAndUnlock(selectedAgent!!)
                viewModel.closePreview()
            }
        )
    }
}

@Composable
fun AgentCard(
    item: AgentStoreItemUi,
    onPreviewClick: () -> Unit,
    onUnlockClick: () -> Unit,
) {
    val manifest = item.manifest
    val borderColor = if (item.isOwned) MeetColors.neonGreen.copy(alpha = 0.6f) else MeetColors.cyberCyan.copy(alpha = 0.3f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(MeetColors.cyberCyan, MeetColors.backgroundDark)
                                )
                            )
                            .border(1.dp, MeetColors.cyberCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.isOwned) Icons.Default.CheckCircle else Icons.Default.Star,
                            contentDescription = null,
                            tint = if (item.isOwned) MeetColors.neonGreen else MeetColors.cyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = manifest.displayName,
                            color = MeetColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                        Text(
                            text = manifest.category.name,
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Price / Owned Badge
                if (item.isOwned) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MeetColors.neonGreen.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.neonGreen)
                    ) {
                        Text(
                            text = if (item.isCurrentActive) "ACTIVO" else "INSTALADO",
                            color = MeetColors.neonGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Text(
                        text = if (manifest.isFree) "GRATIS" else "₡${manifest.priceFiatCrc} CRC",
                        color = MeetColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = manifest.description,
                color = MeetColors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )

            Spacer(modifier = Modifier.height(12.dp))
            // Capabilities pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                manifest.capabilityIds.take(3).forEach { capId ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MeetColors.backgroundDark,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MeetColors.textSecondary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = capId,
                            color = MeetColors.textSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPreviewClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.cyberCyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.cyberCyan)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Previsualizar", fontSize = 12.sp)
                }

                if (!item.isOwned) {
                    Button(
                        onClick = onUnlockClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MeetColors.backgroundDark, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Desbloquear", color = MeetColors.backgroundDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AgentPreviewDialog(
    agent: AgentManifest,
    onDismiss: () -> Unit,
    onUnlock: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Previsualización: ${agent.displayName}", color = MeetColors.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = agent.description, color = MeetColors.textSecondary, fontSize = 13.sp)
                HorizontalDivider(color = MeetColors.borderSubtle)
                Text(text = "Capacidades que desbloquea:", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                agent.capabilityIds.forEach { cap ->
                    Text(text = "• $cap", color = MeetColors.textPrimary, fontSize = 12.sp)
                }
                HorizontalDivider(color = MeetColors.borderSubtle)
                Text(
                    text = "Aviso de Seguridad: Las capacidades avanzadas requieren confirmación de la política de seguridad y no se pueden ejecutar en modo prueba sin licencia.",
                    color = MeetColors.textSecondary,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            EliteButton(
                text = "Cerrar",
                onClick = onDismiss
            )
        },
        containerColor = MeetColors.cardBackground,
    )
}
