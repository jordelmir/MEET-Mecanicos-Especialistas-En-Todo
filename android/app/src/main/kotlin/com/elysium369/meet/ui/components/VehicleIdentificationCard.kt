package com.elysium369.meet.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.elysium369.meet.ui.theme.MeetColors
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.ObdViewModel

/**
 * Vehicle Identification Card — Car Scanner Pro style.
 * Automatically shows vehicle identity data after OBD2 connection.
 * Displays: OBD2 Protocol, VIN, Calibration ID, ECU Name, Adapter info.
 */
@Composable
fun VehicleIdentificationCard(
    viewModel: ObdViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.connectionState.collectAsState()
    val vin by viewModel.vin.collectAsState()
    val protocol by viewModel.detectedProtocol.collectAsState()
    val calibrationId by viewModel.calibrationId.collectAsState()
    val ecuName by viewModel.ecuName.collectAsState()
    val adapterVer by viewModel.adapterVersion.collectAsState()
    val isClone by viewModel.isCloneAdapter.collectAsState()
    val vehicle by viewModel.selectedVehicle.collectAsState()

    val isVisible = state == ObdState.CONNECTED && (vin != null || protocol.isNotBlank())

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        com.elysium369.meet.ui.components.EliteCard(
            backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark,
            borderColor = com.elysium369.meet.ui.theme.MeetColors.electricBlue.copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp),
            modifier = modifier.fillMaxWidth(),
            glowColor = com.elysium369.meet.ui.theme.MeetColors.electricBlue
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "IDENTIFICACIÓN DEL VEHÍCULO",
                        color = com.elysium369.meet.ui.theme.MeetColors.electricBlue.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    // Clone badge
                    if (isClone && adapterVer.isNotBlank()) {
                        Surface(
                            color = com.elysium369.meet.ui.theme.MeetColors.error.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.border(1.dp, com.elysium369.meet.ui.theme.MeetColors.error.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        ) {
                            Text(
                                "⚠ CLON",
                                color = com.elysium369.meet.ui.theme.MeetColors.error,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Vehicle name if available
                if (vehicle != null) {
                    Text(
                        "${vehicle?.make ?: ""} ${vehicle?.model ?: ""} ${vehicle?.year ?: ""}".trim(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Data rows
                if (protocol.isNotBlank()) {
                    IdRow("Protocolo OBD2:", protocol, com.elysium369.meet.ui.theme.MeetColors.neonGreen)
                }
                if (vin != null && vin != "N/A") {
                    IdRow("VIN:", vin!!, com.elysium369.meet.ui.theme.MeetColors.electricBlue)
                }
                if (calibrationId != null) {
                    IdRow("Calibration ID:", calibrationId!!, MeetColors.electricBlue)
                }
                if (ecuName != null) {
                    IdRow("ECU:", ecuName!!, MeetColors.warning)
                }
                if (adapterVer.isNotBlank()) {
                    IdRow("Adaptador:", adapterVer, if (isClone) com.elysium369.meet.ui.theme.MeetColors.error else com.elysium369.meet.ui.theme.MeetColors.neonGreen)
                }
            }
        }
    }
}

@Composable
private fun IdRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            value,
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp)
        )
    }
}
