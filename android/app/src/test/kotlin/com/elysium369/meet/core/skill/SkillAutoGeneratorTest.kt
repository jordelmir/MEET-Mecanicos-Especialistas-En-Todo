package com.elysium369.meet.core.skill

import org.junit.Assert.*
import org.junit.Test

class SkillAutoGeneratorTest {

    private fun action(type: String, domain: String = "plumbing") = ActionRecord(
        actionId = "act-${System.currentTimeMillis()}", actionType = type,
        domain = domain, userId = "user-1",
    )

    @Test
    fun `no proposal before minimum occurrences`() {
        val gen = SkillAutoGenerator()
        val actions = listOf(action("diagnose"), action("quote"))
        val proposal = gen.recordAction(actions.last(), actions)
        assertNull(proposal) // first time seeing this pattern
    }

    @Test
    fun `proposal generated after 3 repetitions`() {
        val gen = SkillAutoGenerator()
        val actions = listOf(action("diagnose"), action("quote"))

        gen.recordAction(actions.last(), actions) // 1st
        gen.recordAction(actions.last(), actions) // 2nd
        val proposal = gen.recordAction(actions.last(), actions) // 3rd → trigger!

        assertNotNull(proposal)
        assertEquals(SkillProposalStatus.DETECTED, proposal!!.status)
        assertTrue(proposal.name.contains("diagnose"))
    }

    @Test
    fun `approve proposal creates skill`() {
        val gen = SkillAutoGenerator()
        val actions = listOf(action("scan"), action("repair"), action("report"))

        gen.recordAction(actions.last(), actions)
        gen.recordAction(actions.last(), actions)
        val proposal = gen.recordAction(actions.last(), actions)!!

        val skill = gen.approveProposal(proposal.proposalId)
        assertNotNull(skill)
        assertEquals(3, skill!!.steps.size)
        assertEquals("plumbing", skill.domain)
    }

    @Test
    fun `reject proposal marks as rejected`() {
        val gen = SkillAutoGenerator()
        val actions = listOf(action("a"), action("b"))

        gen.recordAction(actions.last(), actions)
        gen.recordAction(actions.last(), actions)
        val proposal = gen.recordAction(actions.last(), actions)!!

        assertTrue(gen.rejectProposal(proposal.proposalId))
        assertTrue(gen.activeProposals.isEmpty())
    }

    @Test
    fun `minimum sequence length is 2`() {
        assertEquals(2, SkillAutoGenerator.MIN_SEQUENCE_LENGTH)
    }

    @Test
    fun `minimum occurrences is 3`() {
        assertEquals(3, SkillAutoGenerator.MIN_OCCURRENCES_FOR_PROPOSAL)
    }

    @Test
    fun `single action too short for sequence`() {
        val gen = SkillAutoGenerator()
        val proposal = gen.recordAction(action("solo"), listOf(action("solo")))
        assertNull(proposal)
    }

    @Test
    fun `generated skills list is maintained`() {
        val gen = SkillAutoGenerator()
        val actions = listOf(action("x"), action("y"))

        gen.recordAction(actions.last(), actions)
        gen.recordAction(actions.last(), actions)
        val proposal = gen.recordAction(actions.last(), actions)!!
        gen.approveProposal(proposal.proposalId)

        assertEquals(1, gen.generatedSkills.size)
    }
}
