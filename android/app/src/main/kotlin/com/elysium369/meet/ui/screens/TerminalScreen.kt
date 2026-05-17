package com.elysium369.meet.ui.screens

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.theme.MeetColors
import java.text.SimpleDateFormat
import java.util.*

data class TerminalLine(
    val text: String,
    val type: TerminalLineType,
    val timestamp: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
)

enum class TerminalLineType {
    SYSTEM,   // System messages (cyan)
    COMMAND,  // User commands (green/neon)
    RESPONSE, // OBD responses (white)
    ERROR,    // Errors (red)
    WARNING   // Warnings (yellow)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(viewModel: ObdViewModel) {
    var commandInput by remember { mutableStateOf("") }
    var terminalOutput by remember {
        mutableStateOf(
            listOf(
                TerminalLine("MEET Terminal v2.0 — Raw OBD2 Interface", TerminalLineType.SYSTEM),
                TerminalLine("Escribe un comando AT o PID (ej: ATZ, 010C, AT RV)", TerminalLineType.SYSTEM),
                TerminalLine("─────────────────────────────────────────", TerminalLineType.SYSTEM)
            )
        )
    }
    var isSending by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.connectionState.collectAsState()
    val listState = rememberLazyListState()

    val quickCommands = listOf("ATZ", "AT RV", "ATSP0", "ATDP", "0100", "010C", "0105", "010D", "03", "04", "09 02")

    // Auto-scroll to bottom
    LaunchedEffect(terminalOutput.size) {
        if (terminalOutput.isNotEmpty()) {
            listState.animateScrollToItem(terminalOutput.size - 1)
        }
    }

    val statusColor = when (state) {
        ObdState.CONNECTED -> com.elysium369.meet.ui.theme.MeetColors.neonGreen
        ObdState.CONNECTING -> MeetColors.warning
        else -> com.elysium369.meet.ui.theme.MeetColors.error
    }
    val statusText = when (state) {
        ObdState.CONNECTED -> "● CONECTADO"
        ObdState.CONNECTING -> "● CONECTANDO..."
        ObdState.DISCONNECTED -> "● DESCONECTADO"
        ObdState.ERROR -> "● ERROR"
        else -> "● IDLE"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.carbonGradient)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // --- Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Terminal Raw OBD2",
                    color = MeetColors.warning,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            }
            // Clear button
            TextButton(
                onClick = {
                    terminalOutput = listOf(
                        TerminalLine("Terminal limpiada.", TerminalLineType.SYSTEM)
                    )
                }
            ) {
                Text("LIMPIAR", color = com.elysium369.meet.ui.theme.MeetColors.error, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        // --- Terminal Output Area ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(com.elysium369.meet.ui.theme.MeetColors.backgroundDark)
                .border(1.dp, MeetColors.warning.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(terminalOutput) { line ->
                    val lineColor = when (line.type) {
                        TerminalLineType.SYSTEM -> MeetColors.electricBlue
                        TerminalLineType.COMMAND -> MeetColors.neonGreen
                        TerminalLineType.RESPONSE -> MeetColors.textPrimary
                        TerminalLineType.ERROR -> MeetColors.error
                        TerminalLineType.WARNING -> MeetColors.warning
                    }
                    val prefix = when (line.type) {
                        TerminalLineType.COMMAND -> "❯ "
                        TerminalLineType.RESPONSE -> "  ← "
                        TerminalLineType.ERROR -> "  ✗ "
                        TerminalLineType.WARNING -> "  ⚠ "
                        TerminalLineType.SYSTEM -> "  "
                    }
                    Row(modifier = Modifier.padding(vertical = 1.dp)) {
                        // Timestamp
                        Text(
                            text = "[${line.timestamp}] ",
                            color = MeetColors.textMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                        // Content
                        Text(
                            text = "$prefix${line.text}",
                            color = lineColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Input Area ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it.uppercase() },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark,
                    unfocusedContainerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark,
                    cursorColor = MeetColors.warning,
                    focusedBorderColor = MeetColors.warning,
                    unfocusedBorderColor = MeetColors.warning.copy(alpha = 0.3f)
                ),
                placeholder = {
                    Text("ATZ, 010C, AT RV...", color = MeetColors.textMuted, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                shape = RoundedCornerShape(8.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Button(
                onClick = {
                    val cmd = commandInput.trim()
                    if (cmd.isNotEmpty()) {
                        terminalOutput = terminalOutput + TerminalLine(cmd, TerminalLineType.COMMAND)
                        commandInput = ""
                        
                        if (state != ObdState.CONNECTED) {
                            terminalOutput = terminalOutput + TerminalLine(
                                "OBD no conectado. Conecta el adaptador primero.",
                                TerminalLineType.ERROR
                            )
                            return@Button
                        }
                        
                        isSending = true
                        coroutineScope.launch {
                            try {
                                val response = viewModel.sendRawCommand(cmd)
                                terminalOutput = terminalOutput + TerminalLine(
                                    response,
                                    if (response.contains("ERROR") || response.contains("NO DATA") || response.contains("UNABLE"))
                                        TerminalLineType.WARNING
                                    else
                                        TerminalLineType.RESPONSE
                                )
                            } catch (e: Exception) {
                                terminalOutput = terminalOutput + TerminalLine(
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
                    containerColor = MeetColors.warning,
                    disabledContainerColor = MeetColors.backgroundDark
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(56.dp)
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("TX", fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // --- Quick Commands ---
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(quickCommands) { cmd ->
                AssistChip(
                    onClick = { commandInput = cmd },
                    label = {
                        Text(cmd, color = MeetColors.warning, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MeetColors.warning.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(6.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
    }
}
