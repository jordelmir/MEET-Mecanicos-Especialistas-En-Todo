package com.elysium369.meet.core.resilience

import com.elysium369.meet.core.dignity.ExperienceCategory
import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import org.junit.Assert.*
import org.junit.Test

class CommunityResilienceTest {

    @Test
    fun `register and find resources by type`() {
        val net = CommunityResilienceNetwork()
        net.registerResource(CommunityResource(
            "r1", "user-1", "María", ResourceType.GENERATOR,
            "Honda 3500W", isAvailable = true, isFree = true,
        ))
        val gens = net.availableResources(ResourceType.GENERATOR)
        assertEquals(1, gens.size)
    }

    @Test
    fun `free resources only returns isFree`() {
        val net = CommunityResilienceNetwork()
        net.registerResource(CommunityResource(
            "r1", "u1", "A", ResourceType.WATER, "Tanque 1000L", isFree = true,
        ))
        net.registerResource(CommunityResource(
            "r2", "u2", "B", ResourceType.WATER, "Tanque 500L", isFree = false,
        ))
        assertEquals(1, net.freeResources().size)
    }

    @Test
    fun `register volunteers with skills from DignityMapper categories`() {
        val net = CommunityResilienceNetwork()
        net.registerVolunteer(CommunityVolunteer(
            "v1", "Carlos",
            skills = listOf(
                VolunteerSkill("Primeros auxilios", ExperienceCategory.CAREGIVING,
                    UniversalServiceDomain.CUSTOM, isCertified = true),
                VolunteerSkill("Electricidad", ExperienceCategory.CONSTRUCTION,
                    UniversalServiceDomain.ELECTRICAL),
            ),
        ))
        val medical = net.findVolunteers(ExperienceCategory.CAREGIVING)
        assertEquals(1, medical.size)
    }

    @Test
    fun `find volunteers by service domain`() {
        val net = CommunityResilienceNetwork()
        net.registerVolunteer(CommunityVolunteer(
            "v1", "Ana",
            skills = listOf(
                VolunteerSkill("Plomería", ExperienceCategory.CONSTRUCTION,
                    UniversalServiceDomain.PLUMBING),
            ),
        ))
        val plumbers = net.findVolunteersByDomain(UniversalServiceDomain.PLUMBING)
        assertEquals(1, plumbers.size)
    }

    @Test
    fun `aid request ordered by urgency — life threatening first`() {
        val net = CommunityResilienceNetwork()
        net.requestAid(AidRequest("a1", "u1", "Juan", ResourceType.WATER,
            AidUrgency.MODERATE, "Necesito agua"))
        net.requestAid(AidRequest("a2", "u2", "María", ResourceType.MEDICAL,
            AidUrgency.LIFE_THREATENING, "Herida grave"))
        val open = net.openRequests()
        assertEquals(AidUrgency.LIFE_THREATENING, open.first().urgency)
    }

    @Test
    fun `fulfill aid request changes status`() {
        val net = CommunityResilienceNetwork()
        net.requestAid(AidRequest("a1", "u1", "Luis", ResourceType.FOOD,
            AidUrgency.HIGH, "Comida para 5"))
        assertTrue(net.fulfillAid("a1", "volunteer-1"))
        assertEquals(0, net.openRequests().size)
    }

    @Test
    fun `crisis dashboard counts all resources`() {
        val net = CommunityResilienceNetwork()
        net.activateMode(CommunityMode.CRISIS)
        net.registerResource(CommunityResource("r1", "u1", "A",
            ResourceType.GENERATOR, "Gen 1"))
        net.registerResource(CommunityResource("r2", "u2", "B",
            ResourceType.SHELTER, "Casa grande"))
        net.registerVolunteer(CommunityVolunteer("v1", "C",
            skills = emptyList()))
        net.requestAid(AidRequest("a1", "u3", "D", ResourceType.WATER,
            AidUrgency.LIFE_THREATENING, "Urgent", peopleAffected = 15))

        val dash = net.crisisDashboard()
        assertEquals(CommunityMode.CRISIS, dash.mode)
        assertEquals(2, dash.totalResources)
        assertEquals(1, dash.totalVolunteers)
        assertEquals(1, dash.openRequests)
        assertEquals(1, dash.lifeThreatening)
        assertEquals(15, dash.totalPeopleAffected)
    }

    @Test
    fun `match request to available resources`() {
        val net = CommunityResilienceNetwork()
        net.registerResource(CommunityResource("r1", "u1", "A",
            ResourceType.WATER, "500L"))
        net.requestAid(AidRequest("a1", "u2", "B",
            ResourceType.WATER, AidUrgency.CRITICAL, "Need water"))
        val matches = net.matchRequestToResources("a1")
        assertEquals(1, matches.size)
    }

    @Test
    fun `all 14 resource types exist`() {
        assertEquals(14, ResourceType.entries.size)
    }

    @Test
    fun `all 4 community modes exist`() {
        assertEquals(4, CommunityMode.entries.size)
    }
}
