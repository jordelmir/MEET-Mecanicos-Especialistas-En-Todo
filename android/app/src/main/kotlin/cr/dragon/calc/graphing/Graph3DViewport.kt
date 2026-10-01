package cr.dragon.calc.graphing

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.cos
import kotlin.math.sin

/**
 * Estado de cámara 3D con ángulos de Euler y proyección en perspectiva.
 *
 * Convierte coordenadas (x, y, z) del espacio matemático a coordenadas (screenX, screenY)
 * del Canvas 2D de Compose, aplicando rotación yaw/pitch y división focal.
 */
@Stable
class Graph3DViewport(
    initialYaw: Float = -45f,     // Rotación horizontal (grados)
    initialPitch: Float = 30f,    // Rotación vertical (grados)
    initialDistance: Float = 12f, // Distancia focal de la cámara
    initialScale: Float = 1f
) {
    var yaw by mutableFloatStateOf(initialYaw)
        private set
    var pitch by mutableFloatStateOf(initialPitch)
        private set
    var distance by mutableFloatStateOf(initialDistance)
        private set
    var scale by mutableFloatStateOf(initialScale)
        private set

    // Rango del dominio matemático
    var xMin by mutableDoubleStateOf(-5.0)
        private set
    var xMax by mutableDoubleStateOf(5.0)
        private set
    var yMin by mutableDoubleStateOf(-5.0)
        private set
    var yMax by mutableDoubleStateOf(5.0)
        private set

    // ================================================================
    // PROYECCIÓN 3D → 2D
    // ================================================================

    /**
     * Proyecta un punto (x, y, z) del espacio 3D a coordenadas de pantalla (px, py).
     * Aplica rotación Euler (yaw, pitch) seguida de proyección en perspectiva.
     *
     * @return Pair(screenX, screenY) o null si el punto está detrás de la cámara.
     */
    fun project(x: Double, y: Double, z: Double, screenWidth: Float, screenHeight: Float): Pair<Float, Float>? {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())

        // Rotación Yaw (alrededor del eje Z)
        val cosYaw = cos(yawRad)
        val sinYaw = sin(yawRad)
        val rx = x * cosYaw - y * sinYaw
        val ry = x * sinYaw + y * cosYaw
        val rz = z

        // Rotación Pitch (alrededor del eje X rotado)
        val cosPitch = cos(pitchRad)
        val sinPitch = sin(pitchRad)
        val ey = ry * cosPitch - rz * sinPitch
        val ez = ry * sinPitch + rz * cosPitch
        val ex = rx

        // Proyección en perspectiva con distancia focal
        val d = distance.toDouble()
        val perspectiveFactor = d / (d + ey)
        if (perspectiveFactor <= 0) return null // Detrás de la cámara

        val projX = ex * perspectiveFactor
        val projY = ez * perspectiveFactor

        // Mapear a coordenadas de pantalla centradas
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        val scaleFactor = (screenWidth.coerceAtMost(screenHeight) / 10f) * scale

        val screenX = centerX + (projX * scaleFactor).toFloat()
        val screenY = centerY - (projY * scaleFactor).toFloat() // Invertir Y

        return screenX to screenY
    }

    /**
     * Calcula la profundidad Z de un punto respecto a la cámara
     * (para ordenamiento del Painter's Algorithm).
     */
    fun cameraDepth(x: Double, y: Double, z: Double): Double {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())

        val cosYaw = cos(yawRad)
        val sinYaw = sin(yawRad)
        val ry = x * sinYaw + y * cosYaw

        val cosPitch = cos(pitchRad)
        val sinPitch = sin(pitchRad)
        return ry * sinPitch + z * cosPitch
    }

    // ================================================================
    // GESTOS INTERACTIVOS
    // ================================================================

    fun applyRotation(dYaw: Float, dPitch: Float) {
        yaw += dYaw
        pitch = (pitch + dPitch).coerceIn(-89f, 89f) // Evitar gimbal lock
    }

    fun applyZoom(zoomScale: Float) {
        scale = (scale * zoomScale).coerceIn(0.2f, 5f)
    }

    fun applyDistanceChange(delta: Float) {
        distance = (distance + delta).coerceIn(3f, 50f)
    }
}
