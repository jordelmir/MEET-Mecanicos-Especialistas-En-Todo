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
        PidDefinition("01","01","Estado Monitor","",0f,255f,0f,0f,{a,_,_,_ -> a.toFloat()}, PidCategory.EMISSIONS),
        PidDefinition("01","03","Estado Sist. Comb.","",0f,8f,0f,0f,{a,_,_,_ -> a.toFloat()}, PidCategory.FUEL),
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
        PidDefinition("01","5E","Consumo Comb.","L/h",0f,3212.75f,0f,0f,{a,b,_,_ -> ((a*256f)+b)/20f}, PidCategory.FUEL, isPremium=true),
        
        // ODOMETER / MILEAGE
        PidDefinition("01","A6","Odómetro Standard 01","km",0f,1000000f,0f,0f,{a,b,c,d -> ((a.toFloat()*16777216f)+(b.toFloat()*65536f)+(c.toFloat()*256f)+d.toFloat())/10f}, PidCategory.ENGINE, isPremium=true),
        PidDefinition("09","0D","Odómetro Standard 09","km",0f,1000000f,0f,0f,{a,b,c,d -> ((a.toFloat()*16777216f)+(b.toFloat()*65536f)+(c.toFloat()*256f)+d.toFloat())/10f}, PidCategory.ENGINE, isPremium=true),

        // VIRTUAL CALCULATED SENSORS (CUSTOM)
        PidDefinition("CALC", "_POWER", "Potencia Estimada", "hp", 0f, 500f, 350f, 450f, {_,_,_,_ -> 0f}, PidCategory.CUSTOM),
        PidDefinition("CALC", "_TORQUE", "Torque Estimado", "Nm", 0f, 600f, 450f, 550f, {_,_,_,_ -> 0f}, PidCategory.CUSTOM),
        PidDefinition("CALC", "_BOOST", "Turbo / Boost", "bar", -1f, 3f, 1.8f, 2.5f, {_,_,_,_ -> 0f}, PidCategory.CUSTOM),
        PidDefinition("CALC", "_ACCELERATION", "Aceleración G", "g", -2f, 2f, 1.2f, 1.5f, {_,_,_,_ -> 0f}, PidCategory.CUSTOM),
        PidDefinition("CALC", "_FUEL_RATE", "Consumo Instantáneo (L/h)", "L/h", 0f, 50f, 35f, 45f, {_,_,_,_ -> 0f}, PidCategory.CUSTOM),
        PidDefinition("CALC", "_FUEL_CONSUMPTION", "Consumo L/100km", "L/100km", 0f, 50f, 35f, 45f, {_,_,_,_ -> 0f}, PidCategory.CUSTOM),
        PidDefinition("CALC", "_TRIP_DISTANCE", "Distancia del Viaje", "km", 0f, 9999f, 0f, 0f, {_,_,_,_ -> 0f}, PidCategory.CUSTOM),
        PidDefinition("CALC", "_TOTAL_DISTANCE", "Distancia Total Odo", "km", 0f, 999999f, 0f, 0f, {_,_,_,_ -> 0f}, PidCategory.CUSTOM),
        PidDefinition("CALC", "_AVG_SPEED", "Velocidad Promedio", "km/h", 0f, 255f, 120f, 150f, {_,_,_,_ -> 0f}, PidCategory.CUSTOM),
        PidDefinition("CALC", "_FUEL_USED", "Combustible Usado (Viaje)", "L", 0f, 1000f, 0f, 0f, {_,_,_,_ -> 0f}, PidCategory.CUSTOM),
        PidDefinition("CALC", "_FUEL_USED_TOTAL", "Combustible Usado Total", "L", 0f, 99999f, 0f, 0f, {_,_,_,_ -> 0f}, PidCategory.CUSTOM),
        PidDefinition("CALC", "_AVG_CONSUMPTION", "Consumo Promedio Viaje", "L/100km", 0f, 50f, 0f, 0f, {_,_,_,_ -> 0f}, PidCategory.CUSTOM),
        PidDefinition("CALC", "_AVG_CONSUMPTION_TOTAL", "Consumo Promedio Total", "L/100km", 0f, 50f, 0f, 0f, {_,_,_,_ -> 0f}, PidCategory.CUSTOM),
        PidDefinition("CALC", "_FUEL_PRICE", "Costo Combustible Viaje", "$", 0f, 9999f, 0f, 0f, {_,_,_,_ -> 0f}, PidCategory.CUSTOM),
        PidDefinition("CALC", "_FUEL_PRICE_TOTAL", "Costo Combustible Total", "$", 0f, 99999f, 0f, 0f, {_,_,_,_ -> 0f}, PidCategory.CUSTOM),
        PidDefinition("CALC", "_CURRENT_TIME", "Hora del Sistema", "", 0f, 2359f, 0f, 0f, {_,_,_,_ -> 0f}, PidCategory.CUSTOM),
        PidDefinition("CALC", "_DTC_COUNT", "Cantidad de Códigos DTC", "", 0f, 50f, 1f, 3f, {_,_,_,_ -> 0f}, PidCategory.CUSTOM)
    )

    /**
     * Manufacturer-specific OEM PIDs (Modes 21, 22, UDS 22).
     * Complete world-class multi-brand diagnostic catalogue covering all major global platforms.
     */
    val MANUFACTURER_PIDS = mapOf(
        "TOYOTA" to listOf(
            PidDefinition("21", "01", "Temp Bat HV Híbrida", "°C", -40f, 100f, 50f, 65f, { a, _, _, _ -> a - 40f }, PidCategory.ELECTRICAL, true),
            PidDefinition("21", "02", "SOC Batería HV", "%", 0f, 100f, 40f, 20f, { a, _, _, _ -> a / 2.55f }, PidCategory.ELECTRICAL, true),
            PidDefinition("21", "67", "Temp Transmisión CVT / E-CVT", "°C", -40f, 180f, 110f, 130f, { a, _, _, _ -> a - 40f }, PidCategory.TRANSMISSION, true),
            PidDefinition("21", "C4", "Odómetro Toyota", "km", 0f, 1000000f, 0f, 0f, { a, b, c, _ -> (a.toFloat() * 65536f + b.toFloat() * 256f + c.toFloat()) }, PidCategory.ENGINE, true),
            PidDefinition("21", "82", "Voltaje Bloque 1 Bat HV", "V", 0f, 25f, 12f, 10f, { a, b, _, _ -> (a * 256f + b) / 100f }, PidCategory.ELECTRICAL, true),
            PidDefinition("21", "83", "Voltaje Bloque 2 Bat HV", "V", 0f, 25f, 12f, 10f, { a, b, _, _ -> (a * 256f + b) / 100f }, PidCategory.ELECTRICAL, true),
            PidDefinition("21", "84", "Voltaje Bloque 3 Bat HV", "V", 0f, 25f, 12f, 10f, { a, b, _, _ -> (a * 256f + b) / 100f }, PidCategory.ELECTRICAL, true),
            PidDefinition("21", "85", "Voltaje Bloque 4 Bat HV", "V", 0f, 25f, 12f, 10f, { a, b, _, _ -> (a * 256f + b) / 100f }, PidCategory.ELECTRICAL, true),
            PidDefinition("21", "90", "Temp Inversor MG1/MG2", "°C", -40f, 150f, 90f, 110f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            PidDefinition("21", "91", "Revoluciones Motor MG2", "rpm", -15000f, 15000f, 12000f, 14000f, { a, b, _, _ -> (a * 256f + b) - 32768f }, PidCategory.ELECTRICAL, true),
            PidDefinition("22", "1356", "Presión Aceite Freno Hidráulico", "bar", 0f, 200f, 150f, 180f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ENGINE, true)
        ),
        "FORD" to listOf(
            PidDefinition("22", "03E0", "Carga Alternador SmartCharge", "%", 0f, 100f, 90f, 98f, { a, _, _, _ -> a / 2.55f }, PidCategory.ELECTRICAL, true),
            PidDefinition("22", "0200", "Presión Aceite Motor", "kPa", 0f, 1000f, 150f, 100f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ENGINE, true),
            PidDefinition("22", "1E23", "Temp Aceite Motor EOT", "°C", -40f, 200f, 120f, 140f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            PidDefinition("22", "1E1C", "Temp Culata CHT", "°C", -40f, 250f, 115f, 130f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            PidDefinition("22", "1E1F", "Temp Transmisión TFT 6R/10R", "°C", -40f, 180f, 110f, 130f, { a, b, _, _ -> ((a * 256f + b) / 16f) - 40f }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "0234", "Presión Riel Combustible FRP", "kPa", 0f, 250000f, 180000f, 220000f, { a, b, _, _ -> (a * 256f + b) * 10f }, PidCategory.FUEL, true),
            PidDefinition("22", "042F", "Carga Hollín DPF", "%", 0f, 100f, 80f, 95f, { a, _, _, _ -> a / 2.55f }, PidCategory.EMISSIONS, true),
            PidDefinition("22", "0430", "Presión Boost Turbo EcoBoost", "psi", -14.7f, 35f, 22f, 28f, { a, b, _, _ -> ((a * 256f + b) / 100f) - 14.7f }, PidCategory.ENGINE, true),
            PidDefinition("22", "DD01", "Odómetro Ford", "km", 0f, 1000000f, 0f, 0f, { a, b, c, d -> ((a.toFloat()*16777216f)+(b.toFloat()*65536f)+(c.toFloat()*256f)+d.toFloat())/10f }, PidCategory.ENGINE, true)
        ),
        "GM" to listOf(
            PidDefinition("22", "1940", "Temp Transmisión 6L/8L/10L", "°C", -40f, 160f, 110f, 130f, { a, _, _, _ -> a - 40f }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "1153", "Vida Útil Aceite Motor", "%", 0f, 100f, 15f, 5f, { a, _, _, _ -> a / 2.55f }, PidCategory.ENGINE, true),
            PidDefinition("22", "0513", "Temp Aceite Motor", "°C", -40f, 200f, 120f, 140f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            PidDefinition("22", "1154", "Presión Aceite Motor GM", "kPa", 0f, 1000f, 180f, 120f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ENGINE, true),
            PidDefinition("22", "184B", "Desactivación Cilindros AFM/DFM", "", 0f, 8f, 0f, 0f, { a, _, _, _ -> a.toFloat() }, PidCategory.ENGINE, true),
            PidDefinition("22", "1941", "Resbalamiento Convertidor Torque", "rpm", -500f, 2500f, 150f, 400f, { a, b, _, _ -> (a * 256f + b) - 32768f }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "1198", "Retardo Total Detonación Knock", "°", 0f, 25f, 4f, 8f, { a, _, _, _ -> a / 2f }, PidCategory.ENGINE, true),
            PidDefinition("22", "1A6C", "Odómetro GM", "km", 0f, 1000000f, 0f, 0f, { a, b, c, _ -> (a.toFloat() * 65536f + b.toFloat() * 256f + c.toFloat()) }, PidCategory.ENGINE, true)
        ),
        "VOLKSWAGEN" to listOf(
            PidDefinition("22", "11BD", "Presión Turbo Solicitada", "hPa", 0f, 3500f, 2400f, 2800f, { a, b, _, _ -> (a * 256f + b).toFloat() }, PidCategory.ENGINE, true),
            PidDefinition("22", "11BE", "Presión Turbo Real", "hPa", 0f, 3500f, 2400f, 2800f, { a, b, _, _ -> (a * 256f + b).toFloat() }, PidCategory.ENGINE, true),
            PidDefinition("22", "2032", "Temp Aceite Motor G8", "°C", -40f, 200f, 120f, 140f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            PidDefinition("22", "1942", "Temp Embrague DSG DQ200/DQ250/DQ381", "°C", -40f, 250f, 140f, 170f, { a, _, _, _ -> a - 40f }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "2045", "Masa Hollín DPF Medida", "g", 0f, 80f, 24f, 40f, { a, b, _, _ -> (a * 256f + b) / 100f }, PidCategory.EMISSIONS, true),
            PidDefinition("22", "2046", "Masa Cenizas DPF Acumulada", "g", 0f, 150f, 70f, 90f, { a, b, _, _ -> (a * 256f + b) / 100f }, PidCategory.EMISSIONS, true),
            PidDefinition("22", "115A", "Presión Riel Common Rail HPFP", "bar", 0f, 2500f, 1800f, 2200f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.FUEL, true),
            PidDefinition("22", "118F", "Torsión / Desfase Árbol Levas", "°", -15f, 15f, 3f, 6f, { a, b, _, _ -> ((a * 256f + b) / 10f) - 100f }, PidCategory.ENGINE, true),
            PidDefinition("22", "2203", "Odómetro VAG", "km", 0f, 1000000f, 0f, 0f, { a, b, c, d -> ((a.toFloat() * 16777216f) + (b.toFloat() * 65536f) + (c.toFloat() * 256f) + d.toFloat()) / 1000f }, PidCategory.ENGINE, true)
        ),
        "HYUNDAI" to listOf(
            // Hyundai Gasolina (Accent / Verna 1.6L G4ED, Getz, Matrix, Elantra, Tucson, Sonata)
            PidDefinition("21", "01", "Ancho Pulso Inyector", "ms", 0f, 30f, 15f, 25f, { a, b, _, _ -> (a * 256f + b) / 1000f }, PidCategory.FUEL, true),
            PidDefinition("21", "01", "Temp Fluido ATF (Caja Auto)", "°C", -40f, 180f, 110f, 130f, { _, _, c, _ -> c - 40f }, PidCategory.TRANSMISSION, true),
            PidDefinition("21", "01", "Marcha Actual A4AF3/A6F", "", 0f, 8f, 0f, 0f, { _, _, _, d -> (d and 0x0F).toFloat() }, PidCategory.TRANSMISSION, true),
            PidDefinition("21", "02", "Retardo Encendido Knock", "°", 0f, 25f, 5f, 10f, { a, _, _, _ -> a / 2f }, PidCategory.ENGINE, true),
            PidDefinition("21", "02", "Válvula Ralentí ISCA", "%", 0f, 100f, 80f, 95f, { _, b, _, _ -> b * 100f / 255f }, PidCategory.ENGINE, true),
            PidDefinition("21", "02", "Presión Gas A/C", "bar", 0f, 35f, 25f, 30f, { _, _, c, _ -> c * 0.14f }, PidCategory.ENGINE, true),
            // Hyundai Modern Smartstream & Turbo T-GDI
            PidDefinition("22", "1105", "Presión Turbo Smartstream", "kPa", 0f, 350f, 220f, 260f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ENGINE, true),
            PidDefinition("22", "1108", "Temp Aceite Motor CVVD", "°C", -40f, 200f, 120f, 140f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            // Hyundai EV / Híbridos Modernos (Ioniq, Kona EV)
            PidDefinition("22", "0101", "Temp Bat EV", "°C", -40f, 80f, 45f, 55f, { a, _, _, _ -> a - 40f }, PidCategory.ELECTRICAL, true),
            PidDefinition("22", "0105", "SOH Batería EV", "%", 0f, 100f, 80f, 70f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ELECTRICAL, true),
            PidDefinition("22", "0106", "SOC Batería EV", "%", 0f, 100f, 20f, 10f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ELECTRICAL, true),
            PidDefinition("22", "0102", "Temp Motor Eléctrico", "°C", -40f, 200f, 120f, 150f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true)
        ),
        "NISSAN" to listOf(
            PidDefinition("22", "1166", "Temp Transmisión CVT Jatco", "°C", -40f, 200f, 105f, 125f, { a, _, _, _ -> a - 40f }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "1167", "Contador Deterioro Fluido CVT", "pts", 0f, 250000f, 180000f, 210000f, { a, b, c, _ -> (a.toFloat() * 65536f + b.toFloat() * 256f + c.toFloat()) }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "1183", "Presión Turbo DIG-T", "kPa", 0f, 300f, 200f, 250f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ENGINE, true),
            PidDefinition("22", "115E", "Temp Aceite Motor", "°C", -40f, 200f, 120f, 140f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            PidDefinition("22", "1154", "Ángulo VTC Árbol Levas", "°", -30f, 60f, 45f, 55f, { a, _, _, _ -> a / 2f - 30f }, PidCategory.ENGINE, true),
            PidDefinition("22", "1207", "SOC Bat Leaf EV", "%", 0f, 100f, 20f, 10f, { a, b, _, _ -> (a * 256f + b) / 100f }, PidCategory.ELECTRICAL, true),
            PidDefinition("22", "1208", "SOH Bat Leaf EV", "%", 0f, 100f, 75f, 65f, { a, b, _, _ -> (a * 256f + b) / 100f }, PidCategory.ELECTRICAL, true)
        ),
        "HONDA" to listOf(
            PidDefinition("22", "0130", "Estado VTEC / VTC", "", 0f, 1f, 0f, 0f, { a, _, _, _ -> a.toFloat() }, PidCategory.ENGINE, true),
            PidDefinition("22", "0156", "Presión Transmisión CVT", "kPa", 0f, 3500f, 2200f, 2800f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "0157", "Temp Transmisión AT / CVT", "°C", -40f, 180f, 110f, 130f, { a, _, _, _ -> a - 40f }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "0145", "Temp Aceite Motor", "°C", -40f, 200f, 120f, 140f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            PidDefinition("22", "0160", "SOC Batería Híbrida IMA/e:HEV", "%", 0f, 100f, 20f, 10f, { a, _, _, _ -> a / 2.55f }, PidCategory.ELECTRICAL, true),
            PidDefinition("22", "0165", "Corriente Motor Híbrido", "A", -200f, 200f, 150f, 180f, { a, b, _, _ -> (a * 256f + b) - 500f }, PidCategory.ELECTRICAL, true)
        ),
        "BMW" to listOf(
            PidDefinition("22", "1304", "Temp Aceite Motor DME", "°C", -40f, 200f, 120f, 140f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            PidDefinition("22", "1306", "Temp Salida Radiador", "°C", -40f, 150f, 95f, 110f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            PidDefinition("22", "133F", "Presión Turbo TwinPower Actual", "mbar", 0f, 3500f, 2400f, 2900f, { a, b, _, _ -> (a * 256f + b).toFloat() }, PidCategory.ENGINE, true),
            PidDefinition("22", "1340", "Presión Turbo TwinPower Setpoint", "mbar", 0f, 3500f, 2400f, 2900f, { a, b, _, _ -> (a * 256f + b).toFloat() }, PidCategory.ENGINE, true),
            PidDefinition("22", "1349", "Presión Riel Diésel / Gasolina HDP", "bar", 0f, 2500f, 1800f, 2200f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.FUEL, true),
            PidDefinition("22", "1505", "Temp Módulo DME/DDE", "°C", -40f, 120f, 80f, 95f, { a, _, _, _ -> a - 40f }, PidCategory.ELECTRICAL, true),
            PidDefinition("22", "1802", "Temp Transmisión ZF 8HP", "°C", -40f, 180f, 110f, 130f, { a, _, _, _ -> a - 40f }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "1310", "Ángulo Excéntrico Valvetronic", "°", 0f, 200f, 160f, 180f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ENGINE, true),
            PidDefinition("22", "2BC0", "Odómetro BMW", "km", 0f, 1000000f, 0f, 0f, { a, b, c, d -> ((a.toFloat()*16777216f)+(b.toFloat()*65536f)+(c.toFloat()*256f)+d.toFloat())/10f }, PidCategory.ENGINE, true)
        ),
        "MERCEDES" to listOf(
            PidDefinition("22", "1038", "Temp Aceite Motor CDI/CGI", "°C", -40f, 200f, 120f, 140f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            PidDefinition("22", "104A", "Nivel AdBlue / DEF BlueTEC", "%", 0f, 100f, 15f, 5f, { a, _, _, _ -> a / 2.55f }, PidCategory.EMISSIONS, true),
            PidDefinition("22", "1053", "Carga Hollín Filtro DPF", "%", 0f, 100f, 80f, 95f, { a, _, _, _ -> a / 2.55f }, PidCategory.EMISSIONS, true),
            PidDefinition("22", "1040", "Posición Actuador Turbo VNT", "%", 0f, 100f, 85f, 95f, { a, _, _, _ -> a / 2.55f }, PidCategory.ENGINE, true),
            PidDefinition("22", "1905", "Temp Transmisión 7G/9G-Tronic", "°C", -40f, 180f, 110f, 130f, { a, _, _, _ -> a - 40f }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "1058", "Presión Riel CDI", "bar", 0f, 2500f, 1800f, 2200f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.FUEL, true),
            PidDefinition("22", "1002", "Odómetro Mercedes", "km", 0f, 1000000f, 0f, 0f, { a, b, c, d -> ((a.toFloat()*16777216f)+(b.toFloat()*65536f)+(c.toFloat()*256f)+d.toFloat()) }, PidCategory.ENGINE, true)
        ),
        "SUBARU" to listOf(
            PidDefinition("22", "F002", "Corrección Knock Retard", "°", -25f, 25f, -5f, -10f, { a, _, _, _ -> (a - 128f) / 2f }, PidCategory.ENGINE, true),
            PidDefinition("22", "F00B", "Temp Aceite Motor Boxer", "°C", -40f, 200f, 120f, 140f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            PidDefinition("22", "F006", "Presión Boost Turbo WRX/STI", "psi", -14.7f, 35f, 20f, 26f, { a, _, _, _ -> (a - 128f) * 37f / 255f }, PidCategory.ENGINE, true),
            PidDefinition("22", "F032", "Temp Transmisión Lineartronic CVT", "°C", -40f, 180f, 110f, 130f, { a, _, _, _ -> a - 40f }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "F035", "Temp Diferencial Trasero AWD", "°C", -40f, 200f, 100f, 120f, { a, _, _, _ -> a - 40f }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "F014", "Multiplicador Avance Ignición (IAM)", "", 0f, 1f, 0.8f, 0.5f, { a, _, _, _ -> a / 16f }, PidCategory.ENGINE, true)
        ),
        "MAZDA" to listOf(
            PidDefinition("22", "2186", "Presión Boost Skyactiv-G/D", "kPa", 0f, 350f, 220f, 270f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ENGINE, true),
            PidDefinition("22", "2124", "Temp Aceite Motor Skyactiv", "°C", -40f, 200f, 120f, 140f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            PidDefinition("22", "2150", "Temp Transmisión Skyactiv-Drive 6AT", "°C", -40f, 180f, 110f, 130f, { a, _, _, _ -> a - 40f }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "F432", "Torque Transferencia i-ACTIV AWD", "Nm", 0f, 1500f, 1000f, 1300f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "2195", "Temp Gases Escape EGT", "°C", 0f, 1000f, 800f, 920f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.TEMPERATURE, true)
        ),
        "KIA" to listOf(
            PidDefinition("22", "E003", "SOC Batería EV", "%", 0f, 100f, 20f, 10f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ELECTRICAL, true),
            PidDefinition("22", "E004", "Temp Motor Eléctrico", "°C", -40f, 200f, 120f, 150f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            PidDefinition("22", "E005", "Temp Inversor Potencia", "°C", -40f, 120f, 80f, 95f, { a, _, _, _ -> a - 40f }, PidCategory.ELECTRICAL, true),
            PidDefinition("22", "E010", "Potencia Regenerativa EV", "kW", 0f, 150f, 80f, 120f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ELECTRICAL, true)
        ),
        "CHRYSLER" to listOf(
            PidDefinition("22", "1128", "Estado Desactivación HEMI MDS", "", 0f, 8f, 0f, 0f, { a, _, _, _ -> a.toFloat() }, PidCategory.ENGINE, true),
            PidDefinition("22", "110A", "Presión Aceite HEMI / Pentastar", "kPa", 0f, 1000f, 180f, 120f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ENGINE, true),
            PidDefinition("22", "1190", "Temp Transmisión Torqueflite 8HP", "°C", -40f, 180f, 110f, 130f, { a, _, _, _ -> a - 40f }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "1160", "Estado Módulo TIPM / BCM", "", 0f, 255f, 0f, 0f, { a, _, _, _ -> a.toFloat() }, PidCategory.ELECTRICAL, true)
        ),
        "MITSUBISHI" to listOf(
            PidDefinition("22", "0201", "SOC Batería Outlander PHEV", "%", 0f, 100f, 20f, 10f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ELECTRICAL, true),
            PidDefinition("22", "0210", "Modo Vectorización S-AWC", "", 0f, 4f, 0f, 0f, { a, _, _, _ -> a.toFloat() }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "0215", "Temp Motor Eléctrico Trasero", "°C", -40f, 200f, 120f, 150f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            PidDefinition("22", "0218", "Temp Transmisión INVECS-III CVT", "°C", -40f, 180f, 110f, 130f, { a, _, _, _ -> a - 40f }, PidCategory.TRANSMISSION, true)
        ),
        "VOLVO" to listOf(
            PidDefinition("22", "4028", "Presión Turbo Drive-E", "kPa", 0f, 350f, 220f, 270f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ENGINE, true),
            PidDefinition("22", "4032", "Presión Supercargador Roots", "kPa", 0f, 300f, 200f, 250f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ENGINE, true),
            PidDefinition("22", "4040", "Temp Aceite Motor VEA", "°C", -40f, 200f, 120f, 140f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            PidDefinition("22", "4060", "Estado Control Estabilidad DSTC", "", 0f, 3f, 0f, 0f, { a, _, _, _ -> a.toFloat() }, PidCategory.ENGINE, true)
        ),
        "RENAULT" to listOf(
            PidDefinition("22", "0105", "Masa Cenizas DPF dCi", "g", 0f, 80f, 25f, 40f, { a, b, _, _ -> (a * 256f + b) / 100f }, PidCategory.EMISSIONS, true),
            PidDefinition("22", "0112", "Presión Turbo dCi / TCe", "mbar", 0f, 3500f, 2300f, 2800f, { a, b, _, _ -> (a * 256f + b).toFloat() }, PidCategory.ENGINE, true),
            PidDefinition("22", "0120", "Presión Riel Diésel Common Rail", "bar", 0f, 2200f, 1600f, 2000f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.FUEL, true),
            PidDefinition("22", "0145", "Temp Embrague Transmisión EDC", "°C", -40f, 250f, 140f, 170f, { a, _, _, _ -> a - 40f }, PidCategory.TRANSMISSION, true)
        ),
        "PEUGEOT" to listOf(
            PidDefinition("22", "1104", "Nivel Depósito Urea AdBlue BlueHDi", "%", 0f, 100f, 15f, 5f, { a, _, _, _ -> a / 2.55f }, PidCategory.EMISSIONS, true),
            PidDefinition("22", "110A", "Presión Diferencial Filtro FAP", "mbar", 0f, 600f, 200f, 350f, { a, b, _, _ -> (a * 256f + b).toFloat() }, PidCategory.EMISSIONS, true),
            PidDefinition("22", "1125", "Temp Transmisión EAT6 / EAT8", "°C", -40f, 180f, 110f, 130f, { a, _, _, _ -> a - 40f }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "1132", "Temp Aceite Motor PureTech / HDi", "°C", -40f, 200f, 120f, 140f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true)
        ),
        "SUZUKI" to listOf(
            PidDefinition("22", "010A", "Presión Boost Boosterjet", "kPa", 0f, 280f, 190f, 230f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ENGINE, true),
            PidDefinition("22", "0120", "Temp Fluido CVT Suzuki", "°C", -40f, 180f, 110f, 130f, { a, _, _, _ -> a - 40f }, PidCategory.TRANSMISSION, true),
            PidDefinition("22", "0135", "Estado Tracción AllGrip AWD", "", 0f, 4f, 0f, 0f, { a, _, _, _ -> a.toFloat() }, PidCategory.TRANSMISSION, true)
        ),
        "TESLA_EV" to listOf(
            PidDefinition("22", "0101", "Voltaje Pack HV Total", "V", 0f, 500f, 320f, 300f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ELECTRICAL, true),
            PidDefinition("22", "0102", "Corriente Batería HV", "A", -1200f, 1200f, 800f, 1000f, { a, b, _, _ -> (a * 256f + b) - 2000f }, PidCategory.ELECTRICAL, true),
            PidDefinition("22", "0105", "Temp Promedio Pack HV", "°C", -40f, 85f, 50f, 65f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            PidDefinition("22", "0108", "Temp Inversor Drive Unit", "°C", -40f, 150f, 90f, 115f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            PidDefinition("22", "0109", "Temp Estátor Motor", "°C", -40f, 180f, 120f, 150f, { a, _, _, _ -> a - 40f }, PidCategory.TEMPERATURE, true),
            PidDefinition("22", "0110", "Energía Remanente Útil", "kWh", 0f, 150f, 15f, 5f, { a, b, _, _ -> (a * 256f + b) / 10f }, PidCategory.ELECTRICAL, true)
        )
    )

    val ACTIVE_TESTS = listOf(
        // ══════════════════════════════════════════════════════
        // SISTEMA DE COMBUSTIBLE
        // ══════════════════════════════════════════════════════
        ActiveTest(
            id = "FUEL_PUMP",
            name = "Prueba Bomba Combustible",
            description = "Activa la bomba de combustible por 5 segundos para verificar presión y flujo. " +
                "PROCEDIMIENTO: Conecte un manómetro al riel. La presión debe subir a spec del fabricante (35-65 PSI típico) " +
                "y mantenerse estable sin caer más de 5 PSI en 10 minutos tras apagar.",
            startCommand = "300101",
            stopCommand = "300100",
            durationMs = 5000,
            monitoredPids = listOf("010A"),
            safetyConditions = listOf(SafetyCondition.ENGINE_OFF, SafetyCondition.BATTERY_ABOVE_12V)
        ),
        ActiveTest(
            id = "INJECTOR_BALANCE",
            name = "Balance de Inyectores",
            description = "Pulsa cada inyector individualmente y mide la caída de presión en el riel. " +
                "PROCEDIMIENTO: Se activa cada inyector por un pulso corto (50ms). La caída de presión " +
                "debe ser uniforme entre todos los cilindros (±10%). Una caída menor indica inyector obstruido; " +
                "una caída mayor indica inyector con fuga o atascado abierto.",
            startCommand = "3001FF",
            stopCommand = "300100",
            durationMs = 15000,
            monitoredPids = listOf("010A", "0106"),
            safetyConditions = listOf(SafetyCondition.ENGINE_OFF, SafetyCondition.BATTERY_ABOVE_12V)
        ),

        // ══════════════════════════════════════════════════════
        // SISTEMA DE EMISIONES / EVAP
        // ══════════════════════════════════════════════════════
        ActiveTest(
            id = "EVAP_VENT",
            name = "Solenoide EVAP Vent",
            description = "Cicla la válvula de ventilación del sistema evaporativo (EVAP). " +
                "PROCEDIMIENTO: Escuche un 'click' audible del solenoide cerca del cánister de carbón. " +
                "Si no se escucha, el solenoide puede estar quemado o desconectado. " +
                "NOTA: Esta prueba es esencial para resolver códigos P0440-P0457.",
            startCommand = "2F011003",
            stopCommand = "2F011000",
            durationMs = 10000,
            safetyConditions = listOf(SafetyCondition.ENGINE_OFF)
        ),
        ActiveTest(
            id = "EVAP_PURGE",
            name = "Válvula de Purga EVAP",
            description = "Activa la válvula de purga canister que controla el flujo de vapores al múltiple. " +
                "PROCEDIMIENTO: Con motor en ralentí, active la válvula. Las RPM deben bajar ligeramente " +
                "(vapores de gasolina enriquecen la mezcla). Si NO hay cambio, la válvula está atascada cerrada. " +
                "Si las RPM caen demasiado, está atascada abierta. Códigos relacionados: P0441, P0446.",
            startCommand = "2F011103",
            stopCommand = "2F011100",
            durationMs = 8000,
            monitoredPids = listOf("010C", "0106"),
            safetyConditions = listOf(SafetyCondition.ENGINE_RUNNING, SafetyCondition.VEHICLE_STATIONARY)
        ),
        ActiveTest(
            id = "EGR_VALVE",
            name = "Válvula EGR",
            description = "Abre la válvula de Recirculación de Gases de Escape (EGR). " +
                "PROCEDIMIENTO: En ralentí, al abrir la EGR debe ingresar gas inerte al cilindro, " +
                "causando ralentí inestable o incluso que el motor se apague. Si NO hay efecto, " +
                "la EGR está atascada cerrada o los pasajes de EGR están tapados con carbón. " +
                "Códigos relacionados: P0400-P0409.",
            startCommand = "2F011203",
            stopCommand = "2F011200",
            durationMs = 5000,
            monitoredPids = listOf("010C", "0104", "012C"),
            safetyConditions = listOf(SafetyCondition.ENGINE_RUNNING, SafetyCondition.VEHICLE_STATIONARY)
        ),
        ActiveTest(
            id = "SECONDARY_AIR",
            name = "Bomba de Aire Secundario (AIR)",
            description = "Activa la bomba de aire secundario que inyecta aire fresco en el múltiple de escape " +
                "para calentar el catalizador más rápido durante arranques en frío. " +
                "PROCEDIMIENTO: Debe escucharse el zumbido de la bomba eléctrica y sentir aire en el tubo de salida. " +
                "Si no funciona, verifique relé, fusible y motor de la bomba. Códigos: P0410-P0419.",
            startCommand = "2F011303",
            stopCommand = "2F011300",
            durationMs = 10000,
            monitoredPids = listOf("0114", "0106"),
            safetyConditions = listOf(SafetyCondition.ENGINE_RUNNING, SafetyCondition.VEHICLE_STATIONARY)
        ),

        // ══════════════════════════════════════════════════════
        // SISTEMA DE ENFRIAMIENTO
        // ══════════════════════════════════════════════════════
        ActiveTest(
            id = "COOLING_FAN_LOW",
            name = "Electroventilador — Velocidad Baja",
            description = "Activa el ventilador del radiador en velocidad baja. " +
                "PROCEDIMIENTO: El ventilador debe girar de inmediato. Si no lo hace: " +
                "1) Verifique relé de baja velocidad, 2) Verifique fusible, 3) Verifique motor del ventilador " +
                "con voltaje directo de batería. Si gira con voltaje directo pero no con la prueba, " +
                "el problema es el relé o el circuito de control de la PCM.",
            startCommand = "2F010103",
            stopCommand = "2F010100",
            durationMs = 10000,
            monitoredPids = listOf("0105"),
            safetyConditions = listOf(SafetyCondition.VEHICLE_STATIONARY, SafetyCondition.BATTERY_ABOVE_12V)
        ),
        ActiveTest(
            id = "COOLING_FAN_HIGH",
            name = "Electroventilador — Velocidad Alta",
            description = "Activa el ventilador del radiador en velocidad alta. " +
                "PROCEDIMIENTO: El ventilador debe girar a máxima velocidad con mayor ruido. " +
                "Esta velocidad se usa cuando el A/C está encendido o la temperatura supera ~108°C. " +
                "Si la velocidad baja funciona pero alta no, el problema es el relé de alta velocidad " +
                "o la resistencia del módulo de control del ventilador.",
            startCommand = "2F010203",
            stopCommand = "2F010200",
            durationMs = 10000,
            monitoredPids = listOf("0105"),
            safetyConditions = listOf(SafetyCondition.VEHICLE_STATIONARY, SafetyCondition.BATTERY_ABOVE_12V)
        ),

        // ══════════════════════════════════════════════════════
        // SISTEMA DE ACELERACIÓN / RALENTÍ
        // ══════════════════════════════════════════════════════
        ActiveTest(
            id = "IDLE_SPEED_UP",
            name = "Elevar RPM de Ralentí",
            description = "Comanda a la PCM que suba las RPM de ralentí (típicamente +200 RPM). " +
                "PROCEDIMIENTO: Las RPM deben subir suavemente. Esto confirma que el sistema de control de ralentí " +
                "(IAC o cuerpo de aceleración electrónico) responde correctamente. Si las RPM no cambian, " +
                "el motor del IAC puede estar atascado o el cuerpo de aceleración sucio/dañado.",
            startCommand = "2F010503",
            stopCommand = "2F010500",
            durationMs = 8000,
            monitoredPids = listOf("010C", "0111"),
            safetyConditions = listOf(SafetyCondition.ENGINE_RUNNING, SafetyCondition.VEHICLE_STATIONARY, SafetyCondition.TRANS_IN_PARK)
        ),
        ActiveTest(
            id = "THROTTLE_BODY",
            name = "Cuerpo de Aceleración (TAC)",
            description = "Comanda apertura/cierre del cuerpo de aceleración electrónico (Drive-by-Wire). " +
                "PROCEDIMIENTO: La mariposa debe moverse suavemente de 0% a ~25% y volver. " +
                "Si hay puntos muertos, ruido, o no se mueve, el motor del TAC está dañado. " +
                "ADVERTENCIA: Nunca limpie un TAC electrónico con solventes agresivos, use solo spray dieléctrico.",
            startCommand = "2F011403",
            stopCommand = "2F011400",
            durationMs = 6000,
            monitoredPids = listOf("0111", "010C"),
            safetyConditions = listOf(SafetyCondition.ENGINE_OFF, SafetyCondition.BATTERY_ABOVE_12V)
        ),

        // ══════════════════════════════════════════════════════
        // TRANSMISIÓN AUTOMÁTICA
        // ══════════════════════════════════════════════════════
        ActiveTest(
            id = "TCC_SOLENOID",
            name = "Solenoide TCC (Lock-Up Convertidor)",
            description = "Activa el embrague del convertidor de torque (TCC). " +
                "PROCEDIMIENTO: En carretera a ~60 km/h en D, al activar las RPM deben bajar ~200-400 RPM " +
                "(el convertidor se bloquea mecánicamente). Si NO hay cambio de RPM, el solenoide TCC " +
                "está defectuoso o hay desgaste en el embrague del convertidor. Códigos: P0740-P0744.",
            startCommand = "2F020103",
            stopCommand = "2F020100",
            durationMs = 8000,
            monitoredPids = listOf("010C", "010D"),
            safetyConditions = listOf(SafetyCondition.ENGINE_RUNNING)
        ),
        ActiveTest(
            id = "SHIFT_SOLENOID_A",
            name = "Solenoide de Cambio A (1-2)",
            description = "Activa el solenoide de cambio A de la transmisión automática. " +
                "PROCEDIMIENTO: Debe escucharse un 'click' del solenoide. En vehículos con transmisión " +
                "electrónica (4L60E, 4T65E, etc.), este solenoide controla el cambio de 1ra a 2da. " +
                "Mida resistencia del solenoide: 20-30Ω es normal. Códigos: P0750-P0756.",
            startCommand = "2F020203",
            stopCommand = "2F020200",
            durationMs = 5000,
            safetyConditions = listOf(SafetyCondition.VEHICLE_STATIONARY, SafetyCondition.TRANS_IN_PARK)
        ),

        // ══════════════════════════════════════════════════════
        // AIRE ACONDICIONADO
        // ══════════════════════════════════════════════════════
        ActiveTest(
            id = "AC_COMPRESSOR",
            name = "Embrague Compresor A/C",
            description = "Activa el embrague electromagnético del compresor de A/C. " +
                "PROCEDIMIENTO: Debe escucharse el 'click' del embrague y las RPM deben subir ligeramente " +
                "(la PCM compensa la carga extra). La polea del compresor debe girar junto con el eje. " +
                "Si no se activa: verifique presión de refrigerante (interruptor de baja), relé del A/C, " +
                "y bobina del embrague (~3-5Ω de resistencia).",
            startCommand = "2F010303",
            stopCommand = "2F010300",
            durationMs = 10000,
            monitoredPids = listOf("010C", "0104"),
            safetyConditions = listOf(SafetyCondition.ENGINE_RUNNING, SafetyCondition.VEHICLE_STATIONARY)
        ),

        // ══════════════════════════════════════════════════════
        // FRENOS / ABS
        // ══════════════════════════════════════════════════════
        ActiveTest(
            id = "ABS_PUMP",
            name = "Purga Bomba ABS",
            description = "Activa el motor de la bomba hidráulica del ABS para purgado de aire. " +
                "PROCEDIMIENTO: La bomba debe zumbar durante la prueba. Esta función es ESENCIAL " +
                "después de reemplazar caliper, cilindro maestro, o líneas de freno. " +
                "ADVERTENCIA: Mantenga el depósito de líquido de frenos lleno durante toda la prueba. " +
                "Purgue en el orden: rueda más lejana al maestro → más cercana.",
            manufacturer = "GM",
            startCommand = "22123401",
            stopCommand = "22123400",
            durationMs = 3000,
            safetyConditions = listOf(SafetyCondition.VEHICLE_STATIONARY, SafetyCondition.BATTERY_ABOVE_12V)
        ),

        // ══════════════════════════════════════════════════════
        // DIÉSEL — Bujías Incandescentes
        // ══════════════════════════════════════════════════════
        ActiveTest(
            id = "GLOW_PLUGS",
            name = "Bujías Incandescentes (Diésel)",
            description = "Activa las bujías incandescentes (glow plugs) para verificar su funcionamiento. " +
                "PROCEDIMIENTO: Cada bujía debe consumir 5-10A. Mida con pinza amperimétrica en cada cable. " +
                "Una bujía con consumo 0A está abierta. Después de 10 seg, la punta debe estar al rojo vivo. " +
                "NOTA: En motores diésel modernos (common rail), bujías defectuosas causan humo blanco " +
                "y dificultad para arrancar en frío. Códigos: P0380-P0386.",
            startCommand = "2F010603",
            stopCommand = "2F010600",
            durationMs = 10000,
            monitoredPids = listOf("0142"),
            safetyConditions = listOf(SafetyCondition.ENGINE_OFF, SafetyCondition.BATTERY_ABOVE_12V)
        ),

        // ══════════════════════════════════════════════════════
        // TURBO
        // ══════════════════════════════════════════════════════
        ActiveTest(
            id = "TURBO_WASTEGATE",
            name = "Wastegate / Válvula de Alivio Turbo",
            description = "Comanda la apertura de la wastegate electrónica del turbocompresor. " +
                "PROCEDIMIENTO: Con motor en ralentí, la wastegate debe moverse libremente. " +
                "Verifique que el vástago del actuador se mueva suavemente sin juego excesivo. " +
                "Si no responde, el actuador (vacío o electrónico) puede estar atascado por carbón o dañado. " +
                "Un wastegate que no cierra = falta de potencia/boost. Uno que no abre = sobre-boost peligroso.",
            startCommand = "2F011503",
            stopCommand = "2F011500",
            durationMs = 8000,
            monitoredPids = listOf("010B"),
            safetyConditions = listOf(SafetyCondition.ENGINE_RUNNING, SafetyCondition.VEHICLE_STATIONARY)
        ),
        ActiveTest(
            id = "HORN_TEST",
            name = "Bocina / Claxon",
            description = "Activa la bocina (claxon) del vehículo de forma intermitente por 4 segundos. " +
                "PROCEDIMIENTO: Escuche un tono acústico claro. Si no suena: verifique el fusible de la bocina, " +
                "el relé de bocina en la caja de fusibles del compartimiento del motor (BJB), y el cableado del actuador.",
            startCommand = "2F012003",
            stopCommand = "2F012000",
            durationMs = 4000,
            safetyConditions = listOf(SafetyCondition.VEHICLE_STATIONARY, SafetyCondition.BATTERY_ABOVE_12V)
        ),
        ActiveTest(
            id = "HEADLIGHT_TEST",
            name = "Faros Delanteros",
            description = "Activa los faros delanteros (luces principales) por 6 segundos. " +
                "PROCEDIMIENTO: Verifique visualmente que ambos faros se enciendan. Si uno o ambos fallan, " +
                "inspeccione bulbos, relés e interruptor del panel.",
            startCommand = "2F012103",
            stopCommand = "2F012100",
            durationMs = 6000,
            safetyConditions = listOf(SafetyCondition.VEHICLE_STATIONARY)
        ),
        ActiveTest(
            id = "WIPER_TEST",
            name = "Limpiaparabrisas",
            description = "Cicla el motor del limpiaparabrisas en velocidad baja por 5 segundos. " +
                "PROCEDIMIENTO: Las plumas deben barrer el parabrisas suavemente. Ideal para comprobar motor del wiper y varillaje.",
            startCommand = "2F012203",
            stopCommand = "2F012200",
            durationMs = 5000,
            safetyConditions = listOf(SafetyCondition.VEHICLE_STATIONARY)
        ),
        ActiveTest(
            id = "RADIATOR_FAN_TEST",
            name = "Ventilador del Radiador (Alta)",
            description = "Enciende el ventilador del radiador a velocidad alta por 8 segundos. " +
                "PROCEDIMIENTO: El ventilador debe zumbar con fuerza. Útil para verificar relé de alta y motor del ventilador.",
            startCommand = "2F012303",
            stopCommand = "2F012300",
            durationMs = 8000,
            safetyConditions = listOf(SafetyCondition.ENGINE_OFF, SafetyCondition.BATTERY_ABOVE_12V)
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
