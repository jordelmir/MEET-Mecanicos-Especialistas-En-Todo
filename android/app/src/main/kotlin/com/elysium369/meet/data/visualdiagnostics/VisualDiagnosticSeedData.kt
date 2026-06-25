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
            EngineType.EV -> evComponents()
            else -> l4Components().map { it.copy(engineType = engineType) }
        }
    }

    private fun l4Components(): List<DiagnosticComponent> = listOf(
        DiagnosticComponent(
            id = "alternator",
            engineType = EngineType.L4,
            name = "Alternador",
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
            specs = listOf(
                ComponentSpec("Carga típica", "13.5V-14.8V", "Puede variar por carga inteligente y temperatura."),
                ComponentSpec("Rizado AC", "Bajo", "Rizado excesivo sugiere diodos dañados."),
                ComponentSpec("PID sistema", "0142", "Voltaje del módulo/control si el vehículo lo soporta.")
            ),
            requiredTools = listOf("Multímetro automotriz", "Osciloscopio", "Pinza amperimétrica", "Probador de batería", "Diagrama eléctrico OEM"),
            safetyWarnings = listOf(
                SafetyWarning("CRITICAL", "Desconectar batería si se manipula alimentación principal B+."),
                SafetyWarning("WARNING", "No perforar cables innecesariamente; usar back-probe correcto."),
                SafetyWarning("WARNING", "Evitar cortos con puntas de prueba cerca de poleas y B+.")
            ),
            relatedPids = listOf(RelatedPid("0142", "Voltaje del sistema", "V", "13.5-14.8V cargando", "CRITICAL si permanece bajo 12.6V con motor encendido")),
            relatedDtcs = listOf(
                RelatedDtc("P0562", "Voltaje del sistema bajo", 0.88, "CRITICAL"),
                RelatedDtc("P0563", "Voltaje del sistema alto", 0.78, "WARNING"),
                RelatedDtc("P2503", "Sistema de carga bajo", 0.82, "CRITICAL")
            ),
            position = ComponentPosition(-45f, 10f, -24f, "MOTOR_3D"),
            meshKey = "alternator"
        ),
        ignitionComponent("spark_plugs", "Bujía 1", "spark_plug_0", listOf("P0301", "P0300")),
        ignitionComponent("ignition_coils", "Bobina COP 1", "ignition_coil_0", listOf("P0351", "P0301")),
        airComponent("maf_sensor", "MAF", "maf_sensor", listOf("P0100", "P0102"), listOf(RelatedPid("0110", "Flujo MAF", "g/s", "estable segun cilindrada", "WARNING si se queda fijo o ilógico"))),
        airComponent("throttle_body", "Cuerpo de aceleración", "throttle_body", listOf("P0121", "P2135"), listOf(RelatedPid("0111", "Posición acelerador", "%", "barrido suave", "CRITICAL si salta o se contradice"))),
        fuelComponent("injectors", "Riel combustible", "fuel_rail", listOf("P0201", "P0087")),
        exhaustComponent("catalytic_conv", "Catalizador", "catalytic_converter", listOf("P0420", "P0430")),
        relayFuseComponent("fuse_battery_main", "Fusibles principales", "fuse_battery_main", listOf("P0562", "P0563")),
        relayFuseComponent("relay_fuel_pump", "Relé bomba combustible", "relay_fuel_pump", listOf("P0230", "P0087")),
        DiagnosticComponent(
            id = "maf_harness",
            engineType = EngineType.L4,
            name = "Arnés sensor MAF",
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
            position = ComponentPosition(64f, -22f, -32f, "WIRING_HARNESS"),
            meshKey = "signal_wire_maf"
        )
    )

    private fun ignitionComponent(id: String, name: String, mesh: String, dtcs: List<String>): DiagnosticComponent =
        DiagnosticComponent(
            id = id,
            engineType = EngineType.L4,
            name = name,
            category = ComponentCategory.IGNITION,
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
            position = ComponentPosition(0f, -35f, 0f, "MOTOR_3D"),
            meshKey = mesh
        )

    private fun airComponent(id: String, name: String, mesh: String, dtcs: List<String>, pids: List<RelatedPid>): DiagnosticComponent =
        baseComponent(id, name, ComponentCategory.AIR_INTAKE, mesh, dtcs, pids, "Admisión de aire y medición de carga del motor.")

    private fun fuelComponent(id: String, name: String, mesh: String, dtcs: List<String>): DiagnosticComponent =
        baseComponent(id, name, ComponentCategory.FUEL, mesh, dtcs, listOf(RelatedPid("0106", "Fuel trim corto", "%", "-10% a +10%", "WARNING si corrige extremo")), "Suministro de combustible bajo presión.")

    private fun exhaustComponent(id: String, name: String, mesh: String, dtcs: List<String>): DiagnosticComponent =
        baseComponent(id, name, ComponentCategory.EXHAUST, mesh, dtcs, emptyList(), "Tratamiento de gases y eficiencia de emisiones.")

    private fun relayFuseComponent(id: String, name: String, mesh: String, dtcs: List<String>): DiagnosticComponent =
        baseComponent(id, name, ComponentCategory.RELAY_FUSE, mesh, dtcs, emptyList(), "Protección y distribución eléctrica con fusible/relé.")

    private fun baseComponent(
        id: String,
        name: String,
        category: ComponentCategory,
        mesh: String,
        dtcs: List<String>,
        pids: List<RelatedPid>,
        description: String
    ): DiagnosticComponent =
        DiagnosticComponent(
            id = id,
            engineType = EngineType.L4,
            name = name,
            category = category,
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
            position = ComponentPosition(0f, 0f, 0f, "MOTOR_3D"),
            meshKey = mesh
        )

    private fun evComponents(): List<DiagnosticComponent> = listOf(
        baseComponent("traction_motor", "Motor de tracción", ComponentCategory.EV_HIGH_VOLTAGE, "electric_motor", listOf("P0A90"), emptyList(), "Convierte energía eléctrica en torque."),
        baseComponent("inverter", "Inversor DC/AC", ComponentCategory.EV_HIGH_VOLTAGE, "inverter_module", listOf("P0A78"), emptyList(), "Controla potencia hacia motor eléctrico."),
        baseComponent("hv_battery", "Batería HV", ComponentCategory.EV_HIGH_VOLTAGE, "hv_battery_pack", listOf("P0A80"), emptyList(), "Almacena energía de alto voltaje.")
    )
}

