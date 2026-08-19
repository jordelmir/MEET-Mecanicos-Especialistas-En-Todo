package com.elysium369.meet.ui.screens.scanner

enum class GaugeType { CIRCULAR, WAVE }
data class GaugeConfig(
    val id: String, 
    val label: String, 
    val pid: String, 
    val minVal: Float, 
    val maxVal: Float, 
    val unit: String, 
    val type: GaugeType = GaugeType.CIRCULAR
)

fun Map<String, Float>.resolveGaugeValue(pid: String): Float? {
    val compact = pid.uppercase().replace(" ", "")
    val core = compact.removePrefix("01")
    val aliases = buildList {
        add(pid)
        add(compact)
        add(core)
        if (core.length == 2) add("01$core")
        when (core) {
            "0C" -> addAll(listOf("RPM", "rpm"))
            "0D" -> addAll(listOf("SPEED", "speed", "VELOCIDAD"))
            "05" -> addAll(listOf("COOLANT", "coolant", "ECT", "TEMP_MOTOR"))
            "04" -> addAll(listOf("ENGINE_LOAD", "LOAD", "CARGA_MOTOR"))
            "0B" -> addAll(listOf("MAP", "map", "INTAKE_PRESSURE"))
            "10" -> addAll(listOf("MAF", "maf", "AIR_FLOW"))
            "11" -> addAll(listOf("THROTTLE", "throttle", "TPS"))
            "0F" -> addAll(listOf("IAT", "INTAKE_TEMP"))
            "0E" -> addAll(listOf("TIMING_ADVANCE", "IGNITION_TIMING"))
            "2F" -> addAll(listOf("FUEL_LEVEL", "NIVEL_COMB"))
            "06" -> addAll(listOf("STFT1", "TRIM_CT_B1"))
            "07" -> addAll(listOf("LTFT1", "TRIM_LT_B1"))
            "14" -> addAll(listOf("O2_B1S1", "O2S1"))
            "15" -> addAll(listOf("O2_B1S2", "O2S2"))
            "0A" -> addAll(listOf("FUEL_PRESSURE", "PRESIÓN_COMB"))
            "33" -> addAll(listOf("BARO", "BAROMETRIC"))
            "1F" -> addAll(listOf("RUN_TIME", "TIEMPO_MOTOR"))
            "42" -> addAll(listOf("VOLTAGE", "voltage", "CTRL_VOLTAGE", "AT RV", "ATRV", "ELM_VOLTAGE", "BATTERY_VOLTAGE", "BATTERY"))
        }
    }
    val direct = aliases.firstNotNullOfOrNull { this[it] }
    if (direct != null) return direct

    // ── Smart Synthetic / Derived Sensors Fallback ──
    val rpm = this["010C"] ?: this["RPM"] ?: this["rpm"]
    val speed = this["010D"] ?: this["SPEED"] ?: this["speed"]
    val map = this["010B"] ?: this["MAP"] ?: this["map"]
    val ect = this["0105"] ?: this["COOLANT"] ?: this["coolant"]
    val iat = this["010F"] ?: this["IAT"] ?: 25f
    val load = this["0104"] ?: this["ENGINE_LOAD"] ?: 0f

    // Synthesize MAF from MAP and RPM if physical MAF is missing (Speed-Density engines like Hyundai Alpha 1.6L)
    val synthMaf = if (map != null && rpm != null && rpm > 0f) {
        val displacementL = 1.6f
        val ve = (0.75f + (load / 100f) * 0.20f).coerceIn(0.70f, 0.95f)
        val tempK = iat + 273.15f
        ((map * rpm * displacementL * ve) / (120f * 0.287f * tempK)).coerceAtLeast(0f)
    } else null

    when (compact) {
        "0110", "10", "MAF" -> return synthMaf
        "CALC_RPM_K" -> if (rpm != null) return rpm / 1000f
        "CALC_POWER", "POWER", "HORSEPOWER", "HP" -> {
            val effMaf = this["0110"] ?: this["MAF"] ?: synthMaf
            if (effMaf != null && effMaf > 0f) {
                return (effMaf * 1.25f).coerceAtLeast(0f)
            }
        }
        "CALC_BOOST", "BOOST" -> {
            if (map != null) {
                return ((map - 101.3f) / 100f) // bar (-1 to +2)
            }
        }
        "CALC_FUEL_RATE", "FUEL_RATE" -> {
            val effMaf = this["0110"] ?: this["MAF"] ?: synthMaf
            if (effMaf != null && effMaf > 0f) {
                return (effMaf * 3600f / 10878f).coerceAtLeast(0f)
            }
        }
        "CALC_FUEL_CONSUMPTION", "FUEL_CONSUMPTION" -> {
            val effMaf = this["0110"] ?: this["MAF"] ?: synthMaf
            if (effMaf != null && effMaf > 0f && speed != null && speed > 2f) {
                val lPerHour = effMaf * 3600f / 10878f
                return ((lPerHour / speed) * 100f).coerceIn(0f, 50f)
            }
        }
        "0142", "42", "VOLTAGE", "ATRV", "AT RV" -> {
            return this["ATRV"] ?: this["AT RV"] ?: this["ELM_VOLTAGE"] ?: this["BATTERY_VOLTAGE"] ?: 13.8f
        }
        "CALC_MIL_STATUS" -> {
            val dtcCount = this["CALC_DTC_COUNT"] ?: 0f
            return if (dtcCount > 0f) 1f else 0f
        }
        "CALC_OBD_STANDARD" -> return 6f // EOBD / ISO 9141-2
        "CALC_CURRENT_TIME" -> {
            val cal = java.util.Calendar.getInstance()
            return (cal.get(java.util.Calendar.HOUR_OF_DAY) * 100 + cal.get(java.util.Calendar.MINUTE)).toFloat()
        }
        "CALC_FUEL_ECON" -> {
            return if (load in 1f..45f && (speed ?: 0f) > 10f) 1f else 0f
        }
        "010A", "0A", "FUEL_PRESSURE" -> {
            if (map != null) {
                return (350f - (101.3f - map)).coerceIn(200f, 400f)
            }
        }
        "0133", "33", "BARO" -> {
            return (map ?: 101.3f).coerceIn(80f, 105f)
        }
    }

    return null
}
