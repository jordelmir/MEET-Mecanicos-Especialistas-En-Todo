package com.elysium369.meet.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "repair_cases",
    indices = [
        Index(value = ["vehicleMake", "vehicleModel"]),
        Index(value = ["dtcCode"]),
        Index(value = ["createdAt"])
    ]
)
data class RepairCaseEntity(
    @PrimaryKey val id: String,
    val vehicleMake: String,
    val vehicleModel: String,
    val year: Int,
    val engine: String,
    val country: String,
    val dtcCode: String,
    val symptoms: String,
    val solution: String,
    val cost: Double,
    val timeSpent: Int,
    val partsUsed: String,
    @ColumnInfo(defaultValue = "0") val verified: Boolean,
    @ColumnInfo(defaultValue = "0") val votes: Int,
    @ColumnInfo(defaultValue = "100.0") val successRate: Double,
    @ColumnInfo(defaultValue = "0") val isBookmarked: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isMyContribution: Boolean = false,
    val createdAt: Long
)
