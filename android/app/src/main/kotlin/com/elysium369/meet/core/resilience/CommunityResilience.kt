package com.elysium369.meet.core.resilience

import com.elysium369.meet.core.dignity.ExperienceCategory
import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  C O M M U N I T Y   R E S I L I E N C E   N E T W O R K
 *  ──────────────────────────────────────────────────────────
 *  When a hurricane hits, ELYSIUM transforms into a coordination
 *  platform for mutual aid:
 *
 *  - Who has generators?
 *  - Who can provide shelter?
 *  - Who has medical skills?
 *  - Who has transport?
 *  - Where is clean water?
 *  - Where are roads blocked?
 *
 *  This transcends commerce. This is civilization.
 *
 *  And it connects directly with DignityMapper — the skills we
 *  discovered for employment ALSO save lives in emergencies.
 *
 *  Modes:
 *  ┌───────────────────────────────────────────────────────┐
 *  │  NORMAL     — community directory, resource mapping   │
 *  │  ALERT      — weather warning, prepare resources      │
 *  │  CRISIS     — active disaster, full coordination      │
 *  │  RECOVERY   — post-disaster rebuilding, mutual aid    │
 *  └───────────────────────────────────────────────────────┘
 * ══════════════════════════════════════════════════════════════════════
 */

enum class CommunityMode {
    NORMAL,     // Everyday resource mapping
    ALERT,      // Warning issued, preparation phase
    CRISIS,     // Active disaster, all hands
    RECOVERY,   // Rebuilding phase
}

enum class ResourceType(val label: String, val emoji: String) {
    GENERATOR("Generador eléctrico", "⚡"),
    SHELTER("Refugio / vivienda", "🏠"),
    WATER("Agua potable", "💧"),
    FOOD("Alimentos", "🍽️"),
    MEDICAL("Atención médica", "🏥"),
    TRANSPORT("Transporte", "🚗"),
    COMMUNICATION("Comunicación (radio/satélite)", "📡"),
    TOOLS("Herramientas", "🔧"),
    FUEL("Combustible", "⛽"),
    CHILD_CARE("Cuidado de niños", "👶"),
    ELDER_CARE("Cuidado de adultos mayores", "👵"),
    CONSTRUCTION("Materiales de construcción", "🧱"),
    CLOTHING("Ropa y cobijas", "🧥"),
    PETS("Cuidado de mascotas", "🐾"),
}

@Serializable
data class CommunityResource(
    val resourceId: String,
    val ownerId: String,
    val ownerName: String,
    val type: ResourceType,
    val description: String,
    val quantity: Int = 1,
    val isAvailable: Boolean = true,
    val isFree: Boolean = true,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationDescription: String = "",
    val lastVerifiedEpochMs: Long = System.currentTimeMillis(),
)

@Serializable
data class CommunityVolunteer(
    val userId: String,
    val displayName: String,
    val skills: List<VolunteerSkill>,
    val isAvailable: Boolean = true,
    val canTravel: Boolean = true,
    val maxTravelKm: Double = 20.0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val languages: List<String> = listOf("es"),
)

@Serializable
data class VolunteerSkill(
    val name: String,
    val category: ExperienceCategory,
    val relevantDomain: UniversalServiceDomain,
    val isCertified: Boolean = false,
)

@Serializable
data class AidRequest(
    val requestId: String,
    val requesterId: String,
    val requesterName: String,
    val resourceType: ResourceType,
    val urgency: AidUrgency,
    val description: String,
    val peopleAffected: Int = 1,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val status: AidStatus = AidStatus.OPEN,
    val fulfilledByUserId: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

enum class AidUrgency(val label: String) {
    LIFE_THREATENING("Peligro de vida"),
    CRITICAL("Crítico — necesita hoy"),
    HIGH("Alto — necesita esta semana"),
    MODERATE("Moderado — puede esperar"),
}

enum class AidStatus {
    OPEN,           // Requesting help
    MATCHED,        // Someone volunteered
    IN_TRANSIT,     // Help on the way
    FULFILLED,      // Need was met
    CANCELLED,      // Request withdrawn
}

// ─── Resilience Network Engine ───

class CommunityResilienceNetwork {

    private var mode = CommunityMode.NORMAL
    private val resources = mutableListOf<CommunityResource>()
    private val volunteers = mutableListOf<CommunityVolunteer>()
    private val aidRequests = mutableListOf<AidRequest>()

    // ─── Mode Management ───

    fun activateMode(newMode: CommunityMode) {
        mode = newMode
    }

    fun currentMode(): CommunityMode = mode

    // ─── Resources ───

    fun registerResource(resource: CommunityResource) {
        resources.add(resource)
    }

    fun availableResources(type: ResourceType): List<CommunityResource> {
        return resources.filter { it.type == type && it.isAvailable }
    }

    fun allAvailableResources(): List<CommunityResource> {
        return resources.filter { it.isAvailable }
    }

    fun freeResources(): List<CommunityResource> {
        return resources.filter { it.isAvailable && it.isFree }
    }

    // ─── Volunteers ───

    fun registerVolunteer(volunteer: CommunityVolunteer) {
        volunteers.add(volunteer)
    }

    fun findVolunteers(category: ExperienceCategory): List<CommunityVolunteer> {
        return volunteers.filter { v ->
            v.isAvailable && v.skills.any { it.category == category }
        }
    }

    fun findVolunteersByDomain(domain: UniversalServiceDomain): List<CommunityVolunteer> {
        return volunteers.filter { v ->
            v.isAvailable && v.skills.any { it.relevantDomain == domain }
        }
    }

    // ─── Aid Requests ───

    fun requestAid(request: AidRequest): AidRequest {
        aidRequests.add(request)
        return request
    }

    fun fulfillAid(requestId: String, volunteerId: String): Boolean {
        val idx = aidRequests.indexOfFirst { it.requestId == requestId }
        if (idx < 0) return false

        aidRequests[idx] = aidRequests[idx].copy(
            status = AidStatus.FULFILLED,
            fulfilledByUserId = volunteerId,
        )
        return true
    }

    fun openRequests(): List<AidRequest> {
        return aidRequests
            .filter { it.status == AidStatus.OPEN }
            .sortedBy { it.urgency.ordinal } // Life-threatening first
    }

    fun openRequestsByUrgency(urgency: AidUrgency): List<AidRequest> {
        return openRequests().filter { it.urgency == urgency }
    }

    // ─── Matching ───

    /**
     * Matches aid requests with available resources and volunteers.
     * Returns matches sorted by urgency and proximity.
     */
    fun matchRequestToResources(requestId: String): List<CommunityResource> {
        val request = aidRequests.firstOrNull { it.requestId == requestId }
            ?: return emptyList()
        return availableResources(request.resourceType)
    }

    // ─── Crisis Dashboard ───

    fun crisisDashboard(): CrisisDashboard {
        return CrisisDashboard(
            mode = mode,
            totalResources = resources.count { it.isAvailable },
            totalVolunteers = volunteers.count { it.isAvailable },
            openRequests = aidRequests.count { it.status == AidStatus.OPEN },
            lifeThreatening = aidRequests.count {
                it.status == AidStatus.OPEN && it.urgency == AidUrgency.LIFE_THREATENING
            },
            fulfilledRequests = aidRequests.count { it.status == AidStatus.FULFILLED },
            totalPeopleAffected = aidRequests
                .filter { it.status == AidStatus.OPEN }
                .sumOf { it.peopleAffected },
            resourcesByType = ResourceType.entries.associateWith { type ->
                resources.count { it.type == type && it.isAvailable }
            },
        )
    }

    val totalRegistered: Int get() = resources.size + volunteers.size
}

@Serializable
data class CrisisDashboard(
    val mode: CommunityMode,
    val totalResources: Int,
    val totalVolunteers: Int,
    val openRequests: Int,
    val lifeThreatening: Int,
    val fulfilledRequests: Int,
    val totalPeopleAffected: Int,
    val resourcesByType: Map<ResourceType, Int>,
)
