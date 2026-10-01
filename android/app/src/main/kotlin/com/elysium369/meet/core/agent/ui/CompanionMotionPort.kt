package com.elysium369.meet.core.agent.ui

import androidx.compose.ui.geometry.Rect

data class CompanionHomePose(
    val x: Float,
    val y: Float,
    val scale: Float = 1.0f,
)

enum class EvairMotionState {
    HOME,
    CHARGING,
    SHRINKING,
    VANISHING,
    ARRIVING_AT_TARGET,
    TARGET_PULSE,
    ACTIVATING,
    RETURN_VANISH,
    RETURNING,
    RESTORED,
}

/**
 * Puerto de desacoplamiento para la animación del Avatar 3D de EVAIR.
 * El motor semántico interactúa con el avatar mediante este contrato,
 * sin acoplarse directamente a Compose internals ni a frameworks propietarios.
 */
interface CompanionMotionPort {
    val currentHomePose: CompanionHomePose
    fun updateHomePose(pose: CompanionHomePose)
    suspend fun teleportTo(target: Rect)
    suspend fun tapPulse()
    suspend fun returnHome()
}
