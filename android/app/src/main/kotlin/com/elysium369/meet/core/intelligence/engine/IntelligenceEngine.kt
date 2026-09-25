package com.elysium369.meet.core.intelligence.engine

import com.elysium369.meet.core.finance.Money
import com.elysium369.meet.core.identity.AnalyticsScope
import com.elysium369.meet.core.identity.ScopeType
import com.elysium369.meet.core.intelligence.metrics.MetricsRegistry
import com.elysium369.meet.core.owner.domain.DataFreshness
import com.elysium369.meet.core.owner.domain.OwnerCommandCenterSnapshot
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * ══════════════════════════════════════════════════════════════════════
 *  M E E T   I N T E L L I G E N C E   E N G I N E
 *  ──────────────────────────────────────────────────────────────
 *  Cross-cutting operational and economic intelligence platform.
 *  Answers: "¿Qué está ocurriendo económica y operacionalmente?"
 *
 *  - Enforces AnalyticsScope multi-tenant boundaries.
 *  - Surfaces actionable operational anomalies, not dead statistics.
 *  - Provides bidirectional deep linking into Trust Center.
 * ══════════════════════════════════════════════════════════════════════
 */

@Serializable
enum class AnomalyLevel {
    INFO,
    WARNING,
    HIGH,
    CRITICAL,
}

@Serializable
data class OperationalAnomaly(
    val id: String = UUID.randomUUID().toString(),
    val level: AnomalyLevel,
    val domain: String,
    val title: String,
    val message: String,
    val metricKey: String,
    val baselineValue: Double,
    val currentValue: Double,
    val occurredAtEpochMs: Long = System.currentTimeMillis(),
    val targetScopeId: String? = null,
    val actionDeepLink: String? = null,
)

@Serializable
data class ExecutiveIntelligenceProjection(
    val totalGmv: Money,
    val platformRevenue: Money,
    val netRevenue: Money,
    val activeUsers: Int,
    val activeProviders: Int,
    val completedTrips: Int,
    val completedServices: Int,
    val completedTowCalls: Int,
    val anomalies: List<OperationalAnomaly>,
    val trustAlertCount: Int,
    val expiringDocumentsCount: Int,
    val asOfEpochMs: Long = System.currentTimeMillis(),
    val freshnessSeconds: Int = 15,
    val freshness: DataFreshness = DataFreshness.LIVE,
)

@Serializable
data class DriverIntelligenceProjection(
    val todayGrossEarnings: Money,
    val todayNetEarnings: Money,
    val payoutAvailable: Money,
    val todayTripsCount: Int,
    val totalKm: Double,
    val paidKm: Double,
    val deadheadRatio: Double,
    val onlineHours: Double,
    val occupiedHours: Double,
    val utilizationPercent: Double,
    val revenuePerHour: Money,
    val revenuePerKm: Money,
    val trustAlertsCount: Int,
    val asOfEpochMs: Long = System.currentTimeMillis(),
)

@Serializable
data class FleetIntelligenceProjection(
    val fleetId: String,
    val fleetName: String,
    val totalVehicles: Int,
    val activeVehicles: Int,
    val onlineDrivers: Int,
    val tripsToday: Int,
    val fleetGmv: Money,
    val fleetEarnings: Money,
    val driverEarnings: Money,
    val fleetUtilizationPercent: Double,
    val vehiclesRequiringMaintenance: Int,
    val driversExpiringDocuments: Int,
    val asOfEpochMs: Long = System.currentTimeMillis(),
)

@Serializable
data class PassengerActivityProjection(
    val passengerId: String,
    val completedTripsCount: Int,
    val totalDistanceKm: Double,
    val totalHoursTraveling: Double,
    val totalSpent: Money,
    val averagePerTrip: Money,
    val averagePerKm: Money,
    val totalTipsGiven: Money,
    val totalDiscountsReceived: Money,
    val totalRefundsReceived: Money,
    val estimatedCo2Kg: Double,
    val citiesVisitedCount: Int,
    val asOfEpochMs: Long = System.currentTimeMillis(),
)

@Serializable
data class MechanicBusinessProjection(
    val mechanicId: String,
    val mechanicName: String,
    val jobsReceivedCount: Int,
    val jobsAcceptedCount: Int,
    val jobsCompletedCount: Int,
    val totalRevenue: Money,
    val laborRevenue: Money,
    val partsRevenue: Money,
    val averageTicket: Money,
    val billedHours: Double,
    val revenuePerHour: Money,
    val customerRating: Double,
    val repeatCustomersPercent: Double,
    val cancellationRatePercent: Double,
    val rework30dRatePercent: Double,
    val trustAlertsCount: Int,
    val asOfEpochMs: Long = System.currentTimeMillis(),
)

@Serializable
data class WorkshopCommandCenterProjection(
    val organizationId: String,
    val workshopName: String,
    val totalBays: Int,
    val occupiedBays: Int,
    val bayUtilizationPercent: Double,
    val activeMechanicsCount: Int,
    val activeJobsCount: Int,
    val backlogJobsCount: Int,
    val todayRevenue: Money,
    val laborRevenue: Money,
    val partsRevenue: Money,
    val averageTicket: Money,
    val averageRepairHours: Double,
    val quoteAcceptedConversionPercent: Double,
    val repeatCustomersPercent: Double,
    val rework30dRatePercent: Double,
    val expiringCertificationsCount: Int,
    val asOfEpochMs: Long = System.currentTimeMillis(),
)

@Serializable
data class TowCommandCenterProjection(
    val operatorOrFleetId: String,
    val name: String,
    val isOrganization: Boolean,
    val availableTrucksCount: Int,
    val activeCallsCount: Int,
    val completedCallsCount: Int,
    val totalGmv: Money,
    val operatorEarnings: Money,
    val totalDistanceKm: Double,
    val loadedDistanceKm: Double,
    val loadedRatio: Double,
    val revenuePerKm: Money,
    val revenuePerHour: Money,
    val averageResponseEtaMinutes: Double,
    val acceptanceRatePercent: Double,
    val customerRating: Double,
    val trustAlertsCount: Int,
    val asOfEpochMs: Long = System.currentTimeMillis(),
)

class IntelligenceEngine {

    /**
     * Projects executive analytics for authorized platform administrators.
     * Enforces that callers hold executive platform capabilities.
     *
     * In accordance with ELYSIUM MASTER ORDER OMEGA §45, §48:
     * ZERO synthetic business truth in production.
     * When no authoritative snapshot is provided, returns honest UNAVAILABLE / zero state.
     * When an authoritative snapshot is provided, executes pure derived analytics.
     */
    fun projectExecutive(
        scope: AnalyticsScope,
        authoritativeSnapshot: OwnerCommandCenterSnapshot? = null,
    ): ExecutiveIntelligenceProjection {
        require(scope.canAccess(targetOrgId = null, targetSubjectId = null)) {
            "Unauthorized access to executive command intelligence"
        }

        if (authoritativeSnapshot == null) {
            return ExecutiveIntelligenceProjection(
                totalGmv = Money.zero(),
                platformRevenue = Money.zero(),
                netRevenue = Money.zero(),
                activeUsers = 0,
                activeProviders = 0,
                completedTrips = 0,
                completedServices = 0,
                completedTowCalls = 0,
                anomalies = emptyList(),
                trustAlertCount = 0,
                expiringDocumentsCount = 0,
                freshnessSeconds = 0,
                freshness = DataFreshness.UNAVAILABLE,
            )
        }

        val anomalies = mutableListOf<OperationalAnomaly>()
        if (authoritativeSnapshot.systemHealth.deadLetterCount > 0) {
            anomalies.add(
                OperationalAnomaly(
                    level = AnomalyLevel.CRITICAL,
                    domain = "System",
                    title = "Eventos en Dead Letter Queue",
                    message = "${authoritativeSnapshot.systemHealth.deadLetterCount} eventos sin procesar requieren atención del operador.",
                    metricKey = "system.dlq_count",
                    baselineValue = 0.0,
                    currentValue = authoritativeSnapshot.systemHealth.deadLetterCount.toDouble(),
                )
            )
        }
        if (authoritativeSnapshot.systemHealth.outboxLagSeconds > 60) {
            anomalies.add(
                OperationalAnomaly(
                    level = AnomalyLevel.WARNING,
                    domain = "Outbox",
                    title = "Retraso en el Outbox Transaccional",
                    message = "El retardo de publicación es de ${authoritativeSnapshot.systemHealth.outboxLagSeconds}s.",
                    metricKey = "system.outbox_lag",
                    baselineValue = 5.0,
                    currentValue = authoritativeSnapshot.systemHealth.outboxLagSeconds.toDouble(),
                )
            )
        }

        return ExecutiveIntelligenceProjection(
            totalGmv = authoritativeSnapshot.money.totalGmv,
            platformRevenue = authoritativeSnapshot.money.platformRevenue,
            netRevenue = authoritativeSnapshot.money.netRevenue,
            activeUsers = authoritativeSnapshot.mobility.activeUsers,
            activeProviders = authoritativeSnapshot.mobility.activeProviders,
            completedTrips = authoritativeSnapshot.mobility.completedTrips,
            completedServices = authoritativeSnapshot.servicesCompleted,
            completedTowCalls = authoritativeSnapshot.towCallsCompleted,
            anomalies = anomalies,
            trustAlertCount = authoritativeSnapshot.trust.alertCount,
            expiringDocumentsCount = authoritativeSnapshot.trust.expiringDocumentsCount,
            asOfEpochMs = authoritativeSnapshot.asOfEpochMs,
            freshness = authoritativeSnapshot.freshness,
        )
    }

    /**
     * Projects driver analytics for an authorized driver.
     */
    fun projectDriver(
        scope: AnalyticsScope,
        driverId: String,
        grossUnits: Long = 0L,
        netUnits: Long = 0L,
        payoutAvailableUnits: Long = 0L,
        trips: Int = 0,
        totalKm: Double = 0.0,
        paidKm: Double = 0.0,
        onlineHours: Double = 0.0,
        occupiedHours: Double = 0.0,
        trustAlertsCount: Int = 0,
    ): DriverIntelligenceProjection {
        require(scope.canAccess(targetOrgId = null, targetSubjectId = driverId)) {
            "Unauthorized access to driver analytics for subject $driverId"
        }

        val utilization = MetricsRegistry.calculateUtilization(occupiedHours, onlineHours)
        val deadhead = MetricsRegistry.calculateDeadheadRatio(
            unpaidDistanceKm = (totalKm - paidKm).coerceAtLeast(0.0),
            totalDistanceKm = totalKm,
        )

        val revPerHour = if (onlineHours > 0.0) {
            Money.ofCrc((netUnits / onlineHours).toLong())
        } else {
            Money.zero()
        }

        val revPerKm = if (paidKm > 0.0) {
            Money.ofCrc((netUnits / paidKm).toLong())
        } else {
            Money.zero()
        }

        return DriverIntelligenceProjection(
            todayGrossEarnings = Money.ofCrc(grossUnits),
            todayNetEarnings = Money.ofCrc(netUnits),
            payoutAvailable = Money.ofCrc(payoutAvailableUnits),
            todayTripsCount = trips,
            totalKm = totalKm,
            paidKm = paidKm,
            deadheadRatio = deadhead,
            onlineHours = onlineHours,
            occupiedHours = occupiedHours,
            utilizationPercent = utilization,
            revenuePerHour = revPerHour,
            revenuePerKm = revPerKm,
            trustAlertsCount = trustAlertsCount,
        )
    }

    /**
     * Projects B2B fleet analytics strictly scoped to the authorized organization.
     */
    fun projectFleet(
        scope: AnalyticsScope,
        targetFleetId: String,
        fleetName: String = "Flota",
        totalVehicles: Int = 0,
        activeVehicles: Int = 0,
        onlineDrivers: Int = 0,
        tripsToday: Int = 0,
        fleetGmvUnits: Long = 0L,
        fleetEarningsUnits: Long = 0L,
        driverEarningsUnits: Long = 0L,
        fleetUtilizationPercent: Double = 0.0,
        vehiclesRequiringMaintenance: Int = 0,
        driversExpiringDocuments: Int = 0,
    ): FleetIntelligenceProjection {
        require(scope.canAccess(targetOrgId = targetFleetId, targetSubjectId = null)) {
            "Tenant isolation violation: Cannot access analytics for organization $targetFleetId"
        }

        return FleetIntelligenceProjection(
            fleetId = targetFleetId,
            fleetName = fleetName,
            totalVehicles = totalVehicles,
            activeVehicles = activeVehicles,
            onlineDrivers = onlineDrivers,
            tripsToday = tripsToday,
            fleetGmv = Money.ofCrc(fleetGmvUnits),
            fleetEarnings = Money.ofCrc(fleetEarningsUnits),
            driverEarnings = Money.ofCrc(driverEarningsUnits),
            fleetUtilizationPercent = fleetUtilizationPercent,
            vehiclesRequiringMaintenance = vehiclesRequiringMaintenance,
            driversExpiringDocuments = driversExpiringDocuments,
        )
    }

    /**
     * Projects personal activity metrics for a passenger ("Mi Actividad").
     */
    fun projectPassenger(
        scope: AnalyticsScope,
        passengerId: String,
        tripsCount: Int = 0,
        totalKm: Double = 0.0,
        totalHours: Double = 0.0,
        spentUnits: Long = 0L,
        tipsUnits: Long = 0L,
        discountsUnits: Long = 0L,
        refundsUnits: Long = 0L,
        citiesVisitedCount: Int = 0,
    ): PassengerActivityProjection {
        require(scope.canAccess(targetOrgId = null, targetSubjectId = passengerId)) {
            "Unauthorized access to passenger activity for subject $passengerId"
        }

        val avgPerTrip = if (tripsCount > 0) Money.ofCrc(spentUnits / tripsCount) else Money.zero()
        val avgPerKm = if (totalKm > 0.0) Money.ofCrc((spentUnits / totalKm).toLong()) else Money.zero()
        val estimatedCo2 = totalKm * 0.120 // ~120g CO2 per km average vehicle

        return PassengerActivityProjection(
            passengerId = passengerId,
            completedTripsCount = tripsCount,
            totalDistanceKm = totalKm,
            totalHoursTraveling = totalHours,
            totalSpent = Money.ofCrc(spentUnits),
            averagePerTrip = avgPerTrip,
            averagePerKm = avgPerKm,
            totalTipsGiven = Money.ofCrc(tipsUnits),
            totalDiscountsReceived = Money.ofCrc(discountsUnits),
            totalRefundsReceived = Money.ofCrc(refundsUnits),
            estimatedCo2Kg = estimatedCo2,
            citiesVisitedCount = citiesVisitedCount,
        )
    }

    /**
     * Projects individual mechanic business metrics.
     */
    fun projectMechanic(
        scope: AnalyticsScope,
        mechanicId: String,
        mechanicName: String = "Técnico Especialista",
        jobsCompleted: Int = 0,
        jobsReceivedCount: Int = 0,
        jobsAcceptedCount: Int = 0,
        laborUnits: Long = 0L,
        partsUnits: Long = 0L,
        billedHours: Double = 0.0,
        reworkJobs: Int = 0,
        customerRating: Double = 0.0,
        repeatCustomersPercent: Double = 0.0,
        cancellationRatePercent: Double = 0.0,
        trustAlertsCount: Int = 0,
    ): MechanicBusinessProjection {
        require(scope.canAccess(targetOrgId = null, targetSubjectId = mechanicId)) {
            "Unauthorized access to mechanic analytics for subject $mechanicId"
        }

        val totalUnits = laborUnits + partsUnits
        val avgTicket = if (jobsCompleted > 0) Money.ofCrc(totalUnits / jobsCompleted) else Money.zero()
        val revPerHour = if (billedHours > 0.0) Money.ofCrc((laborUnits / billedHours).toLong()) else Money.zero()
        val reworkRate = MetricsRegistry.calculateReworkRate(reworkJobs, jobsCompleted)

        return MechanicBusinessProjection(
            mechanicId = mechanicId,
            mechanicName = mechanicName,
            jobsReceivedCount = if (jobsReceivedCount > 0) jobsReceivedCount else jobsCompleted,
            jobsAcceptedCount = if (jobsAcceptedCount > 0) jobsAcceptedCount else jobsCompleted,
            jobsCompletedCount = jobsCompleted,
            totalRevenue = Money.ofCrc(totalUnits),
            laborRevenue = Money.ofCrc(laborUnits),
            partsRevenue = Money.ofCrc(partsUnits),
            averageTicket = avgTicket,
            billedHours = billedHours,
            revenuePerHour = revPerHour,
            customerRating = customerRating,
            repeatCustomersPercent = repeatCustomersPercent,
            cancellationRatePercent = cancellationRatePercent,
            rework30dRatePercent = reworkRate,
            trustAlertsCount = trustAlertsCount,
        )
    }

    /**
     * Projects B2B workshop command center analytics.
     */
    fun projectWorkshop(
        scope: AnalyticsScope,
        workshopOrgId: String,
        workshopName: String = "Taller",
        totalBays: Int = 0,
        occupiedBays: Int = 0,
        activeMechanicsCount: Int = 0,
        activeJobs: Int = 0,
        backlogJobs: Int = 0,
        todayLaborUnits: Long = 0L,
        todayPartsUnits: Long = 0L,
        reworkJobsMonth: Int = 0,
        totalJobsMonth: Int = 0,
        averageRepairHours: Double = 0.0,
        quoteAcceptedConversionPercent: Double = 0.0,
        repeatCustomersPercent: Double = 0.0,
        expiringCertificationsCount: Int = 0,
    ): WorkshopCommandCenterProjection {
        require(scope.canAccess(targetOrgId = workshopOrgId, targetSubjectId = null)) {
            "Tenant isolation violation: Cannot access workshop analytics for organization $workshopOrgId"
        }

        val bayUtil = if (totalBays > 0) (occupiedBays.toDouble() / totalBays.toDouble()) * 100.0 else 0.0
        val totalRevenueUnits = todayLaborUnits + todayPartsUnits
        val avgTicket = if (totalJobsMonth > 0) Money.ofCrc(totalRevenueUnits / totalJobsMonth) else Money.zero()
        val reworkRate = MetricsRegistry.calculateReworkRate(reworkJobsMonth, totalJobsMonth)

        return WorkshopCommandCenterProjection(
            organizationId = workshopOrgId,
            workshopName = workshopName,
            totalBays = totalBays,
            occupiedBays = occupiedBays,
            bayUtilizationPercent = bayUtil,
            activeMechanicsCount = activeMechanicsCount,
            activeJobsCount = activeJobs,
            backlogJobsCount = backlogJobs,
            todayRevenue = Money.ofCrc(totalRevenueUnits),
            laborRevenue = Money.ofCrc(todayLaborUnits),
            partsRevenue = Money.ofCrc(todayPartsUnits),
            averageTicket = avgTicket,
            averageRepairHours = averageRepairHours,
            quoteAcceptedConversionPercent = quoteAcceptedConversionPercent,
            repeatCustomersPercent = repeatCustomersPercent,
            rework30dRatePercent = reworkRate,
            expiringCertificationsCount = expiringCertificationsCount,
        )
    }

    /**
     * Projects tow operator or tow fleet command center analytics.
     */
    fun projectTow(
        scope: AnalyticsScope,
        operatorOrOrgId: String,
        isOrganization: Boolean = false,
        name: String = "Grúas y Asistencia",
        availableTrucks: Int = 0,
        activeCalls: Int = 0,
        completedCalls: Int = 0,
        gmvUnits: Long = 0L,
        earningsUnits: Long = 0L,
        totalKm: Double = 0.0,
        loadedKm: Double = 0.0,
        revenuePerHourUnits: Long = 0L,
        averageResponseEtaMinutes: Double = 0.0,
        acceptanceRatePercent: Double = 0.0,
        customerRating: Double = 0.0,
        trustAlertsCount: Int = 0,
    ): TowCommandCenterProjection {
        val targetOrgId = if (isOrganization) operatorOrOrgId else null
        val targetSubjectId = if (!isOrganization) operatorOrOrgId else null
        require(scope.canAccess(targetOrgId = targetOrgId, targetSubjectId = targetSubjectId)) {
            "Tenant isolation violation: Cannot access tow analytics for $operatorOrOrgId"
        }

        val loadedRatio = MetricsRegistry.calculateLoadedRatio(loadedKm, totalKm)
        val revPerKm = if (loadedKm > 0.0) Money.ofCrc((earningsUnits / loadedKm).toLong()) else Money.zero()

        return TowCommandCenterProjection(
            operatorOrFleetId = operatorOrOrgId,
            name = name,
            isOrganization = isOrganization,
            availableTrucksCount = availableTrucks,
            activeCallsCount = activeCalls,
            completedCallsCount = completedCalls,
            totalGmv = Money.ofCrc(gmvUnits),
            operatorEarnings = Money.ofCrc(earningsUnits),
            totalDistanceKm = totalKm,
            loadedDistanceKm = loadedKm,
            loadedRatio = loadedRatio,
            revenuePerKm = revPerKm,
            revenuePerHour = Money.ofCrc(revenuePerHourUnits),
            averageResponseEtaMinutes = averageResponseEtaMinutes,
            acceptanceRatePercent = acceptanceRatePercent,
            customerRating = customerRating,
            trustAlertsCount = trustAlertsCount,
        )
    }
}

