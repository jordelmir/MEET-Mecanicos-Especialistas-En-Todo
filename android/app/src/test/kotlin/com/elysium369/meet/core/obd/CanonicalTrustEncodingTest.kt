package com.elysium369.meet.core.obd

import com.elysium369.meet.core.diagnostics.CalibrationTrustRegistry
import com.elysium369.meet.core.diagnostics.SignedCalibrationArtifact
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CanonicalTrustEncodingTest {
    @Test
    fun mapInsertionOrderCannotChangeCanonicalBytes() {
        val first = linkedMapOf<String, Any?>("z" to listOf("á", "line\n"), "a" to 7)
        val second = linkedMapOf<String, Any?>("a" to 7, "z" to listOf("á", "line\n"))
        assertArrayEquals(CanonicalJson.encode(first), CanonicalJson.encode(second))
    }

    @Test
    fun delimiterLikeTextCannotCollideWithDifferentFieldStructure() {
        val one = CanonicalJson.encode(mapOf("a" to "x|b=y"))
        val two = CanonicalJson.encode(mapOf("a" to "x", "b" to "y"))
        assertNotEquals(one.toList(), two.toList())
    }

    @Test
    fun callerCreatedCalibrationStringsCannotEnableCalibratedAuthority() {
        val forged = SignedCalibrationArtifact(
            datasetId = "caller-controlled",
            version = "999",
            datasetHash = "a".repeat(64),
            methodologyVersion = "unreviewed",
            scope = "all vehicles",
            trainingCutoffMs = 1,
            holdoutHash = "b".repeat(64),
            sampleCount = 1,
            metrics = mapOf("accuracy" to 1.0),
            issuer = "caller",
            keyId = "caller",
            reviewState = "APPROVED",
            signatureBase64 = "Zm9yZ2Vk",
        )
        assertFalse(CalibrationTrustRegistry.DenyAll.authorize(forged))
    }
}
