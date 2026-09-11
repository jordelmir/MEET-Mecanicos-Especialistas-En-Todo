package com.elysium369.meet.core.services.kernel

import com.elysium369.meet.core.money.Money
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class UniversalServiceKernelTest {

    // ─── ServiceIntent ───

    @Test
    fun `service intent captures plumbing need correctly`() {
        val intent = ServiceIntent(
            requesterId = "user-1",
            domain = UniversalServiceDomain.PLUMBING,
            desiredOutcome = "Reparar fuga de agua en cocina",
            locationLat = 9.9281,
            locationLng = -84.0907,
            urgency = ServiceUrgency.URGENT,
            evidenceRefs = listOf("photo://leak_kitchen_001.jpg"),
        )

        assertEquals(UniversalServiceDomain.PLUMBING, intent.domain)
        assertEquals(ServiceVertical.UNIVERSAL, intent.parentVertical)
        assertEquals(ServiceUrgency.URGENT, intent.urgency)
        assertEquals(ServiceIntentState.CREATED, intent.state)
        assertTrue(intent.evidenceRefs.isNotEmpty())
    }

    @Test
    fun `service intent captures automotive need correctly`() {
        val intent = ServiceIntent(
            requesterId = "user-2",
            domain = UniversalServiceDomain.AUTO_MECHANICAL,
            desiredOutcome = "Diagnóstico de motor — DTC P0301",
            budgetMax = Money.ofCrc(50_000),
            qualificationsRequired = listOf("OBD_CERTIFIED"),
        )

        assertEquals(ServiceVertical.REPAIR, intent.parentVertical)
        assertEquals(50_000L, intent.budgetMax?.amountMinor)
    }

    @Test
    fun `service intent captures digital need correctly`() {
        val intent = ServiceIntent(
            requesterId = "user-3",
            domain = UniversalServiceDomain.SOFTWARE,
            modality = ServiceModality.REMOTE,
            desiredOutcome = "Landing page para mi negocio de fontanería",
        )

        assertEquals(ServiceModality.REMOTE, intent.modality)
        assertEquals("Software", intent.domain.displayName)
    }

    // ─── ServiceJob ───

    @Test
    fun `service job tracks provider agreement`() {
        val intentId = UUID.randomUUID()
        val job = ServiceJob(
            intentId = intentId,
            providerId = "provider-1",
            customerId = "user-1",
            domain = UniversalServiceDomain.PLUMBING,
            agreedScope = "Reparación de tubería PVC en pared de cocina",
            quote = Money.ofCrc(25_000),
            materials = listOf(
                ServiceMaterial(
                    description = "Tubería PVC 1/2\"",
                    quantity = 2,
                    estimatedCost = Money.ofCrc(3_000),
                )
            ),
            evidenceRequirements = listOf("PHOTO_BEFORE", "PHOTO_AFTER"),
            paymentTerms = PaymentTerms.ON_COMPLETION,
        )

        assertEquals(ServiceJobState.PENDING, job.state)
        assertEquals(25_000L, job.quote.amountMinor)
        assertEquals(1, job.materials.size)
        assertTrue(job.evidenceRequirements.contains("PHOTO_AFTER"))
    }

    // ─── ServiceOutcome ───

    @Test
    fun `service outcome captures verified result`() {
        val jobId = UUID.randomUUID()
        val outcome = ServiceOutcome(
            jobId = jobId,
            isSuccessful = true,
            customerConfirmed = true,
            evidenceRefs = listOf("photo://after_repair.jpg", "scan://post_scan.pdf"),
            outcomeDescription = "Fuga reparada, presión de agua normalizada",
            completionDurationMinutes = 45,
            warrantyDays = 30,
            customerSatisfaction = OutcomeSatisfaction.SATISFIED,
            verifiedAtEpochMs = System.currentTimeMillis(),
        )

        assertTrue(outcome.isSuccessful)
        assertTrue(outcome.customerConfirmed)
        assertEquals(OutcomeSatisfaction.SATISFIED, outcome.customerSatisfaction)
        assertEquals(30, outcome.warrantyDays)
    }

    // ─── ProviderCapability ───

    @Test
    fun `provider capability models plumber with credentials`() {
        val capability = ProviderCapability(
            providerId = "provider-1",
            domain = UniversalServiceDomain.PLUMBING,
            skills = listOf("PVC_ASSEMBLY", "VALVE_REPAIR", "DRAIN_CLEARING"),
            credentials = listOf(
                ProviderCredential(
                    name = "Técnico en Fontanería",
                    issuedBy = "INA Costa Rica",
                    isExternal = true,
                    verifiedAtEpochMs = 1_700_000_000_000,
                )
            ),
            tools = listOf("PIPE_WRENCH", "THREAD_SEALER", "PIPE_CUTTER"),
            serviceRadiusKm = 15.0,
            availableNow = true,
            completedJobsCount = 47,
            verifiedOutcomeRate = 0.94,
            repeatCustomerRate = 0.62,
        )

        assertEquals(3, capability.skills.size)
        assertTrue(capability.credentials.first().isExternal)
        assertEquals(0.94, capability.verifiedOutcomeRate, 0.001)
    }

    // ─── Cross-Domain Proof (§39) ───

    @Test
    fun `same ServiceJob model works for automotive and plumbing`() {
        val autoJob = ServiceJob(
            intentId = UUID.randomUUID(),
            providerId = "mechanic-1",
            customerId = "driver-1",
            domain = UniversalServiceDomain.AUTO_MECHANICAL,
            agreedScope = "Cambio de pastillas de freno delanteras",
            quote = Money.ofCrc(35_000),
            evidenceRequirements = listOf("PHOTO_BEFORE", "PHOTO_AFTER", "POST_SCAN"),
        )

        val plumbJob = ServiceJob(
            intentId = UUID.randomUUID(),
            providerId = "plumber-1",
            customerId = "homeowner-1",
            domain = UniversalServiceDomain.PLUMBING,
            agreedScope = "Reparación de fuga en tubería de agua caliente",
            quote = Money.ofCrc(18_000),
            evidenceRequirements = listOf("PHOTO_BEFORE", "PHOTO_AFTER"),
        )

        // Both use the same data structure, different domains
        assertNotEquals(autoJob.domain, plumbJob.domain)
        assertEquals(autoJob.state, plumbJob.state) // both PENDING
        assertTrue(autoJob.evidenceRequirements.contains("PHOTO_BEFORE"))
        assertTrue(plumbJob.evidenceRequirements.contains("PHOTO_BEFORE"))

        // Domain-specific parent vertical preserved
        assertEquals(ServiceVertical.REPAIR, autoJob.domain.parentVertical)
        assertEquals(ServiceVertical.UNIVERSAL, plumbJob.domain.parentVertical)
    }

    // ─── Domain Coverage ───

    @Test
    fun `all universal service domains have a non-blank display name`() {
        for (domain in UniversalServiceDomain.entries) {
            assertTrue(
                "Domain $domain must have a non-blank displayName",
                domain.displayName.isNotBlank()
            )
        }
    }

    @Test
    fun `at least 15 universal service domains exist for economic breadth`() {
        assertTrue(
            "Must have 15+ domains for universal coverage",
            UniversalServiceDomain.entries.size >= 15
        )
    }

    @Test
    fun `all urgency levels are distinct`() {
        assertEquals(4, ServiceUrgency.entries.size)
    }

    @Test
    fun `all job states cover the complete lifecycle`() {
        val states = ServiceJobState.entries.map { it.name }.toSet()
        assertTrue(states.contains("PENDING"))
        assertTrue(states.contains("IN_PROGRESS"))
        assertTrue(states.contains("COMPLETED"))
        assertTrue(states.contains("VERIFIED"))
        assertTrue(states.contains("DISPUTED"))
    }
}
