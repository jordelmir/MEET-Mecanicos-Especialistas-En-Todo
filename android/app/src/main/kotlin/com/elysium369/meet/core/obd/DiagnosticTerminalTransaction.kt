package com.elysium369.meet.core.obd

/**
 * Strict, read-only terminal transaction. Grammar is validated before the bus
 * lease is acquired; polling pause/resume and adapter-state verification are
 * owned by [ObdSession.executeTerminalRead].
 */
class DiagnosticTerminalTransaction(
    private val session: ObdSession,
) {
    data class Result(
        val acceptedCommand: String,
        val rawResponse: String,
        val policyReason: String,
    )

    suspend fun execute(rawCommand: String): Result {
        val decision = DiagnosticRawCommandPolicy.evaluate(rawCommand)
        require(decision.allowed) { decision.reason }
        return Result(
            acceptedCommand = decision.normalizedCommand,
            rawResponse = session.executeTerminalRead(decision.normalizedCommand),
            policyReason = decision.reason,
        )
    }
}
