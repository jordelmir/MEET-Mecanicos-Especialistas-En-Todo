package com.elysium369.meet.core.agentstore

import com.elysium369.meet.core.agentstore.data.AgentCatalogRepository
import com.elysium369.meet.core.agentstore.data.AgentEntitlementRepository
import com.elysium369.meet.core.agentstore.domain.AgentCategory
import com.elysium369.meet.core.agentstore.domain.AgentEntitlementGateway
import com.elysium369.meet.core.agentstore.domain.EntitlementSnapshot
import com.elysium369.meet.core.agentstore.domain.OfficialAgents
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AgentStoreTest {

    private class TestGateway : AgentEntitlementGateway {
            override fun currentPrincipalId(): String = "test_user"
        var entitlements: Set<String> = emptySet()
        override suspend fun fetchAuthoritativeEntitlements(): Result<EntitlementSnapshot> {
            return Result.success(
                EntitlementSnapshot(
                    principalId = "test_user",
                    entitlements = entitlements,
                    asOfEpochMs = System.currentTimeMillis(),
                    revision = 1L,
                )
            )
        }
    }

    private lateinit var catalogRepo: AgentCatalogRepository
    private lateinit var entitlementRepo: AgentEntitlementRepository
    private lateinit var testGateway: TestGateway

    @Before
    fun setUp() {
        catalogRepo = AgentCatalogRepository()
        testGateway = TestGateway()
        entitlementRepo = AgentEntitlementRepository(testGateway)
    }

    @Test
    fun `official agents roster contains Elysium original specialized archetypes`() {
        val all = OfficialAgents.ALL
        assertEquals(9, all.size)

        val ids = all.map { it.id }.toSet()
        assertTrue(ids.contains("agent.evair_core"))
        assertTrue(ids.contains("agent.master_mechanic"))
        assertTrue(ids.contains("agent.draco_dragon"))
        assertTrue(ids.contains("agent.volt_aether"))
        assertTrue(ids.contains("agent.titan_vanguard"))
        assertTrue(ids.contains("agent.laya_valkyrie"))
        assertTrue(ids.contains("agent.reptilian"))
        assertTrue(ids.contains("agent.nordic"))
        assertTrue(ids.contains("agent.grey"))
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
    fun `master mechanic is premium with valid descriptor and localized CRC pricing`() {
        val mecha = OfficialAgents.MASTER_MECHANIC
        assertFalse(mecha.isFree)
        assertEquals("agent.master_mechanic", mecha.requiredEntitlement)
        assertNotNull(mecha.commerce)
        assertEquals("agent_master_mechanic_lifetime", mecha.commerce?.storeProductId)
        assertEquals("agent.master_mechanic", mecha.commerce?.entitlementId)
        assertEquals(2_990L, mecha.priceFiatCrc)
        assertEquals(4_500L, mecha.originalPriceFiatCrc)
        assertEquals("CYBER_MECHA", mecha.avatarVisualType)
    }

    @Test
    fun `catalog repository filters by category accurately`() {
        val automotive = catalogRepo.listByCategory(AgentCategory.AUTOMOTIVE)
        assertTrue(automotive.any { it.id == "agent.master_mechanic" })
        assertTrue(automotive.any { it.id == "agent.draco_dragon" })

        val safety = catalogRepo.listByCategory(AgentCategory.SAFETY)
        assertEquals(1, safety.size)
        assertEquals("agent.titan_vanguard", safety.first().id)

        val mobility = catalogRepo.listByCategory(AgentCategory.MOBILITY)
        assertEquals(1, mobility.size)
        assertEquals("agent.laya_valkyrie", mobility.first().id)

        val xenology = catalogRepo.listByCategory(AgentCategory.XENOLOGY)
        assertEquals(3, xenology.size)
        assertTrue(xenology.any { it.id == "agent.reptilian" })
        assertTrue(xenology.any { it.id == "agent.nordic" })
        assertTrue(xenology.any { it.id == "agent.grey" })

        val all = catalogRepo.listByCategory(null)
        assertEquals(9, all.size)
    }

    @Test
    fun `authoritative entitlement unlock enables equipping`() = runBlocking {
        val titan = OfficialAgents.MASTER_MECHANIC

        // Initially locked
        entitlementRepo.refresh()
        assertFalse(entitlementRepo.hasEntitlement(titan.requiredEntitlement))
        assertFalse(entitlementRepo.isAgentOwned(titan.id, titan.requiredEntitlement))

        // Authoritative server grant
        testGateway.entitlements = setOf(titan.requiredEntitlement!!)
        entitlementRepo.refresh()

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
