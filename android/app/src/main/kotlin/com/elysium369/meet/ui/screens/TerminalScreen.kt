package com.elysium369.meet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.core.obd.ObdState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(viewModel: ObdViewModel) {
    var commandInput by remember { mutableStateOf("") }
    var terminalOutput by remember { mutableStateOf(listOf<String>("> Terminal OBD2 Inicializada. Escribe un comando crudo (ej: 010C, AT RV)")) }
    var isSending by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.connectionState.collectAsState()

    val quickCommands = listOf("ATZ", "AT RV", "ATSP0", "0100", "010C", "0105", "03", "04")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Text(
            text = "Terminal Raw OBD2",
            color = Color(0xFFFF6B35), // Naranja Técnico
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Terminal Output Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true
            ) {
                items(terminalOutput.reversed()) { line ->
                    Text(
                        text = line,
                        color = if (line.startsWith(">")) Color.Green else Color.LightGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Input Area
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = commandInput,
                onValueChange = { commandInput = it.uppercase() },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E),
                    cursorColor = Color(0xFFFF6B35),
                    focusedIndicatorColor = Color(0xFFFF6B35)
                ),
                placeholder = { Text("ATZ, 010C...", color = Color.Gray) },
                singleLine = true
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Button(
                onClick = {
                    val cmd = commandInput.trim()
                    if (cmd.isNotEmpty()) {
                        terminalOutput = terminalOutput + listOf("> $cmd")
                        commandInput = ""
                        
                        if (state != ObdState.CONNECTED) {
                            terminalOutput = terminalOutput + listOf("ERROR: OBD is not connected")
                            return@Button
                        }
                        
                        isSending = true
                        coroutineScope.launch {
                            try {
                                val response = viewModel.sendRawCommand(cmd)
                                terminalOutput = terminalOutput + listOf(response)
                            } catch (e: Exception) {
                                terminalOutput = terminalOutput + listOf("ERROR: ${e.message}")
                            } finally {
                                isSending = false
                            }
                        }
                    }
                },
                enabled = !isSending && commandInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6B35),
                    disabledContainerColor = Color.DarkGray
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("ENVIAR", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Quick commands chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(quickCommands) { cmd ->
                AssistChip(
                    onClick = { commandInput = cmd },
                    label = { Text(cmd, color = Color.White) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF2C2C2C))
                )
            }
        }
    }
}
