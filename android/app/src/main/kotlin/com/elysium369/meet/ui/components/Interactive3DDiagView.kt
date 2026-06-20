package com.elysium369.meet.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.elysium369.meet.core.engine3d.ElysiumProceduralModels
import com.elysium369.meet.core.engine3d.Face3D
import com.elysium369.meet.core.engine3d.Mesh3D
import com.elysium369.meet.core.engine3d.Vector3D
import com.elysium369.meet.core.engine3d.EngineType
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.*

enum class SceneType {
    ENGINE_BLOCK,
    RELAY_FUSE_BOX,
    WIRING_HARNESS
}

@Composable
fun Interactive3DDiagView(
    sceneType: SceneType,
    engineType: EngineType,
    activeDtcs: List<String>,
    selectedComponentId: String?,
    onComponentSelected: (componentId: String, componentName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var yaw by remember { mutableStateOf(-0.5f) }
    var pitch by remember { mutableStateOf(-0.4f) }
    var zoom by remember { mutableStateOf(1.2f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }

    val meshes = remember(sceneType, engineType, activeDtcs) {
        when (sceneType) {
            SceneType.ENGINE_BLOCK -> ElysiumProceduralModels.buildEngineBlockScene(engineType, activeDtcs)
            SceneType.RELAY_FUSE_BOX -> ElysiumProceduralModels.buildRelayFuseBoxScene(engineType, activeDtcs)
            SceneType.WIRING_HARNESS -> ElysiumProceduralModels.buildWiringHarnessScene(engineType, activeDtcs)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pistonAnim")
    val animTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "time"
    )

    val dtcPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dtcPulse"
    )

    var tapOffset by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    yaw += dragAmount.x * 0.007f
                    pitch += dragAmount.y * 0.007f
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val down = event.changes.firstOrNull()
                        if (down != null && down.pressed) {
                            tapOffset = down.position
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerX = w / 2f
            val centerY = h / 2f

            drawCircle(
                color = MeetColors.neonGreen.copy(alpha = 0.03f),
                radius = min(w, h) * 0.45f,
                center = Offset(centerX, centerY + 30f)
            )

            val gridColor = MeetColors.neonGreen.copy(alpha = 0.05f)
            for (i in -4..4) {
                val zStart = i * 20f
                val p1 = projectPoint(Vector3D(-80f, 40f, zStart), yaw, pitch, zoom, centerX, centerY, panX, panY)
                val p2 = projectPoint(Vector3D(80f, 40f, zStart), yaw, pitch, zoom, centerX, centerY, panX, panY)
                if (p1 != null && p2 != null) {
                    drawLine(gridColor, p1, p2, 1f)
                }

                val xStart = i * 20f
                val p3 = projectPoint(Vector3D(xStart, 40f, -80f), yaw, pitch, zoom, centerX, centerY, panX, panY)
                val p4 = projectPoint(Vector3D(xStart, 40f, 80f), yaw, pitch, zoom, centerX, centerY, panX, panY)
                if (p3 != null && p4 != null) {
                    drawLine(gridColor, p3, p4, 1f)
                }
            }

            val lightDir = Vector3D(0.5f, -1f, 0.5f).normalize()

            class ProjectedFace(
                val mesh: Mesh3D,
                val face: Face3D,
                val points: List<Offset>,
                val avgZ: Float,
                val shadingColor: Color
            )

            val projectedFaces = mutableListOf<ProjectedFace>()

            meshes.forEach { mesh ->
                val pistonIndex = if (mesh.id.startsWith("piston_")) mesh.id.removePrefix("piston_").toIntOrNull() else null
                val rodIndex = if (mesh.id.startsWith("rod_")) mesh.id.removePrefix("rod_").toIntOrNull() else null
                val sparkIndex = if (mesh.id.startsWith("spark_gap_")) mesh.id.removePrefix("spark_gap_").toIntOrNull() else null

                val pistonOffset = if (pistonIndex != null) {
                    val phase = if (pistonIndex % 2 == 0) 0f else PI.toFloat()
                    sin(animTime + phase) * 12f
                } else if (rodIndex != null) {
                    val phase = if (rodIndex % 2 == 0) 0f else PI.toFloat()
                    sin(animTime + phase) * 12f
                } else 0f

                val isSparkTriggered = sparkIndex != null && (sin(animTime * 4 + sparkIndex) > 0.85f)

                val worldVertices = mesh.transformToWorld(pistonOffset, isSparkTriggered)

                val screenPoints = worldVertices.map { v ->
                    val rotatedCam = v.rotateY(yaw).rotateX(pitch)
                    val zDepth = rotatedCam.z + 180f
                    val projX = centerX + (rotatedCam.x * zoom * 150f) / zDepth + panX
                    val projY = centerY + (rotatedCam.y * zoom * 150f) / zDepth + panY
                    Triple(Offset(projX, projY), zDepth, rotatedCam)
                }

                mesh.faces.forEach facesLoop@{ face ->
                    if (face.vertexIndices.any { it >= screenPoints.size }) return@facesLoop

                    val faceVerticesWorld = face.vertexIndices.map { worldVertices[it] }
                    val facePoints = face.vertexIndices.map { screenPoints[it].first }
                    val avgZ = face.vertexIndices.map { screenPoints[it].second }.average().toFloat()

                    val normal = if (face.vertexIndices.size >= 3) {
                        val v0 = faceVerticesWorld[0]
                        val v1 = faceVerticesWorld[1]
                        val v2 = faceVerticesWorld[2]
                        (v1 - v0).cross(v2 - v0).normalize()
                    } else Vector3D(0f, -1f, 0f)

                    val intensity = max(0.12f, normal.dot(lightDir))
                    val baseColor = face.color
                    val litColor = Color(
                        red = (baseColor.red * (intensity * 0.7f + 0.3f)).coerceIn(0f, 1f),
                        green = (baseColor.green * (intensity * 0.7f + 0.3f)).coerceIn(0f, 1f),
                        blue = (baseColor.blue * (intensity * 0.7f + 0.3f)).coerceIn(0f, 1f),
                        alpha = if (face.isTranslucent) face.opacity else 1f
                    )

                    projectedFaces.add(ProjectedFace(mesh, face, facePoints, avgZ, litColor))
                }
            }

            projectedFaces.sortByDescending { it.avgZ }

            val tappedPos = tapOffset
            if (tappedPos != null) {
                var hitMeshId: String? = null
                var hitMeshName: String? = null

                for (pf in projectedFaces.reversed()) {
                    if (pf.mesh.id == "engine_block" || pf.mesh.id.startsWith("valve_cover") || pf.mesh.id == "fuse_box_housing" || pf.mesh.id == "fuse_box_tray") continue
                    if (isPointInPolygon(tappedPos.x, tappedPos.y, pf.points)) {
                        hitMeshId = pf.mesh.id
                        hitMeshName = pf.mesh.name
                        break
                    }
                }

                if (hitMeshId != null && hitMeshName != null) {
                    onComponentSelected(hitMeshId, hitMeshName)
                }
                tapOffset = null
            }

            projectedFaces.forEach { pf ->
                val mesh = pf.mesh
                val face = pf.face
                val pts = pf.points
                val isSelected = selectedComponentId != null && mesh.id.startsWith(selectedComponentId)

                val path = Path().apply {
                    if (pts.isNotEmpty()) {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1 until pts.size) {
                            lineTo(pts[i].x, pts[i].y)
                        }
                        close()
                    }
                }

                if (face.isLineOnly) {
                    drawPath(
                        path = path,
                        color = pf.shadingColor,
                        style = Stroke(width = 2.dp.toPx())
                    )
                } else {
                    drawPath(
                        path = path,
                        color = if (mesh.isActiveDtc) {
                            Color.Red.copy(alpha = dtcPulse * 0.7f + 0.1f)
                        } else if (isSelected) {
                            MeetColors.neonGreen.copy(alpha = 0.4f)
                        } else {
                            pf.shadingColor
                        }
                    )

                    if (mesh.isActiveDtc) {
                        drawPath(
                            path = path,
                            color = Color.Red.copy(alpha = dtcPulse),
                            style = Stroke(width = 2.5f.dp.toPx())
                        )
                    } else if (isSelected) {
                        drawPath(
                            path = path,
                            color = MeetColors.neonGreen,
                            style = Stroke(width = 1.5f.dp.toPx())
                        )
                    } else {
                        drawPath(
                            path = path,
                            color = pf.shadingColor.copy(alpha = 0.25f),
                            style = Stroke(width = 0.5f.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

private fun projectPoint(
    v: Vector3D,
    yaw: Float,
    pitch: Float,
    zoom: Float,
    centerX: Float,
    centerY: Float,
    panX: Float,
    panY: Float
): Offset? {
    val rotated = v.rotateY(yaw).rotateX(pitch)
    val zDepth = rotated.z + 180f
    if (zDepth <= 10f) return null
    val projX = centerX + (rotated.x * zoom * 150f) / zDepth + panX
    val projY = centerY + (rotated.y * zoom * 150f) / zDepth + panY
    return Offset(projX, projY)
}

private fun isPointInPolygon(px: Float, py: Float, polygon: List<Offset>): Boolean {
    if (polygon.size < 3) return false
    var intersectCount = 0
    for (i in polygon.indices) {
        val next = (i + 1) % polygon.size
        val v1 = polygon[i]
        val v2 = polygon[next]
        if (((v1.y > py) != (v2.y > py)) &&
            (px < (v2.x - v1.x) * (py - v1.y) / (v2.y - v1.y) + v1.x)
        ) {
            intersectCount++
        }
    }
    return (intersectCount % 2) != 0
}
