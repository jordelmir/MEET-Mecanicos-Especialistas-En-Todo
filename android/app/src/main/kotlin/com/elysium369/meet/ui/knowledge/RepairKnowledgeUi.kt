package com.elysium369.meet.ui.knowledge

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.knowledge.graph.ActiveVehicleIdentity
import com.elysium369.meet.core.knowledge.graph.AutomotiveKnowledgeGraphRepository
import com.elysium369.meet.core.knowledge.graph.RepairKnowledgeBundle
import com.elysium369.meet.core.knowledge.graph.RepairKnowledgeOrchestrator
import com.elysium369.meet.core.knowledge.graph.RepairKnowledgeRequest
import com.elysium369.meet.data.supabase.Vehicle
import com.elysium369.meet.diagnostic.DiagnosticProvenance
import com.elysium369.meet.visual3d.domain.RepairKnowledgeVisualNavigator
import com.elysium369.meet.visual3d.domain.RepairVisualNavigationPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface RepairKnowledgeUiState {
    data object Loading : RepairKnowledgeUiState
    data class Ready(
        val bundle: RepairKnowledgeBundle,
        val visualPlan: RepairVisualNavigationPlan
    ) : RepairKnowledgeUiState
    data class Unavailable(val reason: String) : RepairKnowledgeUiState
}

private object ProcessRepairKnowledgeRepository {
    @Volatile
    private var repository: AutomotiveKnowledgeGraphRepository? = null

    fun get(context: Context): AutomotiveKnowledgeGraphRepository =
        repository ?: synchronized(this) {
            repository ?: AutomotiveKnowledgeGraphRepository(context.applicationContext)
                .also { repository = it }
        }
}

@Composable
fun rememberRepairKnowledgeUiState(
    vehicle: Vehicle?,
    dtcs: List<String>,
    selectedComponentCanonicalKey: String? = null,
    provenance: DiagnosticProvenance = DiagnosticProvenance.Offline
): State<RepairKnowledgeUiState> {
    val context = LocalContext.current.applicationContext
    return produceState<RepairKnowledgeUiState>(
        initialValue = RepairKnowledgeUiState.Loading,
        vehicle?.id,
        vehicle?.vin,
        dtcs,
        selectedComponentCanonicalKey,
        provenance
    ) {
        value = RepairKnowledgeUiState.Loading
        value = withContext(Dispatchers.IO) {
            runCatching {
                val repository = ProcessRepairKnowledgeRepository.get(context)
                val bundle = RepairKnowledgeOrchestrator(repository).resolve(
                    RepairKnowledgeRequest(
                        vehicle = vehicle?.toActiveVehicleIdentity(),
                        dtcs = dtcs,
                        selectedComponentCanonicalKey = selectedComponentCanonicalKey,
                        provenance = provenance
                    )
                )
                RepairKnowledgeUiState.Ready(
                    bundle = bundle,
                    visualPlan = RepairKnowledgeVisualNavigator.plan(bundle)
                )
            }.getOrElse { error ->
                RepairKnowledgeUiState.Unavailable(
                    error.message?.take(180)
                        ?: "Conocimiento estructurado no disponible."
                )
            }
        }
    }
}

fun Vehicle.toActiveVehicleIdentity(): ActiveVehicleIdentity = ActiveVehicleIdentity(
    make = make,
    model = model,
    year = year,
    engine = listOfNotNull(
        engine.takeIf(String::isNotBlank),
        displacement_cc.takeIf { it > 0 }?.let { "${it}cc" }
    ).joinToString(" ").ifBlank { null },
    transmission = listOf(transmission_type, transmission_subtype)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .ifBlank { null },
    vin = vin.takeIf(String::isNotBlank)
)

@Composable
fun RepairKnowledgeEvidencePanel(
    state: RepairKnowledgeUiState,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF00E5FF)
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101827)),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "CONOCIMIENTO ESTRUCTURADO Y EVIDENCIA",
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
            when (state) {
                RepairKnowledgeUiState.Loading -> Text(
                    "Validando grafo automotriz…",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
                is RepairKnowledgeUiState.Unavailable -> {
                    Text(
                        "Conocimiento estructurado no disponible; se bloquean acciones materiales.",
                        color = Color(0xFFFF8A80),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(state.reason, color = Color.LightGray, fontSize = 10.sp)
                }
                is RepairKnowledgeUiState.Ready -> {
                    val bundle = state.bundle
                    Text(
                        "Integridad: ${bundle.graphIntegrity.status} · " +
                            "${bundle.citations.size} citas calificadas",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    bundle.nextTests.take(4).forEachIndexed { index, test ->
                        Text(
                            "${index + 1}. ${test.label} " +
                                if (test.completed) "✓" else "· pendiente",
                            color = if (test.completed) Color(0xFF69F0AE) else Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                    if (bundle.doNotReplaceYet.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "NO REEMPLAZAR TODAVÍA",
                            color = Color(0xFFFFAB40),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                        bundle.doNotReplaceYet.take(2).forEach {
                            Text("• ${it.label}: ${it.reason}", color = Color.LightGray, fontSize = 10.sp)
                        }
                    }
                    Text(
                        if (bundle.partGate.purchaseAllowed) {
                            "Repuesto: evidencia exacta verificada para ${bundle.partGate.componentCanonicalKey}."
                        } else {
                            "Repuesto bloqueado: ${bundle.partGate.reason}"
                        },
                        color = if (bundle.partGate.purchaseAllowed) {
                            Color(0xFF69F0AE)
                        } else {
                            Color(0xFFFF8A80)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    state.visualPlan.primaryTarget?.let { visual ->
                        Text(
                            "3D: ${visual.label} · ${visual.visualAuthority} · no dimensional",
                            color = accentColor,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
