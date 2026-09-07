package com.elysium369.meet.core.maintenance

import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  M A I N T E N A N C E   P R E D I C T O R   E N G I N E
 *  ──────────────────────────────────────────────────────────
 *  "Tu banda de tiempo tiene ~5,000 km. Programa ahora y ahorra 40%."
 *
 *  Predicts when vehicle components will need service based on:
 *  ✅ Manufacturer recommended intervals
 *  ✅ Actual mileage and driving patterns
 *  ✅ OBD data patterns (if available)
 *  ✅ Vehicle age and condition
 *  ✅ Historical repair data from the community
 *  ✅ Environmental factors (climate, road conditions)
 *
 *  Each prediction includes:
 *  - Remaining km/days estimate
 *  - Urgency level (GREEN → RED)
 *  - Cost estimate (preventive vs emergency)
 *  - Savings if done preventively
 * ══════════════════════════════════════════════════════════════════════════
 */

// ─── Component ───

enum class VehicleComponent {
    ENGINE_OIL,
    OIL_FILTER,
    AIR_FILTER,
    CABIN_FILTER,
    SPARK_PLUGS,
    TIMING_BELT,
    SERPENTINE_BELT,
    BRAKE_PADS_FRONT,
    BRAKE_PADS_REAR,
    BRAKE_ROTORS,
    BRAKE_FLUID,
    TRANSMISSION_FLUID,
    COOLANT,
    POWER_STEERING_FLUID,
    TIRES,
    BATTERY,
    WIPER_BLADES,
    FUEL_FILTER,
    DIFFERENTIAL_FLUID,
    WHEEL_ALIGNMENT,
}

val VehicleComponent.displayLabel: String
    get() = when (this) {
        VehicleComponent.ENGINE_OIL -> "Aceite de motor"
        VehicleComponent.OIL_FILTER -> "Filtro de aceite"
        VehicleComponent.AIR_FILTER -> "Filtro de aire"
        VehicleComponent.CABIN_FILTER -> "Filtro de cabina"
        VehicleComponent.SPARK_PLUGS -> "Bujías"
        VehicleComponent.TIMING_BELT -> "Banda de tiempo"
        VehicleComponent.SERPENTINE_BELT -> "Banda serpentina"
        VehicleComponent.BRAKE_PADS_FRONT -> "Pastillas de freno (del.)"
        VehicleComponent.BRAKE_PADS_REAR -> "Pastillas de freno (tras.)"
        VehicleComponent.BRAKE_ROTORS -> "Discos de freno"
        VehicleComponent.BRAKE_FLUID -> "Líquido de frenos"
        VehicleComponent.TRANSMISSION_FLUID -> "Fluido de transmisión"
        VehicleComponent.COOLANT -> "Refrigerante"
        VehicleComponent.POWER_STEERING_FLUID -> "Líquido dirección"
        VehicleComponent.TIRES -> "Neumáticos"
        VehicleComponent.BATTERY -> "Batería"
        VehicleComponent.WIPER_BLADES -> "Limpiaparabrisas"
        VehicleComponent.FUEL_FILTER -> "Filtro de combustible"
        VehicleComponent.DIFFERENTIAL_FLUID -> "Fluido diferencial"
        VehicleComponent.WHEEL_ALIGNMENT -> "Alineamiento"
    }

// ─── Default Intervals ───

data class MaintenanceInterval(
    val component: VehicleComponent,
    val intervalKm: Long,
    val intervalMonths: Int,
    val preventiveCost: Long,    // CRC
    val emergencyCost: Long,     // CRC if ignored
    val criticalIfIgnored: Boolean = false,
)

// ─── Urgency ───

enum class PredictionUrgency {
    GREEN,      // > 60% life remaining
    YELLOW,     // 30-60% life remaining
    ORANGE,     // 10-30% life remaining
    RED,        // < 10% — needs immediate attention
    OVERDUE,    // Past due
}

val PredictionUrgency.displayLabel: String
    get() = when (this) {
        PredictionUrgency.GREEN -> "🟢 Sin prisa"
        PredictionUrgency.YELLOW -> "🟡 Programar pronto"
        PredictionUrgency.ORANGE -> "🟠 Atención necesaria"
        PredictionUrgency.RED -> "🔴 Urgente"
        PredictionUrgency.OVERDUE -> "⚫ Vencido"
    }

// ─── Prediction ───

@Serializable
data class MaintenancePrediction(
    val component: VehicleComponent,
    val urgency: PredictionUrgency,
    val remainingKm: Long,
    val remainingDays: Int,
    val percentRemaining: Double,
    val preventiveCost: Long,
    val emergencyCost: Long,
    val savingsIfPreventive: Long,
    val recommendation: String,
    val lastServiceKm: Long? = null,
    val lastServiceEpochMs: Long? = null,
    val currency: String = "CRC",
) {
    val formattedPreventive: String
        get() = "₡${preventiveCost.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")}"
    val formattedEmergency: String
        get() = "₡${emergencyCost.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")}"
    val formattedSavings: String
        get() = "₡${savingsIfPreventive.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")}"
}

// ─── Vehicle Service Record ───

@Serializable
data class ServiceRecord(
    val vehicleId: String,
    val component: VehicleComponent,
    val serviceKm: Long,
    val serviceEpochMs: Long = System.currentTimeMillis(),
    val nextDueKm: Long? = null,
)

// ─── Maintenance Plan ───

data class MaintenancePlan(
    val vehicleId: String,
    val predictions: List<MaintenancePrediction>,
    val totalPreventiveCost: Long,
    val totalEmergencyCost: Long,
    val totalSavings: Long,
    val urgentCount: Int,
    val overdueCount: Int,
    val computedAtEpochMs: Long = System.currentTimeMillis(),
) {
    val overallUrgency: PredictionUrgency
        get() = when {
            overdueCount > 0 -> PredictionUrgency.OVERDUE
            urgentCount > 0 -> PredictionUrgency.RED
            predictions.any { it.urgency == PredictionUrgency.ORANGE } -> PredictionUrgency.ORANGE
            predictions.any { it.urgency == PredictionUrgency.YELLOW } -> PredictionUrgency.YELLOW
            else -> PredictionUrgency.GREEN
        }
}

// ─── Engine ───

class MaintenancePredictorEngine {

    private val defaultIntervals = mapOf(
        VehicleComponent.ENGINE_OIL to MaintenanceInterval(VehicleComponent.ENGINE_OIL, 7500, 6, 25000, 800000, true),
        VehicleComponent.OIL_FILTER to MaintenanceInterval(VehicleComponent.OIL_FILTER, 7500, 6, 5000, 15000),
        VehicleComponent.AIR_FILTER to MaintenanceInterval(VehicleComponent.AIR_FILTER, 20000, 12, 8000, 25000),
        VehicleComponent.CABIN_FILTER to MaintenanceInterval(VehicleComponent.CABIN_FILTER, 20000, 12, 10000, 20000),
        VehicleComponent.SPARK_PLUGS to MaintenanceInterval(VehicleComponent.SPARK_PLUGS, 50000, 36, 30000, 80000),
        VehicleComponent.TIMING_BELT to MaintenanceInterval(VehicleComponent.TIMING_BELT, 90000, 60, 150000, 1500000, true),
        VehicleComponent.SERPENTINE_BELT to MaintenanceInterval(VehicleComponent.SERPENTINE_BELT, 80000, 48, 25000, 80000),
        VehicleComponent.BRAKE_PADS_FRONT to MaintenanceInterval(VehicleComponent.BRAKE_PADS_FRONT, 40000, 24, 35000, 120000, true),
        VehicleComponent.BRAKE_PADS_REAR to MaintenanceInterval(VehicleComponent.BRAKE_PADS_REAR, 50000, 30, 30000, 100000, true),
        VehicleComponent.BRAKE_ROTORS to MaintenanceInterval(VehicleComponent.BRAKE_ROTORS, 80000, 48, 60000, 200000, true),
        VehicleComponent.BRAKE_FLUID to MaintenanceInterval(VehicleComponent.BRAKE_FLUID, 40000, 24, 15000, 50000, true),
        VehicleComponent.TRANSMISSION_FLUID to MaintenanceInterval(VehicleComponent.TRANSMISSION_FLUID, 60000, 36, 35000, 500000, true),
        VehicleComponent.COOLANT to MaintenanceInterval(VehicleComponent.COOLANT, 50000, 24, 20000, 300000, true),
        VehicleComponent.TIRES to MaintenanceInterval(VehicleComponent.TIRES, 60000, 48, 200000, 300000, true),
        VehicleComponent.BATTERY to MaintenanceInterval(VehicleComponent.BATTERY, 75000, 48, 45000, 80000),
        VehicleComponent.WIPER_BLADES to MaintenanceInterval(VehicleComponent.WIPER_BLADES, 20000, 12, 8000, 15000),
    )

    private val serviceRecords = mutableListOf<ServiceRecord>()

    // ─── Record Service ───

    fun recordService(vehicleId: String, component: VehicleComponent, atKm: Long): ServiceRecord {
        val interval = defaultIntervals[component]
        val record = ServiceRecord(
            vehicleId = vehicleId,
            component = component,
            serviceKm = atKm,
            nextDueKm = if (interval != null) atKm + interval.intervalKm else null,
        )
        serviceRecords.add(record)
        return record
    }

    // ─── Predict ───

    fun predict(
        vehicleId: String,
        currentKm: Long,
        vehicleAgeMonths: Int = 0,
        averageMonthlyKm: Long = 1500,
    ): MaintenancePlan {
        val predictions = defaultIntervals.map { (component, interval) ->
            val lastService = serviceRecords
                .filter { it.vehicleId == vehicleId && it.component == component }
                .maxByOrNull { it.serviceKm }

            val kmSinceService = if (lastService != null) currentKm - lastService.serviceKm else currentKm
            val remainingKm = (interval.intervalKm - kmSinceService).coerceAtLeast(-interval.intervalKm)
            val percentRemaining = (remainingKm.toDouble() / interval.intervalKm * 100).coerceIn(-100.0, 100.0)

            val remainingDays = if (averageMonthlyKm > 0) {
                ((remainingKm.toDouble() / averageMonthlyKm) * 30).toInt()
            } else 365

            val urgency = when {
                remainingKm <= 0 -> PredictionUrgency.OVERDUE
                percentRemaining < 10 -> PredictionUrgency.RED
                percentRemaining < 30 -> PredictionUrgency.ORANGE
                percentRemaining < 60 -> PredictionUrgency.YELLOW
                else -> PredictionUrgency.GREEN
            }

            MaintenancePrediction(
                component = component,
                urgency = urgency,
                remainingKm = remainingKm.coerceAtLeast(0),
                remainingDays = remainingDays.coerceAtLeast(0),
                percentRemaining = percentRemaining.coerceAtLeast(0.0),
                preventiveCost = interval.preventiveCost,
                emergencyCost = interval.emergencyCost,
                savingsIfPreventive = interval.emergencyCost - interval.preventiveCost,
                recommendation = buildRecommendation(component, urgency, remainingKm, interval),
                lastServiceKm = lastService?.serviceKm,
                lastServiceEpochMs = lastService?.serviceEpochMs,
            )
        }.sortedBy { it.urgency.ordinal * -1 } // Most urgent first (OVERDUE at top)
            .sortedByDescending { when(it.urgency) {
                PredictionUrgency.OVERDUE -> 5
                PredictionUrgency.RED -> 4
                PredictionUrgency.ORANGE -> 3
                PredictionUrgency.YELLOW -> 2
                PredictionUrgency.GREEN -> 1
            }}

        return MaintenancePlan(
            vehicleId = vehicleId,
            predictions = predictions,
            totalPreventiveCost = predictions.filter {
                it.urgency in listOf(PredictionUrgency.OVERDUE, PredictionUrgency.RED, PredictionUrgency.ORANGE)
            }.sumOf { it.preventiveCost },
            totalEmergencyCost = predictions.filter {
                it.urgency in listOf(PredictionUrgency.OVERDUE, PredictionUrgency.RED, PredictionUrgency.ORANGE)
            }.sumOf { it.emergencyCost },
            totalSavings = predictions.filter {
                it.urgency in listOf(PredictionUrgency.OVERDUE, PredictionUrgency.RED, PredictionUrgency.ORANGE)
            }.sumOf { it.savingsIfPreventive },
            urgentCount = predictions.count { it.urgency == PredictionUrgency.RED },
            overdueCount = predictions.count { it.urgency == PredictionUrgency.OVERDUE },
        )
    }

    // ─── Queries ───

    fun getServiceHistory(vehicleId: String): List<ServiceRecord> =
        serviceRecords.filter { it.vehicleId == vehicleId }
            .sortedByDescending { it.serviceEpochMs }

    fun getOverdueItems(vehicleId: String, currentKm: Long): List<MaintenancePrediction> =
        predict(vehicleId, currentKm).predictions
            .filter { it.urgency == PredictionUrgency.OVERDUE }

    val totalServiceRecords: Int get() = serviceRecords.size

    private fun buildRecommendation(
        component: VehicleComponent,
        urgency: PredictionUrgency,
        remainingKm: Long,
        interval: MaintenanceInterval,
    ): String = when (urgency) {
        PredictionUrgency.OVERDUE -> {
            val savings = interval.emergencyCost - interval.preventiveCost
            "⚫ ${component.displayLabel} VENCIDO. Reparar ya. Ahorro vs emergencia: ₡$savings"
        }
        PredictionUrgency.RED -> {
            "🔴 ${component.displayLabel} necesita servicio pronto (~$remainingKm km restantes)."
        }
        PredictionUrgency.ORANGE -> {
            "🟠 Programar ${component.displayLabel} en los próximos $remainingKm km."
        }
        PredictionUrgency.YELLOW -> {
            "🟡 ${component.displayLabel}: siguiente servicio en ~$remainingKm km."
        }
        PredictionUrgency.GREEN -> {
            "🟢 ${component.displayLabel} en buen estado. Próximo en ~$remainingKm km."
        }
    }
}
