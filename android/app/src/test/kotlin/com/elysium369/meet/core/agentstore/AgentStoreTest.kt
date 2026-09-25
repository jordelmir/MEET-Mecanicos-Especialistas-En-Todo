package com.elysium369.meet.core.agentstore

import com.elysium369.meet.core.agentstore.data.AgentCatalogRepository
import com.elysium369.meet.core.agentstore.data.AgentEntitlementRepository
import com.elysium369.meet.core.agentstore.domain.AgentCategory
import com.elysium369.meet.core.agentstore.domain.OfficialAgents
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AgentStoreTest {

    private lateinit var catalogRepo: AgentCatalogRepository
    private lateinit var entitlementRepo: AgentEntitlementRepository

    @Before
    fun setUp() {
        catalogRepo = AgentCatalogRepository()
        entitlementRepo = AgentEntitlementRepository()
    }

    @Test
    fun `official agents roster contains all five specialized archetypes`() {
        val all = OfficialAgents.ALL
        assertEquals(5, all.size)

        val ids = all.map { it.id }.toSet()
        assertTrue(ids.contains("agent.evair_core"))
        assertTrue(ids.contains("agent.master_mechanic"))
        assertTrue(ids.contains("agent.vanguard_sentinel"))
        assertTrue(ids.contains("agent.mobility_prime"))
        assertTrue(ids.contains("agent.emissions_specialist"))
    }

    @Test
    fun `evair core is free and default equipped`() {
        val evair = OfficialAgents.EVAIR_CORE
        assertTrue(evair.isFree)
        assertNull(evair.requiredEntitlement)
        assertEquals(0L, evair.priceFiatCrc)
        assertEquals("QUANTUM_SPHERE", evair.avatarVisualType)

        assertTrue(entitlementRepo.isAgentOwned(evair.id, evair.requiredEntitlement))
        assertEquals("agent.evair_core", entitlementRepo.equippedAgentId.value)
    }

    @Test
    fun `paid agents require explicit entitlements and have valid localized CRC pricing`() {
        val titan = OfficialAgents.MASTER_MECHANIC
        assertFalse(titan.isFree)
        assertEquals("agent.master_mechanic", titan.requiredEntitlement)
        assertEquals(2_990L, titan.priceFiatCrc)
        assertEquals(4_500L, titan.originalPriceFiatCrc)
        assertEquals("TITAN_EXOSKELETON", titan.avatarVisualType)

        val sentinel = OfficialAgents.VANGUARD_SENTINEL
        assertFalse(sentinel.isFree)
        assertEquals("agent.vanguard_sentinel", sentinel.requiredEntitlement)
        assertEquals(1_990L, sentinel.priceFiatCrc)
        assertEquals("TACTICAL_SHIELD", sentinel.avatarVisualType)

        val concierge = OfficialAgents.MOBILITY_PRIME
        assertFalse(concierge.isFree)
        assertEquals("agent.mobility_prime", concierge.requiredEntitlement)
        assertEquals(1_490L, concierge.priceFiatCrc)
        assertEquals("AERODYNAMIC_CONCIERGE", concierge.avatarVisualType)

        val metrologist = OfficialAgents.EMISSIONS_SPECIALIST
        assertFalse(metrologist.isFree)
        assertEquals("agent.emissions_specialist", metrologist.requiredEntitlement)
        assertEquals(1_990L, metrologist.priceFiatCrc)
        assertEquals("METROLOGY_PRISM", metrologist.avatarVisualType)
    }

    @Test
    fun `catalog repository filters by category accurately`() {
        val mechanics = catalogRepo.listByCategory(AgentCategory.AUTOMOTIVE)
        assertEquals(1, mechanics.size)
        assertEquals("agent.master_mechanic", mechanics.first().id)

        val safety = catalogRepo.listByCategory(AgentCategory.SAFETY)
        assertEquals(1, safety.size)
        assertEquals("agent.vanguard_sentinel", safety.first().id)

        val mobility = catalogRepo.listByCategory(AgentCategory.MOBILITY)
        assertEquals(1, mobility.size)
        assertEquals("agent.mobility_prime", mobility.first().id)

        val all = catalogRepo.listByCategory(null)
        assertEquals(5, all.size)
    }

    @Test
    fun `granting entitlement unlocks agent and enables equipping`() {
        val titan = OfficialAgents.MASTER_MECHANIC

        // Initially locked
        assertFalse(entitlementRepo.hasEntitlement(titan.requiredEntitlement))
        assertFalse(entitlementRepo.isAgentOwned(titan.id, titan.requiredEntitlement))

        // Grant entitlement
        entitlementRepo.grantEntitlement(titan.requiredEntitlement!!)

        // Now unlocked
        assertTrue(entitlementRepo.hasEntitlement(titan.requiredEntitlement))
        assertTrue(entitlementRepo.isAgentOwned(titan.id, titan.requiredEntitlement))

        // Equip agent
        entitlementRepo.equipAgent(titan.id)
        assertEquals(titan.id, entitlementRepo.equippedAgentId.value)
    }

    @Test
    fun `all agents have valid detailed capability pack descriptions with risk ratings`() {
        for (agent in OfficialAgents.ALL) {
            assertTrue("Agent ${agent.id} must have capabilities", agent.detailedCapabilities.isNotEmpty())
            assertTrue("Agent ${agent.id} must have voice sample text", agent.voiceSampleText.isNotBlank())
            for (cap in agent.detailedCapabilities) {
                assertTrue(cap.name.isNotBlank())
                assertTrue(cap.capabilityId.isNotBlank())
                assertTrue(cap.domain.isNotBlank())
                assertTrue(cap.riskLevel in setOf("READ_ONLY", "COMMITTING", "FINANCIAL", "SAFETY_CRITICAL", "VEHICLE_CRITICAL"))
                assertTrue(cap.physicalModelDescription.isNotBlank())
            }
        }
    }
}
