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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon as ComposeIcon
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.R
import com.elysium369.meet.core.geo.CommonMapState
import com.elysium369.meet.core.geo.GeoPoint
import com.elysium369.meet.core.geo.GeoMarkerRole
import com.elysium369.meet.core.geo.MapCameraIntent
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolygonOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * Universal CommonMapPanel powered by MapLibre.
 * Renders CommonMapState with routes, areas (uncertainty footprints), multi-role markers,
 * auto-camera fitting, and fail-honest loading/fallback resilience.
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
    fallbackStyleUrl: String = BuildConfig.RIDE_MAP_STYLE_FALLBACK_URL,
) {
    val latestMarkerClick by rememberUpdatedState(onMarkerClick)
    val latestMapLongClick by rememberUpdatedState(onMapLongClick)
    val latestState by rememberUpdatedState(state)
    val markerIds = remember { mutableMapOf<Long, String>() }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    val interaction = remember { MapInteractionPolicy() }
    var gestureRevision by remember { mutableIntStateOf(0) }
    var lastCameraKey: Any? by remember { mutableStateOf(null) }
    val iconCache = remember { mutableMapOf<Triple<GeoMarkerRole, String, Boolean>, Icon>() }
    var loadPolicy by remember(styleUrl, fallbackStyleUrl) {
        mutableStateOf(MapStyleLoadPolicy.candidates(styleUrl, fallbackStyleUrl))
    }
    var loadRevision by remember { mutableIntStateOf(0) }
    var mapInitializationFailed by remember { mutableStateOf(false) }

    val failureListener = remember {
        MapView.OnDidFailLoadingMapListener {
            if (loadPolicy.phase != MapStyleLoadPolicy.Phase.UNAVAILABLE) {
                loadPolicy = loadPolicy.failed()
            }
        }
    }

    val mapView = remember(context) {
        try {
            MapLibre.getInstance(context.applicationContext)
            MapView(context).apply {
                setBackgroundColor(Color.rgb(8, 15, 24))
                onCreate(null)
            }
        } catch (t: Throwable) {
            android.util.Log.e("CommonMapPanel", "Error initializing MapLibre MapView", t)
            mapInitializationFailed = true
            null
        }
    }

    val renderKey = state.visualKey()
    val cameraKey = renderKey.cameraIntent to (renderKey.markers.map { it.point.latitude to it.point.longitude } to renderKey.routes.map { route -> route.points.map { it.latitude to it.longitude } })

    LaunchedEffect(mapInstance, loadPolicy.attempt, loadRevision) {
        val map = mapInstance ?: return@LaunchedEffect
        val url = loadPolicy.currentUrl ?: return@LaunchedEffect
        val attempt = loadPolicy.attempt
        val revision = loadRevision
        loadPolicy = loadPolicy.copy(phase = MapStyleLoadPolicy.Phase.LOADING)
        map.setStyle(url) {
            if (attempt == loadPolicy.attempt && revision == loadRevision && !mapInitializationFailed && loadPolicy.phase == MapStyleLoadPolicy.Phase.LOADING) {
                loadPolicy = loadPolicy.loaded()
                interaction.invalidate()
                val current = latestState
                renderCommonMap(context, map, current, markerIds, iconCache, moveCamera = !interaction.userOwnsCamera)
                interaction.rendered(current.visualKey())
                lastCameraKey = current.visualKey().cameraIntent to (current.markers.map { it.point.latitude to it.point.longitude } to current.routes.map { route -> route.points.map { it.latitude to it.longitude } })
                onMapReady?.invoke(map)
            }
        }
        delay(12_000)
        if (attempt == loadPolicy.attempt && revision == loadRevision && loadPolicy.phase == MapStyleLoadPolicy.Phase.LOADING) {
            loadPolicy = loadPolicy.failed()
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val currentMapView = mapView ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> currentMapView.onStart()
                Lifecycle.Event.ON_RESUME -> currentMapView.onResume()
                Lifecycle.Event.ON_PAUSE -> currentMapView.onPause()
                Lifecycle.Event.ON_STOP -> currentMapView.onStop()
                Lifecycle.Event.ON_DESTROY -> currentMapView.onDestroy()
                else -> Unit
            }
        }
        currentMapView.addOnDidFailLoadingMapListener(failureListener)
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) currentMapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) currentMapView.onResume()
        onDispose {
            currentMapView.removeOnDidFailLoadingMapListener(failureListener)
            lifecycle.removeObserver(observer)
            if (!currentMapView.isDestroyed) {
                currentMapView.onPause()
                currentMapView.onStop()
                currentMapView.onDestroy()
            }
        }
    }

    Box(modifier = modifier.background(MeetColors.backgroundDeep)) {
        if (mapView != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    mapView.apply {
                        getMapAsync { map ->
                            mapInstance = map
                            bindSmoothMapGestures(this, map, interaction) { gestureRevision++ }
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
                            map.setOnPolygonClickListener { polygon ->
                                markerIds[polygon.id]?.let { latestMarkerClick?.invoke(it) }
                            }
                        }
                    }
                },
                update = {
                    gestureRevision // Reconcile deferred projections once native inertia finishes.
                    mapInstance?.let { map ->
                        if (map.style != null && interaction.shouldRender(renderKey)) {
                            val moveCamera = !interaction.userOwnsCamera && lastCameraKey != cameraKey
                            renderCommonMap(context, map, state, markerIds, iconCache, moveCamera)
                            interaction.rendered(renderKey)
                            if (moveCamera) lastCameraKey = cameraKey
                        }
                    }
                }
            )
        }

        if (loadPolicy.phase != MapStyleLoadPolicy.Phase.READY || mapInitializationFailed) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MeetColors.backgroundDeep)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                val loading = !mapInitializationFailed && loadPolicy.phase == MapStyleLoadPolicy.Phase.LOADING
                if (loading) {
                    CircularProgressIndicator(color = MeetColors.cyberCyan, modifier = Modifier.size(36.dp))
                }
                Text(
                    stringResource(if (loading) R.string.common_map_loading else R.string.common_map_unavailable),
                    color = MeetColors.textPrimary,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (!loading) {
                    TextButton(onClick = {
                        mapInitializationFailed = false
                        loadPolicy = loadPolicy.retry()
                        loadRevision++
                    }) {
                        Text(stringResource(R.string.common_map_retry), color = MeetColors.cyberCyan)
                    }
                }
            }
        }

        if (state.showRecenterButton) {
            FloatingActionButton(
                onClick = {
                    interaction.recenter()
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
                        BorderStroke(1.5.dp, MeetColors.cyberCyan),
                        CircleShape,
                    ),
                shape = CircleShape,
                containerColor = MeetColors.backgroundDark.copy(alpha = 0.94f),
                contentColor = MeetColors.cyberCyan,
            ) {
                ComposeIcon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Centrar en mi ubicación GPS",
                    tint = MeetColors.cyberCyan,
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
        ?: state.areas.firstOrNull()?.boundary?.firstOrNull()
        ?: state.routes.firstOrNull()?.points?.firstOrNull()
        ?: GeoPoint(9.9281, -84.0907)
    return LatLng(fallbackPt.latitude, fallbackPt.longitude)
}

private fun renderCommonMap(
    context: Context,
    map: MapLibreMap,
    state: CommonMapState,
    markerIds: MutableMap<Long, String>,
    iconCache: MutableMap<Triple<GeoMarkerRole, String, Boolean>, Icon>,
    moveCamera: Boolean,
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

    // Public uncertainty footprints are filled areas, never precise incident pins.
    state.areas.forEach { area ->
        val polygon = map.addPolygon(
            PolygonOptions()
                .addAll(area.boundary.map { LatLng(it.latitude, it.longitude) })
                .fillColor(Color.parseColor("#00A6A6"))
                .alpha(0.25f)
                .strokeColor(Color.parseColor("#00E5FF"))
        )
        markerIds[polygon.id] = area.id
    }

    // Render Markers
    state.markers.forEach { marker ->
        val iconKey = Triple(marker.role, marker.iconResName ?: "", marker.isHighlighted)
        val icon = iconCache.getOrPut(iconKey) {
            createCommonMarkerIcon(context, iconFactory, marker.role, marker.iconResName, marker.isHighlighted)
        }
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
    if (moveCamera) when (val intent = state.cameraIntent) {
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
            val north = intent.bounds.northLat
            val south = intent.bounds.southLat
            val east = intent.bounds.eastLng
            val west = intent.bounds.westLng
            if (kotlin.math.abs(north - south) < 0.0001 && kotlin.math.abs(east - west) < 0.0001) {
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(north, east), 14.5),
                    500
                )
            } else {
                val bounds = LatLngBounds.from(north, east, south, west)
                try {
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(bounds, intent.paddingDp.coerceAtLeast(16)),
                        500
                    )
                } catch (_: Exception) {
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng((north + south) / 2.0, (east + west) / 2.0), 13.0),
                        500
                    )
                }
            }
        }
        is MapCameraIntent.FollowUser -> {
            fitCameraToBounds(map, state)
        }
    }
}

private fun fitCameraToBounds(map: MapLibreMap, state: CommonMapState) {
    val allPoints = (
        state.markers.map { LatLng(it.point.latitude, it.point.longitude) } +
        state.areas.flatMap { it.boundary.map { p -> LatLng(p.latitude, p.longitude) } } +
        state.routes.flatMap { it.points.map { p -> LatLng(p.latitude, p.longitude) } }
    ).distinct()

    if (allPoints.isEmpty()) return

    if (allPoints.size == 1) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(allPoints.first(), 15.5), 500)
    } else {
        try {
            val bounds = LatLngBounds.Builder().includes(allPoints).build()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 72), 500)
        } catch (_: Exception) {
            val avgLat = allPoints.map { it.latitude }.average()
            val avgLng = allPoints.map { it.longitude }.average()
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(avgLat, avgLng), 13.0), 500)
        }
    }
}

private fun createCommonMarkerIcon(
    context: Context,
    iconFactory: IconFactory,
    role: GeoMarkerRole,
    categoryOrIcon: String?,
    isHighlighted: Boolean
): Icon {
    val sizePx = if (isHighlighted) 72 else 58
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cleanCategory = (categoryOrIcon ?: "").trim().uppercase()

    val baseColor = when {
        cleanCategory == "HOMICIDE" || role == GeoMarkerRole.HOMICIDE_PIN -> Color.rgb(210, 18, 48) // Crimson Red
        cleanCategory == "DRUG_SALE_ACTIVITY" -> Color.rgb(171, 71, 188) // Purple
        cleanCategory == "VIOLENT_INCIDENT" -> Color.rgb(255, 87, 34) // Fire Orange
        cleanCategory == "THREAT" -> Color.rgb(255, 152, 0) // Amber Warning
        cleanCategory == "MISSING_PERSON" -> Color.rgb(0, 200, 235) // Cyan
        cleanCategory == "INSTITUTIONAL_CONDUCT" -> Color.rgb(33, 150, 243) // Blue
        cleanCategory == "THEFT" -> Color.rgb(255, 193, 7) // Yellow
        role == GeoMarkerRole.USER_LOCATION -> Color.rgb(0, 229, 255) // Cyber Cyan
        role == GeoMarkerRole.VEHICLE_ORIGIN -> Color.rgb(255, 171, 0) // Amber Warning
        role == GeoMarkerRole.DESTINATION -> Color.rgb(0, 230, 118) // Neon Green
        role == GeoMarkerRole.PROVIDER_LIVE -> Color.rgb(179, 136, 255) // Purple Accent
        role == GeoMarkerRole.PROVIDER_WORKSHOP -> Color.rgb(33, 150, 243) // Workshop Blue
        role == GeoMarkerRole.TOW_TRUCK -> Color.rgb(255, 109, 0) // Tow Orange
        role == GeoMarkerRole.STORE_LOCATION -> Color.rgb(255, 64, 129) // Pink Accent
        role == GeoMarkerRole.INCIDENT_PIN -> Color.rgb(255, 23, 68) // Danger Red
        role == GeoMarkerRole.PRIVATE_INCIDENT_PIN -> Color.rgb(255, 171, 0) // Owner-only pending report
        else -> Color.rgb(0, 229, 255)
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = baseColor
        style = Paint.Style.FILL
    }
    val center = sizePx / 2f
    val radius = sizePx / 2.4f

    // Outer Glow / Ring
    paint.color = Color.argb(90, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
    canvas.drawCircle(center, center, center, paint)

    // Solid Inner Core
    paint.color = baseColor
    canvas.drawCircle(center, center, radius, paint)

    // Inner White Backdrop for Icon/Emoji Contrast
    val innerBgRadius = radius * 0.72f
    paint.color = Color.WHITE
    canvas.drawCircle(center, center, innerBgRadius, paint)

    // Determine Icon/Emoji to draw
    val symbol = when {
        cleanCategory == "HOMICIDE" || role == GeoMarkerRole.HOMICIDE_PIN -> "☠️"
        cleanCategory == "DRUG_SALE_ACTIVITY" -> "💊"
        cleanCategory == "VIOLENT_INCIDENT" -> "⚡"
        cleanCategory == "THREAT" -> "⚠️"
        cleanCategory == "MISSING_PERSON" -> "🔍"
        cleanCategory == "INSTITUTIONAL_CONDUCT" -> "🏛️"
        cleanCategory == "THEFT" -> "🚨"
        role == GeoMarkerRole.PRIVATE_INCIDENT_PIN -> "🛡️"
        role == GeoMarkerRole.INCIDENT_PIN -> "⚠️"
        else -> null
    }

    if (symbol != null) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = radius * 0.92f
            textAlign = Paint.Align.CENTER
        }
        val textY = center - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(symbol, center, textY, textPaint)
    } else {
        // Fallback dot
        paint.color = baseColor
        canvas.drawCircle(center, center, radius * 0.35f, paint)
    }

    if (isHighlighted) {
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 229, 255)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(center, center, radius + 2f, highlightPaint)
    }

    return iconFactory.fromBitmap(bitmap)
}

private fun CommonMapState.visualKey(): CommonMapState = copy(
    markers = markers.map { it.copy(point = it.point.copy(capturedAtEpochMs = 0)) },
    routes = routes.map { it.copy(points = it.points.map { p -> p.copy(capturedAtEpochMs = 0) }) },
    areas = areas.map { it.copy(boundary = it.boundary.map { p -> p.copy(capturedAtEpochMs = 0) }) },
    cameraIntent = when (val intent = cameraIntent) {
        is MapCameraIntent.CenterOn -> intent.copy(point = intent.point.copy(capturedAtEpochMs = 0, accuracyMeters = null))
        else -> intent
    },
)
