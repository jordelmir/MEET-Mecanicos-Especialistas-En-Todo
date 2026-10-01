package com.elysium.vanguard.core.util

/** Prevent imported file-management code from logging private paths or media metadata. */
object SafeLog {
    fun d(tag: String, message: String, cause: Throwable? = null): Int = 0
    fun i(tag: String, message: String, cause: Throwable? = null): Int = 0
    fun w(tag: String, message: String, cause: Throwable? = null): Int = 0
    fun e(tag: String, message: String, cause: Throwable? = null): Int = 0
}
