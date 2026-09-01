package com.elysium369.meet.fuel.domain

import com.elysium369.meet.data.local.dao.FuelLedgerDao
import com.elysium369.meet.data.local.entities.FuelTransactionEntity
import com.elysium369.meet.identity.ActivePrincipalKernel
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.flatMapLatest

enum class FuelTransactionTruth { DECLARED, RECEIPT_CAPTURED, SERVER_CONFIRMED }

data class FuelConsumptionResult(
    val litersPer100Km: BigDecimal?,
    val truthState: String,
)

object FuelConsumptionPolicy {
    fun calculate(volumeMilliLiters: Long, distanceMeters: Long?): FuelConsumptionResult {
        if (volumeMilliLiters <= 0L || distanceMeters == null || distanceMeters <= 0L) {
            return FuelConsumptionResult(null, "INSUFFICIENT_EVIDENCE")
        }
        val liters = BigDecimal(volumeMilliLiters).movePointLeft(3)
        val kilometers = BigDecimal(distanceMeters).movePointLeft(3)
        return FuelConsumptionResult(
            liters.divide(kilometers, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100))
                .setScale(2, RoundingMode.HALF_UP),
            "CALCULATED_FROM_RECORDED_DISTANCE",
        )
    }
}

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FuelLedger @Inject constructor(
    private val dao: FuelLedgerDao,
    private val principalKernel: ActivePrincipalKernel,
) {
    val transactions = principalKernel.activePrincipal.flatMapLatest { principal ->
        dao.observeTransactions(principal.id)
    }
    val confirmedRewards = principalKernel.activePrincipal.flatMapLatest { principal ->
        dao.observeConfirmedRewards(principal.id)
    }

    suspend fun recordDeclaredPurchase(
        amountMinor: Long,
        currency: String,
        volumeMilliLiters: Long,
        vehicleId: String?,
        stationId: String?,
        fuelType: String = "UNKNOWN",
        odometerMeters: Long? = null,
        occurredAtEpochMs: Long = System.currentTimeMillis(),
    ): String {
        require(amountMinor > 0L) { "FUEL_AMOUNT" }
        require(volumeMilliLiters > 0L) { "FUEL_VOLUME" }
        Currency.getInstance(currency)
        require(odometerMeters == null || odometerMeters > 0L) { "FUEL_ODOMETER" }
        require(fuelType.matches(Regex("[A-Z0-9_]{3,40}"))) { "FUEL_TYPE" }
        val owner = principalKernel.current()
        val id = UUID.randomUUID().toString()
        val currentOdometer = odometerMeters
        val previousOdometer = if (vehicleId != null && currentOdometer != null) {
            dao.latestTransactionWithOdometer(owner.id, vehicleId, occurredAtEpochMs)?.odometerMeters
        } else {
            null
        }
        val distance = previousOdometer?.let { previous ->
            (requireNotNull(currentOdometer) - previous).takeIf { it > 0L }
        }
        dao.insertTransaction(
            FuelTransactionEntity(
                transactionId = id,
                ownerPrincipalId = owner.id,
                vehicleId = vehicleId,
                stationId = stationId?.trim()?.takeIf(String::isNotBlank),
                fuelType = fuelType,
                odometerMeters = odometerMeters,
                distanceSincePreviousMeters = distance,
                amountMinor = amountMinor,
                currency = currency,
                volumeMilliLiters = volumeMilliLiters,
                pricePerLiterMinor = amountMinor.multiplyExactOrNull(1_000L)?.div(volumeMilliLiters),
                source = "USER_ENTRY",
                truthState = FuelTransactionTruth.DECLARED.name,
                occurredAtEpochMs = occurredAtEpochMs,
                createdAtEpochMs = System.currentTimeMillis(),
                syncState = if (owner.canSyncToCloud) "PENDING" else "LOCAL_ONLY",
            ),
        )
        // A local purchase never mints rewards. Only confirmed server ledger
        // entries can affect the displayed reward balance.
        return id
    }

    private fun Long.multiplyExactOrNull(other: Long): Long? =
        runCatching { Math.multiplyExact(this, other) }.getOrNull()
}
