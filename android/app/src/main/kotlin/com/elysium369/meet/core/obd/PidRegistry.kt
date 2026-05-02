package com.elysium369.meet.core.obd

enum class PidCategory { ENGINE, FUEL, TEMPERATURE, ELECTRICAL, EMISSIONS, TRANSMISSION, CUSTOM }

data class PidDefinition(
    val mode: String,
    val pid: String,
    val name: String,
    val unit: String,
    val minValue: Float,
    val maxValue: Float,
    val warningThreshold: Float,
    val criticalThreshold: Float,
    val formula: (a: Int, b: Int, c: Int, d: Int) -> Float,
    val category: PidCategory,
    val isPremium: Boolean = false
)

object PidRegistry {

    val STANDARD_PIDS = listOf(
        // ENGINE
        PidDefinition("01","0C","RPM","rpm",0f,8000f,6000f,7500f,{a,b,_,_ -> ((a*256f)+b)/4f}, PidCategory.ENGINE),
        PidDefinition("01","0D","Velocidad","km/h",0f,255f,160f,220f,{a,_,_,_ -> a.toFloat()}, PidCategory.ENGINE),
        PidDefinition("01","04","Carga Motor","%",0f,100f,80f,95f,{a,_,_,_ -> a*100f/255f}, PidCategory.ENGINE),
        PidDefinition("01","0B","Presión MAP","kPa",0f,255f,200f,240f,{a,_,_,_ -> a.toFloat()}, PidCategory.ENGINE),
        PidDefinition("01","0E","Avance Enc.","°",-64f,63.5f,50f,60f,{a,_,_,_ -> a/2f-64f}, PidCategory.ENGINE),
        PidDefinition("01","11","Pos. Mariposa","%",0f,100f,80f,95f,{a,_,_,_ -> a*100f/255f}, PidCategory.ENGINE),
        PidDefinition("01","1F","T. desde Arranque","s",0f,65535f,10000f,60000f,{a,b,_,_ -> (a*256f)+b}, PidCategory.ENGINE),
        PidDefinition("01","43","Carga Absoluta","%",0f,255f,100f,150f,{a,b,_,_ -> ((a*256f)+b)*100f/255f}, PidCategory.ENGINE, isPremium=true),
        PidDefinition("01","45","Pos. Rel. Mariposa","%",0f,100f,80f,95f,{a,_,_,_ -> a*100f/255f}, PidCategory.ENGINE),
        PidDefinition("01","47","Pos. Mariposa B","%",0f,100f,80f,95f,{a,_,_,_ -> a*100f/255f}, PidCategory.ENGINE),
        PidDefinition("01","48","Pos. Mariposa C","%",0f,100f,80f,95f,{a,_,_,_ -> a*100f/255f}, PidCategory.ENGINE),
        PidDefinition("01","49","Pos. Pedal D","%",0f,100f,80f,95f,{a,_,_,_ -> a*100f/255f}, PidCategory.ENGINE),
        PidDefinition("01","4A","Pos. Pedal E","%",0f,100f,80f,95f,{a,_,_,_ -> a*100f/255f}, PidCategory.ENGINE),
        PidDefinition("01","4B","Pos. Pedal F","%",0f,100f,80f,95f,{a,_,_,_ -> a*100f/255f}, PidCategory.ENGINE),
        PidDefinition("01","61","Torque Motor","%",-125f,125f,100f,115f,{a,_,_,_ -> a.toFloat()-125f}, PidCategory.ENGINE, isPremium=true),
        PidDefinition("01","62","Torque Demanda","%",-125f,125f,100f,115f,{a,_,_,_ -> a.toFloat()-125f}, PidCategory.ENGINE, isPremium=true),
        PidDefinition("01","63","Torque Refer.","Nm",0f,65535f,0f,0f,{a,b,_,_ -> (a*256f)+b}, PidCategory.ENGINE, isPremium=true),
        
        // TEMPERATURE
        PidDefinition("01","05","Temp Motor","°C",-40f,215f,100f,110f,{a,_,_,_ -> a-40f}, PidCategory.TEMPERATURE),
        PidDefinition("01","0F","Temp Admisión","°C",-40f,215f,50f,80f,{a,_,_,_ -> a-40f}, PidCategory.TEMPERATURE),
        PidDefinition("01","46","Temp Ambiente","°C",-40f,215f,45f,55f,{a,_,_,_ -> a-40f}, PidCategory.TEMPERATURE),
        PidDefinition("01","5C","Temp Aceite","°C",-40f,210f,115f,130f,{a,_,_,_ -> a-40f}, PidCategory.TEMPERATURE, isPremium=true),
        PidDefinition("01","70","Temp Turbo","°C",-40f,215f,150f,190f,{a,_,_,_ -> a-40f}, PidCategory.ENGINE, isPremium=true),
        
        // FUEL
        PidDefinition("01","2F","Nivel Comb.","%",0f,100f,15f,5f,{a,_,_,_ -> a*100f/255f}, PidCategory.FUEL),
        PidDefinition("01","10","Flujo MAF","g/s",0f,655f,200f,400f,{a,b,_,_ -> ((a*256f)+b)/100f}, PidCategory.FUEL),
        PidDefinition("01","06","Trim Comb CT B1","%",-100f,99.2f,25f,40f,{a,_,_,_ -> (a-128)*100f/128f}, PidCategory.FUEL),
        PidDefinition("01","07","Trim Comb LT B1","%",-100f,99.2f,25f,40f,{a,_,_,_ -> (a-128)*100f/128f}, PidCategory.FUEL, isPremium=true),
        PidDefinition("01","0A","Presión Comb.","kPa",0f,765f,300f,400f,{a,_,_,_ -> a*3f}, PidCategory.FUEL),
        PidDefinition("01","22","Pres. Rail Rel.","kPa",0f,5177f,3000f,4000f,{a,b,_,_ -> ((a*256f)+b)*0.079f}, PidCategory.FUEL, isPremium=true),
        PidDefinition("01","23","Pres. Rail Abs.","kPa",0f,655350f,150000f,200000f,{a,b,_,_ -> ((a*256f)+b)*10f}, PidCategory.FUEL, isPremium=true),
        PidDefinition("01","51","Tipo Comb.","",0f,255f,0f,0f,{a,_,_,_ -> a.toFloat()}, PidCategory.FUEL),
        PidDefinition("01","52","Etanol Comb.","%",0f,100f,85f,95f,{a,_,_,_ -> a*100f/255f}, PidCategory.FUEL, isPremium=true),
        PidDefinition("01","5B","Vida Bat Híbrida","%",0f,100f,40f,20f,{a,_,_,_ -> a*100f/255f}, PidCategory.ELECTRICAL, isPremium=true),
        
        // ELECTRICAL
        PidDefinition("01","42","Voltaje ECU","V",0f,65.535f,15f,16f,{a,b,_,_ -> ((a*256f)+b)/1000f}, PidCategory.ELECTRICAL),
        
        // EMISSIONS
        PidDefinition("01","13","Sensores O2","",0f,255f,0f,0f,{a,_,_,_ -> a.toFloat()}, PidCategory.EMISSIONS, isPremium=true),
        PidDefinition("01","14","O2 B1S1 (V)","V",0f,1.275f,0.9f,1.1f,{a,_,_,_ -> a/200f}, PidCategory.EMISSIONS),
        PidDefinition("01","15","O2 B1S2 (V)","V",0f,1.275f,0.9f,1.1f,{a,_,_,_ -> a/200f}, PidCategory.EMISSIONS),
        PidDefinition("01","16","O2 B1S3 (V)","V",0f,1.275f,0.9f,1.1f,{a,_,_,_ -> a/200f}, PidCategory.EMISSIONS),
        PidDefinition("01","17","O2 B1S4 (V)","V",0f,1.275f,0.9f,1.1f,{a,_,_,_ -> a/200f}, PidCategory.EMISSIONS),
        PidDefinition("01","1C","Estándar OBD","",0f,255f,0f,0f,{a,_,_,_ -> a.toFloat()}, PidCategory.EMISSIONS),
        PidDefinition("01","21","Dist. con MIL","km",0f,65535f,5000f,10000f,{a,b,_,_ -> (a*256f)+b}, PidCategory.EMISSIONS),
        PidDefinition("01","2C","Ciclo EGR","%",0f,100f,80f,95f,{a,_,_,_ -> a*100f/255f}, PidCategory.EMISSIONS),
        PidDefinition("01","2D","Error EGR","%",-100f,99.2f,30f,50f,{a,_,_,_ -> a*100f/128f-100f}, PidCategory.EMISSIONS),
        PidDefinition("01","2E","Ciclo EVAP","%",0f,100f,80f,95f,{a,_,_,_ -> a*100f/255f}, PidCategory.EMISSIONS),
        PidDefinition("01","30","DTCs Calientes","",0f,255f,1f,5f,{a,_,_,_ -> a.toFloat()}, PidCategory.EMISSIONS),
        PidDefinition("01","31","Dist. tras Borrado","km",0f,65535f,0f,0f,{a,b,_,_ -> (a*256f)+b}, PidCategory.EMISSIONS),
        PidDefinition("01","33","Presión Baro","kPa",0f,255f,120f,150f,{a,_,_,_ -> a.toFloat()}, PidCategory.EMISSIONS),
        PidDefinition("01","44","Ratio Aire/Comb","",0f,2f,1.2f,1.5f,{a,b,_,_ -> 2f*((a*256f)+b)/65536f}, PidCategory.EMISSIONS, isPremium=true),
        
        // TRANSMISSION (Premium)
        PidDefinition("01","A4","Temp Trans","°C",-40f,215f,110f,130f,{a,_,_,_ -> a-40f}, PidCategory.TRANSMISSION, isPremium=true),
        PidDefinition("01","3C","Temp Cat B1S1","°C",-40f,6513.5f,800f,950f,{a,b,_,_ -> ((a*256f)+b)/10f-40f}, PidCategory.EMISSIONS, isPremium=true),
        PidDefinition("01","3D","Temp Cat B2S1","°C",-40f,6513.5f,800f,950f,{a,b,_,_ -> ((a*256f)+b)/10f-40f}, PidCategory.EMISSIONS, isPremium=true),
        PidDefinition("01","5A","Pedal Relat.","%",0f,100f,80f,95f,{a,_,_,_ -> a*100f/255f}, PidCategory.ENGINE),
        PidDefinition("01","5E","Consumo Comb.","L/h",0f,3212.75f,0f,0f,{a,b,_,_ -> ((a*256f)+b)/20f}, PidCategory.FUEL, isPremium=true)
    )

    /**
     * Manufacturer-specific PIDs (usually Mode 22).
     * These require specific ECU headers and often security access.
     */
    val MANUFACTURER_PIDS = mapOf(
        "FORD" to listOf(
            PidDefinition("22", "03E0", "Carga Alt.", "%", 0f, 100f, 90f, 98f, { a, _, _, _ -> a / 2.55f }, PidCategory.ELECTRICAL, true),
            PidDefinition("22", "0200", "Presión Aceite", "kPa", 0f, 1000f, 150f, 100f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ENGINE, true)
        ),
        "TOYOTA" to listOf(
            PidDefinition("21", "01", "Temp Bat HV", "°C", -40f, 100f, 50f, 65f, { a, _, _, _ -> a - 40f }, PidCategory.ELECTRICAL, true),
            PidDefinition("21", "02", "SOC Bat HV", "%", 0f, 100f, 40f, 20f, { a, _, _, _ -> a / 2.55f }, PidCategory.ELECTRICAL, true)
        ),
        "GM" to listOf(
            PidDefinition("22", "1940", "Temp Trans Fluid", "°C", -40f, 150f, 110f, 125f, { a, _, _, _ -> a - 40f }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "1153", "Vida Aceite", "%", 0f, 100f, 10f, 5f, { a, _, _, _ -> a / 2.55f }, PidCategory.ENGINE, true)
        ),
        "VOLKSWAGEN" to listOf(
            PidDefinition("22", "11BD", "Presión Turbo Deseada", "hPa", 0f, 3000f, 2200f, 2500f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ENGINE, true),
            PidDefinition("22", "11BE", "Presión Turbo Real", "hPa", 0f, 3000f, 2200f, 2500f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ENGINE, true)
        ),
        "HYUNDAI" to listOf(
            PidDefinition("22", "0101", "Temp Bat EV", "°C", -40f, 80f, 45f, 55f, { a, _, _, _ -> a - 40f }, PidCategory.ELECTRICAL, true),
            PidDefinition("22", "0105", "SOH Batería", "%", 0f, 100f, 80f, 70f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ELECTRICAL, true)
        )
    )

    val ACTIVE_TESTS = listOf(
        ActiveTest(
            id = "FUEL_PUMP",
            name = "Prueba Bomba Combustible",
            description = "Activa la bomba de combustible por 5 segundos para verificar presión.",
            startCommand = "300101", // Example UDS command
            stopCommand = "300100",
            durationMs = 5000,
            monitoredPids = listOf("010A"), // Monitor Fuel Pressure
            safetyConditions = listOf(SafetyCondition.ENGINE_OFF, SafetyCondition.BATTERY_ABOVE_12V)
        ),
        ActiveTest(
            id = "EVAP_VENT",
            name = "Solenoide EVAP Vent",
            description = "Cicla la válvula de ventilación del sistema EVAP.",
            startCommand = "2F011003", // Example Mode 2F (IO Control)
            stopCommand = "2F011000",
            durationMs = 10000,
            safetyConditions = listOf(SafetyCondition.ENGINE_OFF)
        ),
        ActiveTest(
            id = "ABS_PUMP",
            name = "Purga Bomba ABS",
            description = "Activa el motor de la bomba ABS para purgado de aire.",
            manufacturer = "GM",
            startCommand = "22123401", // Mocked GM specific
            stopCommand = "22123400",
            durationMs = 3000,
            safetyConditions = listOf(SafetyCondition.VEHICLE_STATIONARY, SafetyCondition.BATTERY_ABOVE_12V)
        )
    )

    fun getPid(mode: String, pid: String): PidDefinition? {
        val std = STANDARD_PIDS.find { it.mode == mode && it.pid == pid }
        if (std != null) return std
        
        // Search in manufacturer PIDs
        MANUFACTURER_PIDS.values.forEach { list ->
            val found = list.find { it.mode == mode && it.pid == pid }
            if (found != null) return found
        }
        
        return null
    }

    fun getOemPids(manufacturer: String): List<PidDefinition> {
        return MANUFACTURER_PIDS[manufacturer.uppercase()] ?: emptyList()
    }
}
