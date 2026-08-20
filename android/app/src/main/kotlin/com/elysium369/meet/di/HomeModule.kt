package com.elysium369.meet.di

import com.elysium369.meet.ui.home.DefaultHomeExperienceRepository
import com.elysium369.meet.ui.home.HomeExperienceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {

    @Binds
    @Singleton
    abstract fun bindHomeExperienceRepository(
        impl: DefaultHomeExperienceRepository
    ): HomeExperienceRepository
}
