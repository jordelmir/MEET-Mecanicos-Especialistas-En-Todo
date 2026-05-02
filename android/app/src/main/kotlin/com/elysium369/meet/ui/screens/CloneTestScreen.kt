package com.elysium369.meet.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TestResult(val name: String, val result: String, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneTestScreen(
    onRunTest: suspend () -> List<TestResult>
) {
    var isRunning by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<TestResult>>(emptyList()) }
    var progress by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    // Derive overall verdict
    val passCount = results.count { it.color == Color(0xFF39FF14) || it.color == Color.Green }
    val warnCount = results.count { it.color == Color(0xFFFFAA00) || it.color == Color.Yellow }
    val failCount = results.count { it.color == Color(0xFFFF003C) || it.color == Color.Red }
    val totalTests = results.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Diagnóstico de Adaptador", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Verificación de Hardware OBD2", color = Color(0xFFFF6B35), fontSize = 11.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E1A))
            )
        },
        containerColor = Color(0xFF060612)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // --- Info Card ---
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFFF6B35).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🔬 Modo de Prueba de Campo",
                        color = Color(0xFFFF6B35),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Verifica la calidad de tu adaptador OBD2. Los clones baratos (ELM327 v1.5/v2.1) " +
                        "tienen problemas de estabilidad, latencia y compatibilidad de protocolos. " +
                        "Esta prueba evalúa la respuesta del firmware, soporte de protocolos y velocidad de comunicación.",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Run Button ---
            Button(
                onClick = {
                    isRunning = true
                    results = emptyList()
                    progress = 0f
                    scope.launch {
                        // Animate progress while tests run
                        val progressJob = launch {
                            var p = 0f
                            while (p < 0.95f) {
                                delay(200)
                                p += 0.05f
                                progress = p.coerceAtMost(0.95f)
                            }
                        }
                        results = onRunTest()
                        progressJob.cancel()
                        progress = 1f
                        isRunning = false
                    }
                },
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6B35),
                    disabledContainerColor = Color(0xFF333333)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("EJECUTANDO PRUEBAS...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                } else {
                    Text("⚡ INICIAR DIAGNÓSTICO", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // --- Progress Bar ---
            AnimatedVisibility(visible = isRunning) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFFFF6B35),
                    trackColor = Color(0xFF1A1A2E)
                )
            }

            // --- Results Summary ---
            AnimatedVisibility(visible = results.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .border(
                            1.dp,
                            when {
                                failCount > 0 -> Color(0xFFFF003C)
                                warnCount > 0 -> Color(0xFFFFAA00)
                                else -> Color(0xFF39FF14)
                            }.copy(alpha = 0.5f),
                            RoundedCornerShape(10.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pass
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$passCount",
                                color = Color(0xFF39FF14),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("PASS", color = Color(0xFF39FF14), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(Color.Gray.copy(alpha = 0.3f))
                        )
                        // Warn
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$warnCount",
                                color = Color(0xFFFFAA00),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("WARN", color = Color(0xFFFFAA00), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(Color.Gray.copy(alpha = 0.3f))
                        )
                        // Fail
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$failCount",
                                color = Color(0xFFFF003C),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("FAIL", color = Color(0xFFFF003C), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(Color.Gray.copy(alpha = 0.3f))
                        )
                        // Verdict
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val verdictColor = when {
                                failCount > 0 -> Color(0xFFFF003C)
                                warnCount > totalTests / 2 -> Color(0xFFFFAA00)
                                else -> Color(0xFF39FF14)
                            }
                            val verdictLabel = when {
                                failCount > 0 -> "CLON"
                                warnCount > totalTests / 2 -> "SOSPECHOSO"
                                else -> "GENUINO"
                            }
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(verdictColor)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(verdictLabel, color = verdictColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- Test Results List ---
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(results) { res ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, res.color.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(14.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Status dot
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(res.color)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    res.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    res.result,
                                    color = res.color.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
