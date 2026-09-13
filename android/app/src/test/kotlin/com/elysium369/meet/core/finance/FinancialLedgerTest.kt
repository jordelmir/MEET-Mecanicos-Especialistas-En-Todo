package com.elysium369.meet.core.finance

import com.elysium369.meet.core.identity.ActorCapability
import com.elysium369.meet.core.identity.AnalyticsScope
import com.elysium369.meet.core.identity.ScopeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Mandatory Financial Quality Gate:
 * Verifies that idempotency, out-of-order delivery, reconciliations,
 * and multi-tenant boundaries conform 100% to constitutional principles.
 */
class FinancialLedgerTest {

    @Test
    fun financialTruthGate_DuplicateCaptureAndReconciliationAndRefundAndIsolation() {
        val engine = LedgerProjectionEngine()
        val tripId = "trip-" + UUID.randomUUID().toString()
        val idempotencyKey = "idem-" + UUID.randomUUID().toString()
        val fleetA = "fleet-A-uuid"
        val fleetB = "fleet-B-uuid"
        val driverA = "driver-A-uuid"

        // 1. PAYMENT_CAPTURED event arrives (₡10,000)
        val captureEvent1 = LedgerTransactionEvent(
            eventId = UUID.randomUUID().toString(),
            idempotencyKey = idempotencyKey,
            tripOrJobId = tripId,
            organizationId = fleetA,
            principalId = driverA,
            entryType = LedgerEntryType.PAYMENT_CAPTURED,
            amount = Money.ofCrc(10_000),
            account = LedgerAccount.PASSENGER_RECEIVABLE,
            occurredAtEpochMs = 1_000_000L,
        )
        val recorded1 = engine.recordEvent(captureEvent1)
        assertTrue("Initial payment capture must be accepted", recorded1)

        // 2. PAYMENT_CAPTURED arrives a SECOND time (event bus retry / replay)
        val captureEvent2 = captureEvent1.copy(eventId = UUID.randomUUID().toString())
        val recorded2 = engine.recordEvent(captureEvent2)
        assertFalse("Duplicate idempotency key must be discarded", recorded2)

        // 3. Out-of-order events arrive:
        //    Driver earnings: ₡7,500
        //    Platform fee:    ₡1,500
        //    Tax:               ₡800
        //    Adjustment:        ₡200
        engine.recordEvent(
            LedgerTransactionEvent(
                eventId = UUID.randomUUID().toString(),
                idempotencyKey = "earn-$tripId",
                tripOrJobId = tripId,
                organizationId = fleetA,
                principalId = driverA,
                entryType = LedgerEntryType.DRIVER_EARNING_POSTED,
                amount = Money.ofCrc(7_500),
                account = LedgerAccount.DRIVER_PAYABLE,
                occurredAtEpochMs = 1_000_050L,
            )
        )
        engine.recordEvent(
            LedgerTransactionEvent(
                eventId = UUID.randomUUID().toString(),
                idempotencyKey = "fee-$tripId",
                tripOrJobId = tripId,
                organizationId = fleetA,
                principalId = driverA,
                entryType = LedgerEntryType.PLATFORM_FEE_POSTED,
                amount = Money.ofCrc(1_500),
                account = LedgerAccount.PLATFORM_REVENUE,
                occurredAtEpochMs = 1_000_060L,
            )
        )
        engine.recordEvent(
            LedgerTransactionEvent(
                eventId = UUID.randomUUID().toString(),
                idempotencyKey = "tax-$tripId",
                tripOrJobId = tripId,
                organizationId = fleetA,
                principalId = driverA,
                entryType = LedgerEntryType.TAX_POSTED,
                amount = Money.ofCrc(800),
                account = LedgerAccount.TAX_LIABILITY,
                occurredAtEpochMs = 1_000_070L,
            )
        )
        engine.recordEvent(
            LedgerTransactionEvent(
                eventId = UUID.randomUUID().toString(),
                idempotencyKey = "adj-$tripId",
                tripOrJobId = tripId,
                organizationId = fleetA,
                principalId = driverA,
                entryType = LedgerEntryType.PROMOTION_POSTED,
                amount = Money.ofCrc(200),
                account = LedgerAccount.PLATFORM_REVENUE,
                occurredAtEpochMs = 1_000_080L,
            )
        )

        // 4. Assert financial projection for Fleet A
        val projectionFleetA = engine.projectBreakdown(organizationId = fleetA)
        assertEquals("GMV must be exactly ₡10,000", 10_000L, projectionFleetA.gmv.minorUnits)
        assertEquals("Driver earnings must be ₡7,500", 7_500L, projectionFleetA.driverEarnings.minorUnits)
        assertEquals("Platform fee must be ₡1,500", 1_500L, projectionFleetA.platformFee.minorUnits)
        assertEquals("Taxes must be ₡800", 800L, projectionFleetA.taxes.minorUnits)
        assertEquals("Adjustments must be ₡200", 200L, projectionFleetA.adjustments.minorUnits)
        assertTrue("Earnings + fee + tax + adjustment must balance with GMV exactly", projectionFleetA.isBalanced)
        assertEquals("Net revenue must equal platform fee before refunds", 1_500L, projectionFleetA.netRevenue.minorUnits)

        // 5. Subsequent partial refund arrives (₡500)
        engine.recordEvent(
            LedgerTransactionEvent(
                eventId = UUID.randomUUID().toString(),
                idempotencyKey = "refund-$tripId",
                tripOrJobId = tripId,
                organizationId = fleetA,
                principalId = driverA,
                entryType = LedgerEntryType.REFUND_POSTED,
                amount = Money.ofCrc(500),
                account = LedgerAccount.REFUND_EXPENSE,
                occurredAtEpochMs = 1_000_200L,
            )
        )
        val projectionAfterRefund = engine.projectBreakdown(organizationId = fleetA)
        assertEquals("GMV remains ₡10,000", 10_000L, projectionAfterRefund.gmv.minorUnits)
        assertEquals("Refund is ₡500", 500L, projectionAfterRefund.refunds.minorUnits)
        assertEquals("Net revenue is reduced by refund (₡1,500 - ₡500 = ₡1,000)", 1_000L, projectionAfterRefund.netRevenue.minorUnits)

        // 6. Multi-tenant isolation gate:
        //    A fleet owner of Fleet-B MUST NOT query or see Fleet-A data
        val scopeFleetB = AnalyticsScope(
            principalId = "owner-B",
            organizationId = fleetB,
            scopeType = ScopeType.FLEET,
            capabilities = setOf(ActorCapability.VIEW_ORGANIZATION_ANALYTICS),
        )
        assertFalse("Fleet-B scope must be blocked from accessing Fleet-A data", scopeFleetB.canAccess(targetOrgId = fleetA, targetSubjectId = null))

        val projectionFleetB = engine.projectBreakdown(organizationId = fleetB)
        assertEquals("Fleet-B must see 0 GMV from Fleet-A", 0L, projectionFleetB.gmv.minorUnits)
        assertEquals("Fleet-B must see 0 earnings", 0L, projectionFleetB.driverEarnings.minorUnits)
    }
}
