package com.elysium369.meet.ui.home

import androidx.compose.runtime.staticCompositionLocalOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The same persisted experience controls backgrounds throughout the APK. */
val LocalHomeExperience = staticCompositionLocalOf { HomeExperience.VANGUARD }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HomeVisualThemeEntryPoint {
    fun homeExperienceRepository(): HomeExperienceRepository
}
