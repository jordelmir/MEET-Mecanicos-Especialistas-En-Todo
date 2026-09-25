package com.elysium369.meet.core.agent.policy

import com.elysium369.meet.core.agent.capability.AgentCapability
import com.elysium369.meet.core.agent.capability.AgentExecutionContext
import com.elysium369.meet.core.agent.capability.AgentRisk

sealed interface PolicyDecision {
    data object Authorized : PolicyDecision

    data class RequiresConfirmation(
        val risk: AgentRisk,
        val prompt: String,
        val previewPayload: Map<String, String> = emptyMap(),
    ) : PolicyDecision

    data class StaleContextAborted(
        val expectedGeneration: Long,
        val actualGeneration: Long,
        val reason: String = "El contexto de usuario o pantalla cambió durante la planeación. Operación abortada por seguridad.",
    ) : PolicyDecision

    data class EntitlementRequired(
        val entitlement: String,
        val message: String = "Esta capacidad requiere desbloquear el agente especializado correspondiente.",
    ) : PolicyDecision

    data class Denied(
        val reason: String,
    ) : PolicyDecision
}

/**
 * Deterministic authorization and safety engine for agent capabilities.
 * Enforces Master Order Omega §21 (Risk Classes), §22 (Confirmation Policy),
 * §23 (Context Concurrency / Stale Plan Abort), §24 (Idempotency Semantics),
 * and §37 (Entitlements).
 *
 * THE FUNDAMENTAL LAW:
 * "AI interprets. Policy authorizes. Domains enforce. Servers confirm. Evidence proves."
 */
class AgentPolicyEngine {

    private val safeIdempotencyRegex = Regex("^[A-Za-z0-9._:-]{16,128}$")

    /**
     * Evaluates whether a proposed capability action can proceed under current context.
     *
     * @param capability The target registered capability.
     * @param planContextGeneration The context generation snapshot when the plan was formulated.
     * @param currentContext The current live execution context.
     * @param intentConfidence The calibrated intent confidence score (0.0 .. 1.0).
     * @param confirmationPrompt Optional custom prompt to present to the user if confirmation is required.
     * @param previewPayload Key-value summary of proposed effects (e.g. price, destination).
     */
    fun evaluate(
        capability: AgentCapability<*, *>,
        planContextGeneration: Long,
        currentContext: AgentExecutionContext,
        intentConfidence: Double = 1.0,
        confirmationPrompt: String? = null,
        previewPayload: Map<String, String> = emptyMap(),
    ): PolicyDecision {
        // 1. Entitlement check (§37, §91)
        val requiredEntitlement = capability.requiredEntitlement
        if (requiredEntitlement != null && requiredEntitlement !in currentContext.userEntitlements) {
            return PolicyDecision.EntitlementRequired(
                entitlement = requiredEntitlement,
                message = "Esta función requiere el agente especializado con licencia '$requiredEntitlement'.",
            )
        }

        // 2. Concurrency & Context Freshness check (§23, §88)
        // If human or background worker modified context since plan formulation, abort!
        if (planContextGeneration != currentContext.contextGeneration) {
            return PolicyDecision.StaleContextAborted(
                expectedGeneration = planContextGeneration,
                actualGeneration = currentContext.contextGeneration,
            )
        }

        // 3. Idempotency Key validation for mutating operations (§24, §92)
        if (capability.requiresIdempotency) {
            val key = currentContext.idempotencyKey
            if (key.isNullOrBlank() || !safeIdempotencyRegex.matches(key)) {
                return PolicyDecision.Denied(
                    reason = "La operación modificadora requiere una clave de idempotencia válida (16-128 caracteres seguros).",
                )
            }
        }

        // 4. Domain & Safety Preconditions for Vehicle / Safety critical actions (§21, §22)
        if (capability.risk == AgentRisk.VEHICLE_CRITICAL || capability.risk == AgentRisk.SAFETY_CRITICAL) {
            if (currentContext.activeVehicleId.isNullOrBlank()) {
                return PolicyDecision.Denied(
                    reason = "La capacidad crítica requiere un vehículo activo validado en el contexto.",
                )
            }
        }

        // 5. Risk Class & Confirmation Evaluation (§21, §22)
        return when (capability.risk) {
            AgentRisk.READ_ONLY, AgentRisk.NAVIGATION -> {
                // Read-only inspection and UI navigation are safe to execute automatically
                PolicyDecision.Authorized
            }

            AgentRisk.REVERSIBLE -> {
                // High confidence reversible actions auto-execute; lower confidence asks
                if (intentConfidence >= 0.85) {
                    PolicyDecision.Authorized
                } else {
                    PolicyDecision.RequiresConfirmation(
                        risk = capability.risk,
                        prompt = confirmationPrompt ?: "¿Deseas que EVAIR realice esta acción?",
                        previewPayload = previewPayload,
                    )
                }
            }

            AgentRisk.COMMITTING, AgentRisk.FINANCIAL -> {
                // Committing side effects and financial charges ALWAYS require explicit user confirmation
                if (currentContext.userConfirmed) {
                    PolicyDecision.Authorized
                } else {
                    PolicyDecision.RequiresConfirmation(
                        risk = capability.risk,
                        prompt = confirmationPrompt ?: "¿Confirmas que deseas ejecutar esta operación?",
                        previewPayload = previewPayload,
                    )
                }
            }

            AgentRisk.PRIVACY_SENSITIVE -> {
                // Requires explicit permission
                if (currentContext.grantedPermissions.contains(capability.id.value) || currentContext.userConfirmed) {
                    PolicyDecision.Authorized
                } else {
                    PolicyDecision.RequiresConfirmation(
                        risk = capability.risk,
                        prompt = confirmationPrompt ?: "Esta acción accede a datos privados. ¿Autorizas continuar?",
                        previewPayload = previewPayload,
                    )
                }
            }

            AgentRisk.SAFETY_CRITICAL, AgentRisk.VEHICLE_CRITICAL -> {
                // Must have explicit confirmation AND domain preconditions verified
                if (currentContext.userConfirmed) {
                    PolicyDecision.Authorized
                } else {
                    PolicyDecision.RequiresConfirmation(
                        risk = capability.risk,
                        prompt = confirmationPrompt ?: "ATENCIÓN: Operación crítica de vehículo. ¿Confirmas la ejecución?",
                        previewPayload = previewPayload,
                    )
                }
            }
        }
    }
}
