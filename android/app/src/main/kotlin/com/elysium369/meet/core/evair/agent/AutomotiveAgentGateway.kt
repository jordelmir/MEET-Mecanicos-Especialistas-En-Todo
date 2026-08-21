package com.elysium369.meet.core.evair.agent

import com.elysium369.meet.core.evair.domain.DiagnosticAgentRequest
import com.elysium369.meet.core.evair.domain.DiagnosticResult
import com.elysium369.meet.core.evair.domain.EvairResult

/**
 * AutomotiveAgentGateway — Cognitive boundary interface for automotive AI agents.
 */
interface AutomotiveAgentGateway {
    suspend fun diagnose(request: DiagnosticAgentRequest): EvairResult<DiagnosticResult>
    suspend fun isAvailable(): Boolean
}
