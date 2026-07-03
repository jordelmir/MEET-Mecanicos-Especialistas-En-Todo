package com.elysium.vanguard.forge.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.elysium.vanguard.forge.domain.CompiledMesh
import com.elysium.vanguard.forge.domain.CompiledVertex
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Render 3D interactivo de una [CompiledMesh] sobre Compose Canvas.
 *
 * Capacidades:
 * - Proyección isométrica (ángulos 30°/30°).
 * - **Drag horizontal** para rotar la pieza en yaw (alrededor del eje Y).
 * - **Doble tap** para resetear la rotación a 0.
 * - **Iluminación Lambertiana** usando las normales por vértice que ya trae
 *   [CompiledVertex]. Da sensación de volumen sin motor de PBR.
 * - Backface culling + painter's algorithm para ordenar back-to-front.
 * - Wireframe opcional superpuesto.
 *
 * Limitaciones:
 * - Sin rotación de pitch (vertical scroll lo consume el LazyColumn padre).
 * - Sin materiales PBR (usa solo el color por vértice).
 * - Si se requiere PBR + rotación libre, swap a Filament (ver ADR).
 */
@Composable
fun IsometricMeshRenderer(
    mesh: CompiledMesh,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF0A0E1A),
    wireframeColor: Color = Color(0xFF00E5FF).copy(alpha = 0.4f),
    showWireframe: Boolean = true,
    fillBackground: Boolean = true,
    enableInteraction: Boolean = true,
    enableLighting: Boolean = true
) {
    var rotation by remember { mutableStateOf(0f) }

    // Recalcular triángulos cuando la malla o la rotación cambian.
    val triangles = remember(mesh, rotation, enableLighting) {
        prepareTriangles(mesh, yaw = rotation, applyLighting = enableLighting)
    }

    val interactionModifier = if (enableInteraction) {
        Modifier
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    // Consumir solo el delta horizontal para no chocar con el scroll vertical.
                    rotation += drag.x * 0.008f
                    change.consume()
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { rotation = 0f })
            }
    } else Modifier

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(if (fillBackground) backgroundColor else Color.Transparent)
            .then(interactionModifier)
    ) {
        if (triangles.isEmpty()) return@Canvas
        drawMesh(
            triangles = triangles,
            canvasSize = size,
            wireframeColor = wireframeColor,
            showWireframe = showWireframe
        )
    }
}

// ─────────── Geometry math ───────────

/**
 * Triángulo pre-procesado: 3 vértices 3D + 3 vértices 2D proyectados + color final
 * (aplicada iluminación Lambertiana) + profundidad media para ordenar.
 */
private data class PreparedTriangle(
    val p0: Offset,
    val p1: Offset,
    val p2: Offset,
    val finalColor: Color,
    val avgZ: Float
)

/** cos(30°) y sin(30°) para la proyección isométrica. */
private const val ISO_COS = 0.8660254f
private const val ISO_SIN = 0.5f

/** Dirección de luz normalizada para iluminación Lambertiana. */
private val LIGHT_DIR: Triple<Float, Float, Float> = run {
    val lx = 0.4f; val ly = 0.7f; val lz = 0.3f
    val len = sqrt(lx * lx + ly * ly + lz * lz)
    Triple(lx / len, ly / len, lz / len)
}

/**
 * Proyecta un punto 3D a 2D aplicando primero yaw (rotación sobre Y) y luego
 * la proyección isométrica fija.
 */
private fun projectRotated(
    x: Float, y: Float, z: Float,
    yaw: Float
): Triple<Float, Float, Float> {
    val cy = cos(yaw); val sy = sin(yaw)
    val xr = x * cy + z * sy
    val zr = -x * sy + z * cy
    val sx = (xr - zr) * ISO_COS
    val sy2 = y + (xr + zr) * ISO_SIN
    // Profundidad lógica para painter's algorithm.
    // Usamos Y (altura) ya que arriba = más al fondo en iso.
    val depth = y + (xr + zr) * 0.5f
    return Triple(sx, sy2, depth)
}

/**
 * Aplica iluminación Lambertiana: dot(N, L) donde N es la normal del vértice
 * y L es la dirección de luz. Ambient mínimo 0.35 para que las caras en sombra
 * no queden totalmente negras.
 */
private fun lambert(normalX: Float, normalY: Float, normalZ: Float): Float {
    val dot = normalX * LIGHT_DIR.first + normalY * LIGHT_DIR.second + normalZ * LIGHT_DIR.third
    val lit = (max(0f, dot) * 0.65f) + 0.35f
    return lit.coerceIn(0.35f, 1.0f)
}

private fun prepareTriangles(
    mesh: CompiledMesh,
    yaw: Float,
    applyLighting: Boolean
): List<PreparedTriangle> {
    if (mesh.isEmpty) return emptyList()

    // Centrar la malla en el origen.
    val cx = ((mesh.min.x + mesh.max.x) * 0.5).toFloat()
    val cy = ((mesh.min.y + mesh.max.y) * 0.5).toFloat()
    val cz = ((mesh.min.z + mesh.max.z) * 0.5).toFloat()

    val projected = mesh.vertices.map { v ->
        val tx = v.x - cx
        val ty = v.y - cy
        val tz = v.z - cz
        val (sx, sy, depth) = projectRotated(tx, ty, tz, yaw)
        val litFactor = if (applyLighting) {
            lambert(v.nx, v.ny, v.nz)
        } else 1.0f
        ProjectedVertex(
            screen = Offset(sx, sy),
            color = Color(
                red = v.r * litFactor,
                green = v.g * litFactor,
                blue = v.b * litFactor,
                alpha = v.a
            ),
            depth = depth
        )
    }

    return mesh.faces.mapNotNull { face ->
        val a = projected.getOrNull(face.a) ?: return@mapNotNull null
        val b = projected.getOrNull(face.b) ?: return@mapNotNull null
        val c = projected.getOrNull(face.c) ?: return@mapNotNull null
        PreparedTriangle(
            p0 = a.screen,
            p1 = b.screen,
            p2 = c.screen,
            finalColor = Color(
                red = (a.color.red + b.color.red + c.color.red) / 3f,
                green = (a.color.green + b.color.green + c.color.green) / 3f,
                blue = (a.color.blue + b.color.blue + c.color.blue) / 3f,
                alpha = (a.color.alpha + b.color.alpha + c.color.alpha) / 3f
            ),
            avgZ = (a.depth + b.depth + c.depth) / 3f
        )
    }.sortedByDescending { it.avgZ }
}

private data class ProjectedVertex(
    val screen: Offset,
    val color: Color,
    val depth: Float
)

private fun DrawScope.drawMesh(
    triangles: List<PreparedTriangle>,
    canvasSize: Size,
    wireframeColor: Color,
    showWireframe: Boolean
) {
    if (triangles.isEmpty()) return

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (tri in triangles) {
        minX = min(minX, min(tri.p0.x, min(tri.p1.x, tri.p2.x)))
        minY = min(minY, min(tri.p0.y, min(tri.p1.y, tri.p2.y)))
        maxX = max(maxX, max(tri.p0.x, max(tri.p1.x, tri.p2.x)))
        maxY = max(maxY, max(tri.p0.y, max(tri.p1.y, tri.p2.y)))
    }
    val meshWidth = (maxX - minX).coerceAtLeast(1f)
    val meshHeight = (maxY - minY).coerceAtLeast(1f)
    val padding = 36f
    val availableW = canvasSize.width - padding * 2
    val availableH = canvasSize.height - padding * 2
    val scale = min(availableW / meshWidth, availableH / meshHeight)
    val offsetX = canvasSize.width * 0.5f - (minX + meshWidth * 0.5f) * scale
    val offsetY = canvasSize.height * 0.5f - (minY + meshHeight * 0.5f) * scale

    fun transform(p: Offset): Offset =
        Offset(offsetX + p.x * scale, offsetY + p.y * scale)

    for (tri in triangles) {
        val t0 = transform(tri.p0)
        val t1 = transform(tri.p1)
        val t2 = transform(tri.p2)

        // Backface culling simple en 2D: si el winding es clockwise, skip.
        val cross = (t1.x - t0.x) * (t2.y - t0.y) - (t1.y - t0.y) * (t2.x - t0.x)
        if (cross >= 0f) continue

        val path = Path().apply {
            moveTo(t0.x, t0.y)
            lineTo(t1.x, t1.y)
            lineTo(t2.x, t2.y)
            close()
        }
        drawPath(path = path, color = tri.finalColor)

        if (showWireframe) {
            drawPath(
                path = path,
                color = wireframeColor,
                style = Stroke(width = 1f)
            )
        }
    }
}