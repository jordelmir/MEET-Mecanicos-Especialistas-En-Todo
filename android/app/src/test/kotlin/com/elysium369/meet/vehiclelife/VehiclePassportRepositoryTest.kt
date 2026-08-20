package com.elysium369.meet.vehiclelife

import com.elysium369.meet.core.domain.SourceAuthority
import com.elysium369.meet.core.domain.VehicleContext
import com.elysium369.meet.core.vehiclelife.VehicleLifeActor
import com.elysium369.meet.core.vehiclelife.VehicleLifeEvent
import com.elysium369.meet.core.vehiclelife.VehicleLifeEventType
import com.elysium369.meet.vehiclelife.passport.DefaultVehiclePassportRepository
import com.elysium369.meet.vehiclelife.timeline.DefaultVehicleTimelineRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VehiclePassportRepositoryTest {

    @Test
    fun testBuildPassportAggregatesVerifiedEventsAndComputesSha256() = runBlocking {
        val timelineRepo = DefaultVehicleTimelineRepository()
        val passportRepo = DefaultVehiclePassportRepository(timelineRepo)

        val vehicleContext = VehicleContext(
            vehicleId = "V-001",
            ownerPrincipalId = "USR-01",
            vin = "KMHCF41BP5U123456",
            make = "Hyundai",
            model = "Accent",
            year = 2005
        )

        timelineRepo.recordEvent(
            VehicleLifeEvent(
                eventId = "EVT_REP_01",
                vehicleId = "V-001",
                ownerPrincipalId = "USR-01",
                type = VehicleLifeEventType.REPAIR,
                occurredAtUtc = System.currentTimeMillis() - 100000,
                actor = VehicleLifeActor("MECH-01", "Mecánico", "Carlos M."),
                title = "Cambio de Bomba de Combustible",
                summary = "Reemplazo con pieza OEM verificada",
                source = SourceAuthority.SERVICE_PROVIDER,
                isVerified = true
            )
        )

        val passport = passportRepo.buildPassport(vehicleContext, healthScore = 92, activeDtcsCount = 0)

        assertEquals("V-001", passport.vehicleId)
        assertEquals(92, passport.health.overallScorePercent)
        assertEquals(1, passport.history.verifiedRepairsCount)
        assertEquals(1, passport.history.totalRecordedEvents)
        assertEquals(64, passport.integrity.passportHashSha256.length) // Valid SHA-256 hex string
        assertTrue(passport.integrity.verifierUrl.contains(passport.integrity.passportHashSha256))
    }
}
