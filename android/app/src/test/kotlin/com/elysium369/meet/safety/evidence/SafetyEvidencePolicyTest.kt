package com.elysium369.meet.safety.evidence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyEvidencePolicyTest {
    @Test
    fun videosRemainLinkOnlyAndAreNotAcceptedAsDirectAttachments() {
        // The server RPC may technically allow these MIME types, but the client
        // deliberately does not expose them because Elysium is not video hosting.
        assertFalse(SafetyEvidencePolicy.allowedMimeTypes.contains("video/mp4"))
        assertFalse(SafetyEvidencePolicy.allowedMimeTypes.contains("video/webm"))
        assertFalse(SafetyEvidencePolicy.allowedMimeTypes.contains("video/quicktime"))
        assertFalse(SafetyEvidencePolicy.allowedMimeTypes.contains("video/3gpp"))
    }

    @Test
    fun existingEvidenceFormatsRemainAvailable() {
        assertTrue(SafetyEvidencePolicy.allowedMimeTypes.contains("image/jpeg"))
        assertTrue(SafetyEvidencePolicy.allowedMimeTypes.contains("audio/mpeg"))
        assertTrue(SafetyEvidencePolicy.allowedMimeTypes.contains("application/pdf"))
    }

    @Test
    fun attachmentSizeMatchesRemoteEvidenceLimit() {
        assertTrue(SafetyEvidencePolicy.MAX_BYTES == 20 * 1024 * 1024)
    }
}
