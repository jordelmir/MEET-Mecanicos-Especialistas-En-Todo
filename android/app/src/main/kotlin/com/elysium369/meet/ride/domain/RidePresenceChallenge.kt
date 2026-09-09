package com.elysium369.meet.ride.domain

import kotlin.math.abs

/** Local presence challenge only: this does not establish a person's identity. */
class RidePresenceChallenge {
    enum class Phase { FIND_FACE, OPEN, CLOSED, VERIFIED }
    data class Observation(
        val faceCount: Int,
        val trackingId: Int? = null,
        val yaw: Float = 0f,
        val pitch: Float = 0f,
        val leftEye: Float? = null,
        val rightEye: Float? = null,
    )
    data class State(val phase: Phase, val instruction: String)

    private var phase = Phase.FIND_FACE
    val isComplete: Boolean get() = phase == Phase.VERIFIED
    private var trackingId: Int? = null
    private var startedAt = 0L
    private var closedAt = 0L

    fun reset(): State {
        phase = Phase.FIND_FACE
        trackingId = null
        return State(phase, "Mira de frente a la cámara con los ojos abiertos")
    }

    fun accept(face: Observation, nowMs: Long): State {
        if (phase == Phase.VERIFIED) return State(phase, "Prueba de presencia completada")
        if (face.faceCount != 1) {
            reset()
            return State(phase, if (face.faceCount > 1) "Debe aparecer una sola persona" else "Coloca tu rostro frente a la cámara")
        }
        if (!face.yaw.isFinite() || !face.pitch.isFinite() || abs(face.yaw) > 20f || abs(face.pitch) > 20f) {
            reset()
            return State(phase, "Mira de frente sin inclinar la cabeza")
        }
        if (phase != Phase.FIND_FACE && (trackingId != face.trackingId || nowMs < startedAt || nowMs - startedAt > 15_000)) reset()
        val left = face.leftEye
        val right = face.rightEye
        if (left == null || right == null || !left.isFinite() || !right.isFinite()) {
            reset()
            return State(phase, "No se distinguen tus ojos. Mejora la luz y evita reflejos")
        }
        when (phase) {
            Phase.FIND_FACE -> if (left > .72f && right > .72f) {
                trackingId = face.trackingId
                startedAt = nowMs
                phase = Phase.OPEN
            }
            Phase.OPEN -> if (left < .28f && right < .28f) {
                closedAt = nowMs
                phase = Phase.CLOSED
            }
            Phase.CLOSED -> if (left > .65f && right > .65f) {
                // A deliberately held closure is observable even on slower cameras.
                phase = if (nowMs - closedAt >= 150) Phase.VERIFIED else Phase.OPEN
            }
            Phase.VERIFIED -> Unit
        }
        return State(phase, when (phase) {
            Phase.FIND_FACE -> "Abre ambos ojos y mira de frente"
            Phase.OPEN -> "Cierra ambos ojos durante un segundo y vuelve a abrirlos"
            Phase.CLOSED -> "Ahora abre ambos ojos mirando de frente"
            Phase.VERIFIED -> "Prueba de presencia completada"
        })
    }
}
