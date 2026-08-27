package com.elysium369.meet.marketos

import com.elysium369.meet.core.money.CurrencyCode
import com.elysium369.meet.core.money.Money
import com.elysium369.meet.fuel.domain.*
import com.elysium369.meet.legal.domain.*
import com.elysium369.meet.platform.marketos.*
import com.elysium369.meet.property.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MarketOsCriticalPoliciesTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    @Test fun suspendedLawyerCannotAppearVerified() {
        val eligibility = LegalProfessionalEligibility(
            lawyerProof = proof("CAAB", CredentialStatus.SUSPENDED),
            notaryProof = null,
            declaredCapabilities = setOf("civil"),
            demonstratedMatterCount = 42,
        )
        assertFalse(eligibility.canPracticeLaw(now, 30 * day))
    }

    @Test fun notaryInactiveCannotOfferNotarialService() {
        val eligibility = LegalProfessionalEligibility(
            lawyerProof = proof("CAAB", CredentialStatus.ACTIVE),
            notaryProof = proof("DNN", CredentialStatus.EXPIRED, expiresAt = now - 1),
            declaredCapabilities = setOf("notarial"),
            demonstratedMatterCount = 8,
        )
        assertFalse(eligibility.canOfferNotarialService(now, 30 * day))
    }

    @Test fun lawyerCannotSeeMatterBeforeConflictClearance() {
        val client = UUID.randomUUID()
        val lawyer = UUID.randomUUID()
        val matter = LegalMatter(
            UUID.randomUUID(), client, "civil", null, "Incumplimiento contractual", "CR-SJ",
            "NORMAL", LegalMatterState.CONFLICT_SCREENING, LegalDisclosureLevel.PARTY_NAMES_ONLY, 1,
        )
        val pending = LegalConflictCheck(matter.matterId, lawyer, null, LegalConflictDecision.PENDING, now, now + day)
        assertFalse(LegalAccessPolicy.mayReadPrivilegedMatter(lawyer, matter, setOf(lawyer), pending, now))
    }

    @Test fun sellerCannotMarkOwnershipVerifiedWithoutRegistryEvidence() {
        val passport = PropertyPassport(
            UUID.randomUUID(), "1-***-000", null,
            mapOf("registered_owner" to TruthClaim("registered_owner", ClaimTruthState.DECLARED, now, null, null)),
            now,
        )
        assertFalse(passport.ownershipVerified)
        assertEquals(PropertyTrustRisk.UNKNOWN, PropertyTrustEngine.assess(passport, false, false))
    }

    @Test fun presaleCannotPublishWithoutComplianceGate() {
        val listing = PropertyListing(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), PropertyOperation.PRESALE,
            "development", "Escazú", null, AddressDisclosure.APPROXIMATE_ZONE,
            PropertyListingState.DRAFT, null, 0,
        )
        assertFalse(listing.canPublish)
        assertEquals("Escazú", listing.publicAddress())
    }

    @Test fun qualifyingSpendIssuesExactCouponUnits() {
        val campaign = campaign()
        assertEquals(0, FuelRewardPolicy.awardUnits(Money(4_999, CurrencyCode.CRC), campaign))
        assertEquals(1, FuelRewardPolicy.awardUnits(Money(5_000, CurrencyCode.CRC), campaign))
        assertEquals(1, FuelRewardPolicy.awardUnits(Money(9_999, CurrencyCode.CRC), campaign))
        assertEquals(2, FuelRewardPolicy.awardUnits(Money(10_000, CurrencyCode.CRC), campaign))
    }

    @Test fun fuelPriceCreditRequiresRegulatoryApproval() {
        assertThrows(IllegalArgumentException::class.java) {
            campaign(benefit = FuelBenefitType.FUEL_PRICE_CREDIT)
        }
    }

    @Test fun couponQrContainsOnlyOpaqueToken() {
        val raw = "https://meet.app/q/9N7vUQcv2ABkx2t9VZq6eP3h4K0xT8aB"
        assertEquals(
            "9N7vUQcv2ABkx2t9VZq6eP3h4K0xT8aB",
            OpaqueQrToken.fromPublicUrl(raw, setOf("meet.app")).value,
        )
        assertThrows(IllegalArgumentException::class.java) {
            OpaqueQrToken.fromPublicUrl("https://meet.app/q/token?phone=88888888", setOf("meet.app"))
        }
    }

    private fun proof(authority: String, status: CredentialStatus, expiresAt: Long? = now + day) =
        ProfessionalCredentialProof(
            UUID.randomUUID(), UUID.randomUUID(), authority, "***123", status,
            now - day, expiresAt, "evidence://proof", "v1",
        )

    private fun campaign(benefit: FuelBenefitType = FuelBenefitType.CAR_WASH_REWARD) = FuelCampaignVersion(
        UUID.randomUUID(), UUID.randomUUID(), 1, Money(5_000, CurrencyCode.CRC),
        FuelIssuePolicy.ONE_PER_EVERY_N_SPEND, null, benefit,
        FuelCampaignTerms(
            now - day, now + day, "Lavado gratis", "Compra liquidada",
            "Una redención por cupón", "Mostrar QR al dependiente", 1, "a".repeat(64),
        ),
        now,
        null,
    )
}
