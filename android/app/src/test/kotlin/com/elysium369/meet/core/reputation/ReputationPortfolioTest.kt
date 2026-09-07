package com.elysium369.meet.core.reputation

import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import org.junit.Assert.*
import org.junit.Test

class ReputationPortfolioTest {

    private fun domainRep(
        domain: UniversalServiceDomain = UniversalServiceDomain.AUTO_MECHANICAL,
        jobs: Int = 50,
        rating: Double = 4.8,
        trust: Double = 0.9,
    ) = DomainReputation(
        domain = domain, verifiedJobs = jobs, averageRating = rating,
        trustScore = trust, specializations = listOf("Frenos", "Motor"),
        yearsActive = 5, repeatCustomerRate = 0.65,
    )

    @Test
    fun `build portfolio with domains and endorsements`() {
        val engine = ReputationPortfolioEngine()
        val portfolio = engine.buildPortfolio(
            "did:elysium:fp-jose", "José García",
            domains = listOf(domainRep()),
            endorsements = listOf(Endorsement(
                "did:elysium:fp-maria", "María López", "Maestra",
                UniversalServiceDomain.AUTO_MECHANICAL,
                "Excelente mecánico", EndorsementRelationship.MASTER,
            )),
        )
        assertEquals(50, portfolio.totalVerifiedJobs)
        assertEquals(0.9, portfolio.overallTrustScore, 0.001)
        assertEquals(1, portfolio.endorsementCount)
    }

    @Test
    fun `integrity hash computed and non-empty`() {
        val engine = ReputationPortfolioEngine()
        val portfolio = engine.buildPortfolio(
            "did:elysium:fp-test", "Test",
            domains = listOf(domainRep()),
        )
        assertTrue(portfolio.integrityHash.isNotBlank())
        assertEquals(32, portfolio.integrityHash.length)
    }

    @Test
    fun `verification QR generated`() {
        val engine = ReputationPortfolioEngine()
        val portfolio = engine.buildPortfolio(
            "did:elysium:fp-test", "Test",
            domains = listOf(domainRep()),
        )
        val qr = engine.generateVerificationQr(portfolio.portfolioId)
        assertNotNull(qr)
        assertTrue(qr!!.startsWith("PORTFOLIO|"))
    }

    @Test
    fun `filter by domain — selective disclosure`() {
        val engine = ReputationPortfolioEngine()
        val portfolio = engine.buildPortfolio(
            "did:elysium:fp-test", "Test",
            domains = listOf(domainRep(), domainRep(UniversalServiceDomain.PLUMBING, 20, 4.5, 0.8)),
        )
        val filtered = engine.filterByDomain(portfolio.portfolioId, UniversalServiceDomain.AUTO_MECHANICAL)!!
        assertEquals(1, filtered.domainCount)
        assertEquals(UniversalServiceDomain.AUTO_MECHANICAL, filtered.domains.first().domain)
    }

    @Test
    fun `summary is human readable`() {
        val engine = ReputationPortfolioEngine()
        val portfolio = engine.buildPortfolio(
            "did:elysium:fp-jose", "José García",
            domains = listOf(domainRep()),
        )
        val summary = engine.generateSummary(portfolio.portfolioId)
        assertTrue(summary.contains("José García"))
        assertTrue(summary.contains("50"))
        assertTrue(summary.contains("PORTAFOLIO"))
    }

    @Test
    fun `certification active vs expired`() {
        val active = PortfolioCertification(
            "OBD-II Level 2", "ELYSIUM", UniversalServiceDomain.AUTO_MECHANICAL,
            isVerified = true,
        )
        assertTrue(active.isActive)

        val expired = PortfolioCertification(
            "Old Cert", "Other", UniversalServiceDomain.AUTO_MECHANICAL,
            isVerified = true,
            expiresAtEpochMs = System.currentTimeMillis() - 1000,
        )
        assertFalse(expired.isActive)
    }

    @Test
    fun `multiple domains averaged for trust`() {
        val engine = ReputationPortfolioEngine()
        val portfolio = engine.buildPortfolio(
            "did:elysium:fp-test", "Test",
            domains = listOf(
                domainRep(trust = 0.8),
                domainRep(UniversalServiceDomain.PLUMBING, trust = 0.6),
            ),
        )
        assertEquals(0.7, portfolio.overallTrustScore, 0.001)
    }

    @Test
    fun `all 4 endorsement relationships exist`() {
        assertEquals(4, EndorsementRelationship.entries.size)
    }

    @Test
    fun `all 6 highlight categories exist`() {
        assertEquals(6, HighlightCategory.entries.size)
    }
}
