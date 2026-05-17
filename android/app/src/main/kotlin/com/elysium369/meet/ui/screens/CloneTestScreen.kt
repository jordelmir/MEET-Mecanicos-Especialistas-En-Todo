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
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteTopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.elysium369.meet.ui.theme.MeetColors
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
    val passCount = results.count { it.color == com.elysium369.meet.ui.theme.MeetColors.neonGreen || it.color == com.elysium369.meet.ui.theme.MeetColors.neonGreen }
    val warnCount = results.count { it.color == MeetColors.warning || it.color == com.elysium369.meet.ui.theme.MeetColors.warning }
    val failCount = results.count { it.color == com.elysium369.meet.ui.theme.MeetColors.error || it.color == com.elysium369.meet.ui.theme.MeetColors.error }
    val totalTests = results.size

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "Diagnóstico de Adaptador",
                subtitle = "Verificación de Hardware OBD2",
                backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark
            )
        },
        containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // --- Info Card ---
            EliteCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = MeetColors.warning.copy(alpha = 0.5f),
                glowColor = MeetColors.warning
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🔬 Modo de Prueba de Campo",
                        color = MeetColors.warning,
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
            EliteButton(
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
                isEnabled = !isRunning,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                color = MeetColors.warning,
                text = if (isRunning) "EJECUTANDO PRUEBAS..." else "⚡ INICIAR DIAGNÓSTICO"
            )

            // --- Progress Bar ---
            AnimatedVisibility(visible = isRunning) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MeetColors.warning,
                    trackColor = MeetColors.backgroundDeep
                )
            }

            // --- Results Summary ---
            AnimatedVisibility(visible = results.isNotEmpty()) {
                val resultColor = when {
                    failCount > 0 -> com.elysium369.meet.ui.theme.MeetColors.error
                    warnCount > 0 -> MeetColors.warning
                    else -> com.elysium369.meet.ui.theme.MeetColors.neonGreen
                }
                EliteCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    borderColor = resultColor.copy(alpha = 0.5f),
                    glowColor = resultColor
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
                                color = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("PASS", color = com.elysium369.meet.ui.theme.MeetColors.neonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(com.elysium369.meet.ui.theme.MeetColors.borderBlue)
                        )
                        // Warn
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$warnCount",
                                color = MeetColors.warning,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("WARN", color = MeetColors.warning, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(com.elysium369.meet.ui.theme.MeetColors.borderBlue)
                        )
                        // Fail
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$failCount",
                                color = com.elysium369.meet.ui.theme.MeetColors.error,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("FAIL", color = com.elysium369.meet.ui.theme.MeetColors.error, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(com.elysium369.meet.ui.theme.MeetColors.borderBlue)
                        )
                        // Verdict
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val verdictColor = when {
                                failCount > 0 -> com.elysium369.meet.ui.theme.MeetColors.error
                                warnCount > totalTests / 2 -> MeetColors.warning
                                else -> com.elysium369.meet.ui.theme.MeetColors.neonGreen
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
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = res.color.copy(alpha = 0.5f),
                        glowColor = res.color
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
