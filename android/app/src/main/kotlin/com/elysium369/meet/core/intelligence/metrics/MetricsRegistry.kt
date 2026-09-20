package com.elysium369.meet.core.intelligence.metrics

import com.elysium369.meet.core.finance.Money
import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  M E E T   C A N O N I C A L   M E T R I C S   R E G I S T R Y
 *  ──────────────────────────────────────────────────────────────
 *  Single source of mathematical truth across Android, Web & Backend.
 *  Prevents formula divergence where different platforms report
 *  conflicting revenue, utilization, or rework figures.
 * ══════════════════════════════════════════════════════════════════════
 */

@Serializable
enum class MetricUnit {
    MONEY,
    PERCENTAGE,
    HOURS,
    COUNT,
    RATIO,
    DISTANCE_KM,
    MINUTES,
}

@Serializable
data class MetricDefinition(
    val key: String,
    val version: Int = 1,
    val name: String,
    val description: String,
    val unit: MetricUnit,
    val ownerDomain: String,
)

object MetricsRegistry {

    // ── Mobility Domain ──
    val MOBILITY_GMV = MetricDefinition(
        key = "mobility.gmv",
        version = 1,
        name = "Mobility Gross Merchandise Value",
        description = "Total captured fares paid by passengers for mobility trips",
        unit = MetricUnit.MONEY,
        ownerDomain = "Mobility",
    )

    val MOBILITY_COMPLETED_TRIPS = MetricDefinition(
        key = "mobility.completed_trips",
        version = 1,
        name = "Viajes Completados",
        description = "Total trips successfully completed and confirmed",
        unit = MetricUnit.COUNT,
        ownerDomain = "Mobility",
    )

    val DRIVER_UTILIZATION_RATE = MetricDefinition(
        key = "driver.utilization_rate",
        version = 1,
        name = "Tasa de Utilización",
        description = "occupied_time / online_time",
        unit = MetricUnit.PERCENTAGE,
        ownerDomain = "Mobility",
    )

    val DRIVER_DEADHEAD_RATIO = MetricDefinition(
        key = "driver.deadhead_ratio",
        version = 1,
        name = "Ratio Deadhead (Km sin pasaje)",
        description = "unpaid_distance / total_distance",
        unit = MetricUnit.RATIO,
        ownerDomain = "Mobility",
    )

    val DRIVER_REVENUE_PER_HOUR = MetricDefinition(
        key = "driver.revenue_per_hour",
        version = 1,
        name = "Ingreso por Hora Conectado",
        description = "net_earnings / online_hours",
        unit = MetricUnit.MONEY,
        ownerDomain = "Mobility",
    )

    val DRIVER_REVENUE_PER_KM = MetricDefinition(
        key = "driver.revenue_per_km",
        version = 1,
        name = "Ingreso por Km Pagado",
        description = "net_earnings / paid_distance_km",
        unit = MetricUnit.MONEY,
        ownerDomain = "Mobility",
    )

    // ── Services & Workshops Domain ──
    val SERVICE_GMV = MetricDefinition(
        key = "service.gmv",
        version = 1,
        name = "Services Gross Merchandise Value",
        description = "Total billings for mechanical and diagnostic services",
        unit = MetricUnit.MONEY,
        ownerDomain = "Services",
    )

    val SERVICE_REWORK_30D_RATE = MetricDefinition(
        key = "service.rework_30d_rate",
        version = 1,
        name = "Tasa de Retrabajo ≤30 Días",
        description = "Percentage of vehicles returning for the same DTC/repair within 30 days",
        unit = MetricUnit.PERCENTAGE,
        ownerDomain = "Services",
    )

    val WORKSHOP_AVERAGE_TICKET = MetricDefinition(
        key = "workshop.average_ticket",
        version = 1,
        name = "Ticket Promedio de Reparación",
        description = "total_revenue / completed_repair_orders",
        unit = MetricUnit.MONEY,
        ownerDomain = "Services",
    )

    // ── Tow Assistance Domain ──
    val TOW_LOADED_RATIO = MetricDefinition(
        key = "tow.loaded_ratio",
        version = 1,
        name = "Ratio de Carga en Grúa",
        description = "loaded_distance / total_distance",
        unit = MetricUnit.RATIO,
        ownerDomain = "Assistance",
    )

    // ── Fleet Domain ──
    val FLEET_REVENUE_PER_VEHICLE = MetricDefinition(
        key = "fleet.revenue_per_vehicle",
        version = 1,
        name = "Ingreso Promedio por Vehículo",
        description = "total_fleet_earnings / active_vehicles",
        unit = MetricUnit.MONEY,
        ownerDomain = "Fleet",
    )

    // ── Executive Platform ──
    val PLATFORM_TOTAL_GMV = MetricDefinition(
        key = "platform.total_gmv",
        version = 1,
        name = "Elysium Global GMV",
        description = "Sum of all transaction volumes across mobility, services, and towing",
        unit = MetricUnit.MONEY,
        ownerDomain = "Platform",
    )

    val PLATFORM_NET_REVENUE = MetricDefinition(
        key = "platform.net_revenue",
        version = 1,
        name = "Ingresos Netos Elysium",
        description = "platform_fees - refunds - chargebacks",
        unit = MetricUnit.MONEY,
        ownerDomain = "Platform",
    )

    fun calculateUtilization(occupiedHours: Double, onlineHours: Double): Double {
        if (onlineHours <= 0.0) return 0.0
        return ((occupiedHours / onlineHours) * 100.0).coerceIn(0.0, 100.0)
    }

    fun calculateDeadheadRatio(unpaidDistanceKm: Double, totalDistanceKm: Double): Double {
        if (totalDistanceKm <= 0.0) return 0.0
        return (unpaidDistanceKm / totalDistanceKm).coerceIn(0.0, 1.0)
    }

    fun calculateLoadedRatio(loadedDistanceKm: Double, totalDistanceKm: Double): Double {
        if (totalDistanceKm <= 0.0) return 0.0
        return (loadedDistanceKm / totalDistanceKm).coerceIn(0.0, 1.0)
    }

    fun calculateReworkRate(reworkJobs: Int, totalCompletedJobs: Int): Double {
        if (totalCompletedJobs <= 0) return 0.0
        return ((reworkJobs.toDouble() / totalCompletedJobs.toDouble()) * 100.0).coerceIn(0.0, 100.0)
    }
}
