package com.elysium369.meet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.domain.ExplainableClaim
import com.elysium369.meet.ui.theme.MeetColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplainClaimBottomSheet(
    claim: ExplainableClaim,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0C1524),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 40.dp, height = 4.dp),
                shape = RoundedCornerShape(2.dp),
                color = MeetColors.textMuted.copy(alpha = 0.5f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(claim.nature.badgeGlyph, fontSize = 22.sp)
                Column {
                    Text(
                        "¿POR QUÉ MEET DICE ESTO?",
                        color = MeetColors.neonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        claim.claimTitle,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Statement Card
            EliteCard(
                glowColor = MeetColors.cyberCyan,
                borderColor = MeetColors.cyberCyan.copy(alpha = 0.35f),
                backgroundColor = Color(0xFF132035),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Afirmación:",
                        color = MeetColors.textMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        claim.claimStatement,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Authority and Nature Rows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF081220))
                    .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailRow("Naturaleza del Dato", "${claim.nature.title} (${claim.nature.badgeGlyph})")
                DetailRow("Fuente Autoritativa", claim.authority.displayName)
                claim.confidencePercent?.let {
                    DetailRow("Nivel de Confianza", "$it% (Basado en Telemetría)")
                }
                claim.derivationSummary?.let {
                    DetailRow("Derivación Lógica", it)
                }
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(claim.timestampUtc))
                DetailRow("Registro Temporal", "$dateStr UTC")
            }

            // Evidences
            if (claim.evidenceRefs.isNotEmpty()) {
                Text(
                    "EVIDENCIAS VINCULADAS (${claim.evidenceRefs.size})",
                    color = MeetColors.electricBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                claim.evidenceRefs.forEach { ref ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF101B2E),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.borderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(ref.uri, color = MeetColors.cyberCyan, fontSize = 11.sp)
                            ref.hashSha256?.let { hash ->
                                Text("SHA-256: ${hash.take(8)}...", color = MeetColors.textMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("ENTENDIDO", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = MeetColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
