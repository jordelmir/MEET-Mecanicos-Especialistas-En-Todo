package com.elysium369.meet.ui.screens.scanner

enum class GaugeType { CIRCULAR, WAVE }
data class GaugeConfig(
    val id: String, 
    val label: String, 
    val pid: String, 
    val minVal: Float, 
    val maxVal: Float, 
    val unit: String, 
    val type: GaugeType = GaugeType.CIRCULAR
)
