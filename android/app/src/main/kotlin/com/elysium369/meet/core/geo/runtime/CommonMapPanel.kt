@file:Suppress("DEPRECATION")

package com.elysium369.meet.core.geo.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon as ComposeIcon
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.core.geo.CommonMapState
import com.elysium369.meet.core.geo.GeoPoint
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
    recenterAlignment: Alignment = Alignment.CenterEnd,
    recenterPadding: PaddingValues = PaddingValues(end = 12.dp),
    userLocation: GeoPoint? = null,
    onMapReady: ((MapLibreMap) -> Unit)? = null,
    onRecenterRequested: (() -> Unit)? = null,
    onMarkerClick: ((String) -> Unit)? = null,
    onMapLongClick: ((GeoPoint) -> Unit)? = null,
) {
    val latestMarkerClick by rememberUpdatedState(onMarkerClick)
    val latestMapLongClick by rememberUpdatedState(onMapLongClick)
    val latestState by rememberUpdatedState(state)
    val markerIds = remember { mutableMapOf<Long, String>() }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var lastRenderedState by remember { mutableStateOf<CommonMapState?>(null) }

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
                        map.uiSettings.isScrollGesturesEnabled = true
                        map.uiSettings.isZoomGesturesEnabled = true
                        map.uiSettings.isRotateGesturesEnabled = true
                        map.uiSettings.isTiltGesturesEnabled = true
                        map.addOnMapLongClickListener { latLng ->
                            latestMapLongClick?.invoke(GeoPoint(latLng.latitude, latLng.longitude))
                            latestMapLongClick != null
                        }
                        map.setOnMarkerClickListener { marker ->
                            val id = markerIds[marker.id]
                            val callback = latestMarkerClick
                            if (id != null && callback != null) { callback(id); true } else false
                        }
                        map.setStyle(styleUrl) {
                            val current = latestState
                            renderCommonMap(ctx, map, current, markerIds)
                            lastRenderedState = current
                            onMapReady?.invoke(map)
                        }
                    }
                }
            },
            update = {
                mapInstance?.let { map ->
                    if (map.style != null && lastRenderedState != state) {
                        renderCommonMap(context, map, state, markerIds)
                        lastRenderedState = state
                    }
                }
            }
        )

        if (state.showRecenterButton) {
            FloatingActionButton(
                onClick = {
                    onRecenterRequested?.invoke()
                    mapInstance?.let { map ->
                        val target = resolveCommonUserCoordinates(context, userLocation, state)
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(target, 16.8),
                            300,
                        )
                    }
                },
                modifier = Modifier
                    .align(recenterAlignment)
                    .padding(recenterPadding)
                    .size(48.dp)
                    .zIndex(20f)
                    .border(
                        BorderStroke(1.5.dp, ComposeColor(0xFF00E5FF)),
                        CircleShape,
                    ),
                shape = CircleShape,
                containerColor = ComposeColor(0xFF07131E).copy(alpha = 0.94f),
                contentColor = ComposeColor(0xFF00E5FF)
            ) {
                ComposeIcon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Centrar en mi ubicación GPS",
                    tint = ComposeColor(0xFF00E5FF),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

private fun resolveCommonUserCoordinates(
    context: Context,
    explicitUserLocation: GeoPoint?,
    state: CommonMapState,
): LatLng {
    if (explicitUserLocation != null && (kotlin.math.abs(explicitUserLocation.latitude) > 0.01 || kotlin.math.abs(explicitUserLocation.longitude) > 0.01)) {
        return LatLng(explicitUserLocation.latitude, explicitUserLocation.longitude)
    }

    val userMarker = state.marker(GeoMarkerRole.USER_LOCATION)
        ?: state.marker(GeoMarkerRole.PROVIDER_LIVE)
        ?: state.marker(GeoMarkerRole.VEHICLE_ORIGIN)
    if (userMarker != null && (kotlin.math.abs(userMarker.point.latitude) > 0.01 || kotlin.math.abs(userMarker.point.longitude) > 0.01)) {
        return LatLng(userMarker.point.latitude, userMarker.point.longitude)
    }

    try {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            val lastGps = locationManager?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            val lastNetwork = locationManager?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            val bestLoc = listOfNotNull(lastGps, lastNetwork)
                .filter { kotlin.math.abs(it.latitude) > 0.01 || kotlin.math.abs(it.longitude) > 0.01 }
                .maxByOrNull { it.time }
            if (bestLoc != null) {
                return LatLng(bestLoc.latitude, bestLoc.longitude)
            }
        }
    } catch (_: Exception) {
    }

    val fallbackPt = state.markers.firstOrNull()?.point
        ?: state.routes.firstOrNull()?.points?.firstOrNull()
        ?: GeoPoint(9.9281, -84.0907)
    return LatLng(fallbackPt.latitude, fallbackPt.longitude)
}

private fun renderCommonMap(
    context: Context,
    map: MapLibreMap,
    state: CommonMapState,
    markerIds: MutableMap<Long, String>,
) {
    markerIds.clear()
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
        val renderedMarker = map.addMarker(
            MarkerOptions()
                .position(LatLng(marker.point.latitude, marker.point.longitude))
                .title(marker.label)
                .snippet(listOfNotNull(marker.subtitle, accuracyText.ifBlank { null }).joinToString(" · "))
                .icon(icon)
        )
        markerIds[renderedMarker.id] = marker.id
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
        GeoMarkerRole.HOMICIDE_PIN -> Color.rgb(210, 18, 48) // Verified public homicide category
        GeoMarkerRole.PRIVATE_INCIDENT_PIN -> Color.rgb(255, 171, 0) // Owner-only pending report
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

    if (role == GeoMarkerRole.HOMICIDE_PIN) {
        // Original generic skull glyph: clear mortality symbol without third-party branding.
        paint.color = Color.WHITE
        canvas.drawCircle(center, center - 3f, radius * .48f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(center - radius * .18f, center - 5f, radius * .11f, paint)
        canvas.drawCircle(center + radius * .18f, center - 5f, radius * .11f, paint)
        canvas.drawRect(center - 2f, center + 1f, center + 2f, center + 7f, paint)
        paint.color = Color.WHITE
        canvas.drawRect(center - radius * .34f, center + radius * .28f, center + radius * .34f, center + radius * .56f, paint)
        paint.color = Color.BLACK
        paint.strokeWidth = 2f
        for (offset in listOf(-5f, 0f, 5f)) canvas.drawLine(center + offset, center + radius * .28f, center + offset, center + radius * .56f, paint)
        return iconFactory.fromBitmap(bitmap)
    }

    // White Center Dot
    paint.color = Color.WHITE
    canvas.drawCircle(center, center, radius * 0.45f, paint)

    return iconFactory.fromBitmap(bitmap)
}
