package com.elysium369.meet.core.evair.safety

import android.util.Log
import com.elysium369.meet.core.evair.domain.AuthorizationResult
import com.elysium369.meet.core.evair.domain.EvairError
import com.elysium369.meet.core.evair.domain.EvairResult
import com.elysium369.meet.core.evair.domain.ProposedVehicleAction
import com.elysium369.meet.core.evair.domain.VehicleCommand
import com.elysium369.meet.core.evair.domain.VehicleSnapshot
import com.elysium369.meet.core.obd.ObdSession
import com.elysium369.meet.core.obd.ActiveDiagnosticTestPhase
import com.elysium369.meet.core.obd.PidRegistry
import com.elysium369.meet.core.obd.ScanCompleteness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
enum class ExecutionStatus {
    PROPOSED,
    AUTHORIZED,
    SIMULATED,
    SENT,
    ACKNOWLEDGED,
    VERIFIED,
    REJECTED
}

@Serializable
data class ActionExecutionResult(
    val actionId: String,
    val isSuccess: Boolean,
    val executedCommand: String,
    val observation: String,
    val durationMs: Long,
    val status: ExecutionStatus = ExecutionStatus.SIMULATED,
)

/**
 * VehicleActionExecutor — Controlled execution gate for non-destructive and active vehicle tests.
 *
 * Implements strict hardware speed-gating, timeout guards, and exclusive bus coordination.
 */
@Singleton
class VehicleActionExecutor @Inject constructor(
    private val safetyBroker: VehicleSafetyBroker,
    private val obdSession: ObdSession? = null,
) {
    private val TAG = "VehicleActionExecutor"

    suspend fun executeAction(
        action: ProposedVehicleAction,
        snapshot: VehicleSnapshot,
        userConfirmed: Boolean,
    ): EvairResult<ActionExecutionResult> = withContext(Dispatchers.IO) {
        // 1. Safety Broker Authorization Gate
        val authDecision = safetyBroker.authorize(action, snapshot)
        when (authDecision) {
            is AuthorizationResult.Denied -> {
                Log.w(TAG, "Action ${action.actionId} DENIED by SafetyBroker: ${authDecision.reason}")
                return@withContext EvairResult.Failure(
                    EvairError.SafetyDenied(command = action.command.toString(), reason = authDecision.reason)
                )
            }
            is AuthorizationResult.RequiresConfirmation -> {
                if (!userConfirmed) {
                    return@withContext EvairResult.Failure(
                        EvairError.SafetyDenied(command = action.command.toString(), reason = "Requiere confirmación explícita del usuario: ${authDecision.reason}")
                    )
                }
            }
            is AuthorizationResult.Allowed -> { /* Proceed */ }
        }

        // 2. Hardware Invariant Check: Stationary Vehicle for active tests (FAIL CLOSED on null speed)
        if (action.command is VehicleCommand.RunDiagnosticTest) {
            val speedKph = snapshot.engine.speedKph
            if (speedKph == null) {
                return@withContext EvairResult.Failure(
                    EvairError.SafetyDenied(
                        command = action.command.toString(),
                        reason = "Invariante de seguridad: Velocidad del vehículo no verificada (UNKNOWN). Requiere lectura física de velocidad 0 km/h."
                    )
                )
            }
            if (speedKph > 0.5) {
                return@withContext EvairResult.Failure(
                    EvairError.SafetyDenied(
                        command = action.command.toString(),
                        reason = "Invariante de seguridad: El vehículo está en movimiento (${speedKph} km/h)"
                    )
                )
            }
        }

        val startTime = System.currentTimeMillis()

        // 3. Controlled Dispatch with Physical Truth
        val timeoutMs = when (val command = action.command) {
            is VehicleCommand.RunDiagnosticTest -> command.timeoutMs.coerceIn(1_000L, 120_000L) + 10_000L
            else -> 30_000L
        }
        val session = obdSession
        if (session == null || !session.connectionTruth.value.isSessionReady || session.connectionTruth.value.isDemoSession) {
            return@withContext EvairResult.Failure(
                EvairError.VehicleDisconnected(reason = "Acción autorizada, pero no existe un enlace OBD físico conectado.")
            )
        }

        val result = withTimeoutOrNull(timeoutMs) {
            try {
                Log.i(TAG, "Executing controlled action ${action.actionId}: ${action.reason}")
                dispatchPhysical(action, session, startTime)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing action: ${e.message}", e)
                ActionExecutionResult(
                    actionId = action.actionId,
                    isSuccess = false,
                    executedCommand = action.command::class.simpleName ?: "VehicleCommand",
                    observation = "Despacho rechazado o no verificable: ${e.message ?: e::class.simpleName}",
                    durationMs = System.currentTimeMillis() - startTime,
                    status = ExecutionStatus.REJECTED,
                )
            }
        }

        if (result != null) {
            EvairResult.Success(result)
        } else {
            EvairResult.Failure(EvairError.ToolTimeout(tool = action.actionId, timeoutMs = timeoutMs))
        }
    }

    private suspend fun dispatchPhysical(
        action: ProposedVehicleAction,
        session: ObdSession,
        startTime: Long,
    ): ActionExecutionResult {
        val commandName = action.command::class.simpleName ?: "VehicleCommand"
        fun result(success: Boolean, observation: String, status: ExecutionStatus) = ActionExecutionResult(
            actionId = action.actionId,
            isSuccess = success,
            executedCommand = commandName,
            observation = observation,
            durationMs = System.currentTimeMillis() - startTime,
            status = status,
        )

        return when (val command = action.command) {
            is VehicleCommand.ReadPid -> {
                val evidence = session.readPidsForEvair(setOf(command.pid)).single()
                result(
                    evidence.acknowledgedByEcu,
                    if (evidence.acknowledgedByEcu) "ECU confirmó respuesta para ${evidence.command}."
                    else "El adaptador no entregó una respuesta positiva para ${evidence.command}.",
                    if (evidence.acknowledgedByEcu) ExecutionStatus.ACKNOWLEDGED else ExecutionStatus.REJECTED,
                )
            }
            is VehicleCommand.ReadPids -> {
                val evidence = session.readPidsForEvair(command.pids)
                val acknowledged = evidence.count { it.acknowledgedByEcu }
                val allAcknowledged = acknowledged == evidence.size
                result(
                    allAcknowledged,
                    "ECU confirmó $acknowledged de ${evidence.size} lecturas PID solicitadas.",
                    if (allAcknowledged) ExecutionStatus.ACKNOWLEDGED else ExecutionStatus.REJECTED,
                )
            }
            is VehicleCommand.ReadDtcs -> {
                val report = session.readProfessionalDtcScan()
                val successful = report.completeness != ScanCompleteness.FAILED
                result(
                    successful,
                    "Escaneo físico ${report.completeness}: ${report.modules.size} módulos, ${report.records.size} DTC, ${report.rawExchanges.size} intercambios.",
                    when (report.completeness) {
                        ScanCompleteness.COMPLETE -> ExecutionStatus.VERIFIED
                        ScanCompleteness.PARTIAL, ScanCompleteness.INCONCLUSIVE -> ExecutionStatus.ACKNOWLEDGED
                        ScanCompleteness.FAILED -> ExecutionStatus.REJECTED
                    },
                )
            }
            is VehicleCommand.ReadFreezeFrame -> {
                val frame = session.readFreezeFrame(command.dtc)
                val verified = frame.outcome == com.elysium369.meet.core.obd.FreezeFrameOutcome.MATCHED
                result(
                    verified,
                    "Freeze frame ${frame.outcome} para ${command.dtc}; ${frame.rawExchanges.size} intercambios físicos.",
                    if (verified) ExecutionStatus.VERIFIED else ExecutionStatus.REJECTED,
                )
            }
            is VehicleCommand.ReadMode06 -> {
                val rows = session.readMode06Results()
                result(
                    rows.isNotEmpty(),
                    if (rows.isNotEmpty()) "ECU devolvió ${rows.size} resultados Mode 06."
                    else "No existe evidencia de respuesta Mode 06 en esta ejecución.",
                    if (rows.isNotEmpty()) ExecutionStatus.ACKNOWLEDGED else ExecutionStatus.REJECTED,
                )
            }
            is VehicleCommand.ReadReadiness -> {
                val readiness = session.readReadinessMonitors()
                result(
                    readiness != null,
                    readiness?.let { "Readiness físico verificado: MIL=${it.milOn}, DTC=${it.dtcCount}, monitores=${it.monitors.size}." }
                        ?: "La ECU no confirmó el estado de readiness.",
                    if (readiness != null) ExecutionStatus.VERIFIED else ExecutionStatus.REJECTED,
                )
            }
            is VehicleCommand.RunDiagnosticTest -> {
                val test = PidRegistry.ACTIVE_TESTS.firstOrNull { it.id.equals(command.testId, ignoreCase = true) }
                    ?: return result(false, "Prueba activa desconocida; no se envió ningún comando.", ExecutionStatus.REJECTED)
                if (command.testParameters.isNotEmpty()) {
                    return result(false, "Parámetros dinámicos no autorizados por un capability pack firmado.", ExecutionStatus.REJECTED)
                }
                if (test.capabilityPackId.isNullOrBlank() || test.safetyEvidenceRequirements.isEmpty()) {
                    return result(false, "La prueba no tiene capability pack ni requisitos de evidencia suficientes; no se despachó.", ExecutionStatus.REJECTED)
                }
                val terminal = session.executeActiveTestForEvair(test.copy(durationMs = command.timeoutMs))
                result(
                    terminal.phase == ActiveDiagnosticTestPhase.STOP_VERIFIED,
                    terminal.message,
                    if (terminal.phase == ActiveDiagnosticTestPhase.STOP_VERIFIED) ExecutionStatus.VERIFIED else ExecutionStatus.REJECTED,
                )
            }
            is VehicleCommand.ClearDtcs -> result(
                false,
                "Rechazado: la orden no incluye plan de relectura por módulo ni evidencia previa suficiente.",
                ExecutionStatus.REJECTED,
            )
        }
    }
}
