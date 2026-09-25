package com.elysium369.meet.core.agent.capability.mechanic

import com.elysium369.meet.core.agent.capability.AgentCapability
import com.elysium369.meet.core.agent.capability.AgentExecutionContext
import com.elysium369.meet.core.agent.capability.AgentResult
import com.elysium369.meet.core.agent.capability.AgentRisk
import com.elysium369.meet.core.agent.capability.CapabilityId
import com.elysium369.meet.core.agent.capability.CapabilityValidation
import kotlinx.serialization.Serializable

@Serializable
data class MechanicDiagnosisInput(
    val dtcCodes: List<String> = emptyList(),
    val query: String = "",
)

@Serializable
data class DtcFinding(
    val code: String,
    val description: String,
    val severity: String,
    val probableCauses: List<String>,
    val requiresPhysicalVerification: Boolean = true,
)

@Serializable
data class MechanicDiagnosisOutput(
    val findings: List<DtcFinding>,
    val summary: String,
    val vehicleId: String?,
    val isPhysicalScan: Boolean,
)

/**
 * Premium diagnostic capability for the Master Mechanic agent.
 * Enforces Master Order Omega §28:
 * "The agent must not substitute invented sensor values.
 * Never mark compatibility EXACT without OEM evidence.
 * Phrasing: 'Requiere prueba física', 'Dato no capturado'."
 */
class MasterMechanicCapability : AgentCapability<MechanicDiagnosisInput, MechanicDiagnosisOutput> {

    override val id: CapabilityId = CapabilityId.of("vehicle.read_dtc")
    override val risk: AgentRisk = AgentRisk.READ_ONLY
    override val requiredEntitlement: String = "agent.master_mechanic"

    override suspend fun validate(input: MechanicDiagnosisInput, context: AgentExecutionContext): CapabilityValidation {
        return CapabilityValidation.Valid
    }

    override suspend fun execute(input: MechanicDiagnosisInput, context: AgentExecutionContext): AgentResult<MechanicDiagnosisOutput> {
        val dtcsToDiagnose = if (input.dtcCodes.isNotEmpty()) {
            input.dtcCodes
        } else {
            // Check context for active DTCs
            emptyList()
        }

        if (dtcsToDiagnose.isEmpty()) {
            return AgentResult.Success(
                data = MechanicDiagnosisOutput(
                    findings = emptyList(),
                    summary = "OBD no disponible o ningún código de falla DTC detectado. Realiza un escaneo físico conectado al puerto OBD-II para diagnosticar fallas activas.",
                    vehicleId = context.activeVehicleId,
                    isPhysicalScan = false,
                ),
                summary = "Diagnóstico completado: sin códigos detectados en el búfer actual.",
            )
        }

        val findings = dtcsToDiagnose.map { code ->
            when (code.uppercase().trim()) {
                "P0420" -> DtcFinding(
                    code = "P0420",
                    description = "Catalyst System Efficiency Below Threshold (Bank 1)",
                    severity = "MODERATE",
                    probableCauses = listOf(
                        "Convertidor catalítico degradado térmicamente o contaminado",
                        "Sensor de oxígeno posterior (B1S2) defectuoso o descalibrado",
                        "Fuga de escape previa o adyacente al convertidor catalítico",
                    ),
                    requiresPhysicalVerification = true,
                )
                "P0171" -> DtcFinding(
                    code = "P0171",
                    description = "System Too Lean (Bank 1)",
                    severity = "HIGH",
                    probableCauses = listOf(
                        "Fuga de vacío en colector de admisión o mangueras PCV",
                        "Sensor de flujo de masa de aire (MAF) contaminado",
                        "Baja presión de combustible (bomba, filtro o regulador)",
                    ),
                    requiresPhysicalVerification = true,
                )
                else -> DtcFinding(
                    code = code.uppercase(),
                    description = "Código de diagnóstico estándar OBD-II ($code)",
                    severity = "REQUIRES_INSPECTION",
                    probableCauses = listOf(
                        "Requiere prueba física y revisión de diagrama de fábrica",
                    ),
                    requiresPhysicalVerification = true,
                )
            }
        }

        return AgentResult.Success(
            data = MechanicDiagnosisOutput(
                findings = findings,
                summary = "Se diagnosticaron ${findings.size} código(s) de falla. Toda conclusión diagnóstica requiere confirmación física antes de cambiar repuestos.",
                vehicleId = context.activeVehicleId,
                isPhysicalScan = true,
            ),
            summary = "Diagnóstico técnico completado con advertencias de verificación física.",
        )
    }
}
