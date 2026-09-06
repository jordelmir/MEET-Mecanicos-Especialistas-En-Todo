package com.elysium369.meet.provider

import com.elysium369.meet.provider.domain.models.ProviderBalance
import com.elysium369.meet.provider.domain.models.ProviderCapability
import com.elysium369.meet.provider.domain.models.ProviderContext
import com.elysium369.meet.provider.domain.models.ProviderNotification
import com.elysium369.meet.provider.domain.models.ProviderNotificationCategory
import com.elysium369.meet.provider.domain.models.ProviderOperationalState
import com.elysium369.meet.provider.domain.models.ProviderPerformance
import com.elysium369.meet.provider.domain.models.ProviderPreferences
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDomainModelsTest {

    @Test
    fun providerCapabilityEnumValues() {
        assertEquals(ProviderCapability.RIDE_DRIVER, ProviderCapability.fromString("RIDE_DRIVER"))
        assertEquals(ProviderCapability.DELIVERY_COURIER, ProviderCapability.fromString("delivery_courier"))
        assertThrows(IllegalArgumentException::class.java) {
            ProviderCapability.fromString("SPACESHIP_PILOT")
        }
    }

    @Test
    fun providerOperationalStateLogic() {
        assertTrue(ProviderOperationalState.ONLINE_STANDBY.isAvailableForWork)
        assertFalse(ProviderOperationalState.OFFLINE.isAvailableForWork)
        assertFalse(ProviderOperationalState.BUSY_DISPATCH.isAvailableForWork)

        assertTrue(ProviderOperationalState.BUSY_DISPATCH.isActivelyEngaged)
        assertTrue(ProviderOperationalState.BUSY_ENGAGED.isActivelyEngaged)
        assertFalse(ProviderOperationalState.ONLINE_STANDBY.isActivelyEngaged)

        assertTrue(ProviderOperationalState.SUSPENDED_SAFETY.isBlocked)
        assertTrue(ProviderOperationalState.RESTRICTED_DOCUMENTS.isBlocked)
        assertFalse(ProviderOperationalState.OFFLINE.isBlocked)
    }

    @Test
    fun providerContextValidation() {
        val ctx = ProviderContext(
            providerId = UUID.randomUUID(),
            capability = ProviderCapability.RIDE_DRIVER,
            operationalState = ProviderOperationalState.ONLINE_STANDBY,
            currentWorkId = null,
            lastStatusChangeAt = Instant.now(),
            deviceBatteryPct = 85,
            networkClass = "CELLULAR_5G",
            appVersion = "1.0.0",
            telemetrySessionId = UUID.randomUUID(),
        )
        assertEquals(85, ctx.deviceBatteryPct)

        assertThrows(IllegalArgumentException::class.java) {
            ctx.copy(deviceBatteryPct = 105)
        }

        assertThrows(IllegalArgumentException::class.java) {
            ctx.copy(appVersion = "  ")
        }
    }

    @Test
    fun providerPerformanceValidation() {
        val perf = ProviderPerformance(
            ratingAverage = 4.95,
            totalRatingsCount = 120,
            acceptanceRatePct = 94.5,
            cancellationRatePct = 1.2,
            totalCompletedOrders = 340,
            trustTier = "GOLD",
        )
        assertEquals(4.95, perf.ratingAverage, 0.001)

        assertThrows(IllegalArgumentException::class.java) {
            perf.copy(ratingAverage = 5.5)
        }

        assertThrows(IllegalArgumentException::class.java) {
            perf.copy(acceptanceRatePct = -1.0)
        }
    }

    @Test
    fun providerBalanceValidation() {
        val bal = ProviderBalance(
            currencyCode = "MXN",
            withdrawableMinor = 50000L,
            pendingMinor = 12000L,
            lockedMinor = 0L,
            lastPayoutMinor = 25000L,
            lastPayoutAt = Instant.now(),
            totalSettledMinor = 87000L,
        )
        assertEquals("MXN", bal.currencyCode)

        assertThrows(IllegalArgumentException::class.java) {
            bal.copy(currencyCode = "invalid")
        }

        assertThrows(IllegalArgumentException::class.java) {
            bal.copy(withdrawableMinor = -100L)
        }
    }

    @Test
    fun providerPreferencesValidation() {
        val prefs = ProviderPreferences()
        assertTrue(prefs.maxRadiusKm in 1.0..500.0)

        assertThrows(IllegalArgumentException::class.java) {
            prefs.copy(maxRadiusKm = 0.5)
        }
    }

    @Test
    fun providerNotificationValidation() {
        val notif = ProviderNotification(
            notificationId = UUID.randomUUID(),
            category = ProviderNotificationCategory.SAFETY_ALERT,
            title = "Speed Alert",
            body = "Excessive speed detected on highway",
            deepLinkUri = "meet://safety/incident/123",
            isRead = false,
            createdAt = Instant.now(),
        )
        assertEquals(ProviderNotificationCategory.SAFETY_ALERT, notif.category)

        assertThrows(IllegalArgumentException::class.java) {
            notif.copy(title = " ")
        }
    }
}
