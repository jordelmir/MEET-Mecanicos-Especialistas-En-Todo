package com.elysium369.meet.core.intent

/**
 * ASTRA V6 §11 — Universal Intent Router.
 *
 * Classifies natural-language human intent into one of the canonical
 * economic/cognitive intents that ELYSIUM can orchestrate. This is
 * the single entry point for the platform's "tell me what you need"
 * experience.
 *
 * The router does NOT use LLM inference at this stage — it uses
 * deterministic keyword/pattern matching for speed and offline
 * capability. LLM-based intent refinement can layer on top.
 */
object UniversalIntentRouter {

    enum class ElysiumIntent(val code: String, val description: String) {
        LEARN("LEARN", "Quiero aprender algo"),
        BUY_SERVICE("BUY_SERVICE", "Necesito un servicio"),
        PROVIDE_SERVICE("PROVIDE_SERVICE", "Quiero ofrecer mis servicios"),
        BUY_PRODUCT("BUY_PRODUCT", "Necesito comprar un producto/repuesto"),
        SELL_PRODUCT("SELL_PRODUCT", "Quiero vender un producto"),
        HIRE("HIRE", "Necesito contratar personal"),
        FIND_WORK("FIND_WORK", "Busco trabajo u oportunidades"),
        CREATE_BUSINESS("CREATE_BUSINESS", "Quiero crear/administrar un negocio"),
        ECONOMIC_DISCOVERY("ECONOMIC_DISCOVERY", "¿Qué servicios tienen demanda?"),
        LEARNING_TO_EARNING("LEARNING_TO_EARNING", "Quiero aprender para trabajar de eso"),
        DIAGNOSE("DIAGNOSE", "Necesito diagnosticar un problema"),
        MOVE("MOVE", "Necesito transportar personas/cosas"),
        UNKNOWN("UNKNOWN", "Intención no clasificada"),
    }

    data class IntentClassification(
        val primaryIntent: ElysiumIntent,
        val confidence: Double,
        val secondaryIntent: ElysiumIntent? = null,
        val extractedSubject: String? = null,
        val extractedDomain: String? = null,
        val rawInput: String,
    ) {
        init {
            require(confidence in 0.0..1.0) { "confidence must be [0.0, 1.0]" }
        }

        val isAmbiguous: Boolean get() = confidence < 0.60
        val needsClarification: Boolean get() = primaryIntent == ElysiumIntent.UNKNOWN || isAmbiguous
    }

    private data class PatternRule(
        val keywords: List<String>,
        val intent: ElysiumIntent,
        val boost: Double = 1.0,
        val domainHint: String? = null,
    )

    private val rules: List<PatternRule> = listOf(
        // LEARNING_TO_EARNING (checked first — highest boost breaks tie with LEARN)
        PatternRule(listOf("aprender para trabajar", "aprender para ganar", "capacitarme para"), ElysiumIntent.LEARNING_TO_EARNING, 2.0),
        PatternRule(listOf("learn to earn", "train for work"), ElysiumIntent.LEARNING_TO_EARNING, 2.0),

        // LEARN
        PatternRule(listOf("aprender", "estudiar", "curso", "enseñar", "tutorial", "clase"), ElysiumIntent.LEARN),
        PatternRule(listOf("learn", "study", "course", "teach", "lesson"), ElysiumIntent.LEARN),
        PatternRule(listOf("cómo se hace", "cómo funciona", "explica"), ElysiumIntent.LEARN, 0.8),

        // DIAGNOSE (high boost — "diagnosticar" is unambiguous)
        PatternRule(listOf("diagnosticar", "diagnóstico", "escanear", "scanner", "obd", "check engine"), ElysiumIntent.DIAGNOSE, 1.5),
        PatternRule(listOf("diagnose", "diagnostic"), ElysiumIntent.DIAGNOSE, 1.5),

        // MOVE (high boost — "grúa", "mudanza", "transportar" are unambiguous)
        PatternRule(listOf("grúa", "grua", "mudanza", "transportar", "taxi", "enviar"), ElysiumIntent.MOVE, 1.5),
        PatternRule(listOf("tow", "moving", "deliver"), ElysiumIntent.MOVE, 1.5),

        // BUY_SERVICE
        PatternRule(listOf("reparar", "arreglar", "instalar", "contratar servicio"), ElysiumIntent.BUY_SERVICE),
        PatternRule(listOf("se rompió", "no funciona", "se salió", "saliendo", "tiene fuga", "no enfría", "no enciende", "gotea"), ElysiumIntent.BUY_SERVICE, 0.9),
        PatternRule(listOf("necesito un", "busco un"), ElysiumIntent.BUY_SERVICE, 0.5),
        PatternRule(listOf("fix my", "repair my", "install"), ElysiumIntent.BUY_SERVICE),

        // PROVIDE_SERVICE
        PatternRule(listOf("soy", "ofrezco", "mis servicios", "quiero clientes", "busco clientes"), ElysiumIntent.PROVIDE_SERVICE),
        PatternRule(listOf("i am a", "i offer", "my services", "looking for clients"), ElysiumIntent.PROVIDE_SERVICE),

        // BUY_PRODUCT
        PatternRule(listOf("comprar", "repuesto", "pieza", "herramienta", "material", "producto"), ElysiumIntent.BUY_PRODUCT),
        PatternRule(listOf("buy", "part", "tool", "material", "product"), ElysiumIntent.BUY_PRODUCT),

        // SELL_PRODUCT
        PatternRule(listOf("vender", "pongo en venta", "quiero vender"), ElysiumIntent.SELL_PRODUCT),

        // HIRE
        PatternRule(listOf("contratar personal", "necesito personal", "busco empleados", "vacante"), ElysiumIntent.HIRE),
        PatternRule(listOf("hire", "staffing", "recruit", "vacancy"), ElysiumIntent.HIRE),

        // FIND_WORK
        PatternRule(listOf("busco trabajo", "necesito ganar", "oportunidades", "empleo"), ElysiumIntent.FIND_WORK),
        PatternRule(listOf("find work", "need income", "job opportunities"), ElysiumIntent.FIND_WORK),

        // CREATE_BUSINESS
        PatternRule(listOf("crear empresa", "mi negocio", "emprender", "abrir taller"), ElysiumIntent.CREATE_BUSINESS),
        PatternRule(listOf("start business", "create company", "entrepreneurship"), ElysiumIntent.CREATE_BUSINESS),

        // ECONOMIC_DISCOVERY
        PatternRule(listOf("demanda", "qué se necesita", "qué falta", "mercado"), ElysiumIntent.ECONOMIC_DISCOVERY),
        PatternRule(listOf("servicios tienen", "servicios necesita"), ElysiumIntent.ECONOMIC_DISCOVERY, 1.2),
        PatternRule(listOf("market demand", "what services", "what's needed"), ElysiumIntent.ECONOMIC_DISCOVERY),
    )

    // Domain detection keywords
    private val domainKeywords: Map<String, String> = mapOf(
        "mecánico" to "AUTOMOTIVE", "carro" to "AUTOMOTIVE", "vehículo" to "AUTOMOTIVE",
        "motor" to "AUTOMOTIVE", "frenos" to "AUTOMOTIVE", "aceite" to "AUTOMOTIVE",
        "car" to "AUTOMOTIVE", "vehicle" to "AUTOMOTIVE", "engine" to "AUTOMOTIVE",
        "plomero" to "PLUMBING", "plomería" to "PLUMBING", "tubería" to "PLUMBING",
        "fuga" to "PLUMBING", "agua" to "PLUMBING", "grifo" to "PLUMBING",
        "plumber" to "PLUMBING", "plumbing" to "PLUMBING", "pipe" to "PLUMBING",
        "electricista" to "ELECTRICAL", "electricidad" to "ELECTRICAL", "cable" to "ELECTRICAL",
        "breaker" to "ELECTRICAL", "enchufe" to "ELECTRICAL",
        "electrician" to "ELECTRICAL", "wiring" to "ELECTRICAL",
        "carpintero" to "CARPENTRY", "carpintería" to "CARPENTRY", "madera" to "CARPENTRY",
        "programación" to "SOFTWARE", "programador" to "SOFTWARE", "software" to "SOFTWARE",
        "web" to "SOFTWARE", "app" to "SOFTWARE", "código" to "SOFTWARE",
        "diseño" to "DESIGN", "logo" to "DESIGN", "gráfico" to "DESIGN",
        "aire acondicionado" to "HVAC", "clima" to "HVAC", "refrigeración" to "HVAC",
        "pintura" to "PAINTING", "pintor" to "PAINTING",
        "soldadura" to "WELDING", "soldador" to "WELDING",
        "matemática" to "EDUCATION_MATH", "inglés" to "EDUCATION_LANGUAGE",
        "física" to "EDUCATION_SCIENCE", "química" to "EDUCATION_SCIENCE",
    )

    /**
     * Classifies raw user input into a structured intent.
     *
     * This is a deterministic first-pass classifier. For ambiguous inputs,
     * [IntentClassification.needsClarification] will be true and the UI
     * should prompt the user for refinement.
     */
    fun classify(input: String): IntentClassification {
        val normalized = input.trim().lowercase()
        if (normalized.isBlank()) {
            return IntentClassification(
                primaryIntent = ElysiumIntent.UNKNOWN,
                confidence = 0.0,
                rawInput = input,
            )
        }

        // Score each intent
        val scores = mutableMapOf<ElysiumIntent, Double>()
        for (rule in rules) {
            val matchCount = rule.keywords.count { keyword ->
                if (keyword.contains(" ")) {
                    // Multi-word: all tokens must be present anywhere in input
                    keyword.split(" ").all { token -> normalized.contains(token) }
                } else {
                    normalized.contains(keyword)
                }
            }
            if (matchCount > 0) {
                val score = (matchCount.toDouble() / rule.keywords.size) * rule.boost
                scores[rule.intent] = (scores[rule.intent] ?: 0.0) + score
            }
        }

        // Detect domain
        val detectedDomain = domainKeywords.entries
            .firstOrNull { (keyword, _) -> normalized.contains(keyword) }
            ?.value

        if (scores.isEmpty()) {
            return IntentClassification(
                primaryIntent = ElysiumIntent.UNKNOWN,
                confidence = 0.10,
                extractedDomain = detectedDomain,
                rawInput = input,
            )
        }

        val sorted = scores.entries.sortedByDescending { it.value }
        val topScore = sorted[0].value
        val maxPossible = rules.filter { it.intent == sorted[0].key }.maxOfOrNull { it.boost } ?: 1.0
        val confidence = (topScore / maxPossible).coerceIn(0.0, 0.98)

        return IntentClassification(
            primaryIntent = sorted[0].key,
            confidence = confidence,
            secondaryIntent = sorted.getOrNull(1)?.key,
            extractedDomain = detectedDomain,
            rawInput = input,
        )
    }
}
