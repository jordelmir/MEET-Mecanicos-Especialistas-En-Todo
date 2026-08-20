package com.elysium369.meet.vehiclelife.passport

import com.elysium369.meet.core.domain.VehicleContext
import com.elysium369.meet.core.money.CurrencyCode
import com.elysium369.meet.core.money.Money
import com.elysium369.meet.vehiclelife.timeline.TimelineCategoryFilter
import com.elysium369.meet.vehiclelife.timeline.VehicleTimelineRepository
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

interface VehiclePassportRepository {
    suspend fun buildPassport(
        vehicleContext: VehicleContext,
        healthScore: Int,
        activeDtcsCount: Int
    ): VehiclePassportProjection
}

@Singleton
class DefaultVehiclePassportRepository @Inject constructor(
    private val timelineRepository: VehicleTimelineRepository
) : VehiclePassportRepository {

    override suspend fun buildPassport(
        vehicleContext: VehicleContext,
        healthScore: Int,
        activeDtcsCount: Int
    ): VehiclePassportProjection {
        val allEvents = timelineRepository.getEventsForVehicle(vehicleContext.vehicleId, TimelineCategoryFilter.ALL)
        val verifiedRepairs = allEvents.count { it.type == com.elysium369.meet.core.vehiclelife.VehicleLifeEventType.REPAIR && it.isVerified }
        val certifiedInspections = allEvents.count { it.type == com.elysium369.meet.core.vehiclelife.VehicleLifeEventType.INSPECTION && it.isVerified }

        val identitySection = PassportIdentitySection(
            vehicleContext = vehicleContext,
            plate = "NOT_SET",
            registrationDateUtc = null,
            isTitleVerified = true
        )

        val healthSection = PassportHealthSection(
            overallScorePercent = healthScore,
            activeDtcsCount = activeDtcsCount,
            subsystemAssessments = emptyList()
        )

        val historySection = PassportHistorySection(
            totalRecordedEvents = allEvents.size,
            verifiedRepairsCount = verifiedRepairs,
            certifiedInspectionsCount = certifiedInspections,
            recentMilestones = allEvents.take(10)
        )

        val financialSection = PassportFinancialSummary(
            totalInvested = Money.zero(CurrencyCode.USD),
            recordedInvoicesCount = allEvents.count { it.type == com.elysium369.meet.core.vehiclelife.VehicleLifeEventType.COST }
        )

        val rawPayload = "${vehicleContext.vehicleId}:${vehicleContext.vin}:${allEvents.size}:$healthScore"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(rawPayload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val integritySection = PassportIntegritySection(
            passportHashSha256 = hash,
            generatedAtUtc = System.currentTimeMillis(),
            verifierUrl = "https://meet.elysium369.com/verify/passport/$hash"
        )

        return VehiclePassportProjection(
            vehicleId = vehicleContext.vehicleId,
            identity = identitySection,
            health = healthSection,
            history = historySection,
            financial = financialSection,
            integrity = integritySection
        )
    }
}
