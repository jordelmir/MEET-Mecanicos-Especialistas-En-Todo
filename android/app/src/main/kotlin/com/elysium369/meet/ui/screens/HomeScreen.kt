package com.elysium369.meet.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.elysium369.meet.ride.domain.PlatformOwnerAccessPolicy
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.screens.home.HomeExperienceHost

/**
 * Legacy entry point. Delegates transparently to [HomeExperienceHost]
 * under the MEET Dual Home Experience Architecture (DHEA).
 * Enforces [PlatformOwnerAccessPolicy] across dynamic experiences.
 */
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    HomeExperienceHost(
        navController = navController,
        obdViewModel = viewModel
    )
}
