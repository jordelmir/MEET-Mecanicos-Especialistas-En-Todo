@file:Suppress("DEPRECATION")

package com.elysium369.meet.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.ride.map.RideGeoPoint
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
    var styleRequested by remember { mutableStateOf(false) }
    var styleReady by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var selectedStyleIndex by remember { mutableIntStateOf(0) }
    var latestMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var configuredMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var userControlsCamera by remember { mutableStateOf(false) }
    var lastCameraSignature by remember { mutableStateOf<String?>(null) }
    var pinSelectionPoint by remember { mutableStateOf(pinSelectionInitialPoint) }
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
            styleReady = true
            mapError = null
            renderRideState(context, map, state, moveCamera = false)
        }
    }

    LaunchedEffect(pinSelectionEnabled, pinSelectionInitialPoint, latestMap, styleReady) {
        if (!pinSelectionEnabled) return@LaunchedEffect
        val map = latestMap ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        pinSelectionInitialPoint?.let { point ->
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
                            renderRideState(context, map, state, shouldMoveCamera)
                            if (shouldMoveCamera) lastCameraSignature = signature
                        }
                        !styleRequested && styleCandidates.isNotEmpty() -> {
                            styleRequested = true
                            map.setStyle(styleCandidates[selectedStyleIndex]) {
                                styleReady = true
                                mapError = null
                                val signature = state.cameraSignature()
                                renderRideState(context, map, state, moveCamera = true)
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
                        renderRideState(context, map, state, moveCamera = true)
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
    moveCamera: Boolean,
) {
    map.clear()
    val iconFactory = IconFactory.getInstance(context)
    val icons = RideMarkerRole.entries.associateWith { role ->
        createMarkerIcon(context, iconFactory, role)
    }

    state.route
        .takeIf { it.size >= 2 }
        ?.map { LatLng(it.latitude, it.longitude) }
        ?.let { route ->
            map.addPolyline(
                PolylineOptions()
                    .addAll(route)
                    .color(Color.rgb(0, 229, 255))
                    .alpha(0.9f)
                    .width(7f),
            )
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
): Icon {
    val density = context.resources.displayMetrics.density
    val size = (52 * density).toInt().coerceAtLeast(52)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = role.color()
        style = Paint.Style.FILL
        setShadowLayer(5f * density, 0f, 2f * density, Color.BLACK)
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 17f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val center = size / 2f
    when (role) {
        RideMarkerRole.DRIVER -> drawElysiumDragon(canvas, markerPaint, size.toFloat())
        RideMarkerRole.PASSENGER_GPS -> drawPassengerSilhouette(canvas, markerPaint, size.toFloat())
        else -> {
            canvas.drawCircle(center, center, size * 0.39f, markerPaint)
            val baseline = center - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(role.shortLabel(), center, baseline, textPaint)
        }
    }
    return iconFactory.fromBitmap(bitmap)
}

/** Proprietary Elysium fire-dragon silhouette; intentionally not based on a licensed character. */
private fun drawElysiumDragon(canvas: Canvas, paint: Paint, size: Float) {
    paint.color = Color.rgb(255, 23, 68)
    val path = Path().apply {
        moveTo(size * 0.50f, size * 0.10f)
        lineTo(size * 0.61f, size * 0.26f)
        lineTo(size * 0.82f, size * 0.18f)
        lineTo(size * 0.72f, size * 0.40f)
        lineTo(size * 0.89f, size * 0.49f)
        lineTo(size * 0.68f, size * 0.54f)
        cubicTo(size * 0.72f, size * 0.76f, size * 0.61f, size * 0.90f, size * 0.50f, size * 0.92f)
        cubicTo(size * 0.39f, size * 0.90f, size * 0.28f, size * 0.76f, size * 0.32f, size * 0.54f)
        lineTo(size * 0.11f, size * 0.49f)
        lineTo(size * 0.28f, size * 0.40f)
        lineTo(size * 0.18f, size * 0.18f)
        lineTo(size * 0.39f, size * 0.26f)
        close()
    }
    canvas.drawPath(path, paint)
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 214, 0)
        style = Paint.Style.FILL
    }.also { eyePaint ->
        canvas.drawCircle(size * 0.43f, size * 0.43f, size * 0.035f, eyePaint)
        canvas.drawCircle(size * 0.57f, size * 0.43f, size * 0.035f, eyePaint)
    }
}

private fun drawPassengerSilhouette(canvas: Canvas, paint: Paint, size: Float) {
    paint.color = Color.rgb(0, 229, 255)
    canvas.drawCircle(size * 0.50f, size * 0.27f, size * 0.14f, paint)
    val body = Path().apply {
        moveTo(size * 0.34f, size * 0.44f)
        cubicTo(size * 0.22f, size * 0.48f, size * 0.20f, size * 0.69f, size * 0.24f, size * 0.78f)
        lineTo(size * 0.39f, size * 0.78f)
        lineTo(size * 0.39f, size * 0.94f)
        lineTo(size * 0.48f, size * 0.94f)
        lineTo(size * 0.50f, size * 0.75f)
        lineTo(size * 0.52f, size * 0.94f)
        lineTo(size * 0.61f, size * 0.94f)
        lineTo(size * 0.61f, size * 0.78f)
        lineTo(size * 0.76f, size * 0.78f)
        cubicTo(size * 0.80f, size * 0.69f, size * 0.78f, size * 0.48f, size * 0.66f, size * 0.44f)
        close()
    }
    canvas.drawPath(body, paint)
}

private fun RideMarkerRole.color(): Int =
    when (this) {
        RideMarkerRole.PASSENGER_GPS -> Color.rgb(0, 188, 212)
        RideMarkerRole.PICKUP -> Color.rgb(0, 200, 83)
        RideMarkerRole.STOP -> Color.rgb(255, 214, 0)
        RideMarkerRole.DESTINATION -> Color.rgb(213, 0, 249)
        RideMarkerRole.DRIVER -> Color.rgb(255, 23, 68)
        RideMarkerRole.ROAD_INCIDENT -> Color.rgb(255, 45, 85)
    }

private fun RideMarkerRole.shortLabel(): String =
    when (this) {
        RideMarkerRole.PASSENGER_GPS -> "U"
        RideMarkerRole.PICKUP -> "R"
        RideMarkerRole.STOP -> "P"
        RideMarkerRole.DESTINATION -> "D"
        RideMarkerRole.DRIVER -> "C"
        RideMarkerRole.ROAD_INCIDENT -> "!"
    }
