package com.elysium369.meet.ride.driver.ui

import android.location.Location
import android.os.SystemClock
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.ride.driver.DriverLocationSample
import com.elysium369.meet.ride.driver.DriverActiveTripViewModel
import com.elysium369.meet.ride.driver.DriverTripIntent
import java.time.Instant

@Composable
fun DriverActiveTripCockpitRoute(
    rideId: String,
    initialProjection: RideRequestEntity,
    onOpenMessages: () -> Unit,
    currentLatitude: Double? = null,
    currentLongitude: Double? = null,
    currentAccuracy: Float? = null,
    currentLocation: Location? = null,
    viewModel: DriverActiveTripViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(rideId) {
        viewModel.initialize(rideId, initialProjection)
    }

    LaunchedEffect(currentLatitude, currentLongitude, currentAccuracy, currentLocation) {
        if (currentLocation != null) {
            val sample = DriverLocationSample(
                latitude = currentLocation.latitude,
                longitude = currentLocation.longitude,
                accuracyMeters = currentLocation.accuracy.toDouble(),
                capturedAt = Instant.ofEpochMilli(currentLocation.time),
                capturedAtElapsedRealtimeNanos = currentLocation.elapsedRealtimeNanos,
            )
            viewModel.dispatch(DriverTripIntent.UpdateDriverLocation(sample))
        } else if (currentLatitude != null && currentLongitude != null) {
            val sample = DriverLocationSample(
                latitude = currentLatitude,
                longitude = currentLongitude,
                accuracyMeters = (currentAccuracy ?: 10f).toDouble(),
                capturedAt = Instant.now(),
                capturedAtElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
            )
            viewModel.dispatch(DriverTripIntent.UpdateDriverLocation(sample))
        }
    }

    val state by viewModel.state.collectAsState()

    DriverActiveTripCockpit(
        state = state,
        onIntent = { viewModel.dispatch(it) },
        onOpenMessages = onOpenMessages,
        modifier = modifier.fillMaxSize(),
    )
}
