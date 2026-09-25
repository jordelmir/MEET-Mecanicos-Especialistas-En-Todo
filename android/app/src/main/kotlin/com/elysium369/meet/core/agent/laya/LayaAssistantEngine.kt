package com.elysium369.meet.core.agent.laya

/**
 * Live context from an active or requested ride to ground the assistant in truth.
 */
data class RideAssistantContext(
    val rideId: String,
    val driverName: String? = null,
    val driverPlate: String? = null,
    val driverVehicle: String? = null,
    val state: String = "NONE", // REQUESTED, ACCEPTED, ARRIVING, IN_PROGRESS, COMPLETED
    val etaMinutes: Int? = null,
    val pickupAddress: String? = null,
    val dropoffAddress: String? = null,
    val fareFormatted: String? = null,
    val paymentMethod: String? = null,
)

/**
 * Live vehicle and OBD telemetry context to ground automotive advice.
 */
data class VehicleAssistantContext(
    val vehicleName: String? = null,
    val activeDtcs: List<String> = emptyList(),
    val coolantTempC: Int? = null,
    val isObdConnected: Boolean = false,
    val preItvRiskScore: Double? = null,
)

/**
 * Actionable shortcut presented to the user alongside the text response.
 */
data class AssistantAction(
    val id: String,
    val label: String,
    val type: ActionType,
)

enum class ActionType {
    CALL_DRIVER,
    MESSAGE_DRIVER,
    SHARE_LOCATION,
    SAFETY_CENTER,
    CANCEL_RIDE,
    SCAN_OBD,
    VIEW_DTC_DETAILS,
    REQUEST_TOW,
    VIEW_PRE_ITV,
    COPY_SINPE,
}

/**
 * Synthesized response with zero hallucinated state and actionable options.
 */
data class AssistantResponse(
    val text: String,
    val domain: String,
    val intent: String,
    val confidence: Double,
    val isEmergency: Boolean,
    val suggestedActions: List<AssistantAction> = emptyList(),
    val latencyMs: Long = 0L,
)

/**
 * Conversational Virtual Assistant Engine powered by Laya AI.
 * Transforms natural language into calibrated decisions (System 1) and synthesizes
 * coherent, truthful, and domain-grounded responses across Rides, Automotive,
 * Emissions, Safety, and Towing.
 */
class LayaAssistantEngine(
    private val decisionEngine: LayaDecisionEngine = LayaDecisionEngine(),
) {
    private val domainOptions = listOf(
        "mobility", "automotive", "emissions", "roadside", "safety", "billing", "general"
    )

    private val intentOptions = listOf(
        // Mobility
        "ride.eta_status",
        "ride.payment_method",
        "ride.pricing_inquiry",
        "ride.route_stop",
        "ride.pet_policy",
        "ride.luggage_comfort",
        "ride.share_trip",
        "ride.driver_delay",
        "ride.lost_item",
        "ride.cancel_policy",

        // Automotive
        "auto.check_engine",
        "auto.dtc_explanation",
        "auto.can_i_drive",
        "auto.repair_cost",
        "auto.obd_connect",

        // Emissions
        "emissions.dekra_rules",
        "emissions.lambda_sensor",
        "emissions.smoke_color",

        // Roadside
        "roadside.flat_tire",
        "roadside.tow_truck",
        "roadside.jump_start",

        // Safety
        "safety.emergency",
        "safety.suspicious",

        // General
        "general.teach_me",
        "general.greetings",
    )

    suspend fun ask(
        query: String,
        rideContext: RideAssistantContext? = null,
        vehicleContext: VehicleAssistantContext? = null,
    ): AssistantResponse {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return AssistantResponse(
                text = "¡Hola! Soy EVAIR, tu asistente en Elysium. ¿En qué te puedo colaborar con tu viaje o con tu vehículo hoy?",
                domain = "general",
                intent = "general.greetings",
                confidence = 1.0,
                isEmergency = false,
            )
        }

        // 1. Query Laya in a single System 1 forward pass
        val questions = listOf(
            LayaQuestion.Choice(name = "domain", options = domainOptions),
            LayaQuestion.Choice(name = "intent", options = intentOptions),
            LayaQuestion.Noul(name = "is_emergency"),
            LayaQuestion.Score(name = "urgency", levels = 5),
            LayaQuestion.Score(name = "sentiment", levels = 5),
        )

        val batch = decisionEngine.evaluate(trimmed, questions)

        val intent = batch.choice("intent")?.value ?: "general.greetings"
        val domain = when {
            intent.startsWith("ride.") -> "mobility"
            intent.startsWith("auto.") -> "automotive"
            intent.startsWith("emissions.") -> "emissions"
            intent.startsWith("roadside.") -> "roadside"
            intent.startsWith("safety.") -> "safety"
            else -> batch.choice("domain")?.value ?: "general"
        }
        val confidence = batch.choice("intent")?.confidence ?: 0.85
        val isEmergency = batch.noul("is_emergency")?.value ?: false
        val urgency = batch.score("urgency")?.level ?: 3
        val latencyMs = batch.latencyMs

        // 2. Synthesize Grounded, Context-Aware Response
        val (responseText, actions) = generateResponse(
            intent = intent,
            isEmergency = isEmergency,
            urgency = urgency,
            ride = rideContext,
            vehicle = vehicleContext,
            query = trimmed,
        )

        return AssistantResponse(
            text = responseText,
            domain = domain,
            intent = intent,
            confidence = confidence,
            isEmergency = isEmergency,
            suggestedActions = actions,
            latencyMs = latencyMs,
        )
    }

    private fun generateResponse(
        intent: String,
        isEmergency: Boolean,
        urgency: Int,
        ride: RideAssistantContext?,
        vehicle: VehicleAssistantContext?,
        query: String,
    ): Pair<String, List<AssistantAction>> {
        // Priority 1: Emergency & Safety Intervention
        if (isEmergency) {
            val text = "⚠️ PROTOCOLO DE EMERGENCIA ACTIVADO: Tu seguridad es la prioridad absoluta. " +
                "Mantén la calma. Puedes presionar el botón de abajo para llamar de inmediato al 911 de Costa Rica " +
                "o activar el monitoreo Guardian para emitir una alerta cifrada con tus coordenadas satelitales."
            val actions = listOf(
                AssistantAction("action_911", "🚨 Llamar al 911", ActionType.SAFETY_CENTER),
                AssistantAction("action_guardian", "🛡️ Abrir Centro de Seguridad", ActionType.SAFETY_CENTER),
                AssistantAction("action_share", "📍 Compartir Ubicación", ActionType.SHARE_LOCATION),
            )
            return text to actions
        }

        return when (intent) {
            // ══════════════ MOBILITY: RIDES ══════════════
            "ride.eta_status" -> {
                if (ride != null && ride.state in listOf("ACCEPTED", "ARRIVING", "IN_PROGRESS")) {
                    val driver = ride.driverName ?: "El conductor"
                    val plate = ride.driverPlate?.let { " ($it)" } ?: ""
                    val eta = ride.etaMinutes?.let { "aproximadamente $it minutos" } ?: "tiempo estimado recalculándose"
                    val text = "🚗 $driver viene en camino$plate. El tiempo estimado de llegada es de $eta. Puedes monitorear su movimiento en tiempo real directamente en el mapa."
                    val actions = listOf(
                        AssistantAction("call_driver", "Llamar al conductor", ActionType.CALL_DRIVER),
                        AssistantAction("msg_driver", "Enviar mensaje", ActionType.MESSAGE_DRIVER),
                        AssistantAction("share_trip", "Compartir mi viaje", ActionType.SHARE_LOCATION),
                    )
                    text to actions
                } else {
                    "Actualmente no tienes un viaje en curso activo. Cuando solicites un viaje, te mostraré aquí el ETA exacto y la posición del vehículo en tiempo real." to emptyList()
                }
            }

            "ride.payment_method" -> {
                val fare = ride?.fareFormatted?.let { " Tarifa estimada: $it." } ?: ""
                val text = "💳 En MEET puedes pagar con SINPE Móvil al número verificado del conductor, tarjeta de débito/crédito en la aplicación, o en efectivo.$fare " +
                    "Si pagas por SINPE Móvil, recuerda solicitar el comprobante al finalizar el viaje para garantizar la conciliación automática en tu cuenta."
                val actions = listOf(
                    AssistantAction("sinpe_info", "Instrucciones de SINPE Móvil", ActionType.COPY_SINPE),
                )
                text to actions
            }

            "ride.pricing_inquiry" -> {
                val fareInfo = ride?.fareFormatted?.let { "Para este viaje la tarifa confirmada es de $it." } ?: "Las tarifas se calculan con el rate card oficial transparente."
                val text = "📊 $fareInfo Nuestra política de precios garantiza cero tarifas dinámicas abusivas ocultas. " +
                    "El desglose contempla tarifa base, distancia por kilómetro y tiempo en tráfico."
                text to emptyList()
            }

            "ride.route_stop" -> {
                val text = "📍 Para agregar una parada intermedia o ajustar la ruta, coordínalo amablemente con el conductor o modifícalo en la pantalla del mapa. " +
                    "El odómetro del viaje ajustará automáticamente la distancia recorrida sin cargos fantasma."
                val actions = listOf(
                    AssistantAction("msg_stop", "Avisar parada al conductor", ActionType.MESSAGE_DRIVER),
                )
                text to actions
            }

            "ride.pet_policy" -> {
                val text = "🐾 ¡Las mascotas son bienvenidas en MEET! Te recomendamos llevar a tu perro o gato en su kennel o con una manta protectora para los asientos. " +
                    "Es una buena práctica enviarle un mensaje corto al conductor antes de abordar para que prepare el espacio del vehículo."
                val actions = listOf(
                    AssistantAction("msg_pet", "Avisar sobre mascota", ActionType.MESSAGE_DRIVER),
                )
                text to actions
            }

            "ride.luggage_comfort" -> {
                val text = "🧳 El maletero del vehículo está a tu disposición para maletas o compras normales. " +
                    "Si viajas con equipaje voluminoso o deseas regular el aire acondicionado, indícaselo con confianza al conductor al abordar."
                text to emptyList()
            }

            "ride.share_trip" -> {
                val text = "🛡️ Puedes compartir tu enlace de seguimiento con familiares o amigos. " +
                    "Ellos verán la placa del vehículo, el nombre del conductor y tu ruta en vivo sin necesidad de tener la app instalada."
                val actions = listOf(
                    AssistantAction("share_live", "Enviar enlace en vivo", ActionType.SHARE_LOCATION),
                )
                text to actions
            }

            "ride.driver_delay" -> {
                val text = "⏳ Si notas que el conductor se ha demorado más de lo habitual, puede deberse a congestión vial en la zona. " +
                    "Puedes llamarlo o enviarle un mensaje rápido para consultar su estado."
                val actions = listOf(
                    AssistantAction("call_now", "Llamar al conductor", ActionType.CALL_DRIVER),
                    AssistantAction("msg_now", "Preguntar por chat", ActionType.MESSAGE_DRIVER),
                )
                text to actions
            }

            "ride.lost_item" -> {
                val text = "🎒 ¿Olvidaste un objeto en el vehículo? Mantén la calma: en la sección de Historial de Viajes puedes presionar 'Reportar Objeto Olvidado' " +
                    "para ponerte en contacto inmediato con el conductor a través de un canal seguro."
                text to emptyList()
            }

            "ride.cancel_policy" -> {
                val text = "❌ Puedes cancelar el viaje sin ningún costo si el conductor aún no ha recorrido una distancia considerable hacia tu punto de recogida. " +
                    "Si ya han pasado más de 5 minutos desde la asignación y el conductor está cerca, podría aplicar una tarifa mínima de compensación operativa."
                val actions = listOf(
                    AssistantAction("cancel_confirm", "Gestionar Cancelación", ActionType.CANCEL_RIDE),
                )
                text to actions
            }

            // ══════════════ AUTOMOTIVE & OBD ══════════════
            "auto.check_engine" -> {
                val dtcText = if (vehicle != null && vehicle.activeDtcs.isNotEmpty()) {
                    " Actualmente tu escáner registra: ${vehicle.activeDtcs.joinToString(", ")}."
                } else ""
                val text = "⚠️ La luz de Check Engine indica que la computadora del motor (ECU) detectó una anomalía en emisiones o rendimiento.$dtcText " +
                    "REGLA DE SEGURIDAD VITAL: Si la luz está FIJA, puedes conducir con precaución hasta un taller. " +
                    "Si la luz está PARPADEANDO, debes detenerte de inmediato: indica fallas de encendido severas que destruirán el convertidor catalítico."
                val actions = listOf(
                    AssistantAction("scan_now", "Escanear Códigos DTC", ActionType.SCAN_OBD),
                )
                text to actions
            }

            "auto.dtc_explanation" -> {
                val dtcDetails = when {
                    query.contains("P0300", ignoreCase = true) -> "P0300 indica fallos de encendido aleatorios en múltiples cilindros (bujías, bobinas o inyectores)."
                    query.contains("P0420", ignoreCase = true) -> "P0420 señala eficiencia del catalizador por debajo del umbral del banco 1 (sensores O2 o catalizador degradado)."
                    query.contains("P0171", ignoreCase = true) -> "P0171 significa mezcla de combustible demasiado pobre (fuga de vacío de aire o sensor MAF sucio)."
                    vehicle != null && vehicle.activeDtcs.isNotEmpty() -> "Tu vehículo reporta los códigos: ${vehicle.activeDtcs.joinToString(", ")}."
                    else -> "Los códigos DTC se dividen en P (Tren motriz), B (Carrocería), C (Chasis) y U (Red CAN)."
                }
                val text = "🔍 $dtcDetails Recuerda que el código OBD orienta el subsistema afectado, pero un buen diagnóstico requiere verificar arneses y lecturas de sensores en vivo."
                val actions = listOf(
                    AssistantAction("view_dtcs", "Ver Diagnóstico Completo", ActionType.VIEW_DTC_DETAILS),
                )
                text to actions
            }

            "auto.can_i_drive" -> {
                val text = "🛑 ¿Puedes seguir rodando? Evalúa tres señales críticas:\n" +
                    "1. ¿Temperatura del refrigerante normal? (Si pasa de 105°C, detente de inmediato).\n" +
                    "2. ¿Presión de aceite o freno encendido? (Prohibido rodar).\n" +
                    "3. ¿Check engine parpadeando o ruidos metálicos fuertes? Si no hay ninguna de estas tres, puedes conducir a velocidad moderada hacia tu taller de confianza."
                val actions = listOf(
                    AssistantAction("scan_obd", "Ver Temperatura y Sensores", ActionType.SCAN_OBD),
                    AssistantAction("call_tow", "Solicitar Grúa Preventiva", ActionType.REQUEST_TOW),
                )
                text to actions
            }

            "auto.repair_cost" -> {
                val text = "💰 En Costa Rica, el costo promedio de reparaciones varía según repuestos OEM o genéricos de calidad: " +
                    "Cambio de pastillas de freno: ₡25.000 - ₡45.000 CRC. Cambio de aceite sintético: ₡28.000 - ₡42.000 CRC. " +
                    "En el Marketplace de MEET puedes consultar el repuesto con compatibilidad garantizada por VIN."
                text to emptyList()
            }

            "auto.obd_connect" -> {
                val text = "🔌 Para conectar tu escáner ELM327 Bluetooth/WiFi:\n" +
                    "1. Conecta el adaptador al puerto OBD-II (usualmente bajo el volante o cerca de la caja de fusibles).\n" +
                    "2. Pon la llave del auto en posición ON (sin encender el motor).\n" +
                    "3. En tu teléfono, ve a Ajustes > Bluetooth, vincula el dispositivo (PIN 1234 o 0000) y pulsa 'Conectar' en la app."
                val actions = listOf(
                    AssistantAction("connect_obd", "Abrir Escáner", ActionType.SCAN_OBD),
                )
                text to actions
            }

            // ══════════════ EMISSIONS & PRE-ITV ══════════════
            "emissions.dekra_rules" -> {
                val text = "🌱 Para aprobar la prueba de emisiones en DEKRA (Costa Rica):\n" +
                    "• Monóxido de Carbono (CO): debe mantenerse dentro del límite legal según año de fabricación.\n" +
                    "• Hidrocarburos (HC): no deben superar las PPM permitidas.\n" +
                    "• Factor Lambda (λ): debe estar rigurosamente entre 0.97 y 1.03 a 2.500 RPM.\n" +
                    "Con nuestro módulo Pre-ITV puedes verificar estos parámetros antes de llevar el auto a la estación."
                val actions = listOf(
                    AssistantAction("open_pre_itv", "Ver Análisis Pre-ITV", ActionType.VIEW_PRE_ITV),
                )
                text to actions
            }

            "emissions.lambda_sensor" -> {
                val text = "🔬 El factor Lambda mide la relación estequiométrica aire-combustible:\n" +
                    "• λ = 1.00: Mezcla estequiométrica ideal (14.7 kg de aire por 1 kg de gasolina).\n" +
                    "• λ < 0.97: Mezcla RICA (exceso de combustible, riesgo de alto CO y humo negro).\n" +
                    "• λ > 1.03: Mezcla POBRE (exceso de oxígeno o fuga en escape, riesgo de alto HC y sobrecalentamiento)."
                text to emptyList()
            }

            "emissions.smoke_color" -> {
                val text = "💨 El color del humo del escape te dice el diagnóstico exacto:\n" +
                    "• Humo NEGRO: Exceso de combustible sin quemar (inyectores goteando, filtro sucio o sensor O2).\n" +
                    "• Humo AZUL: Quema de aceite de motor (retenedores de válvula o anillos de pistón desgastados).\n" +
                    "• Humo BLANCO espeso continuo: Paso de refrigerante a la cámara de combustión (empaque de culata soplado)."
                text to emptyList()
            }

            // ══════════════ ROADSIDE ASSISTANCE ══════════════
            "roadside.flat_tire" -> {
                val text = "🛞 Si se te ponchó o estalló una llanta:\n" +
                    "1. Estaciónate en un lugar plano y seguro lejos del carril de alta velocidad.\n" +
                    "2. Enciende las luces de emergencia y coloca los triángulos reflectivos a 30 metros.\n" +
                    "3. Si necesitas asistencia, en MEET puedes solicitar un auxilio vial rápido para cambio de llanta."
                val actions = listOf(
                    AssistantAction("call_tow", "Solicitar Asistencia Vial", ActionType.REQUEST_TOW),
                )
                text to actions
            }

            "roadside.tow_truck" -> {
                val text = "🚛 Si el vehículo no puede rodar por avería mecánica o colisión, puedes pedir una grúa de plataforma certificada a través de la red de asistencia de MEET. " +
                    "El servicio calcula la distancia exacta hasta tu taller o cochera sin sobreprecios."
                val actions = listOf(
                    AssistantAction("req_tow", "Pedir Grúa Plataforma", ActionType.REQUEST_TOW),
                )
                text to actions
            }

            "roadside.jump_start" -> {
                val text = "⚡ Si tu batería se descargó y el motor no da marcha: enciende las luces; si alumbran muy tenue o escuchas un 'clic clic' rápido, " +
                    "es falta de carga. Puedes solicitar paso de corriente seguro con cables protegidos contra picos de voltaje."
                val actions = listOf(
                    AssistantAction("req_battery", "Solicitar Paso de Corriente", ActionType.REQUEST_TOW),
                )
                text to actions
            }

            // ══════════════ SAFETY & GUARDIAN ══════════════
            "safety.suspicious" -> {
                val text = "🛡️ Tu tranquilidad es indispensable. Si el conductor tomó una ruta desconocida o sientes alguna incomodidad:\n" +
                    "1. Puedes compartir tu trayecto en vivo de inmediato con tus contactos de confianza.\n" +
                    "2. El sistema Guardian registra cada metro recorrido en servidor seguro.\n" +
                    "3. En cualquier momento puedes pedir al conductor detenerse en un lugar público y seguro."
                val actions = listOf(
                    AssistantAction("open_guardian", "Activar Guardian", ActionType.SAFETY_CENTER),
                    AssistantAction("share_route", "Compartir Ubicación", ActionType.SHARE_LOCATION),
                    AssistantAction("call_911_alert", "Llamar 911", ActionType.SAFETY_CENTER),
                )
                text to actions
            }

            // ══════════════ GENERAL / TEACH MODE ══════════════
            "general.teach_me" -> {
                val text = "🎓 ¡Con gusto! Activando el Modo Enseñanza (Teach Mode). Observa el indicador táctil de EVAIR que te mostrará paso a paso cómo utilizar cada botón y función de esta pantalla."
                text to emptyList()
            }

            else -> {
                val text = "¡Hola! Estoy aquí para acompañarte en tu viaje o asistirte con tu vehículo. " +
                    "Puedes consultarme sobre: tiempo de llegada del chofer, métodos de pago (SINPE Móvil), significado de códigos de motor (DTCs), requisitos de Dekra o pedir una grúa."
                text to emptyList()
            }
        }
    }
}
