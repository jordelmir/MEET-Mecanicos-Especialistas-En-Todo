package com.elysium369.meet.safety.di

import com.elysium369.meet.safety.data.remote.SafetyCommandGateway
import com.elysium369.meet.safety.data.remote.SupabaseSafetyCommandGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SafetyBindings {

    @Binds
    @Singleton
    abstract fun bindSafetyCommandGateway(
        implementation: SupabaseSafetyCommandGateway,
    ): SafetyCommandGateway
}
