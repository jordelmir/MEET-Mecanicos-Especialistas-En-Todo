package com.elysium369.meet.platform

import com.elysium369.meet.core.billing.Capability
import com.elysium369.meet.core.billing.CapabilityDecision
import com.elysium369.meet.core.money.CurrencyCode
import com.elysium369.meet.core.money.Money
import com.elysium369.meet.domain.repair.DiagnosticTruthState
import com.elysium369.meet.domain.repair.RepairIntent
import com.elysium369.meet.domain.repair.RepairIntentState
import com.elysium369.meet.platform.marketos.DistanceUnit
import com.elysium369.meet.platform.marketos.MarketCode
import com.elysium369.meet.platform.marketos.MarketConfig
import com.elysium369.meet.platform.marketos.MarketVertical
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class GlobalPlatformDomainTest {

    @Test
    fun testCapabilityEnumResolution() {
        assertEquals(Capability.ADVANCED_DIAGNOSTICS, Capability.fromKey("ADVANCED_DIAGNOSTICS"))
        assertEquals(Capability.AI_DIAGNOSIS, Capability.fromKey("ai_diagnosis"))
        assertEquals(Capability.MODE_06, Capability.fromKey("mode_06"))
        assertEquals(Capability.PROFESSIONAL_REPORTS, Capability.fromKey("professional_reports"))
        assertEquals(Capability.WORKSHOP_WORKSPACE, Capability.fromKey("workshop_workspace"))
        assertEquals(Capability.SERVICE_LEADS, Capability.fromKey("service_leads"))
        assertEquals(Capability.FLEET_TOOLS, Capability.fromKey("fleet_tools"))
        assertEquals(null, Capability.fromKey("NON_EXISTENT"))

        val decision = CapabilityDecision(
            allowed = true,
            reason = CapabilityDecision.Reason.ENTITLED,
            expiresAt = Instant.now().toString()
        )
        assertTrue(decision.allowed)
        assertEquals(CapabilityDecision.Reason.ENTITLED, decision.reason)
    }

    @Test
    fun testRepairIntentInvariants() {
        val clientId = UUID.randomUUID()
        val vehicleId = UUID.randomUUID()

        val validIntent = RepairIntent(
            intentId = UUID.randomUUID(),
            vehicleId = vehicleId,
            diagnosticSessionId = UUID.randomUUID(),
            clientId = clientId,
            observedDtcs = listOf("P0300", "P0301"),
            reportedSymptoms = listOf("Engine misfire under load"),
            recommendedActionClass = "IGNITION_COIL_REPLACEMENT",
            confidence = 0.92,
            truthState = DiagnosticTruthState.OBSERVED
        )
        assertEquals(RepairIntentState.OPEN, validIntent.state)
        assertEquals(2, validIntent.observedDtcs.size)

        // Empty findings must throw
        assertThrows(IllegalArgumentException::class.java) {
            validIntent.copy(observedDtcs = emptyList(), reportedSymptoms = emptyList())
        }

        // Invalid confidence must throw
        assertThrows(IllegalArgumentException::class.java) {
            validIntent.copy(confidence = 1.5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validIntent.copy(confidence = -0.1)
        }
    }

    @Test
    fun testMarketConfigAndCodeValidation() {
        val crMarket = MarketCode("CR_SJO")
        assertEquals("CR_SJO", crMarket.value)

        assertThrows(IllegalArgumentException::class.java) {
            MarketCode("invalid-lowercase")
        }

        val config = MarketConfig(
            code = crMarket,
            currency = CurrencyCode.CRC,
            defaultLocale = "es-CR",
            timezone = "America/Costa_Rica",
            distanceUnit = DistanceUnit.KILOMETERS,
            enabledVerticals = setOf(MarketVertical.AUTOMOTIVE_REPAIR, MarketVertical.TOW, MarketVertical.RIDE),
            electronicPaymentsEnabled = false,
            paymentProviders = emptySet()
        )
        assertFalse(config.electronicPaymentsEnabled)
        assertEquals(DistanceUnit.KILOMETERS, config.distanceUnit)

        // Electronic payments enabled without providers must fail closed
        assertThrows(IllegalArgumentException::class.java) {
            config.copy(electronicPaymentsEnabled = true, paymentProviders = emptySet())
        }
    }

    @Test
    fun testMoneyInvariantsAndZeroSumBalance() {
        val crc = CurrencyCode.CRC
        val m1 = Money(150000L, crc)
        val m2 = Money(50000L, crc)

        val sum = m1 + m2
        assertEquals(200000L, sum.amountMinor)

        val diff = m1 - m2
        assertEquals(100000L, diff.amountMinor)

        // Cross-currency arithmetic must throw
        val usd = CurrencyCode.USD
        val mUsd = Money(1000L, usd)
        assertThrows(IllegalArgumentException::class.java) {
            m1 + mUsd
        }
    }
}
