package com.elysium369.meet.mobility

import com.elysium369.meet.mobility.domain.ledger.LedgerAccountType
import com.elysium369.meet.mobility.domain.ledger.LedgerEntry
import com.elysium369.meet.mobility.domain.ledger.LedgerReferenceType
import com.elysium369.meet.mobility.domain.ledger.LedgerTransaction
import com.elysium369.meet.mobility.domain.ledger.TripSettlement
import com.elysium369.meet.mobility.domain.models.CurrencyCode
import com.elysium369.meet.mobility.domain.models.MarketId
import com.elysium369.meet.mobility.domain.models.Money
import com.elysium369.meet.mobility.domain.models.ServiceCategoryId
import com.elysium369.meet.mobility.domain.models.SignedMoney
import com.elysium369.meet.mobility.domain.payment.PaymentAuthorization
import com.elysium369.meet.mobility.domain.payment.PaymentAuthorizationState
import com.elysium369.meet.mobility.domain.payment.PaymentMethodType
import com.elysium369.meet.mobility.domain.pricing.PricingInput
import com.elysium369.meet.mobility.domain.pricing.PricingMode
import com.elysium369.meet.mobility.domain.pricing.Rate
import com.elysium369.meet.mobility.domain.pricing.RideQuote
import com.elysium369.meet.mobility.domain.pricing.multiply
import com.elysium369.meet.mobility.domain.routing.GeoCoordinate
import com.elysium369.meet.mobility.domain.routing.RoutingProfile
import com.elysium369.meet.mobility.domain.routing.RoutingProvider
import com.elysium369.meet.mobility.domain.routing.RoutingProviderChain
import com.elysium369.meet.mobility.domain.routing.RoutingResult
import com.elysium369.meet.mobility.domain.routing.RoutingUnavailableException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class MobilityFinanceTest {

    private val crc = CurrencyCode.of("CRC")
    private val usd = CurrencyCode.of("USD")

    @Test
    fun rateRationalMathAndMoneyMultiplication() {
        val base = Money(1000L, crc)
        val surge = Rate(3L, 2L) // 1.5x

        val surged = base.multiply(surge)
        assertEquals(1500L, surged.minorUnits)

        // Integer arithmetic: 1000 * 1/3 = 333
        val third = base.multiply(Rate(1L, 3L))
        assertEquals(333L, third.minorUnits)

        // 1000 * 2/3 = 666
        val twoThirds = base.multiply(Rate(2L, 3L))
        assertEquals(666L, twoThirds.minorUnits)

        // Rate validation
        assertThrows(IllegalArgumentException::class.java) {
            Rate(1L, 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Rate(-1L, 2L)
        }
    }

    @Test
    fun upfrontRideQuoteValidatesNonNegativeTotalAndPolicyVersion() {
        val quote = RideQuote(
            quoteId = UUID.randomUUID(),
            rideRequestId = UUID.randomUUID(),
            marketId = MarketId("CR_SJO"),
            serviceCategoryId = ServiceCategoryId("cat_sjo_standard"),
            baseFare = Money(1000L, crc),
            distanceFare = Money(2500L, crc),
            timeFare = Money(500L, crc),
            demandAdjustment = Money(500L, crc),
            tollEstimate = Money(0L, crc),
            taxes = Money(585L, crc),
            discount = Money(0L, crc),
            total = Money(5085L, crc),
            pricingPolicyVersion = 1L,
            expiresAt = Instant.now().plusSeconds(600)
        )

        assertEquals(5085L, quote.total.minorUnits)
        assertEquals("CR_SJO", quote.marketId.value)
        assertEquals("cat_sjo_standard", quote.serviceCategoryId.value)

        // Invalid pricing policy version (< 1) throws
        assertThrows(IllegalArgumentException::class.java) {
            quote.copy(pricingPolicyVersion = 0L)
        }
    }

    @Test
    fun routingProviderChainFallback() {
        val pickup = GeoCoordinate(9.9350, -84.0750)
        val destination = GeoCoordinate(9.9281, -84.0907)

        val failingProvider = object : RoutingProvider {
            override suspend fun route(
                origin: GeoCoordinate,
                stops: List<GeoCoordinate>,
                destination: GeoCoordinate,
                profile: RoutingProfile
            ): RoutingResult = throw RuntimeException("Network timeout")
        }

        val successProvider = object : RoutingProvider {
            override suspend fun route(
                origin: GeoCoordinate,
                stops: List<GeoCoordinate>,
                destination: GeoCoordinate,
                profile: RoutingProfile
            ): RoutingResult = RoutingResult(
                distanceMeters = 5400L,
                durationSeconds = 620L,
                encodedPolyline = "mock_polyline",
                provider = "MockSuccessProvider",
                calculatedAt = Instant.now()
            )
        }

        kotlinx.coroutines.runBlocking {
            val chain = RoutingProviderChain(listOf(failingProvider, successProvider))
            val result = chain.route(pickup, emptyList(), destination, RoutingProfile.DRIVING)

            assertEquals(5400L, result.distanceMeters)
            assertEquals(620L, result.durationSeconds)
            assertEquals("MockSuccessProvider", result.provider)
        }
    }

    @Test
    fun routingProviderChainThrowsWhenAllFail() {
        val pickup = GeoCoordinate(9.9350, -84.0750)
        val destination = GeoCoordinate(9.9281, -84.0907)

        val failingProvider = object : RoutingProvider {
            override suspend fun route(
                origin: GeoCoordinate,
                stops: List<GeoCoordinate>,
                destination: GeoCoordinate,
                profile: RoutingProfile
            ): RoutingResult = throw RuntimeException("Service unavailable")
        }

        kotlinx.coroutines.runBlocking {
            val chain = RoutingProviderChain(listOf(failingProvider))
            assertThrows(RoutingUnavailableException::class.java) {
                kotlinx.coroutines.runBlocking {
                    chain.route(pickup, emptyList(), destination, RoutingProfile.DRIVING)
                }
            }
        }
    }

    @Test
    fun paymentAuthorizationStateLifecycle() {
        val riderId = UUID.randomUUID()
        val auth = PaymentAuthorization(
            authorizationId = UUID.randomUUID(),
            tripId = null,
            riderId = riderId,
            provider = PaymentMethodType.CARD_TOKEN.name,
            providerAuthRef = "auth_tok_12345",
            amount = Money(4500L, crc),
            state = PaymentAuthorizationState.AUTHORIZED,
            createdAt = Instant.now()
        )

        assertEquals(PaymentAuthorizationState.AUTHORIZED, auth.state)
        assertEquals("auth_tok_12345", auth.providerAuthRef)

        val captured = auth.copy(state = PaymentAuthorizationState.CAPTURED, tripId = UUID.randomUUID())
        assertEquals(PaymentAuthorizationState.CAPTURED, captured.state)
    }

    @Test
    fun balancedLedgerTransactionStrictZeroSum() {
        val txId = UUID.randomUUID()
        val riderAcc = UUID.randomUUID()
        val driverAcc = UUID.randomUUID()
        val platformAcc = UUID.randomUUID()
        val taxAcc = UUID.randomUUID()

        // Balanced: +5000 (Rider Receivable) - 4000 (Driver Payable) - 700 (Platform Revenue) - 300 (Tax Escrow) = 0
        val balancedEntries = listOf(
            LedgerEntry(UUID.randomUUID(), riderAcc, SignedMoney(5000L, crc)),
            LedgerEntry(UUID.randomUUID(), driverAcc, SignedMoney(-4000L, crc)),
            LedgerEntry(UUID.randomUUID(), platformAcc, SignedMoney(-700L, crc)),
            LedgerEntry(UUID.randomUUID(), taxAcc, SignedMoney(-300L, crc))
        )

        val tx = LedgerTransaction(
            transactionId = txId,
            referenceType = LedgerReferenceType.TRIP_SETTLEMENT,
            referenceId = UUID.randomUUID(),
            currency = crc,
            entries = balancedEntries,
            createdAt = Instant.now()
        )

        assertEquals(4, tx.entries.size)
        assertEquals(0L, tx.entries.sumOf { it.amount.minorUnits })
    }

    @Test
    fun unbalancedLedgerTransactionThrowsIllegalArgumentException() {
        val txId = UUID.randomUUID()
        val riderAcc = UUID.randomUUID()
        val driverAcc = UUID.randomUUID()

        // Unbalanced: +5000 and -4000 (Sum = +1000 != 0)
        val unbalancedEntries = listOf(
            LedgerEntry(UUID.randomUUID(), riderAcc, SignedMoney(5000L, crc)),
            LedgerEntry(UUID.randomUUID(), driverAcc, SignedMoney(-4000L, crc))
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            LedgerTransaction(
                transactionId = txId,
                referenceType = LedgerReferenceType.TRIP_SETTLEMENT,
                referenceId = UUID.randomUUID(),
                currency = crc,
                entries = unbalancedEntries,
                createdAt = Instant.now()
            )
        }
        assertTrue(ex.message!!.contains("balanced"))
    }

    @Test
    fun tripSettlementComponentCheck() {
        val settlement = TripSettlement(
            settlementId = UUID.randomUUID(),
            tripId = UUID.randomUUID(),
            grossFare = Money(5000L, crc),
            platformFee = Money(750L, crc),
            driverEarnings = Money(3750L, crc),
            tax = Money(500L, crc),
            toll = Money(0L, crc),
            pricingPolicyVersion = 1L,
            ledgerTransactionId = UUID.randomUUID(),
            createdAt = Instant.now()
        )

        assertEquals(5000L, settlement.grossFare.minorUnits)

        // Mismatched gross fare throws
        assertThrows(IllegalArgumentException::class.java) {
            settlement.copy(grossFare = Money(6000L, crc))
        }
    }
}
