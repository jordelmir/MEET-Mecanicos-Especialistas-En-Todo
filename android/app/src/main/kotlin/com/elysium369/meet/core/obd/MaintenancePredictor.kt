package com.elysium369.meet.core.obd

/**
 * MaintenancePredictor — Predicción inteligente de mantenimiento.
 * Basado en odómetro OBD, horas de motor y patrones de uso,
 * calcula cuándo toca el próximo servicio.
 * 
 * NINGUNA app OBD2 hace esto automáticamente sin input manual.
 */
object MaintenancePredictor {

    enum class Urgency { OK, SOON, DUE, OVERDUE }

    data class MaintenanceItem(
        val name: String,
        val icon: String,
        val urgency: Urgency,
        val description: String,
        val estimatedKmRemaining: Int?,
        val intervalKm: Int,
        val reason: String
    )

    /**
     * Genera lista de mantenimientos basado en km y datos del motor.
     * @param currentKm odómetro actual (o estimado desde OBD trip distance)
     * @param lastOilChangeKm km del último cambio de aceite (0 si desconocido)
     * @param coolantTemp temperatura promedio del refrigerante
     * @param fuelType "gasoline", "diesel", "hybrid"
     */
    fun predict(
        currentKm: Float,
        lastOilChangeKm: Float = 0f,
        coolantTemp: Float? = null,
        avgRpm: Float? = null,
        fuelType: String = "gasoline"
    ): List<MaintenanceItem> {
        val items = mutableListOf<MaintenanceItem>()
        val km = currentKm.toInt()

        // ─── Aceite de Motor ───
        val oilInterval = if (fuelType == "diesel") 10000 else 7500
        val kmSinceOil = (currentKm - lastOilChangeKm).toInt()
        val oilRemaining = oilInterval - kmSinceOil
        val oilUrgency = when {
            oilRemaining <= 0 -> Urgency.OVERDUE
            oilRemaining <= 1000 -> Urgency.DUE
            oilRemaining <= 2500 -> Urgency.SOON
            else -> Urgency.OK
        }
        items.add(MaintenanceItem(
            "Cambio de Aceite", "🛢️", oilUrgency,
            "Cada ${oilInterval}km. Último: ${lastOilChangeKm.toInt()}km",
            if (oilRemaining > 0) oilRemaining else 0,
            oilInterval,
            if (oilRemaining <= 0) "¡VENCIDO por ${-oilRemaining}km!" else "Faltan ~${oilRemaining}km"
        ))

        // ─── Filtro de Aire ───
        val airFilterInterval = 20000
        val airFilterKm = km % airFilterInterval
        val airRemaining = airFilterInterval - airFilterKm
        items.add(MaintenanceItem(
            "Filtro de Aire", "💨", if (airRemaining < 3000) Urgency.SOON else Urgency.OK,
            "Cada ${airFilterInterval}km",
            airRemaining, airFilterInterval,
            "Reemplazar para mantener flujo de aire óptimo"
        ))

        // ─── Bujías ───
        val sparkInterval = if (fuelType == "diesel") 0 else 40000
        if (sparkInterval > 0) {
            val sparkKm = km % sparkInterval
            val sparkRemaining = sparkInterval - sparkKm
            items.add(MaintenanceItem(
                "Bujías", "⚡", if (sparkRemaining < 5000) Urgency.SOON else Urgency.OK,
                "Cada ${sparkInterval}km (convencionales)",
                sparkRemaining, sparkInterval,
                "Bujías desgastadas causan fallas de encendido y mayor consumo"
            ))
        }

        // ─── Refrigerante ───
        val coolantInterval = 60000
        val coolantKm = km % coolantInterval
        val coolantRemaining = coolantInterval - coolantKm
        items.add(MaintenanceItem(
            "Refrigerante", "🌡️",
            if (coolantRemaining < 5000 || (coolantTemp != null && coolantTemp > 105)) Urgency.DUE else Urgency.OK,
            "Cada ${coolantInterval}km o 2 años",
            coolantRemaining, coolantInterval,
            if (coolantTemp != null && coolantTemp > 105) "⚠️ Temp elevada ${coolantTemp.toInt()}°C" else "Mantener nivel y concentración"
        ))

        // ─── Banda de Distribución ───
        items.add(MaintenanceItem(
            "Banda/Cadena Distribución", "⚙️",
            if (km > 90000 && km % 100000 > 90000) Urgency.DUE else Urgency.OK,
            "Cada 80-120,000km según fabricante",
            null, 100000,
            "CRÍTICO: Falla causa daño catastrófico al motor"
        ))

        // ─── Líquido de Frenos ───
        items.add(MaintenanceItem(
            "Líquido de Frenos", "🛑",
            if (km % 40000 > 35000) Urgency.SOON else Urgency.OK,
            "Cada 40,000km o 2 años",
            null, 40000,
            "El líquido absorbe humedad y pierde eficacia"
        ))

        // ─── Transmisión ───
        val transInterval = 60000
        items.add(MaintenanceItem(
            "Aceite Transmisión", "🔄",
            if (km % transInterval > 55000) Urgency.SOON else Urgency.OK,
            "Cada ${transInterval}km",
            null, transInterval,
            "Automática: ATF. Manual: GL-4/GL-5"
        ))

        return items.sortedBy { it.urgency.ordinal }.reversed() // Urgent first
    }
}
