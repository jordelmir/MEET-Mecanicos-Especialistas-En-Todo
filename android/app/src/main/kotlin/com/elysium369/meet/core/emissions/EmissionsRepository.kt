package com.elysium369.meet.core.emissions

import com.elysium369.meet.core.emissions.preitv.PreItvResult
import com.elysium369.meet.core.emissions.storage.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmissionsRepository @Inject constructor(
    private val emissionDao: EmissionDao
) {

    fun getAllSessions(): Flow<List<EmissionSessionEntity>> {
        return emissionDao.getAllSessions()
    }

    suspend fun savePreItvResult(
        result: PreItvResult,
        vehicleId: String?,
        vin: String?,
        protocol: String?
    ) {
        val sessionEntity = EmissionSessionEntity(
            id = result.id,
            vehicleId = vehicleId,
            vin = vin,
            startedAt = result.completedAtMs - 60_000L,
            completedAt = result.completedAtMs,
            protocol = protocol,
            ruleSetId = result.ruleVersion,
            appVersion = "4.26.2",
            modelVersion = "VANGUARD_VIRTUAL_GAS_1.0",
            overallVerdict = result.overall.name,
            confidence = result.confidence
        )
        emissionDao.insertSession(sessionEntity)

        val idlePhase = EmissionPhaseResultEntity(
            id = "${result.id}_IDLE",
            sessionId = result.id,
            phaseName = result.idle.phaseName,
            rpmMean = result.idle.rpmMean,
            ectMean = result.idle.ectMean,
            coPoint = result.idle.coEstimate.pointEstimate,
            coLower95 = result.idle.coEstimate.lower95,
            coUpper95 = result.idle.coEstimate.upper95,
            coEval = result.idle.coEvaluation.name,
            hcPoint = result.idle.hcEstimate.pointEstimate,
            hcLower95 = result.idle.hcEstimate.lower95,
            hcUpper95 = result.idle.hcEstimate.upper95,
            hcEval = result.idle.hcEvaluation.name,
            co2Point = result.idle.co2Estimate.pointEstimate,
            co2Eval = result.idle.co2Evaluation.name,
            lambdaVal = result.idle.lambdaEstimate,
            lambdaEval = result.idle.lambdaEvaluation.name
        )

        val accelPhase = EmissionPhaseResultEntity(
            id = "${result.id}_ACCEL",
            sessionId = result.id,
            phaseName = result.accelerated.phaseName,
            rpmMean = result.accelerated.rpmMean,
            ectMean = result.accelerated.ectMean,
            coPoint = result.accelerated.coEstimate.pointEstimate,
            coLower95 = result.accelerated.coEstimate.lower95,
            coUpper95 = result.accelerated.coEstimate.upper95,
            coEval = result.accelerated.coEvaluation.name,
            hcPoint = result.accelerated.hcEstimate.pointEstimate,
            hcLower95 = result.accelerated.hcEstimate.lower95,
            hcUpper95 = result.accelerated.hcEstimate.upper95,
            hcEval = result.accelerated.hcEvaluation.name,
            co2Point = result.accelerated.co2Estimate.pointEstimate,
            co2Eval = result.accelerated.co2Evaluation.name,
            lambdaVal = result.accelerated.lambdaEstimate,
            lambdaEval = result.accelerated.lambdaEvaluation.name
        )

        emissionDao.insertPhaseResults(listOf(idlePhase, accelPhase))
    }
}
