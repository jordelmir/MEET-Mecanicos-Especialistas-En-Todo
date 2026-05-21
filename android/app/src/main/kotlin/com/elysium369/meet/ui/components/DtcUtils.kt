package com.elysium369.meet.ui.components

import com.elysium369.meet.data.local.entities.DtcDefinitionEntity

object DtcUtils {
    fun getDynamicDtcFallbackDescription(code: String, isSpanish: Boolean): String {
        if (code.length < 5) {
            return if (isSpanish) "Código de diagnóstico del sistema." else "System diagnostic code."
        }
        val letter = code.firstOrNull()?.uppercaseChar() ?: 'P'
        val digit1 = code.drop(1).firstOrNull() ?: '0'
        val digit2 = code.drop(2).firstOrNull() ?: '0'
        
        val isGeneric = digit1 == '0' || (letter == 'P' && digit1 == '2') || (letter == 'U' && digit1 == '3')
        val genericStr = if (isSpanish) {
            if (isGeneric) "Genérico" else "Específico del Fabricante"
        } else {
            if (isGeneric) "Generic" else "Manufacturer Specific"
        }
        
        val systemName = if (isSpanish) {
            when (letter) {
                'P' -> "Motor/Transmisión"
                'C' -> "Chasis"
                'B' -> "Carrocería"
                'U' -> "Red/Comunicación"
                else -> "General"
            }
        } else {
            when (letter) {
                'P' -> "Powertrain"
                'C' -> "Chassis"
                'B' -> "Body"
                'U' -> "Network/Communication"
                else -> "General"
            }
        }
        
        val subsys = if (letter == 'P') {
            when (digit2.uppercaseChar()) {
                '0', '1', '2' -> if (isSpanish) " - Medición de aire y combustible" else " - Fuel and Air Metering"
                '3' -> if (isSpanish) " - Sistema de encendido o falla de cilindro" else " - Ignition System or Misfire"
                '4' -> if (isSpanish) " - Controles auxiliares de emisiones" else " - Auxiliary Emissions Controls"
                '5' -> if (isSpanish) " - Control de velocidad, ralentí y entradas auxiliares" else " - Vehicle Speed Controls and Idle Control"
                '6' -> if (isSpanish) " - Computadora y circuitos de salida" else " - Computer Output Circuit"
                '7', '8', '9' -> if (isSpanish) " - Transmisión" else " - Transmission"
                'A', 'B', 'C', 'D', 'E', 'F' -> if (isSpanish) " - Propulsión/Híbrido" else " - Hybrid Propulsion"
                else -> ""
            }
        } else if (letter == 'C') {
            when (digit2.uppercaseChar()) {
                '0', '1', '2' -> if (isSpanish) " - Control de frenado, ABS y tracción" else " - Brake Control, ABS and Traction"
                '3' -> if (isSpanish) " - Sistemas de dirección y estabilidad" else " - Steering and Stability Systems"
                '4', '5' -> if (isSpanish) " - Suspensión activa y amortiguadores" else " - Active Suspension and Dampers"
                else -> if (isSpanish) " - Componentes del chasis y control dinámico" else " - Chassis Components and Dynamic Control"
            }
        } else if (letter == 'B') {
            when (digit2.uppercaseChar()) {
                '0', '1', '2' -> if (isSpanish) " - Módulos de seguridad, airbags y cinturones" else " - Safety Modules, Airbags and Belts"
                '3' -> if (isSpanish) " - Controles de confort, aire acondicionado y climatización" else " - Comfort Controls, AC and Climate Control"
                '4', '5' -> if (isSpanish) " - Iluminación exterior/interior y accesorios" else " - Exterior/Interior Lighting and Accessories"
                else -> if (isSpanish) " - Sistemas eléctricos de confort y carrocería" else " - Body and Comfort Electrical Systems"
            }
        } else if (letter == 'U') {
            when (digit2.uppercaseChar()) {
                '0', '1', '2' -> if (isSpanish) " - Bus de comunicación CAN y enlace de datos principal" else " - CAN Communication Bus and Main Data Link"
                '3' -> if (isSpanish) " - Redes locales multiplexadas" else " - Multiplexed Local Networks"
                else -> if (isSpanish) " - Módulos de control electrónico interconectados" else " - Interconnected Electronic Control Modules"
            }
        } else ""

        return if (isSpanish) {
            "Código $genericStr para $systemName$subsys. Circuito bajo monitoreo activo de rendimiento."
        } else {
            "$genericStr code for $systemName$subsys. Circuit under active performance monitoring."
        }
    }

    fun getDynamicSeverity(code: String): String {
        val letter = code.firstOrNull()?.uppercaseChar() ?: 'P'
        val digit1 = code.drop(1).firstOrNull() ?: '0'
        return if ((letter == 'P' || letter == 'U') && (digit1 == '0' || digit1 == '2')) "HIGH" else "MODERATE"
    }

    fun getDynamicUrgency(code: String): String {
        return if (getDynamicSeverity(code) == "HIGH") "STOP_DRIVING" else "CAUTION"
    }

    fun normalizeManufacturer(make: String?): String {
        if (make.isNullOrBlank()) return "GENERIC"
        val clean = make.trim().uppercase()
        return when {
            clean.contains("TOYOTA") || clean.contains("LEXUS") || clean.contains("SCION") -> "TOYOTA"
            clean.contains("HONDA") || clean.contains("ACURA") -> "HONDA"
            clean.contains("NISSAN") || clean.contains("INFINITI") -> "NISSAN"
            clean.contains("FORD") || clean.contains("LINCOLN") || clean.contains("MERCURY") -> "FORD"
            clean.contains("CHEVROLET") || clean.contains("CHEVY") || clean.contains("GMC") || clean.contains("CADILLAC") || clean.contains("BUICK") || clean.contains("GM") || clean.contains("GENERAL MOTORS") -> "CHEVROLET"
            clean.contains("VOLKSWAGEN") || clean.contains("VW") || clean.contains("AUDI") || clean.contains("SEAT") || clean.contains("SKODA") || clean.contains("PORSCHE") -> "VOLKSWAGEN"
            clean.contains("HYUNDAI") || clean.contains("KIA") -> "HYUNDAI"
            clean.contains("CHRYSLER") || clean.contains("DODGE") || clean.contains("JEEP") || clean.contains("RAM") || clean.contains("STELLANTIS") || clean.contains("FIAT") || clean.contains("ALFA") -> "CHRYSLER"
            clean.contains("MAZDA") -> "MAZDA"
            clean.contains("SUBARU") -> "SUBARU"
            clean.contains("MITSUBISHI") -> "MITSUBISHI"
            clean.contains("VOLVO") -> "VOLVO"
            clean.contains("LAND ROVER") || clean.contains("RANGE ROVER") || clean.contains("JAGUAR") -> "LAND ROVER"
            clean.contains("MERCEDES") || clean.contains("BENZ") || clean.contains("MB") -> "MERCEDES"
            clean.contains("RENAULT") -> "RENAULT"
            clean.contains("PEUGEOT") || clean.contains("CITROEN") || clean.contains("PSA") -> "PEUGEOT"
            else -> clean
        }
    }

    fun generateOfflineDiagnosticReport(
        dtcList: List<String>,
        vehicleInfo: String,
        definitions: List<DtcDefinitionEntity>
    ): String {
        val sb = StringBuilder()
        sb.append("### 📡 INFORME DE DIAGNÓSTICO CLÍNICO OFFLINE (Modo Respaldo)\n\n")
        sb.append("> **Vehículo:** $vehicleInfo\n")
        sb.append("> **Estado de Red:** Sin Conexión / Modo Offline\n")
        sb.append("> **Fecha de Generación:** ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}\n\n")
        sb.append("El motor experto local ha procesado los códigos de error OBD2 activos y generado el siguiente análisis estructurado paso a paso:\n\n")
        
        sb.append("#### 1. Resumen de Anomalías\n")
        sb.append("| Código | Gravedad | Urgencia | Descripción / Subsistema |\n")
        sb.append("| :--- | :--- | :--- | :--- |\n")
        
        val defMap = definitions.associateBy { it.code }
        for (code in dtcList) {
            val def = defMap[code]
            val severity = def?.severity ?: getDynamicSeverity(code)
            val urgency = def?.urgency ?: getDynamicUrgency(code)
            val desc = def?.descriptionEs ?: getDynamicDtcFallbackDescription(code, isSpanish = true)
            sb.append("| **$code** | `$severity` | `$urgency` | $desc |\n")
        }
        sb.append("\n")
        
        sb.append("#### 2. Plan Clínico Detallado por Código de Falla\n\n")
        for (code in dtcList) {
            val def = defMap[code]
            val severity = def?.severity ?: getDynamicSeverity(code)
            val urgency = def?.urgency ?: getDynamicUrgency(code)
            val desc = def?.descriptionEs ?: getDynamicDtcFallbackDescription(code, isSpanish = true)
            val possibleCauses = def?.possibleCauses?.split(",")?.map { it.trim() }
                ?: listOf("Fallo de sensor", "Cableado defectuoso", "Conexión floja")
                
            sb.append("##### Código: **$code**\n")
            sb.append("- **Gravedad:** `$severity` | **Urgencia:** `$urgency`\n")
            sb.append("- **Descripción:** $desc\n")
            sb.append("- **Posibles Causas Comunes:**\n")
            for (cause in possibleCauses) {
                if (cause.isNotBlank()) {
                    sb.append("  - $cause\n")
                }
            }
            sb.append("- **Procedimiento Recomendado de Inspección:**\n")
            sb.append("  1. Inspección Visual: Verifique el arnés y conectores asociados al componente del código **$code**.\n")
            sb.append("  2. Prueba de Señal: Conecte un multímetro u osciloscopio y mida voltajes de referencia y señales activas según parámetros del manual.\n")
            sb.append("  3. Comprobación de Tierras: Verifique la caída de voltaje en los puntos de masa del circuito.\n")
            sb.append("  4. Limpieza/Reemplazo: Limpie los contactos del sensor con limpiador dieléctrico o sustituya el sensor si las pruebas eléctricas confirman falla interna.\n\n")
        }
        
        sb.append("#### 3. Conclusión del Asesor Clínico\n")
        sb.append("Se recomienda atender con prioridad cualquier código catalogado como `HIGH` o `CRITICAL` con urgencia de tipo `STOP_DRIVING` para evitar daños permanentes en el motor o los sistemas de seguridad activa del chasis.")
        return sb.toString()
    }
}
