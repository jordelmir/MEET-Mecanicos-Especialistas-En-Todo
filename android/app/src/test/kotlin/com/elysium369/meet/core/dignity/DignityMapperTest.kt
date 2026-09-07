package com.elysium369.meet.core.dignity

import com.elysium369.meet.education.economic.SkillCredentialState
import org.junit.Assert.*
import org.junit.Test

class DignityMapperTest {

    @Test
    fun `mother of 20 years discovers 4 marketable skills`() {
        val exp = LifeExperience("e1", ExperienceCategory.PARENTING,
            "Madre de 3 hijos", 20)
        val skills = DignityMapper.discoverSkills(listOf(exp))
        assertEquals(4, skills.size)
        assertTrue(skills.any { it.skillName == "Pedagogía informal" })
        assertTrue(skills.any { it.skillName == "Gestión del tiempo" })
    }

    @Test
    fun `farmer discovers mechanical skills`() {
        val exp = LifeExperience("e2", ExperienceCategory.FARMING,
            "Campesino 15 años", 15)
        val skills = DignityMapper.discoverSkills(listOf(exp))
        assertTrue(skills.any { it.skillName == "Mecánica de maquinaria" })
    }

    @Test
    fun `10 plus years maps to HIGH confidence`() {
        val exp = LifeExperience("e3", ExperienceCategory.COOKING,
            "Cocinera de comedor", 12)
        val skills = DignityMapper.discoverSkills(listOf(exp))
        assertTrue(skills.all { it.transferConfidence == TransferConfidence.HIGH })
    }

    @Test
    fun `2 years maps to LOW confidence`() {
        val exp = LifeExperience("e4", ExperienceCategory.DRIVING,
            "Conductor informal", 3)
        val skills = DignityMapper.discoverSkills(listOf(exp))
        assertTrue(skills.all { it.transferConfidence == TransferConfidence.LOW })
    }

    @Test
    fun `every discovered skill has honest disclaimer`() {
        val exp = LifeExperience("e5", ExperienceCategory.CONSTRUCTION,
            "Albañil informal", 8)
        val skills = DignityMapper.discoverSkills(listOf(exp))
        assertTrue(skills.all { it.disclaimer.isNotBlank() })
        assertTrue(skills.all { it.requiresVerification })
    }

    @Test
    fun `construction experience discovers plumbing and electrical`() {
        val exp = LifeExperience("e6", ExperienceCategory.CONSTRUCTION,
            "Ayudante de construcción", 7)
        val skills = DignityMapper.discoverSkills(listOf(exp))
        assertTrue(skills.any { it.skillName.contains("Plomería") })
        assertTrue(skills.any { it.skillName.contains("Electricidad") })
    }

    @Test
    fun `build profile aggregates all experiences`() {
        val experiences = listOf(
            LifeExperience("e1", ExperienceCategory.PARENTING, "Madre", 15),
            LifeExperience("e2", ExperienceCategory.COOKING, "Cocinera", 10),
        )
        val profile = DignityMapper.buildProfile("u1", "María", experiences)
        assertTrue(profile.totalDiscoveredSkills >= 7)
        assertTrue(profile.selfAssessmentComplete)
    }

    @Test
    fun `high confidence skills are subset of all skills`() {
        val exp = LifeExperience("e7", ExperienceCategory.SELLING,
            "Vendedora ambulante", 12)
        val profile = DignityMapper.buildProfile("u2", "Ana", listOf(exp))
        assertTrue(profile.highConfidenceSkills.size <= profile.totalDiscoveredSkills)
    }

    @Test
    fun `never grants above PRACTICED from life experience alone`() {
        val exp = LifeExperience("e8", ExperienceCategory.TECHNOLOGY,
            "Autodidacta tech", 20)
        val skills = DignityMapper.discoverSkills(listOf(exp))
        // Life experience alone caps at PRACTICED, never DEMONSTRATED
        assertTrue(skills.all {
            it.estimatedCredentialState.ordinal <= SkillCredentialState.PRACTICED.ordinal
        })
    }

    @Test
    fun `all 16 experience categories exist`() {
        assertEquals(16, ExperienceCategory.entries.size)
    }
}
