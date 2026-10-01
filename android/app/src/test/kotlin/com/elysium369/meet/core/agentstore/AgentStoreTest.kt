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
    fun `official agents roster contains Elysium original specialized archetypes`() {
        val all = OfficialAgents.ALL
        assertEquals(6, all.size)

        val ids = all.map { it.id }.toSet()
        assertTrue(ids.contains("agent.evair_core"))
        assertTrue(ids.contains("agent.master_mechanic"))
        assertTrue(ids.contains("agent.draco_dragon"))
        assertTrue(ids.contains("agent.pokemon_volt"))
        assertTrue(ids.contains("agent.saiyan_ssj4"))
        assertTrue(ids.contains("agent.laya_valkyrie"))
    }

    @Test
    fun `evair core is free and default equipped`() {
        val evair = OfficialAgents.EVAIR_CORE
        assertTrue(evair.isFree)
        assertNull(evair.requiredEntitlement)
        assertEquals(0L, evair.priceFiatCrc)
        assertEquals("EVAIR_SPIRIT", evair.avatarVisualType)

        assertTrue(entitlementRepo.isAgentOwned(evair.id, evair.requiredEntitlement))
        assertEquals("agent.evair_core", entitlementRepo.equippedAgentId.value)
    }

    @Test
    fun `master mechanic has valid descriptor and visual attributes`() {
        val mecha = OfficialAgents.MASTER_MECHANIC
        assertTrue(mecha.isFree)
        assertNull(mecha.requiredEntitlement)
        assertEquals(0L, mecha.priceFiatCrc)
        assertEquals("CYBER_MECHA", mecha.avatarVisualType)
    }

    @Test
    fun `catalog repository filters by category accurately`() {
        val mechanics = catalogRepo.listByCategory(AgentCategory.AUTOMOTIVE)
        assertTrue(mechanics.any { it.id == "agent.master_mechanic" })
        assertTrue(mechanics.any { it.id == "agent.draco_dragon" })

        val safety = catalogRepo.listByCategory(AgentCategory.SAFETY)
        assertEquals(1, safety.size)
        assertEquals("agent.saiyan_ssj4", safety.first().id)

        val mobility = catalogRepo.listByCategory(AgentCategory.MOBILITY)
        assertEquals(1, mobility.size)
        assertEquals("agent.laya_valkyrie", mobility.first().id)

        val all = catalogRepo.listByCategory(null)
        assertEquals(6, all.size)
    }

    @Test
    fun `granting entitlement and equipping agent updates state`() {
        val mecha = OfficialAgents.MASTER_MECHANIC

        assertTrue(entitlementRepo.isAgentOwned(mecha.id, mecha.requiredEntitlement))

        // Grant custom entitlement
        entitlementRepo.grantEntitlement("agent.custom_pack")
        entitlementRepo.equipAgent(mecha.id)
        assertEquals(mecha.id, entitlementRepo.equippedAgentId.value)
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
