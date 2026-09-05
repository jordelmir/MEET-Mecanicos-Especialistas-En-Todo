package com.elysium369.meet.fulfillment.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.geo.runtime.CommonMapPanel
import com.elysium369.meet.fulfillment.domain.FulfillmentPhase
import com.elysium369.meet.fulfillment.domain.FulfillmentProjection
import com.elysium369.meet.fulfillment.domain.FulfillmentUiAction
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun FulfillmentScaffold(
    projection: FulfillmentProjection,
    onBack: () -> Unit,
    onAction: (FulfillmentUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier,
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = MeetColors.textPrimary)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        projection.serviceName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.textPrimary
                    )
                    Text(
                        projection.phase.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (projection.phase) {
                            is FulfillmentPhase.Completed -> MeetColors.neonGreen
                            is FulfillmentPhase.Cancelled, is FulfillmentPhase.Failed -> MeetColors.error
                            is FulfillmentPhase.Disputed -> MeetColors.warning
                            else -> MeetColors.electricBlue
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(onClick = { onAction(FulfillmentUiAction.SafetyCenter) }) {
                    Icon(Icons.Default.Security, contentDescription = "Centro de Seguridad", tint = MeetColors.error)
                }
            }
        },
        bottomBar = {
            Surface(
                color = MeetColors.cardBackgroundLighter,
                border = BorderStroke(1.dp, MeetColors.borderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (projection.canCancel) {
                        OutlinedButton(
                            onClick = { onAction(FulfillmentUiAction.Cancel) },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MeetColors.error)
                        ) {
                            Text("Cancelar", color = MeetColors.error, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            when (projection.phase) {
                                is FulfillmentPhase.Completing -> onAction(FulfillmentUiAction.ConfirmCompletion)
                                is FulfillmentPhase.Completed -> onBack()
                                else -> Unit
                            }
                        },
                        modifier = Modifier.weight(if (projection.canCancel) 1.5f else 1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (projection.phase) {
                                is FulfillmentPhase.Completed -> MeetColors.neonGreen
                                is FulfillmentPhase.Completing -> MeetColors.electricBlue
                                else -> MeetColors.neonGreen
                            },
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            when (projection.phase) {
                                is FulfillmentPhase.Configuring -> "BUSCAR PROVEEDOR"
                                is FulfillmentPhase.Searching -> "BUSCANDO EN RED..."
                                is FulfillmentPhase.Matched -> "PROVEEDOR ASIGNADO"
                                is FulfillmentPhase.ProviderEnRoute -> "VER RUTA EN VIVO"
                                is FulfillmentPhase.ProviderArrived -> "EN EL SITIO"
                                is FulfillmentPhase.InProgress -> "SERVICIO EN CURSO"
                                is FulfillmentPhase.Completing -> "CONFIRMAR Y PAGAR"
                                is FulfillmentPhase.Completed -> "VER CERTIFICADO"
                                is FulfillmentPhase.Cancelled -> "CERRAR"
                                is FulfillmentPhase.Disputed -> "CONTACTAR SOPORTE"
                                is FulfillmentPhase.Failed -> "REINTENTAR"
                                else -> "ACTIVO"
                            },
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            // Map Layer if coordinates exist
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                if (projection.mapState.markers.isNotEmpty()) {
                    CommonMapPanel(
                        state = projection.mapState,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MeetColors.cardBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Mapa no disponible",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MeetColors.textSecondary
                            )
                            Text(
                                "Ubicación: ${projection.targetLocation?.latitude ?: 0.0}, ${projection.targetLocation?.longitude ?: 0.0}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MeetColors.textSecondary
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Provider tracking card
                projection.provider?.let { provider ->
                    ProviderTrackingCard(
                        provider = provider,
                        onCall = { onAction(FulfillmentUiAction.ContactCall) },
                        onMessage = { onAction(FulfillmentUiAction.ContactMessage) },
                        onPtt = { onAction(FulfillmentUiAction.ContactPtt) }
                    )
                }

                // Pricing Summary
                projection.pricing?.let { pricing ->
                    PricingSummaryCard(pricing = pricing)
                }

                // Timeline
                if (projection.timeline.isNotEmpty()) {
                    FulfillmentTimelineView(timeline = projection.timeline)
                }

                // Forensic Evidence
                if (projection.evidenceSnapshots.isNotEmpty()) {
                    FulfillmentEvidenceCard(evidenceList = projection.evidenceSnapshots)
                }
            }
        }
    }
}
