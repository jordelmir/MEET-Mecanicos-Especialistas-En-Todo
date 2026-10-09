package com.elysium369.meet.safety.evidence

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

object SafetyEvidencePolicy {
    const val MAX_BYTES = 20 * 1024 * 1024
    const val MAX_ATTACHMENTS = 5
    val allowedMimeTypes = setOf(
        "image/jpeg", "image/png", "image/webp", "image/gif", "image/heic", "image/heif",
        // Video is intentionally link-only to avoid turning Elysium into a video-hosting service.
        "audio/mpeg", "audio/mp4", "audio/aac", "audio/ogg", "audio/wav", "audio/x-wav",
        "application/pdf",
        "text/plain", "text/csv", "text/html",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    )
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_BYTES) { "Cada archivo puede tener como máximo 20 MB." }
            output.write(buffer, 0, count)
        }
        require(total > 0) { "El archivo está vacío." }
        return output.toByteArray()
    }
    fun associatedData(owner: String, report: String, evidence: String): ByteArray =
        "MEET-SAFETY-EVIDENCE-LOCAL-V1\u0000$owner\u0000$report\u0000$evidence".toByteArray(Charsets.UTF_8)
}
