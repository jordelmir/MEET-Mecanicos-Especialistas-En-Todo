@file:Suppress("DEPRECATION")

package com.elysium369.meet.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.elysium369.meet.BuildConfig
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

@Composable
fun RideMapPanel(
    state: RideMapState,
    modifier: Modifier = Modifier,
    styleUrl: String = BuildConfig.RIDE_MAP_STYLE_URL,
    fallbackStyleUrl: String = BuildConfig.RIDE_MAP_STYLE_FALLBACK_URL,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var styleRequested by remember { mutableStateOf(false) }
    var styleReady by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var selectedStyleIndex by remember { mutableIntStateOf(0) }
    var latestMap by remember { mutableStateOf<MapLibreMap?>(null) }
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
            renderRideState(context, map, state)
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
                    when {
                        styleReady -> renderRideState(context, map, state)
                        !styleRequested && styleCandidates.isNotEmpty() -> {
                            styleRequested = true
                            map.setStyle(styleCandidates[selectedStyleIndex]) {
                                styleReady = true
                                mapError = null
                                renderRideState(context, map, state)
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
    if (allPoints.isNotEmpty()) {
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
    canvas.drawCircle(center, center, size * 0.39f, markerPaint)
    val baseline = center - (textPaint.ascent() + textPaint.descent()) / 2f
    canvas.drawText(role.shortLabel(), center, baseline, textPaint)
    return iconFactory.fromBitmap(bitmap)
}

private fun RideMarkerRole.color(): Int =
    when (this) {
        RideMarkerRole.PASSENGER_GPS -> Color.rgb(0, 188, 212)
        RideMarkerRole.PICKUP -> Color.rgb(0, 200, 83)
        RideMarkerRole.STOP -> Color.rgb(255, 214, 0)
        RideMarkerRole.DESTINATION -> Color.rgb(213, 0, 249)
        RideMarkerRole.DRIVER -> Color.rgb(255, 145, 0)
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
