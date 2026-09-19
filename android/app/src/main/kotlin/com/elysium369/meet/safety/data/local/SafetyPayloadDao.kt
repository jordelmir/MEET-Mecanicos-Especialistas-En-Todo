package com.elysium369.meet.safety.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SafetyPayloadDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrThrow(entity: SafetyLocalPayloadEntity)

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
