package com.elysium369.meet.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    explodedServiceView: Boolean = false,
    modifier: Modifier = Modifier
) {
    var yaw by remember { mutableStateOf(-0.72f) }
    var pitch by remember { mutableStateOf(-0.58f) }
    var zoom by remember { mutableStateOf(1.9f) }
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

    val explodedProgress by animateFloatAsState(
        targetValue = if (explodedServiceView) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 120f),
        label = "explodedProgress"
    )

    var tapOffset by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, gestureRotation ->
                    yaw += pan.x * 0.0065f + (gestureRotation * PI.toFloat() / 180f) * 0.35f
                    pitch = (pitch + pan.y * 0.0055f).coerceIn(-1.15f, 0.55f)
                    zoom = (zoom * gestureZoom).coerceIn(0.75f, 4.25f)
                    panX = (panX + pan.x * 0.08f).coerceIn(-220f, 220f)
                    panY = (panY + pan.y * 0.08f).coerceIn(-180f, 180f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    tapOffset = it
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerX = w / 2f
            val centerY = h * 0.55f
            val focalLength = min(w, h) * 1.22f
            val cameraDistance = 245f

            drawCircle(
                color = MeetColors.neonGreen.copy(alpha = 0.03f),
                radius = min(w, h) * 0.58f,
                center = Offset(centerX, centerY + h * 0.04f)
            )

            val gridColor = MeetColors.neonGreen.copy(alpha = 0.05f)
            for (i in -6..6) {
                val zStart = i * 20f
                val p1 = projectPoint(Vector3D(-110f, 48f, zStart), yaw, pitch, zoom, centerX, centerY, panX, panY, focalLength, cameraDistance)
                val p2 = projectPoint(Vector3D(110f, 48f, zStart), yaw, pitch, zoom, centerX, centerY, panX, panY, focalLength, cameraDistance)
                if (p1 != null && p2 != null) {
                    drawLine(gridColor, p1, p2, 1f)
                }

                val xStart = i * 20f
                val p3 = projectPoint(Vector3D(xStart, 48f, -110f), yaw, pitch, zoom, centerX, centerY, panX, panY, focalLength, cameraDistance)
                val p4 = projectPoint(Vector3D(xStart, 48f, 110f), yaw, pitch, zoom, centerX, centerY, panX, panY, focalLength, cameraDistance)
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
            val meshCenters = mutableMapOf<String, Pair<Mesh3D, Offset>>()

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

                val serviceOffset = serviceExplodedOffset(mesh.id, explodedProgress)
                val worldVertices = mesh.transformToWorld(pistonOffset, isSparkTriggered).map { it + serviceOffset }

                val screenPoints = worldVertices.map { v ->
                    val rotatedCam = v.rotateY(yaw).rotateX(pitch)
                    val zDepth = rotatedCam.z + cameraDistance
                    val projX = centerX + (rotatedCam.x * zoom * focalLength) / zDepth + panX
                    val projY = centerY + (rotatedCam.y * zoom * focalLength) / zDepth + panY
                    Triple(Offset(projX, projY), zDepth, rotatedCam)
                }

                if (screenPoints.isNotEmpty()) {
                    val avgX = screenPoints.map { it.first.x }.average().toFloat()
                    val avgY = screenPoints.map { it.first.y }.average().toFloat()
                    meshCenters[mesh.id] = mesh to Offset(avgX, avgY)
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
                        if (!face.isLineOnly && pts.size > 2) close()
                    }
                }

                if (face.isLineOnly) {
                    if (pts.size >= 2) {
                        for (i in 0 until pts.lastIndex) {
                            drawLine(
                                color = pf.shadingColor,
                                start = pts[i],
                                end = pts[i + 1],
                                strokeWidth = if (mesh.isActiveDtc) 3.2.dp.toPx() else 2.dp.toPx()
                            )
                        }
                    } else {
                        drawPath(
                            path = path,
                            color = pf.shadingColor,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
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

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 12.sp.toPx()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(178, 0, 10, 16)
            }

            val labeledMeshes = meshCenters.values
                .filter { (mesh, _) -> shouldLabelMesh(mesh, selectedComponentId) }
                .sortedByDescending { (mesh, _) -> if (mesh.isActiveDtc) 2 else if (selectedComponentId != null && mesh.id.startsWith(selectedComponentId)) 1 else 0 }
                .take(8)

            labeledMeshes.forEachIndexed { index, (mesh, center) ->
                val label = serviceLabel(mesh.name)
                val textWidth = labelPaint.measureText(label)
                val labelX = if (index % 2 == 0) (center.x + 18f).coerceAtMost(w - textWidth - 18f) else (center.x - textWidth - 18f).coerceAtLeast(18f)
                val labelY = (center.y - 18f - index * 2f).coerceIn(24f, h - 18f)
                val color = when {
                    mesh.isActiveDtc -> MeetColors.error
                    selectedComponentId != null && mesh.id.startsWith(selectedComponentId) -> MeetColors.neonGreen
                    else -> MeetColors.cyberCyan
                }

                drawLine(
                    color = color.copy(alpha = 0.75f),
                    start = center,
                    end = Offset(labelX, labelY - 4f),
                    strokeWidth = 1.2.dp.toPx()
                )
                drawCircle(color.copy(alpha = 0.85f), radius = 4.dp.toPx(), center = center)
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.drawRoundRect(
                        labelX - 7f,
                        labelY - 18f,
                        labelX + textWidth + 7f,
                        labelY + 5f,
                        8f,
                        8f,
                        labelBgPaint
                    )
                    labelPaint.color = color.toArgbInt()
                    native.drawText(label, labelX, labelY, labelPaint)
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
    panY: Float,
    focalLength: Float,
    cameraDistance: Float
): Offset? {
    val rotated = v.rotateY(yaw).rotateX(pitch)
    val zDepth = rotated.z + cameraDistance
    if (zDepth <= 18f) return null
    val projX = centerX + (rotated.x * zoom * focalLength) / zDepth + panX
    val projY = centerY + (rotated.y * zoom * focalLength) / zDepth + panY
    return Offset(projX, projY)
}

private fun serviceExplodedOffset(meshId: String, progress: Float): Vector3D {
    if (progress <= 0f) return Vector3D(0f, 0f, 0f)
    val direction = when {
        meshId.startsWith("spark_plug_") || meshId.startsWith("ignition_coil_") || meshId.startsWith("spark_gap_") -> Vector3D(0f, -28f, 0f)
        meshId.startsWith("injector_") || meshId == "fuel_rail" -> Vector3D(0f, -12f, -18f)
        meshId == "intake_manifold" || meshId.startsWith("intake_runner_") || meshId == "throttle_body" -> Vector3D(0f, -10f, -30f)
        meshId == "exhaust_manifold" || meshId.startsWith("exhaust_runner_") || meshId.contains("o2") || meshId == "catalytic_converter" -> Vector3D(0f, 8f, 34f)
        meshId == "alternator" || meshId == "serpentine_belt" || meshId == "water_pump" || meshId == "thermostat_housing" -> Vector3D(-28f, 0f, 0f)
        meshId == "oil_pan" || meshId == "oil_filter" -> Vector3D(0f, 26f, 0f)
        else -> Vector3D(0f, 0f, 0f)
    }
    return direction * progress
}

private fun shouldLabelMesh(mesh: Mesh3D, selectedComponentId: String?): Boolean {
    if (mesh.id == "engine_block" || mesh.id.startsWith("fuse_element_")) return false
    if (mesh.isActiveDtc) return true
    if (selectedComponentId != null && mesh.id.startsWith(selectedComponentId)) return true
    return mesh.id in setOf(
        "throttle_body",
        "maf_sensor",
        "fuel_rail",
        "alternator",
        "catalytic_converter",
        "inverter_module",
        "hv_battery_pack",
        "safety_disconnect"
    ) || mesh.id.startsWith("spark_plug_0") || mesh.id.startsWith("ignition_coil_0")
}

private fun serviceLabel(name: String): String {
    return name
        .replace("(Pre-Cat)", "pre-cat")
        .replace("(Post-Cat)", "post-cat")
        .replace("Sensor ", "")
        .replace(" de ", " ")
        .take(28)
}

private fun Color.toArgbInt(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).roundToInt().coerceIn(0, 255),
        (red * 255).roundToInt().coerceIn(0, 255),
        (green * 255).roundToInt().coerceIn(0, 255),
        (blue * 255).roundToInt().coerceIn(0, 255)
    )
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
