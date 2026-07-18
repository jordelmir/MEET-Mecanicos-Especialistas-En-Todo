package com.elysium369.meet.visual3d.domain

data class ServiceOffset(
    val x: Float,
    val y: Float,
    val z: Float
) {
    companion object {
        val ZERO = ServiceOffset(0f, 0f, 0f)
    }
}

data class ServicePosition(
    val x: Float,
    val y: Float,
    val z: Float
)

object ReferenceVehicleServiceLayout {
    val offsets: Map<String, ServiceOffset> = linkedMapOf(
        "Engine" to ServiceOffset(0f, -0.06f, 0.52f),
        "BodyHood" to ServiceOffset(0f, -0.28f, 0.42f),
        "BodyRearPanelsColor1" to ServiceOffset(0f, 0.38f, 0.18f),
        "BodyDoorRColor1" to ServiceOffset(-0.55f, 0f, 0.08f),
        "BodyDoorLColor1" to ServiceOffset(0.55f, 0f, 0.08f),
        "WheelFrontL" to ServiceOffset(0.38f, -0.12f, -0.18f),
        "WheelFrontR" to ServiceOffset(-0.38f, -0.12f, -0.18f),
        "WheelRearL" to ServiceOffset(0.38f, 0.12f, -0.18f),
        "WheelRearR" to ServiceOffset(-0.38f, 0.12f, -0.18f),
        "Axles" to ServiceOffset(0f, 0f, -0.34f)
    )

    fun offsetFor(nodeName: String, progress: Float): ServiceOffset {
        val target = offsets[nodeName] ?: return ServiceOffset.ZERO
        val fraction = progress.coerceIn(0f, 1f)
        if (fraction == 0f) return ServiceOffset.ZERO
        return ServiceOffset(
            x = target.x * fraction,
            y = target.y * fraction,
            z = target.z * fraction
        )
    }

    fun positionFor(
        nodeName: String,
        sourcePosition: ServicePosition,
        progress: Float
    ): ServicePosition {
        val offset = offsetFor(nodeName, progress)
        return ServicePosition(
            x = sourcePosition.x + offset.x,
            y = sourcePosition.y + offset.y,
            z = sourcePosition.z + offset.z
        )
    }
}
