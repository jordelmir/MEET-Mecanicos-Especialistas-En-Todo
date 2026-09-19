package com.elysium369.meet.safety.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "safety_local_payloads")
data class SafetyLocalPayloadEntity(
    @PrimaryKey
    val payloadId: String,
    val ciphertext: ByteArray,
    val sha256: String,
    val createdAt: Long,
    val redactedAt: Long?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SafetyLocalPayloadEntity) return false
        return payloadId == other.payloadId
    }

    override fun hashCode(): Int = payloadId.hashCode()
}
