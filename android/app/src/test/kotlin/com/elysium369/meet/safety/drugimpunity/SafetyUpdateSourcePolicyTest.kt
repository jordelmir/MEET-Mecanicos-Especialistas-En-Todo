package com.elysium369.meet.safety.drugimpunity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyUpdateSourcePolicyTest {

    @Test
    fun acceptsAbsoluteHttpAndHttpsReferences() {
        assertEquals(
            "https://example.test/case/update",
            normalizeSafetyUpdateSourceUrl(" https://example.test/case/update ").getOrThrow(),
        )
        assertTrue(isValidSafetyUpdateSourceUrl("http://example.test/notice"))
    }

    @Test
    fun rejectsMissingSchemeAndUnknownSchemes() {
        assertFalse(isValidSafetyUpdateSourceUrl("example.test/notice"))
        assertFalse(isValidSafetyUpdateSourceUrl("file:///private/record"))
        assertFalse(isValidSafetyUpdateSourceUrl("javascript:alert(1)"))
        assertFalse(isValidSafetyUpdateSourceUrl("data:text/plain,hello"))
    }

    @Test
    fun rejectsMissingHostAndEmbeddedCredentials() {
        assertFalse(isValidSafetyUpdateSourceUrl("https:///missing-host"))
        assertFalse(isValidSafetyUpdateSourceUrl("https://user:secret@example.test/notice"))
    }

    @Test
    fun rejectsBlankOrMalformedUrls() {
        assertFalse(isValidSafetyUpdateSourceUrl(""))
        assertFalse(isValidSafetyUpdateSourceUrl("   "))
        assertFalse(isValidSafetyUpdateSourceUrl("https://exa mple.test/"))
    }

    @Test
    fun urlShapeValidationDoesNotRepresentSourceVerification() {
        assertTrue(isValidSafetyUpdateSourceUrl("https://example.test/article"))
    }
}
