package com.elysium369.meet.safety.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SafetyEvidencePresentationTest {

    @Test
    fun receivedMeansRegisteredButNotYetByteVerified() {
        val status = safetyEvidencePresentation("RECEIVED")
        assertEquals("Recibido · verificación de bytes pendiente", status.label)
        assertEquals(SafetyEvidenceStatusTone.PENDING, status.tone)
    }

    @Test
    fun verificationTransportFailureRemainsPendingInsteadOfShowingSuccess() {
        val status = safetyEvidencePresentation("RECEIVED", "SERVER_VERIFICATION_PENDING")
        assertEquals("Verificación del servidor pendiente", status.label)
        assertEquals(SafetyEvidenceStatusTone.PENDING, status.tone)
    }

    @Test
    fun onlyVerifiedStateUsesVerifiedPresentation() {
        val verified = safetyEvidencePresentation("VERIFIED")
        assertEquals("Bytes verificados por servidor", verified.label)
        assertEquals(SafetyEvidenceStatusTone.VERIFIED, verified.tone)

        listOf("STAGED", "UPLOADING", "UPLOADED", "RECEIVED", "RETRY", "FAILED", "QUARANTINED", "UNKNOWN")
            .forEach { state ->
                assertFalse(
                    "$state must not appear byte-verified",
                    safetyEvidencePresentation(state).tone == SafetyEvidenceStatusTone.VERIFIED,
                )
            }
    }

    @Test
    fun mismatchQuarantineAndUploadFailureAreErrors() {
        assertEquals(SafetyEvidenceStatusTone.ERROR, safetyEvidencePresentation("QUARANTINED").tone)
        assertEquals(SafetyEvidenceStatusTone.ERROR, safetyEvidencePresentation("FAILED").tone)
    }

    @Test
    fun unrecognizedStateIsNeutralRatherThanSuccess() {
        val status = safetyEvidencePresentation("FUTURE_STATE")
        assertEquals(SafetyEvidenceStatusTone.NEUTRAL, status.tone)
        assertEquals("Estado no reconocido: FUTURE_STATE", status.label)
    }
}
