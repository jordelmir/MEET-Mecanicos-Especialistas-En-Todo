package com.elysium369.meet.domain.diagnostics

import com.elysium369.meet.core.vanguard.DiagnosticRetentionClass
import java.util.concurrent.TimeUnit

object DiagnosticEvidenceRetentionPolicy {
    fun expiresAtMs(retentionClass: DiagnosticRetentionClass, capturedAtMs: Long): Long? = when (retentionClass) {
        DiagnosticRetentionClass.RAW_TRANSIENT -> capturedAtMs + TimeUnit.DAYS.toMillis(30)
        DiagnosticRetentionClass.RAW_FORENSIC -> capturedAtMs + TimeUnit.DAYS.toMillis(90)
        DiagnosticRetentionClass.NORMALIZED_LONG_TERM,
        DiagnosticRetentionClass.CERTIFIED -> null
    }
}
