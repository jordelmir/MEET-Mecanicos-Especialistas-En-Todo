package com.elysium369.meet.vehiclelife.passport

import com.elysium369.meet.core.domain.VehicleContext
import com.elysium369.meet.core.money.Money
import com.elysium369.meet.core.subsystem.SubsystemAssessment
import com.elysium369.meet.core.vehiclelife.VehicleLifeEvent

data class PassportIdentitySection(
    val vehicleContext: VehicleContext,
    val plate: String,
    val registrationDateUtc: Long?,
    val isTitleVerified: Boolean
)

data class PassportHealthSection(
    val overallScorePercent: Int,
    val activeDtcsCount: Int,
    val subsystemAssessments: List<SubsystemAssessment>
)

data class PassportHistorySection(
    val totalRecordedEvents: Int,
    val verifiedRepairsCount: Int,
    val certifiedInspectionsCount: Int,
    val recentMilestones: List<VehicleLifeEvent>
)

data class PassportFinancialSummary(
    val totalInvested: Money?,
    val recordedInvoicesCount: Int
)

data class PassportIntegritySection(
    val passportHashSha256: String,
    val generatedAtUtc: Long,
    val verifierUrl: String
)

/**
 * MEET Vehicle Life OS — Consolidated Vehicle Passport Projection.
 * Aggregates all domain facts into an exportable, forensic-grade digital credential.
 */
data class VehiclePassportProjection(
    val vehicleId: String,
    val identity: PassportIdentitySection,
    val health: PassportHealthSection,
    val history: PassportHistorySection,
    val financial: PassportFinancialSummary,
    val integrity: PassportIntegritySection
)
