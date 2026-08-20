package com.elysium369.meet.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.home.HomeExperience
import com.elysium369.meet.ui.home.HomeExperienceUiState
import com.elysium369.meet.ui.home.HomeExperienceViewModel
import com.elysium369.meet.ui.screens.home.adaptive.HomeAdaptiveScreen
import com.elysium369.meet.ui.screens.home.classic.HomeClassicScreen

/**
 * MEET Dual Home Experience Architecture (DHEA) Host.
 * Single entry point mounted at route = "home".
 * Seamlessly transitions between Vanguard Classic and Vanguard Command without interrupting OBD session or domain state.
 */
@Composable
fun HomeExperienceHost(
    navController: NavController,
    obdViewModel: ObdViewModel,
    homeViewModel: HomeExperienceViewModel = hiltViewModel()
) {
    val state by homeViewModel.uiState.collectAsState()

    val currentExperience = when (val s = state) {
        is HomeExperienceUiState.Ready -> s.selected
        else -> HomeExperience.CLASSIC
    }

    val isPreview = when (val s = state) {
        is HomeExperienceUiState.Ready -> s.isPreview
        else -> false
    }

    AnimatedContent(
        targetState = currentExperience,
        transitionSpec = {
            fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(250))
        },
        label = "HomeExperienceHostTransition"
    ) { experience ->
        when (experience) {
            HomeExperience.CLASSIC -> {
                HomeClassicScreen(
                    navController = navController,
                    viewModel = obdViewModel,
                    onSelectExperience = { homeViewModel.switchExperience(it) },
                    onPreviewExperience = { homeViewModel.startPreview(it) },
                    isPreview = isPreview,
                    onCommitPreview = { homeViewModel.commitPreview() },
                    onCancelPreview = { homeViewModel.cancelPreview() }
                )
            }
            HomeExperience.ADAPTIVE -> {
                HomeAdaptiveScreen(
                    navController = navController,
                    viewModel = obdViewModel,
                    onSelectExperience = { homeViewModel.switchExperience(it) },
                    onPreviewExperience = { homeViewModel.startPreview(it) },
                    isPreview = isPreview,
                    onCommitPreview = { homeViewModel.commitPreview() },
                    onCancelPreview = { homeViewModel.cancelPreview() }
                )
            }
        }
    }
}
