package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * VehicleDnaProfileEntity — Persistent mathematical signature/profile for vehicle DNA.
 *
 * Stores the baseline, variance, model confidence, and last training date.
 * Also stores the serialized Isolation Forest model.
 */
@Entity(tableName = "vehicle_dna_profiles")
data class VehicleDnaProfileEntity(
    @PrimaryKey val vehicleId: String,
    val baselineJson: String,      // JSON string representation of normal sensor means
    val varianceJson: String,      // JSON string representation of normal sensor variances/std-devs
    val forestJson: String,        // JSON string representation of the LightweightIsolationForest model
    val confidence: Double,        // Confidence level (0.0 to 100.0) based on training data size
    val lastTrainingDate: Long     // Timestamp of the last model training
)
