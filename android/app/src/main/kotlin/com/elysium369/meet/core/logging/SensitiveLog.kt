package com.elysium369.meet.core.logging

/**
 * Release logging redactor.
 * Prevents PII, secrets, VINs, and tokens from leaking into logs or crash reports.
 */
object SensitiveLog {

    fun phone(value: String): String {
        if (value.isBlank()) return ""
        return if (value.length <= 2) "**"
        else value.takeLast(2).padStart(value.length, '*')
    }

    fun token(value: String): String {
        if (value.isBlank()) return ""
        return if (value.length < 8) "***"
        else "${value.take(3)}***${value.takeLast(3)}"
    }

    fun vin(value: String): String {
        if (value.isBlank()) return ""
        return if (value.length < 6) "***"
        else "***${value.takeLast(6)}"
    }
}
