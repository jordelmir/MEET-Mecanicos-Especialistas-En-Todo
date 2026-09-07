package com.elysium369.meet.education.domain

data class DtcEducationalBridge(
    val dtcCode: String,
    val standardTitle: String,
    val primaryConceptId: String,
    val conceptTitle: String,
    val academicGrade: Int,
    val subjectArea: String,
    val underlyingPhysicsPrinciple: String,
    val socraticInquiryPrompt: String,
    val sandboxType: String? = null // "ELECTRICAL", "GEOMETRY", etc.
)

/**
 * Puente Bidireccional Bucle Cerrado: OBD Diagnostic Trouble Codes ↔ Currículo MEP y Sandbox Físico.
 * Implementa el principio rector "Todo en uno" de MEET.
 */
object DtcCurriculumBridge {

    private val DTC_REGISTRY: Map<String, DtcEducationalBridge> = mapOf(
        "P0230" to DtcEducationalBridge(
            dtcCode = "P0230",
            standardTitle = "Fuel Pump Primary Circuit Malfunction",
            primaryConceptId = "cr_elec9_c_ley_ohm",
            conceptTitle = "Ley de Ohm, Circuitos de Conmutación y Relés",
            academicGrade = 9,
            subjectArea = "ELECTRICIDAD_Y_ELECTRONICA",
            underlyingPhysicsPrinciple = "La caída de tensión en una bobina de relé requiere continuidad completa entre terminal 85 (+) y 86 (-). Sin masa, no hay flujo de corriente (I = V/R = 0), impidiendo el cebado de la bomba.",
            socraticInquiryPrompt = "Si el relé de la bomba no activa pero mides 12V en el terminal 85, ¿qué condición física debe cumplirse en el terminal 86 para que circule corriente según la Ley de Ohm?",
            sandboxType = "ELECTRICAL"
        ),
        "P0562" to DtcEducationalBridge(
            dtcCode = "P0562",
            standardTitle = "System Voltage Low",
            primaryConceptId = "cr_elec9_c_bateria_alternador",
            conceptTitle = "Resistencia Interna de Batería y Caída de Tensión",
            academicGrade = 9,
            subjectArea = "ELECTRICIDAD_Y_ELECTRONICA",
            underlyingPhysicsPrinciple = "La ley de tensiones de Kirchhoff y la resistencia parásita por sulfatación provocan caídas severas de tensión bajo carga de arranque.",
            socraticInquiryPrompt = "¿Por qué una batería que mide 12.6V en reposo puede caer a 8V al activar el motor de arranque?",
            sandboxType = "ELECTRICAL"
        ),
        "P0115" to DtcEducationalBridge(
            dtcCode = "P0115",
            standardTitle = "Engine Coolant Temperature Circuit Malfunction",
            primaryConceptId = "cr_elec9_c_termistores_ntc",
            conceptTitle = "Divisores de Tensión y Termistores de Coeficiente Negativo",
            academicGrade = 9,
            subjectArea = "CIENCIAS_FISICAS",
            underlyingPhysicsPrinciple = "En un termistor NTC, un incremento de temperatura disminuye la resistencia interna, haciendo descender la tensión leída por el convertidor ADC de la ECM.",
            socraticInquiryPrompt = "Si el conector del sensor ECT se desconecta por completo (resistencia infinita), ¿qué voltaje esperas medir en la línea de señal de 5V?",
            sandboxType = "ELECTRICAL"
        ),
        "P0300" to DtcEducationalBridge(
            dtcCode = "P0300",
            standardTitle = "Random/Multiple Cylinder Misfire Detected",
            primaryConceptId = "cr_quim_c_estequiometria",
            conceptTitle = "Estequiometría de Combustión y Balance Químico",
            academicGrade = 10,
            subjectArea = "QUIMICA",
            underlyingPhysicsPrinciple = "La combustión de hidrocarburos C_n H_(2n+2) requiere una relación másica precisa de 14.7:1 con el oxígeno atmosférico para propagar la llama.",
            socraticInquiryPrompt = "¿Cómo influye una fuga de vacío (aire no medido por el MAF) en la energía de activación necesaria para la chispa de la bujía?",
            sandboxType = null
        )
    )

    fun getBridgeForDtc(dtcCode: String): DtcEducationalBridge? {
        val cleanCode = dtcCode.trim().uppercase()
        return DTC_REGISTRY[cleanCode]
    }

    fun findBridgesForActiveDtcs(dtcCodes: List<String>): List<DtcEducationalBridge> {
        return dtcCodes.mapNotNull { getBridgeForDtc(it) }
    }

    fun getAllSupportedCodes(): Set<String> {
        return DTC_REGISTRY.keys
    }
}
