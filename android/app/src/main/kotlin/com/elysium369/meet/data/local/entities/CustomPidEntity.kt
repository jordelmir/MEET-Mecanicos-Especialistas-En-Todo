package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_pids")
data class CustomPidEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val mode: String,
    val pid: String,
    val name: String,
    val unit: String,
    val formula: String,
    val minVal: Float,
    val maxVal: Float,
    val warningThreshold: Float?,
    val color: String
)
