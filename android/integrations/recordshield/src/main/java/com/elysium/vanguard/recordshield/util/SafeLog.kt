package com.elysium.vanguard.recordshield.util

/** Imported recording code must never emit paths, identities, tokens, or evidence metadata. */
object SafeLog {
    fun d(tag: String, message: String, cause: Throwable? = null): Int = 0
    fun i(tag: String, message: String, cause: Throwable? = null): Int = 0
    fun w(tag: String, message: String, cause: Throwable? = null): Int = 0
    fun e(tag: String, message: String, cause: Throwable? = null): Int = 0
}
