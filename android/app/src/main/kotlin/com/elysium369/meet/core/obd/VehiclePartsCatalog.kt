package com.elysium369.meet.core.obd

object VehiclePartsCatalog {

    data class PartCategory(
        val name: String,
        val parts: List<PartDefinition>
    )

    data class PartDefinition(
        val name: String,
        val hasLocation: Boolean = false,
        val expectedLifeKm: Int? = null,
        val expectedLifeMonths: Int? = null,
        val isPeriodic: Boolean = false
    )

    val LOCATIONS = listOf(
        "Delantera Izquierda",
        "Delantera Derecha",
        "Trasera Izquierda",
        "Trasera Derecha",
        "Delantera",
        "Trasera",
        "Izquierda",
        "Derecha",
        "Superior",
        "Inferior",
        "Central"
    )

    val CATALOG = listOf(
        PartCategory(
            "Suspensión y Dirección",
            listOf(
                PartDefinition("Amortiguador", hasLocation = true, expectedLifeKm = 80000, expectedLifeMonths = 60, isPeriodic = true),
                PartDefinition("Rótula", hasLocation = true, expectedLifeKm = 100000),
                PartDefinition("Tijereta / Horquilla", hasLocation = true, expectedLifeKm = 120000),
                PartDefinition("Terminal de Dirección", hasLocation = true, expectedLifeKm = 100000),
                PartDefinition("Axial de Dirección", hasLocation = true, expectedLifeKm = 100000),
                PartDefinition("Buje de Suspensión", hasLocation = true, expectedLifeKm = 80000),
                PartDefinition("Barra Estabilizadora (Tirante)", hasLocation = true, expectedLifeKm = 60000),
                PartDefinition("Cremallera de Dirección", hasLocation = false, expectedLifeKm = 150000),
                PartDefinition("Resorte / Espiral", hasLocation = true, expectedLifeKm = 150000)
            )
        ),
        PartCategory(
            "Frenos",
            listOf(
                PartDefinition("Pastillas de Freno", hasLocation = true, expectedLifeKm = 40000, expectedLifeMonths = 36, isPeriodic = true),
                PartDefinition("Discos de Freno", hasLocation = true, expectedLifeKm = 80000, expectedLifeMonths = 72, isPeriodic = true),
                PartDefinition("Caliper / Mordaza", hasLocation = true),
                PartDefinition("Líquido de Frenos", hasLocation = false, expectedLifeKm = 40000, expectedLifeMonths = 24, isPeriodic = true),
                PartDefinition("Bomba de Freno", hasLocation = false),
                PartDefinition("Booster / Servofreno", hasLocation = false),
                PartDefinition("Manguera de Freno", hasLocation = true),
                PartDefinition("Sensor ABS", hasLocation = true)
            )
        ),
        PartCategory(
            "Motor",
            listOf(
                PartDefinition("Aceite de Motor", hasLocation = false, expectedLifeKm = 10000, expectedLifeMonths = 12, isPeriodic = true),
                PartDefinition("Filtro de Aceite", hasLocation = false, expectedLifeKm = 10000, expectedLifeMonths = 12, isPeriodic = true),
                PartDefinition("Filtro de Aire", hasLocation = false, expectedLifeKm = 20000, expectedLifeMonths = 24, isPeriodic = true),
                PartDefinition("Bujía", hasLocation = false, expectedLifeKm = 60000, isPeriodic = true),
                PartDefinition("Bobina de Encendido", hasLocation = false),
                PartDefinition("Correa de Distribución", hasLocation = false, expectedLifeKm = 100000, expectedLifeMonths = 60, isPeriodic = true),
                PartDefinition("Correa de Accesorios", hasLocation = false, expectedLifeKm = 80000, expectedLifeMonths = 48, isPeriodic = true),
                PartDefinition("Bomba de Agua", hasLocation = false, expectedLifeKm = 120000, expectedLifeMonths = 72, isPeriodic = true),
                PartDefinition("Termostato", hasLocation = false, expectedLifeKm = 100000, expectedLifeMonths = 60, isPeriodic = true),
                PartDefinition("Radiador", hasLocation = false, expectedLifeKm = 150000, expectedLifeMonths = 120),
                PartDefinition("Empaque de Culata / Cabezote", hasLocation = false),
                PartDefinition("Soporte de Motor", hasLocation = true, expectedLifeKm = 100000)
            )
        ),
        PartCategory(
            "Transmisión",
            listOf(
                PartDefinition("Aceite de Transmisión", hasLocation = false, expectedLifeKm = 60000, expectedLifeMonths = 48, isPeriodic = true),
                PartDefinition("Filtro de Transmisión", hasLocation = false, expectedLifeKm = 60000, expectedLifeMonths = 48, isPeriodic = true),
                PartDefinition("Kit de Embrague / Clutch", hasLocation = false, expectedLifeKm = 120000),
                PartDefinition("Bomba de Embrague", hasLocation = false),
                PartDefinition("Soporte de Transmisión", hasLocation = false, expectedLifeKm = 100000),
                PartDefinition("Junta Homocinética / Punta de Eje", hasLocation = true, expectedLifeKm = 120000),
                PartDefinition("Bota de Eje / Guardapolvo", hasLocation = true, expectedLifeKm = 80000)
            )
        ),
        PartCategory(
            "Eléctrico y Electrónico",
            listOf(
                PartDefinition("Batería de 12V", hasLocation = false, expectedLifeMonths = 48, isPeriodic = true),
                PartDefinition("Alternador", hasLocation = false, expectedLifeKm = 150000, expectedLifeMonths = 96),
                PartDefinition("Motor de Arranque", hasLocation = false, expectedLifeKm = 150000, expectedLifeMonths = 96),
                PartDefinition("Sensor de Oxígeno (O2)", hasLocation = true, expectedLifeKm = 100000),
                PartDefinition("Sensor MAF / MAP", hasLocation = false),
                PartDefinition("Foco / Bombillo", hasLocation = true),
                PartDefinition("Fusible / Relé", hasLocation = true)
            )
        ),
        PartCategory(
            "Combustible y Escape",
            listOf(
                PartDefinition("Filtro de Combustible", hasLocation = false, expectedLifeKm = 40000, expectedLifeMonths = 24, isPeriodic = true),
                PartDefinition("Bomba de Combustible", hasLocation = false, expectedLifeKm = 150000),
                PartDefinition("Inyector", hasLocation = false),
                PartDefinition("Catalizador", hasLocation = false, expectedLifeKm = 150000),
                PartDefinition("Silenciador / Mofle", hasLocation = false)
            )
        ),
        PartCategory(
            "Climatización (A/C)",
            listOf(
                PartDefinition("Filtro de Cabina", hasLocation = false, expectedLifeKm = 15000, expectedLifeMonths = 12, isPeriodic = true),
                PartDefinition("Compresor de A/C", hasLocation = false),
                PartDefinition("Evaporador", hasLocation = false),
                PartDefinition("Condensador", hasLocation = false),
                PartDefinition("Gas Refrigerante", hasLocation = false)
            )
        )
    )

    // Flat list for easy searching if needed
    val ALL_PARTS: List<Pair<PartCategory, PartDefinition>> = CATALOG.flatMap { cat -> cat.parts.map { cat to it } }
}
