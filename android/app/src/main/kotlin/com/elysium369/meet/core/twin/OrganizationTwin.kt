package com.elysium369.meet.core.twin

import com.elysium369.meet.core.services.kernel.ServiceVertical
import kotlinx.serialization.Serializable

/**
 * ASTRA V6 §9, §13, §45 — Organization Twin / Business Copilot.
 *
 * Digital twin of any economic entity: solo provider, taller, fleet,
 * franchise, micro-enterprise, school, cooperative. Enables:
 * - "Businesses that learn" (§13)
 * - Micro-enterprise copilot (§45)
 * - Fleet/shop operations management
 * - Demand sensing for provider territory
 */

// ─── Organization Types ───

enum class OrganizationType(val description: String) {
    SOLO_PROVIDER("Individuo ofreciendo servicios"),
    WORKSHOP("Taller o establecimiento"),
    FLEET("Flotilla de vehículos"),
    FRANCHISE("Franquicia o red de talleres"),
    MICRO_ENTERPRISE("Micro empresa (1-5 personas)"),
    SMB("Pequeña/mediana empresa"),
    COOPERATIVE("Cooperativa de trabajadores"),
    SCHOOL("Instituto educativo"),
    GOVERNMENT_ENTITY("Entidad gubernamental"),
}

enum class OrganizationState {
    DRAFT,
    ONBOARDING,
    ACTIVE,
    SUSPENDED,
    CLOSED,
}

// ─── Organization Twin (the digital representation) ───

@Serializable
data class OrganizationTwin(
    val id: String = java.util.UUID.randomUUID().toString(),
    val ownerId: String,
    val name: String,
    val type: OrganizationType,
    val state: OrganizationState = OrganizationState.DRAFT,
    val serviceVerticals: List<ServiceVertical> = emptyList(),
    val teamMemberIds: List<String> = emptyList(),
    val assets: List<OrganizationAsset> = emptyList(),
    val operatingZones: List<OperatingZone> = emptyList(),
    val metrics: OrganizationMetrics = OrganizationMetrics(),
    val createdAtEpochMs: Long = System.currentTimeMillis(),
) {
    val teamSize: Int get() = teamMemberIds.size + 1 // +1 for owner
    val isActive: Boolean get() = state == OrganizationState.ACTIVE
    val isMicroEnterprise: Boolean get() = teamSize <= 5
}

@Serializable
data class OrganizationAsset(
    val id: String,
    val type: AssetType,
    val description: String,
    val condition: AssetCondition = AssetCondition.OPERATIONAL,
    val linkedVehicleId: String? = null,
    val linkedPropertyId: String? = null,
)

enum class AssetType {
    VEHICLE, TOOL, EQUIPMENT, PROPERTY, INVENTORY, LICENSE, CERTIFICATION,
}

enum class AssetCondition {
    OPERATIONAL, NEEDS_MAINTENANCE, OUT_OF_SERVICE, RETIRED,
}

@Serializable
data class OperatingZone(
    val centerLat: Double,
    val centerLng: Double,
    val radiusKm: Double,
    val label: String,
    val isPrimary: Boolean = false,
)

// ─── Organization Metrics (business intelligence) ───

@Serializable
data class OrganizationMetrics(
    val totalJobsCompleted: Int = 0,
    val totalJobsThisMonth: Int = 0,
    val verifiedOutcomeRate: Double = 0.0,
    val repeatCustomerRate: Double = 0.0,
    val averageResponseTimeMinutes: Int = 0,
    val totalRevenue: Long = 0,
    val revenueThisMonth: Long = 0,
    val customerSatisfactionScore: Double = 0.0,
    val activeDisputes: Int = 0,
    val teamUtilizationRate: Double = 0.0,
) {
    val healthScore: Double
        get() {
            if (totalJobsCompleted == 0) return 0.0
            return ((verifiedOutcomeRate * 0.3) +
                (repeatCustomerRate * 0.2) +
                (customerSatisfactionScore * 0.3) +
                ((1.0 - (activeDisputes.toDouble() / totalJobsCompleted.coerceAtLeast(1))) * 0.2))
                .coerceIn(0.0, 1.0)
        }
}

// ─── Business Twin Engine (operations) ───

object BusinessTwinEngine {

    /**
     * Creates an OrganizationTwin for a solo provider upgrading to a micro-enterprise.
     * This is the §45 "micro-enterprise copilot" entry point.
     */
    fun createMicroEnterprise(
        ownerId: String,
        name: String,
        verticals: List<ServiceVertical>,
        operatingZone: OperatingZone,
    ): OrganizationTwin {
        require(name.isNotBlank()) { "Business name cannot be blank" }
        require(verticals.isNotEmpty()) { "At least one service vertical required" }

        return OrganizationTwin(
            id = java.util.UUID.randomUUID().toString(),
            ownerId = ownerId,
            name = name,
            type = OrganizationType.MICRO_ENTERPRISE,
            state = OrganizationState.ONBOARDING,
            serviceVerticals = verticals,
            operatingZones = listOf(operatingZone),
        )
    }

    /**
     * Adds a team member to the organization.
     * Returns updated twin. Validates team size for micro-enterprise constraint.
     */
    fun addTeamMember(
        twin: OrganizationTwin,
        memberId: String,
    ): OrganizationTwin {
        require(memberId != twin.ownerId) { "Owner is already implicit in team" }
        require(memberId !in twin.teamMemberIds) { "Member already in team" }

        return twin.copy(
            teamMemberIds = twin.teamMemberIds + memberId,
        )
    }

    /**
     * Records a completed job outcome into the organization metrics.
     */
    fun recordJobOutcome(
        twin: OrganizationTwin,
        isSuccessful: Boolean,
        isRepeatCustomer: Boolean,
        revenueAmount: Long,
        responseTimeMinutes: Int,
    ): OrganizationTwin {
        val m = twin.metrics
        val newTotal = m.totalJobsCompleted + 1
        val newMonthly = m.totalJobsThisMonth + 1
        val successCount = (m.verifiedOutcomeRate * m.totalJobsCompleted).toInt() +
            if (isSuccessful) 1 else 0
        val repeatCount = (m.repeatCustomerRate * m.totalJobsCompleted).toInt() +
            if (isRepeatCustomer) 1 else 0

        return twin.copy(
            metrics = m.copy(
                totalJobsCompleted = newTotal,
                totalJobsThisMonth = newMonthly,
                verifiedOutcomeRate = successCount.toDouble() / newTotal,
                repeatCustomerRate = repeatCount.toDouble() / newTotal,
                totalRevenue = m.totalRevenue + revenueAmount,
                revenueThisMonth = m.revenueThisMonth + revenueAmount,
                averageResponseTimeMinutes = ((m.averageResponseTimeMinutes.toLong() *
                    m.totalJobsCompleted + responseTimeMinutes) / newTotal).toInt(),
            ),
        )
    }
}
