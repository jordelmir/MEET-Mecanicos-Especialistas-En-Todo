package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dashboards")
data class DashboardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "dashboard_widgets")
data class DashboardWidgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dashboardId: String,
    val name: String,
    val pid: String,
    val type: String, // GAUGE, WAVE, DIGITAL, BAR
    val gridX: Int,
    val gridY: Int,
    val gridW: Int,
    val gridH: Int,
    val color: String,
    val minVal: Float,
    val maxVal: Float,
    val unit: String
)
