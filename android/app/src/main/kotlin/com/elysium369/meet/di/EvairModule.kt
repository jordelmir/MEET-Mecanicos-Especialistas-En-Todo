package com.elysium369.meet.di

import com.elysium369.meet.core.dna.MeetDnaEngine
import com.elysium369.meet.core.evair.baseline.VehicleBaselineEngine
import com.elysium369.meet.core.evair.safety.VehicleSafetyBroker
import com.elysium369.meet.core.evair.state.VehicleStateEngine
import com.elysium369.meet.core.evair.telemetry.AnomalyDetector
import com.elysium369.meet.core.evair.telemetry.TelemetryCollector
import com.elysium369.meet.core.obd.ObdSession
import com.elysium369.meet.core.twin.VehicleTwinEngine
import com.elysium369.meet.data.local.dao.VehicleDnaDao
import com.elysium369.meet.data.local.dao.VehicleTwinDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EvairModule {

    @Provides
    @Singleton
    fun provideTelemetryCollector(
        obdSession: ObdSession,
    ): TelemetryCollector {
        return TelemetryCollector(obdSession)
    }

    @Provides
    @Singleton
    fun provideAnomalyDetector(
        vehicleTwinEngine: VehicleTwinEngine,
    ): AnomalyDetector {
        return AnomalyDetector(vehicleTwinEngine)
    }

    @Provides
    @Singleton
    fun provideVehicleBaselineEngine(
        dnaEngine: MeetDnaEngine,
        twinEngine: VehicleTwinEngine,
        dnaDao: VehicleDnaDao,
        twinDao: VehicleTwinDao,
    ): VehicleBaselineEngine {
        return VehicleBaselineEngine(dnaEngine, twinEngine, dnaDao, twinDao)
    }

    @Provides
    @Singleton
    fun provideVehicleSafetyBroker(
        obdSession: ObdSession,
    ): VehicleSafetyBroker {
        return VehicleSafetyBroker(obdSession)
    }

    @Provides
    @Singleton
    fun provideVehicleStateEngine(
        obdSession: ObdSession,
        telemetryCollector: TelemetryCollector,
        anomalyDetector: AnomalyDetector,
    ): VehicleStateEngine {
        return VehicleStateEngine(obdSession, telemetryCollector, anomalyDetector)
    }
}
