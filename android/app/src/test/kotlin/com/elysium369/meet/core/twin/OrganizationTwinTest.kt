package com.elysium369.meet.core.twin

import com.elysium369.meet.core.services.kernel.ServiceVertical
import org.junit.Assert.*
import org.junit.Test

class OrganizationTwinTest {

    // ─── Creation ───

    @Test
    fun `create micro-enterprise from solo provider`() {
        val twin = BusinessTwinEngine.createMicroEnterprise(
            ownerId = "provider-1",
            name = "Fontanería Express CR",
            verticals = listOf(ServiceVertical.UNIVERSAL),
            operatingZone = OperatingZone(
                centerLat = 9.9281,
                centerLng = -84.0907,
                radiusKm = 15.0,
                label = "Gran Área Metropolitana",
                isPrimary = true,
            ),
        )

        assertEquals(OrganizationType.MICRO_ENTERPRISE, twin.type)
        assertEquals(OrganizationState.ONBOARDING, twin.state)
        assertEquals(1, twin.teamSize) // just owner
        assertTrue(twin.isMicroEnterprise)
    }

    @Test
    fun `blank name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BusinessTwinEngine.createMicroEnterprise(
                ownerId = "owner-1",
                name = "",
                verticals = listOf(ServiceVertical.REPAIR),
                operatingZone = OperatingZone(9.9, -84.0, 10.0, "Test"),
            )
        }
    }

    @Test
    fun `empty verticals rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BusinessTwinEngine.createMicroEnterprise(
                ownerId = "owner-1",
                name = "Test Business",
                verticals = emptyList(),
                operatingZone = OperatingZone(9.9, -84.0, 10.0, "Test"),
            )
        }
    }

    // ─── Team Management ───

    @Test
    fun `add team member increases team size`() {
        val twin = BusinessTwinEngine.createMicroEnterprise(
            ownerId = "owner-1",
            name = "Taller Mecánico",
            verticals = listOf(ServiceVertical.REPAIR),
            operatingZone = OperatingZone(9.9, -84.0, 10.0, "San José"),
        )

        val updated = BusinessTwinEngine.addTeamMember(twin, "member-1")
        assertEquals(2, updated.teamSize)
        assertTrue(updated.isMicroEnterprise) // still <= 5
    }

    @Test
    fun `cannot add owner as team member`() {
        val twin = BusinessTwinEngine.createMicroEnterprise(
            ownerId = "owner-1",
            name = "Test",
            verticals = listOf(ServiceVertical.REPAIR),
            operatingZone = OperatingZone(9.9, -84.0, 10.0, "Test"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            BusinessTwinEngine.addTeamMember(twin, "owner-1")
        }
    }

    @Test
    fun `cannot add duplicate member`() {
        val twin = BusinessTwinEngine.createMicroEnterprise(
            ownerId = "owner-1",
            name = "Test",
            verticals = listOf(ServiceVertical.REPAIR),
            operatingZone = OperatingZone(9.9, -84.0, 10.0, "Test"),
        )

        val withMember = BusinessTwinEngine.addTeamMember(twin, "member-1")
        assertThrows(IllegalArgumentException::class.java) {
            BusinessTwinEngine.addTeamMember(withMember, "member-1")
        }
    }

    // ─── Metrics Tracking ───

    @Test
    fun `recording job outcomes updates metrics`() {
        var twin = BusinessTwinEngine.createMicroEnterprise(
            ownerId = "owner-1",
            name = "Plomería Pro",
            verticals = listOf(ServiceVertical.UNIVERSAL),
            operatingZone = OperatingZone(9.9, -84.0, 15.0, "GAM"),
        )

        twin = BusinessTwinEngine.recordJobOutcome(twin, true, false, 25_000, 45)
        twin = BusinessTwinEngine.recordJobOutcome(twin, true, true, 18_000, 30)
        twin = BusinessTwinEngine.recordJobOutcome(twin, false, false, 0, 60)

        assertEquals(3, twin.metrics.totalJobsCompleted)
        assertEquals(43_000, twin.metrics.totalRevenue)
        assertTrue(twin.metrics.verifiedOutcomeRate > 0.6)
        assertTrue(twin.metrics.healthScore > 0.0)
    }

    @Test
    fun `health score is zero with no jobs`() {
        val metrics = OrganizationMetrics()
        assertEquals(0.0, metrics.healthScore, 0.001)
    }

    // ─── Organization Types Coverage ───

    @Test
    fun `all 9 organization types exist`() {
        assertEquals(9, OrganizationType.entries.size)
        assertNotNull(OrganizationType.valueOf("SOLO_PROVIDER"))
        assertNotNull(OrganizationType.valueOf("WORKSHOP"))
        assertNotNull(OrganizationType.valueOf("FLEET"))
        assertNotNull(OrganizationType.valueOf("MICRO_ENTERPRISE"))
    }
}
