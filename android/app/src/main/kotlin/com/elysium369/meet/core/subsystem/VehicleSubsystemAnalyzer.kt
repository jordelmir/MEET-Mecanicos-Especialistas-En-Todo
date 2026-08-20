package com.elysium369.meet.core.subsystem

import com.elysium369.meet.core.domain.ClaimNature
import com.elysium369.meet.core.domain.EntityRef
import com.elysium369.meet.core.domain.ExplainableClaim
import com.elysium369.meet.core.domain.SourceAuthority
import com.elysium369.meet.core.domain.VehicleContext

enum class VehicleSubsystem(val displayName: String, val glyph: String) {
    ENGINE("Motor y Tren Motriz", "⚙️"),
    TRANSMISSION("Transmisión y Embrague", "🔄"),
    BRAKES("Frenos y Control de Tracción", "🛑"),
    SUSPENSION_STEERING("Suspensión y Dirección", "🛞"),
    ELECTRICAL_BATTERY("Sistema Eléctrico y Batería", "⚡"),
    EMISSIONS("Control de Emisiones y Escape", "🌱"),
    COOLING_THERMAL("Refrigeración y Gestión Térmica", "🌡️"),
    FUEL_COMBUSTION("Combustible e Inyección", "⛽"),
    HVAC("Climatización (A/C y Calefacción)", "❄️"),
    ADAS_SAFETY("Seguridad Activa y ADAS", "🛡️")
}

enum class SubsystemHealthState(val label: String) {
    HEALTHY("Óptimo / Sin Fallas Registradas"),
    ATTENTION_RECOMMENDED("Atención Recomendada / Mantenimiento Próximo"),
    CRITICAL_FAULT("Falla Crítica Activa Detectada"),
    INSUFFICIENT_EVIDENCE("Evidencia Insuficiente / Requiere Prueba Física"),
    NOT_EQUIPPED("No Equipado en este Vehículo")
}

data class SubsystemAssessment(
    val subsystem: VehicleSubsystem,
    val state: SubsystemHealthState,
    val healthScorePercent: Int?, // null if NOT_EQUIPPED or INSUFFICIENT_EVIDENCE
    val findings: List<String> = emptyList(),
    val observations: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    override val evidenceRefs: List<EntityRef.EvidenceRef> = emptyList(),
    override val authority: SourceAuthority = SourceAuthority.MEET_DERIVED,
    override val timestampUtc: Long = System.currentTimeMillis()
) : ExplainableClaim {
    override val claimId: String get() = "CLAIM_SUBSYSTEM_${subsystem.name}"
    override val claimTitle: String get() = "Evaluación: ${subsystem.displayName}"
    override val claimStatement: String get() = "Estado ${state.label} con score ${healthScorePercent?.let { "$it%" } ?: "N/D"}"
    override val nature: ClaimNature get() = if (findings.isNotEmpty()) ClaimNature.OBSERVED else ClaimNature.DERIVED
    override val confidencePercent: Int? get() = if (healthScorePercent != null) 90 else null
    override val derivationSummary: String get() = "${findings.size} fallas evaluadas, ${observations.size} parámetros en rango."
}

interface VehicleSubsystemAnalyzer {
    val supportedSubsystem: VehicleSubsystem
    suspend fun analyze(
        vehicleContext: VehicleContext,
        livePids: Map<String, String>,
        activeDtcs: List<String>
    ): SubsystemAssessment
}
