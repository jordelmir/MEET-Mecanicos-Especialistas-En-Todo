package com.elysium369.meet.mobility

import com.elysium369.meet.mobility.data.gateway.MobilityRetryPolicy
import com.elysium369.meet.mobility.domain.ledger.LedgerAccountType
import com.elysium369.meet.mobility.domain.ledger.LedgerEntry
import com.elysium369.meet.mobility.domain.ledger.LedgerReferenceType
import com.elysium369.meet.mobility.domain.ledger.LedgerTransaction
import com.elysium369.meet.mobility.domain.ledger.TripSettlement
import com.elysium369.meet.mobility.domain.models.CurrencyCode
import com.elysium369.meet.mobility.domain.models.Money
import com.elysium369.meet.mobility.domain.models.SignedMoney
import com.elysium369.meet.mobility.domain.pricing.FinancialRounding
import com.elysium369.meet.mobility.domain.pricing.Rate
import com.elysium369.meet.mobility.domain.pricing.multiply
import com.elysium369.meet.mobility.domain.realtime.AggregateVersionGate
import com.elysium369.meet.mobility.domain.realtime.VersionDecision
import com.elysium369.meet.mobility.domain.result.GatewayFailure
import com.elysium369.meet.mobility.domain.result.MobilityFailureClassifier
import com.elysium369.meet.mobility.domain.routing.GeoCoordinate
import com.elysium369.meet.mobility.domain.routing.RoutingProfile
import com.elysium369.meet.mobility.domain.routing.RoutingProvider
import com.elysium369.meet.mobility.domain.routing.RoutingProviderChain
import com.elysium369.meet.mobility.domain.routing.RoutingProviderFailure
import com.elysium369.meet.mobility.domain.routing.RoutingResult
import com.elysium369.meet.mobility.domain.routing.RoutingUnavailableException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MobilityV7P0HardeningTest {

    private val crc = CurrencyCode.of("CRC")
    private val usd = CurrencyCode.of("USD")

    @Test
    fun ledgerTransactionStrictlyRequiresAtLeastTwoEntries() {
        val entry = LedgerEntry(
            entryId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            amount = SignedMoney(0L, crc),
        )
        assertThrows(IllegalArgumentException::class.java) {
            LedgerTransaction(
                transactionId = UUID.randomUUID(),
                referenceType = LedgerReferenceType.TRIP_SETTLEMENT,
                referenceId = UUID.randomUUID(),
                currency = crc,
                entries = listOf(entry),
                createdAt = Instant.now(),
            )
        }
    }

    @Test
    fun ledgerTransactionRejectsUnbalancedEntries() {
        val acc1 = UUID.randomUUID()
        val acc2 = UUID.randomUUID()
        val entries = listOf(
            LedgerEntry(UUID.randomUUID(), acc1, SignedMoney(1000L, crc)),
            LedgerEntry(UUID.randomUUID(), acc2, SignedMoney(-999L, crc)),
        )
        assertThrows(IllegalArgumentException::class.java) {
            LedgerTransaction(
                transactionId = UUID.randomUUID(),
                referenceType = LedgerReferenceType.TRIP_SETTLEMENT,
                referenceId = UUID.randomUUID(),
                currency = crc,
                entries = entries,
                createdAt = Instant.now(),
            )
        }
    }

    @Test
    fun ledgerTransactionRequiresAtLeastOneDebitAndOneCredit() {
        val acc1 = UUID.randomUUID()
        val acc2 = UUID.randomUUID()
        val zeroEntries = listOf(
            LedgerEntry(UUID.randomUUID(), acc1, SignedMoney(0L, crc)),
            LedgerEntry(UUID.randomUUID(), acc2, SignedMoney(0L, crc)),
        )
        assertThrows(IllegalArgumentException::class.java) {
            LedgerTransaction(
                transactionId = UUID.randomUUID(),
                referenceType = LedgerReferenceType.TRIP_SETTLEMENT,
                referenceId = UUID.randomUUID(),
                currency = crc,
                entries = zeroEntries,
                createdAt = Instant.now(),
            )
        }
    }

    @Test
    fun ledgerTransactionAcceptsStrictlyBalancedTransaction() {
        val acc1 = UUID.randomUUID()
        val acc2 = UUID.randomUUID()
        val entries = listOf(
            LedgerEntry(UUID.randomUUID(), acc1, SignedMoney(5000L, crc)),
            LedgerEntry(UUID.randomUUID(), acc2, SignedMoney(-5000L, crc)),
        )
        val tx = LedgerTransaction(
            transactionId = UUID.randomUUID(),
            referenceType = LedgerReferenceType.TRIP_SETTLEMENT,
            referenceId = UUID.randomUUID(),
            currency = crc,
            entries = entries,
            createdAt = Instant.now(),
        )
        assertEquals(2, tx.entries.size)
    }

    @Test
    fun tripSettlementStrictlyEnforcesGrossEqualsSumOfComponents() {
        val gross = Money(10_000L, crc)
        val fee = Money(1_500L, crc)
        val driver = Money(7_200L, crc)
        val tax = Money(1_300L, crc)
        val toll = Money(0L, crc)

        val settlement = TripSettlement(
            settlementId = UUID.randomUUID(),
            tripId = UUID.randomUUID(),
            grossFare = gross,
            platformFee = fee,
            driverEarnings = driver,
            tax = tax,
            toll = toll,
            pricingPolicyVersion = 1L,
            ledgerTransactionId = UUID.randomUUID(),
            createdAt = Instant.now(),
        )
        assertEquals(10_000L, settlement.grossFare.minorUnits)

        // Invalid: components sum to 9,999L
        assertThrows(IllegalArgumentException::class.java) {
            TripSettlement(
                settlementId = UUID.randomUUID(),
                tripId = UUID.randomUUID(),
                grossFare = gross,
                platformFee = fee,
                driverEarnings = Money(7_199L, crc),
                tax = tax,
                toll = toll,
                pricingPolicyVersion = 1L,
                ledgerTransactionId = UUID.randomUUID(),
                createdAt = Instant.now(),
            )
        }
    }

    @Test
    fun financialRoundingModesProduceExactResults() {
        val money = Money(1000L, crc) // 1000 * 2/3 = 666.666...
        val twoThirds = Rate(2L, 3L)

        val down = money.multiply(twoThirds, FinancialRounding.DOWN)
        assertEquals(666L, down.minorUnits)

        val halfUp = money.multiply(twoThirds, FinancialRounding.HALF_UP)
        assertEquals(667L, halfUp.minorUnits)

        // 1000 * 1/4 = 250 exact
        val quarter = money.multiply(Rate(1L, 4L), FinancialRounding.HALF_EVEN)
        assertEquals(250L, quarter.minorUnits)

        // Half even rounding: 1.5 -> 2 (even), 2.5 -> 2 (even)
        val threeHalves = Rate(3L, 2L) // 1 * 3/2 = 1.5
        val oneUnit = Money(1L, crc)
        assertEquals(2L, oneUnit.multiply(threeHalves, FinancialRounding.HALF_EVEN).minorUnits)

        val fiveHalves = Rate(5L, 2L) // 1 * 5/2 = 2.5
        assertEquals(2L, oneUnit.multiply(fiveHalves, FinancialRounding.HALF_EVEN).minorUnits)
    }

    @Test
    fun routingProviderChainNeverSwallowsCancellationException() {
        val first = object : RoutingProvider {
            override suspend fun route(
                origin: GeoCoordinate,
                stops: List<GeoCoordinate>,
                destination: GeoCoordinate,
                profile: RoutingProfile,
            ): RoutingResult {
                throw CancellationException("Scope cancelled")
            }
        }

        var secondCalled = false
        val second = object : RoutingProvider {
            override suspend fun route(
                origin: GeoCoordinate,
                stops: List<GeoCoordinate>,
                destination: GeoCoordinate,
                profile: RoutingProfile,
            ): RoutingResult {
                secondCalled = true
                error("Second provider must never be called on cancellation")
            }
        }

        val chain = RoutingProviderChain(listOf(first, second))

        assertThrows(CancellationException::class.java) {
            runBlocking {
                chain.route(
                    origin = GeoCoordinate(9.93, -84.08),
                    stops = emptyList(),
                    destination = GeoCoordinate(9.94, -84.09),
                )
            }
        }
        assertFalse(secondCalled)
    }

    @Test
    fun routingProviderChainFailsImmediatelyOnNonFailoverError() {
        val unauthorizedProvider = object : RoutingProvider {
            override suspend fun route(
                origin: GeoCoordinate,
                stops: List<GeoCoordinate>,
                destination: GeoCoordinate,
                profile: RoutingProfile,
            ): RoutingResult {
                throw RoutingProviderFailure.Unauthorized()
            }
        }

        var secondCalled = false
        val second = object : RoutingProvider {
            override suspend fun route(
                origin: GeoCoordinate,
                stops: List<GeoCoordinate>,
                destination: GeoCoordinate,
                profile: RoutingProfile,
            ): RoutingResult {
                secondCalled = true
                throw RoutingProviderFailure.Network(IOException("fail"))
            }
        }

        val chain = RoutingProviderChain(listOf(unauthorizedProvider, second))

        assertThrows(RoutingProviderFailure.Unauthorized::class.java) {
            runBlocking {
                chain.route(
                    origin = GeoCoordinate(9.93, -84.08),
                    stops = emptyList(),
                    destination = GeoCoordinate(9.94, -84.09),
                )
            }
        }
        assertFalse(secondCalled)
    }

    @Test
    fun mobilityFailureClassifierCorrectlyTaxonomizesExceptions() {
        assertTrue(MobilityFailureClassifier.classify(SocketTimeoutException("timeout")) is GatewayFailure.Retryable)
        assertTrue(MobilityFailureClassifier.classify(ConnectException("refused")) is GatewayFailure.Retryable)
        assertTrue(MobilityFailureClassifier.classify(UnknownHostException("no dns")) is GatewayFailure.Retryable)
        assertTrue(MobilityFailureClassifier.classify(SerializationException("bad json")) is GatewayFailure.Protocol)
        assertTrue(MobilityFailureClassifier.classify(IllegalStateException("terminal")) is GatewayFailure.Terminal)

        assertThrows(CancellationException::class.java) {
            MobilityFailureClassifier.classify(CancellationException("cancelled"))
        }
    }

    @Test
    fun aggregateVersionGateEvaluatesProperDecisions() {
        val gate = AggregateVersionGate()

        assertEquals(VersionDecision.Apply, gate.evaluate(current = 5L, incoming = 6L))
        assertEquals(VersionDecision.Ignore, gate.evaluate(current = 5L, incoming = 5L))
        assertEquals(VersionDecision.Ignore, gate.evaluate(current = 5L, incoming = 4L))
        assertEquals(VersionDecision.Resync, gate.evaluate(current = 5L, incoming = 7L))
        assertEquals(VersionDecision.Resync, gate.evaluate(current = 5L, incoming = 100L))
    }

    @Test
    fun mobilityRetryPolicyBoundsAndJitter() {
        val policy = MobilityRetryPolicy(baseDelayMs = 1000L, maxDelayMs = 60_000L, maxAttempts = 5)

        assertTrue(policy.canRetry(0))
        assertTrue(policy.canRetry(4))
        assertFalse(policy.canRetry(5))

        // Delays are non-negative and bounded
        for (attempt in 0..10) {
            val delay = policy.delayMillis(attempt)
            assertTrue(delay in 0L..60_000L)
        }

        // Explicit retryAfterMillis is coerced within bounds
        assertEquals(5_000L, policy.delayMillis(1, retryAfterMillis = 5_000L))
        assertEquals(60_000L, policy.delayMillis(1, retryAfterMillis = 120_000L))
    }
}
