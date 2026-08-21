package com.elysium369.meet.di

import com.elysium369.meet.core.diagnostics.DiagnosticReasoningEngine
import com.elysium369.meet.core.dna.MeetDnaEngine
import com.elysium369.meet.core.evair.agent.AntigravityGateway
import com.elysium369.meet.core.evair.agent.AutomotiveAgentGateway
import com.elysium369.meet.core.evair.baseline.VehicleBaselineEngine
import com.elysium369.meet.core.evair.bridge.DefaultVehicleToolFacade
import com.elysium369.meet.core.evair.bridge.VehicleRuntimeServer
import com.elysium369.meet.core.evair.bridge.VehicleToolFacade
import com.elysium369.meet.core.evair.memory.VehicleMemoryRepository
import com.elysium369.meet.core.evair.prediction.LongitudinalHealthPredictor
import com.elysium369.meet.core.evair.safety.VehicleActionExecutor
import com.elysium369.meet.core.evair.safety.VehicleSafetyBroker
import com.elysium369.meet.core.evair.state.VehicleStateEngine
import com.elysium369.meet.core.evair.telemetry.AnomalyDetector
import com.elysium369.meet.core.evair.telemetry.TelemetryCollector
import com.elysium369.meet.core.evair.vision.ComponentVisionEngine
import com.elysium369.meet.core.evair.voice.VoiceMechanicOrchestrator
import com.elysium369.meet.core.health.PredictiveHealthEngine
import com.elysium369.meet.core.obd.ObdSession
import com.elysium369.meet.core.twin.VehicleTwinEngine
import com.elysium369.meet.data.local.dao.HealthSnapshotDao
import com.elysium369.meet.data.local.dao.PredictionEventDao
import com.elysium369.meet.data.local.dao.SensorHistoryDao
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

    @Provides
    @Singleton
    fun provideVehicleToolFacade(
        obdSession: ObdSession,
        vehicleStateEngine: VehicleStateEngine,
        baselineEngine: VehicleBaselineEngine,
        anomalyDetector: AnomalyDetector,
        healthEngine: PredictiveHealthEngine,
    ): VehicleToolFacade {
        return DefaultVehicleToolFacade(
            obdSession = obdSession,
            vehicleStateEngine = vehicleStateEngine,
            baselineEngine = baselineEngine,
            anomalyDetector = anomalyDetector,
            healthEngine = healthEngine
        )
    }

    @Provides
    @Singleton
    fun provideVehicleRuntimeServer(
        facade: VehicleToolFacade,
    ): VehicleRuntimeServer {
        return VehicleRuntimeServer(facade)
    }

    @Provides
    @Singleton
    fun provideAutomotiveAgentGateway(
        deterministicEngine: DiagnosticReasoningEngine,
    ): AutomotiveAgentGateway {
        return AntigravityGateway(deterministicEngine)
    }

    @Provides
    @Singleton
    fun provideVehicleMemoryRepository(
        healthSnapshotDao: HealthSnapshotDao,
        sensorHistoryDao: SensorHistoryDao,
        predictionEventDao: PredictionEventDao,
        dnaDao: VehicleDnaDao,
        twinDao: VehicleTwinDao,
    ): VehicleMemoryRepository {
        return VehicleMemoryRepository(
            healthSnapshotDao,
            sensorHistoryDao,
            predictionEventDao,
            dnaDao,
            twinDao
        )
    }

    @Provides
    @Singleton
    fun provideVoiceMechanicOrchestrator(
        facade: VehicleToolFacade,
        gateway: AutomotiveAgentGateway,
    ): VoiceMechanicOrchestrator {
        return VoiceMechanicOrchestrator(facade, gateway)
    }

    @Provides
    @Singleton
    fun provideComponentVisionEngine(): ComponentVisionEngine {
        return ComponentVisionEngine()
    }

    @Provides
    @Singleton
    fun provideLongitudinalHealthPredictor(
        sensorHistoryDao: SensorHistoryDao,
    ): LongitudinalHealthPredictor {
        return LongitudinalHealthPredictor(sensorHistoryDao)
    }

    @Provides
    @Singleton
    fun provideVehicleActionExecutor(
        safetyBroker: VehicleSafetyBroker,
        obdSession: ObdSession,
    ): VehicleActionExecutor {
        return VehicleActionExecutor(safetyBroker, obdSession)
    }
}
