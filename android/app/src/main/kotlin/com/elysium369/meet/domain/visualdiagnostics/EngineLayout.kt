package com.elysium369.meet.domain.visualdiagnostics

data class EngineLayout(
    val engineType: EngineType,
    val label: String,
    val cylinderCount: Int,
    val defaultScene: VisualDiagnosticTab = VisualDiagnosticTab.MOTOR_3D
)

