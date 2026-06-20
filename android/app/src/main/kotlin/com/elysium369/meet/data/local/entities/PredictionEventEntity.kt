package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "prediction_events",
    indices = [
        Index(value = ["vehicleId", "createdAt"]),
        Index(value = ["createdAt"])
    ]
)
data class PredictionEventEntity(
    @PrimaryKey val eventId: String,
    val vehicleId: String,
    val severity: String,      // LOW, MODERATE, HIGH, CRITICAL
    val confidence: Double,    // 0.0 to 1.0
    val message: String,
    val estimatedDays: Int,    // days until potential failure
    val createdAt: Long        // timestamp
)
