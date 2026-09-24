package com.elysium369.meet.vehiclelife.costs.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.elysium369.meet.core.domain.EntityRef
import com.elysium369.meet.core.money.CurrencyCode
import com.elysium369.meet.core.money.Money
import com.elysium369.meet.vehiclelife.costs.ExpenseCategory
import com.elysium369.meet.vehiclelife.costs.FinancialEntry
import com.elysium369.meet.vehiclelife.costs.FinancialState

@Entity(
    tableName = "vehicle_financial_ledger",
    indices = [
        Index("vehicleId"),
        Index("category"),
        Index("dateUtc"),
        Index("state"),
    ],
)
data class VehicleFinancialLedgerEntity(
    @PrimaryKey
    val entryId: String,
    val vehicleId: String,
    val category: String,
    val state: String,
    val amountMinor: Long,
    val currency: String,
    val description: String,
    val dateUtc: Long,
    val invoiceRefDocId: String? = null,
    val odometerKmAtExpense: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toDomain(): FinancialEntry = FinancialEntry(
        entryId = entryId,
        vehicleId = vehicleId,
        category = runCatching { ExpenseCategory.valueOf(category) }.getOrDefault(ExpenseCategory.OTHER),
        state = runCatching { FinancialState.valueOf(state) }.getOrDefault(FinancialState.ESTIMATED),
        amount = Money(amountMinor, CurrencyCode.fromString(currency)),
        description = description,
        dateUtc = dateUtc,
        invoiceRef = invoiceRefDocId?.let { EntityRef.DocumentRef(it) },
        odometerKmAtExpense = odometerKmAtExpense,
    )

    companion object {
        fun fromDomain(entry: FinancialEntry): VehicleFinancialLedgerEntity = VehicleFinancialLedgerEntity(
            entryId = entry.entryId,
            vehicleId = entry.vehicleId,
            category = entry.category.name,
            state = entry.state.name,
            amountMinor = entry.amount.amountMinor,
            currency = entry.amount.currency.name,
            description = entry.description,
            dateUtc = entry.dateUtc,
            invoiceRefDocId = entry.invoiceRef?.id,
            odometerKmAtExpense = entry.odometerKmAtExpense,
            createdAt = entry.dateUtc,
        )
    }
}
