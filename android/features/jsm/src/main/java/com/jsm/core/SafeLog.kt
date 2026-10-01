package com.jsm.core

/** WebRTC signaling may contain private network and session data. */
object SafeLog {
    fun d(tag: String, message: String, cause: Throwable? = null): Int = 0
    fun i(tag: String, message: String, cause: Throwable? = null): Int = 0
    fun w(tag: String, message: String, cause: Throwable? = null): Int = 0
    fun e(tag: String, message: String, cause: Throwable? = null): Int = 0
}
