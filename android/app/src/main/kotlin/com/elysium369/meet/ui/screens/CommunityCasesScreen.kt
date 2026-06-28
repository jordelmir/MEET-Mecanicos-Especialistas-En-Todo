package com.elysium369.meet.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.RepairNetworkViewModel
import com.elysium369.meet.ui.components.AnimatedNeonIcon
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CommunityCasesScreen(
    navController: NavController,
    viewModel: RepairNetworkViewModel
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val casesList by viewModel.casesList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showFilters by remember { mutableStateOf(false) }
    val makeFilter by viewModel.makeFilter.collectAsState()
    val modelFilter by viewModel.modelFilter.collectAsState()
    val dtcFilter by viewModel.dtcFilter.collectAsState()
    val countryFilter by viewModel.countryFilter.collectAsState()
    val sortByFilter by viewModel.sortByFilter.collectAsState()
    val onlyVerifiedFilter by viewModel.onlyVerifiedFilter.collectAsState()

    Scaffold(
        containerColor = MeetColors.backgroundDark,
        topBar = {
            EliteTopAppBar(
                title = "📚 CASOS COMUNITARIOS Y SOLUCIONES\nBase de Conocimiento Verificada",
                onBackClick = { navController.popBackStack() },
                backgroundColor = MeetColors.backgroundDark,
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        AnimatedNeonIcon(
                            Icons.Default.FilterList,
                            contentDescription = "Filtros",
                            tint = if (showFilters) MeetColors.neonGreen else Color.White
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("contribute_case") },
                containerColor = MeetColors.backgroundDark,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .border(1.dp, MeetColors.neonGreen, RoundedCornerShape(12.dp))
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AnimatedNeonIcon(Icons.Default.Add, contentDescription = "Publicar", tint = MeetColors.neonGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PUBLICAR CASO", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            stickyHeader {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Buscar DTC, Síntomas, Repuestos o Marcas...", color = MeetColors.textSecondary) },
                    leadingIcon = { AnimatedNeonIcon(Icons.Default.Search, contentDescription = "Buscar", tint = MeetColors.neonGreen) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                AnimatedNeonIcon(Icons.Default.Clear, contentDescription = "Limpiar", tint = MeetColors.textSecondary)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MeetColors.backgroundDark)
                        .padding(bottom = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MeetColors.neonGreen,
                        unfocusedBorderColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                        focusedContainerColor = MeetColors.backgroundDeep,
                        unfocusedContainerColor = MeetColors.backgroundDeep
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (showFilters) {
                item {
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MeetColors.cyberCyan,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "FILTROS AVANZADOS DE CASOS",
                                color = MeetColors.cyberCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = makeFilter,
                                    onValueChange = { viewModel.setMakeFilter(it) },
                                    label = { Text("Marca", fontSize = 10.sp, color = MeetColors.textSecondary) },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                                OutlinedTextField(
                                    value = modelFilter,
                                    onValueChange = { viewModel.setModelFilter(it) },
                                    label = { Text("Modelo", fontSize = 10.sp, color = MeetColors.textSecondary) },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MeetColors.neonGreen)
                    }
                }
            } else if (casesList.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No se encontraron casos comunitarios para tu búsqueda.", color = MeetColors.textSecondary)
                    }
                }
            } else {
                items(casesList) { case ->
                    EliteCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectCase(case.id)
                                navController.navigate("repair_case_detail/${case.id}")
                            },
                        glowColor = if (case.verified) MeetColors.neonGreen else MeetColors.electricBlue,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp)
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
                                    fontSize = 18.sp
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (case.verified) {
                                        Text("✔️ VERIFICADO", color = MeetColors.neonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MeetColors.backgroundDark)
                                            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.upvoteCase(case.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.ThumbUp, contentDescription = "Votar a favor", tint = MeetColors.neonGreen, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = case.votes.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${case.vehicle_make} ${case.vehicle_model} (${case.year}) — ${case.engine}",
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Síntomas: ${case.symptoms}",
                                color = MeetColors.textSecondary,
                                maxLines = 2,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Costo est.: $${case.cost.toInt()} USD", color = MeetColors.cyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Éxito: ${case.success_rate.toInt()}%", color = MeetColors.neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("📍 ${case.country}", color = MeetColors.textSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
