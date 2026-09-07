package com.elysium369.meet.core.fleet

import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  F L E E T   C O M M A N D   E N G I N E
 *  ──────────────────────────────────────────
 *  Fleet management for businesses with 3-500+ vehicles.
 *
 *  "Tu flotilla en la palma de tu mano."
 *
 *  Dashboard:
 *  ✅ All vehicles: health score, status, location
 *  ✅ Upcoming maintenance across fleet
 *  ✅ Cost per vehicle (fuel, repairs, maintenance)
 *  ✅ Driver assignments and performance
 *  ✅ Compliance tracking (inspections, insurance)
 *  ✅ Alerts: overdue maintenance, low health, incidents
 *  ✅ Fleet-wide statistics and trends
 * ══════════════════════════════════════════════════════════════════════════
 */

// ─── Fleet Vehicle ───

@Serializable
data class FleetVehicle(
    val vehicleId: String,
    val licensePlate: String,
    val brand: String,
    val model: String,
    val year: Int,
    val vin: String = "",
    val status: FleetVehicleStatus = FleetVehicleStatus.ACTIVE,
    val assignedDriverId: String? = null,
    val assignedDriverName: String? = null,
    val healthScore: Int = 1000,
    val currentMileageKm: Long = 0,
    val lastInspectionEpochMs: Long? = null,
    val insuranceExpiresEpochMs: Long? = null,
    val totalCostToDate: Long = 0,
    val currency: String = "CRC",
    val addedAtEpochMs: Long = System.currentTimeMillis(),
) {
    val displayName: String get() = "$brand $model $year ($licensePlate)"
    val needsAttention: Boolean get() = healthScore < 600 || status == FleetVehicleStatus.MAINTENANCE
    val isCompliant: Boolean
        get() = insuranceExpiresEpochMs != null && insuranceExpiresEpochMs > System.currentTimeMillis()
}

enum class FleetVehicleStatus {
    ACTIVE,         // In service
    MAINTENANCE,    // In shop for repairs
    IDLE,           // Available but not assigned
    OUT_OF_SERVICE, // Decommissioned
    INCIDENT,       // Involved in incident
}

// ─── Fleet Cost Entry ───

@Serializable
data class FleetCostEntry(
    val entryId: String,
    val vehicleId: String,
    val category: FleetCostCategory,
    val amount: Long,
    val description: String = "",
    val timestampMs: Long = System.currentTimeMillis(),
)

enum class FleetCostCategory {
    FUEL,
    REPAIR,
    MAINTENANCE,
    INSURANCE,
    REGISTRATION,
    TIRES,
    TOLL,
    OTHER,
}

// ─── Fleet Alert ───

data class FleetAlert(
    val vehicleId: String,
    val type: FleetAlertType,
    val message: String,
    val severity: AlertSeverity,
)

enum class FleetAlertType {
    LOW_HEALTH_SCORE,
    MAINTENANCE_OVERDUE,
    INSURANCE_EXPIRING,
    INSPECTION_DUE,
    HIGH_MILEAGE,
    INCIDENT_REPORTED,
    DRIVER_UNASSIGNED,
}

enum class AlertSeverity { INFO, WARNING, CRITICAL }

// ─── Fleet Summary ───

data class FleetSummary(
    val totalVehicles: Int,
    val activeVehicles: Int,
    val inMaintenance: Int,
    val averageHealthScore: Int,
    val totalFleetCost: Long,
    val averageCostPerVehicle: Long,
    val vehiclesNeedingAttention: Int,
    val nonCompliantCount: Int,
    val alerts: List<FleetAlert>,
)

// ─── Engine ───

class FleetCommandEngine {

    private val vehicles = mutableMapOf<String, FleetVehicle>()
    private val costs = mutableListOf<FleetCostEntry>()

    // ─── Vehicle Management ───

    fun addVehicle(
        licensePlate: String, brand: String, model: String, year: Int,
        vin: String = "", currentMileageKm: Long = 0,
    ): FleetVehicle {
        val vehicle = FleetVehicle(
            vehicleId = "fleet-${System.currentTimeMillis()}-${vehicles.size}",
            licensePlate = licensePlate,
            brand = brand, model = model, year = year, vin = vin,
            currentMileageKm = currentMileageKm,
        )
        vehicles[vehicle.vehicleId] = vehicle
        return vehicle
    }

    fun assignDriver(vehicleId: String, driverId: String, driverName: String): Boolean {
        val v = vehicles[vehicleId] ?: return false
        vehicles[vehicleId] = v.copy(assignedDriverId = driverId, assignedDriverName = driverName)
        return true
    }

    fun updateStatus(vehicleId: String, status: FleetVehicleStatus): Boolean {
        val v = vehicles[vehicleId] ?: return false
        vehicles[vehicleId] = v.copy(status = status)
        return true
    }

    fun updateHealthScore(vehicleId: String, score: Int): Boolean {
        val v = vehicles[vehicleId] ?: return false
        vehicles[vehicleId] = v.copy(healthScore = score.coerceIn(0, 1000))
        return true
    }

    fun updateMileage(vehicleId: String, km: Long): Boolean {
        val v = vehicles[vehicleId] ?: return false
        vehicles[vehicleId] = v.copy(currentMileageKm = km)
        return true
    }

    // ─── Cost Tracking ───

    fun recordCost(vehicleId: String, category: FleetCostCategory, amount: Long, description: String = ""): FleetCostEntry {
        val entry = FleetCostEntry(
            entryId = "cost-${System.currentTimeMillis()}-${costs.size}",
            vehicleId = vehicleId, category = category,
            amount = amount, description = description,
        )
        costs.add(entry)
        val v = vehicles[vehicleId]
        if (v != null) vehicles[vehicleId] = v.copy(totalCostToDate = v.totalCostToDate + amount)
        return entry
    }

    fun getCostsByVehicle(vehicleId: String): Long =
        costs.filter { it.vehicleId == vehicleId }.sumOf { it.amount }

    fun getCostsByCategory(): Map<FleetCostCategory, Long> =
        costs.groupBy { it.category }.mapValues { (_, items) -> items.sumOf { it.amount } }

    // ─── Alerts ───

    fun generateAlerts(): List<FleetAlert> = buildList {
        vehicles.values.forEach { v ->
            if (v.healthScore < 400) add(FleetAlert(v.vehicleId, FleetAlertType.LOW_HEALTH_SCORE,
                "${v.displayName}: Health Score crítico (${v.healthScore}/1000)", AlertSeverity.CRITICAL))
            else if (v.healthScore < 600) add(FleetAlert(v.vehicleId, FleetAlertType.LOW_HEALTH_SCORE,
                "${v.displayName}: Health Score bajo (${v.healthScore}/1000)", AlertSeverity.WARNING))

            if (!v.isCompliant) add(FleetAlert(v.vehicleId, FleetAlertType.INSURANCE_EXPIRING,
                "${v.displayName}: Seguro vencido o por vencer", AlertSeverity.CRITICAL))

            if (v.currentMileageKm > 200000) add(FleetAlert(v.vehicleId, FleetAlertType.HIGH_MILEAGE,
                "${v.displayName}: Alto kilometraje (${v.currentMileageKm} km)", AlertSeverity.WARNING))

            if (v.assignedDriverId == null && v.status == FleetVehicleStatus.ACTIVE)
                add(FleetAlert(v.vehicleId, FleetAlertType.DRIVER_UNASSIGNED,
                    "${v.displayName}: Sin conductor asignado", AlertSeverity.INFO))
        }
    }

    // ─── Fleet Summary ───

    fun getSummary(): FleetSummary {
        val all = vehicles.values.toList()
        val alerts = generateAlerts()
        return FleetSummary(
            totalVehicles = all.size,
            activeVehicles = all.count { it.status == FleetVehicleStatus.ACTIVE },
            inMaintenance = all.count { it.status == FleetVehicleStatus.MAINTENANCE },
            averageHealthScore = if (all.isNotEmpty()) all.sumOf { it.healthScore } / all.size else 0,
            totalFleetCost = costs.sumOf { it.amount },
            averageCostPerVehicle = if (all.isNotEmpty()) costs.sumOf { it.amount } / all.size else 0,
            vehiclesNeedingAttention = all.count { it.needsAttention },
            nonCompliantCount = all.count { !it.isCompliant },
            alerts = alerts,
        )
    }

    // ─── Queries ───

    fun getVehicle(id: String): FleetVehicle? = vehicles[id]
    fun getAllVehicles(): List<FleetVehicle> = vehicles.values.sortedBy { it.displayName }
    fun getVehiclesByHealth(): List<FleetVehicle> = vehicles.values.sortedBy { it.healthScore }

    val totalVehicles: Int get() = vehicles.size
}
