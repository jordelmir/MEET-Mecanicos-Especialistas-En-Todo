package com.elysium369.meet.core.obd

import javax.inject.Inject
import javax.inject.Singleton

data class ExpertDiagnosticProcedure(
    val title: String,
    val description: String,
    val severity: DiagnosticSeverity,
    val probableCauses: List<String>,
    val testSteps: List<String>
)


@Singleton
class LocalExpertSystem @Inject constructor() {

    /**
     * Analiza los datos en vivo para buscar anomalías basadas en heurísticas automotrices profesionales.
     * Retorna una lista de procedimientos a seguir por el mecánico.
     */
    fun analyzeLiveTelemetry(liveData: Map<String, Float>): List<ExpertDiagnosticProcedure> {
        val procedures = mutableListOf<ExpertDiagnosticProcedure>()

        checkVoltage(liveData, procedures)
        checkCoolant(liveData, procedures)
        checkFuelTrims(liveData, procedures)
        checkMaf(liveData, procedures)
        checkMap(liveData, procedures)
        checkTps(liveData, procedures)
        checkO2Sensors(liveData, procedures)
        checkO2Bank2(liveData, procedures)
        checkCatalyticEfficiency(liveData, procedures)
        checkWidebandO2(liveData, procedures)
        checkIat(liveData, procedures)
        checkLoadAndTiming(liveData, procedures)
        checkBatteryAlternator(liveData, procedures)
        checkFuelTrimsBank2(liveData, procedures)
        checkMisfireAnalysis(liveData, procedures)
        checkThermalSystem(liveData, procedures)
        checkFuelPressure(liveData, procedures)
        checkFuelSystemStatus(liveData, procedures)
        checkTransmissionHealth(liveData, procedures)
        checkSpeedVsRpmSlippage(liveData, procedures)

        if (procedures.isEmpty()) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "Sistemas Dentro de Rango Normal",
                    description = "Todos los parámetros monitorizados (Voltaje, Temperatura, Mezcla, Sensores de Aire/Presión) se encuentran operando dentro de los márgenes nominales del fabricante.",
                    severity = DiagnosticSeverity.INFO,
                    probableCauses = emptyList(),
                    testSteps = listOf(
                        "Continúe el monitoreo de rutina.",
                        "Revise si hay Códigos de Falla Pendientes (DTCs) que no hayan encendido la luz MIL aún."
                    )
                )
            )
        }

        return procedures
    }

    private fun checkVoltage(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        val voltage = liveData["0142"] ?: return

        // Motor en marcha asume que si hay RPM ("010C" > 400), el alternador debe estar cargando.
        val rpm = liveData["010C"] ?: 0f
        val engineRunning = rpm > 400f

        if (engineRunning) {
            if (voltage < 13.0f) {
                procedures.add(
                    ExpertDiagnosticProcedure(
                        title = "Voltaje de Carga Deficiente",
                        description = "El alternador no está suministrando voltaje suficiente para mantener el sistema eléctrico y cargar la batería.",
                        severity = DiagnosticSeverity.HIGH,
                        probableCauses = listOf(
                            "Alternador defectuoso (diodos o regulador de voltaje).",
                            "Banda de accesorios suelta o desgastada.",
                            "Caída de voltaje en el cable positivo (B+) o tierra del motor."
                        ),
                        testSteps = listOf(
                            "1. Mida el voltaje directamente en los postes de la batería con un multímetro. Debe ser mayor a 13.5V.",
                            "2. Realice una prueba de caída de voltaje: Mida en voltios DC conectando la punta roja al poste positivo del alternador y la negra al positivo de la batería. No debe exceder 0.2V.",
                            "3. Mida la caída de voltaje en la tierra: Punta roja en la carcasa del alternador y negra en el poste negativo de la batería. No debe exceder 0.2V.",
                            "4. Si hay rizado (AC) mayor a 0.5V medido en los bornes de la batería, sospeche de diodos averiados."
                        )
                    )
                )
            } else if (voltage > 15.2f) {
                procedures.add(
                    ExpertDiagnosticProcedure(
                        title = "Sobrecarga del Sistema",
                        description = "El voltaje del sistema es peligrosamente alto y puede freír módulos electrónicos (ECUs) o hervir la batería.",
                        severity = DiagnosticSeverity.CRITICAL,
                        probableCauses = listOf(
                            "Regulador de voltaje del alternador en cortocircuito.",
                            "Fallo en la comunicación LIN/PWM entre el PCM y el alternador inteligente.",
                            "Batería internamente en corto."
                        ),
                        testSteps = listOf(
                            "1. APAGUE EL MOTOR inmediatamente para evitar daños a la electrónica sensible.",
                            "2. Verifique la línea de señal del alternador hacia la computadora (PCM).",
                            "3. Pruebe el alternador en banco. Si el regulador está integrado, reemplace la unidad completa."
                        )
                    )
                )
            }
        } else {
            // Motor apagado (switch on)
            if (voltage < 11.5f && voltage > 5.0f) {
                procedures.add(
                    ExpertDiagnosticProcedure(
                        title = "Batería Débil o Descargada",
                        description = "El voltaje en reposo es insuficiente para un arranque confiable. Riesgo inminente de no poder iniciar el motor.",
                        severity = DiagnosticSeverity.MODERATE,
                        probableCauses = listOf(
                            "Batería vieja con alta resistencia interna.",
                            "Fuga de corriente parásita excesiva.",
                            "El vehículo estuvo en ignición por mucho tiempo sin motor arrancado."
                        ),
                        testSteps = listOf(
                            "1. Apague todas las luces y accesorios.",
                            "2. Si la batería no recupera al menos 12.0V en 5 minutos, cargue externamente y aplique probador de CCA (Cold Cranking Amps).",
                            "3. Si la batería es buena, haga prueba de consumo parásito insertando un multímetro (Amperímetro) en serie en el polo negativo."
                        )
                    )
                )
            }
        }
    }

    private fun checkCoolant(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        val temp = liveData["0105"] ?: return

        if (temp > 110f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "Sobrecalentamiento del Motor Detectado",
                    description = "La temperatura del refrigerante es peligrosamente alta (>110°C), lo cual puede deformar cabezotes/culatas y soplar empacaduras.",
                    severity = DiagnosticSeverity.CRITICAL,
                    probableCauses = listOf(
                        "Bajo nivel de refrigerante o fuga.",
                        "Termostato atascado en posición cerrada.",
                        "Ventiladores del radiador (electroventiladores) inoperativos o relé quemado.",
                        "Bomba de agua con aspas desgastadas."
                    ),
                    testSteps = listOf(
                        "1. APAGUE EL MOTOR para evitar daños catastróficos.",
                        "2. PRECAUCIÓN: NO abra la tapa del radiador o del depósito de expansión mientras el motor esté caliente, riesgo de quemaduras.",
                        "3. Revise visualmente si hay fugas bajo el vehículo o alrededor de mangueras y bomba de agua.",
                        "4. Active el ventilador usando las pruebas de actuadores del scanner bi-direccional. Si no enciende, verifique relés, fusibles y el motor del electroventilador.",
                        "5. Con el motor frío, pruebe si el termostato abre tocando la manguera inferior del radiador al calentar (debería calentarse súbitamente cuando abre a ~85-90°C)."
                    )
                )
            )
        } else if (temp < 60f) {
            // Requeriría saber el tiempo desde arranque (Run Time Since Engine Start "011F") para ser más preciso.
            val runTime = liveData["011F"] ?: 0f
            if (runTime > 600f) { // Más de 10 minutos
                procedures.add(
                    ExpertDiagnosticProcedure(
                        title = "Motor Operando Frío (Termostato Posiblemente Abierto)",
                        description = "El motor no ha alcanzado la temperatura operativa ideal tras 10 minutos, causando un consumo excesivo de combustible (lazo abierto perpetuo).",
                        severity = DiagnosticSeverity.MODERATE,
                        probableCauses = listOf(
                            "Termostato atascado en posición abierta (o extraído).",
                            "Sensor ECT emitiendo señal falsa o corto a tierra.",
                            "Clima extremadamente frío (menor probabilidad sin termostato defectuoso)."
                        ),
                        testSteps = listOf(
                            "1. Use un termómetro láser/infrarrojo apuntando a la carcasa del termostato para verificar la temperatura física.",
                            "2. Compare el valor del láser con la lectura de $temp °C vista en vivo. Si discrepan enormemente, el sensor ECT o su cableado fallan.",
                            "3. Si coinciden, reemplace el termostato."
                        )
                    )
                )
            }
        }
    }

    private fun checkFuelTrims(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        val stft1 = liveData["0106"]
        val ltft1 = liveData["0107"]
        val totalTrim1 = (stft1 ?: 0f) + (ltft1 ?: 0f)

        if (stft1 == null && ltft1 == null) return

        if (totalTrim1 > 15f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "Mezcla de Combustible Pobre (Condición Lean)",
                    description = "La computadora (PCM) está sumando más de 15% de combustible a los inyectores porque detecta demasiado oxígeno en el escape.",
                    severity = DiagnosticSeverity.HIGH,
                    probableCauses = listOf(
                        "Fuga de vacío severa (Múltiple de admisión, mangueras PCV o servofreno).",
                        "Baja presión de combustible (bomba débil, filtro obstruido).",
                        "Inyectores obstruidos.",
                        "Sensor MAF sucio (sub-reportando el aire entrante)."
                    ),
                    testSteps = listOf(
                        "1. Acelere el motor a 2500 RPM. Si el Trim mejora (baja a cerca de 0%), el problema es una fuga de vacío. Si el Trim empeora o no cambia, es un problema de flujo de combustible o un MAF sucio.",
                        "2. Aplique humo con una máquina generadora en el múltiple de admisión para buscar fugas de vacío.",
                        "3. Conecte un manómetro al riel de inyectores para confirmar si la presión de la bomba es la indicada por fábrica (usualmente 40-50 PSI).",
                        "4. Limpie el sensor MAF con limpiador especializado."
                    )
                )
            )
        } else if (totalTrim1 < -15f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "Mezcla de Combustible Rica (Condición Rich)",
                    description = "La computadora (PCM) está restando más de 15% de combustible porque detecta una combustión muy saturada (falta de oxígeno).",
                    severity = DiagnosticSeverity.HIGH,
                    probableCauses = listOf(
                        "Inyectores goteando o atascados abiertos.",
                        "Presión de combustible excesivamente alta (regulador averiado).",
                        "Sensor MAF descalibrado (sobre-reportando aire).",
                        "Filtro de aire severamente obstruido."
                    ),
                    testSteps = listOf(
                        "1. Revise el regulador de presión de combustible (si es externo al tanque). Retire su manguera de vacío, si sale gasolina, está averiado.",
                        "2. Revise si el filtro de aire de motor está totalmente tapado.",
                        "3. Revise el tiempo de inyección (pulso en ms). Si la PCM lo está reduciendo al mínimo pero la mezcla sigue rica, hay inyectores mecánicamente pegados.",
                        "4. Pruebe los inyectores en un banco de pruebas o usando un osciloscopio para revisar la rampa de caída de voltaje."
                    )
                )
            )
        }
    }

    private fun checkMaf(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        val maf = liveData["0110"] ?: return
        val rpm = liveData["010C"] ?: 0f

        // Asumimos un motor general a ralentí (700-900 RPM)
        if (rpm in 600f..1000f) {
            if (maf < 1.0f && maf > 0f) {
                procedures.add(
                    ExpertDiagnosticProcedure(
                        title = "Flujo de Masa de Aire (MAF) Extremadamente Bajo",
                        description = "El sensor MAF reporta menos de 1 g/s de aire en ralentí. Esto es insuficiente e indica una falla del sensor o que el aire está ingresando por una fuga masiva sin pasar por el sensor.",
                        severity = DiagnosticSeverity.HIGH,
                        probableCauses = listOf(
                            "Fuga de vacío masiva (Tubo de admisión roto o desacoplado entre el MAF y la mariposa).",
                            "Sensor MAF dañado o severamente contaminado.",
                            "Problema de cableado (Tierra defectuosa o señal caída)."
                        ),
                        testSteps = listOf(
                            "1. Inspeccione visualmente el conducto de aire de admisión desde el filtro hasta el cuerpo de aceleración. Busque roturas o abrazaderas sueltas.",
                            "2. Desconecte el conector del MAF. Si el ralentí del motor mejora o se estabiliza de inmediato (la PCM usa valores predeterminados de mapa), el MAF es el culpable.",
                            "3. Mida el voltaje de alimentación (12V o 5V) y tierra en el conector del MAF."
                        )
                    )
                )
            } else if (maf > 10.0f) {
                procedures.add(
                    ExpertDiagnosticProcedure(
                        title = "Flujo de Masa de Aire (MAF) Elevado en Ralentí",
                        description = "El sensor reporta excesiva entrada de aire (>10 g/s) cuando el motor está en ralentí. Esto causa mezcla rica (LTFT negativo) o ralentí inestable.",
                        severity = DiagnosticSeverity.MODERATE,
                        probableCauses = listOf(
                            "Sensor MAF contaminado (aceite de filtro, tierra) o descalibrado.",
                            "Cuerpo de aceleración sucio o descalibrado, forzando la apertura de la mariposa para mantener las RPM.",
                            "Válvula IAC atascada abierta (en vehículos más antiguos)."
                        ),
                        testSteps = listOf(
                            "1. Revise el elemento filtrante de aire. Si instaló un filtro de alto flujo, el aceite del filtro pudo contaminar el MAF. Límpielo con spray limpia MAF.",
                            "2. Si la mariposa es electrónica (Drive-by-Wire), inspeccione y limpie el cuerpo de aceleración. Puede requerir un aprendizaje (Idle Relearn) con el escáner."
                        )
                    )
                )
            }
        }
    }

    private fun checkMap(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        val map = liveData["010B"] ?: return
        val rpm = liveData["010C"] ?: 0f

        if (rpm in 600f..1000f) {
            // El MAP en ralentí ideal es entre 25 kPa y 45 kPa. 
            // Si está muy cercano a la presión atmosférica (ej. > 65 kPa), hay poco vacío.
            if (map > 65f) {
                procedures.add(
                    ExpertDiagnosticProcedure(
                        title = "Presión Absoluta del Múltiple (MAP) Anormal (Bajo Vacío)",
                        description = "El motor presenta un vacío deficiente en ralentí. Un motor sano debe tener entre 18 y 22 inHg (aprox 25-45 kPa de presión absoluta).",
                        severity = DiagnosticSeverity.HIGH,
                        probableCauses = listOf(
                            "Fallo mecánico del motor (baja compresión, cadena/banda de tiempo saltada).",
                            "Fuga de vacío (múltiple, mangueras, servofreno).",
                            "Válvula EGR atascada abierta.",
                            "Escape restringido (Convertidor catalítico tapado)."
                        ),
                        testSteps = listOf(
                            "1. Conecte un vacuómetro análogo directamente al múltiple. Si la aguja fluctúa rápidamente, sospeche de válvulas con problemas de sello o guías.",
                            "2. Si el vacío es bajo y estable, acelere el motor a 2500 RPM. Si el vacío cae lentamente a cero, el escape o el catalizador están tapados.",
                            "3. Compruebe la sincronización de la correa/cadena de distribución si las pruebas de vacío sugieren un retraso en el tiempo de válvulas."
                        )
                    )
                )
            }
        }
    }

    private fun checkTps(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        val tps = liveData["0111"] ?: return
        val rpm = liveData["010C"] ?: 0f

        // Si el motor está en ralentí (pie fuera del acelerador)
        if (rpm in 600f..1000f) {
            if (tps > 20.0f) {
                procedures.add(
                    ExpertDiagnosticProcedure(
                        title = "Lectura del Sensor TPS Excesiva en Ralentí",
                        description = "El Sensor de Posición del Acelerador (TPS) reporta que la mariposa está más de un 20% abierta, aunque el motor está en RPM de ralentí.",
                        severity = DiagnosticSeverity.MODERATE,
                        probableCauses = listOf(
                            "Cable del acelerador atascado (en sistemas mecánicos).",
                            "Cuerpo de aceleración extremadamente sucio por carbón, forzando una gran apertura para ralentí.",
                            "Sensor TPS descalibrado o dañado."
                        ),
                        testSteps = listOf(
                            "1. Visualice el barrido del TPS: Con el motor apagado y encendido en 'ON', presione el pedal lentamente a fondo. El TPS debe subir de ~10% a ~90% de manera fluida, sin cortes (glitches).",
                            "2. Inspeccione físicamente el cuerpo de aceleración. Límpielo con solvente dieléctrico seguro para sensores.",
                            "3. Realice una recalibración (Idle Relearn) usando funciones especiales si el vehículo cuenta con cuerpo de aceleración electrónico (TAC)."
                        )
                    )
                )
            }
        }
    }

    private fun checkO2Sensors(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        // En una lectura en vivo snapshot, solo podemos buscar si el sensor está atascado en límites extremos.
        val o2B1S1 = liveData["0114"] ?: return

        // 0 a 1V es el rango de O2 de Zirconio.
        if (o2B1S1 < 0.05f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "Sensor O2 B1S1 (Pre-Catalizador) Atascado Pobre",
                    description = "El voltaje del sensor de oxígeno principal está clavado cerca de 0V, lo cual es anormal ya que debería ciclar entre 0.1V y 0.9V.",
                    severity = DiagnosticSeverity.HIGH,
                    probableCauses = listOf(
                        "Sensor de oxígeno defectuoso (elemento calefactor quemado o contaminado con silicón/refrigerante).",
                        "Cortocircuito a tierra en el cable de señal del sensor de oxígeno.",
                        "Condición extremadamente pobre real (bomba de gasolina muriendo, inyectores bloqueados)."
                    ),
                    testSteps = listOf(
                        "1. Induzca artificialmente una mezcla rica (ej. rociando arrancador / limpia carburador sutilmente en la admisión o desconectando el regulador de presión). Si el sensor reacciona subiendo a >0.8V, el sensor funciona y el motor tiene una condición real pobre.",
                        "2. Si el sensor no reacciona en absoluto, desconecte el sensor y mida el voltaje del lado del arnés de la PCM (Debe tener aprox 0.45V de voltaje de polarización). Si hay 0V, revise el cableado."
                    )
                )
            )
        } else if (o2B1S1 > 0.95f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "Sensor O2 B1S1 (Pre-Catalizador) Atascado Rico",
                    description = "El voltaje del sensor de oxígeno principal está clavado a casi 1V, reportando mezcla constantemente rica.",
                    severity = DiagnosticSeverity.HIGH,
                    probableCauses = listOf(
                        "Condición rica real extrema (Inyector goteando, regulador roto pasando gasolina por vacío).",
                        "Sensor de oxígeno contaminado (usualmente negro por hollín) y defectuoso.",
                        "Cortocircuito a voltaje en el cable de señal del sensor."
                    ),
                    testSteps = listOf(
                        "1. Induzca una fuga de vacío masiva intencionalmente (ej. desconectando manguera del servofreno). Si el sensor de O2 reacciona bajando a <0.2V, el sensor funciona bien.",
                        "2. Si reacciona bien, dedíquese a buscar por qué se inyecta demasiada gasolina (ver inyectores, presión de combustible y MAF).",
                        "3. Si no reacciona, reemplace el sensor de oxígeno."
                    )
                )
            )
        }
    }

    private fun checkIat(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        val iat = liveData["010F"] ?: return
        val ect = liveData["0105"]

        if (iat < -30f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "Temperatura de Aire de Admisión (IAT) Irracionalmente Baja",
                    description = "El sensor IAT está leyendo -30°C o menos. A menos que usted esté operando en el Ártico, esto indica una falla de circuito abierto.",
                    severity = DiagnosticSeverity.MODERATE,
                    probableCauses = listOf(
                        "Sensor IAT desconectado.",
                        "Cable roto (Circuito Abierto) en el arnés del IAT.",
                        "Resistencia interna del termistor rota."
                    ),
                    testSteps = listOf(
                        "1. Verifique que el conector del IAT (a menudo integrado en el MAF) esté firmemente insertado.",
                        "2. Con un multímetro, verifique que haya 5V de referencia en un cable y tierra en el otro lado del conector. Si falta la referencia de 5V, busque la falla en el arnés o la PCM.",
                        "3. Haga un puente corto en el conector del IAT con un clip. La lectura del escáner debe saltar inmediatamente a > 130°C. Si lo hace, cambie el sensor. Si no, hay falla de cableado hacia la PCM."
                    )
                )
            )
        } else if (iat > 80f && ect != null && ect < 100f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "Temperatura de Aire de Admisión (IAT) Excesivamente Alta",
                    description = "El sensor IAT está reportando un aire ingresante demasiado caliente (>80°C) sin correspondencia con el ambiente.",
                    severity = DiagnosticSeverity.MODERATE,
                    probableCauses = listOf(
                        "Filtro de aire de alto flujo absorbiendo aire directamente de los múltiples de escape (Instalación deficiente sin Heat Shield).",
                        "Circuito en cortocircuito a tierra en la línea de señal del IAT.",
                        "Sensor averiado."
                    ),
                    testSteps = listOf(
                        "1. Verifique el enrutamiento de la toma de aire (Intake).",
                        "2. Desconecte el sensor IAT. Si la lectura cae instantáneamente a -40°C, reemplace el sensor. Si se queda en un valor alto, hay cortocircuito a tierra en el arnés."
                    )
                )
            )
        }
    }

    private fun checkLoadAndTiming(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        val load = liveData["0104"] ?: return
        val timing = liveData["010E"] ?: return
        val rpm = liveData["010C"] ?: 0f

        if (rpm in 600f..1000f) {
            if (load > 45f) {
                procedures.add(
                    ExpertDiagnosticProcedure(
                        title = "Carga de Motor Alta en Ralentí",
                        description = "El parámetro Engine Load está por encima del 45% sin acelerar el motor. Normalmente en ralentí sin A/C debe ser entre 15% y 30%.",
                        severity = DiagnosticSeverity.MODERATE,
                        probableCauses = listOf(
                            "Compresor del A/C u otro accesorio mecánico encendido o agarrotado.",
                            "Transmisión automática enganchada o convertidor de par bloqueado.",
                            "Lectura del sensor MAF subestimada causando cálculo de carga erróneo."
                        ),
                        testSteps = listOf(
                            "1. Apague por completo el Aire Acondicionado y cualquier carga eléctrica pesada (luces, desempañador).",
                            "2. Verifique si alguna de las poleas accesorias genera ruido anormal (dirección hidráulica, alternador, compresor).",
                            "3. Limpie el sensor MAF."
                        )
                    )
                )
            }

            if (timing < 0f) {
                procedures.add(
                    ExpertDiagnosticProcedure(
                        title = "Avance de Chispa Retardado",
                        description = "El encendido (Spark Advance) muestra un valor negativo en ralentí, indicando que la chispa ocurre después del Punto Muerto Superior (ATDC).",
                        severity = DiagnosticSeverity.HIGH,
                        probableCauses = listOf(
                            "Intervención del sensor de detonación (Knock Sensor) debido a ruidos internos o pistoneo (mala gasolina).",
                            "Base de tiempo (distribuidor) mal ajustada, si aplica.",
                            "La PCM trata agresivamente de bajar el ralentí compensando una mariposa sucia o una fuga de vacío leve."
                        ),
                        testSteps = listOf(
                            "1. Verifique la calidad de la gasolina y escuche el motor en busca de ruidos metálicos internos (cascabeleo o válvulas).",
                            "2. Observe el PID de los retardos por detonación (Knock Retard) si está disponible.",
                            "3. Si el vehículo tiene cuerpo de aceleración electrónico, limpie la mariposa para prevenir que la PCM use el tiempo de encendido para gobernar un ralentí inestable."
                        )
                    )
                )
            }
        }
    }

    private fun checkWidebandO2(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        // Wideband Equivalence Ratio (Lambda) PIDs: 0124 (B1S1), 0125 (B1S2), etc.
        // Formula: Lambda = ( (A*256)+B ) / 32768
        // Stoichiometric is 1.0. < 1.0 is Rich, > 1.0 is Lean.
        
        val lambdaB1S1 = liveData["0124"] ?: liveData["0134"] ?: return
        
        if (lambdaB1S1 < 0.85f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "Sensor Wideband (AFR) Detecta Mezcla Muy Rica",
                    description = "El sensor de banda ancha reporta un valor Lambda de $lambdaB1S1, indicando un exceso severo de combustible.",
                    severity = DiagnosticSeverity.HIGH,
                    probableCauses = listOf(
                        "Inyectores con fuga o goteo.",
                        "Regulador de presión de combustible fallido.",
                        "Sensor MAF contaminado subiendo el cálculo de carga.",
                        "Válvula de purga (EVAP) atascada abierta pasando vapores de gasolina."
                    ),
                    testSteps = listOf(
                        "1. Verifique el 'Fuel System Status'. Si está en 'Closed Loop' y Lambda sigue bajo, la PCM no está logrando compensar.",
                        "2. Realice una prueba de presión de combustible.",
                        "3. Revise si hay presencia de combustible en la manguera de vacío del regulador de presión."
                    )
                )
            )
        } else if (lambdaB1S1 > 1.15f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "Sensor Wideband (AFR) Detecta Mezcla Muy Pobre",
                    description = "El sensor de banda ancha reporta un valor Lambda de $lambdaB1S1, indicando una falta crítica de combustible o exceso de aire.",
                    severity = DiagnosticSeverity.HIGH,
                    probableCauses = listOf(
                        "Fuga de vacío significativa (admisión, PCV).",
                        "Filtro de combustible obstruido o bomba de gasolina débil.",
                        "Entrada de aire falso después del MAF.",
                        "Fuga en el múltiple de escape antes del sensor de O2 (introduce aire fresco al sensor)."
                    ),
                    testSteps = listOf(
                        "1. Busque fugas de aire en el sistema de admisión usando una máquina de humo.",
                        "2. Verifique si hay grietas en el múltiple de escape o empaques quemados que puedan engañar al sensor.",
                        "3. Pruebe la entrega de la bomba de gasolina (flujo y presión)."
                    )
                )
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BANCO 2 — Análisis de Sensores O2 (V6/V8)
    // ═══════════════════════════════════════════════════════════════
    private fun checkO2Bank2(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        val o2B2S1 = liveData["0116"] ?: liveData["0118"] ?: return

        if (o2B2S1 < 0.05f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "Sensor O2 B2S1 (Banco 2 Pre-Cat) Atascado Pobre",
                    description = "El sensor de oxígeno del Banco 2 (cilindros traseros en V6/V8) está clavado cerca de 0V. Debe oscilar entre 0.1V y 0.9V.",
                    severity = DiagnosticSeverity.HIGH,
                    probableCauses = listOf(
                        "Sensor O2 del Banco 2 defectuoso o contaminado.",
                        "Fuga de vacío en el múltiple de admisión del lado del Banco 2.",
                        "Inyectores del Banco 2 obstruidos causando mezcla pobre real.",
                        "Fuga en el colector de escape del Banco 2 introduciendo aire falso."
                    ),
                    testSteps = listOf(
                        "1. Compare Fuel Trims del Banco 2 (STFT2/LTFT2) con Banco 1. Si solo Banco 2 está positivo (+10% o más), el problema está aislado en ese banco.",
                        "2. Induzca mezcla rica rociando arrancador en la admisión del Banco 2. Si el O2 reacciona, el sensor funciona y hay condición pobre real.",
                        "3. Revise cableado del sensor O2 B2S1 — suele pasar por zona caliente del escape y derretirse."
                    )
                )
            )
        } else if (o2B2S1 > 0.95f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "Sensor O2 B2S1 (Banco 2 Pre-Cat) Atascado Rico",
                    description = "El sensor O2 del Banco 2 reporta mezcla constantemente rica (>0.95V). Debe oscilar normalmente.",
                    severity = DiagnosticSeverity.HIGH,
                    probableCauses = listOf(
                        "Inyector del Banco 2 goteando o atascado abierto.",
                        "Sensor O2 B2S1 contaminado con hollín o silicón.",
                        "Regulador de presión de combustible pasando gasolina excesiva."
                    ),
                    testSteps = listOf(
                        "1. Compare Fuel Trims: Si STFT2/LTFT2 son muy negativos (<-10%) y STFT1/LTFT1 son normales, el problema está en Banco 2.",
                        "2. Inspeccione bujías del Banco 2 — una bujía negra y húmeda indica inyector goteando en ese cilindro.",
                        "3. Desconecte sensor O2 B2S1 y verifique voltaje de bias (0.45V) en el arnés de la PCM."
                    )
                )
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // EFICIENCIA CATALÍTICA EN TIEMPO REAL
    // Compara O2 pre-cat vs post-cat para detectar catalizador muriendo
    // ═══════════════════════════════════════════════════════════════
    private fun checkCatalyticEfficiency(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        val o2PreCat = liveData["0114"] ?: return   // B1S1 (pre-catalizador)
        val o2PostCat = liveData["0115"] ?: return   // B1S2 (post-catalizador)
        val rpm = liveData["010C"] ?: 0f

        // Solo analizar con motor en marcha estable
        if (rpm < 600f) return

        // Un catalizador SANO: el post-cat debe estar relativamente estable (~0.4-0.7V)
        // Un catalizador MUERTO: el post-cat copia la onda del pre-cat
        // En un snapshot instantáneo, si ambos están en el mismo extremo, es sospechoso
        val delta = kotlin.math.abs(o2PreCat - o2PostCat)

        // Si el post-cat está en los extremos (muy alto o muy bajo) como el pre-cat
        if (o2PostCat < 0.1f && o2PreCat < 0.15f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "⚠️ Eficiencia Catalítica Banco 1 — SOSPECHOSA",
                    description = "El sensor O2 post-catalizador (B1S2) está en voltaje bajo (${"%,.2f".format(o2PostCat)}V) similar al pre-cat (${"%,.2f".format(o2PreCat)}V). Un catalizador sano debería mantener el post-cat estable alrededor de 0.5-0.7V.",
                    severity = DiagnosticSeverity.MODERATE,
                    probableCauses = listOf(
                        "Catalizador perdiendo capacidad de almacenamiento de oxígeno.",
                        "Catalizador contaminado por aceite (sellos de válvula) o refrigerante (empaque cabeza).",
                        "Sensor O2 B1S2 defectuoso dando lectura falsa."
                    ),
                    testSteps = listOf(
                        "1. PRUEBA DEFINITIVA: Grafique B1S1 y B1S2 simultáneamente a 2500 RPM estables por 2 minutos. El B1S2 debe ser una línea casi plana (~0.6V). Si copia la onda del B1S1, catalizador agotado.",
                        "2. Revise si hay códigos P0420 o P0430 pendientes.",
                        "3. Verifique que no haya fugas de escape entre el catalizador y el sensor B1S2."
                    )
                )
            )
        } else if (o2PostCat > 0.85f && o2PreCat > 0.85f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "⚠️ Eficiencia Catalítica Banco 1 — CRÍTICA",
                    description = "Ambos sensores O2 (pre y post catalizador) están en voltaje alto (rico). El catalizador no está convirtiendo gases. Eficiencia estimada: BAJA.",
                    severity = DiagnosticSeverity.HIGH,
                    probableCauses = listOf(
                        "Catalizador derretido internamente o envenenado.",
                        "Exceso de combustible no quemado destruyendo el catalizador (verifique si hay misfire activo).",
                        "El catalizador puede estar físicamente obstruido — causa pérdida severa de potencia."
                    ),
                    testSteps = listOf(
                        "1. URGENTE: Verifique si hay códigos de misfire (P0300-P0308). Fallos de encendido destruyen catalizadores.",
                        "2. Mida la contrapresión del escape antes del catalizador. Si excede 3 PSI en ralentí, está tapado.",
                        "3. Use termómetro infrarrojo: Mida temperatura ANTES y DESPUÉS del catalizador. Un cat funcional debe estar 50-100°F más caliente a la salida."
                    )
                )
            )
        }

        // También verificar Banco 2 si está disponible
        val o2PreCatB2 = liveData["0116"] ?: liveData["0118"]
        val o2PostCatB2 = liveData["0117"] ?: liveData["0119"]
        if (o2PreCatB2 != null && o2PostCatB2 != null && rpm > 600f) {
            if ((o2PostCatB2 > 0.85f && o2PreCatB2 > 0.85f) || (o2PostCatB2 < 0.1f && o2PreCatB2 < 0.15f)) {
                procedures.add(
                    ExpertDiagnosticProcedure(
                        title = "⚠️ Eficiencia Catalítica Banco 2 — SOSPECHOSA",
                        description = "El catalizador del Banco 2 muestra signos de degradación. Los sensores O2 pre/post-cat del Banco 2 están en el mismo rango de voltaje.",
                        severity = DiagnosticSeverity.MODERATE,
                        probableCauses = listOf(
                            "Catalizador del Banco 2 degradado.",
                            "Problema de mezcla aislado en Banco 2 dañando el catalizador.",
                            "Sensor O2 B2S2 defectuoso."
                        ),
                        testSteps = listOf(
                            "1. Grafique B2S1 y B2S2 simultáneamente. Si B2S2 copia la onda, catalizador Banco 2 agotado.",
                            "2. Verifique código P0430 (Eficiencia Catalizador Banco 2).",
                            "3. Compare Fuel Trims del Banco 2 con Banco 1 para aislar problemas de mezcla."
                        )
                    )
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PRUEBA DE BATERÍA / ALTERNADOR
    // ═══════════════════════════════════════════════════════════════
    private fun checkBatteryAlternator(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        val voltage = liveData["0142"] ?: return
        val rpm = liveData["010C"] ?: 0f
        val engineRunning = rpm > 400f

        if (!engineRunning) {
            // Motor apagado — evaluar batería en reposo
            if (voltage < 12.0f) {
                procedures.add(
                    ExpertDiagnosticProcedure(
                        title = "🔋 Batería Descargada o Defectuosa",
                        description = "Voltaje en reposo: ${"%,.1f".format(voltage)}V. Una batería sana debe estar entre 12.4V y 12.8V con motor apagado.",
                        severity = DiagnosticSeverity.HIGH,
                        probableCauses = listOf(
                            "Batería con celda muerta (voltaje <12.0V = batería al 0-25% de carga).",
                            "Consumo parásito excesivo descargando la batería (módulo que no duerme).",
                            "Batería con vida útil agotada (típicamente 3-5 años)."
                        ),
                        testSteps = listOf(
                            "1. Cargue la batería completamente y realice prueba de carga (Load Test) con tester de batería.",
                            "2. Si la batería no mantiene carga, mida consumo parásito: desconecte cable negativo, conecte amperímetro en serie. Debe ser <50mA después de 30 minutos.",
                            "3. Si el consumo es alto, retire fusibles uno por uno para identificar el circuito con fuga de corriente."
                        )
                    )
                )
            } else if (voltage < 12.4f) {
                procedures.add(
                    ExpertDiagnosticProcedure(
                        title = "🔋 Batería con Carga Baja",
                        description = "Voltaje en reposo: ${"%,.1f".format(voltage)}V. Esto indica batería entre 50-75% de carga. Debería ser ≥12.6V.",
                        severity = DiagnosticSeverity.MODERATE,
                        probableCauses = listOf(
                            "Batería no recargada completamente (viajes cortos frecuentes).",
                            "Alternador con carga marginal que no completa la carga.",
                            "Inicio de degradación de la batería."
                        ),
                        testSteps = listOf(
                            "1. Conduzca 30+ minutos en carretera y re-mida. Si sube a 12.6V+, la batería está bien pero no se cargaba.",
                            "2. Si persiste bajo, realice prueba de CCA (Cold Cranking Amps) con tester de batería."
                        )
                    )
                )
            }
        } else {
            // Motor en marcha — evaluar alternador
            if (voltage > 15.0f) {
                procedures.add(
                    ExpertDiagnosticProcedure(
                        title = "⚡ Sobrecarga del Alternador",
                        description = "Voltaje de carga: ${"%,.1f".format(voltage)}V. ¡PELIGRO! El regulador de voltaje del alternador está fallando. Voltaje mayor a 15V puede dañar la batería, módulos electrónicos y bombillas.",
                        severity = DiagnosticSeverity.HIGH,
                        probableCauses = listOf(
                            "Regulador de voltaje del alternador defectuoso (falla en circuito abierto = máximo voltaje).",
                            "Mala conexión de masa del alternador causando sobre-compensación.",
                            "Sensor de temperatura de batería defectuoso (en sistemas de carga inteligente)."
                        ),
                        testSteps = listOf(
                            "1. ¡PRECAUCIÓN! Voltaje >15.5V puede hacer hervir la batería. Apague accesorios y diríjase al taller.",
                            "2. Revise el regulador de voltaje (en algunos alternadores es reemplazable por separado).",
                            "3. Verifique la masa del alternador y el cable de excitación (campo)."
                        )
                    )
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FUEL TRIMS BANCO 2 — Con comparación cruzada B1 vs B2
    // ═══════════════════════════════════════════════════════════════
    private fun checkFuelTrimsBank2(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        val stft2 = liveData["0108"]
        val ltft2 = liveData["0109"]
        if (stft2 == null && ltft2 == null) return

        val totalTrim2 = (stft2 ?: 0f) + (ltft2 ?: 0f)
        val stft1 = liveData["0106"]
        val ltft1 = liveData["0107"]
        val totalTrim1 = (stft1 ?: 0f) + (ltft1 ?: 0f)

        // Comparación cruzada: si un banco está bien y el otro no, el problema está aislado
        val bankDelta = kotlin.math.abs(totalTrim1 - totalTrim2)

        if (totalTrim2 > 15f) {
            val isolated = totalTrim1 < 10f
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "Mezcla Pobre Banco 2" + if (isolated) " (Aislada)" else "",
                    description = "El Banco 2 tiene Fuel Trim combinado de +${"%,.0f".format(totalTrim2)}%." +
                        if (isolated) " El Banco 1 está normal (${"%,.0f".format(totalTrim1)}%), lo que confirma un problema AISLADO en el Banco 2." else "",
                    severity = DiagnosticSeverity.HIGH,
                    probableCauses = if (isolated) listOf(
                        "Fuga de vacío en el múltiple de admisión del lado del Banco 2.",
                        "Inyectores del Banco 2 obstruidos o con flujo reducido.",
                        "Fuga en el colector de escape del Banco 2 (engaña al O2 sensor).",
                        "Empaques de admisión del lado Banco 2 dañados."
                    ) else listOf(
                        "Problema de combustible global (bomba débil, filtro tapado).",
                        "Sensor MAF sub-reportando aire.",
                        "Fuga de vacío central (PCV, servofreno)."
                    ),
                    testSteps = listOf(
                        "1. PRUEBA CLAVE: Compare STFT1 vs STFT2 a 2500 RPM. Diferencia >10% = problema aislado en un banco.",
                        "2. Si es aislado en B2: Use máquina de humo enfocada en el múltiple del lado B2.",
                        "3. Realice prueba de balance de inyectores del Banco 2 con pruebas de actuadores."
                    )
                )
            )
        } else if (totalTrim2 < -15f) {
            val isolated = totalTrim1 > -10f
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "Mezcla Rica Banco 2" + if (isolated) " (Aislada)" else "",
                    description = "El Banco 2 tiene Fuel Trim combinado de ${"%,.0f".format(totalTrim2)}%." +
                        if (isolated) " El Banco 1 está normal, confirmando problema aislado en Banco 2." else "",
                    severity = DiagnosticSeverity.HIGH,
                    probableCauses = if (isolated) listOf(
                        "Inyector del Banco 2 goteando o atascado abierto.",
                        "Sensor O2 B2S1 envejecido reportando falso pobre.",
                        "Fuga de aceite entrando por sellos de válvula del Banco 2."
                    ) else listOf(
                        "Presión de combustible excesiva (regulador).",
                        "Válvula de purga EVAP atascada abierta.",
                        "Sensor MAF sobre-reportando."
                    ),
                    testSteps = listOf(
                        "1. Inspeccione bujías del Banco 2 — bujías negras/húmedas = confirmación de mezcla rica.",
                        "2. Realice prueba de caída de tensión en inyectores del B2 con osciloscopio.",
                        "3. Verifique si el sensor O2 B2S1 reacciona correctamente (debe oscilar 0.1-0.9V en lazo cerrado)."
                    )
                )
            )
        }

        // Alerta de desbalance entre bancos (incluso si ambos están en rango)
        if (bankDelta > 8f && totalTrim1 in -15f..15f && totalTrim2 in -15f..15f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "Desbalance de Fuel Trims Entre Bancos",
                    description = "Diferencia de ${"%,.1f".format(bankDelta)}% entre Banco 1 (${"%,.0f".format(totalTrim1)}%) y Banco 2 (${"%,.0f".format(totalTrim2)}%). Aunque ambos están en rango, un desbalance >8% indica un problema incipiente.",
                    severity = DiagnosticSeverity.MODERATE,
                    probableCauses = listOf(
                        "Fuga de vacío pequeña en un lado del múltiple de admisión.",
                        "Diferencia de flujo entre inyectores de un banco vs otro (suciedad acumulada).",
                        "Sensor O2 de un banco envejecido respondiendo más lento."
                    ),
                    testSteps = listOf(
                        "1. Este desbalance puede ser el primer signo de un problema futuro. Monitoree en las próximas sesiones.",
                        "2. Si persiste, limpie inyectores de ambos bancos con aditivo profesional o ultrasonido.",
                        "3. Compare la respuesta (cross-counts) de ambos sensores O2 pre-catalizador."
                    )
                )
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ANÁLISIS DE MISFIRE — Detección por inestabilidad de RPM
    // ═══════════════════════════════════════════════════════════════
    private fun checkMisfireAnalysis(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        val rpm = liveData["010C"] ?: return
        val load = liveData["0104"] ?: return
        val stft = liveData["0106"] ?: 0f
        val timing = liveData["010E"]

        // Solo en ralentí donde el misfire es más detectable
        if (rpm !in 500f..1100f) return

        // Un ralentí extremadamente bajo con carga alta sugiere misfire
        if (rpm < 600f && load > 30f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "🔥 Posible Falla de Encendido (Misfire) Detectada",
                    description = "RPM inusualmente bajo (${"%,.0f".format(rpm)}) con carga alta (${"%,.0f".format(load)}%) en ralentí. Patrón consistente con falla de encendido en uno o más cilindros.",
                    severity = DiagnosticSeverity.HIGH,
                    probableCauses = listOf(
                        "Bujía desgastada o con gap incorrecto.",
                        "Bobina de encendido con fuga de aislamiento (más común en húmedo/frío).",
                        "Cable de bujía roto o con alta resistencia (si aplica).",
                        "Inyector obstruido o con falla eléctrica.",
                        "Baja compresión en uno o más cilindros."
                    ),
                    testSteps = listOf(
                        "1. PRUEBA RÁPIDA: Desconecte las bobinas una por una en ralentí. Si desconectar una bobina NO cambia el ralentí, ese cilindro ya estaba fallando.",
                        "2. Revise los contadores de misfire en Mode 06 (Monitores de Falla de Encendido) para identificar el cilindro exacto.",
                        "3. Inspeccione bujías — electrodo blanco = mezcla pobre, negro húmedo = aceite entrando, negro seco = mezcla rica.",
                        "4. Mida resistencia de cables de bujía: debe ser <15kΩ/pie. Mayor resistencia = reemplace.",
                        "5. Si las bujías y bobinas están bien, realice prueba de compresión cilindro por cilindro."
                    )
                )
            )
        }

        // Timing muy retardado con carga alta = compensación por knock o misfire
        if (timing != null && timing < -5f && load > 25f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "Retardo de Encendido Severo — Knock o Misfire",
                    description = "Avance de encendido en ${"%,.1f".format(timing)}° (ATDC) con carga ${"%,.0f".format(load)}%. La PCM está retardando agresivamente para proteger el motor.",
                    severity = DiagnosticSeverity.HIGH,
                    probableCauses = listOf(
                        "Detonación (knock) por gasolina de bajo octanaje.",
                        "Depósitos de carbón causando puntos calientes (pre-ignición).",
                        "Sensor de detonación (Knock Sensor) defectuoso enviando señal falsa.",
                        "EGR excesiva diluyendo la mezcla."
                    ),
                    testSteps = listOf(
                        "1. Pruebe con gasolina premium (alto octanaje). Si el timing se normaliza, era combustible.",
                        "2. Verifique el PID de Knock Retard si está disponible.",
                        "3. En motores con inyección directa (GDI), realice limpieza de válvulas de admisión."
                    )
                )
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SISTEMA TÉRMICO AVANZADO — Coolant vs Oil vs IAT cruzado
    // ═══════════════════════════════════════════════════════════════
    private fun checkThermalSystem(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        val coolant = liveData["0105"] ?: return
        val iat = liveData["010F"]
        val oilTemp = liveData["015C"]  // PID Mode 01, PID 5C = Oil Temperature
        val runTime = liveData["011F"] ?: 0f  // Seconds since engine start

        // Análisis cruzado Coolant vs Oil (si disponible)
        if (oilTemp != null && coolant > 80f) {
            val tempDelta = oilTemp - coolant
            if (tempDelta > 30f) {
                procedures.add(
                    ExpertDiagnosticProcedure(
                        title = "🌡️ Aceite Significativamente Más Caliente que Refrigerante",
                        description = "Temperatura de aceite (${"%,.0f".format(oilTemp)}°C) supera al refrigerante (${"%,.0f".format(coolant)}°C) por ${"%,.0f".format(tempDelta)}°C. Diferencia normal es 10-20°C.",
                        severity = DiagnosticSeverity.MODERATE,
                        probableCauses = listOf(
                            "Enfriador de aceite obstruido o deficiente.",
                            "Nivel de aceite bajo causando sobrecalentamiento por fricción.",
                            "Aceite degradado (viscosidad fuera de especificación).",
                            "Conducción agresiva prolongada sin enfriamiento adecuado."
                        ),
                        testSteps = listOf(
                            "1. Verifique nivel y condición del aceite inmediatamente. Aceite oscuro/quemado = reemplace.",
                            "2. Inspeccione el enfriador de aceite (oil cooler) si el vehículo lo tiene.",
                            "3. Confirme que la viscosidad del aceite sea la especificada por el fabricante."
                        )
                    )
                )
            }
        }

        // Motor caliente pero IAT demasiado cercano al coolant (heat soak)
        if (iat != null && coolant > 90f) {
            if (iat > 65f && (coolant - iat) < 20f) {
                procedures.add(
                    ExpertDiagnosticProcedure(
                        title = "🌡️ Heat Soak — Aire de Admisión Excesivamente Caliente",
                        description = "IAT (${"%,.0f".format(iat)}°C) está demasiado cercano al refrigerante (${"%,.0f".format(coolant)}°C). El motor está aspirando aire caliente, reduciendo potencia y eficiencia.",
                        severity = DiagnosticSeverity.MODERATE,
                        probableCauses = listOf(
                            "Toma de aire aspirando del compartimiento motor en vez de aire fresco externo.",
                            "Deflector de calor del colector de escape roto o faltante.",
                            "Ventiladores del radiador creando flujo inverso de aire caliente.",
                            "Estacionamiento prolongado en ralentí con motor caliente."
                        ),
                        testSteps = listOf(
                            "1. Inspeccione el recorrido del ducto de admisión — debe tomar aire de la zona frontal/fría.",
                            "2. Verifique que los deflectores de calor estén instalados correctamente.",
                            "3. Conduzca a velocidad de carretera por 5 min y re-evalúe — si IAT baja, era heat soak por ralentí."
                        )
                    )
                )
            }
        }

        // Calentamiento anormalmente rápido (posible pérdida de refrigerante)
        if (runTime < 120f && coolant > 100f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "⚠️ Calentamiento Anormalmente Rápido",
                    description = "El motor alcanzó ${"%,.0f".format(coolant)}°C en menos de 2 minutos (${"%,.0f".format(runTime)} seg). Esto es anormal y sugiere bajo nivel de refrigerante o termostato defectuoso.",
                    severity = DiagnosticSeverity.HIGH,
                    probableCauses = listOf(
                        "Nivel de refrigerante críticamente bajo — el sensor lee aire/vapor en vez de líquido.",
                        "Bolsa de aire en el sistema de enfriamiento.",
                        "Empaque de cabeza con fuga interna (gases de combustión calientan el refrigerante).",
                        "Sensor ECT defectuoso."
                    ),
                    testSteps = listOf(
                        "1. URGENTE: Apague el motor y verifique el nivel de refrigerante en el depósito de expansión.",
                        "2. Si hay burbujas en el depósito con motor caliente = posible empaque de cabeza.",
                        "3. Realice prueba de bloques (Block Test / Kit de CO2) para confirmar gases de combustión en el refrigerante.",
                        "4. Compare lectura del sensor ECT con termómetro infrarrojo para descartar sensor defectuoso."
                    )
                )
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PRESIÓN DE COMBUSTIBLE — Análisis de la línea de suministro
    // ═══════════════════════════════════════════════════════════════
    private fun checkFuelPressure(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        val fuelPressure = liveData["010A"] ?: return  // kPa (PID 0A = gauge pressure * 3)
        val rpm = liveData["010C"] ?: 0f
        val load = liveData["0104"] ?: 0f

        // Presión críticamente baja (normal es ~250-450 kPa / 35-65 PSI)
        if (fuelPressure < 150f && rpm > 500f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "⛽ Presión de Combustible Baja (${"%,.0f".format(fuelPressure)} kPa)",
                    description = "La presión del riel de inyectores está por debajo del mínimo operativo. Esto causa mezcla pobre, falta de potencia, y dificultad para arrancar en caliente.",
                    severity = DiagnosticSeverity.HIGH,
                    probableCauses = listOf(
                        "Bomba de combustible débil o con desgaste eléctrico.",
                        "Filtro de combustible obstruido (especialmente si tiene >60,000 km).",
                        "Regulador de presión de combustible con fuga de diafragma.",
                        "Línea de combustible con restricción o aplastamiento.",
                        "Fuga en el riel de inyectores o o-rings de inyectores."
                    ),
                    testSteps = listOf(
                        "1. Conecte un manómetro mecánico al riel y compare con este PID. Si difieren >15%, el sensor está malo.",
                        "2. Prueba de volumen: la bomba debe entregar >750 ml en 30 segundos.",
                        "3. Prueba de retención: presión no debe caer más de 30 kPa en 10 min con motor apagado.",
                        "4. Si la presión sube al pinzar el retorno, el regulador está mal. Si no sube, es la bomba."
                    )
                )
            )
        }

        // Presión excesivamente alta (regulador atascado cerrado)
        if (fuelPressure > 500f && rpm > 500f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "⛽ Presión de Combustible Excesiva (${"%,.0f".format(fuelPressure)} kPa)",
                    description = "La presión supera el rango operativo normal. Causa mezcla rica, bujías encharcadas, y potencial daño al catalizador.",
                    severity = DiagnosticSeverity.HIGH,
                    probableCauses = listOf(
                        "Regulador de presión atascado cerrado.",
                        "Línea de retorno de combustible obstruida o aplastada.",
                        "Regulador de presión sin vacío conectado (manguera rota)."
                    ),
                    testSteps = listOf(
                        "1. Verifique que la manguera de vacío del regulador esté conectada y sin fugas.",
                        "2. Desconecte la línea de retorno — si sale combustible a chorro, el retorno está libre.",
                        "3. Si la presión no baja al desconectar el vacío del regulador, reemplace el regulador."
                    )
                )
            )
        }

        // Caída de presión bajo carga (bomba que no aguanta demanda)
        if (fuelPressure < 200f && load > 60f && rpm > 2000f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "⛽ Caída de Presión Bajo Carga Alta",
                    description = "Presión cayó a ${"%,.0f".format(fuelPressure)} kPa con ${"%,.0f".format(load)}% de carga a ${"%,.0f".format(rpm)} RPM. La bomba no sostiene la demanda a altas RPM.",
                    severity = DiagnosticSeverity.CRITICAL,
                    probableCauses = listOf(
                        "Bomba de combustible con desgaste — mantiene presión en ralentí pero falla bajo demanda.",
                        "Filtro de combustible parcialmente obstruido — restricción aumenta con flujo.",
                        "Voltaje insuficiente al circuito de la bomba (conexiones corroídas, relé débil)."
                    ),
                    testSteps = listOf(
                        "1. PRUEBA DEFINITIVA: Mida presión con manómetro mientras acelera a fondo (WOT). Debe mantenerse ±10% del valor en ralentí.",
                        "2. Mida voltaje en el conector de la bomba durante WOT — debe ser >11.5V.",
                        "3. Revise el relé de la bomba y la masa del circuito."
                    )
                )
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FUEL SYSTEM STATUS — Lazo abierto vs cerrado
    // ═══════════════════════════════════════════════════════════════
    private fun checkFuelSystemStatus(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        val fuelStatus = liveData["0103"] ?: return
        val coolant = liveData["0105"] ?: 0f
        val runTime = liveData["011F"] ?: 0f

        // Status 1 = Open Loop, 2 = Closed Loop, 4 = Open Loop (fault), 8 = Closed Loop (O2 fault)
        // Si el motor está caliente (>75°C) y lleva >120 seg pero sigue en lazo abierto = problema
        if (fuelStatus == 1f && coolant > 75f && runTime > 120f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "🔄 Motor Atascado en Lazo Abierto (Open Loop)",
                    description = "El motor lleva ${"%,.0f".format(runTime)} seg a ${"%,.0f".format(coolant)}°C pero la PCM NO ha entrado en Lazo Cerrado. El motor está usando mapa de combustible fijo en vez de retroalimentación O2.",
                    severity = DiagnosticSeverity.HIGH,
                    probableCauses = listOf(
                        "Sensor O2 pre-catalizador defectuoso o desconectado.",
                        "Calentador del sensor O2 quemado (no alcanza temperatura operativa).",
                        "Sensor ECT reportando temperatura incorrecta (PCM cree que motor está frío).",
                        "Fuga de vacío masiva impidiendo que la PCM estabilice la mezcla."
                    ),
                    testSteps = listOf(
                        "1. Verifique que el sensor O2 B1S1 esté oscilando (0.1-0.9V). Si está fijo, está muerto.",
                        "2. Mida resistencia del calentador O2 — debe ser 5-15Ω. Infinito = abierto, reemplace.",
                        "3. Compare temperatura real del motor (termómetro infrarrojo) vs lectura ECT.",
                        "4. Consecuencia: consumo de combustible +20-40% mientras permanezca en Open Loop."
                    )
                )
            )
        }

        // Status 4 = Open Loop por falla detectada
        if (fuelStatus == 4f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "⚠️ Lazo Abierto por Falla Detectada",
                    description = "La PCM entró en modo de emergencia Open Loop porque detectó una falla en el sistema de retroalimentación de combustible. El motor opera con tablas fijas.",
                    severity = DiagnosticSeverity.CRITICAL,
                    probableCauses = listOf(
                        "Código DTC activo en el sistema de combustible (P0130-P0175 típicos).",
                        "Sensor O2 con cortocircuito o señal irracional.",
                        "Cableado del sensor O2 dañado."
                    ),
                    testSteps = listOf(
                        "1. Lea DTCs primero — habrá un código que causó esta condición.",
                        "2. Este modo aumenta emisiones y consumo significativamente.",
                        "3. Repare el DTC asociado para que la PCM vuelva a Closed Loop."
                    )
                )
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TRANSMISIÓN — Temperatura y salud
    // ═══════════════════════════════════════════════════════════════
    private fun checkTransmissionHealth(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        val transTemp = liveData["015E"] ?: return  // PID 5E = Transmission Fluid Temperature
        val coolant = liveData["0105"] ?: 0f

        // Temperatura de transmisión peligrosamente alta
        if (transTemp > 120f) {
            val severity = if (transTemp > 140f) DiagnosticSeverity.CRITICAL else DiagnosticSeverity.HIGH
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "🔥 Transmisión Sobrecalentada (${"%,.0f".format(transTemp)}°C)",
                    description = "La temperatura del fluido de transmisión supera el límite seguro. A >140°C, los sellos y empaques se degradan exponencialmente. Cada 10°C extra reduce la vida del fluido a la mitad.",
                    severity = severity,
                    probableCauses = listOf(
                        "Nivel de fluido de transmisión bajo.",
                        "Enfriador de transmisión obstruido o líneas de enfriamiento tapadas.",
                        "Convertidor de torque con deslizamiento excesivo.",
                        "Conducción con remolque o en pendiente prolongada sin enfriamiento adecuado.",
                        "Fluido de transmisión degradado/quemado (cambio de color a marrón oscuro)."
                    ),
                    testSteps = listOf(
                        "1. INMEDIATO: Reduzca la carga del motor. Si es posible, deténgase y deje enfriar en Park con motor encendido.",
                        "2. Verifique nivel y condición del fluido — debe ser rojo/rosado y sin olor a quemado.",
                        "3. Inspeccione las líneas del enfriador de transmisión (van al radiador) por obstrucción.",
                        "4. Si el fluido está oscuro/quemado, cámbielo con filtro. NO haga flush a presión en transmisiones con >100k km."
                    )
                )
            )
        }

        // Transmisión fría cuando el motor ya está caliente (enfriador con bypass abierto)
        if (transTemp < 40f && coolant > 85f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "❄️ Transmisión Anormalmente Fría",
                    description = "Fluido de transmisión a ${"%,.0f".format(transTemp)}°C mientras el motor está a ${"%,.0f".format(coolant)}°C. La transmisión no está alcanzando temperatura operativa.",
                    severity = DiagnosticSeverity.MODERATE,
                    probableCauses = listOf(
                        "Termostato de transmisión atascado abierto.",
                        "Sensor de temperatura de transmisión defectuoso.",
                        "Enfriador de transmisión con bypass permanente."
                    ),
                    testSteps = listOf(
                        "1. Compare lectura del sensor con termómetro infrarrojo en el cárter de la transmisión.",
                        "2. Si coinciden, la transmisión realmente está fría — verifique el termostato.",
                        "3. Consecuencia: cambios bruscos, menor eficiencia de combustible, desgaste acelerado."
                    )
                )
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DESLIZAMIENTO DE TRANSMISIÓN — Speed vs RPM
    // ═══════════════════════════════════════════════════════════════
    private fun checkSpeedVsRpmSlippage(liveData: Map<String, Float>, procedures: MutableList<ExpertDiagnosticProcedure>) {
        val speed = liveData["010D"] ?: return  // km/h
        val rpm = liveData["010C"] ?: return

        // Solo analizar en movimiento estable (>30 km/h)
        if (speed < 30f || rpm < 800f) return

        // Ratio RPM/Speed — en marcha directa (3ra/4ta) debe ser ~30-45 RPM por km/h
        val ratio = rpm / speed

        // RPM excesivamente alto para la velocidad = deslizamiento
        if (ratio > 65f && speed > 50f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "⚙️ Posible Deslizamiento de Transmisión",
                    description = "Ratio RPM/Velocidad anormal (${"%,.0f".format(ratio)} RPM por km/h). A ${"%,.0f".format(speed)} km/h el motor gira a ${"%,.0f".format(rpm)} RPM — las RPM son excesivamente altas para esta velocidad.",
                    severity = DiagnosticSeverity.HIGH,
                    probableCauses = listOf(
                        "Deslizamiento del convertidor de torque (lock-up no se activa).",
                        "Embragues/bandas de la transmisión automática desgastados.",
                        "Nivel de fluido de transmisión bajo o degradado.",
                        "Solenoide TCC (Torque Converter Clutch) defectuoso.",
                        "Problema de válvula de cuerpo (valve body) de la transmisión."
                    ),
                    testSteps = listOf(
                        "1. Verifique nivel y condición del fluido ATF. Si huele a quemado, hay daño interno.",
                        "2. En carretera a velocidad constante (80 km/h), las RPM deben ser ~2000-2500. Si son >3000, hay slip.",
                        "3. Si tiene scanner bidireccional, active el solenoide TCC manualmente y observe si RPM bajan.",
                        "4. Revise DTCs de transmisión — P0740-P0770 son típicos de deslizamiento."
                    )
                )
            )
        }

        // Motor girando pero vehículo casi no avanza (transmisión patinando severamente)
        if (rpm > 2500f && speed < 15f && speed > 0f) {
            procedures.add(
                ExpertDiagnosticProcedure(
                    title = "🚨 Transmisión Patinando Severamente",
                    description = "Motor a ${"%,.0f".format(rpm)} RPM pero velocidad solo ${"%,.0f".format(speed)} km/h. La transmisión no está transfiriendo potencia al tren motriz.",
                    severity = DiagnosticSeverity.CRITICAL,
                    probableCauses = listOf(
                        "Embragues internos severamente desgastados.",
                        "Nivel de ATF críticamente bajo.",
                        "Convertidor de torque con falla interna.",
                        "Eje de transmisión roto o desconectado (verificar físicamente)."
                    ),
                    testSteps = listOf(
                        "1. DETENGA EL VEHÍCULO. Continuar conduciendo puede destruir la transmisión completamente.",
                        "2. Verifique nivel de fluido INMEDIATAMENTE.",
                        "3. Si el nivel está correcto y el fluido huele a quemado, la transmisión requiere reconstrucción."
                    )
                )
            )
        }
    }
}
