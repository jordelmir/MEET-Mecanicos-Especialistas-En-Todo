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

    fun getDtcParagraphExplanation(code: String, shortDesc: String, isSpanish: Boolean): String {
        val upperCode = code.trim().uppercase()
        val letter = upperCode.firstOrNull() ?: 'P'
        
        // Specific paragraph explanations for cylinder misfire codes
        if (upperCode.matches(Regex("P030[1-9]|P031[0-2]"))) {
            val cylinder = upperCode.substring(3).toIntOrNull() ?: 1
            return if (isSpanish) {
                "El código $upperCode es un código de diagnóstico OBD-II que significa fallo de encendido detectado en el cilindro $cylinder. Esto indica que el combustible no se está quemando de manera eficiente en ese cilindro en específico, reduciendo el rendimiento motor. Las causas comunes incluyen bujías desgastadas en el cilindro $cylinder, bobina de encendido correspondiente defectuosa, inyector tapado o problemas de compresión mecánica en dicho cilindro."
            } else {
                "The $upperCode code is an OBD-II diagnostic trouble code that means misfire detected in cylinder $cylinder. This indicates that fuel is not burning properly in that specific cylinder, reducing engine performance. Common causes include worn spark plugs in cylinder $cylinder, a failed ignition coil for that cylinder, a clogged fuel injector, or mechanical compression issues in that cylinder."
            }
        }

        // Specific paragraph explanations for common codes
        if (isSpanish) {
            when (upperCode) {
                "P0300" -> return "El código P0300 es un código de diagnóstico OBD-II que significa que el módulo de control del motor (ECM) ha detectado fallas de encendido aleatorias en múltiples cilindros. Esto indica que el combustible no se está quemando de manera eficiente o regular en más de un cilindro, lo que puede provocar pérdida de potencia y tirones. Las causas comunes incluyen bujías desgastadas o defectuosas, bobinas de encendido con fallas, cables de bujías dañados, inyectores de combustible obstruidos o una fuga de vacío en la admisión."
                "P0171" -> return "El código P0171 es un código de diagnóstico OBD-II que significa que el sistema del motor está funcionando con una mezcla demasiado pobre en el banco 1. Esto indica que hay un exceso de aire o una deficiencia de combustible en la cámara de combustión, afectando la eficiencia térmica. Las causas comunes incluyen fugas de vacío, un sensor de flujo de masa de aire (MAF) sucio o dañado, baja presión de combustible por bomba debilitada o un inyector obstruido."
                "P0420" -> return "El código P0420 es un código de diagnóstico OBD-II que significa que la eficiencia del sistema catalítico está por debajo del umbral permitido en el banco 1. Esto indica que el convertidor catalítico no está purificando adecuadamente los gases de escape nocivos para cumplir con las normas ambientales. Las causas comunes incluyen un convertidor catalítico dañado o desgastado, fallas de encendido previas que dañaron el catalizador, fugas de escape antes del catalizador o lecturas erráticas de los sensores de oxígeno."
                "P0101" -> return "El código P0101 es un código de diagnóstico OBD-II que significa un problema de rango o rendimiento en el circuito del sensor de flujo de masa o volumen de aire (MAF). Esto indica que la computadora recibe señales de flujo de aire inconsistentes que no coinciden con la carga del motor. Las causas comunes incluyen un sensor MAF sucio o contaminado por aceite/polvo, fugas de aire en la bota de admisión o un filtro de aire excesivamente obstruido."
                "P0505" -> return "El código P0505 es un código de diagnóstico OBD-II que significa un mal funcionamiento en el sistema de control del aire de ralentí (IAC). Esto indica que la computadora del motor no puede estabilizar las revoluciones mínimas deseadas cuando el vehículo está detenido. Las causas comunes incluyen una válvula IAC sucia o atascada, acumulación de carbón en el cuerpo de aceleración o fugas de vacío."
            }
        } else {
            when (upperCode) {
                "P0300" -> return "The P0300 code is an OBD-II diagnostic trouble code that means random or multiple cylinder misfire detected. This indicates that fuel is not burning properly in more than one cylinder, causing engine hesitation or power loss. Common causes include worn or faulty spark plugs, failing ignition coils, damaged spark plug wires, clogged fuel injectors, or a vacuum leak in the intake."
                "P0171" -> return "The P0171 code is an OBD-II diagnostic trouble code that means system too lean in Bank 1. This indicates that there is too much air or too little fuel in the combustion chambers, impacting thermal efficiency. Common causes include intake vacuum leaks, a dirty or failed Mass Air Flow (MAF) sensor, low fuel pressure from a weak pump, or a clogged fuel injector."
                "P0420" -> return "The P0420 code is an OBD-II diagnostic trouble code that means catalyst system efficiency below threshold in Bank 1. This indicates that the catalytic converter is not cleaning harmful exhaust emissions properly to meet environmental standards. Common causes include a degraded or melted catalytic converter, previous cylinder misfires damaging the catalyst, or leaking oxygen sensors."
                "P0101" -> return "The P0101 code is an OBD-II diagnostic trouble code that means Mass or Volume Air Flow (MAF) circuit range or performance problem. This indicates that the computer is receiving inconsistent airflow readings relative to engine load. Common causes include a dirty or contaminated MAF sensor, air leaks in the intake boots, or an extremely dirty engine air filter."
                "P0505" -> return "The P0505 code is an OBD-II diagnostic trouble code that means idle air control system malfunction. This indicates that the engine computer is unable to maintain a stable or target idle speed when the vehicle is stationary. Common causes include a dirty or stuck IAC valve, carbon buildup in the throttle body, or intake vacuum leaks."
            }
        }

        // Generic dynamic paragraph constructor
        val cleanDesc = shortDesc.trim().trimEnd('.')
        val indication = if (isSpanish) {
            when (letter) {
                'P' -> "que existe una anomalía en el funcionamiento de la propulsión, la gestión de combustión, emisión o control de transmisión"
                'C' -> "que el chasis o sistemas de seguridad activa (como frenos ABS, dirección asistida o tracción) registran valores fuera del rango esperado"
                'B' -> "que los componentes de la carrocería, climatización, seguridad pasiva (airbags) o módulos de confort reportan fallas operativas"
                'U' -> "que se ha interrumpido el flujo de datos digitales o la comunicación multiplexada entre las computadoras del vehículo en el bus CAN"
                else -> "que el sistema monitoreado ha reportado lecturas fuera de los parámetros de fábrica"
            }
        } else {
            when (letter) {
                'P' -> "that there is an anomaly in the operation of the vehicle's powertrain, combustion management, emissions, or transmission systems"
                'C' -> "that the chassis or active safety systems (such as ABS brakes, steering, or traction control) are registering values outside the expected range"
                'B' -> "that body components, climate control, passive safety (airbags), or comfort modules are reporting operational issues"
                'U' -> "that digital data flow or multiplexed communication between control modules has been disrupted on the CAN bus"
                else -> "that the monitored system is reporting readings outside standard factory parameters"
            }
        }

        val causes = if (isSpanish) {
            when (letter) {
                'P' -> "sensores defectuosos (como de oxígeno o MAF), fallas de cableado eléctrico, fugas de vacío, inyectores sucios o desgaste físico de piezas internas"
                'C' -> "sensores de velocidad de rueda averiados, bajo nivel de líquido, conectores sulfatados o actuadores mecánicos desgastados"
                'B' -> "fusibles fundidos, interruptores defectuosos, motores de accesorios dañados, cableado expuesto o conectores de seguridad flojos"
                'U' -> "problemas de alimentación en algún módulo, cables del bus de datos dañados o con cortocircuito, o conectores de red flojos"
                else -> "sensores con fallas internas, cableado averiado, conexiones flojas o componentes mecánicos con holguras fuera de tolerancia"
            }
        } else {
            when (letter) {
                'P' -> "faulty sensors (such as oxygen or MAF), damaged wiring harness, vacuum leaks, clogged injectors, or physical wear of internal components"
                'C' -> "failed wheel speed sensors, low fluid levels, corroded connector terminals, or worn mechanical actuators"
                'B' -> "blown fuses, malfunctioning switches, damaged accessory motors, exposed wiring, or loose safety connectors"
                'U' -> "loss of power to control modules, damaged or shorted data lines, or loose network harness connections"
                else -> "failed sensors, broken electrical wires, loose terminal connections, or mechanical parts out of tolerance"
            }
        }

        return if (isSpanish) {
            "El código $upperCode es un código de diagnóstico OBD-II que significa $cleanDesc. Esto indica $indication. Las causas comunes incluyen $causes."
        } else {
            "The $upperCode code is an OBD-II diagnostic trouble code that means $cleanDesc. This indicates $indication. Common causes include $causes."
        }
    }
}
