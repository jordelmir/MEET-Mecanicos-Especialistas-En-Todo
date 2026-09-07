package com.elysium369.meet.core.agent

import org.junit.Assert.*
import org.junit.Test

class ElysiumAgentBusTest {

    @Test
    fun `register and route to agent by domain`() {
        val bus = ElysiumAgentBus()
        val agent = AgentRegistration(
            agentId = AgentId("auto-agent"),
            domain = AgentDomain.AUTOMOTIVE,
            capabilities = listOf(AgentCapability("repair", "Handle car repair")),
            priority = 10,
        )
        bus.registerAgent(agent)

        val request = AgentRequest(
            messageId = "msg-1", intent = "fix my car",
            targetDomain = AgentDomain.AUTOMOTIVE,
        )
        val routed = bus.route(request)
        assertNotNull(routed)
        assertEquals("auto-agent", routed!!.agentId.value)
    }

    @Test
    fun `route by explicit agent id`() {
        val bus = ElysiumAgentBus()
        bus.registerAgent(AgentRegistration(
            AgentId("plumber"), AgentDomain.PLUMBING,
            listOf(AgentCapability("pipe", "Fix pipes")),
        ))
        val request = AgentRequest(
            messageId = "msg-2", intent = "anything",
            toAgentId = "plumber",
        )
        val routed = bus.route(request)
        assertNotNull(routed)
        assertEquals(AgentDomain.PLUMBING, routed!!.domain)
    }

    @Test
    fun `route by capability matching`() {
        val bus = ElysiumAgentBus()
        bus.registerAgent(AgentRegistration(
            AgentId("edu-agent"), AgentDomain.EDUCATION,
            listOf(AgentCapability("learn", "Teach concepts")),
        ))
        val request = AgentRequest(messageId = "msg-3", intent = "learn math")
        val routed = bus.route(request)
        assertNotNull(routed)
        assertEquals(AgentDomain.EDUCATION, routed!!.domain)
    }

    @Test
    fun `returns null when no matching agent`() {
        val bus = ElysiumAgentBus()
        val request = AgentRequest(messageId = "msg-4", intent = "fly to moon")
        assertNull(bus.route(request))
    }

    @Test
    fun `cannot register duplicate agent id`() {
        val bus = ElysiumAgentBus()
        val agent = AgentRegistration(
            AgentId("a1"), AgentDomain.GENERAL,
            listOf(AgentCapability("help", "General help")),
        )
        bus.registerAgent(agent)
        assertThrows(IllegalArgumentException::class.java) {
            bus.registerAgent(agent)
        }
    }

    @Test
    fun `higher priority agent wins domain routing`() {
        val bus = ElysiumAgentBus()
        bus.registerAgent(AgentRegistration(
            AgentId("low"), AgentDomain.AUTOMOTIVE,
            listOf(AgentCapability("fix", "Fix")), priority = 1,
        ))
        bus.registerAgent(AgentRegistration(
            AgentId("high"), AgentDomain.AUTOMOTIVE,
            listOf(AgentCapability("fix", "Fix")), priority = 10,
        ))
        val request = AgentRequest(
            messageId = "msg-5", intent = "fix",
            targetDomain = AgentDomain.AUTOMOTIVE,
        )
        assertEquals("high", bus.route(request)!!.agentId.value)
    }

    @Test
    fun `agents for domain returns sorted by priority`() {
        val bus = ElysiumAgentBus()
        bus.registerAgent(AgentRegistration(
            AgentId("b"), AgentDomain.EDUCATION,
            listOf(AgentCapability("teach", "Teach")), priority = 5,
        ))
        bus.registerAgent(AgentRegistration(
            AgentId("a"), AgentDomain.EDUCATION,
            listOf(AgentCapability("tutor", "Tutor")), priority = 15,
        ))
        val agents = bus.agentsForDomain(AgentDomain.EDUCATION)
        assertEquals(2, agents.size)
        assertEquals("a", agents.first().agentId.value)
    }

    @Test
    fun `event listeners receive published events`() {
        val bus = ElysiumAgentBus()
        var received: AgentEvent? = null
        bus.addEventListener { received = it }
        bus.publishEvent(AgentEvent("ev-1", fromAgentId = "a1", eventType = "job_complete"))
        assertNotNull(received)
        assertEquals("job_complete", received!!.eventType)
    }

    @Test
    fun `all 12 agent domains exist`() {
        assertEquals(12, AgentDomain.entries.size)
    }
}
