package com.elysium369.meet.data.visualdiagnostics

import com.elysium369.meet.domain.visualdiagnostics.ComponentCategory
import com.elysium369.meet.domain.visualdiagnostics.ComponentPosition
import com.elysium369.meet.domain.visualdiagnostics.ComponentSpec
import com.elysium369.meet.domain.visualdiagnostics.ComponentTest
import com.elysium369.meet.domain.visualdiagnostics.DiagnosticComponent
import com.elysium369.meet.domain.visualdiagnostics.EngineType
import com.elysium369.meet.domain.visualdiagnostics.RelatedDtc
import com.elysium369.meet.domain.visualdiagnostics.RelatedPid
import com.elysium369.meet.domain.visualdiagnostics.RepairStep
import com.elysium369.meet.domain.visualdiagnostics.SafetyWarning

object VisualDiagnosticSeedData {
    fun components(engineType: EngineType): List<DiagnosticComponent> {
        return when (engineType) {
            EngineType.UNKNOWN -> universalComponents()
            EngineType.EV -> evComponents()
            EngineType.HYBRID, EngineType.PHEV -> l4Components() + hybridComponents() + universalComponents()
            EngineType.DIESEL_L4 -> l4Components() + dieselComponents(4) + universalComponents()
            EngineType.DIESEL_V6 -> l4Components() + dieselComponents(6) + universalComponents() + v6Extras()
            EngineType.DIESEL_V8 -> l4Components() + dieselComponents(8) + universalComponents() + v8Extras()
            EngineType.V6 -> l4Components() + v6Extras() + universalComponents()
            EngineType.V8 -> l4Components() + v8Extras() + universalComponents()
            EngineType.V10 -> l4Components() + v8Extras() + v10Extras() + universalComponents()
            EngineType.V12 -> l4Components() + v8Extras() + v10Extras() + universalComponents()
            EngineType.H4, EngineType.H6 -> l4Components() + boxerExtras() + universalComponents()
            EngineType.ROTARY -> l4Components() + rotaryExtras() + universalComponents()
            EngineType.L6 -> l4Components() + l6Extras() + universalComponents()
            else -> l4Components() + universalComponents()
        }.map { it.copy(engineType = engineType) }
    }

    // ═══════════════════════════════════════════════════════════
    //  UNIVERSAL COMPONENTS (all engine types)
    // ═══════════════════════════════════════════════════════════
    private fun universalComponents(): List<DiagnosticComponent> = listOf(
        // ── ECU / PCM ──
        DiagnosticComponent(
            id = "ecu_pcm", engineType = EngineType.L4, name = "ECU/PCM (Módulo Control Motor)",
            category = ComponentCategory.ELECTRICAL,
            description = "Cerebro del vehículo: controla inyección, encendido, emisiones y transmisión. Rara vez falla — confirmar alimentación, masas y bus CAN antes de sospechar hardware.",
            location = "Compartimiento motor o detrás del tablero, según fabricante.",
            commonFailures = listOf("Corrosión en conector principal", "Masa dañada", "Reprogramación necesaria", "Daño por picos de voltaje"),
            workshopTests = listOf(
                ComponentTest("Alimentación", "Medir VBATT y masas en pines del conector ECU con diagrama OEM.", "12V+ en pines de poder, <0.1V en masas.", "Multímetro"),
                ComponentTest("Bus CAN", "Verificar resistencia entre CAN-H y CAN-L (desconectado) = ~60Ω.", "60Ω entre terminadores. Sin cortocircuito.", "Multímetro"),
                ComponentTest("Comunicación", "Intentar comunicación por OBD-II con protocolo auto-detect.", "Responde a 0100 con PIDs soportados.", "Escáner Elysium Vanguard")
            ),
            repairFlow = listOf(
                RepairStep(1, "Verificar fusibles ECM (BATT y IGN) y relé principal.", "Alimentación confirmada."),
                RepairStep(2, "Inspeccionar conector por agua, corrosión o pines doblados.", "Contacto limpio."),
                RepairStep(3, "Probar masas del ECU con prueba de caída de voltaje.", "Masas sólidas."),
                RepairStep(4, "Solo si todo lo anterior está bien, considerar reflash o reemplazo.", "Comunicación restaurada.")
            ),
            specs = listOf(ComponentSpec("Alimentación", "12V+ continuo", "Fusible BATT + IGN"), ComponentSpec("Bus CAN", "60Ω terminación", "Entre CAN-H y CAN-L")),
            requiredTools = listOf("Escáner OBD-II", "Multímetro", "Diagrama eléctrico OEM", "Herramienta de reflash (si aplica)"),
            safetyWarnings = listOf(SafetyWarning("CRITICAL", "Desconectar batería antes de manipular conector ECU."), SafetyWarning("WARNING", "Nunca probar alimentación con carga artificial en pines de señal.")),
            relatedPids = listOf(RelatedPid("0142", "Voltaje sistema", "V", "12-14.8V", "CRITICAL si <10V")),
            relatedDtcs = listOf(RelatedDtc("U0100", "Pérdida comunicación con ECM/PCM", 0.95, "CRITICAL"), RelatedDtc("P0606", "Procesador ECM/PCM", 0.90, "CRITICAL"), RelatedDtc("P0603", "Error memoria KAM ECU", 0.85, "WARNING")),
            position = ComponentPosition(0f, -15f, 30f, "MOTOR_3D"), meshKey = "ecu_module"
        ),
        // ── BCM (Body Control Module) ──
        baseComponent("bcm", "BCM (Módulo Control Carrocería)", ComponentCategory.BODY_CONTROL, "bcm_module",
            listOf("U0140", "B1000", "B1318"), emptyList(), "Controla luces, seguros, alarma, vidrios y accesorios eléctricos."),
        // ── TCM (Transmission Control Module) ──
        baseComponent("tcm", "TCM (Módulo Transmisión)", ComponentCategory.TRANSMISSION, "tcm_module",
            listOf("U0101", "P0700", "P0715", "P0720"), listOf(RelatedPid("010C", "RPM", "rpm", "correlación con velocidad", "WARNING si patina")),
            "Controla cambios, presiones y embragues de transmisión automática."),
        // ── ABS Module ──
        baseComponent("abs_module", "Módulo ABS/ESC", ComponentCategory.BRAKES, "abs_module",
            listOf("C0035", "C0040", "C0045", "C0050", "U0121"), emptyList(), "Controla frenos antibloqueo y estabilidad electrónica."),
        // ── Starter Motor ──
        baseComponent("starter_motor", "Motor de arranque", ComponentCategory.ELECTRICAL, "starter_motor",
            listOf("P0512", "P0615", "P0616"), listOf(RelatedPid("0142", "Voltaje", "V", ">9.6V durante arranque", "CRITICAL si cae debajo")),
            "Convierte energía eléctrica en torque mecánico para arrancar el motor."),
        // ── Battery 12V ──
        DiagnosticComponent(
            id = "battery_12v", engineType = EngineType.L4, name = "Batería 12V",
            category = ComponentCategory.ELECTRICAL,
            description = "Almacena energía para arranque y alimentación de módulos. Voltaje bajo causa múltiples DTCs fantasma.",
            location = "Compartimiento motor o cajuela, según vehículo.",
            commonFailures = listOf("Celda muerta", "Sulfatación en bornes", "Cable de masa corroído", "Capacidad reducida por edad"),
            workshopTests = listOf(
                ComponentTest("Voltaje reposo", "Medir con motor apagado, sin carga, 2h después de uso.", "12.6V = 100% carga.", "Multímetro"),
                ComponentTest("Prueba de carga", "Aplicar carga de arranque por 15s y medir voltaje.", ">9.6V durante arranque.", "Probador de batería"),
                ComponentTest("Caída de voltaje masas", "Medir entre negativo batería y bloque motor.", "<0.2V bajo carga.", "Multímetro")
            ),
            repairFlow = listOf(RepairStep(1, "Limpiar bornes y cables.", "Contacto limpio."), RepairStep(2, "Probar carga y CCA.", "Capacidad aceptable."), RepairStep(3, "Reemplazar si CCA <75% del nominal.", "Arranque fuerte.")),
            specs = listOf(ComponentSpec("Voltaje reposo", "12.6V", "100% carga"), ComponentSpec("Arranque", ">9.6V", "Durante cranking")),
            requiredTools = listOf("Multímetro", "Probador de batería/CCA", "Limpiador de bornes"),
            safetyWarnings = listOf(SafetyWarning("CRITICAL", "Gases de hidrógeno — no generar chispas cerca de batería."), SafetyWarning("WARNING", "Desconectar negativo primero, reconectar último.")),
            relatedPids = listOf(RelatedPid("0142", "Voltaje sistema", "V", "12.6V reposo, 14V cargando", "CRITICAL si <11V")),
            relatedDtcs = listOf(RelatedDtc("P0562", "Voltaje sistema bajo", 0.92, "CRITICAL"), RelatedDtc("P0563", "Voltaje sistema alto", 0.75, "WARNING")),
            position = ComponentPosition(-50f, 5f, 30f, "MOTOR_3D"), meshKey = "battery_12v"
        ),
        // ── Fuel Pump ──
        baseComponent("fuel_pump", "Bomba de combustible", ComponentCategory.FUEL, "fuel_pump",
            listOf("P0230", "P0231", "P0232", "P0087", "P0088"),
            listOf(RelatedPid("012F", "Nivel combustible", "%", ">10%", "WARNING si vacío")),
            "Suministra combustible a presión desde el tanque al riel de inyección."),
        // ── Fuel Filter ──
        baseComponent("fuel_filter", "Filtro de combustible", ComponentCategory.FUEL, "fuel_filter",
            listOf("P0087", "P0171"), emptyList(), "Retiene impurezas del combustible. Restricción causa presión baja."),
        // ── EVAP Canister ──
        baseComponent("evap_canister", "Canister EVAP", ComponentCategory.FUEL, "evap_canister",
            listOf("P0440", "P0441", "P0442", "P0446", "P0455", "P0456"), emptyList(),
            "Almacena vapores de combustible para purga controlada. Fuga causa DTCs de emisiones."),
        // ── Purge Valve ──
        baseComponent("purge_valve", "Válvula de purga EVAP", ComponentCategory.FUEL, "purge_valve",
            listOf("P0443", "P0444", "P0445", "P0441"), emptyList(),
            "Controla flujo de vapores desde canister hacia múltiple de admisión."),
        // ── Radiator ──
        coolingComponent("radiator", "Radiador", "radiator", listOf("P0217", "P0480"), listOf(RelatedPid("0105", "Temp refrigerante", "°C", "85-105°C operación", "CRITICAL si >110°C"))),
        // ── Cooling Fan ──
        baseComponent("cooling_fan", "Ventilador de enfriamiento", ComponentCategory.COOLING, "cooling_fan",
            listOf("P0480", "P0481", "P0482"),
            listOf(RelatedPid("0105", "Temp refrigerante", "°C", "ventilador activa 95-105°C", "CRITICAL si no enciende")),
            "Ventilador eléctrico o acoplamiento viscoso para mantener temperatura."),
        // ── Heater Core ──
        coolingComponent("heater_core", "Núcleo calefacción", "heater_core", listOf("P0128"), emptyList()),
        // ── Oil Pressure Sensor ──
        sensorComponent("oil_pressure_sensor", "Sensor presión aceite", "oil_pressure_sensor",
            listOf("P0520", "P0521", "P0522", "P0523"),
            listOf(RelatedPid("010C", "RPM", "rpm", "presión sube con RPM", "CRITICAL si luz de aceite encendida"))),
        // ── Knock Sensor ──
        sensorComponent("knock_sensor", "Sensor de detonación (knock)", "knock_sensor",
            listOf("P0325", "P0326", "P0327", "P0328"),
            listOf(RelatedPid("010E", "Avance encendido", "°", "retrocede con knock", "WARNING si retrocede excesivo"))),
        // ── EVAP Pressure Sensor ──
        sensorComponent("evap_pressure_sensor", "Sensor presión EVAP", "evap_pressure_sensor",
            listOf("P0450", "P0451", "P0452", "P0453"), emptyList()),
        // ── Exhaust Manifold ──
        exhaustComponent("exhaust_manifold", "Múltiple de escape", "exhaust_manifold", listOf("P0420", "P0430", "P0171")),
        // ── Muffler ──
        exhaustComponent("muffler", "Silenciador/Mofle", "muffler", listOf("P0420")),
        // ── EGR Valve ──
        DiagnosticComponent(
            id = "egr_valve", engineType = EngineType.L4, name = "Válvula EGR",
            category = ComponentCategory.EXHAUST,
            description = "Recircula gases de escape para reducir NOx. Carbón acumulado causa apertura/cierre irregular.",
            location = "Entre múltiple de escape y admisión, parte trasera o lateral del motor.",
            commonFailures = listOf("Carbón bloqueando vástago", "Diafragma roto (EGR por vacío)", "Solenoide pegado (EGR electrónica)", "Fugas de vacío en líneas"),
            workshopTests = listOf(
                ComponentTest("Comando activo", "Activar EGR con escáner a diferentes posiciones.", "RPM baja/inestable = flujo real presente.", "Escáner bidireccional"),
                ComponentTest("Inspección visual", "Remover y verificar acumulación de carbón.", "Vástago limpio, movimiento libre.", "Herramientas manuales")
            ),
            repairFlow = listOf(RepairStep(1, "Verificar vacío o señal eléctrica según tipo.", "Comando llega a EGR."), RepairStep(2, "Limpiar o reemplazar según acumulación de carbón.", "Flujo restablecido.")),
            specs = listOf(ComponentSpec("Flujo", "Variable", "Según apertura comandada")),
            requiredTools = listOf("Escáner bidireccional", "Bomba de vacío manual", "Limpiador de carburador"),
            safetyWarnings = listOf(SafetyWarning("WARNING", "Componente caliente — esperar enfriamiento antes de manipular.")),
            relatedPids = listOf(RelatedPid("0104", "Carga motor", "%", "varía con EGR", "WARNING si no responde")),
            relatedDtcs = listOf(RelatedDtc("P0401", "Flujo EGR insuficiente", 0.88, "WARNING"), RelatedDtc("P0402", "Flujo EGR excesivo", 0.85, "WARNING"), RelatedDtc("P0403", "Circuito EGR", 0.80, "WARNING")),
            position = ComponentPosition(20f, -10f, -15f, "MOTOR_3D"), meshKey = "egr_valve"
        ),
        // ── Transmission Speed Sensors ──
        baseComponent("transmission_input_speed", "Sensor velocidad entrada trans.", ComponentCategory.TRANSMISSION, "trans_input_speed",
            listOf("P0715", "P0716", "P0717"), listOf(RelatedPid("010C", "RPM", "rpm", "correlación con velocidad salida", "WARNING si incoherente")),
            "Mide velocidad del eje de entrada de la transmisión."),
        baseComponent("transmission_output_speed", "Sensor velocidad salida trans.", ComponentCategory.TRANSMISSION, "trans_output_speed",
            listOf("P0720", "P0721", "P0722"), listOf(RelatedPid("010D", "Velocidad", "km/h", "proporcional a salida trans", "WARNING si discrepancia")),
            "Mide velocidad del eje de salida — usado para cálculo de relación de marcha."),
        // ── Torque Converter ──
        baseComponent("torque_converter", "Convertidor de par", ComponentCategory.TRANSMISSION, "torque_converter",
            listOf("P0740", "P0741", "P0742"), listOf(RelatedPid("010C", "RPM", "rpm", "slip <200rpm en lock-up", "WARNING si slip alto")),
            "Acopla motor con transmisión. Lock-up reduce patinaje en crucero."),
        // ── Main Engine Harness ──
        baseComponent("main_engine_harness", "Arnés principal motor", ComponentCategory.HARNESS, "engine_harness_main",
            listOf("U0100", "P0606", "P0685"), emptyList(), "Ramal de cables principal que alimenta y comunica todos los sensores y actuadores del motor."),
        // ── Injector Harness ──
        baseComponent("injector_harness", "Arnés inyectores", ComponentCategory.HARNESS, "injector_harness",
            listOf("P0201", "P0202", "P0203", "P0204"), emptyList(), "Ramal de cables que alimenta todos los inyectores. Resistencia y continuidad."),
        // ── O2 Sensor Harness ──
        baseComponent("o2_harness", "Arnés sensores O2", ComponentCategory.HARNESS, "o2_harness",
            listOf("P0130", "P0136", "P0135", "P0141"), emptyList(), "Cables de señal y calefacción para sensores O2/Lambda."),
        // ── ABS Harness ──
        baseComponent("abs_harness", "Arnés ABS/ruedas", ComponentCategory.HARNESS, "abs_harness",
            listOf("C0035", "C0040", "C0045", "C0050"), emptyList(), "Cables de sensores de velocidad de rueda al módulo ABS."),
        // ── OBD-II Port / DLC ──
        baseComponent("obd_port", "Puerto OBD-II / DLC", ComponentCategory.CONNECTOR, "obd_dlc_port",
            listOf("U0100", "U0001"), listOf(RelatedPid("0100", "PIDs soportados", "", "responde lista", "CRITICAL si no comunica")),
            "Conector de diagnóstico de 16 pines. Pin 16=VBATT, Pin 4/5=masa, Pin 6/14=CAN."),
        // ── Ground Straps ──
        baseComponent("ground_straps", "Masas del motor/carrocería", ComponentCategory.ELECTRICAL, "ground_straps",
            listOf("P0562", "U0100", "P0606"), listOf(RelatedPid("0142", "Voltaje", "V", "estable", "CRITICAL si fluctúa")),
            "Cables de masa entre motor-carrocería-batería. Corrosión causa DTCs fantasma múltiples.")
    )

    // ═══════════════════════════════════════════════════════════
    //  DIESEL-SPECIFIC COMPONENTS
    // ═══════════════════════════════════════════════════════════
    private fun dieselComponents(cylinders: Int): List<DiagnosticComponent> {
        val injectors = (1..cylinders).map { cyl ->
            baseComponent("diesel_injector_$cyl", "Inyector Diesel Cil.$cyl", ComponentCategory.FUEL, "diesel_injector_${cyl - 1}",
                listOf("P020$cyl", "P030$cyl"), listOf(RelatedPid("010C", "RPM", "rpm", "estable en ralentí", "WARNING si vibración")),
                "Inyector de alta presión common-rail. Requiere limpieza/codificación al reemplazar.")
        }
        return injectors + listOf(
            // DPF
            DiagnosticComponent(
                id = "dpf", engineType = EngineType.DIESEL_L4, name = "Filtro de Partículas DPF",
                category = ComponentCategory.DIESEL_EMISSIONS,
                description = "Atrapa hollín del escape diesel. Requiere regeneración periódica. Bloqueo causa pérdida de potencia y modo limp.",
                location = "Línea de escape, después del turbo y antes del catalizador SCR.",
                commonFailures = listOf("Saturación por trayectos cortos", "Sensor diferencial dañado", "Regeneración fallida", "Ceniza acumulada"),
                workshopTests = listOf(
                    ComponentTest("Presión diferencial", "Leer presión antes/después DPF con escáner.", "Diferencial baja = flujo libre.", "Escáner avanzado"),
                    ComponentTest("Regeneración forzada", "Iniciar regen con escáner, motor caliente, neutro.", "Temperatura escape sube a >600°C.", "Escáner bidireccional")
                ),
                repairFlow = listOf(RepairStep(1, "Verificar nivel de hollín con escáner.", "Determinar si regen es posible."), RepairStep(2, "Forzar regeneración si nivel <80%.", "DPF limpio."), RepairStep(3, "Si nivel >80% o regen falla, limpieza profesional o reemplazo.", "Flujo restaurado.")),
                specs = listOf(ComponentSpec("Hollín máximo", "~45g", "Varía por fabricante"), ComponentSpec("Temp regeneración", ">600°C", "Normal durante regen activa")),
                requiredTools = listOf("Escáner bidireccional", "Pirómetro", "Diagrama de escape"),
                safetyWarnings = listOf(SafetyWarning("CRITICAL", "Temperaturas >600°C durante regeneración — alejarse del escape."), SafetyWarning("WARNING", "No interrumpir regeneración una vez iniciada.")),
                relatedPids = listOf(RelatedPid("010C", "RPM", "rpm", "sube durante regen", "WARNING si no completa")),
                relatedDtcs = listOf(RelatedDtc("P2002", "DPF eficiencia baja", 0.90, "CRITICAL"), RelatedDtc("P244A", "Presión diferencial DPF", 0.85, "WARNING"), RelatedDtc("P2463", "Acumulación hollín DPF", 0.88, "WARNING")),
                position = ComponentPosition(0f, 5f, -40f, "MOTOR_3D"), meshKey = "dpf_filter"
            ),
            // SCR / AdBlue
            baseComponent("scr_system", "Sistema SCR / AdBlue", ComponentCategory.DIESEL_EMISSIONS, "scr_catalyst",
                listOf("P20EE", "P20E8", "P2BAD"), emptyList(), "Reduce NOx usando urea (AdBlue). Fallo causa limitación de velocidad en algunos vehículos."),
            // Glow Plugs
            baseComponent("glow_plugs", "Bujías de precalentamiento", ComponentCategory.IGNITION, "glow_plugs",
                listOf("P0380", "P0381", "P0382", "P0383"), emptyList(), "Calientan cámara de combustión para arranque en frío diesel."),
            // High Pressure Fuel Pump (diesel)
            baseComponent("hp_fuel_pump", "Bomba alta presión CR", ComponentCategory.FUEL, "hp_fuel_pump",
                listOf("P0087", "P0088", "P0089"), listOf(RelatedPid("010C", "RPM", "rpm", "presión sube con RPM", "CRITICAL si cae")),
                "Genera presión de hasta 2000+ bar para inyección directa diesel common-rail.")
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  TURBO COMPONENTS
    // ═══════════════════════════════════════════════════════════
    private fun turboComponents(): List<DiagnosticComponent> = listOf(
        DiagnosticComponent(
            id = "turbocharger", engineType = EngineType.L4, name = "Turbocompresor",
            category = ComponentCategory.TURBO_SUPERCHARGER,
            description = "Comprime aire de admisión usando gases de escape. Aumenta potencia sin aumentar cilindrada.",
            location = "Múltiple de escape, conectado a intercooler vía tubo de presión.",
            commonFailures = listOf("Sello de aceite dañado (humo azul)", "Eje con holgura radial", "Actuador wastegate pegado", "Carbón en VGT"),
            workshopTests = listOf(
                ComponentTest("Holgura del eje", "Con turbo desmontado, mover eje radial y axialmente.", "Juego mínimo, sin contacto con housing.", "Manual"),
                ComponentTest("Boost test", "Leer presión boost real vs deseada con escáner en aceleración.", "Presión alcanza target sin oscilación.", "Escáner + manómetro"),
                ComponentTest("Actuador", "Comandar actuador wastegate/VGT con escáner.", "Movimiento suave y completo.", "Escáner bidireccional")
            ),
            repairFlow = listOf(RepairStep(1, "Verificar presión de boost vs deseada.", "Determinar si sub-boost o over-boost."), RepairStep(2, "Inspeccionar líneas de aceite, vacío y tuberías de presión.", "Sin fugas."), RepairStep(3, "Verificar actuador wastegate/VGT.", "Movimiento correcto.")),
            specs = listOf(ComponentSpec("Boost típico", "0.5-1.5 bar", "Varía por motor"), ComponentSpec("Aceite", "Flujo continuo requerido", "Estrangulamiento = daño")),
            requiredTools = listOf("Escáner bidireccional", "Manómetro de boost", "Pirómetro"),
            safetyWarnings = listOf(SafetyWarning("CRITICAL", "Turbo alcanza 100,000+ RPM — nunca insertar objetos."), SafetyWarning("WARNING", "Temperaturas extremas en housing de escape.")),
            relatedPids = listOf(RelatedPid("010B", "Presión MAP", "kPa", "sube con boost", "WARNING si no alcanza target")),
            relatedDtcs = listOf(RelatedDtc("P0234", "Turbo overboost", 0.90, "CRITICAL"), RelatedDtc("P0299", "Turbo underboost", 0.88, "WARNING"), RelatedDtc("P006A", "MAP vs MAF correlación", 0.75, "WARNING")),
            position = ComponentPosition(10f, -20f, -25f, "MOTOR_3D"), meshKey = "turbocharger"
        ),
        baseComponent("intercooler", "Intercooler", ComponentCategory.TURBO_SUPERCHARGER, "intercooler",
            listOf("P0299", "P006A"), listOf(RelatedPid("010F", "Temp admisión", "°C", "más frío que sin intercooler", "WARNING si IAT alta")),
            "Enfría aire comprimido del turbo antes de entrar al motor. Reduce temperatura para mayor densidad de aire."),
        baseComponent("wastegate", "Wastegate/BOV", ComponentCategory.TURBO_SUPERCHARGER, "wastegate",
            listOf("P0234", "P0299"), emptyList(), "Controla presión máxima de boost. Bypass de gases cuando se alcanza presión objetivo."),
        baseComponent("boost_pressure_sensor", "Sensor presión boost", ComponentCategory.SENSOR, "boost_sensor",
            listOf("P0236", "P0237", "P0238"), listOf(RelatedPid("010B", "MAP/Boost", "kPa", "sube con aceleración", "WARNING si estático")),
            "Mide presión de sobrealimentación post-turbo para control de boost.")
    )

    // ═══════════════════════════════════════════════════════════
    //  HYBRID / PHEV COMPONENTS
    // ═══════════════════════════════════════════════════════════
    private fun hybridComponents(): List<DiagnosticComponent> = listOf(
        baseComponent("hv_battery", "Batería Alto Voltaje", ComponentCategory.EV_HIGH_VOLTAGE, "hv_battery_pack",
            listOf("P0A80", "P0A7F", "P0AA6"), emptyList(), "Pack de baterías de alto voltaje (200-400V). Requiere certificación para manipular."),
        baseComponent("electric_motor_mg1", "Motor/Generador MG1", ComponentCategory.EV_HIGH_VOLTAGE, "motor_generator_1",
            listOf("P0A90", "P0A93"), emptyList(), "Motor-generador principal para tracción y frenado regenerativo."),
        baseComponent("inverter_hybrid", "Inversor DC/AC", ComponentCategory.EV_HIGH_VOLTAGE, "inverter_module",
            listOf("P0A78", "P0A09"), emptyList(), "Convierte DC de batería a AC para motor eléctrico y viceversa."),
        baseComponent("dc_dc_converter", "Convertidor DC-DC", ComponentCategory.EV_HIGH_VOLTAGE, "dc_dc_converter",
            listOf("P0562", "P0A0F"), listOf(RelatedPid("0142", "Voltaje 12V", "V", "14V con motor encendido", "CRITICAL si <12V")),
            "Convierte alto voltaje HV a 12V para alimentar sistemas del vehículo."),
        baseComponent("onboard_charger", "Cargador a bordo (PHEV)", ComponentCategory.EV_HIGH_VOLTAGE, "onboard_charger",
            listOf("P0A09", "P0AF0"), emptyList(), "Convierte AC de la red eléctrica a DC para cargar batería HV.")
    )

    // ═══════════════════════════════════════════════════════════
    //  ENGINE-SPECIFIC EXTRAS
    // ═══════════════════════════════════════════════════════════
    private fun v6Extras(): List<DiagnosticComponent> = listOf(
        ignitionComponent("spark_plugs_cyl_5", "Bujía 5", "spark_plug_4", listOf("P0305", "P0300")),
        ignitionComponent("spark_plugs_cyl_6", "Bujía 6", "spark_plug_5", listOf("P0306", "P0300")),
        ignitionComponent("ignition_coils_cyl_5", "Bobina COP 5", "ignition_coil_4", listOf("P0355", "P0305")),
        ignitionComponent("ignition_coils_cyl_6", "Bobina COP 6", "ignition_coil_5", listOf("P0356", "P0306")),
        fuelComponent("injector_5", "Inyector 5", "injector_4", listOf("P0205", "P0305")),
        fuelComponent("injector_6", "Inyector 6", "injector_5", listOf("P0206", "P0306")),
        exhaustComponent("o2_b2s1", "Sensor O2 Banco 2 S1", "o2_b2s1", listOf("P0150", "P0151", "P0152", "P0153")),
        exhaustComponent("o2_b2s2", "Sensor O2 Banco 2 S2", "o2_b2s2", listOf("P0156", "P0157", "P0158")),
        exhaustComponent("catalytic_conv_b2", "Catalizador Banco 2", "catalytic_converter_b2", listOf("P0430"))
    )

    private fun v8Extras(): List<DiagnosticComponent> = v6Extras() + listOf(
        ignitionComponent("spark_plugs_cyl_7", "Bujía 7", "spark_plug_6", listOf("P0307", "P0300")),
        ignitionComponent("spark_plugs_cyl_8", "Bujía 8", "spark_plug_7", listOf("P0308", "P0300")),
        ignitionComponent("ignition_coils_cyl_7", "Bobina COP 7", "ignition_coil_6", listOf("P0357", "P0307")),
        ignitionComponent("ignition_coils_cyl_8", "Bobina COP 8", "ignition_coil_7", listOf("P0358", "P0308")),
        fuelComponent("injector_7", "Inyector 7", "injector_6", listOf("P0207", "P0307")),
        fuelComponent("injector_8", "Inyector 8", "injector_7", listOf("P0208", "P0308"))
    )

    private fun v10Extras(): List<DiagnosticComponent> = listOf(
        ignitionComponent("spark_plugs_cyl_9", "Bujía 9", "spark_plug_8", listOf("P0309", "P0300")),
        ignitionComponent("spark_plugs_cyl_10", "Bujía 10", "spark_plug_9", listOf("P0310", "P0300")),
        fuelComponent("injector_9", "Inyector 9", "injector_8", listOf("P0209", "P0309")),
        fuelComponent("injector_10", "Inyector 10", "injector_9", listOf("P0210", "P0310"))
    )

    private fun l6Extras(): List<DiagnosticComponent> = listOf(
        ignitionComponent("spark_plugs_cyl_5_l6", "Bujía 5", "spark_plug_4", listOf("P0305", "P0300")),
        ignitionComponent("spark_plugs_cyl_6_l6", "Bujía 6", "spark_plug_5", listOf("P0306", "P0300")),
        fuelComponent("injector_5_l6", "Inyector 5", "injector_4", listOf("P0205", "P0305")),
        fuelComponent("injector_6_l6", "Inyector 6", "injector_5", listOf("P0206", "P0306"))
    ) + turboComponents() // L6 often turbocharged (BMW, Toyota Supra)

    private fun boxerExtras(): List<DiagnosticComponent> = listOf(
        exhaustComponent("o2_b2s1_boxer", "Sensor O2 Banco 2 S1 (Boxer)", "o2_b2s1", listOf("P0150", "P0151")),
        exhaustComponent("catalytic_conv_b2_boxer", "Catalizador Banco 2 (Boxer)", "catalytic_converter_b2", listOf("P0430"))
    ) + turboComponents() // Subaru/Porsche often turbo

    private fun rotaryExtras(): List<DiagnosticComponent> = listOf(
        baseComponent("apex_seal", "Sellos Apex (Rotor)", ComponentCategory.IGNITION, "apex_seal",
            listOf("P0300", "P0301", "P0302"), listOf(RelatedPid("010C", "RPM", "rpm", "estable", "CRITICAL si compresión baja")),
            "Sellos de punta del rotor Wankel. Desgaste causa pérdida de compresión y misfire. Requiere rebuilt completo.")
    )

    // ═══════════════════════════════════════════════════════════
    //  L4 BASE COMPONENTS (original)
    // ═══════════════════════════════════════════════════════════
    private fun l4Components(): List<DiagnosticComponent> = listOf(
        DiagnosticComponent(
            id = "alternator", engineType = EngineType.L4, name = "Alternador",
            category = ComponentCategory.ELECTRICAL,
            description = "Genera corriente para sostener consumidores eléctricos y recargar batería. El DTC indica sistema/circuito afectado; no confirma pieza dañada sin pruebas.",
            location = "Frente o lateral del motor, alineado con banda serpentina y cable B+ grueso hacia batería/fusible principal.",
            commonFailures = listOf("Diodos rectificadores dañados", "Regulador fuera de rango", "Banda floja o contaminada", "Caída de voltaje en cable B+ o masa"),
            workshopTests = listOf(
                ComponentTest("Voltaje KOEO", "Medir batería con motor apagado y llave en OFF/ON.", "12.4V-12.7V en batería cargada.", "Multímetro"),
                ComponentTest("Carga en marcha", "Arrancar motor y medir en bornes de batería con luces/soplador encendidos.", "13.5V-14.8V según temperatura y estrategia.", "Multímetro"),
                ComponentTest("Caída positivo/negativo", "Medir caída entre B+ alternador y batería, luego carcasa alternador a negativo.", "Baja caída bajo carga; investigar cables si sube.", "Multímetro"),
                ComponentTest("Rizado AC", "Medir componente AC sobre batería con motor en marcha.", "Rizado excesivo sugiere diodos dañados.", "Multímetro/oscilloscope"),
                ComponentTest("Banda", "Inspeccionar tensión, patinaje, polea libre y alineación.", "Sin chillido, grietas, brillo ni saltos.", "Inspección + tensor")
            ),
            repairFlow = listOf(
                RepairStep(1, "Guardar DTC/freeze frame y medir batería base.", "Batería no descargada por causa externa."),
                RepairStep(2, "Probar carga y caídas de voltaje bajo carga.", "Falla aislada a alternador, cableado o masa."),
                RepairStep(3, "Corregir banda/tensor/conexiones antes de reemplazar.", "Sistema estable sin caída anormal."),
                RepairStep(4, "Reemplazar alternador solo si pruebas confirman salida/regulador/diodos.", "Carga dentro de rango."),
                RepairStep(5, "Borrar DTC y validar con consumidores eléctricos activos.", "No regresan P0562/P0563/P2503.")
            ),
            specs = listOf(ComponentSpec("Carga típica", "13.5V-14.8V", "Puede variar por carga inteligente y temperatura."), ComponentSpec("Rizado AC", "Bajo", "Rizado excesivo sugiere diodos dañados."), ComponentSpec("PID sistema", "0142", "Voltaje del módulo/control si el vehículo lo soporta.")),
            requiredTools = listOf("Multímetro automotriz", "Osciloscopio", "Pinza amperimétrica", "Probador de batería", "Diagrama eléctrico OEM"),
            safetyWarnings = listOf(SafetyWarning("CRITICAL", "Desconectar batería si se manipula alimentación principal B+."), SafetyWarning("WARNING", "No perforar cables innecesariamente; usar back-probe correcto."), SafetyWarning("WARNING", "Evitar cortos con puntas de prueba cerca de poleas y B+.")),
            relatedPids = listOf(RelatedPid("0142", "Voltaje del sistema", "V", "13.5-14.8V cargando", "CRITICAL si permanece bajo 12.6V con motor encendido")),
            relatedDtcs = listOf(RelatedDtc("P0562", "Voltaje del sistema bajo", 0.88, "CRITICAL"), RelatedDtc("P0563", "Voltaje del sistema alto", 0.78, "WARNING"), RelatedDtc("P2503", "Sistema de carga bajo", 0.82, "CRITICAL")),
            position = ComponentPosition(-45f, 10f, -24f, "MOTOR_3D"), meshKey = "alternator"
        ),
        ignitionComponent("spark_plugs", "Bujía 1", "spark_plug_0", listOf("P0301", "P0300")),
        ignitionComponent("ignition_coils", "Bobina COP 1", "ignition_coil_0", listOf("P0351", "P0301")),
        ignitionComponent("spark_plugs_cyl_2", "Bujía 2", "spark_plug_1", listOf("P0302", "P0300")),
        ignitionComponent("spark_plugs_cyl_3", "Bujía 3", "spark_plug_2", listOf("P0303", "P0300")),
        ignitionComponent("spark_plugs_cyl_4", "Bujía 4", "spark_plug_3", listOf("P0304", "P0300")),
        ignitionComponent("ignition_coils_cyl_2", "Bobina COP 2", "ignition_coil_1", listOf("P0352", "P0302")),
        ignitionComponent("ignition_coils_cyl_3", "Bobina COP 3", "ignition_coil_2", listOf("P0353", "P0303")),
        ignitionComponent("ignition_coils_cyl_4", "Bobina COP 4", "ignition_coil_3", listOf("P0354", "P0304")),
        airComponent("maf_sensor", "MAF", "maf_sensor", listOf("P0100", "P0101", "P0102", "P0103", "P0171", "P0172"), listOf(RelatedPid("0110", "Flujo MAF", "g/s", "estable segun cilindrada", "WARNING si se queda fijo o ilógico"))),
        airComponent("map_sensor", "MAP", "map_sensor", listOf("P0105", "P0106", "P0107", "P0108"), listOf(RelatedPid("010B", "Presión MAP", "kPa", "KOEO cerca barométrica", "WARNING si no responde al vacío/carga"))),
        airComponent("iat_sensor", "Sensor IAT", "iat_sensor", listOf("P0110", "P0112", "P0113"), listOf(RelatedPid("010F", "Temperatura aire admisión", "°C", "cercana ambiente KOEO", "WARNING si queda fija"))),
        airComponent("throttle_body", "Cuerpo de aceleración", "throttle_body", listOf("P0121", "P0122", "P0123", "P2135"), listOf(RelatedPid("0111", "Posición acelerador", "%", "barrido suave", "CRITICAL si salta o se contradice"))),
        sensorComponent("ect_sensor", "Sensor ECT", "ect_sensor", listOf("P0115", "P0116", "P0117", "P0118", "P0128"), listOf(RelatedPid("0105", "Temperatura refrigerante", "°C", "sube progresivo al calentar", "CRITICAL si marca extremo"))),
        sensorComponent("ckp_sensor", "Sensor CKP cigüeñal", "ckp_sensor", listOf("P0335", "P0336", "P0337", "P0338"), listOf(RelatedPid("010C", "RPM", "rpm", "debe existir durante arranque", "CRITICAL si no hay RPM al dar marcha"))),
        sensorComponent("camshaft_sensor", "Sensor CMP árbol de levas", "camshaft_sensor", listOf("P0340", "P0341", "P0342", "P0343"), listOf(RelatedPid("010C", "RPM", "rpm", "sincronía con CKP", "WARNING si arranque largo"))),
        fuelComponent("injector_1", "Inyector 1", "injector_0", listOf("P0201", "P0301")),
        fuelComponent("injector_2", "Inyector 2", "injector_1", listOf("P0202", "P0302")),
        fuelComponent("injector_3", "Inyector 3", "injector_2", listOf("P0203", "P0303")),
        fuelComponent("injector_4", "Inyector 4", "injector_3", listOf("P0204", "P0304")),
        fuelComponent("injectors", "Riel combustible", "fuel_rail", listOf("P0087", "P0088", "P0191")),
        fuelComponent("fuel_pressure_sensor", "Sensor presión combustible", "fuel_pressure_sensor", listOf("P0190", "P0191", "P0192", "P0193", "P0087")),
        exhaustComponent("o2_upstream", "Sensor O2/AFR delantero", "o2_upstream", listOf("P0130", "P0131", "P0132", "P0133", "P0135", "P0171", "P0172")),
        exhaustComponent("catalytic_conv", "Catalizador", "catalytic_converter", listOf("P0420", "P0430")),
        exhaustComponent("o2_downstream", "Sensor O2 trasero", "o2_downstream", listOf("P0136", "P0137", "P0138", "P0141", "P0420")),
        coolingComponent("water_pump", "Bomba de agua", "water_pump", listOf("P0217", "P0128"), listOf(RelatedPid("0105", "Temperatura refrigerante", "°C", "estable en rango operativo", "CRITICAL si sube sin control"))),
        coolingComponent("thermostat_housing", "Termostato", "thermostat_housing", listOf("P0128", "P0116"), listOf(RelatedPid("0105", "Temperatura refrigerante", "°C", "alcanza temperatura de operación", "WARNING si tarda demasiado"))),
        lubricationComponent("oil_filter", "Filtro de aceite", "oil_filter", listOf("P0520", "P0521", "P0522", "P0523")),
        lubricationComponent("oil_pan", "Cárter de aceite", "oil_pan", listOf("P0520", "P0522")),
        baseComponent("serpentine_belt", "Banda serpentina", ComponentCategory.ELECTRICAL, "serpentine_belt", listOf("P0562", "P2503"), emptyList(), "Transmite movimiento a alternador, bomba y accesorios; el patinaje puede parecer falla eléctrica."),
        relayFuseComponent("fuse_battery_main", "Fusibles principales", "fuse_battery_main", listOf("P0562", "P0563")),
        relayFuseComponent("fuse_ecm_batt", "Fusible ECM memoria", "fuse_ecm_batt", listOf("P0603", "P0685")),
        relayFuseComponent("fuse_ecm_ign", "Fusible ECM ignición", "fuse_ecm_ign", listOf("P0685", "U0100")),
        relayFuseComponent("fuse_injectors", "Fusible inyectores", "fuse_injectors", listOf("P0201", "P0202", "P0203", "P0204")),
        relayFuseComponent("fuse_ignition_coils", "Fusible bobinas", "fuse_ignition_coils", listOf("P0351", "P0352", "P0353", "P0354", "P0300")),
        relayFuseComponent("fuse_o2_heater", "Fusible calentadores O2", "fuse_o2_heater", listOf("P0135", "P0141")),
        relayFuseComponent("fuse_maf_map", "Fusible MAF/MAP/IAT", "fuse_maf_map", listOf("P0100", "P0105", "P0110")),
        relayFuseComponent("fuse_fuel_pump", "Fusible bomba combustible", "fuse_fuel_pump", listOf("P0230", "P0087")),
        relayFuseComponent("fuse_obd_dlc", "Fusible OBD-II DLC", "fuse_obd_dlc", listOf("U0100")),
        relayFuseComponent("relay_fuel_pump", "Relé bomba combustible", "relay_fuel_pump", listOf("P0230", "P0087")),
        relayFuseComponent("relay_starter", "Relé motor de arranque", "relay_starter", listOf("P0512")),
        DiagnosticComponent(
            id = "maf_harness", engineType = EngineType.L4, name = "Arnés sensor MAF",
            category = ComponentCategory.HARNESS,
            description = "Ramal de alimentación, masa, señal y referencia del sensor MAF/IAT. Un DTC de MAF suele ser circuito/aire falso antes que sensor dañado.",
            location = "Ducto de admisión entre filtro y cuerpo de aceleración; ramal sujeto al arnés frontal/superior.",
            commonFailures = listOf("Cable quebrado por vibración", "Terminal abierto", "Sulfato por ingreso de agua", "Aislamiento rozado"),
            workshopTests = listOf(ComponentTest("Alimentación y masa", "Back-probe con sensor conectado y carga real.", "Voltaje estable y masa con caída baja.", "Multímetro")),
            repairFlow = listOf(RepairStep(1, "Inspeccionar conector/ramal con prueba de movimiento.", "Lectura MAF no se corta.")),
            specs = listOf(ComponentSpec("Señal", "variable", "Debe cambiar con flujo de aire.")),
            requiredTools = listOf("Puntas back-probe", "Diagrama OEM", "Osciloscopio"),
            safetyWarnings = listOf(SafetyWarning("WARNING", "No abrir terminales con puntas gruesas.")),
            relatedPids = listOf(RelatedPid("0110", "Flujo MAF", "g/s", "coherente con RPM/carga", "WARNING si no responde")),
            relatedDtcs = listOf(RelatedDtc("P0100", "Circuito MAF", 0.82, "WARNING")),
            position = ComponentPosition(64f, -22f, -32f, "WIRING_HARNESS"), meshKey = "signal_wire_maf"
        )
    )

    // ═══════════════════════════════════════════════════════════
    //  EV COMPONENTS
    // ═══════════════════════════════════════════════════════════
    private fun evComponents(): List<DiagnosticComponent> = listOf(
        baseComponent("traction_motor", "Motor de tracción", ComponentCategory.EV_HIGH_VOLTAGE, "electric_motor", listOf("P0A90"), emptyList(), "Convierte energía eléctrica en torque."),
        baseComponent("inverter", "Inversor DC/AC", ComponentCategory.EV_HIGH_VOLTAGE, "inverter_module", listOf("P0A78"), emptyList(), "Controla potencia hacia motor eléctrico."),
        baseComponent("hv_battery_ev", "Batería HV Principal", ComponentCategory.EV_HIGH_VOLTAGE, "hv_battery_pack", listOf("P0A80", "P0A7F"), emptyList(), "Pack de celdas de litio. 300-800V según vehículo."),
        baseComponent("onboard_charger_ev", "Cargador AC a bordo", ComponentCategory.EV_HIGH_VOLTAGE, "onboard_charger", listOf("P0A09"), emptyList(), "Convierte AC del cargador público a DC para batería."),
        baseComponent("dc_dc_converter_ev", "Convertidor DC-DC", ComponentCategory.EV_HIGH_VOLTAGE, "dc_dc_converter", listOf("P0562", "P0A0F"), listOf(RelatedPid("0142", "Voltaje 12V", "V", "14V normal", "CRITICAL si <12V")), "Alimenta sistema 12V desde batería HV."),
        baseComponent("thermal_management_ev", "Sistema gestión térmica", ComponentCategory.COOLING, "ev_thermal_system", listOf("P0A80"), emptyList(), "Enfría/calienta batería y motor para rendimiento óptimo."),
        baseComponent("charge_port_ev", "Puerto de carga", ComponentCategory.CONNECTOR, "charge_port", listOf("P0A09"), emptyList(), "Conector físico para carga AC/DC. Incluye piloto y comunicación."),
        baseComponent("bms_ev", "BMS (Battery Management System)", ComponentCategory.EV_HIGH_VOLTAGE, "bms_module", listOf("P0A80", "P0AA6"), emptyList(), "Monitorea voltaje, temperatura y balance de cada celda de la batería HV.")
    )

    // ═══════════════════════════════════════════════════════════
    //  HELPER FACTORIES (unchanged)
    // ═══════════════════════════════════════════════════════════
    private fun derivePosition(id: String, category: ComponentCategory): ComponentPosition {
        val h = java.lang.Math.abs(id.hashCode())
        val f1 = (h % 1000) / 1000f
        val f2 = ((h / 1000) % 1000) / 1000f
        val f3 = ((h / 1000000) % 1000) / 1000f

        fun lerp(min: Float, max: Float, f: Float): Float = min + (max - min) * f

        var x = 0f
        var y = 0f
        var z = 0f

        when (category) {
            ComponentCategory.IGNITION -> {
                val cyl = when {
                    id.endsWith("2") || id.contains("cyl_2") -> 2
                    id.endsWith("3") || id.contains("cyl_3") -> 3
                    id.endsWith("4") || id.contains("cyl_4") -> 4
                    id.endsWith("5") || id.contains("cyl_5") -> 5
                    id.endsWith("6") || id.contains("cyl_6") -> 6
                    id.endsWith("7") || id.contains("cyl_7") -> 7
                    id.endsWith("8") || id.contains("cyl_8") -> 8
                    id.endsWith("9") || id.contains("cyl_9") -> 9
                    id.endsWith("10") || id.contains("cyl_10") -> 10
                    else -> 1
                }
                x = -16f + (cyl - 1) * 4f
                y = -26f + f1 * 2f
                z = 28f + f2 * 2f
            }
            ComponentCategory.AIR_INTAKE -> {
                x = lerp(-30f, -16f, f1)
                y = lerp(-30f, -22f, f2)
                z = lerp(18f, 26f, f3)
            }
            ComponentCategory.FUEL -> {
                if (id.contains("injector")) {
                    val cyl = when {
                        id.endsWith("2") -> 2
                        id.endsWith("3") -> 3
                        id.endsWith("4") -> 4
                        id.endsWith("5") -> 5
                        id.endsWith("6") -> 6
                        id.endsWith("7") -> 7
                        id.endsWith("8") -> 8
                        else -> 1
                    }
                    x = -16f + (cyl - 1) * 4f + 1f
                    y = -24f + f1 * 1f
                    z = 26f + f2 * 1f
                } else if (id.contains("pump") || id.contains("filter") || id.contains("canister")) {
                    x = lerp(-12f, 12f, f1)
                    y = lerp(20f, 32f, f2)
                    z = lerp(-25f, -15f, f3)
                } else {
                    x = lerp(-10f, 10f, f1)
                    y = lerp(-25f, -15f, f2)
                    z = lerp(15f, 25f, f3)
                }
            }
            ComponentCategory.EXHAUST -> {
                x = lerp(-6f, 6f, f1)
                y = lerp(-20f, 32f, f2)
                z = lerp(-32f, -24f, f3)
            }
            ComponentCategory.COOLING -> {
                x = lerp(-25f, 25f, f1)
                y = lerp(-35f, -32f, f2)
                z = lerp(12f, 24f, f3)
            }
            ComponentCategory.LUBRICATION -> {
                x = lerp(-8f, 8f, f1)
                y = lerp(-24f, -14f, f2)
                z = lerp(-28f, -18f, f3)
            }
            ComponentCategory.SENSOR -> {
                x = lerp(-32f, 32f, f1)
                y = lerp(-34f, -12f, f2)
                z = lerp(12f, 28f, f3)
            }
            ComponentCategory.RELAY_FUSE -> {
                x = lerp(-32f, -22f, f1)
                y = lerp(-24f, -16f, f2)
                z = lerp(24f, 30f, f3)
            }
            ComponentCategory.TRANSMISSION -> {
                x = lerp(-6f, 6f, f1)
                y = lerp(-4f, 12f, f2)
                z = lerp(-8f, 8f, f3)
            }
            ComponentCategory.SUSPENSION, ComponentCategory.BRAKES -> {
                val isLeft = id.contains("left") || id.contains("_l") || f1 < 0.5f
                val isFront = id.contains("front") || id.contains("input") || f2 < 0.5f
                x = if (isLeft) -44f else 44f
                y = if (isFront) -22f else 22f
                z = if (category == ComponentCategory.BRAKES) -12f else -10f
                x += f3 * 3f - 1.5f
                y += f1 * 3f - 1.5f
            }
            ComponentCategory.STEERING -> {
                x = lerp(-22f, 22f, f1)
                y = lerp(-24f, -18f, f2)
                z = lerp(-12f, -8f, f3)
            }
            ComponentCategory.BODY_CONTROL -> {
                x = lerp(-25f, 25f, f1)
                y = lerp(-14f, 2f, f2)
                z = lerp(14f, 24f, f3)
            }
            ComponentCategory.EV_HIGH_VOLTAGE -> {
                x = lerp(-18f, 18f, f1)
                y = lerp(-18f, 18f, f2)
                z = lerp(-24f, -18f, f3)
            }
            ComponentCategory.TURBO_SUPERCHARGER -> {
                x = lerp(14f, 24f, f1)
                y = lerp(-24f, -14f, f2)
                z = lerp(10f, 20f, f3)
            }
            ComponentCategory.DIESEL_EMISSIONS -> {
                x = lerp(-8f, 8f, f1)
                y = lerp(10f, 26f, f2)
                z = lerp(-26f, -18f, f3)
            }
            else -> {
                x = lerp(-30f, 30f, f1)
                y = lerp(-32f, 15f, f2)
                z = lerp(10f, 28f, f3)
            }
        }
        return ComponentPosition(x, y, z, "MOTOR_3D")
    }

    private fun ignitionComponent(id: String, name: String, mesh: String, dtcs: List<String>): DiagnosticComponent =
        DiagnosticComponent(
            id = id, engineType = EngineType.L4, name = name, category = ComponentCategory.IGNITION,
            description = "Elemento del sistema de encendido. Confirmar chispa, compresión y combustible antes de reemplazar piezas por DTC de misfire.",
            location = "Parte superior de culata, cilindro 1 o banco correspondiente.",
            commonFailures = listOf("Aislante carbonizado", "Bota COP con fuga", "Electrodo desgastado", "Aceite/combustible en pozo"),
            workshopTests = listOf(ComponentTest("Intercambio controlado", "Mover pieza a cilindro vecino si el motor lo permite.", "El DTC se mueve solo si la pieza es causa.", "Escáner + herramientas manuales")),
            repairFlow = listOf(RepairStep(1, "Leer freeze frame y contador misfire.", "Cilindro confirmado."), RepairStep(2, "Probar chispa/compresión/inyector.", "Causa aislada.")),
            specs = listOf(ComponentSpec("Separación bujía", "OEM", "Usar etiqueta o manual.")),
            requiredTools = listOf("Escáner", "Probador COP", "Torquímetro", "Medidor compresión"),
            safetyWarnings = listOf(SafetyWarning("WARNING", "Alto voltaje de encendido; no manipular bobinas energizadas.")),
            relatedPids = listOf(RelatedPid("010C", "RPM", "rpm", "estable en ralentí", "WARNING si cae con misfire")),
            relatedDtcs = dtcs.map { RelatedDtc(it, "Misfire/encendido relacionado", 0.7, "WARNING") },
            position = derivePosition(id, ComponentCategory.IGNITION), meshKey = mesh
        )

    private fun airComponent(id: String, name: String, mesh: String, dtcs: List<String>, pids: List<RelatedPid>): DiagnosticComponent =
        baseComponent(id, name, ComponentCategory.AIR_INTAKE, mesh, dtcs, pids, "Admisión de aire y medición de carga del motor.")

    private fun fuelComponent(id: String, name: String, mesh: String, dtcs: List<String>): DiagnosticComponent =
        baseComponent(id, name, ComponentCategory.FUEL, mesh, dtcs, listOf(RelatedPid("0106", "Fuel trim corto", "%", "-10% a +10%", "WARNING si corrige extremo")), "Suministro de combustible bajo presión.")

    private fun exhaustComponent(id: String, name: String, mesh: String, dtcs: List<String>): DiagnosticComponent =
        baseComponent(id, name, ComponentCategory.EXHAUST, mesh, dtcs, emptyList(), "Tratamiento de gases y eficiencia de emisiones.")

    private fun sensorComponent(id: String, name: String, mesh: String, dtcs: List<String>, pids: List<RelatedPid>): DiagnosticComponent =
        baseComponent(id, name, ComponentCategory.SENSOR, mesh, dtcs, pids, "Sensor crítico para estrategia de arranque, mezcla, sincronía o protección térmica.")

    private fun coolingComponent(id: String, name: String, mesh: String, dtcs: List<String>, pids: List<RelatedPid>): DiagnosticComponent =
        baseComponent(id, name, ComponentCategory.COOLING, mesh, dtcs, pids, "Sistema de enfriamiento; confirmar temperatura real, flujo, ventiladores y purga antes de reemplazar.")

    private fun lubricationComponent(id: String, name: String, mesh: String, dtcs: List<String>): DiagnosticComponent =
        baseComponent(id, name, ComponentCategory.LUBRICATION, mesh, dtcs, listOf(RelatedPid("010C", "RPM", "rpm", "presión depende de régimen y temperatura", "CRITICAL si hay ruido o luz de aceite")), "Lubricación de motor; cualquier alerta requiere validar nivel, presión real y fugas antes de operar.")

    private fun relayFuseComponent(id: String, name: String, mesh: String, dtcs: List<String>): DiagnosticComponent =
        baseComponent(id, name, ComponentCategory.RELAY_FUSE, mesh, dtcs, emptyList(), "Protección y distribución eléctrica con fusible/relé.")

    private fun baseComponent(id: String, name: String, category: ComponentCategory, mesh: String, dtcs: List<String>, pids: List<RelatedPid>, description: String): DiagnosticComponent =
        DiagnosticComponent(
            id = id, engineType = EngineType.L4, name = name, category = category,
            description = "$description Un DTC indica circuito/sistema afectado, no confirma pieza dañada sin pruebas.",
            location = "Ubicación guiada por el visor 3D; confirmar por VIN y manual OEM.",
            commonFailures = listOf("Conector flojo/sulfatado", "Arnés con roce o calor", "Lectura fuera de rango por causa secundaria"),
            workshopTests = listOf(ComponentTest("Confirmación circuito", "Verificar alimentación, masa y señal bajo carga.", "Valores coherentes con condición real.", "Multímetro/osciloscopio")),
            repairFlow = listOf(RepairStep(1, "Guardar DTC y freeze frame.", "Datos preservados."), RepairStep(2, "Confirmar con prueba física.", "Causa aislada.")),
            specs = listOf(ComponentSpec("Rango", "OEM", "Validar por año/motor.")),
            requiredTools = listOf("Escáner OBD-II", "Multímetro", "Osciloscopio", "Manual OEM"),
            safetyWarnings = listOf(SafetyWarning("WARNING", "No reemplazar piezas sin confirmar alimentación, masa, señal y condición mecánica.")),
            relatedPids = pids,
            relatedDtcs = dtcs.map { RelatedDtc(it, "DTC relacionado a $name", 0.65, "WARNING") },
            position = derivePosition(id, category), meshKey = mesh
        )
}
