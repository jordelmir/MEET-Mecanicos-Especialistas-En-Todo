package com.elysium.nexus.fabric.canonical

/**
 * §69 State Reconciliation Engine.
 *
 * When [desiredState] ≠ [reportedState], the
 * reconciliation engine decides what to do:
 *
 * 1. **Retry** — the command was sent but state
 *    was not confirmed; retry on the same route.
 * 2. **Fallback** — the same route failed; try
 *    a different protocol.
 * 3. **Warn** — the device is unreachable or the
 *    state is uncertain; surface to the user.
 * 4. **Accept** — the desired state is unreachable
 *    (e.g. IR toggle with no read-back); accept
 *    the inferred state.
 *
 * The engine is deterministic: given the same
 * history and policy, it produces the same
 * decision. There are no probabilistic branches.
 *
 * ## Why a separate engine
 *
 * The reconciliation logic is complex enough to
 * deserve its own test surface. Mixing it into
 * the [ActionDispatcher] would make the dispatcher
 * untestable. The engine is a pure function of
 * the [DeviceTwinHistory] + [ReconciliationPolicy].
 */
class StateReconciliationEngine(
    private val maxRetries: Int = 2,
    private val maxFallbacks: Int = 1
) {
    init {
        require(maxRetries in 0..5) { "maxRetries must be in [0, 5] (got $maxRetries)." }
        require(maxFallbacks in 0..3) { "maxFallbacks must be in [0, 3] (got $maxFallbacks)." }
    }

    /**
     * Decide what to do given the current history.
     *
     * @return a [ReconciliationDecision] that the
     *   action dispatcher executes.
     */
    fun decide(history: DeviceTwinHistory): ReconciliationDecision {
        if (!history.isStale) {
            return ReconciliationDecision.Accepted(
                reason = "Desired state matches reported state"
            )
        }

        val latestSnapshot = history.latest
        val failures = latestSnapshot?.consecutiveFailures ?: 0

        // Check if we've exceeded retry limit
        if (failures >= maxRetries) {
            return ReconciliationDecision.WarnUser(
                reason = "State reconciliation failed after $failures attempts. " +
                    "Desired: ${history.desiredState}, Reported: ${history.reportedState}",
                suggestedAction = ReconciliationSuggestion.ManualOverride
            )
        }

        // Check confidence level
        when {
            history.confidence < 0.3 -> {
                return ReconciliationDecision.WarnUser(
                    reason = "State confidence too low (${ "%.2f".format(history.confidence) }). " +
                        "Device may be unreachable.",
                    suggestedAction = ReconciliationSuggestion.CheckConnectivity
                )
            }
            history.confidence < 0.6 -> {
                return ReconciliationDecision.Fallback(
                    reason = "State confidence moderate; trying alternate route",
                    fallbackAttempt = failures
                )
            }
        }

        // Default: retry on the same route
        return ReconciliationDecision.Retry(
            reason = "State mismatch detected; retrying command",
            retryAttempt = failures
        )
    }

    /**
     * Decide whether to reconcile based on the
     * protocol's confirmation ability.
     */
    fun canReconcile(protocol: Protocol): Boolean = when (protocol) {
        // IR has no read-back; accept inferred state
        Protocol.DirectIr, Protocol.HubIr -> false
        // All other protocols can confirm state
        else -> true
    }
}

/**
 * A reconciliation decision.
 */
sealed class ReconciliationDecision {
    /** State is correct; nothing to do. */
    data class Accepted(val reason: String) : ReconciliationDecision()

    /** Retry the command on the same route. */
    data class Retry(
        val reason: String,
        val retryAttempt: Int
    ) : ReconciliationDecision()

    /** Try a different protocol route. */
    data class Fallback(
        val reason: String,
        val fallbackAttempt: Int
    ) : ReconciliationDecision()

    /** Surface the issue to the user. */
    data class WarnUser(
        val reason: String,
        val suggestedAction: ReconciliationSuggestion
    ) : ReconciliationDecision()
}

/**
 * Suggested user action for reconciliation warnings.
 */
enum class ReconciliationSuggestion {
    ManualOverride,
    CheckConnectivity,
    RePairDevice,
    CheckDevicePower
}
