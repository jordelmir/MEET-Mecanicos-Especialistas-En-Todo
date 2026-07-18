package com.elysium369.meet.ai.data

object SecretRedactor {
    private val patterns = listOf(
        Regex("""sk-[A-Za-z0-9_\-]{20,}"""),
        Regex("""AIza[0-9A-Za-z_\-]{20,}"""),
        Regex("""Bearer\s+[A-Za-z0-9_\-\.]{20,}"""),
        Regex("""[0-9]{8,10}:[A-Za-z0-9_\-]{30,}"""),
        Regex("""(?i)(api[_-]?key|token|secret)\s*[:=]\s*[A-Za-z0-9_\-\.]{16,}""")
    )

    fun redact(input: String): String {
        return patterns.fold(input) { acc, regex ->
            regex.replace(acc, "[REDACTED_SECRET]")
        }
    }
}
