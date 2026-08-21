package com.elysium369.meet.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.core.obd.ObdCommandExplainer
import com.elysium369.meet.ui.components.AnimatedNeonGlyph
import com.elysium369.meet.ui.theme.MeetColors
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

data class TerminalLine(
    val text: String,
    val type: TerminalLineType,
    val timestamp: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
)

enum class TerminalLineType {
    SYSTEM,       // System messages (cyan)
    COMMAND,      // User commands (neonGreen)
    RESPONSE,     // OBD responses (white)
    ERROR,        // Errors (red)
    WARNING,      // Warnings (yellow)
    EXPLANATION,  // Expert explanations (cyberCyan panel)
    DECODED       // Decoded response values (neon purple)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(viewModel: ObdViewModel) {
    var commandInput by remember { mutableStateOf("") }
    val terminalOutput by viewModel.terminalSessionLogs.collectAsState()
    val commandHistory by viewModel.commandHistory.collectAsState()
    
    var activeFilter by remember { mutableStateOf<TerminalLineType?>(null) }
    val filteredTerminalOutput = remember(terminalOutput, activeFilter) {
        if (activeFilter == null) {
            terminalOutput
        } else {
            terminalOutput.filter { it.type == activeFilter }
        }
    }
    
    var showUdsWizard by remember { mutableStateOf(false) }
    
    var activeTerminalTab by remember { mutableStateOf("OBD") } // "OBD" or "ANDROID"
    var localCommandInput by remember { mutableStateOf("") }
    val localShellLines by viewModel.localShellLines.collectAsState()
    val activeDistro by viewModel.activeDistro.collectAsState()
    val installedDistros by viewModel.installedDistros.collectAsState()
    val installingDistro by viewModel.localShellManager.installingDistro.collectAsState()
    val installProgress by viewModel.localShellManager.installProgress.collectAsState()
    
    var isSending by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.connectionState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val quickCommands = listOf(
        "ATZ", "AT RV", "ATDP", "0100", "0101", "0104", "0105",
        "010C", "010D", "010F", "0110", "0111", "012F", "03", "07", "0A", "09 02"
    )

    // Neon glow animation for header
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val localListState = rememberLazyListState()

    // Auto-scroll to bottom based on filtered output size
    LaunchedEffect(filteredTerminalOutput.size) {
        if (filteredTerminalOutput.isNotEmpty()) {
            listState.animateScrollToItem(filteredTerminalOutput.size - 1)
        }
    }

    // Auto-scroll to bottom based on Android terminal logs
    LaunchedEffect(localShellLines.size) {
        if (localShellLines.isNotEmpty()) {
            localListState.animateScrollToItem(localShellLines.size - 1)
        }
    }

    val statusColor = when (state) {
        ObdState.CONNECTED -> MeetColors.neonGreen
        ObdState.CONNECTING -> MeetColors.warning
        else -> MeetColors.error
    }
    val statusText = when (state) {
        ObdState.CONNECTED -> "● ENLACE ACTIVO"
        ObdState.CONNECTING -> "◌ SINCRONIZANDO..."
        ObdState.DISCONNECTED -> "○ DESCONECTADO"
        ObdState.ERROR -> "✗ ERROR DE ENLACE"
        else -> "○ IDLE"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MeetColors.carbonGradient)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
        // ═══ HEADER with neon glow ═══
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MeetColors.backgroundDeep)
                .drawBehind {
                    drawRoundRect(
                        color = MeetColors.neonGreen.copy(alpha = glowAlpha * 0.3f),
                        cornerRadius = CornerRadius(10.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "⚡ EXPERT TERMINAL",
                        color = MeetColors.neonGreen.copy(alpha = glowAlpha),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (activeTerminalTab == "OBD") {
                        TextButton(
                            onClick = {
                                showUdsWizard = true
                            }
                        ) {
                            Text(
                                "ASISTENTE UDS",
                                color = Color(0xFFBD00FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    TextButton(
                        onClick = {
                            if (activeTerminalTab == "OBD") {
                                copyTerminalLogsToClipboard(context, terminalOutput)
                            } else {
                                copyLocalShellLogsToClipboard(context, localShellLines)
                            }
                        }
                    ) {
                        Text(
                            "COPIAR",
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = {
                            if (activeTerminalTab == "OBD") {
                                shareTerminalLogs(context, terminalOutput)
                            } else {
                                shareLocalShellLogs(context, localShellLines)
                            }
                        }
                    ) {
                        Text(
                            "COMPARTIR",
                            color = MeetColors.neonGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = {
                            if (activeTerminalTab == "OBD") {
                                viewModel.clearTerminalLogs()
                            } else {
                                viewModel.localShellManager.clearTerminal()
                            }
                        }
                    ) {
                        Text(
                            "LIMPIAR",
                            color = MeetColors.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ═══ TAB BAR SELECTOR ═══
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MeetColors.backgroundDeep)
                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp)),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (activeTerminalTab == "OBD") MeetColors.neonGreen.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { activeTerminalTab = "OBD" }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CONSOLA OBD-II",
                    color = if (activeTerminalTab == "OBD") MeetColors.neonGreen else MeetColors.textSecondary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (activeTerminalTab == "ANDROID") MeetColors.cyberCyan.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { activeTerminalTab = "ANDROID" }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SISTEMAS ANDROID & LINUX",
                    color = if (activeTerminalTab == "ANDROID") MeetColors.cyberCyan else MeetColors.textSecondary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (activeTerminalTab == "OBD") {
            // ═══ OBD LAYOUT ═══
            // ═══ FILTER BAR (Task 22) ═══
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    TerminalFilterChip(
                        selected = activeFilter == null,
                        onClick = { activeFilter = null },
                        label = "TODOS",
                        activeColor = MeetColors.neonGreen
                    )
                }
                
                val filterTypes = listOf(
                    TerminalLineType.COMMAND to "ENVIADOS",
                    TerminalLineType.RESPONSE to "RESPUESTAS",
                    TerminalLineType.EXPLANATION to "EXPLICACIONES",
                    TerminalLineType.ERROR to "ERRORES",
                    TerminalLineType.DECODED to "DECODIFICADOS",
                    TerminalLineType.SYSTEM to "SISTEMA"
                )
                
                items(filterTypes) { (type, label) ->
                    val color = when (type) {
                        TerminalLineType.COMMAND -> MeetColors.neonGreen
                        TerminalLineType.RESPONSE -> Color.White
                        TerminalLineType.EXPLANATION -> MeetColors.cyberCyan
                        TerminalLineType.ERROR -> MeetColors.error
                        TerminalLineType.DECODED -> Color(0xFFBD00FF)
                        else -> MeetColors.cyberCyan.copy(alpha = 0.8f)
                    }
                    TerminalFilterChip(
                        selected = activeFilter == type,
                        onClick = { activeFilter = type },
                        label = label,
                        activeColor = color
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ═══ TERMINAL OUTPUT AREA (CRT Scanlines Overlay) ═══
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MeetColors.backgroundDeep)
                    .border(
                        1.dp,
                        MeetColors.neonGreen.copy(alpha = 0.15f),
                        RoundedCornerShape(10.dp)
                    )
                    .drawWithContent {
                        drawContent()
                        // CRT Scanlines Effect
                        val scanlineSpacing = 6.dp.toPx()
                        val scanlineHeight = 1.5.dp.toPx()
                        var y = 0f
                        while (y < size.height) {
                            drawRect(
                                color = Color.Black.copy(alpha = 0.12f),
                                topLeft = androidx.compose.ui.geometry.Offset(0f, y),
                                size = androidx.compose.ui.geometry.Size(size.width, scanlineHeight)
                            )
                            y += scanlineSpacing
                        }
                    }
                    .padding(8.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredTerminalOutput) { line ->
                        TerminalLineRow(line, glowAlpha)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ═══ INPUT AREA ═══
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it.uppercase() },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MeetColors.neonGreen,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = MeetColors.backgroundDeep,
                        unfocusedContainerColor = MeetColors.backgroundDeep,
                        cursorColor = MeetColors.neonGreen,
                        focusedBorderColor = MeetColors.neonGreen,
                        unfocusedBorderColor = MeetColors.neonGreen.copy(alpha = 0.25f)
                    ),
                    placeholder = {
                        Text(
                            "ATZ, 010C, AT RV...",
                            color = MeetColors.textMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val cmd = commandInput.trim()
                        if (cmd.isNotEmpty()) {
                            // 1. Show the command
                            val newLines = mutableListOf<TerminalLine>()
                            newLines.add(TerminalLine(cmd, TerminalLineType.COMMAND))

                            // 2. Show expert explanation BEFORE sending
                            val explanation = ObdCommandExplainer.explain(cmd)
                            if (explanation != null) {
                                newLines.add(TerminalLine(explanation, TerminalLineType.EXPLANATION))
                            }

                            viewModel.addTerminalLogs(newLines)
                            viewModel.addTerminalCommand(cmd)
                            commandInput = ""

                            if (state != ObdState.CONNECTED) {
                                viewModel.addTerminalLog(
                                    "OBD no conectado. Conecta el adaptador ELM327 al puerto OBD2 y enciende el contacto.",
                                    TerminalLineType.ERROR
                                )
                                return@Button
                            }

                            isSending = true
                            coroutineScope.launch {
                                try {
                                    val response = viewModel.sendRawCommand(cmd)
                                    val responseLines = mutableListOf<TerminalLine>()

                                    // 3. Show raw response
                                    val responseType = when {
                                        response.contains("ERROR", true) ||
                                        response.contains("NO DATA", true) ||
                                        response.contains("UNABLE", true) ||
                                        response.contains("BLOCKED", true) -> TerminalLineType.WARNING
                                        else -> TerminalLineType.RESPONSE
                                    }
                                    responseLines.add(TerminalLine(response, responseType))

                                    // 4. Show decoded value (if applicable)
                                    val decoded = ObdCommandExplainer.decodeResponse(cmd, response)
                                    if (decoded != null) {
                                        responseLines.add(TerminalLine(decoded, TerminalLineType.DECODED))
                                    }

                                    viewModel.addTerminalLogs(responseLines)
                                } catch (e: Exception) {
                                    viewModel.addTerminalLog(
                                        "Exception: ${e.message}",
                                        TerminalLineType.ERROR
                                    )
                                } finally {
                                    isSending = false
                                }
                            }
                        }
                    },
                    enabled = !isSending && commandInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.neonGreen,
                        contentColor = MeetColors.backgroundDeep,
                        disabledContainerColor = MeetColors.backgroundDark
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(56.dp)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MeetColors.backgroundDeep
                        )
                    } else {
                        Text(
                            "TX",
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ═══ COMMAND HISTORY ═══
            if (commandHistory.isNotEmpty()) {
                Text(
                    text = "HISTORIAL DE COMANDOS",
                    color = MeetColors.textMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(commandHistory) { cmd ->
                        val icon = ObdCommandExplainer.categoryIcon(cmd)
                        AssistChip(
                            onClick = { commandInput = cmd },
                            label = {
                                Text(
                                    "$icon $cmd",
                                    color = MeetColors.neonGreen,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MeetColors.backgroundDeep
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MeetColors.neonGreen.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(6.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ═══ QUICK COMMANDS ═══
            Text(
                text = "COMANDOS RÁPIDOS",
                color = MeetColors.textMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(quickCommands) { cmd ->
                    val icon = ObdCommandExplainer.categoryIcon(cmd)
                    AssistChip(
                        onClick = { commandInput = cmd },
                        label = {
                            Text(
                                "$icon $cmd",
                                color = MeetColors.cyberCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MeetColors.backgroundDeep
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MeetColors.cyberCyan.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    )
                }
            }
        } else {
            // ═══ MULTI-DISTRO LINUX & ANDROID LAYOUT ═══
            val distroTabs = listOf(
                "android" to "🤖 Host",
                "alpine" to "🏔️ Alpine",
                "debian" to "🍥 Debian",
                "ubuntu" to "🧡 Ubuntu"
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                distroTabs.forEach { (distroId, label) ->
                    val isSelected = activeDistro == distroId
                    val isInstalled = installedDistros.contains(distroId)
                    val activeColor = when (distroId) {
                        "android" -> MeetColors.cyberCyan
                        "alpine" -> MeetColors.neonGreen
                        "debian" -> Color(0xFFBD00FF)
                        "ubuntu" -> Color(0xFFFF5500)
                        else -> MeetColors.cyberCyan
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) activeColor.copy(alpha = 0.15f)
                                else MeetColors.backgroundDeep
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) activeColor 
                                        else if (isInstalled) MeetColors.textMuted.copy(alpha = 0.3f)
                                        else MeetColors.textMuted.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                if (activeDistro != distroId) {
                                    viewModel.switchActiveDistro(distroId)
                                }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = label,
                                color = if (isSelected) activeColor else if (isInstalled) Color.White.copy(alpha = 0.8f) else MeetColors.textMuted,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(
                                            if (isInstalled) MeetColors.neonGreen 
                                            else MeetColors.error
                                        )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isInstalled) "ACTIVO" else "DISPONIBLE",
                                    color = if (isInstalled) MeetColors.neonGreen.copy(alpha = 0.8f) else MeetColors.textMuted.copy(alpha = 0.6f),
                                    fontSize = 7.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))

            val activeIsInstalled = installedDistros.contains(activeDistro)
            if (!activeIsInstalled) {
                // Show installation prompt
                val distroTitle = when (activeDistro) {
                    "alpine" -> "Alpine Linux"
                    "debian" -> "Debian GNU/Linux"
                    "ubuntu" -> "Ubuntu Linux"
                    else -> if (activeDistro.isNotEmpty()) activeDistro.substring(0, 1).uppercase() + activeDistro.substring(1) else ""
                }
                val distroDesc = when (activeDistro) {
                    "alpine" -> "Distribución ultraligera (~3.2MB de descarga, se extrae en ~9MB). Ideal para scripts sencillos y contenedores rápidos."
                    "debian" -> "Distribución Debian Bookworm estable (~40.9MB de descarga, se extrae en ~110MB). Soporte completo para desarrollo."
                    "ubuntu" -> "Ubuntu Base 22.04 LTS (~26.3MB de descarga, se extrae en ~75MB). Recomendado para instalar pip, Python y Antigravity CLI."
                    else -> ""
                }
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MeetColors.backgroundDeep)
                        .border(
                            1.dp,
                            MeetColors.textMuted.copy(alpha = 0.2f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "📦 INSTALADOR DE SISTEMAS",
                            color = MeetColors.cyberCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = distroTitle,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = distroDesc,
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        if (installingDistro == activeDistro) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .padding(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = when (activeDistro) {
                                        "alpine" -> MeetColors.neonGreen
                                        "debian" -> Color(0xFFBD00FF)
                                        "ubuntu" -> Color(0xFFFF5500)
                                        else -> MeetColors.cyberCyan
                                    },
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = installProgress.ifEmpty { "Descargando e instalando $distroTitle..." },
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.localShellManager.installDistro(activeDistro)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when (activeDistro) {
                                        "alpine" -> MeetColors.neonGreen
                                        "debian" -> Color(0xFFBD00FF)
                                        "ubuntu" -> Color(0xFFFF5500)
                                        else -> MeetColors.cyberCyan
                                    }
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text(
                                    text = "📥 DESCARGAR E INSTALAR $distroTitle",
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            } else {
                // CRT terminal console area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MeetColors.backgroundDeep)
                        .border(
                            1.dp,
                            when (activeDistro) {
                                "android" -> MeetColors.cyberCyan
                                "alpine" -> MeetColors.neonGreen
                                "debian" -> Color(0xFFBD00FF)
                                "ubuntu" -> Color(0xFFFF5500)
                                else -> MeetColors.cyberCyan
                            }.copy(alpha = 0.15f),
                            RoundedCornerShape(10.dp)
                        )
                        .drawWithContent {
                            drawContent()
                            // CRT Scanlines Effect
                            val scanlineSpacing = 6.dp.toPx()
                            val scanlineHeight = 1.5.dp.toPx()
                            var y = 0f
                            while (y < size.height) {
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.12f),
                                    topLeft = androidx.compose.ui.geometry.Offset(0f, y),
                                    size = androidx.compose.ui.geometry.Size(size.width, scanlineHeight)
                                )
                                y += scanlineSpacing
                            }
                        }
                        .padding(8.dp)
                ) {
                    LazyColumn(
                        state = localListState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(localShellLines) { line ->
                            val isDiffRemove = line.startsWith("- ") || line.contains(" - func") || line.contains(" - ")
                            val isDiffAdd = line.startsWith("+ ") || line.contains(" + func") || line.contains(" + ")
                            val isYouPrompt = line.startsWith("> you:")
                            val isAgyResponse = line.startsWith("AGY:")
                            val isAgyLogo = line.contains("███") || line.contains("▄▄▄") || line.contains("Welcome to Antigravity CLI")

                            if (isDiffRemove) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 1.dp)
                                        .background(Color(0xFF8B0000).copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = line,
                                        color = Color(0xFFFF6B6B),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            } else if (isDiffAdd) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 1.dp)
                                        .background(Color(0xFF006400).copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = line,
                                        color = Color(0xFF51CF66),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            } else {
                                Row(modifier = Modifier.padding(vertical = 1.dp)) {
                                    Text(
                                        text = line,
                                        color = when {
                                            isYouPrompt -> Color(0xFFBD00FF)
                                            isAgyResponse -> Color(0xFF38EF7D)
                                            isAgyLogo -> Color(0xFF00E5FF)
                                            line.startsWith("❯") -> when (activeDistro) {
                                                "android" -> MeetColors.cyberCyan
                                                "alpine" -> MeetColors.neonGreen
                                                "debian" -> Color(0xFFBD00FF)
                                                "ubuntu" -> Color(0xFFFF5500)
                                                else -> MeetColors.cyberCyan
                                            }
                                            line.startsWith("[Error") || line.startsWith("[AI Error") -> MeetColors.error
                                            line.startsWith("[Shell") -> MeetColors.error
                                            line.startsWith("•") || line.startsWith("✓") -> MeetColors.neonGreen
                                            line.startsWith("===") -> Color(0xFFFFD600)
                                            else -> Color.White
                                        },
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action button bar for Restart Shell next to console input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.localShellManager.restartShell()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeetColors.cardBackgroundLighter,
                            contentColor = when (activeDistro) {
                                "android" -> MeetColors.cyberCyan
                                "alpine" -> MeetColors.neonGreen
                                "debian" -> Color(0xFFBD00FF)
                                "ubuntu" -> Color(0xFFFF5500)
                                else -> MeetColors.cyberCyan
                            }
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "REINICIAR ENTORNOS",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }

                // ═══ VIRTUAL KEY ROW (Pro Unix/Terminal Keys) ═══
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val virtualKeys = listOf(
                        "TAB" to { localCommandInput += "\t" },
                        "CTRL+C" to { viewModel.localShellManager.executeCommand("\u0003") },
                        "ESC" to { viewModel.localShellManager.executeCommand("\u001B") },
                        "|" to { localCommandInput += " | " },
                        "~" to { localCommandInput += "~" },
                        "/" to { localCommandInput += "/" },
                        "-" to { localCommandInput += "-" },
                        "$" to { localCommandInput += "$" },
                        "CLEAR" to { viewModel.localShellManager.clearTerminal() }
                    )
                    items(virtualKeys) { (keyLabel, action) ->
                        AssistChip(
                            onClick = { action() },
                            label = {
                                Text(
                                    keyLabel,
                                    color = when (activeDistro) {
                                        "android" -> MeetColors.cyberCyan
                                        "alpine" -> MeetColors.neonGreen
                                        "debian" -> Color(0xFFBD00FF)
                                        "ubuntu" -> Color(0xFFFF5500)
                                        else -> MeetColors.cyberCyan
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MeetColors.backgroundDeep
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MeetColors.borderSubtle
                            ),
                            shape = RoundedCornerShape(4.dp)
                        )
                    }
                }

                // Command input row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeColor = when (activeDistro) {
                        "android" -> MeetColors.cyberCyan
                        "alpine" -> MeetColors.neonGreen
                        "debian" -> Color(0xFFBD00FF)
                        "ubuntu" -> Color(0xFFFF5500)
                        else -> MeetColors.cyberCyan
                    }
                    OutlinedTextField(
                        value = localCommandInput,
                        onValueChange = { localCommandInput = it },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = activeColor,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = MeetColors.backgroundDeep,
                            unfocusedContainerColor = MeetColors.backgroundDeep,
                            cursorColor = activeColor,
                            focusedBorderColor = activeColor,
                            unfocusedBorderColor = activeColor.copy(alpha = 0.25f)
                        ),
                        placeholder = {
                            Text(
                                when (activeDistro) {
                                    "android" -> "meet status, meet vin read, ls -la..."
                                    "alpine" -> "apk update, meet status, python3..."
                                    "debian", "ubuntu" -> "meet status, agy --version, python3..."
                                    else -> "Comando de consola..."
                                },
                                color = MeetColors.textMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send,
                            keyboardType = KeyboardType.Text
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                val cmd = localCommandInput.trim()
                                if (cmd.isNotEmpty()) {
                                    viewModel.localShellManager.executeCommand(cmd)
                                    localCommandInput = ""
                                }
                            }
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val cmd = localCommandInput.trim()
                            if (cmd.isNotEmpty()) {
                                viewModel.localShellManager.executeCommand(cmd)
                                localCommandInput = ""
                            }
                        },
                        enabled = localCommandInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = activeColor,
                            contentColor = MeetColors.backgroundDeep,
                            disabledContainerColor = MeetColors.backgroundDark
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(
                            "RUN",
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Contextual quick commands list
                val quickCommandsForDistro = when (activeDistro) {
                    "android" -> listOf(
                        "meet status" to "⚡ meet status",
                        "meet vin read" to "🧬 meet vin read",
                        "meet dtc scan" to "🔍 meet dtc scan",
                        "meet dtc clear" to "🧹 meet dtc clear",
                        "meet ecu ping" to "📡 meet ecu ping",
                        "meet can dump" to "📊 meet can dump",
                        "meet live rpm,speed,temp" to "📈 meet live",
                        "meet battery" to "🔋 meet battery",
                        "meet garage" to "🏎️ meet garage",
                        "termux-battery-status" to "🔋 battery",
                        "termux-toast 'Elysium Vanguard'" to "🍞 toast",
                        "termux-vibrate -d 300" to "📳 vibrate",
                        "termux-tts-speak 'Motor Antigravity Activo'" to "🗣️ tts",
                        "termux-torch on" to "🔦 torch-on",
                        "termux-torch off" to "🔦 torch-off",
                        "termux-location" to "📍 gps",
                        "uname -a" to "🐧 kernel",
                        "ls -la" to "📁 ls",
                        "df -h" to "💾 df"
                    )
                    "alpine" -> listOf(
                        "meet status" to "⚡ meet status",
                        "meet vin read" to "🧬 meet vin read",
                        "meet dtc scan" to "🔍 meet dtc scan",
                        "pkg update" to "🔄 pkg-update",
                        "pkg install htop curl" to "📦 pkg-install",
                        "ls" to "📁 ls",
                        "pwd" to "📍 pwd",
                        "whoami" to "👤 whoami"
                    )
                    "debian", "ubuntu" -> listOf(
                        "meet status" to "⚡ meet status",
                        "meet vin read" to "🧬 meet vin read",
                        "meet dtc scan" to "🔍 meet dtc scan",
                        "meet ecu ping" to "📡 meet ecu ping",
                        "agy --version" to "🛸 agy-version",
                        "agy --help" to "🛸 agy-help",
                        "termux-battery-status" to "🔋 battery",
                        "termux-toast 'Ubuntu Jammy'" to "🍞 toast",
                        "pkg update" to "🔄 pkg-update",
                        "pkg install -y htop curl git" to "📦 pkg-install",
                        "python3 -V" to "🐍 python3",
                        "startvnc" to "🖥️ startvnc",
                        "ls" to "📁 ls",
                        "pwd" to "📍 pwd"
                    )
                    else -> emptyList()
                }

                Text(
                    text = "ACCIONES RÁPIDAS (${activeDistro.uppercase()})",
                    color = MeetColors.textMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(quickCommandsForDistro) { (cmd, label) ->
                        val activeColor = when (activeDistro) {
                            "android" -> MeetColors.cyberCyan
                            "alpine" -> MeetColors.neonGreen
                            "debian" -> Color(0xFFBD00FF)
                            "ubuntu" -> Color(0xFFFF5500)
                            else -> MeetColors.cyberCyan
                        }
                        AssistChip(
                            onClick = {
                                viewModel.localShellManager.executeCommand(cmd)
                            },
                            label = {
                                Text(
                                    label,
                                    color = activeColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MeetColors.backgroundDeep
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = activeColor.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(6.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }

    // UDS WIZARD OVERLAY
    if (showUdsWizard) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(enabled = true, onClick = { showUdsWizard = false }),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MeetColors.backgroundDeep)
                    .border(
                        BorderStroke(1.5.dp, Color(0xFFBD00FF)),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .clickable(enabled = true, onClick = {}) // consume click
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "⚙️ ASISTENTE UDS / ISO 14229",
                        color = Color(0xFFBD00FF),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    IconButton(onClick = { showUdsWizard = false }) {
                        AnimatedNeonGlyph(
                            glyph = "✕",
                            contentDescription = "Cerrar",
                            tint = MeetColors.error,
                            fontSize = 18.sp,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                var selectedService by remember { mutableStateOf("22") }
                var didInput by remember { mutableStateOf("") }
                var payloadInput by remember { mutableStateOf("") }
                var selectedSession by remember { mutableStateOf("03") }
                var selectedReset by remember { mutableStateOf("01") }
                var controlOption by remember { mutableStateOf("03") }
                
                Text("Servicio UDS:", color = MeetColors.textSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "22" to "Leer DID",
                        "2E" to "Escribir",
                        "2F" to "Ctrl E/S",
                        "10" to "Sesión",
                        "11" to "Reset"
                    ).forEach { (srv, label) ->
                        val isSrvSelected = selectedService == srv
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSrvSelected) Color(0xFFBD00FF).copy(alpha = 0.2f) else MeetColors.cardBackgroundLighter)
                                .border(1.dp, if (isSrvSelected) Color(0xFFBD00FF) else MeetColors.textMuted.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .clickable { selectedService = srv }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(label, color = if (isSrvSelected) Color(0xFFBD00FF) else MeetColors.textMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    when (selectedService) {
                        "22" -> {
                            Text("Lectura de Datos por Identificador (Read Data by Identifier)", color = MeetColors.textPrimary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = didInput,
                                onValueChange = { if (it.length <= 4) didInput = it.uppercase().replace(Regex("[^0-9A-F]"), "") },
                                label = { Text("DID (4 Hex digitos, ej: F190)", color = MeetColors.textSecondary) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MeetColors.textPrimary,
                                    unfocusedTextColor = MeetColors.textSecondary,
                                    focusedContainerColor = MeetColors.backgroundDeep,
                                    unfocusedContainerColor = MeetColors.backgroundDeep
                                )
                            )
                        }
                        "2E" -> {
                            Text("Escritura de Datos por Identificador (Write Data by Identifier)", color = MeetColors.textPrimary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = didInput,
                                onValueChange = { if (it.length <= 4) didInput = it.uppercase().replace(Regex("[^0-9A-F]"), "") },
                                label = { Text("DID (4 Hex digitos, ej: F190)", color = MeetColors.textSecondary) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MeetColors.textPrimary,
                                    unfocusedTextColor = MeetColors.textSecondary,
                                    focusedContainerColor = MeetColors.backgroundDeep,
                                    unfocusedContainerColor = MeetColors.backgroundDeep
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = payloadInput,
                                onValueChange = { payloadInput = it.uppercase().replace(Regex("[^0-9A-F]"), "") },
                                label = { Text("Payload / Datos (Hex bytes)", color = MeetColors.textSecondary) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MeetColors.textPrimary,
                                    unfocusedTextColor = MeetColors.textSecondary,
                                    focusedContainerColor = MeetColors.backgroundDeep,
                                    unfocusedContainerColor = MeetColors.backgroundDeep
                                )
                            )
                        }
                        "2F" -> {
                            Text("Control de Entrada / Salida (Active Test / Actuation)", color = MeetColors.textPrimary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = didInput,
                                onValueChange = { if (it.length <= 4) didInput = it.uppercase().replace(Regex("[^0-9A-F]"), "") },
                                label = { Text("DID (4 Hex digitos, ej: 013C)", color = MeetColors.textSecondary) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MeetColors.textPrimary,
                                    unfocusedTextColor = MeetColors.textSecondary,
                                    focusedContainerColor = MeetColors.backgroundDeep,
                                    unfocusedContainerColor = MeetColors.backgroundDeep
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("03" to "Controlar", "00" to "Retornar a ECU").forEach { (opt, lbl) ->
                                    val isOptSelected = controlOption == opt
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isOptSelected) MeetColors.cyberCyan.copy(alpha = 0.2f) else MeetColors.cardBackgroundLighter)
                                            .border(1.dp, if (isOptSelected) MeetColors.cyberCyan else MeetColors.textMuted.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                            .clickable { controlOption = opt }
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(lbl, color = if (isOptSelected) MeetColors.cyberCyan else MeetColors.textMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                            if (controlOption == "03") {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = payloadInput,
                                    onValueChange = { payloadInput = it.uppercase().replace(Regex("[^0-9A-F]"), "") },
                                    label = { Text("Estado de Control (ej: 01 = ON, 00 = OFF)", color = MeetColors.textSecondary) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MeetColors.textPrimary,
                                        unfocusedTextColor = MeetColors.textSecondary,
                                        focusedContainerColor = MeetColors.backgroundDeep,
                                        unfocusedContainerColor = MeetColors.backgroundDeep
                                    )
                                )
                            }
                        }
                        "10" -> {
                            Text("Control de Sesión de Diagnóstico (Diagnostic Session Control)", color = MeetColors.textPrimary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "01" to "Default Session (Sesión estándar)",
                                    "02" to "Programming Session (Programación)",
                                    "03" to "Extended Session (Sesión Extendida)"
                                ).forEach { (sess, lbl) ->
                                    val isSessSelected = selectedSession == sess
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSessSelected) MeetColors.neonGreen.copy(alpha = 0.15f) else MeetColors.cardBackgroundLighter)
                                            .border(1.dp, if (isSessSelected) MeetColors.neonGreen else MeetColors.textMuted.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                            .clickable { selectedSession = sess }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(lbl, color = if (isSessSelected) MeetColors.neonGreen else MeetColors.textPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                        "11" -> {
                            Text("Reinicio de ECU (ECU Reset)", color = MeetColors.textPrimary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "01" to "Hard Reset (Apagar físico)",
                                    "02" to "Key Off On Reset (KOEO)",
                                    "03" to "Soft Reset (Reinicio software)"
                                ).forEach { (rst, lbl) ->
                                    val isRstSelected = selectedReset == rst
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isRstSelected) MeetColors.error.copy(alpha = 0.15f) else MeetColors.cardBackgroundLighter)
                                            .border(1.dp, if (isRstSelected) MeetColors.error else MeetColors.textMuted.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                            .clickable { selectedReset = rst }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(lbl, color = if (isRstSelected) MeetColors.error else MeetColors.textPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                val generatedCommand = remember(selectedService, didInput, payloadInput, selectedSession, selectedReset, controlOption) {
                    when (selectedService) {
                        "22" -> "22 $didInput".trim()
                        "2E" -> "2E $didInput $payloadInput".trim()
                        "2F" -> "2F $didInput $controlOption $payloadInput".trim()
                        "10" -> "10 $selectedSession".trim()
                        "11" -> "11 $selectedReset".trim()
                        else -> ""
                    }
                }
                
                Text(
                    text = "Comando generado: $generatedCommand",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MeetColors.cardBackgroundLighter)
                        .padding(8.dp)
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showUdsWizard = false },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cardBackgroundLighter),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = MeetColors.textPrimary)
                    }
                    
                    Button(
                        onClick = {
                            commandInput = generatedCommand
                            showUdsWizard = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBD00FF)),
                        enabled = generatedCommand.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cargar Comando", color = Color.White)
                    }
                }
            }
        }
    }
}
}

// ═══════════════════════════════════════
//  TERMINAL UTILS
// ═══════════════════════════════════════

private fun copyTerminalLogsToClipboard(context: Context, logs: List<TerminalLine>) {
    if (logs.isEmpty()) return
    val text = logs.joinToString("\n") { line ->
        "[${line.timestamp}] ${if (line.type == TerminalLineType.COMMAND) "❯" else if (line.type == TerminalLineType.RESPONSE) "←" else " "} ${line.text}"
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Elysium Vanguard Terminal Logs", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
}

private fun shareTerminalLogs(context: Context, logs: List<TerminalLine>) {
    if (logs.isEmpty()) return
    val text = logs.joinToString("\n") { line ->
        "[${line.timestamp}] ${if (line.type == TerminalLineType.COMMAND) "❯" else if (line.type == TerminalLineType.RESPONSE) "←" else " "} ${line.text}"
    }
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Exportar Terminal Elysium Vanguard")
    context.startActivity(shareIntent)
}

private fun copyLocalShellLogsToClipboard(context: Context, logs: List<String>) {
    if (logs.isEmpty()) return
    val text = logs.joinToString("\n")
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Elysium Vanguard Local Terminal Logs", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
}

private fun shareLocalShellLogs(context: Context, logs: List<String>) {
    if (logs.isEmpty()) return
    val text = logs.joinToString("\n")
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Exportar Terminal Elysium Vanguard Android")
    context.startActivity(shareIntent)
}

// ═══════════════════════════════════════
//  TERMINAL LINE RENDERING
// ═══════════════════════════════════════

@Composable
private fun TerminalLineRow(line: TerminalLine, glowAlpha: Float) {
    when (line.type) {
        TerminalLineType.EXPLANATION -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp, horizontal = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MeetColors.cyberCyan.copy(alpha = 0.08f),
                                MeetColors.electricBlue.copy(alpha = 0.05f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        MeetColors.cyberCyan.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = line.text,
                    color = MeetColors.cyberCyan.copy(alpha = 0.9f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
        TerminalLineType.DECODED -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp, horizontal = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MeetColors.electricBlue.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        MeetColors.electricBlue.copy(alpha = 0.25f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "📡 ${line.text}",
                    color = MeetColors.electricBlue.copy(alpha = 0.95f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 16.sp
                )
            }
        }
        else -> {
            val lineColor = when (line.type) {
                TerminalLineType.SYSTEM -> MeetColors.cyberCyan.copy(alpha = 0.7f)
                TerminalLineType.COMMAND -> MeetColors.neonGreen
                TerminalLineType.RESPONSE -> MeetColors.textPrimary
                TerminalLineType.ERROR -> MeetColors.error
                TerminalLineType.WARNING -> MeetColors.warning
                else -> MeetColors.textPrimary
            }
            val prefix = when (line.type) {
                TerminalLineType.COMMAND -> "❯ "
                TerminalLineType.RESPONSE -> "  ← "
                TerminalLineType.ERROR -> "  ✗ "
                TerminalLineType.WARNING -> "  ⚠ "
                TerminalLineType.SYSTEM -> "  "
                else -> "  "
            }
            val fontWeight = when (line.type) {
                TerminalLineType.COMMAND -> FontWeight.Bold
                else -> FontWeight.Normal
            }

            Row(modifier = Modifier.padding(vertical = 1.dp)) {
                Text(
                    text = "[${line.timestamp}] ",
                    color = MeetColors.textMuted.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
                Text(
                    text = "$prefix${line.text}",
                    color = lineColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = fontWeight,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun TerminalFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    activeColor: Color
) {
    val backgroundColor = if (selected) activeColor.copy(alpha = 0.15f) else MeetColors.backgroundDeep
    val borderColor = if (selected) activeColor else MeetColors.textMuted.copy(alpha = 0.2f)
    val textColor = if (selected) activeColor else MeetColors.textMuted

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
