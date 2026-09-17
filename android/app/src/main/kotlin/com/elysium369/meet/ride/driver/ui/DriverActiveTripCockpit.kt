package com.elysium369.meet.ride.driver.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ride.driver.DriverLocationSample
import com.elysium369.meet.ride.driver.DriverTripIntent
import com.elysium369.meet.ride.driver.DriverTripUiState
import com.elysium369.meet.ride.driver.NavigationCameraMode
import com.elysium369.meet.ride.map.RideGeoPoint
import com.elysium369.meet.ride.map.RideMapStateFactory
import com.elysium369.meet.ui.screens.RideMapPanel
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun DriverActiveTripCockpit(
    state: DriverTripUiState,
    onIntent: (DriverTripIntent) -> Unit,
    onOpenMessages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val driverGeoPoint = remember(state.driverLocation) {
        state.driverLocation?.toGeoPoint()
    }

    val mapState = remember(
        state.pickup,
        state.destination,
        state.stops,
        driverGeoPoint,
        state.route,
    ) {
        RideMapStateFactory.create(
            passengerGps = null,
            pickup = state.pickup,
            stops = state.stops,
            destination = state.destination,
            driverGps = driverGeoPoint,
            route = state.route?.geometry,
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        // 1. Interactive Vanguard Map (MapLibre Engine)
        RideMapPanel(
            state = mapState,
            modifier = Modifier.fillMaxSize(),
            userLocation = driverGeoPoint,
            showRecenterButton = false, // We control camera mode explicitly
            onRecenterRequested = { onIntent(DriverTripIntent.RecenterMap) },
        )

        // 2. Top HUD: Turn-by-Turn Navigation Header
        AnimatedVisibility(
            visible = state.navigation != null,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            state.navigation?.let { guidance ->
                DriverNavigationHeader(guidance = guidance)
            }
        }

        // 3. User Message Toast / Banner
        state.userMessage?.let { message ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MeetColors.backgroundDark.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.6f)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = if (state.navigation != null) 90.dp else 16.dp)
                    .padding(horizontal = 24.dp)
                    .clickable { onIntent(DriverTripIntent.DismissUserMessage) },
            ) {
                Text(
                    text = message,
                    color = MeetColors.cyberCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        // 4. Recenter Map Floating Button (Visible when user panned camera)
        if (state.cameraMode == NavigationCameraMode.USER_CONTROLLED) {
            FloatingActionButton(
                onClick = { onIntent(DriverTripIntent.RecenterMap) },
                containerColor = MeetColors.backgroundDark,
                contentColor = MeetColors.neonGreen,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Recentrar mapa en vehículo",
                    tint = MeetColors.neonGreen,
                )
            }
        }

        // 5. Bottom HUD: Operational Trip Bottom Panel
        DriverTripBottomPanel(
            state = state,
            onIntent = onIntent,
            onOpenMessages = onOpenMessages,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )

        // 6. Trip Details & Settings Sheet
        if (state.activeDetailsSheet) {
            DriverTripDetailsSheet(
                state = state,
                onIntent = onIntent,
            )
        }
    }
}

private fun DriverLocationSample.toGeoPoint(): RideGeoPoint = RideGeoPoint(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters.toFloat(),
    capturedAtEpochMs = capturedAt.toEpochMilli(),
)
