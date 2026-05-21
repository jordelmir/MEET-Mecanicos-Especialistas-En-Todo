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
import com.elysium369.meet.ui.theme.MeetColors
import java.text.SimpleDateFormat
import java.util.*

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

    // Auto-scroll to bottom
    LaunchedEffect(terminalOutput.size) {
        if (terminalOutput.isNotEmpty()) {
            listState.animateScrollToItem(terminalOutput.size - 1)
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
                    TextButton(
                        onClick = {
                            copyTerminalLogsToClipboard(context, terminalOutput)
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
                            shareTerminalLogs(context, terminalOutput)
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
                            viewModel.clearTerminalLogs()
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

        Spacer(modifier = Modifier.height(8.dp))

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
                items(terminalOutput) { line ->
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
                                    response.contains("UNABLE", true) -> TerminalLineType.WARNING
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

        Spacer(modifier = Modifier.height(4.dp))
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
    val clip = ClipData.newPlainText("MEET Terminal Logs", text)
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
    val shareIntent = Intent.createChooser(sendIntent, "Exportar Terminal MEET")
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
