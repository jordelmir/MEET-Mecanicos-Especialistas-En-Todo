package com.elysium369.meet.core.fleet

import kotlinx.serialization.Serializable

@Serializable
enum class FleetContractType(val label: String) {
    FIXED_CANON("Canon Fijo Diario"),
    PROFIT_SPLIT("Reparto Porcentual"),
    HYBRID("Canon Base + Bono"),
}

@Serializable
enum class FleetUnitStatus(val label: String) {
    ON_ROUTE("En Ruta (Generando)"),
    AVAILABLE("Disponible"),
    IN_WORKSHOP("En Taller"),
    DISPATCH_LOCKED("Bloqueado (Mora/Alerta)"),
}

@Serializable
data class FleetMogulVehicle(
    val id: String,
    val plate: String,
    val brand: String,
    val model: String,
    val year: Int,
    val assignedDriverName: String,
    val driverPhone: String,
    val contractType: FleetContractType,
    val dailyCanonCrc: Long = 0L,
    val ownerSplitPercent: Int = 25, // e.g. 25% to owner, 70% to driver, 5% MEET constitutional cap
    val status: FleetUnitStatus = FleetUnitStatus.AVAILABLE,
    val todayGrossRevenueCrc: Long = 0L,
    val todayOwnerEarningsCrc: Long = 0L,
    val balanceDueFromDriverCrc: Long = 0L,
    val healthScore: Int = 1000,
    val engineTempC: Float = 88.0f,
    val currentSpeedKph: Float = 0.0f,
    val rpm: Float = 0.0f,
    val activeDtcs: List<String> = emptyList(),
    val isAbuseDetected: Boolean = false,
    val isDispatchLocked: Boolean = false,
    val currentMileageKm: Long = 0L,
    val lastUpdateEpochMs: Long = System.currentTimeMillis(),
) {
    val displayName: String get() = "$brand $model $year ($plate)"
    val hasAlerts: Boolean get() = isAbuseDetected || healthScore < 600 || activeDtcs.isNotEmpty() || balanceDueFromDriverCrc > 0
}
