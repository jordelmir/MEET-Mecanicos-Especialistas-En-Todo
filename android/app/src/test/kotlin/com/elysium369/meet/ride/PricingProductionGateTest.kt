package com.elysium369.meet.ride

import com.elysium369.meet.core.money.CurrencyCode
import com.elysium369.meet.core.money.Money
import com.elysium369.meet.data.local.MeetDatabase
import com.elysium369.meet.ride.domain.DriverEconomicsProjection
import com.elysium369.meet.vehiclelife.costs.DefaultVehicleFinancialLedgerRepository
import com.elysium369.meet.vehiclelife.costs.ExpenseCategory
import com.elysium369.meet.vehiclelife.costs.FinancialEntry
import com.elysium369.meet.vehiclelife.costs.FinancialState
import com.elysium369.meet.vehiclelife.costs.local.VehicleFinancialLedgerEntity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate P0-P14: Pricing Production Gate
 * Executable invariant verification for double-authority elimination,
 * driver economic guarantees, CAS concurrency, and Room ledger durability.
 */
class PricingProductionGateTest {

    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
        File("../$path"),
        File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `Gate P1 - Unified Authority ride_create_request_v4 reads from mobility_pricing_policies`() {
        val v4Sql = projectFile("supabase/migrations/20260923010000_ride_create_request_v4_unified_authority.sql").readText()

        assertTrue("v4 must define ride_create_request_v4", v4Sql.contains("create or replace function public.ride_create_request_v4"))
        assertTrue("v4 must query mobility_pricing_policies", v4Sql.contains("from public.mobility_pricing_policies"))
        assertTrue("v4 must support market_id parameter", v4Sql.contains("p_market_id text default 'CR_GAM'"))
        assertTrue("v4 must support rate card version parameter", v4Sql.contains("p_fare_rate_card_version bigint"))
        assertTrue("v4 must reject estimate mismatch with authoritative policy", v4Sql.contains("FARE_ESTIMATE_MISMATCH"))
    }

    @Test
    fun `Gate P2 - Pilot Rate Card V3 is seeded with 900 base, 350 per km, 80 per min`() {
        val pilotSql = projectFile("supabase/migrations/20260923020000_seed_mobility_pricing_pilot_v3.sql").readText()

        assertTrue("Pilot must seed version 3", pilotSql.contains("3,"))
        assertTrue("Pilot must have 900 colones base fare", pilotSql.contains("900,"))
        assertTrue("Pilot must have 350 per meter numerator", pilotSql.contains("350,"))
        assertTrue("Pilot must have 80 per second numerator", pilotSql.contains("80,"))
        assertTrue("Pilot must have 1200 colones minimum fare", pilotSql.contains("1200,"))
    }

    @Test
    fun `Gate P3 - ride_change_fare_v1 enforces CAS concurrency and metered mode immutability`() {
        val casSql = projectFile("supabase/migrations/20260923030000_ride_change_fare_cas_idempotent.sql").readText()

        assertTrue("Must support expected_version for CAS", casSql.contains("p_expected_version bigint default null"))
        assertTrue("Must fail on stale version conflict", casSql.contains("STALE_VERSION"))
        assertTrue("Must reject manual edits on metered rides", casSql.contains("METERED_MODE_IMMUTABLE"))
        assertTrue("Must enforce minimum fare bounds", casSql.contains("FARE_BELOW_MINIMUM"))
        assertTrue("Must record command receipt", casSql.contains("ride_record_command_receipt"))
    }

    @Test
    fun `Gate P4 - DriverEconomicsProjection guarantees 5 percent commission and net viability floor`() {
        val projection = DriverEconomicsProjection.calculate(
            grossFareMinor = 15_000L,
            tripDistanceMeters = 12_000L,
            tripDurationSeconds = 1_800L,
            pickupDeadheadDistanceMeters = 1_000L,
            pickupDeadheadDurationSeconds = 180L,
            fuelCostPerKmMinor = 65L,
            commissionBps = 500L, // 5%
        )

        // Platform fee is strictly 5% (750 colones)
        assertEquals(750L, projection.platformFeeMinor)
        assertEquals(14_250L, projection.grossDriverPayoutMinor)

        // Total distance: 13 km -> fuel = 13 * 65 = 845 colones
        assertEquals(845L, projection.estimatedFuelExpenseMinor)

        // Net earnings = 14,250 - 845 = 13,405
        assertEquals(13_405L, projection.projectedNetEarningsMinor)
        assertTrue(projection.isViable)
    }

    @Test
    fun `Gate P5 - Room Database is at version 82 with VehicleFinancialLedgerEntity and MIGRATION_81_82`() {
        val dbFile = projectFile("android/app/src/main/kotlin/com/elysium369/meet/data/local/MeetDatabase.kt").readText()
        val appModuleFile = projectFile("android/app/src/main/kotlin/com/elysium369/meet/di/AppModule.kt").readText()

        assertTrue("MeetDatabase must be version 82", dbFile.contains("version = 82"))
        assertTrue("MeetDatabase must include VehicleFinancialLedgerEntity", dbFile.contains("VehicleFinancialLedgerEntity::class"))
        assertTrue("AppModule must declare MIGRATION_81_82", appModuleFile.contains("val MIGRATION_81_82 = object : Migration(81, 82)"))
        assertTrue("AppModule must register MIGRATION_81_82", appModuleFile.contains("MIGRATION_81_82,"))
    }

    @Test
    fun `Gate P6 - Financial Ledger preserves TCO cash accounting truth`() = runBlocking {
        val repo = DefaultVehicleFinancialLedgerRepository()

        repo.recordEntry(
            FinancialEntry(
                entryId = "P1",
                vehicleId = "V-TEST",
                category = ExpenseCategory.FUEL,
                state = FinancialState.PAID,
                amount = Money(4_000L, CurrencyCode.CRC),
                description = "Gasolina Super",
            ),
        )

        repo.recordEntry(
            FinancialEntry(
                entryId = "Q1",
                vehicleId = "V-TEST",
                category = ExpenseCategory.MAINTENANCE,
                state = FinancialState.QUOTED, // Unapproved quote must not count as cash paid
                amount = Money(50_000L, CurrencyCode.CRC),
                description = "Cotización Taller",
            ),
        )

        val tco = repo.calculateTco("V-TEST", totalKmDriven = 100, targetCurrency = CurrencyCode.CRC)

        assertEquals("Paid TCO must only sum disbursed cash", 4_000L, tco.totalPaid.amountMinor)
        assertEquals("Quoted must be recorded separately", 50_000L, tco.totalQuotedPending.amountMinor)
        assertEquals("Cost per km is 40 CRC/km", 40L, tco.costPerKm?.amountMinor)
    }
}
