package com.elysium369.meet.safety.data.local

import androidx.room.Dao
import androidx.room.Query

@Dao
interface SafetyPayloadDao {

    @Query(
        """
        INSERT OR IGNORE INTO safety_local_payloads
        (payloadId, ciphertext, sha256, createdAt, redactedAt)
        VALUES (:payloadId, :ciphertext, :sha256, :now, NULL)
        """
    )
    suspend fun insert(payloadId: String, ciphertext: ByteArray, sha256: String, now: Long)

    @Query("SELECT * FROM safety_local_payloads WHERE payloadId = :payloadId")
    suspend fun get(payloadId: String): SafetyLocalPayloadEntity?

    @Query(
        """
        UPDATE safety_local_payloads
        SET redactedAt = :now
        WHERE payloadId = :payloadId AND redactedAt IS NULL
        """
    )
    suspend fun redact(payloadId: String, now: Long): Int
}
