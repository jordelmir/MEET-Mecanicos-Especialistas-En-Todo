package com.elysium369.meet.visual3d.ui

enum class TwinFocusMode {
    COMPLETE_VEHICLE,
    SYSTEM,
    COMPONENT,
    REPAIR
}

data class VehicleTwinViewportState(
    val focusMode: TwinFocusMode = TwinFocusMode.COMPLETE_VEHICLE,
    val xRayEnabled: Boolean = false,
    val autoRotateEnabled: Boolean = true,
    val explodedProgress: Float = 0f,
    val cameraResetNonce: Int = 0
) {
    fun focusSystem() = copy(focusMode = TwinFocusMode.SYSTEM)
    fun focusComponent() = copy(focusMode = TwinFocusMode.COMPONENT)
    fun enterRepair() = copy(
        focusMode = TwinFocusMode.REPAIR,
        autoRotateEnabled = false,
        explodedProgress = 1f
    )
    fun returnToVehicle() = copy(focusMode = TwinFocusMode.COMPLETE_VEHICLE, explodedProgress = 0f)
    fun toggleXRay() = copy(xRayEnabled = !xRayEnabled)
    fun toggleAutoRotate() = copy(autoRotateEnabled = !autoRotateEnabled)
    fun resetCamera() = copy(cameraResetNonce = cameraResetNonce + 1)
}
