@file:Suppress("DEPRECATION")

package com.elysium369.meet.ui.screens

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.ride.map.RideGeoPoint
import com.elysium369.meet.ride.map.RideMapAvatarRenderer
import com.elysium369.meet.ride.map.RideMapAvatarSelection
import com.elysium369.meet.ride.map.RideMapAvatarStore
import com.elysium369.meet.ride.map.RideMapState
import com.elysium369.meet.ride.map.RideMarkerRole
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineBlur
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import java.util.Locale

@SuppressLint("ClickableViewAccessibility")
@Composable
fun RideMapPanel(
    state: RideMapState,
    modifier: Modifier = Modifier,
    styleUrl: String = BuildConfig.RIDE_MAP_STYLE_URL,
    fallbackStyleUrl: String = BuildConfig.RIDE_MAP_STYLE_FALLBACK_URL,
    pinSelectionEnabled: Boolean = false,
    pinSelectionLabel: String = "Ubicación exacta",
    pinSelectionInitialPoint: RideGeoPoint? = null,
    onPinSelectionChanged: ((RideGeoPoint) -> Unit)? = null,
    onPinSelectionConfirmed: ((RideGeoPoint) -> Unit)? = null,
    onPinSelectionCancelled: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val avatarSelection = remember(context) { RideMapAvatarStore(context).load() }
    val routePulseController = remember { RoutePulseController() }
    var styleRequested by remember { mutableStateOf(false) }
    var styleReady by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var selectedStyleIndex by remember { mutableIntStateOf(0) }
    var latestMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var configuredMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var userControlsCamera by remember { mutableStateOf(false) }
    var lastCameraSignature by remember { mutableStateOf<String?>(null) }
    var pinSelectionPoint by remember { mutableStateOf(pinSelectionInitialPoint) }
    var pinCameraInitialized by remember { mutableStateOf(false) }
    var recenterRequest by remember { mutableIntStateOf(0) }
    val currentPinSelectionEnabled by rememberUpdatedState(pinSelectionEnabled)
    val currentOnPinSelectionChanged by rememberUpdatedState(onPinSelectionChanged)
    val styleCandidates = remember(styleUrl, fallbackStyleUrl) {
        listOf(styleUrl, fallbackStyleUrl)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(null) }
    }
    val failureListener = remember {
        MapView.OnDidFailLoadingMapListener { error ->
            mapError = error.ifBlank { "El proveedor de mapas no respondió" }
        }
    }

    LaunchedEffect(mapError, latestMap, selectedStyleIndex, styleCandidates) {
        val map = latestMap ?: return@LaunchedEffect
        if (mapError == null || selectedStyleIndex >= styleCandidates.lastIndex) return@LaunchedEffect
        selectedStyleIndex += 1
        styleRequested = true
        styleReady = false
        map.setStyle(styleCandidates[selectedStyleIndex]) {
            applyVanguardRoadHierarchy(map)
            styleReady = true
            mapError = null
            renderRideState(context, map, state, avatarSelection, routePulseController, moveCamera = false)
        }
    }

    val pinInitialPointAvailable = pinSelectionInitialPoint != null
    LaunchedEffect(pinSelectionEnabled, pinInitialPointAvailable, latestMap, styleReady) {
        if (!pinSelectionEnabled) return@LaunchedEffect
        if (pinCameraInitialized) return@LaunchedEffect
        val map = latestMap ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        pinSelectionInitialPoint?.let { point ->
            pinCameraInitialized = true
            pinSelectionPoint = point
            userControlsCamera = true
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(point.latitude, point.longitude),
                    17.0,
                ),
                450,
            )
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        mapView.addOnDidFailLoadingMapListener(failureListener)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onStart()
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }

        onDispose {
            mapView.removeOnDidFailLoadingMapListener(failureListener)
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                mapView.onPause()
            }
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                mapView.onStop()
            }
            if (!mapView.isDestroyed) {
                mapView.onDestroy()
            }
            routePulseController.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.getMapAsync { map ->
                    latestMap = map
                    if (configuredMap !== map) {
                        configuredMap = map
                        map.uiSettings.apply {
                            isZoomGesturesEnabled = true
                            isQuickZoomGesturesEnabled = true
                            isScrollGesturesEnabled = true
                            isRotateGesturesEnabled = true
                            isTiltGesturesEnabled = true
                            zoomRate = 0.85f
                        }
                        view.setOnTouchListener { touchedView, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN,
                                MotionEvent.ACTION_POINTER_DOWN,
                                MotionEvent.ACTION_MOVE,
                                -> touchedView.parent?.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP,
                                MotionEvent.ACTION_CANCEL,
                                -> {
                                    touchedView.parent?.requestDisallowInterceptTouchEvent(false)
                                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                                        touchedView.performClick()
                                    }
                                }
                            }
                            false
                        }
                        map.addOnCameraMoveStartedListener { reason ->
                            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                userControlsCamera = true
                            }
                        }
                        map.addOnCameraIdleListener {
                            if (currentPinSelectionEnabled) {
                                val target = map.cameraPosition.target
                                    ?: return@addOnCameraIdleListener
                                val point = RideGeoPoint(
                                    latitude = target.latitude,
                                    longitude = target.longitude,
                                    accuracyMeters = null,
                                    capturedAtEpochMs = System.currentTimeMillis(),
                                )
                                pinSelectionPoint = point
                                currentOnPinSelectionChanged?.invoke(point)
                            }
                        }
                    }
                    when {
                        styleReady -> {
                            val signature = state.cameraSignature()
                            val shouldMoveCamera = !pinSelectionEnabled &&
                                (!userControlsCamera || recenterRequest > 0) &&
                                signature != lastCameraSignature
                            renderRideState(context, map, state, avatarSelection, routePulseController, shouldMoveCamera)
                            if (shouldMoveCamera) lastCameraSignature = signature
                        }
                        !styleRequested && styleCandidates.isNotEmpty() -> {
                            styleRequested = true
                            map.setStyle(styleCandidates[selectedStyleIndex]) {
                                applyVanguardRoadHierarchy(map)
                                styleReady = true
                                mapError = null
                                val signature = state.cameraSignature()
                                renderRideState(context, map, state, avatarSelection, routePulseController, moveCamera = true)
                                lastCameraSignature = signature
                            }
                        }
                    }
                }
            },
        )

        if (state.markers.isEmpty()) {
            RideMapStatus(
                message = "Esperando ubicaciones verificadas…",
                modifier = Modifier.align(Alignment.Center),
            )
        }
        mapError?.let { error ->
            RideMapStatus(
                message = if (selectedStyleIndex < styleCandidates.lastIndex) {
                    "Cambiando a mapa de respaldo. Los datos del viaje siguen visibles.\n$error"
                } else {
                    "Mapa no disponible. Los datos del viaje siguen visibles.\n$error"
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (pinSelectionEnabled) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .wrapContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    color = ComposeColor(0xFF07131E).copy(alpha = 0.92f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = pinSelectionLabel,
                        color = ComposeColor(0xFF00E5FF),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                Text(
                    text = "▼",
                    color = ComposeColor(0xFFFF1744),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            ) {
                Button(
                    onClick = { onPinSelectionCancelled?.invoke() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeColor(0xFF111827).copy(alpha = 0.94f),
                    ),
                ) {
                    Text("CANCELAR")
                }
                Button(
                    onClick = {
                        pinSelectionPoint?.let { onPinSelectionConfirmed?.invoke(it) }
                    },
                    enabled = pinSelectionPoint != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeColor(0xFF00C853),
                    ),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text("CONFIRMAR PIN", fontWeight = FontWeight.Black)
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MapControlButton("+") {
                    latestMap?.animateCamera(CameraUpdateFactory.zoomIn(), 180)
                }
                Spacer(Modifier.height(8.dp))
                MapControlButton("−") {
                    latestMap?.animateCamera(CameraUpdateFactory.zoomOut(), 180)
                }
                Spacer(Modifier.height(8.dp))
                MapControlButton("◎") {
                    pinSelectionInitialPoint?.let { point ->
                        latestMap?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(point.latitude, point.longitude),
                                17.25,
                            ),
                            320,
                        )
                    }
                }
            }
            pinSelectionPoint?.let { point ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    color = ComposeColor(0xFF07131E).copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "%.6f, %.6f",
                            point.latitude,
                            point.longitude,
                        ),
                        color = ComposeColor.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
        } else if (state.markers.isNotEmpty()) {
            FloatingActionButton(
                onClick = {
                    userControlsCamera = false
                    lastCameraSignature = null
                    recenterRequest += 1
                    latestMap?.let { map ->
                        renderRideState(context, map, state, avatarSelection, routePulseController, moveCamera = true)
                        lastCameraSignature = state.cameraSignature()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(44.dp),
                shape = CircleShape,
                containerColor = ComposeColor(0xFF07131E).copy(alpha = 0.92f),
                contentColor = ComposeColor(0xFF00E5FF),
            ) {
                Text("◎", fontWeight = FontWeight.Black)
            }
        }

        if (!pinSelectionEnabled && state.route.size >= 2) {
            VanguardRouteLegend(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun VanguardRouteLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        ComposeColor(0xE607131E),
                        ComposeColor(0xD9121530),
                        ComposeColor(0xE607131E),
                    ),
                ),
                shape = MaterialTheme.shapes.medium,
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        ComposeColor(0xFF00F5D4),
                        ComposeColor(0xFF00B8FF),
                        ComposeColor(0xFFB026FF),
                    ),
                ),
                shape = MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("◈", color = ComposeColor(0xFF00F5D4), fontWeight = FontWeight.Black)
        Text(
            text = "  VANGUARD NAV  •  RUTA ACTIVA",
            color = ComposeColor.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun MapControlButton(
    label: String,
    onClick: () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(46.dp),
        shape = CircleShape,
        containerColor = ComposeColor(0xFF07131E).copy(alpha = 0.94f),
        contentColor = ComposeColor(0xFF00E5FF),
    ) {
        Text(label, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun RideMapStatus(
    message: String,
    modifier: Modifier,
) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
            .padding(16.dp)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

private fun renderRideState(
    context: Context,
    map: MapLibreMap,
    state: RideMapState,
    avatarSelection: RideMapAvatarSelection,
    routePulseController: RoutePulseController,
    moveCamera: Boolean,
) {
    routePulseController.bind(null, null)
    map.clear()
    val iconFactory = IconFactory.getInstance(context)
    val icons = RideMarkerRole.entries.associateWith { role ->
        createMarkerIcon(context, iconFactory, role, avatarSelection)
    }

    state.route
        .takeIf { it.size >= 2 }
        ?.map { LatLng(it.latitude, it.longitude) }
        ?.let { route ->
            map.addPolyline(
                PolylineOptions()
                    .addAll(route)
                    .color(Color.argb(210, 0, 2, 12))
                    .width(17f),
            )
            val pulse = map.addPolyline(
                PolylineOptions()
                    .addAll(route)
                    .color(Color.argb(92, 0, 229, 255))
                    .width(12f),
            )
            addNeonRouteSegments(map, route)
            map.addPolyline(
                PolylineOptions()
                    .addAll(route)
                    .color(Color.argb(220, 222, 255, 255))
                    .width(2.1f),
            )
            routePulseController.bind(map, pulse)
        }

    state.markers.forEach { marker ->
        val accuracy = marker.point.accuracyMeters?.let {
            String.format(Locale.ROOT, "Precisión ±%.0f m", it)
        } ?: "Precisión no reportada"
        val freshness = when (
            marker.point.freshness(
                nowEpochMs = System.currentTimeMillis(),
                staleAfterMs = 30_000,
            )
        ) {
            com.elysium369.meet.ride.map.RidePositionFreshness.FRESH -> "posición reciente"
            com.elysium369.meet.ride.map.RidePositionFreshness.STALE -> "posición desactualizada"
            com.elysium369.meet.ride.map.RidePositionFreshness.CLOCK_SKEW -> "hora del dispositivo no coincide"
        }
        map.addMarker(
            MarkerOptions()
                .position(LatLng(marker.point.latitude, marker.point.longitude))
                .title(marker.label)
                .snippet("$accuracy · $freshness")
                .icon(icons.getValue(marker.role)),
        )
    }

    val allPoints = (
        state.markers.map { it.point } + state.route
        ).distinctBy { it.latitude to it.longitude }
    if (moveCamera && allPoints.isNotEmpty()) {
        val latLngs = allPoints.map { LatLng(it.latitude, it.longitude) }
        if (latLngs.size == 1) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(latLngs.first(), 16.0),
                500,
            )
        } else {
            val bounds = LatLngBounds.Builder().includes(latLngs).build()
            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, 96),
                500,
            )
        }
    }
}

private fun addNeonRouteSegments(map: MapLibreMap, route: List<LatLng>) {
    val palette = intArrayOf(
        Color.rgb(0, 245, 212),
        Color.rgb(0, 184, 255),
        Color.rgb(46, 91, 255),
        Color.rgb(124, 77, 255),
        Color.rgb(176, 38, 255),
        Color.rgb(255, 23, 145),
    )
    val segmentCount = minOf(18, route.lastIndex)
    if (segmentCount <= 0) return
    repeat(segmentCount) { segment ->
        val start = (segment * route.lastIndex) / segmentCount
        val end = ((segment + 1) * route.lastIndex) / segmentCount
        if (end <= start) return@repeat
        val colorIndex = (segment * palette.lastIndex / segmentCount).coerceIn(palette.indices)
        map.addPolyline(
            PolylineOptions()
                .addAll(route.subList(start, end + 1))
                .color(palette[colorIndex])
                .width(7.2f),
        )
    }
}

private fun applyVanguardRoadHierarchy(map: MapLibreMap) {
    val style = map.style ?: return
    style.layers.filterIsInstance<LineLayer>().forEach { layer ->
        val id = layer.id.lowercase(Locale.ROOT)
        val roadLevel = when {
            id.contains("motorway") || id.contains("freeway") || id.contains("trunk") -> 3
            id.contains("primary") || id.contains("highway") -> 2
            id.contains("secondary") || id.contains("tertiary") -> 1
            id.contains("road") || id.contains("street") || id.contains("transportation") -> 0
            else -> return@forEach
        }
        val color = when (roadLevel) {
            3 -> Color.rgb(0, 184, 255)
            2 -> Color.rgb(0, 245, 212)
            1 -> Color.rgb(78, 96, 180)
            else -> Color.rgb(36, 68, 90)
        }
        val opacity = when (roadLevel) {
            3 -> 0.92f
            2 -> 0.78f
            1 -> 0.58f
            else -> 0.38f
        }
        runCatching {
            layer.setProperties(
                lineColor(color),
                lineOpacity(opacity),
                lineBlur(if (roadLevel >= 2) 0.35f else 0f),
            )
        }
    }
}

private class RoutePulseController {
    private var animator: ValueAnimator? = null

    fun bind(map: MapLibreMap?, polyline: org.maplibre.android.annotations.Polyline?) {
        animator?.cancel()
        animator = null
        if (map == null || polyline == null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1_800L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animation ->
                val phase = animation.animatedValue as Float
                val alpha = (58 + 86 * phase).toInt()
                polyline.color = Color.argb(alpha, 0, 229, 255)
                polyline.width = 11f + phase * 3f
                runCatching { map.updatePolyline(polyline) }
            }
            start()
        }
    }

    fun release() = bind(null, null)
}

private fun RideMapState.cameraSignature(): String = buildString {
    route.firstOrNull()?.let { append("${it.latitude}:${it.longitude}|") }
    route.lastOrNull()?.let { append("${it.latitude}:${it.longitude}|") }
    markers
        .filter { it.role !in setOf(RideMarkerRole.DRIVER, RideMarkerRole.PASSENGER_GPS) }
        .sortedBy { it.id }
        .forEach { append("${it.id}:${it.point.latitude}:${it.point.longitude}|") }
}

private fun createMarkerIcon(
    context: Context,
    iconFactory: IconFactory,
    role: RideMarkerRole,
    selection: RideMapAvatarSelection,
): Icon {
    return iconFactory.fromBitmap(RideMapAvatarRenderer.render(context, role, selection))
}
