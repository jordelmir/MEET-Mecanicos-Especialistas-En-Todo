package com.elysium369.meet.di

import com.elysium369.meet.ride.automatch.RideAutoMatchGateway
import com.elysium369.meet.ride.automatch.SupabaseRideAutoMatchGateway
import com.elysium369.meet.ride.demand.RideDemandGateway
import com.elysium369.meet.ride.demand.SupabaseRideDemandGateway
import com.elysium369.meet.ride.dispatch.RideExposureGateway
import com.elysium369.meet.ride.dispatch.SupabaseRideExposureGateway
import com.elysium369.meet.ride.eta.RideEtaGateway
import com.elysium369.meet.ride.eta.SupabaseRideEtaGateway
import com.elysium369.meet.ride.nextjob.RideNextJobGateway
import com.elysium369.meet.ride.nextjob.SupabaseRideNextJobGateway
import com.elysium369.meet.ride.payment.RidePaymentGateway
import com.elysium369.meet.ride.payment.SupabaseRidePaymentGateway
import com.elysium369.meet.ride.presence.RidePresenceGateway
import com.elysium369.meet.ride.presence.SupabaseRidePresenceGateway
import com.elysium369.meet.ride.reputation.RideReputationGateway
import com.elysium369.meet.ride.reputation.SupabaseRideReputationGateway
import com.elysium369.meet.ride.safety.RideSafetyGateway
import com.elysium369.meet.ride.safety.SupabaseRideSafetyGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RideModule {

    @Binds
    @Singleton
    abstract fun bindRidePresenceGateway(
        impl: SupabaseRidePresenceGateway
    ): RidePresenceGateway

    @Binds
    @Singleton
    abstract fun bindRideExposureGateway(
        impl: SupabaseRideExposureGateway
    ): RideExposureGateway

    @Binds
    @Singleton
    abstract fun bindRideReputationGateway(
        impl: SupabaseRideReputationGateway
    ): RideReputationGateway

    @Binds
    @Singleton
    abstract fun bindRideAutoMatchGateway(
        impl: SupabaseRideAutoMatchGateway
    ): RideAutoMatchGateway

    @Binds
    @Singleton
    abstract fun bindRideEtaGateway(
        impl: SupabaseRideEtaGateway
    ): RideEtaGateway

    @Binds
    @Singleton
    abstract fun bindRideNextJobGateway(
        impl: SupabaseRideNextJobGateway
    ): RideNextJobGateway

    @Binds
    @Singleton
    abstract fun bindRideDemandGateway(
        impl: SupabaseRideDemandGateway
    ): RideDemandGateway

    @Binds
    @Singleton
    abstract fun bindRidePaymentGateway(
        impl: SupabaseRidePaymentGateway
    ): RidePaymentGateway

    @Binds
    @Singleton
    abstract fun bindRideSafetyGateway(
        impl: SupabaseRideSafetyGateway
    ): RideSafetyGateway
}
