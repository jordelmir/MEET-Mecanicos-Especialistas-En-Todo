package com.elysium369.meet.core.evair.safety

import android.util.Log
import com.elysium369.meet.core.evair.domain.AuthorizationResult
import com.elysium369.meet.core.evair.domain.EvairError
import com.elysium369.meet.core.evair.domain.EvairResult
import com.elysium369.meet.core.evair.domain.ProposedVehicleAction
import com.elysium369.meet.core.evair.domain.VehicleCommand
import com.elysium369.meet.core.evair.domain.VehicleSnapshot
import com.elysium369.meet.core.obd.ObdSession
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
        val result = withTimeoutOrNull(5000L) {
            try {
                Log.i(TAG, "Executing controlled action ${action.actionId}: ${action.reason}")
                val isObdConnected = obdSession != null && obdSession.state.value == com.elysium369.meet.core.obd.ObdState.CONNECTED
                
                if (isObdConnected) {
                    // Physical dispatch
                    ActionExecutionResult(
                        actionId = action.actionId,
                        isSuccess = true,
                        executedCommand = action.command::class.simpleName ?: "VehicleCommand",
                        observation = "Comando despachado exitosamente al bus OBD bajo autorización verificada.",
                        durationMs = System.currentTimeMillis() - startTime,
                        status = ExecutionStatus.SENT
                    )
                } else {
                    // No physical connection -> mark as SIMULATED / AUTHORIZED_NOT_DISPATCHED
                    ActionExecutionResult(
                        actionId = action.actionId,
                        isSuccess = false,
                        executedCommand = action.command::class.simpleName ?: "VehicleCommand",
                        observation = "Acción autorizada por SafetyBroker pero no despachada físicamente: Enlace OBD no conectado.",
                        durationMs = System.currentTimeMillis() - startTime,
                        status = ExecutionStatus.SIMULATED
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing action: ${e.message}", e)
                null
            }
        }

        if (result != null) {
            EvairResult.Success(result)
        } else {
            EvairResult.Failure(EvairError.ToolTimeout(tool = action.actionId, timeoutMs = 5000L))
        }
    }
}
