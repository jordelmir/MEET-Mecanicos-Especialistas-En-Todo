package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.RepairNetworkViewModel
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.PhantomSectionHeader
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.neonGlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairCaseDetailScreen(navController: NavController, caseId: String, viewModel: RepairNetworkViewModel) {
    val activeCase by viewModel.activeCaseDetails.collectAsState()
    val comments by viewModel.activeCaseComments.collectAsState()
    val isBookmarked by viewModel.isBookmarkedState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var newCommentText by remember { mutableStateOf("") }
    var authorNameText by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = MeetColors.backgroundDark,
        topBar = {
            EliteTopAppBar(
                title = "DETALLE DE CASO",
                onBackClick = { navController.popBackStack() },
                backgroundColor = MeetColors.backgroundDark,
                actions = {
                    activeCase?.let { case ->
                        IconButton(onClick = { viewModel.toggleBookmark(case) }) {
                            AnimatedNeonIcon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Guardar",
                                tint = if (isBookmarked) MeetColors.neonGreen else Color.White
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading && activeCase == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MeetColors.neonGreen)
            }
        } else if (activeCase == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Error al cargar el caso. Intente nuevamente.",
                    color = MeetColors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val case = activeCase!!
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Main Header Info
                EliteCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = if (case.verified) MeetColors.neonGreen else MeetColors.electricBlue,
                    backgroundColor = MeetColors.backgroundDeep,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = case.dtc_code.uppercase(),
                                color = if (case.verified) MeetColors.neonGreen else MeetColors.electricBlue,
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp
                            )
                            if (case.verified) {
                                Text(
                                    "✔️ VERIFICADO",
                                    color = MeetColors.neonGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${case.vehicle_make} ${case.vehicle_model} (${case.year})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Motor: ${case.engine}",
                            color = MeetColors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }

                // Summary Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Cost Box
                    Box(modifier = Modifier.weight(1f)) {
                        EliteCard(
                            modifier = Modifier.fillMaxWidth(),
                            glowColor = MeetColors.cyberCyan,
                            backgroundColor = MeetColors.backgroundDeep,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AnimatedNeonIcon(Icons.Default.AttachMoney, contentDescription = "Costo", tint = MeetColors.cyberCyan, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("COSTO EST.", color = MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("$${case.cost.toInt()} USD", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Success Rate Box
                    Box(modifier = Modifier.weight(1f)) {
                        EliteCard(
                            modifier = Modifier.fillMaxWidth(),
                            glowColor = MeetColors.neonGreen,
                            backgroundColor = MeetColors.backgroundDeep,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AnimatedNeonIcon(Icons.Default.CheckCircle, contentDescription = "Tasa", tint = MeetColors.neonGreen, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("TASA ÉXITO", color = MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${case.success_rate.toInt()}%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Time Spent Box
                    Box(modifier = Modifier.weight(1f)) {
                        EliteCard(
                            modifier = Modifier.fillMaxWidth(),
                            glowColor = MeetColors.warning,
                            backgroundColor = MeetColors.backgroundDeep,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AnimatedNeonIcon(Icons.Default.AccessTime, contentDescription = "Tiempo", tint = MeetColors.warning, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("TIEMPO", color = MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${case.time_spent} min", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Vote Actions Card
                EliteCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = MeetColors.cyberCyan,
                    backgroundColor = MeetColors.backgroundDeep,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "¿Te sirvió esta solución?",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.downvoteCase(case.id) },
                                modifier = Modifier.background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            ) {
                                AnimatedNeonIcon(Icons.Default.ThumbDown, contentDescription = "Voto Negativo", tint = MeetColors.error)
                            }
                            Text(
                                text = case.votes.toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = { viewModel.upvoteCase(case.id) },
                                modifier = Modifier.background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            ) {
                                AnimatedNeonIcon(Icons.Default.ThumbUp, contentDescription = "Voto Positivo", tint = MeetColors.neonGreen)
                            }
                        }
                    }
                }

                // Symptoms
                Column(modifier = Modifier.fillMaxWidth()) {
                    PhantomSectionHeader(label = "Síntomas Reportados", accentColor = MeetColors.cyberCyan)
                    Spacer(modifier = Modifier.height(6.dp))
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MeetColors.cyberCyan.copy(alpha = 0.2f),
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = case.symptoms,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp),
                            lineHeight = 20.sp
                        )
                    }
                }

                // Solution Step-by-Step
                Column(modifier = Modifier.fillMaxWidth()) {
                    PhantomSectionHeader(label = "Solución Aplicada", accentColor = MeetColors.neonGreen)
                    Spacer(modifier = Modifier.height(6.dp))
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MeetColors.neonGreen.copy(alpha = 0.2f),
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = case.solution,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp),
                            lineHeight = 20.sp
                        )
                    }
                }

                // Parts Used
                if (case.parts_used.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        PhantomSectionHeader(label = "Repuestos Utilizados", accentColor = MeetColors.warning)
                        Spacer(modifier = Modifier.height(6.dp))
                        EliteCard(
                            modifier = Modifier.fillMaxWidth(),
                            glowColor = MeetColors.warning.copy(alpha = 0.2f),
                            backgroundColor = MeetColors.backgroundDeep,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = case.parts_used,
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(16.dp),
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                // Country / Date Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📍 País: ${case.country}",
                        color = MeetColors.textSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "ID: ${case.id.take(8)}...",
                        color = MeetColors.textMuted,
                        fontSize = 11.sp
                    )
                }

                Divider(color = MeetColors.borderSubtle, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                // Comments Thread
                Column(modifier = Modifier.fillMaxWidth()) {
                    PhantomSectionHeader(label = "Comentarios y Consejos (${comments.size})", accentColor = MeetColors.electricBlue)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Write Comment Box
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MeetColors.electricBlue.copy(alpha = 0.3f),
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Agregar Consejo Mecánico",
                                color = MeetColors.electricBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )

                            OutlinedTextField(
                                value = authorNameText,
                                onValueChange = { authorNameText = it },
                                placeholder = { Text("Su nombre (Ej. Taller Mecánico Ruiz)", color = MeetColors.textSecondary, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MeetColors.electricBlue,
                                    unfocusedBorderColor = MeetColors.borderSubtle,
                                    focusedContainerColor = MeetColors.backgroundDark,
                                    unfocusedContainerColor = MeetColors.backgroundDark
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )

                            OutlinedTextField(
                                value = newCommentText,
                                onValueChange = { newCommentText = it },
                                placeholder = { Text("Escribe tu recomendación técnica o duda sobre este caso...", color = MeetColors.textSecondary, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth().height(90.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MeetColors.electricBlue,
                                    unfocusedBorderColor = MeetColors.borderSubtle,
                                    focusedContainerColor = MeetColors.backgroundDark,
                                    unfocusedContainerColor = MeetColors.backgroundDark
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )

                            EliteButton(
                                text = "Enviar Comentario",
                                onClick = {
                                    val reputation = viewModel.getReputationTitle("me", totalCasesByAuthor = 1)
                                    viewModel.submitComment(case.id, authorNameText, reputation, newCommentText)
                                    newCommentText = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                                color = MeetColors.electricBlue,
                                isEnabled = newCommentText.isNotBlank()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Comments List
                    if (comments.isEmpty()) {
                        Text(
                            text = "No hay comentarios todavía. ¡Sé el primero en aconsezar a la comunidad!",
                            color = MeetColors.textSecondary,
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            comments.forEach { comment ->
                                EliteCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    enableHolo3D = false,
                                    backgroundColor = MeetColors.backgroundDeep,
                                    borderColor = MeetColors.borderSubtle,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = comment.author_name,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = comment.author_reputation,
                                                color = MeetColors.cyberCyan,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = comment.comment_body,
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
