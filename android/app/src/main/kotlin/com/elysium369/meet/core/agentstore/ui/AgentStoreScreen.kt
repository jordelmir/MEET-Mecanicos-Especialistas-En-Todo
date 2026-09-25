package com.elysium369.meet.core.agentstore.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.elysium369.meet.core.agentstore.domain.AgentCategory
import com.elysium369.meet.core.agentstore.domain.AgentManifest
import com.elysium369.meet.core.agentstore.presentation.AgentStoreItemUi
import com.elysium369.meet.core.agentstore.presentation.AgentStoreViewModel
import com.elysium369.meet.ui.theme.MeetColors

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E L Y S I U M   A G E N T   S T O R E
 *  ──────────────────────────────────────────────────────────────
 *  Collectible 3D Agent Store with real engineering capabilities.
 *  - Large horizontal carousel cards with interactive 3D avatars.
 *  - Category filter pills.
 *  - Equipped companion badge with live telemetry sync.
 *  - Interactive 360° preview with voice synthesis playback.
 *  - Localized Costa Rica pricing (₡ CRC) with offer tags.
 * ══════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentStoreScreen(
    navController: NavController,
    viewModel: AgentStoreViewModel,
) {
    val storeItems by viewModel.storeItems.collectAsState()
    val equippedAgent by viewModel.equippedAgent.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedAgent by viewModel.selectedAgent.collectAsState()
    val isPreviewOpen by viewModel.previewModalOpen.collectAsState()
    val isVoicePlaying by viewModel.isVoicePlaying.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ELYSIUM AGENT STORE",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = Color.White,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            text = "Inteligencias Coleccionables con Capacidad Real",
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
        containerColor = MeetColors.backgroundDeep,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // ── Section 1: Active Equipped Companion Header ──
            equippedAgent?.let { equipped ->
                EquippedCompanionCard(
                    agent = equipped,
                    onOpenPreview = { viewModel.openPreview(equipped) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Section 2: Category Filter Bar ──
            CategoryFilterBar(
                selectedCategory = selectedCategory,
                onSelectCategory = { viewModel.selectCategory(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Section 3: Horizontal Collectible Cards Carousel ──
            Text(
                text = "CATÁLOGO DE AGENTES DISPONIBLES",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(storeItems, key = { it.manifest.id }) { item ->
                    CollectibleAgentCard(
                        item = item,
                        onPreviewClick = { viewModel.openPreview(item.manifest) },
                        onUnlockClick = { viewModel.purchaseAndUnlock(item.manifest) },
                        onEquipClick = { viewModel.equipAgent(item.manifest) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section 4: Architectural Guarantees Banner ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(MeetColors.neonGreen.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MeetColors.neonGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Laya System 1: Cero Costo de Nube",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = "Cada agente opera con modelos de decisión cuántica local y física pura. 100% privado en tu dispositivo, sin suscripciones de tokens ni llamadas lentas a internet.",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── Interactive Preview Modal Dialog ──
    if (isPreviewOpen && selectedAgent != null) {
        val agent = selectedAgent!!
        val isOwned = storeItems.find { it.manifest.id == agent.id }?.isOwned == true
        val isCurrentActive = storeItems.find { it.manifest.id == agent.id }?.isCurrentActive == true

        AgentPreviewModal(
            agent = agent,
            isOwned = isOwned,
            isCurrentActive = isCurrentActive,
            isVoicePlaying = isVoicePlaying,
            onDismiss = { viewModel.closePreview() },
            onPlayVoice = { viewModel.playVoiceSample(agent.voiceSampleText) },
            onStopVoice = { viewModel.stopVoiceSample() },
            onUnlock = {
                viewModel.purchaseAndUnlock(agent)
                viewModel.closePreview()
            },
            onEquip = {
                viewModel.equipAgent(agent)
                viewModel.closePreview()
            }
        )
    }
}

@Composable
private fun EquippedCompanionCard(
    agent: AgentManifest,
    onOpenPreview: () -> Unit,
) {
    val themeColor = Color(agent.themeColorHex)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onOpenPreview() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1424)),
        border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(themeColor, MeetColors.cyberCyan))),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(themeColor.copy(alpha = 0.12f))
                    .border(1.dp, themeColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Agent3dAvatarCanvas(
                    avatarVisualType = agent.avatarVisualType,
                    themeColor = themeColor,
                    modifier = Modifier.fillMaxSize(),
                    isInteractive = false
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MeetColors.neonGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "COMPAÑERO ACTIVO EN VEHÍCULO",
                        color = MeetColors.neonGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = agent.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
                Text(
                    text = agent.subtitle,
                    color = themeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            IconButton(onClick = onOpenPreview) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Ver detalles",
                    tint = MeetColors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterBar(
    selectedCategory: AgentCategory?,
    onSelectCategory: (AgentCategory?) -> Unit,
) {
    val categories = listOf(
        null to "Todos",
        AgentCategory.AUTOMOTIVE to "Mecánica",
        AgentCategory.SAFETY to "Seguridad",
        AgentCategory.MOBILITY to "Movilidad",
        AgentCategory.EMISSIONS to "Emisiones",
        AgentCategory.CORE to "Core",
    )

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { (category, label) ->
            val isSelected = (selectedCategory == category)
            Surface(
                modifier = Modifier.clickable { onSelectCategory(category) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) MeetColors.cyberCyan.copy(alpha = 0.22f) else MeetColors.cardBackground,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MeetColors.cyberCyan else MeetColors.borderSubtle
                )
            ) {
                Text(
                    text = label,
                    color = if (isSelected) MeetColors.cyberCyan else MeetColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun CollectibleAgentCard(
    item: AgentStoreItemUi,
    onPreviewClick: () -> Unit,
    onUnlockClick: () -> Unit,
    onEquipClick: () -> Unit,
) {
    val manifest = item.manifest
    val themeColor = Color(manifest.themeColorHex)

    val cardBorder = if (item.isCurrentActive) {
        BorderStroke(2.dp, MeetColors.neonGreen)
    } else if (item.isOwned) {
        BorderStroke(1.dp, themeColor.copy(alpha = 0.6f))
    } else {
        BorderStroke(1.dp, MeetColors.borderSubtle)
    }

    Card(
        modifier = Modifier
            .width(290.dp)
            .height(515.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = cardBorder,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Top Tag Badges Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (manifest.badgeTag != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = themeColor.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, themeColor.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = manifest.badgeTag,
                                color = themeColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MeetColors.backgroundDeep,
                        border = BorderStroke(0.5.dp, MeetColors.borderSubtle)
                    ) {
                        Text(
                            text = manifest.category.name,
                            color = MeetColors.textSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Hero Area: 3D Interactive Avatar Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF040A14),
                                    themeColor.copy(alpha = 0.12f)
                                )
                            )
                        )
                        .border(0.5.dp, themeColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Agent3dAvatarCanvas(
                        avatarVisualType = manifest.avatarVisualType,
                        themeColor = themeColor,
                        modifier = Modifier.fillMaxSize(),
                        isInteractive = true
                    )

                    // Touch drag hint
                    Text(
                        text = "⟳ arrastra en 360°",
                        color = MeetColors.textMuted.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Title & Subtitle
                Text(
                    text = manifest.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = manifest.subtitle,
                    color = themeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Description
                Text(
                    text = manifest.description,
                    color = MeetColors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Capability pills (first 3)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    manifest.detailedCapabilities.take(2).forEach { cap ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MeetColors.backgroundDeep,
                            border = BorderStroke(0.5.dp, MeetColors.borderSubtle)
                        ) {
                            Text(
                                text = cap.name,
                                color = MeetColors.textSecondary,
                                fontSize = 9.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Pricing & Actions Area
            Column {
                Divider(color = MeetColors.borderSubtle.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        if (manifest.originalPriceFiatCrc != null && !item.isOwned) {
                            Text(
                                text = "₡${manifest.originalPriceFiatCrc}",
                                color = MeetColors.textMuted,
                                fontSize = 10.sp,
                                textDecoration = TextDecoration.LineThrough
                            )
                        }
                        Text(
                            text = if (manifest.isFree) "INCLUIDO" else "₡${manifest.priceFiatCrc} CRC",
                            color = if (manifest.isFree) MeetColors.neonGreen else Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                    }

                    if (item.isCurrentActive) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MeetColors.neonGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, MeetColors.neonGreen)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("ACTIVO", color = MeetColors.neonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPreviewClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.cyberCyan),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.7f)),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Detalles", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (item.isCurrentActive) {
                        // Already active, do nothing extra
                    } else if (item.isOwned) {
                        Button(
                            onClick = onEquipClick,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text("Equipar", color = MeetColors.backgroundDark, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    } else {
                        Button(
                            onClick = onUnlockClick,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Black, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Desbloquear", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentPreviewModal(
    agent: AgentManifest,
    isOwned: Boolean,
    isCurrentActive: Boolean,
    isVoicePlaying: Boolean,
    onDismiss: () -> Unit,
    onPlayVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onUnlock: () -> Unit,
    onEquip: () -> Unit,
) {
    val themeColor = Color(agent.themeColorHex)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF07101E)),
            border = BorderStroke(1.5.dp, themeColor.copy(alpha = 0.8f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = themeColor.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, themeColor)
                        ) {
                            Text(
                                text = agent.category.name,
                                color = themeColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                        }
                    }

                    // 3D Canvas in Preview Modal (larger, interactive 360)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF030811),
                                        themeColor.copy(alpha = 0.15f)
                                    )
                                )
                            )
                            .border(1.dp, themeColor.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Agent3dAvatarCanvas(
                            avatarVisualType = agent.avatarVisualType,
                            themeColor = themeColor,
                            modifier = Modifier.fillMaxSize(),
                            isInteractive = true
                        )

                        Text(
                            text = "⟳ Gira en 360° con tu dedo",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = agent.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp
                    )
                    Text(
                        text = agent.subtitle,
                        color = themeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Personality Quote
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MeetColors.cardBackground,
                        border = BorderStroke(0.5.dp, themeColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "“${agent.voiceSampleText}”",
                            color = MeetColors.textPrimary,
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Voice preview button with animated frequency bars
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (isVoicePlaying) onStopVoice() else onPlayVoice() },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isVoicePlaying) themeColor.copy(alpha = 0.25f) else MeetColors.cardBackground,
                        border = BorderStroke(1.dp, if (isVoicePlaying) themeColor else MeetColors.borderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isVoicePlaying) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = themeColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (isVoicePlaying) "Reproduciendo voz del agente..." else "Escuchar muestra de voz",
                                    color = if (isVoicePlaying) themeColor else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (isVoicePlaying) {
                                AnimatedWaveformIndicator(color = themeColor)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "CAPACIDADES TÉCNICAS DESBLOQUEADAS",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Detailed Capability Packs
                    agent.detailedCapabilities.forEach { cap ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDark),
                            border = BorderStroke(0.5.dp, MeetColors.borderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cap.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    val riskColor = when (cap.riskLevel) {
                                        "SAFETY_CRITICAL" -> Color(0xFFFF3B30)
                                        "COMMITTING" -> Color(0xFFFF9500)
                                        else -> MeetColors.cyberCyan
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = riskColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = cap.riskLevel,
                                            color = riskColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = cap.physicalModelDescription,
                                    color = MeetColors.textSecondary,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                // Modal Footer
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Divider(color = MeetColors.borderSubtle)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (isCurrentActive) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, MeetColors.neonGreen)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MeetColors.neonGreen)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("COMPAÑERO ACTUALMENTE EQUIPADO", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                        }
                    } else if (isOwned) {
                        Button(
                            onClick = onEquip,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)
                        ) {
                            Text("EQUIPAR ESTE AGENTE EN EL VEHÍCULO", color = MeetColors.backgroundDark, fontWeight = FontWeight.Black)
                        }
                    } else {
                        Button(
                            onClick = onUnlock,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (agent.isFree) "DESBLOQUEAR GRATIS" else "DESBLOQUEAR POR ₡${agent.priceFiatCrc} CRC",
                                color = Color.Black,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedWaveformIndicator(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "WaveformBars")
    val barCount = 4

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400 + i * 120, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((16 * scale).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}
