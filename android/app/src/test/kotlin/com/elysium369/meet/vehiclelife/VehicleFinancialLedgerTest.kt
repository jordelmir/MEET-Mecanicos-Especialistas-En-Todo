package com.elysium369.meet.vehiclelife

import com.elysium369.meet.core.money.CurrencyCode
import com.elysium369.meet.core.money.Money
import com.elysium369.meet.vehiclelife.costs.DefaultVehicleFinancialLedgerRepository
import com.elysium369.meet.vehiclelife.costs.ExpenseCategory
import com.elysium369.meet.vehiclelife.costs.FinancialEntry
import com.elysium369.meet.vehiclelife.costs.FinancialState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VehicleFinancialLedgerTest {

    @Test
    fun testTcoLedgerCalculation() = runBlocking {
        val repo = DefaultVehicleFinancialLedgerRepository()

        // 1. Paid fuel expense
        repo.recordEntry(
            FinancialEntry(
                entryId = "EXP_01",
                vehicleId = "V-001",
                category = ExpenseCategory.FUEL,
                state = FinancialState.PAID,
                amount = Money(5000L, CurrencyCode.USD), // $50.00
                description = "Gasolina Regular"
            )
        )

        // 2. Paid maintenance repair
        repo.recordEntry(
            FinancialEntry(
                entryId = "EXP_02",
                vehicleId = "V-001",
                category = ExpenseCategory.MAINTENANCE,
                state = FinancialState.PAID,
                amount = Money(15000L, CurrencyCode.USD), // $150.00
                description = "Cambio de Aceite Sintético y Filtro"
            )
        )

        // 3. Quoted unapproved repair (must NOT contaminate totalPaid)
        repo.recordEntry(
            FinancialEntry(
                entryId = "QUOTE_01",
                vehicleId = "V-001",
                category = ExpenseCategory.PART,
                state = FinancialState.QUOTED,
                amount = Money(30000L, CurrencyCode.USD), // $300.00
                description = "Cotización Amortiguadores"
            )
        )

        val tco = repo.calculateTco("V-001", totalKmDriven = 1000)

        // Paid should only sum EXP_01 + EXP_02 = $200.00 (20000 minor)
        assertEquals(20000L, tco.totalPaid.amountMinor)
        assertEquals("$200.00", tco.totalPaid.formatted())

        // Quoted should be $300.00 (30000 minor)
        assertEquals(30000L, tco.totalQuotedPending.amountMinor)
        assertEquals("$300.00", tco.totalQuotedPending.formatted())

        // Cost per km = $200.00 / 1000km = $0.20 per km (20 minor)
        assertNotNull(tco.costPerKm)
        assertEquals(20L, tco.costPerKm?.amountMinor)
        assertEquals("$0.20", tco.costPerKm?.formatted())
    }
}
