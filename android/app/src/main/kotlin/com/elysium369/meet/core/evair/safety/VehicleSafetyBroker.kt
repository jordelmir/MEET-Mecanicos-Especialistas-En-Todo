package com.elysium369.meet.core.evair.safety

import com.elysium369.meet.core.evair.domain.AuthorizationResult
import com.elysium369.meet.core.evair.domain.ProposedVehicleAction
import com.elysium369.meet.core.evair.domain.SafetyLevel
import com.elysium369.meet.core.evair.domain.VehicleCommand
import com.elysium369.meet.core.evair.domain.VehicleSnapshot
import com.elysium369.meet.core.obd.ObdSession
import com.elysium369.meet.core.obd.PhysicalBusOwner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VehicleSafetyBroker — The definitive authorization gate for all actions proposed by EVAIR agents.
 *
 * Safety Principles:
 * 1. AI agents PROPOSE actions, they NEVER execute directly.
 * 2. Level 0 (Read-Only) is auto-authorized.
 * 3. Level 2 (Active Tests) require: vehicle stationary, engine condition checks, user confirmation.
 * 4. Level 3 (State Modifying e.g. Clear DTCs) requires explicit user confirmation.
 * 5. Level 4 (Safety Critical e.g. steering/braking) is DENIED BY DESIGN for autonomous AI.
 */
@Singleton
class VehicleSafetyBroker(
    private val getPhysicalBusOwner: () -> PhysicalBusOwner,
) {
    @Inject
    constructor(obdSession: ObdSession) : this(
        getPhysicalBusOwner = { obdSession.physicalBusOwner.value }
    )

    fun authorize(
        proposal: ProposedVehicleAction,
        currentSnapshot: VehicleSnapshot? = null,
    ): AuthorizationResult {
        return when (val cmd = proposal.command) {
            is VehicleCommand.ReadPid,
            is VehicleCommand.ReadPids,
            is VehicleCommand.ReadDtcs,
            is VehicleCommand.ReadFreezeFrame,
            is VehicleCommand.ReadMode06,
            is VehicleCommand.ReadReadiness -> {
                AuthorizationResult.Allowed
            }

            is VehicleCommand.RunDiagnosticTest -> {
                authorizeActiveTest(cmd, currentSnapshot)
            }

            is VehicleCommand.ClearDtcs -> {
                AuthorizationResult.RequiresConfirmation(
                    reason = "Borrar los códigos DTC reiniciará los monitores de emisiones I/M y borrará el historial de freeze frame. ¿Deseas continuar?"
                )
            }
        }
    }

    private fun authorizeActiveTest(
        cmd: VehicleCommand.RunDiagnosticTest,
        snapshot: VehicleSnapshot?,
    ): AuthorizationResult {
        // 1. Safety check: Vehicle must NOT be moving
        val speedKph = snapshot?.engine?.speedKph ?: snapshot?.transmission?.speedKph
        if (speedKph != null && speedKph > 0.0) {
            return AuthorizationResult.Denied(
                reason = "Prueba activa '${cmd.testId}' denegada: El vehículo está en movimiento (${speedKph} km/h). El vehículo debe estar completamente detenido."
            )
        }

        // 2. Bus ownership check: Bus must not be occupied by oscilloscope or critical scan
        val currentOwner = getPhysicalBusOwner()
        if (currentOwner != PhysicalBusOwner.IDLE && currentOwner != PhysicalBusOwner.ACTIVE_TEST) {
            return AuthorizationResult.Denied(
                reason = "Prueba activa '${cmd.testId}' denegada: El bus físico OBD está ocupado por $currentOwner."
            )
        }

        // 3. User confirmation required for all active tests
        return AuthorizationResult.RequiresConfirmation(
            reason = "La prueba activa '${cmd.testId}' actuará componentes del vehículo en modo de diagnóstico. Asegúrate de que el vehículo esté estacionado con el freno de mano activado."
        )
    }

    /**
     * Helper to verify if an action can be safely executed autonomously without user prompt.
     */
    fun isAutoExecutable(cmd: VehicleCommand): Boolean {
        return cmd.safetyLevel == SafetyLevel.READ_ONLY || cmd.safetyLevel == SafetyLevel.NON_DESTRUCTIVE_DIAGNOSTIC
    }
}
