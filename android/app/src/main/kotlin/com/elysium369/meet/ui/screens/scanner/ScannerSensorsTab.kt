package com.elysium369.meet.ui.screens.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.obd.PidRegistry
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.WaveGraphWidget

@Composable
fun ScannerSensorsTab(
    viewModel: ObdViewModel,
    defaultGauges: List<GaugeConfig>
) {
    val liveData by viewModel.liveData.collectAsState()
    val pinnedPids by viewModel.pinnedPids.collectAsState()
    val telemetryHistory by viewModel.telemetryHistory.collectAsState()
    val anomalousPids by viewModel.anomalousPids.collectAsState()
    
    var expandedPid by remember { mutableStateOf<String?>(null) }
    
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { 
            Text(
                "TELEMETRÍA EN TIEMPO REAL", 
                color = Color(0xFF00FFCC).copy(alpha = 0.5f), 
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = FontWeight.Bold, 
                modifier = Modifier.padding(bottom = 12.dp)
            ) 
        }
        
        items(defaultGauges.size) { index ->
            val gauge = defaultGauges[index]
            val currentValue = liveData[gauge.pid] ?: 0f
            val isPinned = pinnedPids.contains(gauge.pid)
            val isExpanded = expandedPid == gauge.pid
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .border(
                        1.dp, 
                        if (isExpanded) Color(0xFF00FFCC) else if (isPinned) Color(0xFF00FFCC).copy(alpha = 0.4f) else Color(0xFF00FFCC).copy(alpha = 0.15f), 
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { expandedPid = if (isExpanded) null else gauge.pid }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column { 
                        Text(gauge.label, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isExpanded) FontWeight.Bold else FontWeight.Normal)
                        Text("PID: ${gauge.pid}", color = Color.DarkGray, style = MaterialTheme.typography.labelSmall) 
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${String.format("%.1f", currentValue)} ${gauge.unit}", color = if (isExpanded) Color.White else Color(0xFF00FFCC), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { if (isPinned) viewModel.unpinPid(gauge.pid) else viewModel.pinPid(gauge.pid) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text(if (isPinned) "📌" else "📍", fontSize = 16.sp)
                        }
                    }
                }
                
                if (isExpanded) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        val pidDef = PidRegistry.getPid("01", gauge.pid)
                        
                        WaveGraphWidget(
                            label = "HISTORIAL ${gauge.label}",
                            currentValue = currentValue,
                            minVal = gauge.minVal,
                            maxVal = gauge.maxVal,
                            unit = gauge.unit,
                            warningThreshold = pidDef?.warningThreshold,
                            criticalThreshold = pidDef?.criticalThreshold,
                            isAnomaly = anomalousPids.any { it.pid == gauge.pid },
                            historyData = telemetryHistory[gauge.pid]
                        )
                    }
                    
                    if (!isPinned) {
                        Text(
                            "Pinna este PID para activar telemetría de alta velocidad",
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}
