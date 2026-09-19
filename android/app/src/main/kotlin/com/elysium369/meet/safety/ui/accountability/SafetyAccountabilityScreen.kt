package com.elysium369.meet.safety.ui.accountability

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyAccountabilityScreen(
    onBack: () -> Unit = {},
) {
    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ACCOUNTABILITY", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color.White)
                        Text("Cronologías & seguimiento institucional", fontSize = 11.sp, color = MeetColors.cyberCyan)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("EVENTOS OBSERVABLES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "El ledger de accountability muestra eventos observables: envío de reporte, confirmación de recepción, respuesta documentada, acción pública encontrada.",
                            fontSize = 13.sp,
                            color = MeetColors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Las interpretaciones viven como claims. El sistema muestra lo observable.",
                            fontSize = 13.sp,
                            color = MeetColors.neonGreen,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }
        }
    }
}
