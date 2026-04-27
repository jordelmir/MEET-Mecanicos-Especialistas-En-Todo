package com.elysium369.meet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.elysium369.meet.R

data class ProFeature(val id: String, val title: String, val icon: String, val color: Color, val route: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProHubScreen(navController: NavController, viewModel: com.elysium369.meet.ui.ObdViewModel) {
    val isPro by viewModel.isAdapterPro.collectAsState()
    
    val proFeatures = listOf(
        ProFeature("topology", "Mapeo\nTopológico", "🕸️", Color(0xFF00FFCC), "topology"),
        ProFeature("active_tests", "Pruebas\nActivas", "⚙️", Color(0xFFFF003C), "active_tests"),
        ProFeature("resets", "Service\nResets", "🛠️", Color(0xFFFFD700), "service_resets"),
        ProFeature("reports", "Reportes\nPDF", "📄", Color(0xFFCC00FF), "reports"),
        ProFeature("ai", "IA\nDiagnóstico", "🧠", Color(0xFFCC00FF), "ai"),
        ProFeature("dashboard", "Dashboards\nElite", "📈", Color(0xFF00FFCC), "custom_pid")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MEET PRO ELITE", color = Color.White, fontWeight = FontWeight.Black) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            
            // ELITE LOGO
            Image(
                painter = painterResource(id = R.drawable.meet_elite_logo),
                contentDescription = "Meet Elite Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Color(0xFF00FFCC), RoundedCornerShape(16.dp))
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            if (!isPro) {
                Surface(
                    color = Color(0xFFFF003C).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFFF003C), RoundedCornerShape(8.dp)).padding(bottom = 16.dp)
                ) {
                    Text(
                        "ADVERTENCIA: Adaptador CLON detectado. Las funciones avanzadas UDS/OEM están limitadas. Para una experiencia profesional completa, usa un adaptador Vgate vLinker o OBDLink.",
                        color = Color(0xFFFF003C),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Surface(
                    color = Color(0xFF00FFCC).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp)).padding(bottom = 16.dp)
                ) {
                    Text(
                        "✓ ADAPTADOR PROFESIONAL DETECTADO. Acceso Total Concedido.",
                        color = Color(0xFF00FFCC),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text("Funciones Nivel Agencia", color = Color(0xFF00FFCC), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Herramientas de diagnóstico avanzado, controles bidireccionales y reportes de nivel mundial.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(24.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(proFeatures) { feature ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .border(1.dp, feature.color.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .clickable { navController.navigate(feature.route) }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(feature.icon, style = MaterialTheme.typography.displayMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                feature.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
