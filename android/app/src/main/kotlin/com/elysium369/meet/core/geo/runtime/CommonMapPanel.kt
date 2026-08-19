@file:Suppress("DEPRECATION")

package com.elysium369.meet.core.geo.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.core.geo.CommonMapState
import com.elysium369.meet.core.geo.GeoMarkerRole
import com.elysium369.meet.core.geo.MapCameraIntent
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

/**
 * Universal CommonMapPanel powered by MapLibre.
 * Renders CommonMapState with routes, multi-role markers, and auto-camera fitting.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun CommonMapPanel(
    state: CommonMapState,
    modifier: Modifier = Modifier,
    styleUrl: String = BuildConfig.RIDE_MAP_STYLE_URL,
    onMapReady: ((MapLibreMap) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(lifecycle, mapViewInstance) {
        val mapView = mapViewInstance ?: return@DisposableEffect onDispose {}
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
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapLibre.getInstance(ctx)
                MapView(ctx).apply {
                    mapViewInstance = this
                    getMapAsync { map ->
                        mapInstance = map
                        map.setStyle(styleUrl) {
                            renderCommonMap(ctx, map, state)
                            onMapReady?.invoke(map)
                        }
                    }
                }
            },
            update = {
                mapInstance?.let { map ->
                    if (map.style != null) {
                        renderCommonMap(context, map, state)
                    }
                }
            }
        )

        if (state.showRecenterButton && state.markers.isNotEmpty()) {
            FloatingActionButton(
                onClick = {
                    mapInstance?.let { map ->
                        fitCameraToBounds(map, state)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                shape = CircleShape,
                containerColor = ComposeColor(0xFF00E5FF),
                contentColor = ComposeColor.Black
            ) {
                Text("🎯", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun renderCommonMap(
    context: Context,
    map: MapLibreMap,
    state: CommonMapState,
) {
    map.clear()
    val iconFactory = IconFactory.getInstance(context)

    // Render Routes
    state.routes.forEach { route ->
        if (route.points.size >= 2) {
            val latLngs = route.points.map { LatLng(it.latitude, it.longitude) }
            val parsedColor = runCatching { Color.parseColor(route.routeColorHex) }.getOrDefault(Color.CYAN)
            
            // Outer glow
            map.addPolyline(
                PolylineOptions()
                    .addAll(latLngs)
                    .color(Color.argb(80, Color.red(parsedColor), Color.green(parsedColor), Color.blue(parsedColor)))
                    .width(14f)
            )
            // Core line
            map.addPolyline(
                PolylineOptions()
                    .addAll(latLngs)
                    .color(parsedColor)
                    .width(4f)
            )
        }
    }

    // Render Markers
    state.markers.forEach { marker ->
        val icon = createCommonMarkerIcon(context, iconFactory, marker.role, marker.isHighlighted)
        val accuracyText = marker.point.accuracyMeters?.let { "±${it.toInt()}m" } ?: ""
        map.addMarker(
            MarkerOptions()
                .position(LatLng(marker.point.latitude, marker.point.longitude))
                .title(marker.label)
                .snippet(listOfNotNull(marker.subtitle, accuracyText.ifBlank { null }).joinToString(" · "))
                .icon(icon)
        )
    }

    // Camera Positioning
    when (val intent = state.cameraIntent) {
        is MapCameraIntent.CenterOn -> {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(intent.point.latitude, intent.point.longitude),
                    intent.zoomLevel
                ),
                500
            )
        }
        is MapCameraIntent.FitBounds -> {
            val bounds = LatLngBounds.from(
                intent.bounds.northLat,
                intent.bounds.eastLng,
                intent.bounds.southLat,
                intent.bounds.westLng
            )
            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, intent.paddingDp),
                500
            )
        }
        is MapCameraIntent.FollowUser -> {
            fitCameraToBounds(map, state)
        }
    }
}

private fun fitCameraToBounds(map: MapLibreMap, state: CommonMapState) {
    val allPoints = (
        state.markers.map { LatLng(it.point.latitude, it.point.longitude) } +
        state.routes.flatMap { it.points.map { p -> LatLng(p.latitude, p.longitude) } }
    ).distinct()

    if (allPoints.isEmpty()) return

    if (allPoints.size == 1) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(allPoints.first(), 15.5), 500)
    } else {
        val bounds = LatLngBounds.Builder().includes(allPoints).build()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 72), 500)
    }
}

private fun createCommonMarkerIcon(
    context: Context,
    iconFactory: IconFactory,
    role: GeoMarkerRole,
    isHighlighted: Boolean
): Icon {
    val sizePx = if (isHighlighted) 64 else 52
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val baseColor = when (role) {
        GeoMarkerRole.USER_LOCATION -> Color.rgb(0, 229, 255) // Cyber Cyan
        GeoMarkerRole.VEHICLE_ORIGIN -> Color.rgb(255, 171, 0) // Amber Warning
        GeoMarkerRole.DESTINATION -> Color.rgb(0, 230, 118) // Neon Green
        GeoMarkerRole.PROVIDER_LIVE -> Color.rgb(179, 136, 255) // Purple Accent
        GeoMarkerRole.PROVIDER_WORKSHOP -> Color.rgb(33, 150, 243) // Workshop Blue
        GeoMarkerRole.TOW_TRUCK -> Color.rgb(255, 109, 0) // Tow Orange
        GeoMarkerRole.STORE_LOCATION -> Color.rgb(255, 64, 129) // Pink Accent
        GeoMarkerRole.INCIDENT_PIN -> Color.rgb(255, 23, 68) // Danger Red
        GeoMarkerRole.GENERIC_SERVICE -> Color.rgb(0, 229, 255)
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = baseColor
        style = Paint.Style.FILL
    }
    val center = sizePx / 2f
    val radius = sizePx / 2.5f

    // Outer Glow / Ring
    paint.color = Color.argb(90, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
    canvas.drawCircle(center, center, center, paint)

    // Solid Inner Core
    paint.color = baseColor
    canvas.drawCircle(center, center, radius, paint)

    // White Center Dot
    paint.color = Color.WHITE
    canvas.drawCircle(center, center, radius * 0.45f, paint)

    return iconFactory.fromBitmap(bitmap)
}
