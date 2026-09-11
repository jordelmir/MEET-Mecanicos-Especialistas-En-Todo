package com.elysium369.meet.ride.domain

/** Detect allowed proof bytes; a filename or picker MIME is not content evidence. */
object RideProofFormat {
    const val MAX_BYTES = 12 * 1024 * 1024
    fun extension(bytes: ByteArray): String? = when {
        bytes.size < 4 || bytes.size > MAX_BYTES -> null
        bytes.take(5).toByteArray().contentEquals("%PDF-".toByteArray(Charsets.US_ASCII)) -> "pdf"
        bytes.size >= 8 && bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)) -> "png"
        bytes[0] == (-1).toByte() && bytes[1] == (-40).toByte() && bytes[2] == (-1).toByte() -> "jpg"
        else -> null
    }
}
