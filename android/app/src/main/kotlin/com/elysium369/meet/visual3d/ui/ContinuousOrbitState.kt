package com.elysium369.meet.visual3d.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class OrbitCameraPose(
    val eyeX: Float,
    val eyeY: Float,
    val eyeZ: Float,
    val targetX: Float,
    val targetY: Float,
    val targetZ: Float,
    val upX: Float,
    val upY: Float,
    val upZ: Float
) {
    fun allFinite(): Boolean = listOf(
        eyeX, eyeY, eyeZ,
        targetX, targetY, targetZ,
        upX, upY, upZ
    ).all(Float::isFinite)
}

/** Scene-library-independent state for direct, accumulated, pole-safe orbit control. */
internal class ContinuousOrbitState(
    initialYawRadians: Float = 0.42f,
    initialPitchRadians: Float = 0.20f,
    initialRadius: Float = 4.35f,
    private val minimumRadius: Float = 0.28f,
    private val maximumRadius: Float = 12f,
    private val targetX: Float = 0f,
    private val targetY: Float = 0.12f,
    private val targetZ: Float = 0f
) {
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var yaw = initialYawRadians
    private var pitch = initialPitchRadians
    private var radius = initialRadius.coerceIn(minimumRadius, maximumRadius)
    private var previousX = 0
    private var previousY = 0
    private var grabbing = false

    fun setViewport(width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
    }

    fun pose(): OrbitCameraPose {
        val sinYaw = sin(yaw)
        val cosYaw = cos(yaw)
        val sinPitch = sin(pitch)
        val cosPitch = cos(pitch)
        return OrbitCameraPose(
            eyeX = targetX + radius * sinYaw * cosPitch,
            eyeY = targetY + radius * sinPitch,
            eyeZ = targetZ + radius * cosYaw * cosPitch,
            targetX = targetX,
            targetY = targetY,
            targetZ = targetZ,
            // Camera-local up remains perpendicular at both poles and rolls
            // naturally through an inverted view for a genuine vertical orbit.
            upX = -sinYaw * sinPitch,
            upY = cosPitch,
            upZ = -cosYaw * sinPitch
        )
    }

    fun grabBegin(x: Int, y: Int) {
        previousX = x
        previousY = y
        grabbing = true
    }

    fun grabUpdate(x: Int, y: Int) {
        if (!grabbing) return
        val deltaX = x - previousX
        val deltaY = y - previousY
        previousX = x
        previousY = y
        yaw = normalizeAngle(yaw - deltaX * FULL_TURN / viewportWidth)
        pitch = normalizeAngle(pitch + deltaY * FULL_TURN / viewportHeight)
    }

    fun grabEnd() {
        grabbing = false
    }

    fun zoom(previousSeparation: Float, currentSeparation: Float) {
        if (previousSeparation <= 0f || currentSeparation <= 0f) return
        radius = (radius * previousSeparation / currentSeparation)
            .coerceIn(minimumRadius, maximumRadius)
    }

    internal fun anglesForTest(): Pair<Float, Float> = yaw to pitch

    internal fun radiusForTest(): Float = radius

    private fun normalizeAngle(value: Float): Float {
        var normalized = value % FULL_TURN
        if (normalized > PI.toFloat()) normalized -= FULL_TURN
        if (normalized < -PI.toFloat()) normalized += FULL_TURN
        return normalized
    }

    private companion object {
        const val FULL_TURN = (PI * 2.0).toFloat()
    }
}
