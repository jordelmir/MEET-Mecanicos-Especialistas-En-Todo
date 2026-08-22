package com.elysium369.meet.vehiclelife.costs

import com.elysium369.meet.core.domain.EntityRef
import com.elysium369.meet.core.money.CurrencyCode
import com.elysium369.meet.core.money.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ExpenseCategory(val displayName: String, val glyph: String) {
    FUEL("Combustible / Carga", "⛽"),
    MAINTENANCE("Mantenimiento Preventivo", "🔧"),
    PART("Repuestos y Piezas", "📦"),
    LABOR("Mano de Obra Mecánica", "🛠️"),
    INSURANCE("Seguro Vehicular", "🛡️"),
    INSPECTION("Inspección Técnica / RTV", "🔍"),
    TOLL("Peajes de Carretera", "🛣️"),
    PARKING("Estacionamiento", "🅿️"),
    TAX("Impuestos / Marchamo", "📑"),
    FINE("Multas de Tránsito", "⚠️"),
    OTHER("Otros Gastos", "💰")
}

enum class FinancialState(val label: String) {
    ESTIMATED("Estimado / Preliminar"),
    QUOTED("Cotizado por Proveedor"),
    AUTHORIZED("Autorizado por Propietario"),
    INVOICED("Facturado"),
    PAID("Pagado / Liquidado"),
    REFUNDED("Reembolsado")
}

data class FinancialEntry(
    val entryId: String,
    val vehicleId: String,
    val category: ExpenseCategory,
    val state: FinancialState,
    val amount: Money,
    val description: String,
    val dateUtc: Long = System.currentTimeMillis(),
    val invoiceRef: EntityRef.DocumentRef? = null,
    val odometerKmAtExpense: Int? = null
)

data class TcoMetrics(
    val totalPaid: Money,
    val totalQuotedPending: Money,
    val costPerKm: Money?,
    val monthlyAverage: Money
)

interface VehicleFinancialLedgerRepository {
    val entries: StateFlow<List<FinancialEntry>>
    suspend fun recordEntry(entry: FinancialEntry)
    suspend fun getEntriesForVehicle(vehicleId: String): List<FinancialEntry>
    suspend fun calculateTco(vehicleId: String, totalKmDriven: Int?, targetCurrency: CurrencyCode? = null): TcoMetrics
}

@Singleton
class DefaultVehicleFinancialLedgerRepository @Inject constructor() : VehicleFinancialLedgerRepository {
    private val _entries = MutableStateFlow<List<FinancialEntry>>(emptyList())
    override val entries: StateFlow<List<FinancialEntry>> = _entries.asStateFlow()

    override suspend fun recordEntry(entry: FinancialEntry) {
        _entries.value = listOf(entry) + _entries.value.filter { it.entryId != entry.entryId }
    }

    override suspend fun getEntriesForVehicle(vehicleId: String): List<FinancialEntry> {
        return _entries.value.filter { it.vehicleId == vehicleId }
    }

    override suspend fun calculateTco(vehicleId: String, totalKmDriven: Int?, targetCurrency: CurrencyCode?): TcoMetrics {
        val vehicleEntries = getEntriesForVehicle(vehicleId)
        val defaultCurrency = targetCurrency ?: vehicleEntries.firstOrNull()?.amount?.currency ?: CurrencyCode.USD

        // Strict financial truth: ONLY FinancialState.PAID represents disbursed money. INVOICED is pending.
        val paidEntries = vehicleEntries.filter { it.state == FinancialState.PAID && it.amount.currency == defaultCurrency }
        val quotedEntries = vehicleEntries.filter { 
            (it.state == FinancialState.QUOTED || it.state == FinancialState.AUTHORIZED || it.state == FinancialState.INVOICED) &&
            it.amount.currency == defaultCurrency
        }

        val totalPaidAmountMinor = paidEntries.fold(0L) { acc, entry -> Math.addExact(acc, entry.amount.amountMinor) }
        val totalQuotedAmountMinor = quotedEntries.fold(0L) { acc, entry -> Math.addExact(acc, entry.amount.amountMinor) }

        val totalPaid = Money(totalPaidAmountMinor, defaultCurrency)
        val totalQuoted = Money(totalQuotedAmountMinor, defaultCurrency)

        val costPerKm = if (totalKmDriven != null && totalKmDriven > 0 && totalPaidAmountMinor > 0) {
            Money(totalPaidAmountMinor / totalKmDriven, defaultCurrency)
        } else null

        // Observation window calculation based on recorded timestamps
        val observationMonths = if (paidEntries.size >= 2) {
            val minDate = paidEntries.minOf { it.dateUtc }
            val maxDate = paidEntries.maxOf { it.dateUtc }
            val spanMs = maxDate - minDate
            val months = (spanMs / (30L * 24 * 60 * 60 * 1000L)).toInt()
            months.coerceAtLeast(1)
        } else {
            1
        }

        val monthlyAverage = Money(totalPaidAmountMinor / observationMonths, defaultCurrency)

        return TcoMetrics(
            totalPaid = totalPaid,
            totalQuotedPending = totalQuoted,
            costPerKm = costPerKm,
            monthlyAverage = monthlyAverage
        )
    }
}
