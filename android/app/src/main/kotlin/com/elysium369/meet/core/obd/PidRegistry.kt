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
        PidDefinition("01","0D","Velocidad","km/h",0f,260f,160f,220f,{a,_,_,_ -> a.toFloat()}, PidCategory.ENGINE),
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
        
        // TEMPERATURE
        PidDefinition("01","05","Temp Motor","°C",-40f,215f,100f,110f,{a,_,_,_ -> a-40f}, PidCategory.TEMPERATURE),
        PidDefinition("01","0F","Temp Admisión","°C",-40f,215f,50f,80f,{a,_,_,_ -> a-40f}, PidCategory.TEMPERATURE),
        PidDefinition("01","46","Temp Ambiente","°C",-40f,215f,45f,55f,{a,_,_,_ -> a-40f}, PidCategory.TEMPERATURE),
        PidDefinition("01","5C","Temp Aceite","°C",-40f,210f,115f,130f,{a,_,_,_ -> a-40f}, PidCategory.TEMPERATURE, isPremium=true),
        PidDefinition("01","70","Temp Turbo","°C",-40f,215f,150f,190f,{a,_,_,_ -> a-40f}, PidCategory.ENGINE, isPremium=true),
        
        // FUEL
        PidDefinition("01","2F","Nivel Comb.","%",0f,100f,15f,5f,{a,_,_,_ -> a*100f/255f}, PidCategory.FUEL),
        PidDefinition("01","10","Flujo MAF","g/s",0f,655f,200f,400f,{a,b,_,_ -> ((a*256f)+b)/100f}, PidCategory.FUEL),
        PidDefinition("01","06","Trim Comb CT B1","%",-100f,99.2f,25f,40f,{a,_,_,_ -> a*100f/128f-100f}, PidCategory.FUEL),
        PidDefinition("01","07","Trim Comb LT B1","%",-100f,99.2f,25f,40f,{a,_,_,_ -> a*100f/128f-100f}, PidCategory.FUEL, isPremium=true),
        PidDefinition("01","0A","Presión Comb.","kPa",0f,765f,300f,400f,{a,_,_,_ -> a*3f}, PidCategory.FUEL),
        PidDefinition("01","22","Pres. Rail Rel.","kPa",0f,5177f,3000f,4000f,{a,b,_,_ -> ((a*256f)+b)*0.079f}, PidCategory.FUEL, isPremium=true),
        PidDefinition("01","23","Pres. Rail Abs.","kPa",0f,655350f,150000f,200000f,{a,b,_,_ -> ((a*256f)+b)*10f}, PidCategory.FUEL, isPremium=true),
        PidDefinition("01","51","Tipo Comb.","",0f,255f,0f,0f,{a,_,_,_ -> a.toFloat()}, PidCategory.FUEL),
        
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
        PidDefinition("01","A4","Temp Trans","°C",-40f,215f,110f,130f,{a,_,_,_ -> a-40f}, PidCategory.TRANSMISSION, isPremium=true)
    )

    fun getPid(mode: String, pid: String): PidDefinition? {
        return STANDARD_PIDS.find { it.mode == mode && it.pid == pid }
    }
}
