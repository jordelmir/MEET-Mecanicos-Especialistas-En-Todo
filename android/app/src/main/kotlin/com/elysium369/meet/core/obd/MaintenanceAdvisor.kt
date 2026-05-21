package com.elysium369.meet.core.obd

object MaintenanceAdvisor {
    
    data class MaintenanceItem(
        val category: String,
        val defaultIntervalKm: Int,
        val defaultIntervalMonths: Int,
        val description: String
    )
    
    val STANDARD_MAINTENANCE_ITEMS = listOf(
        MaintenanceItem("Aceite Sintético", 10000, 12, "Cambio de aceite de motor sintético y filtro"),
        MaintenanceItem("Aceite Mineral", 5000, 6, "Cambio de aceite de motor mineral y filtro"),
        MaintenanceItem("Aceite Semi-Sintético", 7500, 9, "Cambio de aceite semi-sintético y filtro"),
        MaintenanceItem("Filtro de Aire", 20000, 24, "Reemplazo del filtro de aire del motor"),
        MaintenanceItem("Filtro de Combustible", 40000, 24, "Reemplazo de filtro de gasolina/diésel"),
        MaintenanceItem("Filtro de Cabina", 15000, 12, "Reemplazo del filtro de aire acondicionado/habitáculo"),
        MaintenanceItem("Bujías (Iridio/Platino)", 100000, 60, "Reemplazo de bujías de larga duración"),
        MaintenanceItem("Bujías (Cobre)", 30000, 24, "Reemplazo de bujías estándar de cobre"),
        MaintenanceItem("Líquido de Frenos", 40000, 24, "Purga y reemplazo completo del líquido de frenos (DOT3/DOT4)"),
        MaintenanceItem("Refrigerante (Anticongelante)", 80000, 48, "Drenado y llenado de sistema de enfriamiento"),
        MaintenanceItem("Correa de Distribución", 100000, 60, "Reemplazo preventivo para evitar daño grave de motor (si aplica)"),
        MaintenanceItem("Correa de Accesorios (Serpentina)", 80000, 48, "Reemplazo de banda impulsora del alternador/bomba"),
        MaintenanceItem("Alineación y Balanceo", 10000, 6, "Rotación, alineación y balanceo de los 4 neumáticos"),
        MaintenanceItem("Servicio de Transmisión Automática", 60000, 48, "Cambio de ATF y filtro de transmisión")
    )

    data class RepairItem(
        val partCategory: String,
        val expectedLifeKm: Int?,
        val expectedLifeMonths: Int?,
        val isPeriodic: Boolean
    )

    val STANDARD_REPAIR_ITEMS = listOf(
        RepairItem("Pastillas de Freno", 40000, 36, true),
        RepairItem("Discos de Freno", 80000, 72, true),
        RepairItem("Amortiguadores", 80000, 60, true),
        RepairItem("Batería de 12V", null, 48, true),
        RepairItem("Neumáticos", 50000, 60, true),
        RepairItem("Bomba de Agua", 120000, 72, true),
        RepairItem("Alternador", 150000, 96, false),
        RepairItem("Motor de Arranque", 150000, 96, false),
        RepairItem("Embrague (Manual)", 120000, null, false),
        RepairItem("Termostato", 100000, 60, true),
        RepairItem("Bomba de Combustible", 150000, null, false),
        RepairItem("Radiador", 150000, 120, false)
    )

    fun getNextMaintenanceDue(
        currentOdometer: Int, 
        currentDateMillis: Long, 
        intervalKm: Int, 
        intervalMonths: Int,
        avgDailyKm: Float? = null
    ): Pair<Int, Long> {
        val nextKm = currentOdometer + intervalKm
        
        val nextDate = if (avgDailyKm != null && avgDailyKm > 1f) {
            // Predict next date based on daily driving average
            val daysUntilDue = intervalKm / avgDailyKm
            currentDateMillis + (daysUntilDue * 24 * 60 * 60 * 1000).toLong()
        } else {
            // Fallback to static months
            currentDateMillis + (intervalMonths * 30L * 24 * 60 * 60 * 1000)
        }
        
        return Pair(nextKm, nextDate)
    }
}
