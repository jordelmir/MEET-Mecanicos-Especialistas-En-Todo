package com.elysium369.meet.core.education.theory

/**
 * Original MEET study material. It is not an official question bank and does not
 * reproduce the paid 2026 MOPT/DGEV manuals. Every curriculum item keeps its
 * public source identifiers so the UI can show where the rule came from.
 */
enum class TheoryLicenseTrack(val displayName: String, val shortName: String) {
    AUTOMOBILE("Automóvil · Clase B", "AUTO"),
    MOTORCYCLE("Motocicleta · Clase A", "MOTO"),
}

enum class TheoryTopic(val displayName: String, val icon: String) {
    SAFE_MOBILITY("Movilidad segura", "◎"),
    SIGNALS("Señalamiento vial", "◇"),
    VEHICLE("Vehículo seguro", "⚙"),
    MANEUVERS("Maniobras", "↗"),
    RISK("Riesgo y prevención", "△"),
    LAW("Ley y consecuencias", "§"),
}

enum class TheorySourceKind { OFFICIAL, LAW, RESEARCH, INDEPENDENT_PRACTICE }

data class TheorySource(
    val id: String,
    val title: String,
    val authority: String,
    val url: String,
    val kind: TheorySourceKind,
    val note: String,
)

data class TheoryLesson(
    val id: String,
    val tracks: Set<TheoryLicenseTrack>,
    val topic: TheoryTopic,
    val title: String,
    val objective: String,
    val keyPoints: List<String>,
    val decisionRule: String,
    val sourceIds: Set<String>,
)

data class TheoryQuestion(
    val id: String,
    val tracks: Set<TheoryLicenseTrack>,
    val topic: TheoryTopic,
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val sourceIds: Set<String>,
) {
    init {
        require(options.size == 4) { "Every question must have exactly four options" }
        require(correctIndex in options.indices) { "Correct index is outside the options" }
        require(sourceIds.isNotEmpty()) { "Every question needs at least one traceable source" }
    }
}

object TheoryExamKnowledge {
    const val VERIFIED_ON = "22 agosto 2026"
    const val PASSING_SCORE = 80
    const val SIMULATION_SIZE = 40
    const val OFFICIAL_MANUAL_PRICE_CRC = 3_500
    const val OFFICIAL_TEST_PRICE_CRC = 5_000

    private val both = TheoryLicenseTrack.entries.toSet()
    private val auto = setOf(TheoryLicenseTrack.AUTOMOBILE)
    private val moto = setOf(TheoryLicenseTrack.MOTORCYCLE)

    val sources = listOf(
        TheorySource(
            "mopt_2026_manuals",
            "Nuevos Manuales del Conductor disponibles",
            "MOPT · Dirección General de Educación Vial",
            "https://www.mopt.go.cr/index.php/noticias/2026/nuevos-manuales-del-conductor-estaran-disponibles-el-lunes",
            TheorySourceKind.OFFICIAL,
            "Confirma manuales separados para automóvil y motocicleta, vigencia desde el 2 de marzo de 2026, precio y canales autorizados.",
        ),
        TheorySource(
            "mopt_license_portal",
            "Portal oficial de licencias",
            "Ministerio de Obras Públicas y Transportes",
            "https://www.mopt.go.cr/licencias",
            TheorySourceKind.OFFICIAL,
            "Punto de entrada oficial para credenciales y matrícula. Los trámites no se coordinan por redes sociales.",
        ),
        TheorySource(
            "mopt_faq",
            "Preguntas frecuentes de Educación Vial",
            "MOPT · Educación Vial",
            "https://serviciosweb.mopt.go.cr/SQDC/faces/PreguntaFrecuentes.xhtml",
            TheorySourceKind.OFFICIAL,
            "Publica, entre otros datos operativos, que la nota mínima es 80.",
        ),
        TheorySource(
            "law_9078",
            "Ley de Tránsito por Vías Públicas Terrestres y Seguridad Vial N.° 9078",
            "Sistema Costarricense de Información Jurídica · PGR",
            "https://pgrweb.go.cr/Scij/Busqueda/Normativa/Normas/nrm_texto_completo.aspx?nValor1=1&nValor2=73504",
            TheorySourceKind.LAW,
            "Texto normativo vigente. Los montos y artículos pueden reformarse; MEET enlaza la fuente viva en vez de congelar cifras.",
        ),
        TheorySource(
            "cosevi_curriculum",
            "Estructura técnica del manual de conducción segura",
            "COSEVI · Junta Directiva, sesión 3044-2021",
            "https://www.csv.go.cr/documents/20126/2801815/3044-21.pdf",
            TheorySourceKind.OFFICIAL,
            "Antecedente público del enfoque preventivo y de seis áreas: movilidad, señales, vehículo, maniobras, riesgos y ley.",
        ),
        TheorySource(
            "imprenta_manuals",
            "Venta autorizada de los manuales 2026",
            "Imprenta Nacional de Costa Rica",
            "https://www.imprentanacional.go.cr/noticias/2026/venta-nuevos-manuales-conductor.aspx",
            TheorySourceKind.OFFICIAL,
            "Canales, precio y forma de adquirir el manual oficial. MEET no distribuye copias no autorizadas.",
        ),
        TheorySource(
            "learning_science",
            "The science of effective learning with spacing and retrieval practice",
            "Nature Reviews Psychology (2022)",
            "https://www.nature.com/articles/s44159-022-00089-1.pdf",
            TheorySourceKind.RESEARCH,
            "Base del repaso espaciado, la recuperación activa y la autorregulación del aprendizaje.",
        ),
        TheorySource(
            "free_99kph",
            "Curso teórico gratuito 2026",
            "99kph · recurso independiente",
            "https://99kph.com/curso-teorico-de-manejo",
            TheorySourceKind.INDEPENDENT_PRACTICE,
            "Práctica externa gratuita. No es MOPT, DGEV ni COSEVI; MEET no certifica que sus preguntas sean oficiales.",
        ),
        TheorySource(
            "free_manejogo",
            "Simulador gratuito de examen teórico",
            "ManejoGO · recurso independiente",
            "https://manejogo.com/",
            TheorySourceKind.INDEPENDENT_PRACTICE,
            "Banco externo para práctica adicional. Debe contrastarse con el manual oficial 2026 correspondiente.",
        ),
    )

    val lessons = listOf(
        TheoryLesson(
            "safe_system", both, TheoryTopic.SAFE_MOBILITY,
            "Conducir es compartir un sistema",
            "Tomar decisiones que protejan primero a quienes tienen menos protección física.",
            listOf(
                "La vía es un espacio compartido por peatones, ciclistas, motociclistas, pasajeros y vehículos.",
                "La persona conductora debe anticipar el error ajeno y dejar un margen de seguridad.",
                "Velocidad adecuada no significa únicamente respetar el máximo: también depende de visibilidad, clima y tránsito.",
                "La cortesía nunca autoriza una maniobra contraria al señalamiento o que sorprenda a terceros.",
            ),
            "Si una decisión reduce tu margen para detenerte o vuelve impredecible tu trayectoria, no es una decisión segura.",
            setOf("cosevi_curriculum", "law_9078"),
        ),
        TheoryLesson(
            "signals", both, TheoryTopic.SIGNALS,
            "Leer la vía como un lenguaje",
            "Reconocer la función de señales verticales, demarcación, semáforos y órdenes manuales.",
            listOf(
                "Reglamentación impone obligaciones o prohibiciones; prevención advierte peligros; información orienta.",
                "La demarcación horizontal organiza carriles, zonas de detención y maniobras permitidas.",
                "Una luz amarilla exige detenerse cuando pueda hacerse con seguridad; no es una invitación a acelerar.",
                "Las indicaciones de la autoridad de tránsito prevalecen durante el control de la circulación.",
            ),
            "Antes de actuar identifica quién regula, qué orden expresa y a quién aplica.",
            setOf("cosevi_curriculum", "law_9078"),
        ),
        TheoryLesson(
            "vehicle", both, TheoryTopic.VEHICLE,
            "El vehículo como sistema de seguridad",
            "Entender cómo visibilidad, llantas, frenos, iluminación y retención reducen el riesgo.",
            listOf(
                "Ajusta asiento, volante, espejos y cinturón antes de iniciar la marcha.",
                "Llantas con presión o condición inadecuadas reducen adherencia, estabilidad y capacidad de frenado.",
                "ABS ayuda a conservar control direccional durante una frenada intensa; no elimina las leyes de la física.",
                "Testigos, fugas, luces defectuosas o una respuesta anormal del freno requieren evaluación antes de circular.",
            ),
            "Un testigo de seguridad o una falla que afecte dirección, freno, llanta o visibilidad se atiende antes del viaje.",
            setOf("cosevi_curriculum", "law_9078"),
        ),
        TheoryLesson(
            "maneuvers", both, TheoryTopic.MANEUVERS,
            "Maniobra segura: observar, comunicar, ejecutar",
            "Construir una secuencia repetible para incorporarse, girar, adelantar, frenar y estacionar.",
            listOf(
                "Observa espejos y punto ciego, señaliza con anticipación y confirma que existe espacio.",
                "No adelantes sin visibilidad, distancia y posibilidad real de regresar al carril sin forzar a otros.",
                "En lluvia, oscuridad o superficie deslizante aumenta distancia y reduce suavemente la velocidad.",
                "En rotondas, planifica la salida, respeta prioridad y evita cambios bruscos de carril.",
            ),
            "Espejo → señal → punto ciego → espacio → maniobra suave → cancelar señal.",
            setOf("cosevi_curriculum", "law_9078"),
        ),
        TheoryLesson(
            "risk", both, TheoryTopic.RISK,
            "Riesgo humano y respuesta al siniestro",
            "Detectar condiciones personales y ambientales que vuelven inseguro conducir.",
            listOf(
                "Alcohol, drogas, ciertos medicamentos, fatiga, estrés intenso y distracción degradan decisiones y reacción.",
                "El teléfono desvía ojos, manos o mente; incluso una interacción breve puede ocultar un peligro completo.",
                "Ante un siniestro: protege la escena sin exponerte, alerta a emergencias y auxilia dentro de tu capacidad.",
                "No muevas innecesariamente a una persona lesionada salvo peligro inmediato.",
            ),
            "Si no estás en condiciones de conducir con atención completa, la acción segura es no iniciar o detener el viaje.",
            setOf("cosevi_curriculum", "law_9078"),
        ),
        TheoryLesson(
            "law", both, TheoryTopic.LAW,
            "Normas, responsabilidad y fuentes vivas",
            "Comprender obligaciones sin memorizar montos que pueden cambiar.",
            listOf(
                "La Ley 9078 regula la circulación y la seguridad vial en Costa Rica.",
                "Hay consecuencias administrativas, multas, puntos y posibles consecuencias penales según la conducta.",
                "Licencia, condiciones del vehículo y documentos exigibles deben corresponder a la situación real.",
                "Para cifras, requisitos y sanciones vigentes consulta siempre la versión actual del SCIJ/PGR y el MOPT.",
            ),
            "En preguntas legales distingue la conducta, el deber de seguridad y la consecuencia; verifica cifras en la norma viva.",
            setOf("law_9078", "mopt_faq"),
        ),
        TheoryLesson(
            "auto_specific", auto, TheoryTopic.VEHICLE,
            "Automóvil: control, ocupantes y espacio",
            "Preparar el habitáculo y controlar un vehículo de cuatro ruedas sin crear zonas ciegas.",
            listOf(
                "Todos los ocupantes deben usar el sistema de retención que corresponda.",
                "La carga debe quedar asegurada y no interferir con pedales, volante, palanca ni visibilidad.",
                "La reversa se limita a lo necesario, a baja velocidad y tras verificar todo el entorno.",
                "Luces altas se usan sin deslumbrar; deben cambiarse cuando afecten a otros usuarios.",
            ),
            "Cabina asegurada, 360° observado y trayectoria clara antes de mover el automóvil.",
            setOf("cosevi_curriculum", "law_9078"),
        ),
        TheoryLesson(
            "moto_specific", moto, TheoryTopic.VEHICLE,
            "Motocicleta: visibilidad, equilibrio y protección",
            "Gestionar la vulnerabilidad propia de dos ruedas con equipo y técnica preventiva.",
            listOf(
                "Casco correctamente ajustado y equipo protector reducen la gravedad de lesiones; no evitan el siniestro.",
                "Frena de forma progresiva usando ambos frenos según adherencia y condición de la motocicleta.",
                "Hazte visible, conserva una vía de escape y evita permanecer en puntos ciegos.",
                "Pasajero y carga cambian aceleración, equilibrio y frenado: deben asegurarse y respetar la capacidad del vehículo.",
            ),
            "Mira lejos, crea espacio, sé visible y ejecuta mandos con suavidad.",
            setOf("cosevi_curriculum", "law_9078"),
        ),
    )

    val questions: List<TheoryQuestion> = buildList {
        fun q(id: String, tracks: Set<TheoryLicenseTrack>, topic: TheoryTopic, prompt: String,
              correct: String, b: String, c: String, d: String, explanation: String,
              vararg sourceIds: String) {
            add(TheoryQuestion(id, tracks, topic, prompt, listOf(correct, b, c, d), 0, explanation, sourceIds.toSet()))
        }

        q("c01", both, TheoryTopic.SAFE_MOBILITY, "¿Qué decisión representa mejor el enfoque de movilidad segura?",
            "Proteger primero a usuarios vulnerables y conservar margen ante errores", "Mantener siempre el límite máximo publicado", "Ceder aunque se contradiga el señalamiento", "Confiar en que los demás no cometerán errores",
            "La seguridad vial se basa en convivencia, anticipación y protección de quienes tienen menor protección física.", "cosevi_curriculum")
        q("c02", both, TheoryTopic.SAFE_MOBILITY, "La velocidad máxima indicada…",
            "Es un máximo; puede ser necesario circular más lento por las condiciones", "Debe alcanzarse aunque llueva", "Solo aplica cuando hay otros vehículos", "Es una recomendación opcional",
            "Visibilidad, clima, superficie y tránsito pueden exigir una velocidad inferior al máximo.", "law_9078", "cosevi_curriculum")
        q("c03", both, TheoryTopic.SAFE_MOBILITY, "¿Cuál es la mejor forma de conservar distancia de seguimiento?",
            "Usar una referencia temporal y aumentarla cuando crece el riesgo", "Mantener siempre una distancia fija en metros", "Acercarse para impedir que otro vehículo ingrese", "Copiar exactamente la velocidad del vehículo delantero",
            "Una referencia en tiempo se adapta mejor a la velocidad; lluvia, oscuridad y carga requieren más margen.", "cosevi_curriculum")
        q("c04", both, TheoryTopic.SAFE_MOBILITY, "Un peatón parece dispuesto a cruzar. ¿Qué debe hacer la persona conductora?",
            "Reducir, cubrir el freno y estar preparada para ceder", "Tocar la bocina y mantener velocidad", "Acelerar antes de que entre a la vía", "Desviarse sin revisar espejos",
            "Anticipar a usuarios vulnerables evita depender de una reacción tardía.", "law_9078", "cosevi_curriculum")
        q("c05", both, TheoryTopic.SAFE_MOBILITY, "¿Qué significa conducir de forma predecible?",
            "Comunicar con tiempo y realizar trayectorias progresivas", "Circular siempre por el centro del carril", "Nunca ajustar la velocidad", "Dar prioridad por cortesía aunque sea ilegal",
            "Señales oportunas y movimientos suaves permiten a los demás comprender tu intención.", "cosevi_curriculum")
        q("c06", both, TheoryTopic.SIGNALS, "¿Qué orden debe obedecerse durante un control manual del tránsito?",
            "La indicación de la autoridad que está dirigiendo la circulación", "La señal vertical aunque contradiga al oficial", "La costumbre habitual de esa intersección", "La instrucción de otro conductor",
            "La autoridad dirige la situación excepcional; hay que interpretar su indicación antes de avanzar.", "law_9078", "cosevi_curriculum")
        q("c07", both, TheoryTopic.SIGNALS, "Una luz amarilla fija significa que debe…",
            "Detenerse si puede hacerlo con seguridad", "Acelerar para cruzar antes del rojo", "Detenerse dentro de la intersección", "Continuar siempre sin reducir",
            "La fase amarilla advierte el cambio; no autoriza acelerar ni detenerse de forma peligrosa.", "law_9078", "cosevi_curriculum")
        q("c08", both, TheoryTopic.SIGNALS, "Ante una señal de ALTO se debe…",
            "Realizar una detención completa y avanzar solo cuando sea seguro", "Reducir un poco si no se observa tránsito", "Tocar la bocina y continuar", "Detenerse únicamente de noche",
            "ALTO exige detención completa y verificación de la prioridad y del entorno.", "law_9078")
        q("c09", both, TheoryTopic.SIGNALS, "La señal CEDA exige…",
            "Reducir y ceder; detenerse cuando sea necesario", "Detenerse siempre durante tres segundos", "Acelerar para incorporarse primero", "Ignorar a peatones",
            "CEDA obliga a dar prioridad y puede requerir una detención según el tránsito.", "law_9078", "cosevi_curriculum")
        q("c10", both, TheoryTopic.SIGNALS, "¿Qué comunica normalmente una línea longitudinal continua?",
            "Una restricción para cruzarla o adelantar en ese tramo", "Una zona exclusiva de estacionamiento", "Que debe aumentarse la velocidad", "Que finaliza toda prioridad",
            "La demarcación continua separa movimientos que no deben cruzarse salvo las excepciones legales aplicables.", "cosevi_curriculum")
        q("c11", both, TheoryTopic.SIGNALS, "Las señales preventivas tienen como función principal…",
            "Advertir un peligro o condición que exige atención", "Imponer todas las multas", "Indicar únicamente destinos", "Sustituir las órdenes del oficial",
            "Prevención alerta; reglamentación obliga o prohíbe; información orienta.", "cosevi_curriculum")
        q("c12", both, TheoryTopic.SIGNALS, "Si un semáforo no funciona, la conducta correcta es…",
            "Reducir, tratar la intersección con máxima precaución y respetar la prioridad aplicable", "Cruzar rápido antes que los demás", "Seguir al vehículo precedente sin observar", "Esperar indefinidamente aunque una autoridad dé paso",
            "Una falla elimina la guía luminosa, no el deber de ceder ni de prevenir el conflicto.", "law_9078")
        q("c13", both, TheoryTopic.VEHICLE, "Antes de iniciar un viaje, el ajuste correcto incluye…",
            "Asiento, espejos, cinturón y controles al alcance", "Espejos después de entrar a carretera", "Cinturón únicamente en autopista", "Volante lo más cerca posible del pecho",
            "La posición se prepara con el vehículo detenido para controlar y observar sin distracción.", "cosevi_curriculum")
        q("c14", both, TheoryTopic.VEHICLE, "Una presión incorrecta en las llantas puede afectar…",
            "Adherencia, estabilidad, frenado y desgaste", "Solo el sonido del motor", "Únicamente la radio", "Nada si el dibujo aún es visible",
            "Las llantas son el contacto con la vía; presión y condición alteran su desempeño.", "cosevi_curriculum")
        q("c15", both, TheoryTopic.VEHICLE, "Durante una frenada de emergencia con ABS activo se debe…",
            "Mantener presión firme y dirigir hacia una zona segura", "Bombear rápidamente el pedal", "Soltar por completo el freno", "Apagar el motor de inmediato",
            "ABS modula la presión para reducir bloqueo; no acorta mágicamente toda distancia de frenado.", "cosevi_curriculum")
        q("c16", both, TheoryTopic.VEHICLE, "Un testigo de frenos permanece encendido al iniciar la marcha. ¿Qué procede?",
            "Detener la salida y verificar la causa antes de circular", "Ignorarlo hasta el próximo mantenimiento", "Acelerar para que se apague", "Desconectar la batería",
            "Una alerta que puede comprometer el frenado exige diagnóstico antes del viaje.", "cosevi_curriculum", "law_9078")
        q("c17", both, TheoryTopic.VEHICLE, "¿Para qué sirve principalmente el apoyacabezas bien ajustado?",
            "Reducir el movimiento lesivo de cabeza y cuello en un impacto", "Mejorar la visibilidad trasera", "Sustituir el cinturón", "Evitar la fatiga de las manos",
            "Es parte del sistema de retención; funciona junto con asiento y cinturón.", "cosevi_curriculum")
        q("c18", both, TheoryTopic.VEHICLE, "La carga dentro o sobre un vehículo debe…",
            "Estar asegurada sin bloquear controles, luces ni visibilidad", "Moverse libremente para repartir peso", "Cubrir la placa si sobresale", "Apoyarse contra el conductor",
            "Una carga suelta puede alterar el control, lesionar ocupantes u ocultar elementos obligatorios.", "law_9078", "cosevi_curriculum")
        q("c19", both, TheoryTopic.MANEUVERS, "Antes de cambiar de carril, la secuencia más segura es…",
            "Espejos, señal, punto ciego, espacio y movimiento suave", "Señal y giro inmediato", "Bocina, aceleración y giro", "Frenar dentro del carril vecino",
            "El punto ciego debe comprobarse además de los espejos y la intención debe anunciarse.", "cosevi_curriculum")
        q("c20", both, TheoryTopic.MANEUVERS, "¿Cuándo debe descartarse un adelantamiento?",
            "Cuando no hay visibilidad o espacio suficiente para completarlo", "Cuando el vehículo delantero va bajo el máximo", "Solo cuando llueve", "Cuando el carril contrario parece vacío por un instante",
            "Debe existir visibilidad continua, espacio y retorno seguro sin forzar a terceros.", "law_9078", "cosevi_curriculum")
        q("c21", both, TheoryTopic.MANEUVERS, "Al ingresar a una rotonda se debe…",
            "Reducir, observar el flujo y ceder según el señalamiento y la prioridad", "Ingresar rápido para obtener prioridad", "Detenerse siempre dentro de la rotonda", "Cambiar de carril en la salida sin señal",
            "La entrada se realiza solo cuando existe un espacio seguro y la salida se planifica con anticipación.", "cosevi_curriculum", "law_9078")
        q("c22", both, TheoryTopic.MANEUVERS, "Comienza lluvia intensa. La respuesta correcta es…",
            "Reducir progresivamente y aumentar la distancia", "Encender luces altas y mantener velocidad", "Frenar bruscamente en cada charco", "Seguir más cerca las luces del vehículo delantero",
            "La adherencia y la visibilidad disminuyen; se necesita más margen para observar y detenerse.", "cosevi_curriculum")
        q("c23", both, TheoryTopic.MANEUVERS, "Si pierde adherencia, una reacción generalmente segura es…",
            "Mirar hacia la salida, suavizar mandos y evitar frenazos bruscos", "Girar el mando al extremo opuesto", "Aplicar máxima aceleración", "Cerrar los ojos y sujetar rígidamente",
            "Movimientos abruptos consumen la poca adherencia disponible; la recuperación debe ser progresiva.", "cosevi_curriculum")
        q("c24", both, TheoryTopic.MANEUVERS, "Las luces altas deben cambiarse a bajas…",
            "Cuando puedan deslumbrar a otra persona usuaria", "Solo dentro de ciudades", "Únicamente cuando llueve", "Nunca en carretera",
            "La iluminación debe permitir ver sin quitar visión a quien circula de frente o delante.", "law_9078", "cosevi_curriculum")
        q("c25", both, TheoryTopic.RISK, "Usar el teléfono al conducir es peligroso porque…",
            "Puede desviar simultáneamente vista, manos y atención", "Solo consume batería", "Afecta únicamente a conductores nuevos", "Es seguro si el vehículo avanza despacio",
            "La distracción no desaparece por circular lento ni por conocer la ruta.", "cosevi_curriculum", "law_9078")
        q("c26", both, TheoryTopic.RISK, "Siente sueño persistente al conducir. ¿Qué debe hacer?",
            "Detenerse en un lugar seguro y descansar o cambiar de conductor", "Abrir la ventana y continuar", "Subir el volumen de la música", "Aumentar la velocidad para llegar antes",
            "Los trucos momentáneos no restauran la capacidad; la fatiga requiere detener el viaje.", "cosevi_curriculum")
        q("c27", both, TheoryTopic.RISK, "Respecto al alcohol y la conducción, la decisión de menor riesgo es…",
            "No conducir y usar una alternativa segura", "Conducir solo por calles conocidas", "Tomar café antes de salir", "Esperar unos minutos sin evaluar el estado",
            "No existe una maniobra que compense el deterioro de percepción y juicio.", "cosevi_curriculum", "law_9078")
        q("c28", both, TheoryTopic.RISK, "Ante un siniestro con personas lesionadas, el orden básico es…",
            "Proteger sin exponerse, alertar y auxiliar dentro de la capacidad", "Mover a todas las personas de inmediato", "Abandonar el sitio para evitar tránsito", "Dar bebidas antes de llamar",
            "Primero se evita un segundo siniestro, luego se activa ayuda profesional y se auxilia sin agravar lesiones.", "cosevi_curriculum")
        q("c29", both, TheoryTopic.RISK, "Un medicamento nuevo indica somnolencia. Antes de conducir debe…",
            "Consultar la advertencia y no conducir si afecta capacidades", "Compensarlo con una bebida energética", "Tomar media dosis sin indicación", "Conducir únicamente de día",
            "Medicamentos pueden degradar reacción y atención; la etiqueta y orientación profesional importan.", "cosevi_curriculum")
        q("c30", both, TheoryTopic.RISK, "¿Qué acción reduce el riesgo al aproximarse a una intersección con visión limitada?",
            "Reducir y prepararse para detenerse ante un conflicto oculto", "Usar la bocina y asumir vía libre", "Invadir parcialmente el carril contrario", "Acelerar para pasar menos tiempo allí",
            "Menor visibilidad exige menor velocidad y mayor capacidad de detenerse.", "cosevi_curriculum")
        q("c31", both, TheoryTopic.LAW, "¿Dónde debe verificarse el texto vigente de la Ley 9078?",
            "En el Sistema Costarricense de Información Jurídica de la PGR", "En una publicación anónima sin fecha", "En cualquier simulador comercial", "En mensajes reenviados por redes sociales",
            "El SCIJ muestra la norma y sus versiones; las cifras legales pueden cambiar.", "law_9078")
        q("c32", both, TheoryTopic.LAW, "Según la información oficial consultada, la nota mínima publicada para aprobar es…",
            "80", "70", "60", "100",
            "La FAQ oficial de Educación Vial publica una nota mínima de 80. Conviene revalidarla al matricular.", "mopt_faq")

        q("a01", auto, TheoryTopic.VEHICLE, "En un automóvil, ¿cuándo deben ajustarse los espejos?",
            "Antes de iniciar la marcha y con la posición de conducción ya ajustada", "Durante el primer cambio de carril", "Después de entrar a la carretera", "Solo cuando otra persona lo solicita",
            "Ajustarlos detenido evita distracción y reduce zonas ciegas desde el inicio.", "cosevi_curriculum")
        q("a02", auto, TheoryTopic.VEHICLE, "Una persona menor viaja en el automóvil. Debe utilizar…",
            "El sistema de retención que corresponda a sus características y a la norma", "El cinturón de un adulto en cualquier asiento", "Los brazos de otro pasajero", "Ninguna retención si el viaje es corto",
            "La protección infantil no depende de la duración del viaje; debe ser compatible y usarse correctamente.", "law_9078", "cosevi_curriculum")
        q("a03", auto, TheoryTopic.MANEUVERS, "La marcha atrás debe realizarse…",
            "Solo lo necesario, lentamente y tras observar todo el entorno", "Con rapidez para terminar antes", "Mirando únicamente la cámara", "Sin señalizar porque el vehículo ya muestra luces",
            "Cámaras y sensores ayudan, pero no sustituyen la observación directa y los espejos.", "cosevi_curriculum")
        q("a04", auto, TheoryTopic.MANEUVERS, "Antes de abrir una puerta hacia el tránsito se debe…",
            "Verificar espejos y punto ciego para no interceptar ciclistas u otros usuarios", "Abrirla primero y observar después", "Tocar la bocina", "Encender luces altas",
            "Una puerta puede invadir la trayectoria de usuarios vulnerables; observar antes evita el conflicto.", "cosevi_curriculum")
        q("a05", auto, TheoryTopic.MANEUVERS, "Si aparece aquaplaning, la respuesta apropiada es…",
            "Soltar suavemente el acelerador y mantener dirección estable", "Frenar al máximo y girar rápido", "Acelerar para recuperar contacto", "Aplicar el freno de estacionamiento",
            "El cambio brusco de velocidad o dirección puede empeorar la pérdida de adherencia.", "cosevi_curriculum")
        q("a06", auto, TheoryTopic.VEHICLE, "¿Qué debe hacer si el pedal de freno se siente anormal antes del viaje?",
            "No circular hasta verificar el sistema", "Bombearlo en carretera", "Usar solo el freno de estacionamiento", "Compensar conduciendo más rápido",
            "El freno es un sistema crítico; una respuesta anormal exige revisión antes de exponer el vehículo.", "cosevi_curriculum", "law_9078")
        q("a07", auto, TheoryTopic.MANEUVERS, "Al estacionar, el vehículo debe quedar…",
            "Inmovilizado, asegurado y sin obstruir circulación ni visibilidad", "Con el motor encendido", "Pegado a una salida de emergencia", "Sobre el paso peatonal si es breve",
            "El estacionamiento no debe crear un nuevo peligro ni ocupar espacios protegidos.", "law_9078")
        q("a08", auto, TheoryTopic.RISK, "Una maleta queda suelta en la cabina. En una frenada podría…",
            "Convertirse en un proyectil y lesionar a ocupantes", "Mejorar la distribución del peso", "Reducir la distancia de frenado", "Activar correctamente el airbag",
            "Los objetos sueltos conservan movimiento durante cambios bruscos; deben asegurarse.", "cosevi_curriculum")

        q("m01", moto, TheoryTopic.VEHICLE, "El casco de motocicleta protege mejor cuando…",
            "Es adecuado, está en buen estado y correctamente abrochado", "Se lleva suelto para retirarlo rápido", "Se coloca sobre una gorra voluminosa", "Solo se usa en carretera",
            "El casco debe permanecer ajustado durante todo el recorrido.", "cosevi_curriculum", "law_9078")
        q("m02", moto, TheoryTopic.VEHICLE, "¿Por qué conviene usar equipo protector además del casco?",
            "Reduce exposición y gravedad de lesiones, aunque no evita el siniestro", "Permite circular más rápido", "Sustituye la técnica de frenado", "Elimina los puntos ciegos",
            "Protección y prevención se complementan; ninguna autoriza aumentar el riesgo.", "cosevi_curriculum")
        q("m03", moto, TheoryTopic.MANEUVERS, "Una frenada controlada en motocicleta normalmente requiere…",
            "Aplicar ambos frenos progresivamente según adherencia y condición", "Usar siempre solo el freno trasero", "Cerrar los ojos y tensar brazos", "Girar mientras se frena al máximo",
            "La distribución progresiva aprovecha mejor la adherencia y mantiene control.", "cosevi_curriculum")
        q("m04", moto, TheoryTopic.RISK, "Para reducir exposición a puntos ciegos, la persona motociclista debe…",
            "Evitar permanecer junto a vehículos y buscar una posición visible", "Circular siempre pegada al vehículo delantero", "Confiar en el ruido del escape", "Usar luces altas permanentemente",
            "La posición y el espacio son la principal defensa; ser ruidoso no garantiza ser visto.", "cosevi_curriculum")
        q("m05", moto, TheoryTopic.MANEUVERS, "Sobre pintura vial mojada o tapas metálicas conviene…",
            "Reducir antes, mantener la moto estable y evitar mandos bruscos", "Frenar fuerte encima", "Acelerar y girar simultáneamente", "Apagar las luces",
            "Estas superficies pueden ofrecer menos adherencia, especialmente mojadas.", "cosevi_curriculum")
        q("m06", moto, TheoryTopic.VEHICLE, "Llevar pasajero en motocicleta cambia…",
            "Equilibrio, aceleración y distancia de frenado", "Únicamente el consumo de combustible", "Nada si el pasajero es adulto", "Solo la altura del asiento",
            "La masa adicional modifica la respuesta; conductor, pasajero y vehículo deben estar preparados.", "cosevi_curriculum")
        q("m07", moto, TheoryTopic.VEHICLE, "La carga en una motocicleta debe…",
            "Quedar firme, equilibrada y dentro de la capacidad permitida", "Colgar libremente a un lado", "Cubrir la luz trasera", "Apoyarse sobre los controles",
            "Una distribución asimétrica o suelta puede alterar dirección, equilibrio y visibilidad.", "law_9078", "cosevi_curriculum")
        q("m08", moto, TheoryTopic.RISK, "¿Cuál es una estrategia defensiva propia de la motocicleta?",
            "Mantener espacio de escape y mirar lejos para anticipar", "Circular entre vehículos sin margen", "Frenar dentro de cada curva", "Asumir que el faro garantiza ser visto",
            "La vulnerabilidad exige anticipación, visibilidad y una salida disponible.", "cosevi_curriculum")
    }

    fun lessonsFor(track: TheoryLicenseTrack): List<TheoryLesson> =
        lessons.filter { track in it.tracks }

    fun questionsFor(track: TheoryLicenseTrack): List<TheoryQuestion> =
        questions.filter { track in it.tracks }
}
